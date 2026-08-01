package com.blog.blog_literario.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Guards the two-location migration layout configured by
 * {@code spring.flyway.locations=classpath:db/migration,classpath:db/vendor/{vendor}}.
 *
 * <p>Flyway merges both locations into a single version timeline, so a version number
 * reused across them is a startup failure that no other test would catch: the whole
 * suite runs on H2, which resolves {@code db/vendor/h2} to nothing and therefore never
 * loads the Postgres-only files at all.
 *
 * <p>This does not verify that the vendor SQL is <em>correct</em> — that needs a real
 * PostgreSQL instance (Testcontainers). It verifies the layout invariant that a
 * migration author is most likely to break.
 */
class FlywayMigrationLayoutTest {

    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+)__.+\\.sql$");

    private static List<Resource> migrationsIn(String location) throws IOException {
        return List.of(new PathMatchingResourcePatternResolver()
                .getResources("classpath:" + location + "/*.sql"));
    }

    @Test
    void versionNumbersAreUniqueAcrossPortableAndVendorLocations() throws IOException {
        List<Resource> all = new ArrayList<>();
        all.addAll(migrationsIn("db/migration"));
        all.addAll(migrationsIn("db/vendor/postgresql"));

        Map<Integer, String> seen = new HashMap<>();
        List<String> collisions = new ArrayList<>();

        for (Resource resource : all) {
            String name = resource.getFilename();
            Matcher matcher = VERSIONED.matcher(name);
            assertThat(matcher.matches())
                    .as("migration '%s' does not follow the V<number>__<description>.sql convention", name)
                    .isTrue();

            int version = Integer.parseInt(matcher.group(1));
            String previous = seen.putIfAbsent(version, name);
            if (previous != null) {
                collisions.add("V" + version + ": " + previous + " and " + name);
            }
        }

        assertThat(collisions)
                .as("Flyway merges db/migration and db/vendor/{vendor} into one timeline, "
                        + "so a version may only appear once across both")
                .isEmpty();
    }

    @Test
    void vendorMigrationsAreNotDuplicatedInThePortableLocation() throws IOException {
        List<String> portable = migrationsIn("db/migration").stream()
                .map(Resource::getFilename)
                .toList();

        // A vendor migration exists precisely because H2 cannot parse it. Shipping the
        // same file in db/migration would break every test that replays migrations.
        assertThat(migrationsIn("db/vendor/postgresql").stream().map(Resource::getFilename))
                .as("vendor-specific migrations must not also live in the portable location")
                .doesNotContainAnyElementsOf(portable);
    }
}
