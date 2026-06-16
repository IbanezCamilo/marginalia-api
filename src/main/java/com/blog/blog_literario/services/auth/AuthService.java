package com.blog.blog_literario.services.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.auth.AuthTokenPair;
import com.blog.blog_literario.dto.auth.LoginRequest;
import com.blog.blog_literario.dto.auth.RegisterRequest;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.UserDetailsImpl;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.users.UserCreationService;

import lombok.RequiredArgsConstructor;

/**
 * Handles user self-registration, authentication, token refresh, and logout.
 *
 * <p>Login and registration return an {@link AuthTokenPair} — a short-lived JWT
 * (access token) and a long-lived opaque refresh token. The controller writes both
 * to HttpOnly cookies. On logout, the refresh token is deleted from the database;
 * the access token expires naturally within its 15-minute window.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserCreationService userCreationService;
    private final RefreshTokenService refreshTokenService;

    public AuthTokenPair register(RegisterRequest request) {
        User newUser = userCreationService.createUser(
                request.name(),
                request.email(),
                request.password(),
                Role.READER
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(newUser.getEmail());
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = refreshTokenService.create(newUser);

        return new AuthTokenPair(accessToken, refreshToken);
    }

    public AuthTokenPair login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
            String accessToken = jwtService.generateToken(userDetails);
            User user = ((UserDetailsImpl) userDetails).getUser();
            String refreshToken = refreshTokenService.create(user);

            return new AuthTokenPair(accessToken, refreshToken);
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
    }

    public AuthTokenPair refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawRefreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(result.user().getEmail());
        String newAccessToken = jwtService.generateToken(userDetails);
        return new AuthTokenPair(newAccessToken, result.newRawToken());
    }

    public void logout(String rawRefreshToken, String jwt) {
        if (rawRefreshToken != null) {
            refreshTokenService.deleteByRawToken(rawRefreshToken);
        }
        if (jwt != null) {
            try {
                String email = jwtService.extractUsernameAllowExpired(jwt);
                UserDetails ud = userDetailsService.loadUserByUsername(email);
                User user = ((UserDetailsImpl) ud).getUser();
                refreshTokenService.deleteAllByUser(user);
            } catch (Exception ignored) {}
        }
    }
}
