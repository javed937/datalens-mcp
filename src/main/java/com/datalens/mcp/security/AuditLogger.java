package com.datalens.mcp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);

    private static final int MAX_SQL_LOG_LENGTH = 200;

    public void logQuery(String connectionId, String sql, int rowsReturned, long durationMs) {
        log.info("event=QUERY_EXECUTED connectionId={} rowsReturned={} durationMs={} sql={}",
                connectionId, rowsReturned, durationMs, truncate(sql));
    }

    public void logBlocked(String connectionId, String sql, String reason) {
        log.warn("event=QUERY_BLOCKED connectionId={} reason={} sql={}",
                connectionId, reason, truncate(sql));
    }

    private String truncate(String sql) {
        if (sql == null) return "";
        return sql.length() > MAX_SQL_LOG_LENGTH
                ? sql.substring(0, MAX_SQL_LOG_LENGTH) + "..."
                : sql;
    }
}
