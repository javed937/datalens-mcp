-- Seed data shared by both PostgreSQL and MySQL containers
-- Used for local development; integration tests seed their own data inline

CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
    id          BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku         VARCHAR(50)    NOT NULL UNIQUE,
    name        VARCHAR(200)   NOT NULL,
    price       DECIMAL(10,2),
    stock       INT            NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS orders (
    id         BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    total      DECIMAL(10,2),
    status     VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (name, email) VALUES
    ('Alice Smith',   'alice@example.com'),
    ('Bob Jones',     NULL),
    ('Carol White',   'carol@example.com'),
    ('David Brown',   'david@example.com'),
    ('Eve Davis',     NULL);

INSERT INTO products (sku, name, price, stock) VALUES
    ('SKU-001', 'Widget A',  9.99,  100),
    ('SKU-002', 'Gadget B',  24.99,  50),
    ('SKU-003', 'Doohickey', NULL,    0);

INSERT INTO orders (user_id, total, status) VALUES
    (1,  9.99, 'shipped'),
    (1, 24.99, 'pending'),
    (3,  9.99, 'delivered');
