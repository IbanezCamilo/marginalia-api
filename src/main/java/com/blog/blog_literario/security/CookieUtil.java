package com.blog.blog_literario.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

@Component
public class CookieUtil {

    @Value("${app.cookie.secure:true}")
    private boolean secure; // false in dev, true en prod

    private static final String NAME = "jwt";
    private static final int MAX_AGE = 60 * 60 * 24; // 24 hours

    public void addJwtCookie(HttpServletResponse res, String token) {
        ResponseCookie cookie = ResponseCookie.from(NAME, token)
                .httpOnly(true)       
                .secure(secure)
                .path("/")
                .maxAge(MAX_AGE)
                .sameSite("Lax")
                .build();

        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearJwtCookie(HttpServletResponse res) {
        ResponseCookie cookie = ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}