package com.blog.blog_literario.services.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import com.blog.blog_literario.config.properties.LockoutProperties;
import com.blog.blog_literario.dto.auth.AuthTokenPair;
import com.blog.blog_literario.dto.auth.LoginRequest;
import com.blog.blog_literario.dto.auth.RegisterRequest;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
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
    private final UserRepository userRepository;
    private final LockoutProperties lockoutProperties;

    public AuthTokenPair register(RegisterRequest request) {
        User newUser = userCreationService.createUser(
                request.name(),
                request.email(),
                request.password(),
                Role.READER
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(newUser.getEmail());
        String accessToken = jwtService.generateToken(userDetails, newUser.getTokenVersion());
        String refreshToken = refreshTokenService.create(newUser);

        return new AuthTokenPair(accessToken, refreshToken);
    }

    /**
     * Authenticates a user, enforcing a temporary lockout after too many failed attempts.
     *
     * <p>A locked account is rejected before its password is even checked, and every failure
     * path throws the same generic {@link BadCredentialsException} — the response never reveals
     * whether the email exists or whether the account is locked.
     *
     * <p>{@code noRollbackFor} is required: the failed-attempt counter is written inside this
     * transaction and must survive the rethrown {@link BadCredentialsException}, which would
     * otherwise trigger a rollback and silently discard the increment.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthTokenPair login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (isLocked(user)) {
            throw new BadCredentialsException("Credenciales inválidas");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));

            if (user != null) {
                resetFailedAttempts(user);
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
            User authUser = ((UserDetailsImpl) userDetails).getUser();
            String accessToken = jwtService.generateToken(userDetails, authUser.getTokenVersion());
            String refreshToken = refreshTokenService.create(authUser);

            return new AuthTokenPair(accessToken, refreshToken);
        } catch (AuthenticationException e) {
            if (user != null) {
                registerFailedAttempt(user);
            }
            throw new BadCredentialsException("Credenciales inválidas");
        }
    }

    private boolean isLocked(User user) {
        return user != null
                && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= lockoutProperties.maxAttempts()) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutProperties.durationMinutes()));
        }
        userRepository.save(user);
    }

    public AuthTokenPair refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawRefreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(result.user().getEmail());
        String newAccessToken = jwtService.generateToken(userDetails, result.user().getTokenVersion());
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
