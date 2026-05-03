package com.datalens.mcp.health;

import com.datalens.mcp.adapter.DatabaseAdapter;
import com.datalens.mcp.model.ConnectionConfig;
import com.datalens.mcp.model.DatabaseType;
import com.datalens.mcp.registry.ConnectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionHealthIndicatorTest {

    @Mock
    private DatabaseAdapter adapter;

    private ConnectionRegistry registry;
    private ConnectionHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        registry = new ConnectionRegistry();
        indicator = new ConnectionHealthIndicator(registry);
    }

    @Test
    void health_isUp_whenNoConnectionsRegistered() {
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("connections");
    }

    @Test
    void health_isUp_whenAllConnectionsHealthy() {
        when(adapter.testConnection()).thenReturn(true);
        registry.register(config("db1"), adapter);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("db1", "UP");
    }

    @Test
    void health_isDown_whenAnyConnectionFails() {
        when(adapter.testConnection()).thenReturn(false);
        registry.register(config("db1"), adapter);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("db1", "DOWN");
    }

    @Test
    void health_showsPerConnectionStatus_withMixedConnections() {
        DatabaseAdapter good = org.mockito.Mockito.mock(DatabaseAdapter.class);
        DatabaseAdapter bad  = org.mockito.Mockito.mock(DatabaseAdapter.class);
        when(good.testConnection()).thenReturn(true);
        when(bad.testConnection()).thenReturn(false);

        registry.register(config("good-db"), good);
        registry.register(config("bad-db"),  bad);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("good-db", "UP");
        assertThat(health.getDetails()).containsEntry("bad-db",  "DOWN");
    }

    @Test
    void health_isDown_whenAdapterThrows() {
        when(adapter.testConnection()).thenThrow(new RuntimeException("connection refused"));
        registry.register(config("flaky-db"), adapter);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("flaky-db", "DOWN");
    }

    private static ConnectionConfig config(String id) {
        return new ConnectionConfig(id, "Test DB", DatabaseType.SQLITE, "jdbc:sqlite::memory:");
    }
}
