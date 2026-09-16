package com.Teenkung.devSkills.storage;

import com.Teenkung.devSkills.api.booster.XpBoosterScope;
import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.config.StorageSettings;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.domain.user.UserSettings;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteStorageTest {

    @TempDir
    private Path tempDir;

    @Test
    void savesAndLoadsProfile() {
        StorageSettings settings = new StorageSettings("sqlite", "test.db", "localhost", 3306, "devskills", "root", "", false, 1);
        UUID uuid = UUID.randomUUID();
        UserProfile profile = new UserProfile(
                uuid,
                Map.of("mining", 123.5D),
                Map.of("strength", 4),
                new UserSettings(true, false, true, "en")
        );

        SqliteStorage storage = new SqliteStorage(tempDir.toFile(), settings);
        storage.initialize();
        storage.saveProfile(profile);
        UserProfile loaded = storage.loadProfile(uuid);
        storage.close();

        Assertions.assertEquals(123.5D, loaded.xp("mining"), 0.001D);
        Assertions.assertEquals(4, loaded.manualTraitLevel("strength"));
        Assertions.assertFalse(loaded.settings().actionbar());
        Assertions.assertTrue(loaded.settings().bossbar());
    }

    @Test
    void migratesEmptyLegacySchemaToVersionTwo() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE devskill_schema (version INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE devskill_users (uuid VARCHAR(36) PRIMARY KEY, locale VARCHAR(16), sounds BOOLEAN, actionbar BOOLEAN, bossbar BOOLEAN, updated_at BIGINT)");
        }

        SqliteStorage storage = new SqliteStorage(tempDir.toFile(), settings("legacy.db"));
        storage.initialize();
        storage.close();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT version FROM devskill_schema")) {
            Assertions.assertTrue(result.next());
            Assertions.assertEquals(2, result.getInt("version"));
            Assertions.assertFalse(result.next());
        }
    }

    @Test
    void persistsBoostersPrunesExpiryAndResolvesKnownNames() {
        Instant start = Instant.parse("2026-07-17T00:00:00Z");
        UUID playerId = UUID.randomUUID();
        XpBoosterSnapshot global = new XpBoosterSnapshot(
                UUID.randomUUID(), XpBoosterScope.GLOBAL, Optional.empty(), 1.5D, "Weekend",
                Optional.empty(), start, Optional.empty()
        );
        XpBoosterSnapshot expired = new XpBoosterSnapshot(
                UUID.randomUUID(), XpBoosterScope.PLAYER, Optional.of(playerId), 2.0D, "Personal",
                Optional.of(UUID.randomUUID()), start, Optional.of(start.plusSeconds(60L))
        );

        SqliteStorage storage = new SqliteStorage(tempDir.toFile(), settings("boosters.db"));
        storage.initialize();
        storage.saveBooster(global);
        storage.saveBooster(expired);
        storage.recordPlayerIdentity(playerId, "KnownPlayer");

        List<XpBoosterSnapshot> loaded = storage.loadBoosters();
        Assertions.assertEquals(2, loaded.size());
        Assertions.assertTrue(loaded.contains(global));
        Assertions.assertTrue(loaded.contains(expired));
        Assertions.assertEquals(Optional.of(playerId), storage.findKnownPlayerUuid("knownplayer"));
        Assertions.assertEquals(1, storage.deleteExpiredBoosters(start.plusSeconds(60L).toEpochMilli()));
        Assertions.assertEquals(List.of(global), storage.loadBoosters());
        Assertions.assertTrue(storage.deleteBooster(global.id()));
        Assertions.assertFalse(storage.deleteBooster(global.id()));
        storage.close();
    }

    @Test
    void replacingKnownNameMappingIsCaseInsensitive() {
        UUID oldId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        SqliteStorage storage = new SqliteStorage(tempDir.toFile(), settings("identities.db"));
        storage.initialize();

        storage.recordPlayerIdentity(oldId, "PlayerOne");
        storage.recordPlayerIdentity(newId, "PLAYERONE");

        Assertions.assertEquals(Optional.of(newId), storage.findKnownPlayerUuid("playerone"));
        storage.close();
    }

    private StorageSettings settings(String sqliteFile) {
        return new StorageSettings("sqlite", sqliteFile, "localhost", 3306, "devskills", "root", "", false, 1);
    }
}
