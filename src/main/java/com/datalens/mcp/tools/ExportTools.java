package com.datalens.mcp.tools;

import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.exception.QueryBlockedException;
import com.datalens.mcp.model.QueryResult;
import com.datalens.mcp.registry.ConnectionRegistry;
import com.datalens.mcp.security.AuditLogger;
import com.datalens.mcp.security.QueryGuard;
import com.datalens.mcp.security.QuerySanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ExportTools {

    private final ConnectionRegistry registry;
    private final QuerySanitizer sanitizer;
    private final QueryGuard guard;
    private final AuditLogger auditLogger;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public ExportTools(ConnectionRegistry registry,
                       QuerySanitizer sanitizer,
                       QueryGuard guard,
                       AuditLogger auditLogger,
                       AppProperties appProperties,
                       ObjectMapper objectMapper) {
        this.registry      = registry;
        this.sanitizer     = sanitizer;
        this.guard         = guard;
        this.auditLogger   = auditLogger;
        this.appProperties = appProperties;
        this.objectMapper  = objectMapper;
    }

    @Tool(description = """
            Execute a SQL SELECT query and return results in a specific format.
            Parameters:
              connectionId - the id of a registered connection
              sql          - a SELECT query to execute (read-only)
              format       - output format: "csv", "json", or "markdown"
            Results are capped at the server's configured row limit.
            CSV output includes a header row and properly quoted fields.
            Markdown output renders as a formatted table.
            JSON output is an array of objects keyed by column name.
            """)
    public String exportData(String connectionId, String sql, String format) {
        String clean;
        try {
            clean = sanitizer.sanitize(sql);
            guard.validate(clean);
        } catch (QueryBlockedException e) {
            auditLogger.logBlocked(connectionId, sql, e.getMessage());
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            return "Error validating query: " + e.getMessage();
        }

        String fmt = (format == null || format.isBlank()) ? "json" : format.strip().toLowerCase();
        if (!fmt.equals("csv") && !fmt.equals("json") && !fmt.equals("markdown")) {
            return "Error: unsupported format '" + format + "'. Use csv, json, or markdown.";
        }

        return registry.find(connectionId)
                .map(entry -> {
                    try {
                        QueryResult result = entry.adapter().executeQuery(
                                clean,
                                appProperties.security().maxRows(),
                                appProperties.security().queryTimeoutSec());
                        auditLogger.logQuery(connectionId, clean, result.rowCount(), result.durationMs());
                        return switch (fmt) {
                            case "csv"      -> toCsv(result);
                            case "markdown" -> toMarkdown(result);
                            default         -> toJson(result);
                        };
                    } catch (Exception e) {
                        return "Error exporting data: " + e.getMessage();
                    }
                })
                .orElse("Error: no connection found with id '" + connectionId + "'.");
    }

    // --- formatters ---

    private String toCsv(QueryResult result) {
        if (result.rows().isEmpty()) {
            return String.join(",", result.columns().stream().map(this::csvQuote).toList()) + "\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(result.columns().stream().map(this::csvQuote).collect(Collectors.joining(","))).append("\n");
        for (List<Object> row : result.rows()) {
            sb.append(row.stream()
                    .map(v -> csvQuote(v == null ? "" : v.toString()))
                    .collect(Collectors.joining(","))).append("\n");
        }
        return sb.toString();
    }

    private String csvQuote(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String toMarkdown(QueryResult result) {
        if (result.columns().isEmpty()) {
            return "_No data_";
        }
        int[] widths = IntStream.range(0, result.columns().size())
                .map(i -> Math.max(
                        result.columns().get(i).length(),
                        result.rows().stream()
                                .mapToInt(r -> r.get(i) == null ? 4 : r.get(i).toString().length())
                                .max().orElse(0)))
                .toArray();

        StringBuilder sb = new StringBuilder();

        // header
        sb.append("|");
        for (int i = 0; i < result.columns().size(); i++) {
            sb.append(" ").append(String.format("%-" + widths[i] + "s", result.columns().get(i))).append(" |");
        }
        sb.append("\n");

        // separator
        sb.append("|");
        for (int w : widths) {
            sb.append(" ").append("-".repeat(w)).append(" |");
        }
        sb.append("\n");

        // rows
        for (List<Object> row : result.rows()) {
            sb.append("|");
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i) == null ? "NULL" : row.get(i).toString();
                sb.append(" ").append(String.format("%-" + widths[i] + "s", cell)).append(" |");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String toJson(QueryResult result) throws JsonProcessingException {
        List<Map<String, Object>> records = result.rows().stream().map(row -> {
            Map<String, Object> record = new LinkedHashMap<>();
            for (int i = 0; i < result.columns().size(); i++) {
                record.put(result.columns().get(i), row.get(i));
            }
            return record;
        }).toList();
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(records);
    }
}
