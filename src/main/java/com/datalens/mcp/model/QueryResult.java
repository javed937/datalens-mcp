package com.datalens.mcp.model;

import java.util.List;

public record QueryResult(
        List<String> columns,
        List<List<Object>> rows,
        int rowCount,
        long durationMs
) {}
