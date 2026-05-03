package com.datalens.mcp.adapter;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;

public class MySQLAdapter extends AbstractJdbcAdapter {

    private final HikariDataSource dataSource;

    public MySQLAdapter(String jdbcUrl) {
        this(defaultConfig(jdbcUrl));
    }

    MySQLAdapter(HikariConfig config) {
        this(new HikariDataSource(config));
    }

    private MySQLAdapter(HikariDataSource ds) {
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
        config.setPoolName("datalens-mysql");
        return config;
    }

    /**
     * MySQL's DatabaseMetaData treats the catalog as the database name.
     * Without this, getTables() and getColumns() enumerate every database
     * visible to the user, not just the connected one.
     */
    @Override
    protected String catalog(Connection conn) throws Exception {
        return conn.getCatalog();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
