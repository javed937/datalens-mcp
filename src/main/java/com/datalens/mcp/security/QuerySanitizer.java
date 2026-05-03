package com.datalens.mcp.security;

import org.springframework.stereotype.Component;

@Component
public class QuerySanitizer {

    public String sanitize(String sql) {
        if (sql == null) return null;

        String result = sql;

        // Strip block comments /* ... */ (dotall: matches across newlines, non-greedy)
        result = result.replaceAll("(?s)/\\*.*?\\*/", " ");

        // Strip line comments -- ...
        result = result.replaceAll("--[^\\n\\r]*", " ");

        // Strip MySQL-style inline comments # ...
        result = result.replaceAll("#[^\\n\\r]*", " ");

        // Remove semicolons — prevents multi-statement execution
        result = result.replace(";", "");

        // Collapse excess whitespace
        result = result.strip();

        return result;
    }
}
