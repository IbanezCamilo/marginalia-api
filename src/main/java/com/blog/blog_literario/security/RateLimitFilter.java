package com.blog.blog_literario.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Token-bucket rate limiter scoped to all auth endpoints.
 *
 * <p>Each client IP gets its own {@link Bucket} capped at 10 auth attempts per minute.
 * Buckets inactive for more than 10 minutes are evicted to prevent unbounded memory growth.
 * All other paths are passed through without inspection.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    private static final class BucketEntry {
        final Bucket bucket;
        volatile Instant lastUsed;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastUsed = Instant.now();
        }
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(
                10,
                Refill.greedy(10, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        BucketEntry entry = buckets.computeIfAbsent(ip, k -> new BucketEntry(createBucket()));
        entry.lastUsed = Instant.now();

        if (entry.bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\": \"Demasiados intentos. Espera un minuto antes de volver a intentar.\"}"
            );
        }
    }

    @Scheduled(fixedDelay = 60_000)
    void evictStaleBuckets() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(10));
        buckets.entrySet().removeIf(e -> e.getValue().lastUsed.isBefore(threshold));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
