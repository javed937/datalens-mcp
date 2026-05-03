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
import java.util.Map;

@Service
public class QueryTools {

    private final ConnectionRegistry registry;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final QueryGuard queryGuard;
    private final QuerySanitizer querySanitizer;
    private final AuditLogger auditLogger;

    public QueryTools(ConnectionRegistry registry, AppProperties appProperties,
                      ObjectMapper objectMapper, QueryGuard queryGuard,
                      QuerySanitizer querySanitizer, AuditLogger auditLogger) {
        this.registry = registry;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.queryGuard = queryGuard;
        this.querySanitizer = querySanitizer;
        this.auditLogger = auditLogger;
    }

    @Tool(description = """
            Execute a SQL SELECT query against a registered database connection.
            Parameters:
              connectionId - the id of a registered connection (use listConnections to see available ones)
              sql          - a SQL SELECT statement to execute
            Returns results as JSON with columns, rows, rowCount, and execution time.
            Results are capped at the server's configured row limit (default 1000).
            Only SELECT and WITH queries are permitted unless the server is in write mode.
            """)
    public String executeQuery(String connectionId, String sql) {
        String sanitized = querySanitizer.sanitize(sql);

        try {
            queryGuard.validate(sanitized);
        } catch (QueryBlockedException e) {
            auditLogger.logBlocked(connectionId, sql, e.getMessage());
            return "Error: " + e.getMessage();
        }

        return registry.find(connectionId)
                .map(entry -> {
                    try {
                        QueryResult result = entry.adapter().executeQuery(
                                sanitized,
                                appProperties.security().maxRows(),
                                appProperties.security().queryTimeoutSec()
                        );
                        auditLogger.logQuery(connectionId, sanitized, result.rowCount(), result.durationMs());
                        return formatResult(result);
                    } catch (Exception e) {
                        return "Error executing query: " + e.getMessage();
                    }
                })
                .orElse("Error: no connection found with id '" + connectionId
                        + "'. Use listConnections to see available connections.");
    }

    private String formatResult(QueryResult result) {
        if (result.rowCount() == 0) {
            return "Query executed successfully. 0 rows returned. (" + result.durationMs() + "ms)";
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("rowCount", result.rowCount());
        output.put("durationMs", result.durationMs());
        output.put("columns", result.columns());
        output.put("rows", result.rows());

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (JsonProcessingException e) {
            return "Error formatting results: " + e.getMessage();
        }
    }
}
