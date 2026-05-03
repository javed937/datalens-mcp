package com.datalens.mcp.tools;

import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.model.DatabaseType;
import com.datalens.mcp.model.QueryResult;
import com.datalens.mcp.registry.ConnectionRegistry;
import com.datalens.mcp.exception.QueryBlockedException;
import com.datalens.mcp.security.AuditLogger;
import com.datalens.mcp.security.QueryGuard;
import com.datalens.mcp.security.QuerySanitizer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ExplainTools {

    private final ConnectionRegistry registry;
    private final QuerySanitizer sanitizer;
    private final QueryGuard guard;
    private final AuditLogger auditLogger;
    private final AppProperties appProperties;

    public ExplainTools(ConnectionRegistry registry,
                        QuerySanitizer sanitizer,
                        QueryGuard guard,
                        AuditLogger auditLogger,
                        AppProperties appProperties) {
        this.registry     = registry;
        this.sanitizer    = sanitizer;
        this.guard        = guard;
        this.auditLogger  = auditLogger;
        this.appProperties = appProperties;
    }

    @Tool(description = """
            Show the database execution plan for a SQL query without running it.
            Uses EXPLAIN QUERY PLAN for SQLite, EXPLAIN for MySQL, EXPLAIN for PostgreSQL.
            Parameters:
              connectionId - the id of a registered connection
              sql          - the SELECT query to explain (must be read-only)
            Returns the query plan as a formatted text table.
            """)
    public String explainQuery(String connectionId, String sql) {
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

        return registry.find(connectionId)
                .map(entry -> {
                    try {
                        DatabaseType type = entry.config().type();
                        String explainSql = explainPrefix(type) + clean;
                        int timeout = appProperties.security().queryTimeoutSec();
                        QueryResult result = entry.adapter().executeQuery(explainSql, 500, timeout);
                        auditLogger.logQuery(connectionId, explainSql, result.rowCount(), result.durationMs());
                        return formatPlan(result);
                    } catch (Exception e) {
                        return "Error explaining query: " + e.getMessage();
                    }
                })
                .orElse("Error: no connection found with id '" + connectionId + "'.");
    }

    private static String explainPrefix(DatabaseType type) {
        return switch (type) {
            case SQLITE     -> "EXPLAIN QUERY PLAN ";
            case POSTGRESQL -> "EXPLAIN ";
            case MYSQL      -> "EXPLAIN ";
        };
    }

    private String formatPlan(QueryResult result) {
        if (result.rows().isEmpty()) {
            return "(empty plan)";
        }
        int[] widths = IntStream.range(0, result.columns().size())
                .map(i -> Math.max(
                        result.columns().get(i).length(),
                        result.rows().stream()
                                .mapToInt(r -> r.get(i) == null ? 4 : r.get(i).toString().length())
                                .max().orElse(0)))
                .toArray();

        String separator = IntStream.of(widths)
                .mapToObj(w -> "-".repeat(w + 2))
                .collect(Collectors.joining("+", "+", "+"));

        StringBuilder sb = new StringBuilder();
        sb.append(separator).append("\n");
        sb.append(formatRow(result.columns().stream().map(Object.class::cast).toList(), widths)).append("\n");
        sb.append(separator).append("\n");
        for (var row : result.rows()) {
            sb.append(formatRow(row, widths)).append("\n");
        }
        sb.append(separator);
        return sb.toString();
    }

    private String formatRow(java.util.List<Object> values, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < values.size(); i++) {
            String cell = values.get(i) == null ? "NULL" : values.get(i).toString();
            sb.append(" ").append(String.format("%-" + widths[i] + "s", cell)).append(" |");
        }
        return sb.toString();
    }
}
