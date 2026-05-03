package com.datalens.mcp.adapter;

import com.datalens.mcp.exception.DataLensException;
import com.datalens.mcp.model.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractJdbcAdapter implements DatabaseAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractJdbcAdapter.class);

    protected final JdbcTemplate jdbcTemplate;

    protected AbstractJdbcAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /**
     * Returns the catalog name used to scope metadata queries.
     * Null means no catalog filter (correct for SQLite and PostgreSQL).
     * MySQL overrides this to return conn.getCatalog() so metadata is scoped
     * to the connected database rather than all databases on the server.
     */
    protected String catalog(Connection conn) throws Exception {
        return null;
    }

    @Override
    public QueryResult executeQuery(String sql, int maxRows, int timeoutSeconds) {
        long start = System.currentTimeMillis();

        jdbcTemplate.setMaxRows(maxRows);
        jdbcTemplate.setQueryTimeout(timeoutSeconds);

        List<Map<String, Object>> rawRows = jdbcTemplate.queryForList(sql);
        long duration = System.currentTimeMillis() - start;

        if (rawRows.isEmpty()) {
            return new QueryResult(List.of(), List.of(), 0, duration);
        }

        List<String> columns = new ArrayList<>(rawRows.get(0).keySet());
        List<List<Object>> data = rawRows.stream()
                .map(row -> columns.stream().map(row::get).toList())
                .toList();

        log.debug("Query returned {} rows in {}ms", data.size(), duration);
        return new QueryResult(columns, data, data.size(), duration);
    }

    @Override
    public boolean testConnection() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.warn("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> getTables() {
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            String cat = catalog(conn);
            ResultSet rs = conn.getMetaData().getTables(cat, null, "%", new String[]{"TABLE"});
            List<Map<String, Object>> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(Map.of("name", rs.getString("TABLE_NAME")));
            }
            rs.close();
            return tables;
        } catch (Exception e) {
            throw new DataLensException("Failed to retrieve table list: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> getColumns(String tableName) {
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            String cat = catalog(conn);
            ResultSet rs = conn.getMetaData().getColumns(cat, null, tableName, "%");
            List<Map<String, Object>> columns = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> col = new LinkedHashMap<>();
                col.put("name", rs.getString("COLUMN_NAME"));
                col.put("type", rs.getString("TYPE_NAME"));
                col.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                col.put("size", rs.getInt("COLUMN_SIZE"));
                columns.add(col);
            }
            rs.close();
            return columns;
        } catch (Exception e) {
            throw new DataLensException(
                    "Failed to retrieve columns for table '" + tableName + "': " + e.getMessage(), e);
        }
    }
}
