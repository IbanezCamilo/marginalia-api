package com.blog.blog_literario.controllers.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.auth.AuthTokenPair;
import com.blog.blog_literario.dto.auth.LoginRequest;
import com.blog.blog_literario.dto.auth.RegisterRequest;
import com.blog.blog_literario.security.CookieUtil;
import com.blog.blog_literario.services.auth.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Handles user registration, login, token refresh, and logout.
 *
 * <p>Successful login and registration write both a short-lived JWT (access token)
 * and a long-lived refresh token to separate HttpOnly cookies via {@link CookieUtil}.
 * The refresh endpoint rotates the refresh token and issues a new access token.
 * Logout deletes the refresh token from the database and clears both cookies.
 */
@Tag(name = "Authentication")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    @Operation(summary = "Login", description = "Authenticates the user and sets both JWT and refresh token cookies on success.")
    @PostMapping("/login")
    public ResponseEntity<Void> login(
            @RequestBody @Valid LoginRequest dto,
            HttpServletResponse response) {

        AuthTokenPair tokens = authService.login(dto);
        cookieUtil.addJwtCookie(response, tokens.accessToken());
        cookieUtil.addRefreshTokenCookie(response, tokens.refreshToken());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Register", description = "Creates a new READER account and sets both cookies for immediate login.")
    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @RequestBody @Valid RegisterRequest dto,
            HttpServletResponse response) {

        AuthTokenPair tokens = authService.register(dto);
        cookieUtil.addJwtCookie(response, tokens.accessToken());
        cookieUtil.addRefreshTokenCookie(response, tokens.refreshToken());

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Refresh", description = "Issues a new access token and rotates the refresh token. Both cookies are updated.")
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        String rawRefreshToken = cookieUtil.extractFromRequest(request, CookieUtil.REFRESH_COOKIE_NAME);
        if (rawRefreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AuthTokenPair tokens = authService.refresh(rawRefreshToken);
        cookieUtil.addJwtCookie(response, tokens.accessToken());
        cookieUtil.addRefreshTokenCookie(response, tokens.refreshToken());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Logout", description = "Deletes the refresh token from the database and clears both cookies.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        String jwt = cookieUtil.extractFromRequest(request, CookieUtil.NAME);
        String rawRefreshToken = cookieUtil.extractFromRequest(request, CookieUtil.REFRESH_COOKIE_NAME);
        authService.logout(rawRefreshToken, jwt);

        cookieUtil.clearJwtCookie(response);
        cookieUtil.clearRefreshTokenCookie(response);

        return ResponseEntity.ok().build();
    }
}
