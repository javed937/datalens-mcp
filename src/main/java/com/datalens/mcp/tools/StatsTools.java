package com.datalens.mcp.tools;

import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.model.QueryResult;
import com.datalens.mcp.registry.ConnectionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StatsTools {

    private static final Pattern SAFE_NAME = Pattern.compile("[a-zA-Z0-9_]+");

    private final ConnectionRegistry registry;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public StatsTools(ConnectionRegistry registry, AppProperties appProperties, ObjectMapper objectMapper) {
        this.registry = registry;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Tool(description = """
            Get row count and per-column null statistics for a table in a registered database.
            Parameters:
              connectionId - the id of a registered connection
              tableName    - name of the table to analyse
            Returns JSON with rowCount and a columns array showing nullCount and nullPct for each column.
            """)
    public String getTableStats(String connectionId, String tableName) {
        if (!SAFE_NAME.matcher(tableName).matches()) {
            return "Error: table name '" + tableName
                    + "' contains invalid characters. Only letters, digits, and underscores are allowed.";
        }

        return registry.find(connectionId)
                .map(entry -> {
                    try {
                        List<Map<String, Object>> columns = entry.adapter().getColumns(tableName);
                        if (columns.isEmpty()) {
                            return "Table '" + tableName + "' not found or has no columns.";
                        }

                        int timeout = appProperties.security().queryTimeoutSec();

                        // Build a single query: COUNT(*) + SUM(CASE WHEN col IS NULL) per column
                        String nullSums = columns.stream()
                                .map(c -> "SUM(CASE WHEN \"" + c.get("name") + "\" IS NULL THEN 1 ELSE 0 END)")
                                .collect(Collectors.joining(", "));

                        String statsQuery = "SELECT COUNT(*) AS total, " + nullSums + " FROM " + tableName;
                        QueryResult statsResult = entry.adapter().executeQuery(statsQuery, 1, timeout);

                        long rowCount = ((Number) statsResult.rows().get(0).get(0)).longValue();

                        List<Map<String, Object>> columnStats = new ArrayList<>();
                        for (int i = 0; i < columns.size(); i++) {
                            Map<String, Object> col = columns.get(i);
                            long nullCount = ((Number) statsResult.rows().get(0).get(i + 1)).longValue();
                            double nullPct = rowCount == 0 ? 0.0
                                    : Math.round(nullCount * 100.0 / rowCount * 10) / 10.0;

                            Map<String, Object> stat = new LinkedHashMap<>();
                            stat.put("column", col.get("name"));
                            stat.put("type", col.get("type"));
                            stat.put("nullCount", nullCount);
                            stat.put("nullPct", nullPct + "%");
                            columnStats.add(stat);
                        }

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("table", tableName);
                        result.put("rowCount", rowCount);
                        result.put("columns", columnStats);

                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
                    } catch (JsonProcessingException e) {
                        return "Error formatting response: " + e.getMessage();
                    } catch (Exception e) {
                        return "Error getting stats for '" + tableName + "': " + e.getMessage();
                    }
                })
                .orElse("Error: no connection found with id '" + connectionId + "'.");
    }
}
