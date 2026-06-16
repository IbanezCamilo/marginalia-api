package com.blog.blog_literario.services.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.config.properties.JwtProperties;
import com.blog.blog_literario.model.RefreshToken;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public record RotationResult(User user, String newRawToken) {}

    /**
     * Creates a new refresh token for {@code user}, persists its SHA-256 hash,
     * and returns the raw (unhashed) token to be stored in the client cookie.
     */
    public String create(User user) {
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        String hashedToken = hashToken(rawToken);

        LocalDateTime expiresAt = LocalDateTime.now().plus(jwtProperties.refreshExpiration(), ChronoUnit.MILLIS);

        refreshTokenRepository.deleteByUser(user);

        RefreshToken entity = new RefreshToken();
        entity.setToken(hashedToken);
        entity.setUser(user);
        entity.setExpiresAt(expiresAt);

        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /**
     * Validates {@code rawToken}, deletes the old DB row, and issues a new one.
     * Throws {@link BadCredentialsException} if the token is not found or has expired.
     */
    public RotationResult rotate(String rawToken) {
        String hashedToken = hashToken(rawToken);
        RefreshToken stored = refreshTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new BadCredentialsException("Refresh token expirado");
        }

        User user = stored.getUser();
        refreshTokenRepository.delete(stored);

        String newRawToken = create(user);
        return new RotationResult(user, newRawToken);
    }

    /**
     * Deletes the refresh token matching {@code rawToken} from the database.
     * Does nothing if no matching token exists (idempotent logout).
     */
    public void deleteByRawToken(String rawToken) {
        String hashedToken = hashToken(rawToken);
        refreshTokenRepository.findByToken(hashedToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    /** Deletes all refresh tokens for {@code user}. Used during logout to ensure full cleanup. */
    public void deleteAllByUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    /** Returns true if {@code email} has at least one non-expired token in the database. */
    public boolean hasActiveTokenForEmail(String email) {
        return refreshTokenRepository.existsActiveByUserEmail(email, LocalDateTime.now());
    }

    String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
