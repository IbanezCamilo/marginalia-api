package com.blog.blog_literario.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.blog.blog_literario.config.properties.CookieProperties;
import com.blog.blog_literario.config.properties.JwtProperties;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Utility for writing and clearing the JWT cookie on HTTP responses.
 *
 * <p>Both methods produce an HttpOnly, SameSite=Lax cookie. The {@code Secure}
 * flag is driven by {@link CookieProperties} so it can be disabled in local dev.
 */
@Component
@RequiredArgsConstructor
public class CookieUtil {

    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    static final String NAME = "jwt";

    /**
     * Appends a {@code Set-Cookie} header that stores {@code token} as an HttpOnly
     * cookie. The max-age is derived from {@link JwtProperties#expiration()}.
     *
     * @param res   the current HTTP response
     * @param token a signed JWT string
     */
    public void addJwtCookie(HttpServletResponse res, String token) {
        long maxAgeSeconds = jwtProperties.expiration() / 1000;
        ResponseCookie cookie = ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();

        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Appends a {@code Set-Cookie} header that expires the JWT cookie immediately
     * (max-age=0, empty value), effectively logging the user out.
     *
     * @param res the current HTTP response
     */
    public void clearJwtCookie(HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
