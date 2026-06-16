package com.blog.blog_literario.security;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.blog.blog_literario.services.auth.RefreshTokenService;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Once-per-request filter that reads the JWT from the {@code jwt} cookie,
 * validates it, and populates the {@link SecurityContextHolder} so that
 * downstream filters and controllers see an authenticated principal.
 *
 * <p>When the access token is expired and a valid {@code refresh_token} cookie
 * is present, the filter silently rotates the refresh token, issues new cookies,
 * and authenticates the request — transparent to the caller.
 *
 * <p>Public routes (auth, images, GET /api/public/**) are passed through without
 * token inspection.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDetailsService userDetailsService,
                                   RefreshTokenService refreshTokenService,
                                   CookieUtil cookieUtil) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.refreshTokenService = refreshTokenService;
        this.cookieUtil = cookieUtil;
    }

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
        } catch (ExpiredJwtException e) {
            String raw = cookieUtil.extractFromRequest(req, CookieUtil.REFRESH_COOKIE_NAME);
            String fallbackEmail = e.getClaims() != null ? e.getClaims().getSubject() : null;
            if (raw != null && tryRefresh(raw, fallbackEmail, req, res)) {
                filterChain.doFilter(req, res);
                return;
            }
            log.debug("JWT expired — proceeding without authentication");
        } catch (Exception e) {
            log.warn("JWT authentication error — token rejected", e);
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }
        filterChain.doFilter(req, res);

    }

    private boolean tryRefresh(String rawRefreshToken, String fallbackEmail,
                               HttpServletRequest req, HttpServletResponse res) {
        try {
            RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawRefreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(result.user().getEmail());
            String newAccessToken = jwtService.generateToken(userDetails);
            cookieUtil.addJwtCookie(res, newAccessToken);
            cookieUtil.addRefreshTokenCookie(res, result.newRawToken());
            authenticate(userDetails, req);
            log.debug("Silent token refresh succeeded for {}", result.user().getEmail());
            return true;
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            // Refresh token already consumed by a concurrent request — verify session is still alive
            if (fallbackEmail != null && refreshTokenService.hasActiveTokenForEmail(fallbackEmail)) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(fallbackEmail);
                cookieUtil.addJwtCookie(res, jwtService.generateToken(userDetails));
                authenticate(userDetails, req);
                log.debug("Concurrent refresh resolved for {}", fallbackEmail);
                return true;
            }
            log.debug("Silent token refresh failed: {}", ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.debug("Silent token refresh failed: {}", ex.getMessage());
            return false;
        }
    }

    private void authenticate(UserDetails userDetails, HttpServletRequest req) {
        var authToken = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
        SecurityContextHolder.getContext().setAuthentication(authToken);
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
