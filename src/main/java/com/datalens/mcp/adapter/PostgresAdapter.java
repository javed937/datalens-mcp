package com.datalens.mcp.adapter;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class PostgresAdapter extends AbstractJdbcAdapter {

    private final HikariDataSource dataSource;

    public PostgresAdapter(String jdbcUrl) {
        this(defaultConfig(jdbcUrl));
    }

    PostgresAdapter(HikariConfig config) {
        this(new HikariDataSource(config));
    }

    private PostgresAdapter(HikariDataSource ds) {
        super(ds);
        this.dataSource = ds;
    }

    private static HikariConfig defaultConfig(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setPoolName("datalens-pg");
        return config;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
