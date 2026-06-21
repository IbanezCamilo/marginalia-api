package com.blog.blog_literario.security;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Generates a fresh correlation ID for every request, exposes it to the logging
 * context (MDC) for the duration of the request, and echoes it back as a response
 * header so a user-reported error can be traced to its exact log lines.
 *
 * <p>The ID is always generated server-side rather than trusted from an inbound
 * header, since it ends up in logs.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final String RESPONSE_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(RESPONSE_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
