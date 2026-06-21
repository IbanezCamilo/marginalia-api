package com.blog.blog_literario.security;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.blog.blog_literario.config.properties.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Token-bucket rate limiter with profile-based limits per endpoint group.
 *
 * <p>Each client IP gets an independent {@link Bucket} per profile. Limits:
 * AUTH — 10/min, PUBLIC — 60/min, IMAGES — 30/min, UPLOAD — 10/hr,
 * AUTHENTICATED ({@code /api/me/**}, {@code /api/moderator/**}, {@code /api/admin/**}) — 120/min.
 * Buckets inactive for more than 10 minutes are evicted to prevent unbounded memory growth.
 * Paths that do not match any profile are passed through without inspection.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    private static final Pattern COVER_IMAGE_PATTERN =
            Pattern.compile("/api/me/posts/\\d+/cover-image");

    private static final class BucketEntry {
        final Bucket bucket;
        volatile Instant lastUsed;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastUsed = Instant.now();
        }
    }

    private Bucket createBucket(RateLimitProfile profile) {
        Bandwidth limit = Bandwidth.classic(
                profile.capacity,
                Refill.greedy(profile.capacity, profile.refillPeriod)
        );
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        RateLimitProfile profile = resolveProfile(uri, method);
        if (profile == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        String bucketKey = ip + ":" + profile.name();
        BucketEntry entry = buckets.computeIfAbsent(bucketKey, k -> new BucketEntry(createBucket(profile)));
        entry.lastUsed = Instant.now();

        if (entry.bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Espera un minuto antes de volver a intentar.");
            problem.setType(URI.create("https://blog-literario.com/errors/rate-limited"));

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), problem);
        }
    }

    RateLimitProfile resolveProfile(String uri, String method) {
        if (uri.startsWith("/api/auth/"))   return RateLimitProfile.AUTH;
        if (uri.startsWith("/api/images/")) return RateLimitProfile.IMAGES;
        if (isUploadEndpoint(uri, method))  return RateLimitProfile.UPLOAD;
        if (uri.startsWith("/api/public/")) return RateLimitProfile.PUBLIC;
        if (uri.startsWith("/api/me/") || uri.startsWith("/api/moderator/") || uri.startsWith("/api/admin/")) {
            return RateLimitProfile.AUTHENTICATED;
        }
        return null;
    }

    private boolean isUploadEndpoint(String uri, String method) {
        return "POST".equals(method) && (
                COVER_IMAGE_PATTERN.matcher(uri).matches() ||
                "/api/me/profile/image".equals(uri)
        );
    }

    @Scheduled(fixedDelay = 60_000)
    void evictStaleBuckets() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(10));
        buckets.entrySet().removeIf(e -> e.getValue().lastUsed.isBefore(threshold));
    }

    private String getClientIp(HttpServletRequest request) {
        if (rateLimitProperties.trustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
