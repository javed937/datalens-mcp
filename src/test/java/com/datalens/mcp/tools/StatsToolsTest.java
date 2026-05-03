package com.datalens.mcp.tools;

import com.datalens.mcp.adapter.DatabaseAdapter;
import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.model.ConnectionConfig;
import com.datalens.mcp.model.DatabaseType;
import com.datalens.mcp.model.QueryResult;
import com.datalens.mcp.registry.ConnectionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsToolsTest {

    @Mock
    private ConnectionRegistry registry;

    @Mock
    private DatabaseAdapter adapter;

    private StatsTools tools;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(new AppProperties.Security(100, 10, 5000, false), new AppProperties.Demo(false));
        tools = new StatsTools(registry, props, new ObjectMapper());
    }

    private ConnectionRegistry.ConnectionEntry entry() {
        return new ConnectionRegistry.ConnectionEntry(
                new ConnectionConfig("db1", "Test DB", DatabaseType.SQLITE, "jdbc:sqlite::memory:"),
                adapter
        );
    }

    @Test
    void getTableStats_returnsRowCountAndNullStats() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getColumns("users")).thenReturn(List.of(
                Map.of("name", "id",    "type", "INTEGER", "nullable", false, "size", 0),
                Map.of("name", "email", "type", "TEXT",    "nullable", true,  "size", 255)
        ));
        // Aggregated stats query: COUNT(*) = 10, null_id = 0, null_email = 3
        when(adapter.executeQuery(contains("COUNT(*)"), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("total", "null_id", "null_email"),
                        List.of(List.of(10L, 0L, 3L)),
                        1, 2L
                ));

        String result = tools.getTableStats("db1", "users");

        assertThat(result).contains("\"rowCount\" : 10");
        assertThat(result).contains("\"nullCount\" : 0");
        assertThat(result).contains("\"nullCount\" : 3");
        assertThat(result).contains("nullPct");
        assertThat(result).contains("30.0%");
    }

    @Test
    void getTableStats_handlesZeroRows() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getColumns("empty_table")).thenReturn(List.of(
                Map.of("name", "id", "type", "INTEGER", "nullable", false, "size", 0)
        ));
        when(adapter.executeQuery(contains("COUNT(*)"), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("total", "null_id"),
                        List.of(List.of(0L, 0L)),
                        1, 1L
                ));

        String result = tools.getTableStats("db1", "empty_table");

        assertThat(result).contains("\"rowCount\" : 0");
        assertThat(result).contains("0.0%");
    }

    @Test
    void getTableStats_returnsError_whenTableHasNoColumns() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getColumns("ghost")).thenReturn(List.of());

        String result = tools.getTableStats("db1", "ghost");

        assertThat(result).contains("not found");
    }

    @Test
    void getTableStats_returnsError_whenConnectionNotFound() {
        when(registry.find("missing")).thenReturn(Optional.empty());

        String result = tools.getTableStats("missing", "users");

        assertThat(result).contains("no connection found");
    }

    @Test
    void getTableStats_rejectsTableNameWithSpecialCharacters() {
        String result = tools.getTableStats("db1", "users; DROP TABLE users");

        assertThat(result).contains("Error");
        assertThat(result).contains("invalid characters");
    }

    @Test
    void getTableStats_rejectsTableNameWithDot() {
        String result = tools.getTableStats("db1", "schema.users");

        assertThat(result).contains("Error");
        assertThat(result).contains("invalid characters");
    }

    @Test
    void getTableStats_acceptsValidTableName() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getColumns("order_items_2024")).thenReturn(List.of(
                Map.of("name", "id", "type", "INTEGER", "nullable", false, "size", 0)
        ));
        when(adapter.executeQuery(contains("COUNT(*)"), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("total", "null_id"),
                        List.of(List.of(5L, 0L)),
                        1, 1L
                ));

        String result = tools.getTableStats("db1", "order_items_2024");

        assertThat(result).contains("order_items_2024");
        assertThat(result).contains("\"rowCount\" : 5");
    }
}
