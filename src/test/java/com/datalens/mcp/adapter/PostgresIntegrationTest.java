package com.datalens.mcp.adapter;

import com.datalens.mcp.model.QueryResult;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    static PostgresAdapter adapter;

    @BeforeAll
    static void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        adapter = new PostgresAdapter(config);

        adapter.jdbcTemplate.execute("""
                CREATE TABLE users (
                    id    BIGSERIAL    PRIMARY KEY,
                    name  VARCHAR(100) NOT NULL,
                    email VARCHAR(255)
                )
                """);
        adapter.jdbcTemplate.execute("""
                INSERT INTO users (name, email) VALUES
                    ('Alice', 'alice@example.com'),
                    ('Bob',   NULL)
                """);
    }

    @AfterAll
    static void tearDown() {
        if (adapter != null) adapter.close();
    }

    @Test
    void testConnection_returnsTrue() {
        assertThat(adapter.testConnection()).isTrue();
    }

    @Test
    void getTables_includesSeededTable() {
        List<Map<String, Object>> tables = adapter.getTables();
        List<String> names = tables.stream().map(t -> (String) t.get("name")).toList();
        assertThat(names).contains("users");
    }

    @Test
    void getColumns_returnsExpectedSchema() {
        List<Map<String, Object>> cols = adapter.getColumns("users");
        List<String> names = cols.stream().map(c -> (String) c.get("name")).toList();
        assertThat(names).containsExactlyInAnyOrder("id", "name", "email");
    }

    @Test
    void executeQuery_returnsRows() {
        QueryResult result = adapter.executeQuery("SELECT id, name FROM users ORDER BY id", 100, 10);
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.columns()).containsExactly("id", "name");
    }

    @Test
    void executeQuery_returnsEmptyResultForNoRows() {
        QueryResult result = adapter.executeQuery(
                "SELECT * FROM users WHERE name = 'nobody'", 100, 10);
        assertThat(result.rowCount()).isEqualTo(0);
        assertThat(result.rows()).isEmpty();
    }

    @Test
    void executeQuery_respectsMaxRows() {
        QueryResult result = adapter.executeQuery("SELECT * FROM users", 1, 10);
        assertThat(result.rowCount()).isEqualTo(1);
    }
}
