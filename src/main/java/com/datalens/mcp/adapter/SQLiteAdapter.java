package com.datalens.mcp.adapter;

import com.datalens.mcp.exception.ConnectionException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class SQLiteAdapter extends AbstractJdbcAdapter {

    public SQLiteAdapter(String jdbcUrl) {
        super(buildDataSource(jdbcUrl));
    }

    private static DriverManagerDataSource buildDataSource(String jdbcUrl) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new ConnectionException("SQLite JDBC driver not found on classpath", e);
        }
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(jdbcUrl);
        return ds;
    }

    @Override
    public void close() {
        // DriverManagerDataSource does not pool connections — nothing to close
    }
}
