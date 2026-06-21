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
