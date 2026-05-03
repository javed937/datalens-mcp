package com.datalens.mcp.security;

import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.exception.QueryBlockedException;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class QueryGuard {

    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE",
            "ALTER", "CREATE", "GRANT", "REVOKE", "EXEC", "EXECUTE"
    );

    private final AppProperties appProperties;

    public QueryGuard(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new QueryBlockedException("SQL query must not be empty");
        }

        if (sql.length() > appProperties.security().maxQueryLength()) {
            throw new QueryBlockedException(
                    "Query exceeds maximum allowed length of "
                    + appProperties.security().maxQueryLength() + " characters");
        }

        if (appProperties.security().allowWrite()) {
            return;
        }

        String normalized = sql.strip().toUpperCase();

        if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
            throw new QueryBlockedException(
                    "Only SELECT and WITH queries are permitted. Received: "
                    + normalized.substring(0, Math.min(20, normalized.length())));
        }

        for (String keyword : BLOCKED_KEYWORDS) {
            if (containsKeyword(normalized, keyword)) {
                throw new QueryBlockedException("Blocked keyword detected: " + keyword);
            }
        }
    }

    private boolean containsKeyword(String sql, String keyword) {
        return Pattern.compile("\\b" + keyword + "\\b").matcher(sql).find();
    }
}
