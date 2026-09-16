package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.api.booster.XpBoosterRequest;
import com.Teenkung.devSkills.api.booster.XpBoosterScope;
import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.storage.StorageProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Owns persisted XP boosters and provides lock-free active-booster queries.
 *
 * <p>All storage mutations are serialized. A mutation is published to the in-memory view only
 * after its storage operation has completed successfully.</p>
 */
public final class XpBoosterService implements AutoCloseable {

    private static final Comparator<XpBoosterSnapshot> DISPLAY_ORDER = Comparator
            .comparing(XpBoosterSnapshot::startsAt)
            .thenComparing(XpBoosterSnapshot::id);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final StorageProvider storageProvider;
    private final double maximumMultiplier;
    private final Clock clock;
    private final ExecutorService executor;
    private final ConcurrentHashMap<UUID, XpBoosterSnapshot> boosters = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private volatile boolean closed;
    private boolean mutationsPaused;
    private CompletableFuture<Void> startFuture;

    /**
     * Creates a service using a dedicated daemon persistence worker and the UTC wall clock.
     *
     * @param storageProvider initialized storage provider
     * @param maximumMultiplier maximum combined multiplier; must be finite and at least one
     */
    public XpBoosterService(StorageProvider storageProvider, double maximumMultiplier) {
        this(storageProvider, maximumMultiplier, Clock.systemUTC(), newPersistenceExecutor());
    }

    XpBoosterService(StorageProvider storageProvider, double maximumMultiplier, Clock clock, ExecutorService executor) {
        this.storageProvider = Objects.requireNonNull(storageProvider, "storageProvider");
        if (!Double.isFinite(maximumMultiplier) || maximumMultiplier < 1.0D) {
            throw new IllegalArgumentException("maximumMultiplier must be finite and at least one");
        }
        this.maximumMultiplier = maximumMultiplier;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Loads persisted boosters and prunes expired rows before publishing the initial view.
     */
    public CompletableFuture<Void> start() {
        synchronized (lifecycleLock) {
            if (startFuture != null) {
                return startFuture;
            }
            startFuture = submitLocked(() -> {
                Instant now = clock.instant();
                storageProvider.deleteExpiredBoosters(now.toEpochMilli());
                List<XpBoosterSnapshot> loaded = storageProvider.loadBoosters();
                ConcurrentHashMap<UUID, XpBoosterSnapshot> replacement = new ConcurrentHashMap<>();
                for (XpBoosterSnapshot booster : loaded) {
                    if (booster.activeAt(now)) {
                        replacement.put(booster.id(), booster);
                    }
                }
                boosters.clear();
                boosters.putAll(replacement);
                return null;
            });
            return startFuture;
        }
    }

    /**
     * Persists and then activates a booster.
     */
    public CompletableFuture<XpBoosterSnapshot> create(XpBoosterRequest request) {
        Objects.requireNonNull(request, "request");
        return submitMutation(() -> {
            Instant startsAt = clock.instant();
            if (request.expiresAt().isPresent() && !request.expiresAt().orElseThrow().isAfter(startsAt)) {
                throw new IllegalArgumentException("booster expiry must be in the future");
            }
            XpBoosterSnapshot booster = new XpBoosterSnapshot(
                    UUID.randomUUID(),
                    request.scope(),
                    request.targetPlayer(),
                    request.multiplier(),
                    request.label(),
                    request.creator(),
                    startsAt,
                    request.expiresAt()
            );
            storageProvider.saveBooster(booster);
            boosters.put(booster.id(), booster);
            return booster;
        });
    }

    /**
     * Deletes a booster by its exact UUID and then removes it from the active view.
     */
    public CompletableFuture<Boolean> remove(UUID boosterId) {
        Objects.requireNonNull(boosterId, "boosterId");
        return submitMutation(() -> {
            boolean deleted = storageProvider.deleteBooster(boosterId);
            boosters.remove(boosterId);
            return deleted;
        });
    }

    /**
     * Deletes a booster by an exact ID or a unique case-insensitive ID prefix.
     *
     * @return removed snapshot, or empty when no in-memory booster matches
     * @throws IllegalArgumentException when the prefix is empty or ambiguous
     */
    public CompletableFuture<Optional<XpBoosterSnapshot>> removeByIdOrUniquePrefix(String idOrPrefix) {
        String normalized = Objects.requireNonNull(idOrPrefix, "idOrPrefix").strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("booster id or prefix cannot be empty");
        }
        return submitMutation(() -> {
            List<XpBoosterSnapshot> matches = boosters.values().stream()
                    .filter(booster -> booster.id().toString().toLowerCase(Locale.ROOT).startsWith(normalized))
                    .toList();
            if (matches.size() > 1) {
                throw new IllegalArgumentException("booster id prefix is ambiguous");
            }
            if (matches.isEmpty()) {
                return Optional.empty();
            }
            XpBoosterSnapshot match = matches.getFirst();
            storageProvider.deleteBooster(match.id());
            boosters.remove(match.id());
            return Optional.of(match);
        });
    }

    /**
     * Deletes expired persisted rows and then evicts expired in-memory snapshots.
     */
    public CompletableFuture<Integer> pruneExpired() {
        return submit(() -> {
            Instant now = clock.instant();
            int deleted = storageProvider.deleteExpiredBoosters(now.toEpochMilli());
            boosters.entrySet().removeIf(entry -> !entry.getValue().activeAt(now));
            return deleted;
        });
    }

    /**
     * Records a safe offline player name mapping asynchronously.
     */
    public CompletableFuture<Void> recordKnownPlayer(UUID playerId, String playerName) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        return submit(() -> {
            storageProvider.recordPlayerIdentity(playerId, playerName);
            return null;
        });
    }

    /**
     * Resolves a previously recorded offline player name case-insensitively.
     */
    public CompletableFuture<Optional<UUID>> findKnownPlayer(String playerName) {
        Objects.requireNonNull(playerName, "playerName");
        return submit(() -> storageProvider.findKnownPlayerUuid(playerName));
    }

    /**
     * Returns all active global and player-specific boosters applicable to a player.
     */
    public List<XpBoosterSnapshot> activeBoosters(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Instant now = clock.instant();
        return activeSnapshot(now).stream()
                .filter(booster -> booster.scope() == XpBoosterScope.GLOBAL
                        || booster.targetPlayer().filter(playerId::equals).isPresent())
                .toList();
    }

    /**
     * Returns every active booster, including boosters for other players.
     */
    public List<XpBoosterSnapshot> activeBoosters() {
        return activeSnapshot(clock.instant());
    }

    /**
     * Returns active global boosters only.
     */
    public List<XpBoosterSnapshot> activeGlobalBoosters() {
        return activeSnapshot(clock.instant()).stream()
                .filter(booster -> booster.scope() == XpBoosterScope.GLOBAL)
                .toList();
    }

    /**
     * Returns active boosters targeting exactly one player, excluding global boosters.
     */
    public List<XpBoosterSnapshot> activePlayerBoosters(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return activeSnapshot(clock.instant()).stream()
                .filter(booster -> booster.scope() == XpBoosterScope.PLAYER)
                .filter(booster -> booster.targetPlayer().filter(playerId::equals).isPresent())
                .toList();
    }

    /**
     * Calculates {@code 1 + sum(multiplier - 1)} and clamps it to the configured maximum.
     */
    public double effectiveMultiplier(UUID playerId) {
        double combined = 1.0D;
        for (XpBoosterSnapshot booster : activeBoosters(playerId)) {
            combined = Math.min(maximumMultiplier, combined + booster.additiveContribution());
            if (combined >= maximumMultiplier) {
                return maximumMultiplier;
            }
        }
        return combined;
    }

    public double maximumMultiplier() {
        return maximumMultiplier;
    }

    /**
     * Prevents new booster create/remove operations and completes after previously accepted
     * mutations have drained. Queries remain available while a replacement runtime is prepared.
     */
    public CompletableFuture<Void> pauseMutationsAndDrain() {
        synchronized (lifecycleLock) {
            if (closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("XP booster service is closed"));
            }
            mutationsPaused = true;
            return submitLocked(() -> null);
        }
    }

    /** Reopens mutations when a prepared runtime could not be installed. */
    public void resumeMutations() {
        synchronized (lifecycleLock) {
            if (!closed) {
                mutationsPaused = false;
            }
        }
    }

    /**
     * Stops accepting work and blocks until queued storage mutations have drained.
     */
    public void shutdown() {
        close();
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            executor.shutdown();
        }
        boolean interrupted = false;
        try {
            if (!executor.awaitTermination(30L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out draining XP booster persistence tasks");
                }
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            executor.shutdownNow();
            throw new IllegalStateException("Interrupted while draining XP booster persistence tasks", exception);
        } finally {
            boosters.clear();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<XpBoosterSnapshot> activeSnapshot(Instant now) {
        List<XpBoosterSnapshot> active = new ArrayList<>();
        for (XpBoosterSnapshot booster : boosters.values()) {
            if (booster.activeAt(now)) {
                active.add(booster);
            }
        }
        active.sort(DISPLAY_ORDER);
        return List.copyOf(active);
    }

    private <T> CompletableFuture<T> submit(Supplier<T> action) {
        synchronized (lifecycleLock) {
            return submitLocked(action);
        }
    }

    private <T> CompletableFuture<T> submitMutation(Supplier<T> action) {
        synchronized (lifecycleLock) {
            if (mutationsPaused) {
                return CompletableFuture.failedFuture(new IllegalStateException("XP booster mutations are paused for reload"));
            }
            return submitLocked(action);
        }
    }

    private <T> CompletableFuture<T> submitLocked(Supplier<T> action) {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("XP booster service is closed"));
        }
        try {
            return CompletableFuture.supplyAsync(action, executor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static ExecutorService newPersistenceExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DevSkills-XpBooster-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
