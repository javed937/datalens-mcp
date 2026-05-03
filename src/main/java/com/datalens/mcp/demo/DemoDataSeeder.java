package com.datalens.mcp.demo;

import com.datalens.mcp.adapter.AdapterFactory;
import com.datalens.mcp.adapter.DatabaseAdapter;
import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.model.ConnectionConfig;
import com.datalens.mcp.model.DatabaseType;
import com.datalens.mcp.registry.ConnectionRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    // Named shared in-memory SQLite — all connections using this URI share the same DB.
    // At least one connection must stay open or SQLite destroys the database.
    static final String DEMO_ID  = "demo";
    static final String DEMO_URL = "jdbc:sqlite:file:datalens_demo?mode=memory&cache=shared";

    private final AppProperties      props;
    private final ConnectionRegistry registry;
    private final AdapterFactory     adapterFactory;

    private Connection anchor; // keeps the shared in-memory DB alive for the server lifetime

    public DemoDataSeeder(AppProperties props, ConnectionRegistry registry, AdapterFactory adapterFactory) {
        this.props         = props;
        this.registry      = registry;
        this.adapterFactory = adapterFactory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!props.demo().enabled()) {
            log.info("Demo database disabled (datalens.demo.enabled=false)");
            return;
        }
        try {
            Class.forName("org.sqlite.JDBC");
            anchor = DriverManager.getConnection(DEMO_URL);
            createSchema(anchor);
            insertData(anchor);

            DatabaseAdapter adapter = adapterFactory.create(DatabaseType.SQLITE, DEMO_URL);
            ConnectionConfig config = new ConnectionConfig(
                    DEMO_ID, "Demo Database (SQLite in-memory)", DatabaseType.SQLITE, DEMO_URL);
            registry.register(config, adapter);

            log.info("Demo database ready — connection id '{}' (tables: users, products, orders)", DEMO_ID);
        } catch (Exception e) {
            log.error("Failed to seed demo database: {}", e.getMessage(), e);
        }
    }

    private void createSchema(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        name       TEXT    NOT NULL,
                        email      TEXT,
                        created_at TEXT    NOT NULL DEFAULT (datetime('now'))
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS products (
                        id    INTEGER PRIMARY KEY AUTOINCREMENT,
                        sku   TEXT    NOT NULL UNIQUE,
                        name  TEXT    NOT NULL,
                        price REAL,
                        stock INTEGER NOT NULL DEFAULT 0
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id    INTEGER NOT NULL,
                        total      REAL,
                        status     TEXT    NOT NULL DEFAULT 'pending',
                        created_at TEXT    NOT NULL DEFAULT (datetime('now'))
                    )""");
        }
    }

    private void insertData(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO users (name, email) VALUES
                        ('Alice Smith', 'alice@example.com'),
                        ('Bob Jones',   NULL),
                        ('Carol White', 'carol@example.com'),
                        ('David Brown', 'david@example.com'),
                        ('Eve Davis',   NULL)""");
            stmt.execute("""
                    INSERT INTO products (sku, name, price, stock) VALUES
                        ('SKU-001', 'Widget A',  9.99,  100),
                        ('SKU-002', 'Gadget B',  24.99,  50),
                        ('SKU-003', 'Doohickey', NULL,    0)""");
            stmt.execute("""
                    INSERT INTO orders (user_id, total, status) VALUES
                        (1,  9.99, 'shipped'),
                        (1, 24.99, 'pending'),
                        (3,  9.99, 'delivered')""");
        }
    }

    @PreDestroy
    public void cleanup() {
        if (anchor != null) {
            try {
                anchor.close();
            } catch (Exception ignored) {}
        }
    }
}
