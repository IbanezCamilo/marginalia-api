package com.blog.blog_literario.security;

import java.io.IOException; 
import java.util.Arrays;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
            @NonNull HttpServletResponse res, @NonNull FilterChain filterChain) throws ServletException, IOException {
                
        final String path = req.getServletPath();
        final String method = req.getMethod();

        if (isPublicRoute(path, method)) {
            filterChain.doFilter(req, res);
            return;
        }

        String jwt = extractFromCookie(req);
        
        if (jwt == null) {
            filterChain.doFilter(req, res);
            return;
        }

        try {
            String userEmail = jwtService.extractUsername(jwt);
            log.debug("User extracted from JWT: {}", userEmail);
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                boolean valid = jwtService.isTokenValid(jwt, userDetails);
                log.debug("Token valid: {}", valid);
                if (valid) {
                    var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authorities: {}", userDetails.getAuthorities());
                }
            }
        } catch (Exception e) {
            log.warn("Error en JwtAuthenticationFilter", e);
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }
        filterChain.doFilter(req, res);

    }

    private boolean isPublicRoute(String path, String method) {
        if (path.startsWith("/api/auth/")
                || path.startsWith("/api/images/")) {
            return true;
        }

        if ("GET".equals(method)) {
            if (path.startsWith("/api/public/")) {
                return true;
            }
        }
        return false;
    }

    private String extractFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        return Arrays.stream(cookies)
                .filter(c -> CookieUtil.NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

}
