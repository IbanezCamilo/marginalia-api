package com.blog.blog_literario.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs {@code V1__baseline.sql} against a fresh database and validates it with
 * {@code ddl-auto=validate}, so a mismatch between the migration and the JPA
 * entities fails the build instead of surfacing at deploy time.
 *
 * <p><strong>Coverage limit:</strong> this runs on H2, so
 * {@code spring.flyway.locations} resolves {@code classpath:db/vendor/h2} to nothing
 * and the Postgres-only migrations are never executed here — today that means
 * {@code db/vendor/postgresql/V10__unique_lower_email_index.sql}, the
 * {@code lower(email)} unique index, is not exercised by any test. The application
 * layer's case-insensitive email handling <em>is</em> covered (see
 * {@code UserValidatorTest}); V10 is the database-level backstop for writes that
 * bypass it, and verifying it requires a real PostgreSQL instance (Testcontainers),
 * which would add a Docker dependency to CI. {@code FlywayMigrationLayoutTest} guards
 * the version-numbering invariant across both locations in the meantime.
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationConsistencyTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:flywayconsistency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void migrationMatchesEntityMappings() {
    }
}
