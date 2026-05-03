package com.datalens.mcp.perf;

import com.datalens.mcp.config.AppProperties;
import com.datalens.mcp.security.QueryGuard;
import com.datalens.mcp.security.QuerySanitizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SecurityPipelinePerfTest {

    private static final QuerySanitizer SANITIZER = new QuerySanitizer();
    private static final QueryGuard GUARD = new QueryGuard(
            new AppProperties(new AppProperties.Security(1000, 30, 10_000, false), new AppProperties.Demo(false)));

    @Test
    void sanitizer_handles10kQueriesUnder2Seconds() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            SANITIZER.sanitize("SELECT id, name FROM users -- comment\n WHERE id = " + i);
        }
        assertThat(System.currentTimeMillis() - start)
                .as("sanitizer should process 10k queries under 2s")
                .isLessThan(2_000);
    }

    @Test
    void guard_validates10kSelectsUnder2Seconds() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10_000; i++) {
            assertDoesNotThrow(() ->
                    GUARD.validate("SELECT id, email FROM orders WHERE total > 0"));
        }
        assertThat(System.currentTimeMillis() - start)
                .as("guard should validate 10k selects under 2s")
                .isLessThan(2_000);
    }

    @Test
    void fullPipeline_handles5kQueriesUnder2Seconds() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5_000; i++) {
            String clean = SANITIZER.sanitize(
                    "SELECT u.id, u.name, o.total /* join */ FROM users u JOIN orders o ON u.id = o.user_id WHERE u.id = " + i);
            assertDoesNotThrow(() -> GUARD.validate(clean));
        }
        assertThat(System.currentTimeMillis() - start)
                .as("full sanitize+guard pipeline should handle 5k queries under 2s")
                .isLessThan(2_000);
    }
}
