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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExplainToolsTest {

    @Mock private ConnectionRegistry registry;
    @Mock private DatabaseAdapter adapter;
    @Mock private AuditLogger auditLogger;

    private ExplainTools tools;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(new AppProperties.Security(100, 10, 5000, false), new AppProperties.Demo(false));
        tools = new ExplainTools(registry, new QuerySanitizer(), new QueryGuard(props), auditLogger, props);
    }

    private ConnectionRegistry.ConnectionEntry entry(DatabaseType type) {
        return new ConnectionRegistry.ConnectionEntry(
                new ConnectionConfig("db1", "Test DB", type, "jdbc:sqlite::memory:"),
                adapter
        );
    }

    @Test
    void explainQuery_prependsExplainQueryPlanForSQLite() {
        when(registry.find("db1")).thenReturn(Optional.of(entry(DatabaseType.SQLITE)));
        when(adapter.executeQuery(startsWith("EXPLAIN QUERY PLAN"), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("id", "parent", "notused", "detail"),
                        List.of(List.of(2, 0, 0, "SCAN users")),
                        1, 3L
                ));

        String result = tools.explainQuery("db1", "SELECT * FROM users");

        assertThat(result).contains("SCAN users");
    }

    @Test
    void explainQuery_prependsExplainForPostgres() {
        when(registry.find("db1")).thenReturn(Optional.of(entry(DatabaseType.POSTGRESQL)));
        when(adapter.executeQuery(startsWith("EXPLAIN "), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("QUERY PLAN"),
                        List.of(List.of("Seq Scan on users  (cost=0.00..1.01 rows=1 width=8)")),
                        1, 2L
                ));

        String result = tools.explainQuery("db1", "SELECT * FROM users");

        assertThat(result).contains("Seq Scan");
    }

    @Test
    void explainQuery_prependsExplainForMySQL() {
        when(registry.find("db1")).thenReturn(Optional.of(entry(DatabaseType.MYSQL)));
        when(adapter.executeQuery(startsWith("EXPLAIN "), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("id", "select_type", "table"),
                        List.of(List.of(1, "SIMPLE", "users")),
                        1, 2L
                ));

        String result = tools.explainQuery("db1", "SELECT * FROM users");

        assertThat(result).contains("SIMPLE");
    }

    @Test
    void explainQuery_returnsError_whenConnectionNotFound() {
        when(registry.find("missing")).thenReturn(Optional.empty());

        String result = tools.explainQuery("missing", "SELECT 1");

        assertThat(result).contains("no connection found");
    }

    @Test
    void explainQuery_blocksDropTable() {
        String result = tools.explainQuery("db1", "DROP TABLE users");

        assertThat(result).startsWith("Error:");
        verify(registry, never()).find(any());
        verify(auditLogger).logBlocked(eq("db1"), anyString(), anyString());
    }

    @Test
    void explainQuery_stripsComments() {
        when(registry.find("db1")).thenReturn(Optional.of(entry(DatabaseType.SQLITE)));
        when(adapter.executeQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(new QueryResult(List.of("detail"), List.of(List.of("SCAN users")), 1, 1L));

        // Comment should be stripped; the remaining SELECT is valid
        String result = tools.explainQuery("db1", "SELECT * FROM users -- injected comment");

        assertThat(result).doesNotContain("Error:");
    }

    @Test
    void explainQuery_formatsResultAsTable() {
        when(registry.find("db1")).thenReturn(Optional.of(entry(DatabaseType.SQLITE)));
        when(adapter.executeQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("col1", "col2"),
                        List.of(List.of("val1", "val2")),
                        1, 1L
                ));

        String result = tools.explainQuery("db1", "SELECT 1");

        assertThat(result).contains("col1");
        assertThat(result).contains("val1");
        assertThat(result).contains("|");
        assertThat(result).contains("-");
    }
}
