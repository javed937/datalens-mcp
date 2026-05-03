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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExportToolsTest {

    @Mock private ConnectionRegistry registry;
    @Mock private DatabaseAdapter adapter;
    @Mock private AuditLogger auditLogger;

    private ExportTools tools;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(new AppProperties.Security(100, 10, 5000, false), new AppProperties.Demo(false));
        tools = new ExportTools(registry, new QuerySanitizer(), new QueryGuard(props),
                auditLogger, props, new ObjectMapper());
    }

    private ConnectionRegistry.ConnectionEntry entry() {
        return new ConnectionRegistry.ConnectionEntry(
                new ConnectionConfig("db1", "Test DB", DatabaseType.SQLITE, "jdbc:sqlite::memory:"),
                adapter
        );
    }

    private QueryResult twoRowResult() {
        // second row uses a mutable list because List.of() rejects null elements
        List<Object> bobRow = new java.util.ArrayList<>();
        bobRow.add(2);
        bobRow.add("Bob");
        bobRow.add(null);
        return new QueryResult(
                List.of("id", "name", "score"),
                List.of(List.of(1, "Alice", 95.5), bobRow),
                2, 5L
        );
    }

    // --- JSON format ---

    @Test
    void exportData_json_returnsArrayOfObjects() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(anyString(), anyInt(), anyInt())).thenReturn(twoRowResult());

        String result = tools.exportData("db1", "SELECT * FROM users", "json");

        assertThat(result).contains("\"name\" : \"Alice\"");
        assertThat(result).contains("\"id\" : 1");
        assertThat(result).contains("\"score\" : null");
    }

    @Test
    void exportData_json_isDefault_whenFormatIsNull() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(anyString(), anyInt(), anyInt())).thenReturn(twoRowResult());

        String result = tools.exportData("db1", "SELECT * FROM users", null);

        assertThat(result).startsWith("[");
    }

    // --- CSV format ---

    @Test
    void exportData_csv_hasHeaderAndRows() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(anyString(), anyInt(), anyInt())).thenReturn(twoRowResult());

        String result = tools.exportData("db1", "SELECT * FROM users", "csv");

        String[] lines = result.split("\n");
        assertThat(lines[0]).isEqualTo("id,name,score");
        assertThat(lines[1]).isEqualTo("1,Alice,95.5");
        assertThat(lines[2]).startsWith("2,Bob,");
    }

    @Test
    void exportData_csv_quotesFieldsWithCommas() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("name"),
                        List.of(List.of("Smith, John")),
                        1, 1L
                ));

        String result = tools.exportData("db1", "SELECT name FROM users", "csv");

        assertThat(result).contains("\"Smith, John\"");
    }

    @Test
    void exportData_csv_quotesFieldsWithEmbeddedQuotes() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(anyString(), anyInt(), anyInt()))
                .thenReturn(new QueryResult(
                        List.of("note"),
                        List.of(List.of("say \"hello\"")),
                        1, 1L
                ));

        String result = tools.exportData("db1", "SELECT note FROM t", "csv");

        assertThat(result).contains("\"say \"\"hello\"\"\"");
    }

    // --- Markdown format ---

    @Test
    void exportData_markdown_hasHeaderSeparatorAndRows() {
        when(registry.find("db1")).thenReturn(Optional.of(entry()));
        when(adapter.executeQuery(anyString(), anyInt(), anyInt())).thenReturn(twoRowResult());

        String result = tools.exportData("db1", "SELECT * FROM users", "markdown");

        assertThat(result).contains("| id");
        assertThat(result).contains("| name");
        assertThat(result).contains("| --");
        assertThat(result).contains("Alice");
    }

    // --- Error cases ---

    @Test
    void exportData_returnsError_forUnsupportedFormat() {
        String result = tools.exportData("db1", "SELECT 1", "xml");

        assertThat(result).contains("Error");
        assertThat(result).contains("unsupported format");
        verify(registry, never()).find(any());
    }

    @Test
    void exportData_blocksDropTable() {
        String result = tools.exportData("db1", "DROP TABLE users", "csv");

        assertThat(result).startsWith("Error:");
        verify(registry, never()).find(any());
        verify(auditLogger).logBlocked(eq("db1"), anyString(), anyString());
    }

    @Test
    void exportData_returnsError_whenConnectionNotFound() {
        when(registry.find("missing")).thenReturn(Optional.empty());

        String result = tools.exportData("missing", "SELECT 1", "json");

        assertThat(result).contains("no connection found");
    }
}
