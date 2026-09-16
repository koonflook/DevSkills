package com.Teenkung.devSkills.storage;

import com.Teenkung.devSkills.api.booster.XpBoosterScope;
import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.config.StorageSettings;
import com.Teenkung.devSkills.domain.user.UserProfile;
import com.Teenkung.devSkills.domain.user.UserSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

abstract class JdbcStorageProvider implements StorageProvider {

    private static final int CURRENT_SCHEMA_VERSION = 2;

    private final StorageSettings settings;
    private HikariDataSource dataSource;

    JdbcStorageProvider(StorageSettings settings) {
        this.settings = settings;
    }

    @Override
    public void initialize() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl());
        config.setMaximumPoolSize(Math.max(1, settings.poolSize()));
        configure(config);
        dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection()) {
            migrate(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize DevSkills storage", exception);
        }
    }

    @Override
    public UserProfile loadProfile(UUID uuid) {
        try (Connection connection = dataSource.getConnection()) {
            UserSettings userSettings = loadSettings(connection, uuid);
            Map<String, Double> xp = loadXp(connection, uuid);
            Map<String, Integer> traits = loadManualTraits(connection, uuid);
            return new UserProfile(uuid, xp, traits, userSettings);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load DevSkills profile " + uuid, exception);
        }
    }

    @Override
    public void saveProfile(UserProfile profile) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            deleteRows(connection, "devskill_users", "uuid", profile.uuid().toString());
            deleteRows(connection, "devskill_skill_xp", "uuid", profile.uuid().toString());
            deleteRows(connection, "devskill_trait_grant", "uuid", profile.uuid().toString());
            insertUser(connection, profile);
            insertXp(connection, profile);
            insertTraits(connection, profile);
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save DevSkills profile " + profile.uuid(), exception);
        }
    }

    @Override
    public List<XpBoosterSnapshot> loadBoosters() {
        String sql = "SELECT id, booster_scope, target_uuid, multiplier, label, creator_uuid, starts_at, expires_at FROM devskill_xp_booster";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            List<XpBoosterSnapshot> boosters = new ArrayList<>();
            while (result.next()) {
                boosters.add(readBooster(result));
            }
            return List.copyOf(boosters);
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load DevSkills XP boosters", exception);
        }
    }

    @Override
    public void saveBooster(XpBoosterSnapshot booster) {
        String sql = "INSERT INTO devskill_xp_booster "
                + "(id, booster_scope, target_uuid, multiplier, label, creator_uuid, starts_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, booster.id().toString());
            statement.setString(2, booster.scope().name());
            setNullableUuid(statement, 3, booster.targetPlayer());
            statement.setDouble(4, booster.multiplier());
            statement.setString(5, booster.label());
            setNullableUuid(statement, 6, booster.creator());
            statement.setLong(7, booster.startsAt().toEpochMilli());
            if (booster.expiresAt().isPresent()) {
                statement.setLong(8, booster.expiresAt().orElseThrow().toEpochMilli());
            } else {
                statement.setNull(8, Types.BIGINT);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save DevSkills XP booster " + booster.id(), exception);
        }
    }

    @Override
    public boolean deleteBooster(UUID boosterId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM devskill_xp_booster WHERE id = ?")) {
            statement.setString(1, boosterId.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete DevSkills XP booster " + boosterId, exception);
        }
    }

    @Override
    public int deleteExpiredBoosters(long expiresAtOrBeforeEpochMillis) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM devskill_xp_booster WHERE expires_at IS NOT NULL AND expires_at <= ?")) {
            statement.setLong(1, expiresAtOrBeforeEpochMillis);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to prune expired DevSkills XP boosters", exception);
        }
    }

    @Override
    public void recordPlayerIdentity(UUID playerId, String playerName) {
        String normalizedName = normalizePlayerName(playerName);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM devskill_player_identity WHERE uuid = ? OR player_name_lower = ?")) {
                    delete.setString(1, playerId.toString());
                    delete.setString(2, normalizedName);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO devskill_player_identity (uuid, player_name, player_name_lower, updated_at) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, playerId.toString());
                    insert.setString(2, playerName.strip());
                    insert.setString(3, normalizedName);
                    insert.setLong(4, System.currentTimeMillis());
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record DevSkills player identity " + playerId, exception);
        }
    }

    @Override
    public Optional<UUID> findKnownPlayerUuid(String playerName) {
        String normalizedName = normalizePlayerName(playerName);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid FROM devskill_player_identity WHERE player_name_lower = ?")) {
            statement.setString(1, normalizedName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(UUID.fromString(result.getString("uuid")))
                        : Optional.empty();
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to find known DevSkills player " + playerName, exception);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    protected abstract String jdbcUrl();

    protected void configure(HikariConfig config) {
    }

    protected StorageSettings settings() {
        return settings;
    }

    private void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS devskill_schema (version INTEGER NOT NULL)");
        }
        int version = schemaVersion(connection);
        if (version > CURRENT_SCHEMA_VERSION) {
            throw new SQLException("Unsupported DevSkills schema version " + version);
        }
        if (version < 1) {
            migrateToVersion1(connection);
            writeSchemaVersion(connection, 1);
            version = 1;
        }
        if (version < 2) {
            migrateToVersion2(connection);
            writeSchemaVersion(connection, 2);
        }
    }

    private int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT MAX(version) AS version FROM devskill_schema")) {
            if (!result.next()) {
                return 0;
            }
            int version = result.getInt("version");
            return result.wasNull() ? 0 : version;
        }
    }

    private void migrateToVersion1(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS devskill_users (uuid VARCHAR(36) PRIMARY KEY, locale VARCHAR(16), sounds BOOLEAN, actionbar BOOLEAN, bossbar BOOLEAN, updated_at BIGINT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS devskill_skill_xp (uuid VARCHAR(36), skill_id VARCHAR(64), xp DOUBLE, PRIMARY KEY(uuid, skill_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS devskill_trait_grant (uuid VARCHAR(36), trait_id VARCHAR(64), amount INTEGER, PRIMARY KEY(uuid, trait_id))");
        }
    }

    private void migrateToVersion2(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS devskill_xp_booster (id VARCHAR(36) PRIMARY KEY, booster_scope VARCHAR(16) NOT NULL, target_uuid VARCHAR(36), multiplier DOUBLE NOT NULL, label VARCHAR(255) NOT NULL, creator_uuid VARCHAR(36), starts_at BIGINT NOT NULL, expires_at BIGINT)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS devskill_player_identity (uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16) NOT NULL, player_name_lower VARCHAR(16) NOT NULL UNIQUE, updated_at BIGINT NOT NULL)");
        }
        createIndexIfMissing(connection, "devskill_xp_booster", "idx_devskill_booster_target", "target_uuid");
        createIndexIfMissing(connection, "devskill_xp_booster", "idx_devskill_booster_expiry", "expires_at");
    }

    private void writeSchemaVersion(Connection connection, int version) throws SQLException {
        try (Statement delete = connection.createStatement()) {
            delete.executeUpdate("DELETE FROM devskill_schema");
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO devskill_schema (version) VALUES (?)")) {
            insert.setInt(1, version);
            insert.executeUpdate();
        }
    }

    private void createIndexIfMissing(Connection connection, String table, String index, String column) throws SQLException {
        if (hasIndex(connection.getMetaData(), table, index)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE INDEX " + index + " ON " + table + " (" + column + ")");
        }
    }

    private boolean hasIndex(DatabaseMetaData metadata, String table, String index) throws SQLException {
        String catalog = metadata.getConnection().getCatalog();
        for (String tableName : List.of(table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT))) {
            try (ResultSet result = metadata.getIndexInfo(catalog, null, tableName, false, false)) {
                while (result.next()) {
                    if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private XpBoosterSnapshot readBooster(ResultSet result) throws SQLException {
        String target = result.getString("target_uuid");
        String creator = result.getString("creator_uuid");
        long expiry = result.getLong("expires_at");
        Optional<Instant> expiresAt = result.wasNull() ? Optional.empty() : Optional.of(Instant.ofEpochMilli(expiry));
        return new XpBoosterSnapshot(
                UUID.fromString(result.getString("id")),
                XpBoosterScope.valueOf(result.getString("booster_scope")),
                target == null ? Optional.empty() : Optional.of(UUID.fromString(target)),
                result.getDouble("multiplier"),
                result.getString("label"),
                creator == null ? Optional.empty() : Optional.of(UUID.fromString(creator)),
                Instant.ofEpochMilli(result.getLong("starts_at")),
                expiresAt
        );
    }

    private void setNullableUuid(PreparedStatement statement, int parameter, Optional<UUID> uuid) throws SQLException {
        if (uuid.isPresent()) {
            statement.setString(parameter, uuid.orElseThrow().toString());
        } else {
            statement.setNull(parameter, Types.VARCHAR);
        }
    }

    private String normalizePlayerName(String playerName) {
        String stripped = java.util.Objects.requireNonNull(playerName, "playerName").strip();
        if (stripped.isEmpty() || stripped.length() > 16) {
            throw new IllegalArgumentException("playerName must contain 1 to 16 characters");
        }
        return stripped.toLowerCase(Locale.ROOT);
    }

    private void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private UserSettings loadSettings(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT locale, sounds, actionbar, bossbar FROM devskill_users WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new UserSettings(
                            result.getBoolean("sounds"),
                            result.getBoolean("actionbar"),
                            result.getBoolean("bossbar"),
                            result.getString("locale")
                    );
                }
            }
        }
        return UserSettings.defaults();
    }

    private Map<String, Double> loadXp(Connection connection, UUID uuid) throws SQLException {
        Map<String, Double> xp = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT skill_id, xp FROM devskill_skill_xp WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    xp.put(result.getString("skill_id"), result.getDouble("xp"));
                }
            }
        }
        return xp;
    }

    private Map<String, Integer> loadManualTraits(Connection connection, UUID uuid) throws SQLException {
        Map<String, Integer> traits = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT trait_id, amount FROM devskill_trait_grant WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    traits.put(result.getString("trait_id"), result.getInt("amount"));
                }
            }
        }
        return traits;
    }

    private void deleteRows(Connection connection, String table, String column, String value) throws SQLException {
        String sql = "DELETE FROM " + table + " WHERE " + column + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.executeUpdate();
        }
    }

    private void insertUser(Connection connection, UserProfile profile) throws SQLException {
        String sql = "INSERT INTO devskill_users (uuid, locale, sounds, actionbar, bossbar, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.uuid().toString());
            statement.setString(2, profile.settings().locale());
            statement.setBoolean(3, profile.settings().sounds());
            statement.setBoolean(4, profile.settings().actionbar());
            statement.setBoolean(5, profile.settings().bossbar());
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void insertXp(Connection connection, UserProfile profile) throws SQLException {
        String sql = "INSERT INTO devskill_skill_xp (uuid, skill_id, xp) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Double> entry : profile.skillXp().entrySet()) {
                statement.setString(1, profile.uuid().toString());
                statement.setString(2, entry.getKey());
                statement.setDouble(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertTraits(Connection connection, UserProfile profile) throws SQLException {
        String sql = "INSERT INTO devskill_trait_grant (uuid, trait_id, amount) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : profile.manualTraitLevels().entrySet()) {
                statement.setString(1, profile.uuid().toString());
                statement.setString(2, entry.getKey());
                statement.setInt(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
