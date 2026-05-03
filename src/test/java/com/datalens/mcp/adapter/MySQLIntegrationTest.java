package com.datalens.mcp.adapter;

import com.datalens.mcp.model.QueryResult;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MySQLIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.3")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    static MySQLAdapter adapter;

    @BeforeAll
    static void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mysql.getJdbcUrl());
        config.setUsername(mysql.getUsername());
        config.setPassword(mysql.getPassword());
        adapter = new MySQLAdapter(config);

        adapter.jdbcTemplate.execute("""
                CREATE TABLE products (
                    id    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    sku   VARCHAR(50)  NOT NULL,
                    name  VARCHAR(200) NOT NULL,
                    price DECIMAL(10,2)
                )
                """);
        adapter.jdbcTemplate.execute("""
                INSERT INTO products (sku, name, price) VALUES
                    ('SKU-001', 'Widget A',  9.99),
                    ('SKU-002', 'Gadget B', 24.99),
                    ('SKU-003', 'Mystery',   NULL)
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
        assertThat(names).contains("products");
    }

    @Test
    void getColumns_returnsExpectedSchema() {
        List<Map<String, Object>> cols = adapter.getColumns("products");
        List<String> names = cols.stream().map(c -> (String) c.get("name")).toList();
        assertThat(names).containsExactlyInAnyOrder("id", "sku", "name", "price");
    }

    @Test
    void executeQuery_returnsRows() {
        QueryResult result = adapter.executeQuery("SELECT id, sku FROM products ORDER BY id", 100, 10);
        assertThat(result.rowCount()).isEqualTo(3);
        assertThat(result.columns()).containsExactly("id", "sku");
    }

    @Test
    void executeQuery_returnsEmptyResultForNoRows() {
        QueryResult result = adapter.executeQuery(
                "SELECT * FROM products WHERE sku = 'NONE'", 100, 10);
        assertThat(result.rowCount()).isEqualTo(0);
        assertThat(result.rows()).isEmpty();
    }

    @Test
    void executeQuery_respectsMaxRows() {
        QueryResult result = adapter.executeQuery("SELECT * FROM products", 1, 10);
        assertThat(result.rowCount()).isEqualTo(1);
    }
}
