package com.datalens.mcp.tools;

import com.datalens.mcp.adapter.DatabaseAdapter;
import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.model.ConnectionConfig;
import com.datalens.mcp.model.DatabaseType;
import com.datalens.mcp.model.QueryResult;
import com.datalens.mcp.registry.ConnectionRegistry;
import com.datalens.mcp.security.AuditLogger;
import com.datalens.mcp.security.QueryGuard;
import com.datalens.mcp.security.QuerySanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryToolsTest {

    @Mock
    private ConnectionRegistry registry;

    @Mock
    private DatabaseAdapter adapter;

    @Mock
    private AuditLogger auditLogger;

    private QueryTools tools;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(new AppProperties.Security(100, 10, 5000, false), new AppProperties.Demo(false));
        tools = new QueryTools(
                registry, props, new ObjectMapper(),
                new QueryGuard(props), new QuerySanitizer(), auditLogger
        );
    }

    private ConnectionRegistry.ConnectionEntry entry() {
        return new ConnectionRegistry.ConnectionEntry(
                new ConnectionConfig("db1", "Test DB", DatabaseType.SQLITE, "jdbc:sqlite::memory:"),
                adapter
        );
    }

    @Test
    void executeQuery_returnsResults_whenQuerySucceeds() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(eq("SELECT * FROM users"), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("id", "name"),
                        List.of(List.of(1, "Alice"), List.of(2, "Bob")),
                        2, 5L
                ));

        String result = tools.executeQuery("db1", "SELECT * FROM users");

        assertThat(result).contains("rowCount");
        assertThat(result).contains("Alice");
        assertThat(result).contains("name");
        verify(auditLogger).logQuery(eq("db1"), eq("SELECT * FROM users"), eq(2), anyLong());
    }

    @Test
    void executeQuery_returnsZeroRowsMessage_whenResultIsEmpty() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(eq("SELECT * FROM empty_table"), anyInt(), anyInt()))
                .thenReturn(new QueryResult(List.of(), List.of(), 0, 3L));

        String result = tools.executeQuery("db1", "SELECT * FROM empty_table");

        assertThat(result).contains("0 rows");
    }

    @Test
    void executeQuery_returnsError_whenConnectionNotFound() {
        when(registry.find("missing")).thenReturn(Optional.empty());

        String result = tools.executeQuery("missing", "SELECT 1");

        assertThat(result).contains("no connection found");
    }

    @Test
    void executeQuery_returnsError_whenAdapterThrows() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(eq("SELECT * FROM nonexistent"), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("no such table: nonexistent"));

        String result = tools.executeQuery("db1", "SELECT * FROM nonexistent");

        assertThat(result).contains("Error executing query");
        assertThat(result).contains("nonexistent");
    }

    @Test
    void executeQuery_blocksDropTable() {
        String result = tools.executeQuery("db1", "DROP TABLE users");

        assertThat(result).startsWith("Error:");
        verify(registry, never()).find(any());
        verify(auditLogger).logBlocked(eq("db1"), eq("DROP TABLE users"), anyString());
    }

    @Test
    void executeQuery_blocksDeleteStatement() {
        String result = tools.executeQuery("db1", "DELETE FROM users");

        assertThat(result).startsWith("Error:");
        verify(registry, never()).find(any());
        verify(auditLogger).logBlocked(eq("db1"), eq("DELETE FROM users"), anyString());
    }

    @Test
    void executeQuery_blocksInsertStatement() {
        String result = tools.executeQuery("db1", "INSERT INTO users VALUES (1, 'hack')");

        assertThat(result).startsWith("Error:");
        verify(registry, never()).find(any());
    }

    @Test
    void executeQuery_stripsLineCommentsBeforeValidation() {
        // After sanitization: "SELECT 1  " — valid, should reach the registry
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(new QueryResult(List.of("1"), List.of(List.of(1)), 1, 1L));

        String result = tools.executeQuery("db1", "SELECT 1 -- DROP TABLE users");

        assertThat(result).doesNotContain("Error:");
        verify(auditLogger, never()).logBlocked(any(), any(), any());
    }

    @Test
    void executeQuery_blocksSemicolonInjection_afterSanitization() {
        // Semicolon is stripped, but "DROP TABLE users" is still in the text → guard blocks it
        String result = tools.executeQuery("db1", "SELECT 1; DROP TABLE users");

        assertThat(result).startsWith("Error:");
        verify(auditLogger).logBlocked(eq("db1"), anyString(), anyString());
    }

    @Test
    void executeQuery_blocksEmptyQuery() {
        String result = tools.executeQuery("db1", "");

        assertThat(result).startsWith("Error:");
        verify(registry, never()).find(any());
    }
}
