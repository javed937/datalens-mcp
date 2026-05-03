package com.datalens.mcp.tools;

import com.datalens.mcp.registry.ConnectionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class SchemaTools {

    private final ConnectionRegistry registry;
    private final ObjectMapper objectMapper;

    public SchemaTools(ConnectionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Tool(description = """
            List all tables and their column names for a registered database connection.
            Parameters:
              connectionId - the id of a registered connection
            Returns a JSON array where each entry has "table" and "columns" (list of column names).
            """)
    public String exploreSchema(String connectionId) {
        return registry.find(connectionId)
                .map(entry -> {
                    try {
                        List<Map<String, Object>> tables = entry.adapter().getTables();
                        if (tables.isEmpty()) {
                            return "No tables found in connection '" + connectionId + "'.";
                        }

                        List<Map<String, Object>> result = tables.stream().map(t -> {
                            String name = (String) t.get("name");
                            List<String> cols = entry.adapter().getColumns(name).stream()
                                    .map(c -> (String) c.get("name"))
                                    .toList();
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("table", name);
                            row.put("columns", cols);
                            return row;
                        }).toList();

                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
                    } catch (JsonProcessingException e) {
                        return "Error formatting schema: " + e.getMessage();
                    } catch (Exception e) {
                        return "Error exploring schema: " + e.getMessage();
                    }
                })
                .orElse("Error: no connection found with id '" + connectionId + "'.");
    }

    @Tool(description = """
            Get full column definitions for a specific table in a registered database.
            Parameters:
              connectionId - the id of a registered connection
              tableName    - name of the table to describe
            Returns JSON with table name and a list of columns (name, type, nullable, size).
            """)
    public String describeTable(String connectionId, String tableName) {
        return registry.find(connectionId)
                .map(entry -> {
                    try {
                        List<Map<String, Object>> columns = entry.adapter().getColumns(tableName);
                        if (columns.isEmpty()) {
                            return "No columns found for table '" + tableName
                                    + "'. Verify the table exists using exploreSchema.";
                        }

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("table", tableName);
                        result.put("columns", columns);
                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
                    } catch (JsonProcessingException e) {
                        return "Error formatting response: " + e.getMessage();
                    } catch (Exception e) {
                        return "Error describing table: " + e.getMessage();
                    }
                })
                .orElse("Error: no connection found with id '" + connectionId + "'.");
    }

    @Tool(description = """
            Find tables whose names match a pattern in a registered database.
            Parameters:
              connectionId - the id of a registered connection
              pattern      - SQL-style pattern: use % for any characters, _ for a single character.
                             Leave blank or use % to list all tables.
            Examples: "user%" finds tables starting with "user", "%order%" finds tables containing "order".
            """)
    public String findTables(String connectionId, String pattern) {
        return registry.find(connectionId)
                .map(entry -> {
                    try {
                        List<Map<String, Object>> tables = entry.adapter().getTables();

                        String effectivePattern = (pattern == null || pattern.isBlank()) ? "%" : pattern;
                        Pattern regex = toRegex(effectivePattern);

                        List<String> matched = tables.stream()
                                .map(t -> (String) t.get("name"))
                                .filter(name -> regex.matcher(name).matches())
                                .toList();

                        if (matched.isEmpty()) {
                            return "No tables found matching '" + effectivePattern + "'.";
                        }
                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(matched);
                    } catch (JsonProcessingException e) {
                        return "Error formatting response: " + e.getMessage();
                    } catch (Exception e) {
                        return "Error finding tables: " + e.getMessage();
                    }
                })
                .orElse("Error: no connection found with id '" + connectionId + "'.");
    }

    private Pattern toRegex(String sqlPattern) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : sqlPattern.toCharArray()) {
            switch (c) {
                case '%' -> regex.append(".*");
                case '_' -> regex.append(".");
                default  -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        regex.append("$");
        try {
            return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return Pattern.compile("^.*$");
        }
    }
}
