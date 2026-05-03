package com.datalens.mcp.adapter;

import com.datalens.mcp.model.DatabaseType;
import org.springframework.stereotype.Component;

@Component
public class AdapterFactory {

    public DatabaseAdapter create(DatabaseType type, String jdbcUrl) {
        return switch (type) {
            case SQLITE     -> new SQLiteAdapter(jdbcUrl);
            case POSTGRESQL -> new PostgresAdapter(jdbcUrl);
            case MYSQL      -> new MySQLAdapter(jdbcUrl);
        };
    }
}
