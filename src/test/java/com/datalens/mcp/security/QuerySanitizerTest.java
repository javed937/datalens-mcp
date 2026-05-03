package com.datalens.mcp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuerySanitizerTest {

    private QuerySanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new QuerySanitizer();
    }

    @Test
    void sanitize_removesLineComment() {
        String result = sanitizer.sanitize("SELECT * FROM users -- drop everything");
        assertThat(result).doesNotContain("--");
        assertThat(result).doesNotContain("drop everything");
        assertThat(result).contains("SELECT * FROM users");
    }

    @Test
    void sanitize_removesBlockComment() {
        String result = sanitizer.sanitize("SELECT /* hidden injection */ * FROM users");
        assertThat(result).doesNotContain("/*");
        assertThat(result).doesNotContain("hidden injection");
        assertThat(result).contains("SELECT");
        assertThat(result).contains("FROM users");
    }

    @Test
    void sanitize_removesMultilineBlockComment() {
        String result = sanitizer.sanitize("SELECT *\n/* DROP TABLE users;\ndelete everything */\nFROM users");
        assertThat(result).doesNotContain("DROP TABLE");
        assertThat(result).contains("FROM users");
    }

    @Test
    void sanitize_removesMysqlLineComment() {
        String result = sanitizer.sanitize("SELECT * FROM users # injected comment");
        assertThat(result).doesNotContain("#");
        assertThat(result).doesNotContain("injected comment");
    }

    @Test
    void sanitize_removesSemicolon() {
        String result = sanitizer.sanitize("SELECT 1; DROP TABLE users");
        assertThat(result).doesNotContain(";");
    }

    @Test
    void sanitize_removesMultipleSemicolons() {
        String result = sanitizer.sanitize("SELECT 1; SELECT 2; SELECT 3");
        assertThat(result).doesNotContain(";");
    }

    @Test
    void sanitize_handlesNull() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_handlesEmptyString() {
        assertThat(sanitizer.sanitize("")).isEmpty();
    }

    @Test
    void sanitize_trimsWhitespace() {
        assertThat(sanitizer.sanitize("   SELECT 1   ")).isEqualTo("SELECT 1");
    }

    @Test
    void sanitize_preservesValidQuery() {
        String sql = "SELECT id, name FROM users WHERE age > 18 ORDER BY name";
        assertThat(sanitizer.sanitize(sql)).isEqualTo(sql);
    }

    @Test
    void sanitize_handlesNestedBlockComments() {
        // Outer /* */ should be stripped; the text inside is gone
        String result = sanitizer.sanitize("SELECT /* outer /* inner */ */ 1");
        assertThat(result).doesNotContain("outer");
        assertThat(result).doesNotContain("inner");
    }
}
