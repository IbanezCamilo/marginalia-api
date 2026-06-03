package com.blog.blog_literario.controllers.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.auth.LoginRequest;
import com.blog.blog_literario.dto.auth.RegisterRequest;
import com.blog.blog_literario.security.CookieUtil;
import com.blog.blog_literario.services.auth.AuthService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Handles user registration, login, and logout.
 *
 * <p>Successful login and registration write the JWT to an HttpOnly cookie via
 * {@link CookieUtil} rather than returning it in the response body. Logout clears
 * the cookie by setting its max-age to 0.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /** Authenticates the user and sets the JWT cookie on success. */
    @PostMapping("/login")
    public ResponseEntity<Void> login(
        @RequestBody @Valid LoginRequest dto,
        HttpServletResponse response) {

       String token = authService.login(dto);
       cookieUtil.addJwtCookie(response, token);

       return ResponseEntity.ok().build();
    }

    /** Creates a new READER account and sets the JWT cookie for immediate login. */
    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @RequestBody @Valid RegisterRequest dto,
            HttpServletResponse response) {

        String token = authService.register(dto);
        cookieUtil.addJwtCookie(response, token);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** Clears the JWT cookie, ending the user's session. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        cookieUtil.clearJwtCookie(response);
        return ResponseEntity.ok().build();
    }
}
