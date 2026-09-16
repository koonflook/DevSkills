package com.Teenkung.devSkills.storage;

import com.Teenkung.devSkills.config.StorageSettings;
import com.zaxxer.hikari.HikariConfig;

public final class MySqlStorage extends JdbcStorageProvider {

    public MySqlStorage(StorageSettings settings) {
        super(settings);
    }

    @Override
    protected String jdbcUrl() {
        String ssl = settings().useSsl() ? "true" : "false";
        return "jdbc:mariadb://" + settings().host() + ":" + settings().port() + "/" + settings().database() + "?useSSL=" + ssl;
    }

    @Override
    protected void configure(HikariConfig config) {
        config.setDriverClassName(driverClassName());
        config.setUsername(settings().username());
        config.setPassword(settings().password());
    }

    private String driverClassName() {
        String relocated = "com.Teenkung.devSkills.libs.mariadb.jdbc.Driver";
        if (isPresent(relocated)) {
            return relocated;
        }
        return "org.mariadb.jdbc.Driver";
    }

    private boolean isPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
