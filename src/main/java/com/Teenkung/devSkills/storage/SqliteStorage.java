package com.Teenkung.devSkills.storage;

import com.Teenkung.devSkills.config.StorageSettings;
import com.zaxxer.hikari.HikariConfig;
import java.io.File;

public final class SqliteStorage extends JdbcStorageProvider {

    private final File dataFolder;

    public SqliteStorage(File dataFolder, StorageSettings settings) {
        super(settings);
        this.dataFolder = dataFolder;
    }

    @Override
    protected String jdbcUrl() {
        File databaseFile = new File(dataFolder, settings().sqliteFile());
        return "jdbc:sqlite:" + databaseFile.getAbsolutePath();
    }

    @Override
    protected void configure(HikariConfig config) {
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
    }
}
