package com.datalens.mcp.tools;

import com.datalens.mcp.adapter.AdapterFactory;
import com.datalens.mcp.adapter.DatabaseAdapter;
import com.datalens.mcp.model.ConnectionConfig;
import com.datalens.mcp.model.DatabaseType;
import com.datalens.mcp.registry.ConnectionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ConnectionTools {

    private final ConnectionRegistry registry;
    private final AdapterFactory adapterFactory;
    private final ObjectMapper objectMapper;

    public ConnectionTools(ConnectionRegistry registry, AdapterFactory adapterFactory, ObjectMapper objectMapper) {
        this.registry = registry;
        this.adapterFactory = adapterFactory;
        this.objectMapper = objectMapper;
    }

    @Tool(description = """
            Register a new database connection so it can be queried.
            Parameters:
              id       - unique identifier for this connection (e.g. "prod-db")
              type     - database type: SQLITE, POSTGRESQL, or MYSQL
              jdbcUrl  - JDBC connection URL (e.g. "jdbc:sqlite:/path/to/file.db" or "jdbc:sqlite::memory:")
              name     - human-readable label for this connection
            """)
    public String registerConnection(String id, String type, String jdbcUrl, String name) {
        if (registry.exists(id)) {
            return "Error: connection id '" + id + "' already exists. Choose a different id or remove it first.";
        }

        DatabaseType dbType;
        try {
            dbType = DatabaseType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Error: unknown database type '" + type + "'. Supported values: SQLITE, POSTGRESQL, MYSQL.";
        }

        DatabaseAdapter adapter;
        try {
            adapter = adapterFactory.create(dbType, jdbcUrl);
        } catch (UnsupportedOperationException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            return "Error creating adapter: " + e.getMessage();
        }

        if (!adapter.testConnection()) {
            adapter.close();
            return "Error: could not connect to the database. Check that the JDBC URL is correct: " + jdbcUrl;
        }

        registry.register(new ConnectionConfig(id, name, dbType, jdbcUrl), adapter);
        return "Connection '" + id + "' registered successfully. Name: " + name + ", Type: " + dbType + ".";
    }

    @Tool(description = "List all registered database connections. Returns id, name, and type for each.")
    public String listConnections() {
        List<ConnectionConfig> all = registry.listAll();
        if (all.isEmpty()) {
            return "No connections registered. Use registerConnection to add one.";
        }

        List<Map<String, String>> summary = all.stream()
                .map(c -> Map.of("id", c.id(), "name", c.name(), "type", c.type().name()))
                .toList();

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            return "Error formatting response: " + e.getMessage();
        }
    }

    @Tool(description = "Test connectivity for a registered connection. Returns healthy or error details.")
    public String testConnection(String connectionId) {
        return registry.find(connectionId)
                .map(entry -> entry.adapter().testConnection()
                        ? "Connection '" + connectionId + "' is healthy."
                        : "Connection '" + connectionId + "' is NOT responding. Check if the database is running.")
                .orElse("Error: no connection found with id '" + connectionId + "'.");
    }

    @Tool(description = "Remove a registered connection and release all its resources.")
    public String removeConnection(String connectionId) {
        return registry.remove(connectionId)
                ? "Connection '" + connectionId + "' removed."
                : "Error: no connection found with id '" + connectionId + "'.";
    }
}
