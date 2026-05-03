package com.datalens.mcp.config;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class PromptConfig {

    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> prompts() {
        return List.of(schemaOverview(), dataQualityCheck(), queryHelper());
    }

    private McpServerFeatures.SyncPromptSpecification schemaOverview() {
        var prompt = new McpSchema.Prompt(
                "schema-overview",
                "Explore and summarize the full schema of a registered database connection.",
                List.of(new McpSchema.PromptArgument("connectionId", "ID of the registered database connection", true))
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            String connectionId = argAsString(request.arguments(), "connectionId");
            String text = """
                    You have access to a database via the datalens-mcp server.

                    Please do the following for connection '%s':
                    1. Call exploreSchema to list all tables and their columns.
                    2. For each table, call describeTable to understand the full column definitions.
                    3. Identify relationships between tables (foreign keys, naming conventions).
                    4. Produce a structured summary: purpose of each table, key columns, and a short entity-relationship description.
                    """.formatted(connectionId);
            return promptResult("Explore and summarise the database schema", text);
        });
    }

    private McpServerFeatures.SyncPromptSpecification dataQualityCheck() {
        var prompt = new McpSchema.Prompt(
                "data-quality-check",
                "Run a comprehensive data quality check on a specific table.",
                List.of(
                        new McpSchema.PromptArgument("connectionId", "ID of the registered database connection", true),
                        new McpSchema.PromptArgument("tableName", "Name of the table to inspect", true)
                )
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            String connectionId = argAsString(request.arguments(), "connectionId");
            String tableName    = argAsString(request.arguments(), "tableName");
            String text = """
                    You have access to a database via the datalens-mcp server.

                    Please perform a data quality check on table '%s' in connection '%s':
                    1. Call getTableStats to get row count and null statistics per column.
                    2. Call executeQuery to sample 20 rows and inspect the data.
                    3. Identify columns with high null rates (>20%%), suspicious values, or unexpected types.
                    4. Produce a concise data quality report: overall health score, per-column issues, and recommended fixes.
                    """.formatted(tableName, connectionId);
            return promptResult("Data quality check for table " + tableName, text);
        });
    }

    private McpServerFeatures.SyncPromptSpecification queryHelper() {
        var prompt = new McpSchema.Prompt(
                "query-helper",
                "Help write an optimised SQL query for a specific task.",
                List.of(
                        new McpSchema.PromptArgument("connectionId", "ID of the registered database connection", true),
                        new McpSchema.PromptArgument("task", "Plain-language description of the data you need", true)
                )
        );
        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            String connectionId = argAsString(request.arguments(), "connectionId");
            String task         = argAsString(request.arguments(), "task");
            String text = """
                    You have access to a database via the datalens-mcp server (connection: '%s').

                    Task: %s

                    Please do the following:
                    1. Call exploreSchema to understand the available tables and columns.
                    2. Call describeTable on any tables that look relevant.
                    3. Write a safe, read-only SQL query that satisfies the task.
                    4. Execute it with executeQuery and present the results clearly.
                    5. Explain what the query does and any caveats the user should know.
                    """.formatted(connectionId, task);
            return promptResult("Query helper: " + task, text);
        });
    }

    private static McpSchema.GetPromptResult promptResult(String description, String text) {
        return new McpSchema.GetPromptResult(
                description,
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text)))
        );
    }

    private static String argAsString(Map<String, Object> args, String key) {
        if (args == null) return "";
        Object v = args.get(key);
        return v == null ? "" : v.toString();
    }
}
