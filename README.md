# DataLens — Talk to Your Database in Plain English

## The Problem

Every team has databases. Most people on that team can't query them.

A product manager wants to know which customers haven't ordered in 90 days. A support agent needs the order history for a specific user. A founder wants to see revenue by product this week. None of them know SQL. So they ask the one developer who does — who is already busy, who has to context-switch, write the query, format the results, and send them back.

This is the daily reality at most companies:

- **Developers** spend hours writing one-off queries for non-technical colleagues
- **Analysts** waste time figuring out undocumented schemas before they can write a single line of SQL
- **Data teams** can't move fast because database access is bottlenecked through a few SQL experts
- **Everyone** copies raw data into spreadsheets because they don't know how to export it properly

The underlying databases have the answers. The bottleneck is the interface.

---

## The Solution

DataLens is an MCP server that connects Claude directly to your databases. Instead of writing SQL, your team just talks to Claude — and Claude talks to the database.

**Before DataLens** — a typical workflow:
```
PM asks dev → dev writes query → dev runs it → dev formats results → dev sends CSV
(takes hours, interrupts dev's flow, scales to zero when dev is on holiday)
```

**After DataLens** — the same workflow:
```
PM asks Claude → Claude queries the database → Claude explains the results
(takes seconds, works for anyone, available 24/7)
```

DataLens handles the hard parts: safe SQL generation, schema discovery, result formatting, credential management, and audit logging — all without exposing raw database access to anyone.

---

## See It In Action

DataLens ships with a built-in demo database — a SQLite in-memory database seeded with sample `users`, `products`, and `orders` tables. It's registered automatically when the server starts (connection id: `demo`). No Docker, no setup. Start the server and it's ready.

The demo data looks like this:

**users** — 5 rows
| id | name | email | created_at |
|---|---|---|---|
| 1 | Alice Smith | alice@example.com | ... |
| 2 | Bob Jones | NULL | ... |
| 3 | Carol White | carol@example.com | ... |
| 4 | David Brown | david@example.com | ... |
| 5 | Eve Davis | NULL | ... |

**products** — 3 rows
| id | sku | name | price | stock |
|---|---|---|---|---|
| 1 | SKU-001 | Widget A | 9.99 | 100 |
| 2 | SKU-002 | Gadget B | 24.99 | 50 |
| 3 | SKU-003 | Doohickey | NULL | 0 |

**orders** — 3 rows
| id | user_id | total | status | created_at |
|---|---|---|---|---|
| 1 | 1 | 9.99 | shipped | ... |
| 2 | 1 | 24.99 | pending | ... |
| 3 | 3 | 9.99 | delivered | ... |

---

### Scenario 1 — The product manager who needs customer insights

The old way: open a ticket, wait for a dev.

With DataLens:

```
You: Show me all customers and how much they've spent in total.

Claude: Here's the customer spend summary from the demo database:

  Alice Smith    — $34.98 (2 orders)
  Carol White    — $9.99  (1 order)
  Bob Jones      — $0.00  (no orders)
  David Brown    — $0.00  (no orders)
  Eve Davis      — $0.00  (no orders)

Alice is your highest-value customer. Three customers have never placed an order —
Bob, David, and Eve — which might be worth a re-engagement campaign.
```

Claude wrote and ran this query behind the scenes:
```sql
SELECT u.name,
       COUNT(o.id)      AS order_count,
       COALESCE(SUM(o.total), 0) AS total_spent
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
GROUP BY u.id, u.name
ORDER BY total_spent DESC
```

---

### Scenario 2 — The analyst who doesn't know the schema

New analyst joins. They don't know what tables exist, what the columns mean, or how they relate.

```
You: What tables are in the demo database and what do they contain?

Claude: The demo database has 3 tables:

  users    — customer records (id, name, email, created_at).
             Email is nullable — 2 of 5 users have no email on file.

  products — product catalog (id, sku, name, price, stock).
             Price is nullable — Doohickey (SKU-003) has no price set and 0 stock.
             Looks like a discontinued product.

  orders   — purchase history (id, user_id, total, status, created_at).
             Links to users via user_id. Status values seen: shipped, pending, delivered.
             No foreign key constraint defined — worth checking if user_id is always valid.

You: Are there any data quality issues?

Claude: A few things stand out:
  1. Two users (Bob Jones, Eve Davis) have no email — you can't contact them.
  2. SKU-003 has NULL price and 0 stock — is this intentional or a data entry gap?
  3. All 3 orders belong to only 2 of the 5 users — the other 3 have never ordered.
  4. No foreign key constraint on orders.user_id — data integrity relies on application logic.
```

---

### Scenario 3 — The support agent handling a customer complaint

```
You: A customer named Alice is asking about her orders. What can you tell me?

Claude: Alice Smith (alice@example.com) has 2 orders:

  Order #1 — $9.99  — Widget A  — Status: SHIPPED
  Order #2 — $24.99 — Gadget B  — Status: PENDING

Her shipped order has been fulfilled. Her second order is still pending —
that's likely the one she's asking about.
```

---

### Scenario 4 — Exporting data without opening a spreadsheet tool

```
You: Export all orders with customer names as a CSV.

Claude: Here's the CSV export:

  order_id,customer_name,total,status
  1,Alice Smith,9.99,shipped
  2,Alice Smith,24.99,pending
  3,Carol White,9.99,delivered
```

---

### Scenario 5 — A developer investigating a slow query

```
You: Explain this query on demo: SELECT * FROM orders WHERE user_id = 1

Claude: Query plan for the demo database:

  SCAN orders (~3 rows estimated)

  There's no index on orders.user_id. For this 3-row demo table that's fine,
  but at scale this would be a full table scan on every lookup by user.
  Recommendation: CREATE INDEX idx_orders_user_id ON orders(user_id)
```

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `mvnw` wrapper)
- Claude CLI (`claude`) or Claude Desktop

Docker is optional — only needed for PostgreSQL/MySQL integration tests.

### 1. Build

```bash
# Windows
.\mvnw.cmd package -DskipTests

# Linux / macOS
./mvnw package -DskipTests
```

Output: `target/datalens-mcp-1.0.0.jar`

### 2. Register with Claude CLI

```bash
# Read-only mode (default — SELECT/WITH only, safe for production)
claude mcp add datalens-mcp java -- -jar "C:\path\to\target\datalens-mcp-1.0.0.jar"

# Write mode (enables INSERT/UPDATE/DELETE)
claude mcp add datalens-mcp java -- -jar "C:\path\to\target\datalens-mcp-1.0.0.jar" --datalens.security.allow-write=true

# To remove the added mcp
claude mcp remove datalens-mcp

```

Verify:
```bash
claude mcp list
```

### 3. Try the demo database

The `demo` connection is registered automatically on startup. Open Claude by typing claude and try:

```
List all connections
Show me all tables in demo
Which users have placed orders?
Export the orders table as CSV
```

### 4. Connect your own database

```
Register a connection called "prod" as POSTGRESQL
at jdbc:postgresql://localhost:5432/myapp?user=readonly&password=secret
```

---

## Claude Desktop Setup

### Config File Location (Windows)

| Install type | Config path |
|---|---|
| Standard install | `%APPDATA%\Claude\claude_desktop_config.json` |
| Microsoft Store / packaged app | `C:\Users\<user>\AppData\Local\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Roaming\Claude\claude_desktop_config.json` |

### Config Contents

```json
{
  "mcpServers": {
    "datalens-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\Projects\\Remote\\Github\\datalens-mcp\\target\\datalens-mcp-1.0.0.jar"
      ]
    }
  }
}
```

Add `"--datalens.security.allow-write=true"` to `args` to enable write mode. Restart Claude Desktop after editing.

---

## JDBC URL Reference

| Database | URL Format |
|---|---|
| SQLite file | `jdbc:sqlite:C:/data/mydb.db` |
| SQLite in-memory | `jdbc:sqlite::memory:` |
| PostgreSQL | `jdbc:postgresql://localhost:5432/dbname?user=u&password=p` |
| MySQL | `jdbc:mysql://localhost:3306/dbname?user=u&password=p` |

---

## Local Development with Docker

```bash
cd docker
docker compose up -d
```

Starts PostgreSQL 16 (`localhost:5432`) and MySQL 8.3 (`localhost:3306`), both seeded with the same `users`/`products`/`orders` data as the demo database.

```
Register connection "pg" as POSTGRESQL
at jdbc:postgresql://localhost:5432/datalens?user=postgres&password=postgres
```

---

## Architecture

### How It Works

```
Claude (MCP Client)
      │
      │  JSON-RPC over stdio
      ▼
DataLens MCP Server (Spring Boot JAR)
      │
      ├── ConnectionRegistry  — in-memory map of registered connections
      ├── QueryGuard          — SQL allow-list + keyword blocklist
      ├── QuerySanitizer      — strips comments and semicolon injection
      ├── AuditLogger         — logs every query to stderr
      │
      ├── PostgresAdapter  ──▶  HikariCP pool ──▶ PostgreSQL
      ├── MySQLAdapter     ──▶  HikariCP pool ──▶ MySQL
      └── SQLiteAdapter    ──▶  DriverManager  ──▶ SQLite file / in-memory
```

Claude never has direct database access. Every query goes through DataLens, which enforces safety controls before touching the database.

### Tools Exposed to Claude

| Tool | What it does |
|---|---|
| `registerConnection` | Register a DB connection by name, type, and JDBC URL |
| `listConnections` | List all registered connections and their status |
| `testConnection` | Ping a database to verify it's reachable |
| `removeConnection` | Deregister a connection and close its pool |
| `exploreSchema` | List all tables and columns in a database |
| `describeTable` | Get column types, nullability, and indexes for one table |
| `executeQuery` | Run a SELECT with all safety controls applied |
| `getTableStats` | Row count, size on disk, null counts per column |
| `explainQuery` | Get EXPLAIN output for a query |
| `exportData` | Export query results as CSV, JSON, or Markdown |
| `findTables` | Search for tables by name pattern across all connections |

### Prompts

| Prompt | What it does |
|---|---|
| `schema-overview` | Full schema walkthrough — tables, relationships, recommendations |
| `data-quality-check` | Reviews a table for nulls, duplicates, and anomalies |
| `query-helper` | Generates and validates SQL for a natural language request |

---

## Security

### Why It's Safe to Connect to Production

The biggest concern with giving any tool database access is: what happens if it goes wrong?

DataLens is built read-only by default. Every query goes through a three-layer safety pipeline:

**Layer 1 — QuerySanitizer**
Strips SQL comments and semicolons before the query reaches any validation logic. Prevents tricks like `SELECT 1; DROP TABLE users` being smuggled past the keyword check.

**Layer 2 — QueryGuard**
```java
// Only SELECT and WITH queries are allowed through
if (!normalized.startsWith("SELECT") && !normalized.startsWith("WITH")) {
    throw new QueryBlockedException("Only SELECT and WITH queries are allowed");
}

// These keywords are blocked even inside a SELECT
Set.of("DROP", "DELETE", "UPDATE", "INSERT", "TRUNCATE",
       "ALTER", "CREATE", "GRANT", "REVOKE", "EXEC", "EXECUTE")
```

**Layer 3 — Database-level controls**
- Row cap: every query gets `LIMIT N` appended (default 1000)
- Query timeout: JDBC `Statement.setQueryTimeout()` kills runaway queries
- Query length cap: oversized queries are rejected before parsing

**Credential safety:** JDBC URLs (which may contain passwords) are stored in memory only — never written to disk, never returned in tool responses. Claude only ever sees connection IDs and display names.

**Audit log:** every `executeQuery` call writes a structured entry to stderr:
```json
{"event": "QUERY_EXECUTED", "connectionId": "prod", "rowsReturned": 47, "durationMs": 23, "blocked": false}
{"event": "QUERY_BLOCKED",  "connectionId": "prod", "reason": "Blocked keyword: DROP"}
```

---

## Configuration Reference

```yaml
datalens:
  security:
    max-rows: 1000          # Hard cap on results returned per query
    query-timeout-sec: 30   # Kill queries that run longer than this
    max-query-length: 10000 # Reject queries longer than this many characters
    allow-write: false      # Set true to allow INSERT/UPDATE/DELETE/DROP
  demo:
    enabled: true           # Auto-register the seeded SQLite demo database on startup

spring:
  main:
    web-application-type: none  # MCP uses stdio — no HTTP server needed
    banner-mode: off            # Required: banner must not write to stdout (MCP protocol uses stdout)
  ai:
    mcp:
      server:
        name: datalens-mcp
        version: 1.0.0
```

Override any setting at runtime:
```bash
java -jar datalens-mcp-1.0.0.jar \
  --datalens.security.allow-write=true \
  --datalens.security.max-rows=5000 \
  --datalens.demo.enabled=false
```

---

## Project Structure

```
datalens-mcp/
├── src/main/java/com/datalens/mcp/
│   ├── DataLensMcpApplication.java
│   ├── adapter/
│   │   ├── DatabaseAdapter.java        # Interface for all DB backends
│   │   ├── AbstractJdbcAdapter.java    # Shared JDBC logic (executeQuery, schema metadata)
│   │   ├── SQLiteAdapter.java          # DriverManagerDataSource; no-op close
│   │   ├── PostgresAdapter.java        # HikariCP pool; closes on deregister
│   │   ├── MySQLAdapter.java           # HikariCP pool; catalog() scoped to connected DB
│   │   └── AdapterFactory.java         # Picks the right adapter by DatabaseType
│   ├── config/
│   │   ├── AppProperties.java          # @ConfigurationProperties(prefix="datalens")
│   │   ├── McpServerConfig.java        # ToolCallbackProvider beans
│   │   └── PromptConfig.java           # 3 SyncPromptSpecification beans
│   ├── demo/
│   │   └── DemoDataSeeder.java         # Seeds in-memory SQLite with users/products/orders
│   ├── exception/
│   │   ├── DataLensException.java
│   │   ├── ConnectionException.java
│   │   └── QueryBlockedException.java
│   ├── health/
│   │   ├── ConnectionHealthIndicator.java
│   │   └── StartupHealthLogger.java
│   ├── model/
│   │   ├── ConnectionConfig.java       # record: id, name, type, jdbcUrl
│   │   ├── DatabaseType.java           # Enum: SQLITE, POSTGRESQL, MYSQL
│   │   └── QueryResult.java            # record: columns, rows, rowCount, durationMs
│   ├── registry/
│   │   └── ConnectionRegistry.java     # ConcurrentHashMap<id, ConnectionEntry>
│   ├── security/
│   │   ├── QuerySanitizer.java
│   │   ├── QueryGuard.java
│   │   └── AuditLogger.java
│   └── tools/
│       ├── ConnectionTools.java
│       ├── QueryTools.java
│       ├── SchemaTools.java
│       ├── StatsTools.java
│       ├── ExplainTools.java
│       └── ExportTools.java
├── docker/
│   ├── docker-compose.yml              # PostgreSQL 16 + MySQL 8.3
│   └── init/01-seed.sql               # Same schema as the demo database
├── .github/workflows/ci.yml
├── pom.xml
└── README.md
```

---

## Testing

```bash
# Unit tests only (no Docker needed)
.\mvnw.cmd test

# Unit + integration tests (requires Docker Desktop)
.\mvnw.cmd verify
```

| Test class | Count | What it covers |
|---|---|---|
| `QueryGuardTest` | 38 | Every blocked keyword, injection patterns, allowWrite mode |
| `QuerySanitizerTest` | 11 | Comment stripping, semicolon injection |
| `ConnectionToolsTest` | 10 | Register, list, test, remove connections |
| `QueryToolsTest` | 10 | executeQuery with mocked adapter |
| `SchemaToolsTest` | 12 | exploreSchema, describeTable, findTables |
| `StatsToolsTest` | 7 | getTableStats |
| `ExplainToolsTest` | 7 | explainQuery with db-aware prefix |
| `ExportToolsTest` | 9 | CSV, JSON, Markdown export |
| `DemoDataSeederTest` | 7 | Demo DB seeding, JOIN queries, disabled mode |
| `ConnectionHealthIndicatorTest` | 5 | UP/DOWN/mixed/exception health states |
| `SecurityPipelinePerfTest` | 3 | Timing assertions on the security pipeline |
| `PostgresIntegrationTest` | 6 | Real PostgreSQL via Testcontainers |
| `MySQLIntegrationTest` | 6 | Real MySQL via Testcontainers |
| **Total** | **131** | 12 Testcontainers tests skipped without Docker |

---

## CI/CD

Every push and pull request runs the full build on GitHub Actions:

```yaml
- uses: actions/setup-java@v4
  with: { java-version: '21', distribution: 'temurin' }
- run: ./mvnw verify
- uses: actions/upload-artifact@v4
  with: { name: datalens-mcp-jar, path: target/datalens-mcp-*.jar }
```

Docker is available in the Actions runner — Testcontainers integration tests run in CI with no extra setup.

---

## Troubleshooting

### `Unexpected token '.', " . ____ "... is not valid JSON`

Spring Boot's startup banner is printing to stdout. MCP uses stdout for JSON-RPC, so any non-JSON output corrupts the protocol stream. The fix is already applied (`banner-mode: off` in `application.yml`) — if you see this, check that the setting is present.

### `Invalid environment variable format: java`

When using `claude mcp add` with the `-e` flag, the `-e` parser consumes arguments that follow it. Pass write mode as a Spring Boot argument after the JAR path instead:

```bash
# Wrong
claude mcp add datalens-mcp -e ALLOW_WRITE=true java -- -jar datalens-mcp-1.0.0.jar

# Correct
claude mcp add datalens-mcp java -- -jar datalens-mcp-1.0.0.jar --datalens.security.allow-write=true
```

### Claude Desktop config location not found (Windows)

If `%APPDATA%\Claude\` doesn't exist, Claude Desktop was installed from the Microsoft Store. Use:
```
C:\Users\<YourUsername>\AppData\Local\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Roaming\Claude\claude_desktop_config.json
```

### Connection refused on PostgreSQL or MySQL

```bash
docker ps                          # verify containers are running
docker compose -f docker/docker-compose.yml up -d   # start them if not
```

Then use `test connection <id>` in Claude to confirm DataLens can reach the database.
