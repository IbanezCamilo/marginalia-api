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
import com.blog.blog_literario.events.EmailChangeCompleted;
import com.blog.blog_literario.events.EmailChangeRequested;
import com.blog.blog_literario.events.VerificationEmailRequested;
import com.blog.blog_literario.exception.InvalidVerificationTokenException;
import com.blog.blog_literario.exception.OwnerEmailImmutableException;
import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.exception.VerificationTokenExpiredException;
import com.blog.blog_literario.model.EmailVerificationToken;
import com.blog.blog_literario.model.TokenType;
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
    private final RefreshTokenService refreshTokenService;

    /**
     * Issues a fresh verification token for {@code user} and publishes a
     * {@link VerificationEmailRequested} event, which an {@code AFTER_COMMIT} listener
     * turns into the actual email. Silently does nothing when the cooldown or the
     * daily cap would be exceeded.
     */
    public void requestVerificationEmail(User user) {
        LocalDateTime now = LocalDateTime.now();
        Optional<EmailVerificationToken> latest = tokenRepository.findTopByUserOrderByCreatedAtDesc(user);

        if (throttled(latest, user, now)) {
            log.debug("Verification email throttled for user {}", user.getId());
            return;
        }
        supersede(latest, now);

        String rawToken = newRawToken();
        EmailVerificationToken saved = issueToken(user, TokenType.VERIFICATION, rawToken, null, null, now);

        // Keyed by the token hash, not the row ID: IDs repeat across database instances
        // (recreated DB, other environments on the same Resend key) and Resend remembers
        // idempotency keys for 24h, rejecting a reused key with a different body (409).
        eventPublisher.publishEvent(new VerificationEmailRequested(
                user.getEmail(), user.getName(), rawToken, "verify-email/" + saved.getToken()));
    }

    /**
     * Requests a change of {@code user}'s email to {@code newRawEmail}. Stages the new
     * address on an {@link TokenType#EMAIL_CHANGE} token — the account keeps its current,
     * verified address until the confirm link is redeemed — and publishes an
     * {@link EmailChangeRequested} event (confirm link to the new address, cancel link to
     * the old one). The same throttle as verification applies, but here a breach is
     * surfaced rather than silent: the caller is authenticated, so there is nothing to
     * hide, and a silent success would mislead them into waiting for an email that never
     * comes.
     *
     * @throws IllegalArgumentException   if the address is blank or equals the current one
     * @throws UserAlreadyExistsException if the address already belongs to another account
     * @throws IllegalStateException      if the cooldown or daily cap would be exceeded
     */
    public void requestEmailChange(User user, String newRawEmail) {
        // The OWNER's email is env-managed: DataInitializer reseeds the owner by OWNER_EMAIL
        // on every startup, so a self-service change would spawn a second, undeletable OWNER
        // on the next restart. Rejected before any token work.
        if (user.getRole() != null && user.getRole().isOwner()) {
            throw new OwnerEmailImmutableException(
                    "El correo del propietario se gestiona mediante una variable de entorno y no puede cambiarse desde la cuenta.");
        }

        String newEmail = userValidator.sanitizeEmail(newRawEmail);
        if (newEmail.isBlank()) {
            throw new IllegalArgumentException("El correo no puede estar vacío");
        }
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("El nuevo correo debe ser diferente al actual");
        }
        if (userRepository.existsByEmailExcludingId(newEmail, user.getId())) {
            throw new UserAlreadyExistsException(
                    "El correo '" + newEmail + "' ya está en uso por otro usuario");
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<EmailVerificationToken> latest = tokenRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (throttled(latest, user, now)) {
            throw new IllegalStateException(
                    "Debes esperar un momento antes de solicitar otro cambio de correo");
        }
        supersede(latest, now);

        String confirmRaw = newRawToken();
        String cancelRaw = newRawToken();
        EmailVerificationToken saved = issueToken(user, TokenType.EMAIL_CHANGE, confirmRaw, cancelRaw, newEmail, now);

        eventPublisher.publishEvent(new EmailChangeRequested(
                newEmail, user.getEmail(), user.getName(), confirmRaw, cancelRaw,
                "email-change/" + saved.getToken()));
    }

    /**
     * Redeems an email-change confirm token: swaps the account's address to the staged
     * one, invalidates every session (email is the login identity, so outstanding tokens
     * must die), clears the user's email-change tokens, and publishes an
     * {@link EmailChangeCompleted} notice to the old address. Only {@code EMAIL_CHANGE}
     * tokens are accepted — a registration token presented here is rejected as invalid.
     *
     * @throws InvalidVerificationTokenException if the token matches nothing or is the wrong type
     * @throws VerificationTokenExpiredException if the token expired or was superseded
     * @throws UserAlreadyExistsException        if the address was taken since the request
     */
    public void confirmEmailChange(String rawToken) {
        EmailVerificationToken stored = tokenRepository.findByToken(hashToken(rawToken))
                .filter(t -> t.getTokenType() == TokenType.EMAIL_CHANGE)
                .orElseThrow(() -> new InvalidVerificationTokenException(
                        "El enlace de cambio de correo no es válido"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new VerificationTokenExpiredException("El enlace de cambio de correo ha caducado");
        }

        User user = stored.getUser();
        String newEmail = stored.getPendingEmail();
        // Re-check uniqueness at confirm time: the address may have been claimed by another
        // account between the request and the click.
        if (userRepository.existsByEmailExcludingId(newEmail, user.getId())) {
            throw new UserAlreadyExistsException(
                    "El correo '" + newEmail + "' ya está en uso por otro usuario");
        }

        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        user.incrementTokenVersion();
        userRepository.save(user);
        // Scoped by the explicit discriminator — leaves any other token types intact.
        tokenRepository.deleteByUserAndTokenType(user, TokenType.EMAIL_CHANGE);
        refreshTokenService.deleteAllByUser(user);

        eventPublisher.publishEvent(new EmailChangeCompleted(
                oldEmail, newEmail, user.getName(), "email-change-done/" + stored.getToken()));
    }

    /**
     * Cancels a pending email change from the cancel link sent to the current address.
     * Deletes the staged token so neither its confirm nor cancel link works again. Expiry
     * is not checked — voiding an already-stale pending change is harmless and reassures
     * the owner. Only {@code EMAIL_CHANGE} tokens carry a cancel token, but the type is
     * asserted defensively.
     *
     * @throws InvalidVerificationTokenException if the cancel token matches nothing
     */
    public void cancelEmailChange(String rawToken) {
        EmailVerificationToken stored = tokenRepository.findByCancelToken(hashToken(rawToken))
                .filter(t -> t.getTokenType() == TokenType.EMAIL_CHANGE)
                .orElseThrow(() -> new InvalidVerificationTokenException(
                        "El enlace de cancelación no es válido"));
        tokenRepository.delete(stored);
    }

    /** Whether issuing another token for {@code user} would breach the cooldown or daily cap. */
    private boolean throttled(Optional<EmailVerificationToken> latest, User user, LocalDateTime now) {
        boolean inCooldown = latest.isPresent()
                && latest.get().getCreatedAt().isAfter(now.minusSeconds(properties.cooldownSeconds()));
        boolean capReached = tokenRepository.countByUserAndCreatedAtAfter(user, now.minusHours(24))
                >= properties.dailyCap();
        return inCooldown || capReached;
    }

    /** Supersedes the user's latest still-active token so it keeps counting toward the daily cap. */
    private void supersede(Optional<EmailVerificationToken> latest, LocalDateTime now) {
        latest.filter(t -> t.getExpiresAt().isAfter(now))
                .ifPresent(t -> {
                    t.setExpiresAt(now);
                    tokenRepository.save(t);
                });
    }

    /** Persists a new token row; only the SHA-256 hashes are stored, never the raw values. */
    private EmailVerificationToken issueToken(User user, TokenType type, String rawToken,
                                              String rawCancelToken, String pendingEmail, LocalDateTime now) {
        EmailVerificationToken entity = new EmailVerificationToken();
        entity.setToken(hashToken(rawToken));
        entity.setTokenType(type);
        entity.setPendingEmail(pendingEmail);
        if (rawCancelToken != null) {
            entity.setCancelToken(hashToken(rawCancelToken));
        }
        entity.setUser(user);
        entity.setExpiresAt(now.plusHours(properties.tokenExpirationHours()));
        return tokenRepository.save(entity);
    }

    private String newRawToken() {
        return UUID.randomUUID().toString().replace("-", "");
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
