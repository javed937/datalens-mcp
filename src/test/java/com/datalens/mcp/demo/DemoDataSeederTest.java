package com.datalens.mcp.demo;

import com.datalens.mcp.adapter.AdapterFactory;
import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.model.QueryResult;
import com.datalens.mcp.registry.ConnectionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataSeederTest {

    private ConnectionRegistry registry;
    private DemoDataSeeder     seeder;

    @BeforeEach
    void setUp() {
        registry = new ConnectionRegistry();
        AppProperties props = new AppProperties(
                new AppProperties.Security(1000, 30, 10000, false),
                new AppProperties.Demo(true));
        seeder = new DemoDataSeeder(props, registry, new AdapterFactory());
        seeder.seed();
    }

    @AfterEach
    void tearDown() {
        seeder.cleanup();
    }

    @Test
    void seed_registersConnectionWithDemoId() {
        assertThat(registry.exists(DemoDataSeeder.DEMO_ID)).isTrue();
    }

    @Test
    void seed_usersTableHasFiveRows() {
        QueryResult result = query("SELECT * FROM users");
        assertThat(result.rowCount()).isEqualTo(5);
    }

    @Test
    void seed_productsTableHasThreeRows() {
        QueryResult result = query("SELECT * FROM products");
        assertThat(result.rowCount()).isEqualTo(3);
    }

    @Test
    void seed_ordersTableHasThreeRows() {
        QueryResult result = query("SELECT * FROM orders");
        assertThat(result.rowCount()).isEqualTo(3);
    }

    @Test
    void seed_usersHaveExpectedColumns() {
        QueryResult result = query("SELECT * FROM users LIMIT 1");
        assertThat(result.columns()).containsExactlyInAnyOrder("id", "name", "email", "created_at");
    }

    @Test
    void seed_ordersJoinUsersReturnsData() {
        QueryResult result = query("""
                SELECT u.name, o.status, o.total
                FROM orders o
                JOIN users u ON u.id = o.user_id""");
        assertThat(result.rowCount()).isEqualTo(3);
    }

    @Test
    void seed_disabledSkipsRegistration() {
        ConnectionRegistry emptyRegistry = new ConnectionRegistry();
        AppProperties disabled = new AppProperties(
                new AppProperties.Security(1000, 30, 10000, false),
                new AppProperties.Demo(false));
        DemoDataSeeder disabledSeeder = new DemoDataSeeder(disabled, emptyRegistry, new AdapterFactory());
        disabledSeeder.seed();

        assertThat(emptyRegistry.listAll()).isEmpty();
        disabledSeeder.cleanup();
    }

    private QueryResult query(String sql) {
        return registry.find(DemoDataSeeder.DEMO_ID)
                .orElseThrow()
                .adapter()
                .executeQuery(sql, 1000, 30);
    }
}
