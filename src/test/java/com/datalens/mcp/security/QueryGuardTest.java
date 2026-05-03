package com.datalens.mcp.security;

import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.exception.QueryBlockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class QueryGuardTest {

    private QueryGuard guard;

    @BeforeEach
    void setUp() {
        guard = new QueryGuard(new AppProperties(
                new AppProperties.Security(1000, 30, 10000, false), new AppProperties.Demo(false)));
    }

    // --- Valid queries ---

    @Test
    void validate_passes_simpleSelect() {
        assertDoesNotThrow(() -> guard.validate("SELECT * FROM users"));
    }

    @Test
    void validate_passes_selectWithJoin() {
        assertDoesNotThrow(() -> guard.validate(
                "SELECT u.id, u.name FROM users u JOIN orders o ON u.id = o.user_id"));
    }

    @Test
    void validate_passes_withCte() {
        assertDoesNotThrow(() -> guard.validate(
                "WITH recent AS (SELECT * FROM orders WHERE created > '2024-01-01') SELECT * FROM recent"));
    }

    @Test
    void validate_passes_caseInsensitiveSelect() {
        assertDoesNotThrow(() -> guard.validate("select id from users"));
    }

    // --- Statement-type blocking ---

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM users",
            "UPDATE users SET name = 'x'",
            "INSERT INTO users VALUES (1, 'test')",
            "DROP TABLE users",
            "TRUNCATE TABLE users",
            "ALTER TABLE users ADD COLUMN x INT",
            "CREATE TABLE foo (id INT)",
            "GRANT ALL ON users TO public",
            "REVOKE ALL ON users FROM public",
            "EXEC sp_help",
            "EXECUTE sp_help"
    })
    void validate_blocks_nonSelectStatements(String sql) {
        assertThatThrownBy(() -> guard.validate(sql))
                .isInstanceOf(QueryBlockedException.class);
    }

    // --- Keyword injection inside a SELECT ---

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM users WHERE 1=1 UNION ALL DELETE FROM users",
            "SELECT * FROM users WHERE id IN (SELECT id FROM t) DROP TABLE users",
            "SELECT * FROM (UPDATE users SET name='x' RETURNING id) sub",
            "SELECT * FROM users WHERE name = 'x' INSERT INTO log VALUES (1)",
            "SELECT TRUNCATE(price, 2) FROM products WHERE 1=1 TRUNCATE TABLE products",
            "SELECT * FROM users WHERE 1=0 CREATE TABLE pwned (id INT)",
            "SELECT * FROM users GRANT ALL ON users TO attacker",
            "SELECT * FROM users REVOKE ALL ON users FROM app",
            "SELECT * FROM users EXEC xp_cmdshell 'whoami'",
            "SELECT * FROM users EXECUTE xp_cmdshell 'whoami'",
            "SELECT * FROM users ALTER TABLE users DROP COLUMN name"
    })
    void validate_blocks_keywordInjectionInsideSelect(String sql) {
        assertThatThrownBy(() -> guard.validate(sql))
                .isInstanceOf(QueryBlockedException.class);
    }

    // --- Edge cases ---

    @Test
    void validate_blocks_emptyQuery() {
        assertThatThrownBy(() -> guard.validate(""))
                .isInstanceOf(QueryBlockedException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void validate_blocks_blankQuery() {
        assertThatThrownBy(() -> guard.validate("   "))
                .isInstanceOf(QueryBlockedException.class);
    }

    @Test
    void validate_blocks_nullQuery() {
        assertThatThrownBy(() -> guard.validate(null))
                .isInstanceOf(QueryBlockedException.class);
    }

    @Test
    void validate_blocks_queryExceedingMaxLength() {
        String longSql = "SELECT " + "x,".repeat(6000);
        assertThatThrownBy(() -> guard.validate(longSql))
                .isInstanceOf(QueryBlockedException.class)
                .hasMessageContaining("length");
    }

    @Test
    void validate_errorMessageContainsBlockedKeyword() {
        assertThatThrownBy(() -> guard.validate("DROP TABLE secrets"))
                .isInstanceOf(QueryBlockedException.class);
    }

    // --- allowWrite mode ---

    @Test
    void validate_allowsInsert_whenAllowWriteIsTrue() {
        QueryGuard writeGuard = new QueryGuard(new AppProperties(
                new AppProperties.Security(1000, 30, 10000, true), new AppProperties.Demo(false)));
        assertDoesNotThrow(() -> writeGuard.validate("INSERT INTO users VALUES (1, 'test')"));
    }

    @Test
    void validate_allowsDrop_whenAllowWriteIsTrue() {
        QueryGuard writeGuard = new QueryGuard(new AppProperties(
                new AppProperties.Security(1000, 30, 10000, true), new AppProperties.Demo(false)));
        assertDoesNotThrow(() -> writeGuard.validate("DROP TABLE users"));
    }

    @Test
    void validate_stillBlocksBlank_whenAllowWriteIsTrue() {
        QueryGuard writeGuard = new QueryGuard(new AppProperties(
                new AppProperties.Security(1000, 30, 10000, true), new AppProperties.Demo(false)));
        assertThatThrownBy(() -> writeGuard.validate(""))
                .isInstanceOf(QueryBlockedException.class);
    }

    // --- Mixed-case keyword detection ---

    @Test
    void validate_blocks_lowercaseKeywords() {
        assertThatThrownBy(() -> guard.validate("select * from users where 1=1 drop table users"))
                .isInstanceOf(QueryBlockedException.class);
    }

    @Test
    void validate_blocks_mixedCaseKeywords() {
        assertThatThrownBy(() -> guard.validate("SELECT * FROM users WHERE 1=1 Drop Table users"))
                .isInstanceOf(QueryBlockedException.class);
    }

    // --- Error message content ---

    @Test
    void validate_errorMessage_mentionsBlockedKeyword() {
        assertThatThrownBy(() -> guard.validate("SELECT * FROM users DROP TABLE users"))
                .isInstanceOf(QueryBlockedException.class)
                .hasMessageContaining("DROP");
    }

    @Test
    void validate_errorMessage_mentionsPermittedTypes_forNonSelect() {
        assertThatThrownBy(() -> guard.validate("CALL some_procedure()"))
                .isInstanceOf(QueryBlockedException.class)
                .hasMessageContaining("SELECT");
    }
}
