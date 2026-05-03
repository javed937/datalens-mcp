package com.datalens.mcp.adapter;

import com.datalens.mcp.model.QueryResult;

import java.util.List;
import java.util.Map;

public interface DatabaseAdapter {

    QueryResult executeQuery(String sql, int maxRows, int timeoutSeconds);

    boolean testConnection();

    void close();

    List<Map<String, Object>> getTables();

    List<Map<String, Object>> getColumns(String tableName);
}
