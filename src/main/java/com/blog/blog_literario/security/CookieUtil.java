package com.blog.blog_literario.security;

import java.util.Arrays;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.blog.blog_literario.config.properties.CookieProperties;
import com.blog.blog_literario.config.properties.JwtProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Utility for writing and clearing JWT and refresh token cookies on HTTP responses.
 *
 * <p>All cookies are HttpOnly, SameSite=Lax. The {@code Secure} flag is driven by
 * {@link CookieProperties} so it can be disabled in local dev.
 *
 * <p>The refresh token cookie uses {@code path=/} so it is available to the silent-refresh
 * filter on all protected routes. A legacy path ({@code /api/auth}) is always cleared
 * alongside any write or clear operation to migrate browsers that still carry the old cookie.
 */
@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public static final String NAME = "jwt";
    public static final String REFRESH_COOKIE_NAME = "refresh_token";

    private static final String REFRESH_PATH = "/";
    private static final String LEGACY_REFRESH_PATH = "/api/auth";

    public void addJwtCookie(HttpServletResponse res, String token) {
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(NAME, token, "/", jwtProperties.expiration() / 1000).toString());
    }

    public void clearJwtCookie(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(NAME, "", "/", 0).toString());
    }

    public void addRefreshTokenCookie(HttpServletResponse res, String token) {
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE_NAME, token, REFRESH_PATH, jwtProperties.refreshExpiration() / 1000).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE_NAME, "", LEGACY_REFRESH_PATH, 0).toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE_NAME, "", REFRESH_PATH, 0).toString());
        res.addHeader(HttpHeaders.SET_COOKIE, buildCookie(REFRESH_COOKIE_NAME, "", LEGACY_REFRESH_PATH, 0).toString());
    }

    public String extractFromRequest(HttpServletRequest req, String cookieName) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie buildCookie(String name, String value, String path, long maxAgeSeconds) {
        String domain = cookieProperties.domain();
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(path)
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .domain(domain != null && !domain.isBlank() ? domain : null)
                .build();
    }
}
