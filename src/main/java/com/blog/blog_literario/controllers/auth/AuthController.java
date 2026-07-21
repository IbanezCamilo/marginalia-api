package com.blog.blog_literario.controllers.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.auth.AuthTokenPair;
import com.blog.blog_literario.dto.auth.LoginRequest;
import com.blog.blog_literario.dto.auth.RegisterRequest;
import com.blog.blog_literario.dto.auth.ResendVerificationRequest;
import com.blog.blog_literario.dto.auth.VerificationStatusResponse;
import com.blog.blog_literario.dto.auth.VerifyEmailRequest;
import com.blog.blog_literario.security.CookieUtil;
import com.blog.blog_literario.services.auth.AuthService;
import com.blog.blog_literario.services.auth.EmailVerificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Handles user registration, email verification, login, token refresh, and logout.
 *
 * <p>Successful login writes both a short-lived JWT (access token) and a long-lived
 * refresh token to separate HttpOnly cookies via {@link CookieUtil}. Registration
 * sets no cookies: the account must first be verified through the emailed link
 * ({@code /verify-email}), and until then login and refresh answer 403.
 * The refresh endpoint rotates the refresh token and issues a new access token.
 * Logout deletes the refresh token from the database and clears both cookies.
 */
@Tag(name = "Authentication")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
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

    @Operation(summary = "Register", description = "Creates a new READER account and emails a verification link. No cookies are set: login is blocked until the email is verified.")
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody @Valid RegisterRequest dto) {

        authService.register(dto);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Verify email", description = "Validates the emailed verification token and activates the account. 400 if the token is unknown, 410 if it expired or was superseded.")
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestBody @Valid VerifyEmailRequest dto) {

        emailVerificationService.verify(dto.token());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Resend verification email", description = "Issues a new verification token and email. Always answers 200 — it never reveals whether the account exists, is verified, or is rate-limited.")
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@RequestBody @Valid ResendVerificationRequest dto) {

        emailVerificationService.resendVerification(dto.email());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Confirm email change", description = "Validates the token from the new-address link and swaps the account's email. Invalidates the session (login identity changed). 400 if the token is unknown or the wrong type, 410 if it expired, 409 if the new address was taken since the request.")
    @PostMapping("/confirm-email-change")
    public ResponseEntity<Void> confirmEmailChange(@RequestBody @Valid VerifyEmailRequest dto) {

        emailVerificationService.confirmEmailChange(dto.token());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Cancel email change", description = "Validates the token from the old-address link and voids the pending email change. 400 if the token is unknown.")
    @PostMapping("/cancel-email-change")
    public ResponseEntity<Void> cancelEmailChange(@RequestBody @Valid VerifyEmailRequest dto) {

        emailVerificationService.cancelEmailChange(dto.token());

        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Verification status", description = "Whether the given email belongs to a verified account. Polled by the post-registration waiting screen to detect verification from another device. Unknown emails answer verified=false, never 404.")
    @GetMapping("/verification-status")
    public ResponseEntity<VerificationStatusResponse> verificationStatus(@RequestParam String email) {

        return ResponseEntity.ok(new VerificationStatusResponse(emailVerificationService.isEmailVerified(email)));
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
