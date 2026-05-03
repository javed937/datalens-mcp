package com.datalens.mcp.tools;

import com.datalens.mcp.adapter.AdapterFactory;
import com.datalens.mcp.adapter.DatabaseAdapter;
import com.datalens.mcp.model.ConnectionConfig;
import com.datalens.mcp.model.DatabaseType;
import com.datalens.mcp.registry.ConnectionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionToolsTest {

    @Mock
    private ConnectionRegistry registry;

    @Mock
    private AdapterFactory adapterFactory;

    @Mock
    private DatabaseAdapter adapter;

    private ConnectionTools tools;

    @BeforeEach
    void setUp() {
        tools = new ConnectionTools(registry, adapterFactory, new ObjectMapper());
    }

    @Test
    void registerConnection_succeeds_whenIdIsNew() {
        when(registry.exists("mydb")).thenReturn(false);
        when(adapterFactory.create(DatabaseType.SQLITE, "jdbc:sqlite::memory:")).thenReturn(adapter);
        when(adapter.testConnection()).thenReturn(true);

        String result = tools.registerConnection("mydb", "SQLITE", "jdbc:sqlite::memory:", "My DB");

        assertThat(result).contains("registered successfully");
        verify(registry).register(any(ConnectionConfig.class), eq(adapter));
    }

    @Test
    void registerConnection_fails_whenIdAlreadyExists() {
        when(registry.exists("mydb")).thenReturn(true);

        String result = tools.registerConnection("mydb", "SQLITE", "jdbc:sqlite::memory:", "Duplicate");

        assertThat(result).contains("already exists");
        verify(registry, never()).register(any(), any());
    }

    @Test
    void registerConnection_fails_whenTypeIsUnknown() {
        when(registry.exists("mydb")).thenReturn(false);

        String result = tools.registerConnection("mydb", "ORACLE", "jdbc:oracle:thin:@host:1521:xe", "Oracle DB");

        assertThat(result).contains("unknown database type");
        verify(adapterFactory, never()).create(any(), any());
    }

    @Test
    void registerConnection_fails_whenConnectionTestFails() {
        when(registry.exists("mydb")).thenReturn(false);
        when(adapterFactory.create(DatabaseType.SQLITE, "jdbc:sqlite:/bad/path.db")).thenReturn(adapter);
        when(adapter.testConnection()).thenReturn(false);

        String result = tools.registerConnection("mydb", "SQLITE", "jdbc:sqlite:/bad/path.db", "Bad DB");

        assertThat(result).contains("could not connect");
        verify(adapter).close();
        verify(registry, never()).register(any(), any());
    }

    @Test
    void listConnections_returnsMessage_whenEmpty() {
        when(registry.listAll()).thenReturn(List.of());

        String result = tools.listConnections();

        assertThat(result).contains("No connections registered");
    }

    @Test
    void listConnections_returnsJson_whenConnectionsExist() {
        when(registry.listAll()).thenReturn(List.of(
                new ConnectionConfig("db1", "Test DB", DatabaseType.SQLITE, "jdbc:sqlite::memory:")
        ));

        String result = tools.listConnections();

        assertThat(result).contains("db1");
        assertThat(result).contains("SQLITE");
        assertThat(result).contains("Test DB");
    }

    @Test
    void testConnection_returnsHealthy_whenAdapterResponds() {
        var entry = new ConnectionRegistry.ConnectionEntry(
                new ConnectionConfig("db1", "Test", DatabaseType.SQLITE, "jdbc:sqlite::memory:"),
                adapter
        );
        when(registry.find("db1")).thenReturn(Optional.of(entry));
        when(adapter.testConnection()).thenReturn(true);

        String result = tools.testConnection("db1");

        assertThat(result).contains("healthy");
    }

    @Test
    void testConnection_returnsError_whenConnectionNotFound() {
        when(registry.find("missing")).thenReturn(Optional.empty());

        String result = tools.testConnection("missing");

        assertThat(result).contains("no connection found");
    }

    @Test
    void removeConnection_succeeds_whenConnectionExists() {
        when(registry.remove("db1")).thenReturn(true);

        String result = tools.removeConnection("db1");

        assertThat(result).contains("removed");
    }

    @Test
    void removeConnection_fails_whenConnectionNotFound() {
        when(registry.remove("missing")).thenReturn(false);

        String result = tools.removeConnection("missing");

        assertThat(result).contains("no connection found");
    }
}
