package com.Teenkung.devSkills.service;

import com.Teenkung.devSkills.api.booster.XpBoosterRequest;
import com.Teenkung.devSkills.api.booster.XpBoosterScope;
import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.config.StorageSettings;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.storage.SqliteStorage;
import com.Teenkung.devSkills.storage.StorageProvider;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class XpBoosterServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void stacksGlobalAndPlayerBoostersAdditivelyAndCapsResult() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        SqliteStorage storage = storage("stacking.db");
        XpBoosterService service = service(storage, 2.25D, clock);
        service.start().join();

        service.create(XpBoosterRequest.global(1.5D, "Global", null, null)).join();
        service.create(XpBoosterRequest.player(player, 2.0D, "Personal", null, null)).join();

        Assertions.assertEquals(2.25D, service.effectiveMultiplier(player), 0.0001D);
        Assertions.assertEquals(1.5D, service.effectiveMultiplier(other), 0.0001D);
        Assertions.assertEquals(2, service.activeBoosters(player).size());
        Assertions.assertEquals(1, service.activeGlobalBoosters().size());
        Assertions.assertEquals(1, service.activePlayerBoosters(player).size());
        service.close();
        storage.close();
    }

    @Test
    void combinesOnePointFiveAndTwoAsTwoPointFive() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        UUID player = UUID.randomUUID();
        SqliteStorage storage = storage("uncapped-stacking.db");
        XpBoosterService service = service(storage, 100.0D, clock);
        service.start().join();

        service.create(XpBoosterRequest.global(1.5D, "Global", null, null)).join();
        service.create(XpBoosterRequest.player(player, 2.0D, "Personal", null, null)).join();

        Assertions.assertEquals(2.5D, service.effectiveMultiplier(player), 0.0001D);
        service.close();
        storage.close();
    }

    @Test
    void reloadBarrierDrainsAndTemporarilyRejectsMutations() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        SqliteStorage storage = storage("reload-barrier.db");
        XpBoosterService service = service(storage, 100.0D, clock);
        service.start().join();
        XpBoosterSnapshot existing = service.create(XpBoosterRequest.global(1.5D, "Before reload", null, null)).join();

        service.pauseMutationsAndDrain().join();
        Assertions.assertEquals(List.of(existing), storage.loadBoosters());
        Assertions.assertThrows(RuntimeException.class, () -> service.create(
                XpBoosterRequest.global(2.0D, "During reload", null, null)
        ).join());

        service.resumeMutations();
        service.create(XpBoosterRequest.global(2.0D, "After failure", null, null)).join();
        Assertions.assertEquals(2.5D, service.effectiveMultiplier(UUID.randomUUID()), 0.0001D);
        service.close();
        storage.close();
    }

    @Test
    void filtersExpirySynchronouslyAndPrunesPersistedRows() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        UUID player = UUID.randomUUID();
        SqliteStorage storage = storage("expiry.db");
        XpBoosterService service = service(storage, 100.0D, clock);
        service.start().join();
        XpBoosterSnapshot booster = service.create(XpBoosterRequest.player(
                player, 2.0D, "Timed", null, clock.instant().plus(Duration.ofMinutes(5L))
        )).join();

        Assertions.assertEquals(2.0D, service.effectiveMultiplier(player), 0.0001D);
        clock.advance(Duration.ofMinutes(5L));
        Assertions.assertEquals(1.0D, service.effectiveMultiplier(player), 0.0001D);
        Assertions.assertTrue(service.activeBoosters(player).isEmpty());
        Assertions.assertEquals(1, service.pruneExpired().join());
        Assertions.assertFalse(storage.loadBoosters().contains(booster));
        service.close();
        storage.close();
    }

    @Test
    void reloadsPermanentBoosterAndKnownOfflineIdentity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        UUID player = UUID.randomUUID();
        SqliteStorage storage = storage("restart.db");
        XpBoosterService first = service(storage, 100.0D, clock);
        first.start().join();
        XpBoosterSnapshot created = first.create(XpBoosterRequest.global(1.75D, "Permanent", null, null)).join();
        first.recordKnownPlayer(player, "OfflinePlayer").join();
        first.close();

        XpBoosterService second = service(storage, 100.0D, clock);
        second.start().join();

        Assertions.assertEquals(created, second.activeBoosters().getFirst());
        Assertions.assertEquals(Optional.of(player), second.findKnownPlayer("offlineplayer").join());
        second.close();
        storage.close();
    }

    @Test
    void prunesExpiredRowsDuringStartupAndRemovesByUniquePrefix() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T01:00:00Z"));
        UUID expiredId = UUID.randomUUID();
        SqliteStorage storage = storage("startup-prune.db");
        storage.saveBooster(new XpBoosterSnapshot(
                expiredId,
                XpBoosterScope.GLOBAL,
                Optional.empty(),
                2.0D,
                "Expired",
                Optional.empty(),
                clock.instant().minus(Duration.ofHours(2L)),
                Optional.of(clock.instant().minus(Duration.ofHours(1L)))
        ));
        XpBoosterService service = service(storage, 100.0D, clock);
        service.start().join();
        Assertions.assertTrue(service.activeBoosters().isEmpty());
        Assertions.assertTrue(storage.loadBoosters().isEmpty());

        XpBoosterSnapshot active = service.create(XpBoosterRequest.global(2.0D, "Active", null, null)).join();
        String prefix = active.id().toString().substring(0, 8);
        Assertions.assertEquals(Optional.of(active), service.removeByIdOrUniquePrefix(prefix).join());
        Assertions.assertTrue(service.activeBoosters().isEmpty());
        Assertions.assertTrue(storage.loadBoosters().isEmpty());
        service.close();
        storage.close();
    }

    @Test
    void rejectsInvalidAndAlreadyExpiredRequests() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> XpBoosterRequest.global(Double.NaN, "Invalid", null, null));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> XpBoosterRequest.global(1.0D, "Invalid", null, null));

        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        SqliteStorage storage = storage("validation.db");
        XpBoosterService service = service(storage, 100.0D, clock);
        service.start().join();
        Assertions.assertThrows(RuntimeException.class, () -> service.create(
                XpBoosterRequest.global(2.0D, "Expired", null, clock.instant())
        ).join());
        Assertions.assertTrue(service.activeBoosters().isEmpty());
        service.close();
        storage.close();
    }

    @Test
    void doesNotPublishBoosterWhenPersistenceFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        XpBoosterService service = new XpBoosterService(new FailingStorage(), 100.0D, clock, executor);
        service.start().join();

        Assertions.assertThrows(RuntimeException.class, () -> service.create(
                XpBoosterRequest.global(2.0D, "Cannot persist", null, null)
        ).join());
        Assertions.assertTrue(service.activeBoosters().isEmpty());
        service.close();
    }

    private SqliteStorage storage(String fileName) {
        StorageSettings settings = new StorageSettings(
                "sqlite", fileName, "localhost", 3306, "devskills", "root", "", false, 1
        );
        SqliteStorage storage = new SqliteStorage(tempDir.toFile(), settings);
        storage.initialize();
        return storage;
    }

    private XpBoosterService service(SqliteStorage storage, double maximumMultiplier, Clock clock) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        return new XpBoosterService(storage, maximumMultiplier, clock, executor);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("test clock only supports UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class FailingStorage implements StorageProvider {

        @Override
        public void initialize() {
        }

        @Override
        public UserProfile loadProfile(UUID uuid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveProfile(UserProfile profile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<XpBoosterSnapshot> loadBoosters() {
            return List.of();
        }

        @Override
        public void saveBooster(XpBoosterSnapshot booster) {
            throw new IllegalStateException("database unavailable");
        }

        @Override
        public boolean deleteBooster(UUID boosterId) {
            return false;
        }

        @Override
        public int deleteExpiredBoosters(long expiresAtOrBeforeEpochMillis) {
            return 0;
        }

        @Override
        public void recordPlayerIdentity(UUID playerId, String playerName) {
        }

        @Override
        public Optional<UUID> findKnownPlayerUuid(String playerName) {
            return Optional.empty();
        }

        @Override
        public void close() {
        }
    }
}
