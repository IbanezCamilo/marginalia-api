package com.blog.blog_literario.services.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.config.properties.EmailVerificationProperties;
import com.blog.blog_literario.events.VerificationEmailRequested;
import com.blog.blog_literario.exception.InvalidVerificationTokenException;
import com.blog.blog_literario.exception.VerificationTokenExpiredException;
import com.blog.blog_literario.model.EmailVerificationToken;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.EmailVerificationTokenRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.utils.UserValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Issues, verifies, and resends email verification tokens.
 *
 * <p>Tokens follow the same pattern as {@link RefreshTokenService}: the raw value only
 * travels in the emailed link, the database stores its SHA-256 hash. Only the most
 * recently issued token is valid — issuing a new one expires the previous one instead
 * of deleting it, so the resend cooldown and daily cap can be computed from
 * {@code createdAt}; a nightly job purges rows long past expiry.
 *
 * <p>Throttle violations are silent no-ops: the public resend endpoint always answers
 * the same way, revealing nothing about whether an account exists or is rate-limited.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailVerificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final UserValidator userValidator;

    /**
     * Issues a fresh verification token for {@code user} and publishes a
     * {@link VerificationEmailRequested} event, which an {@code AFTER_COMMIT} listener
     * turns into the actual email. Silently does nothing when the cooldown or the
     * daily cap would be exceeded.
     */
    public void requestVerificationEmail(User user) {
        LocalDateTime now = LocalDateTime.now();
        Optional<EmailVerificationToken> latest = tokenRepository.findTopByUserOrderByCreatedAtDesc(user);

        boolean inCooldown = latest.isPresent()
                && latest.get().getCreatedAt().isAfter(now.minusSeconds(properties.cooldownSeconds()));
        boolean capReached = tokenRepository.countByUserAndCreatedAtAfter(user, now.minusHours(24))
                >= properties.dailyCap();
        if (inCooldown || capReached) {
            log.debug("Verification email throttled for user {} (cooldown={}, capReached={})",
                    user.getId(), inCooldown, capReached);
            return;
        }

        // Supersede rather than delete: the row must keep counting toward the daily cap.
        latest.filter(t -> t.getExpiresAt().isAfter(now))
                .ifPresent(t -> {
                    t.setExpiresAt(now);
                    tokenRepository.save(t);
                });

        String rawToken = UUID.randomUUID().toString().replace("-", "");
        EmailVerificationToken entity = new EmailVerificationToken();
        entity.setToken(hashToken(rawToken));
        entity.setUser(user);
        entity.setExpiresAt(now.plusHours(properties.tokenExpirationHours()));
        EmailVerificationToken saved = tokenRepository.save(entity);

        // Keyed by the token hash, not the row ID: IDs repeat across database instances
        // (recreated DB, other environments on the same Resend key) and Resend remembers
        // idempotency keys for 24h, rejecting a reused key with a different body (409).
        eventPublisher.publishEvent(new VerificationEmailRequested(
                user.getEmail(), user.getName(), rawToken, "verify-email/" + saved.getToken()));
    }

    /**
     * Marks the token's owner as verified and deletes all their verification tokens.
     *
     * @throws InvalidVerificationTokenException  if {@code rawToken} matches nothing
     * @throws VerificationTokenExpiredException  if the token expired or was superseded
     */
    public void verify(String rawToken) {
        EmailVerificationToken stored = tokenRepository.findByToken(hashToken(rawToken))
                .orElseThrow(() -> new InvalidVerificationTokenException(
                        "El enlace de verificación no es válido"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new VerificationTokenExpiredException("El enlace de verificación ha caducado");
        }

        User user = stored.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        tokenRepository.deleteByUser(user);
    }

    /**
     * Resends the verification email for {@code email}. Unknown addresses and already
     * verified accounts are silent no-ops so the endpoint reveals nothing.
     */
    public void resendVerification(String email) {
        userRepository.findByEmail(userValidator.sanitizeEmail(email))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::requestVerificationEmail);
    }

    /**
     * Whether {@code email} belongs to a verified account. Unknown addresses answer
     * {@code false} — indistinguishable from an unverified account — so the public
     * status endpoint reveals nothing about whether an account exists.
     */
    @Transactional(readOnly = true)
    public boolean isEmailVerified(String email) {
        return userRepository.findByEmail(userValidator.sanitizeEmail(email))
                .map(User::isEmailVerified)
                .orElse(false);
    }

    /**
     * Nightly purge of stale token rows. The 24-hour grace period after expiry keeps
     * every row that could still count toward a user's daily cap.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void purgeExpiredTokens() {
        tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now().minusHours(24));
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
