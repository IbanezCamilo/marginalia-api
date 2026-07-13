package com.blog.blog_literario.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Coverage regression tests for {@link RateLimitFilter#resolveProfile}.
 *
 * <p>{@code resolveProfile} is fail-open: any path matching no rule returns {@code null}
 * and passes through unthrottled. These tests enumerate every MVC endpoint registered in
 * the application context so that a newly added route which escapes rate limiting — or an
 * upload endpoint that silently lands on the generic 120/min profile instead of UPLOAD
 * (10/hr) — fails the build instead of shipping as a silent hole.
 *
 * <p>Two invariants:
 * <ul>
 *   <li><b>A</b> — every endpoint resolves to a non-null profile, unless it is on the
 *       explicit {@link #INTENTIONAL_NO_LIMIT} allowlist.</li>
 *   <li><b>B</b> — every handler that accepts a {@link MultipartFile} resolves to
 *       {@link RateLimitProfile#UPLOAD} (A alone would miss these: a new upload endpoint
 *       still resolves to a non-null profile via its {@code /api/me/} or {@code /api/admin/}
 *       prefix, so it passes A while being ~720x less restricted than it should be).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class RateLimitCoverageTest {

    /**
     * Endpoints deliberately left with no rate-limit profile (documented, accepted fail-open).
     * {@code /actuator/health} and {@code /actuator/info} are the accepted public fail-open
     * (see the audit plan); {@code /error} is Spring's internal error dispatch, not a
     * client-callable route.
     */
    private static final Set<String> INTENTIONAL_NO_LIMIT = Set.of(
            "/actuator/health",
            "/actuator/info",
            "/error"
    );

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Autowired
    RateLimitFilter rateLimitFilter;

    @Test
    void everyEndpointResolvesToARateLimitProfile() {
        List<String> unprotected = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            for (String pattern : pathPatterns(info)) {
                String uri = sampleUri(pattern);
                if (INTENTIONAL_NO_LIMIT.contains(uri)) {
                    continue;
                }
                for (String method : httpMethods(info)) {
                    if (rateLimitFilter.resolveProfile(uri, method) == null) {
                        unprotected.add(method + " " + uri + "  (" + handler.getShortLogMessage() + ")");
                    }
                }
            }
        });

        assertThat(unprotected)
                .as("Endpoints with no rate-limit profile. Assign one in resolveProfile(), or if the "
                        + "fail-open is intentional add the path to INTENTIONAL_NO_LIMIT:%n%s",
                        String.join("\n", unprotected))
                .isEmpty();
    }

    @Test
    void everyMultipartUploadEndpointResolvesToUpload() {
        List<String> misclassified = new ArrayList<>();

        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            if (!acceptsMultipart(handler)) {
                return;
            }
            for (String pattern : pathPatterns(info)) {
                String uri = sampleUri(pattern);
                RateLimitProfile profile = rateLimitFilter.resolveProfile(uri, "POST");
                if (profile != RateLimitProfile.UPLOAD) {
                    misclassified.add("POST " + uri + " -> " + profile + "  (" + handler.getShortLogMessage() + ")");
                }
            }
        });

        assertThat(misclassified)
                .as("Multipart upload endpoints not classified as UPLOAD (10/hr). A new upload route "
                        + "must be registered in RateLimitFilter.isUploadEndpoint():%n%s",
                        String.join("\n", misclassified))
                .isEmpty();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private static Set<String> pathPatterns(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        // Fallback for the legacy AntPathMatcher strategy (not used by default in Boot 3).
        return info.getPatternsCondition().getPatterns();
    }

    private static Set<String> httpMethods(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            // No method restriction = applies to all. Prefix-based profiles are method-agnostic,
            // so a single GET probe is sufficient for the non-null invariant.
            return Set.of("GET");
        }
        return methods.stream().map(RequestMethod::name).collect(Collectors.toSet());
    }

    /** Replaces every {@code {var}} path segment with "1" (also satisfies the {@code \d+} cover-image matcher). */
    private static String sampleUri(String pattern) {
        return pattern.replaceAll("\\{[^{}]+\\}", "1");
    }

    private static boolean acceptsMultipart(HandlerMethod handler) {
        for (var param : handler.getMethodParameters()) {
            if (MultipartFile.class.isAssignableFrom(param.getParameterType())) {
                return true;
            }
        }
        return false;
    }
}
