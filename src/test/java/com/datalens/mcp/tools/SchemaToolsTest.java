package com.datalens.mcp.tools;

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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaToolsTest {

    @Mock
    private ConnectionRegistry registry;

    @Mock
    private DatabaseAdapter adapter;

    private SchemaTools tools;

    @BeforeEach
    void setUp() {
        tools = new SchemaTools(registry, new ObjectMapper());
    }

    private ConnectionRegistry.ConnectionEntry entry() {
        return new ConnectionRegistry.ConnectionEntry(
                new ConnectionConfig("db1", "Test DB", DatabaseType.SQLITE, "jdbc:sqlite::memory:"),
                adapter
        );
    }

    // --- exploreSchema ---

    @Test
    void exploreSchema_returnsTablesWithColumns() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getTables()).thenReturn(List.of(
                Map.of("name", "users"),
                Map.of("name", "orders")
        ));
        when(adapter.getColumns("users")).thenReturn(List.of(
                Map.of("name", "id", "type", "INTEGER", "nullable", false, "size", 0),
                Map.of("name", "email", "type", "TEXT", "nullable", true, "size", 255)
        ));
        when(adapter.getColumns("orders")).thenReturn(List.of(
                Map.of("name", "id", "type", "INTEGER", "nullable", false, "size", 0),
                Map.of("name", "user_id", "type", "INTEGER", "nullable", false, "size", 0)
        ));

        String result = tools.exploreSchema("db1");

        assertThat(result).contains("users");
        assertThat(result).contains("orders");
        assertThat(result).contains("email");
        assertThat(result).contains("user_id");
    }

    @Test
    void exploreSchema_returnsMessage_whenNoTables() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getTables()).thenReturn(List.of());

        String result = tools.exploreSchema("db1");

        assertThat(result).contains("No tables found");
    }

    @Test
    void exploreSchema_returnsError_whenConnectionNotFound() {
        when(registry.find("missing")).thenReturn(Optional.empty());

        String result = tools.exploreSchema("missing");

        assertThat(result).contains("no connection found");
    }

    // --- describeTable ---

    @Test
    void describeTable_returnsColumnDetails() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getColumns("users")).thenReturn(List.of(
                Map.of("name", "id", "type", "INTEGER", "nullable", false, "size", 0),
                Map.of("name", "name", "type", "TEXT", "nullable", true, "size", 200),
                Map.of("name", "email", "type", "TEXT", "nullable", false, "size", 255)
        ));

        String result = tools.describeTable("db1", "users");

        assertThat(result).contains("users");
        assertThat(result).contains("INTEGER");
        assertThat(result).contains("email");
        assertThat(result).contains("nullable");
    }

    @Test
    void describeTable_returnsMessage_whenTableNotFound() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getColumns("ghost")).thenReturn(List.of());

        String result = tools.describeTable("db1", "ghost");

        assertThat(result).contains("No columns found");
        assertThat(result).contains("ghost");
    }

    @Test
    void describeTable_returnsError_whenConnectionNotFound() {
        when(registry.find("missing")).thenReturn(Optional.empty());

        String result = tools.describeTable("missing", "users");

        assertThat(result).contains("no connection found");
    }

    // --- findTables ---

    @Test
    void findTables_returnsAllTables_whenPatternIsBlank() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getTables()).thenReturn(List.of(
                Map.of("name", "users"),
                Map.of("name", "orders"),
                Map.of("name", "products")
        ));

        String result = tools.findTables("db1", "");

        assertThat(result).contains("users");
        assertThat(result).contains("orders");
        assertThat(result).contains("products");
    }

    @Test
    void findTables_filtersByPrefixPattern() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getTables()).thenReturn(List.of(
                Map.of("name", "users"),
                Map.of("name", "user_roles"),
                Map.of("name", "orders")
        ));

        String result = tools.findTables("db1", "user%");

        assertThat(result).contains("users");
        assertThat(result).contains("user_roles");
        assertThat(result).doesNotContain("orders");
    }

    @Test
    void findTables_filtersByContainsPattern() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getTables()).thenReturn(List.of(
                Map.of("name", "order_items"),
                Map.of("name", "orders"),
                Map.of("name", "users")
        ));

        String result = tools.findTables("db1", "%order%");

        assertThat(result).contains("order_items");
        assertThat(result).contains("orders");
        assertThat(result).doesNotContain("users");
    }

    @Test
    void findTables_isCaseInsensitive() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getTables()).thenReturn(List.of(Map.of("name", "Users")));

        String result = tools.findTables("db1", "user%");

        assertThat(result).contains("Users");
    }

    @Test
    void findTables_returnsMessage_whenNoMatch() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.getTables()).thenReturn(List.of(Map.of("name", "users")));

        String result = tools.findTables("db1", "invoice%");

        assertThat(result).contains("No tables found");
    }

    @Test
    void findTables_returnsError_whenConnectionNotFound() {
        when(registry.find("missing")).thenReturn(Optional.empty());

        String result = tools.findTables("missing", "%");

        assertThat(result).contains("no connection found");
    }
}
