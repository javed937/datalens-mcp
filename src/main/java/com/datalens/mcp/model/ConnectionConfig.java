package com.datalens.mcp.model;

public record ConnectionConfig(
        String id,
        String name,
        DatabaseType type,
        String jdbcUrl
) {}
