package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.api.event.DevSkillProfileLoadEvent;
import com.Teenkung.devSkills.domain.trait.EffectiveTraits;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.integration.MythicLibStatBridge;
import com.Teenkung.devSkills.storage.StorageProvider;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ProfileService {

    private final JavaPlugin plugin;
    private final StorageProvider storageProvider;
    private final TraitService traitService;
    private final MythicLibStatBridge mythicLibStatBridge;
    private final Map<UUID, UserProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dirtyGenerations = new ConcurrentHashMap<>();
    private final ExecutorService storageExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DevSkills-ProfileStorage");
        thread.setDaemon(true);
        return thread;
    });
    private BukkitTask flushTask;
    private volatile boolean closed;

    public ProfileService(JavaPlugin plugin, StorageProvider storageProvider, TraitService traitService, MythicLibStatBridge mythicLibStatBridge) {
        this.plugin = plugin;
        this.storageProvider = storageProvider;
        this.traitService = traitService;
        this.mythicLibStatBridge = mythicLibStatBridge;
    }

    public void start(int flushIntervalSeconds) {
        if (closed) {
            throw new IllegalStateException("Profile service is closed");
        }
        long interval = 20L * Math.max(1, flushIntervalSeconds);
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushDirty, interval, interval);
    }

    public void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        for (UserProfile profile : new ArrayList<>(profiles.values())) {
            saveAsync(profile.snapshot());
        }
        profiles.clear();
        dirtyGenerations.clear();
        storageExecutor.shutdown();
        try {
            if (!storageExecutor.awaitTermination(30L, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Timed out while flushing DevSkills profiles during shutdown; cancelling remaining writes.");
                storageExecutor.shutdownNow();
                storageExecutor.awaitTermination(5L, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while flushing DevSkills profiles during shutdown.");
        }
    }

    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        CompletableFuture.supplyAsync(() -> storageProvider.loadProfile(uuid), storageExecutor).whenComplete((profile, failure) -> {
            if (failure != null) {
                if (!closed) {
                    plugin.getLogger().severe("Failed to load DevSkills profile " + uuid + ": " + failure.getMessage());
                }
                return;
            }
            if (closed) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed || !player.isOnline()) {
                    if (closed) {
                        return;
                    }
                    saveAsync(profile.snapshot());
                    return;
                }
                profiles.put(uuid, profile);
                Bukkit.getPluginManager().callEvent(new DevSkillProfileLoadEvent(player, profile));
                EffectiveTraits traits = traitService.effectiveTraits(profile);
                Bukkit.getScheduler().runTaskLater(plugin, () -> mythicLibStatBridge.refresh(player, traits), 1L);
            });
        });
    }

    public void unload(Player player) {
        UUID uuid = player.getUniqueId();
        UserProfile profile = profiles.remove(uuid);
        dirtyGenerations.remove(uuid);
        if (profile != null) {
            saveAsync(profile.snapshot());
        }
    }

    public Optional<UserProfile> profile(UUID uuid) {
        return Optional.ofNullable(profiles.get(uuid));
    }

    public void markDirty(UserProfile profile) {
        if (closed) {
            return;
        }
        dirtyGenerations.merge(profile.uuid(), 1L, Long::sum);
    }

    public void flushDirty() {
        if (closed) {
            return;
        }
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(dirtyGenerations.entrySet())) {
            UUID uuid = entry.getKey();
            UserProfile profile = profiles.get(uuid);
            if (profile != null) {
                long generation = entry.getValue();
                UserProfile snapshot = profile.snapshot();
                CompletableFuture.runAsync(() -> storageProvider.saveProfile(snapshot), storageExecutor)
                        .whenComplete((ignored, failure) -> {
                            if (failure == null) {
                                dirtyGenerations.remove(uuid, generation);
                            } else {
                                plugin.getLogger().severe("Failed to save DevSkills profile " + uuid + ": " + failure.getMessage());
                            }
                        });
            }
        }
    }

    private void saveAsync(UserProfile snapshot) {
        CompletableFuture.runAsync(() -> storageProvider.saveProfile(snapshot), storageExecutor)
                .exceptionally(failure -> {
                    plugin.getLogger().severe("Failed to save DevSkills profile " + snapshot.uuid() + ": " + failure.getMessage());
                    return null;
                });
    }
}
