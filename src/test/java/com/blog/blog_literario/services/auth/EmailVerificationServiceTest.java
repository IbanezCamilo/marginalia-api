package com.blog.blog_literario.services.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.blog.blog_literario.config.properties.EmailVerificationProperties;
import com.blog.blog_literario.events.EmailChangeCompleted;
import com.blog.blog_literario.events.EmailChangeRequested;
import com.blog.blog_literario.events.VerificationEmailRequested;
import com.blog.blog_literario.exception.InvalidVerificationTokenException;
import com.blog.blog_literario.exception.OwnerEmailImmutableException;
import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.exception.VerificationTokenExpiredException;
import com.blog.blog_literario.model.EmailVerificationToken;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.TokenType;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.EmailVerificationTokenRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.utils.UserValidator;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock UserRepository userRepository;
    @Mock EmailVerificationProperties properties;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RefreshTokenService refreshTokenService;
    // Real instance: sanitizeEmail never touches the repository, and the tests
    // must exercise the actual trim/lowercase behavior.
    @Spy UserValidator userValidator = new UserValidator(null);

    @InjectMocks EmailVerificationService emailVerificationService;

    private User unverifiedUser() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setEmailVerified(false);
        return user;
    }

    private User verifiedUser() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setEmailVerified(true);
        return user;
    }

    @Test
    void requestVerificationEmail_firstToken_savesHashAndPublishesEvent() {
        User user = unverifiedUser();
        given(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).willReturn(Optional.empty());
        given(tokenRepository.countByUserAndCreatedAtAfter(any(), any())).willReturn(0L);
        given(properties.dailyCap()).willReturn(5);
        given(properties.tokenExpirationHours()).willReturn(24L);
        given(tokenRepository.save(any(EmailVerificationToken.class))).willAnswer(invocation -> {
            EmailVerificationToken token = invocation.getArgument(0);
            token.setId(10L);
            return token;
        });

        emailVerificationService.requestVerificationEmail(user);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        ArgumentCaptor<VerificationEmailRequested> eventCaptor = ArgumentCaptor.forClass(VerificationEmailRequested.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        VerificationEmailRequested event = eventCaptor.getValue();
        assertThat(event.email()).isEqualTo("alice@test.com");
        assertThat(event.name()).isEqualTo("Alice");
        assertThat(event.rawToken()).isNotBlank();
        // Hash-keyed so the key is unique across database instances, not just rows.
        assertThat(event.idempotencyKey())
                .isEqualTo("verify-email/" + emailVerificationService.hashToken(event.rawToken()));

        // Only the SHA-256 hash is persisted — never the raw token from the link.
        EmailVerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getToken()).isEqualTo(emailVerificationService.hashToken(event.rawToken()));
        assertThat(saved.getToken()).isNotEqualTo(event.rawToken());
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));
    }

    @Test
    void requestVerificationEmail_withinCooldown_silentlyDoesNothing() {
        User user = unverifiedUser();
        EmailVerificationToken recent = new EmailVerificationToken();
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        given(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).willReturn(Optional.of(recent));
        given(tokenRepository.countByUserAndCreatedAtAfter(any(), any())).willReturn(1L);
        given(properties.cooldownSeconds()).willReturn(60L);
        given(properties.dailyCap()).willReturn(5);

        emailVerificationService.requestVerificationEmail(user);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestVerificationEmail_dailyCapReached_silentlyDoesNothing() {
        User user = unverifiedUser();
        EmailVerificationToken old = new EmailVerificationToken();
        old.setCreatedAt(LocalDateTime.now().minusHours(2));
        given(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).willReturn(Optional.of(old));
        given(tokenRepository.countByUserAndCreatedAtAfter(any(), any())).willReturn(5L);
        given(properties.cooldownSeconds()).willReturn(60L);
        given(properties.dailyCap()).willReturn(5);

        emailVerificationService.requestVerificationEmail(user);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestVerificationEmail_supersedesPreviousActiveToken() {
        User user = unverifiedUser();
        EmailVerificationToken previous = new EmailVerificationToken();
        previous.setCreatedAt(LocalDateTime.now().minusHours(2));
        previous.setExpiresAt(LocalDateTime.now().plusHours(22)); // still active
        given(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).willReturn(Optional.of(previous));
        given(tokenRepository.countByUserAndCreatedAtAfter(any(), any())).willReturn(1L);
        given(properties.cooldownSeconds()).willReturn(60L);
        given(properties.dailyCap()).willReturn(5);
        given(properties.tokenExpirationHours()).willReturn(24L);
        given(tokenRepository.save(any(EmailVerificationToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        emailVerificationService.requestVerificationEmail(user);

        // The old token is expired, not deleted, so it keeps counting toward the daily cap.
        assertThat(previous.getExpiresAt()).isBeforeOrEqualTo(LocalDateTime.now());
        verify(tokenRepository).save(previous);
        verify(eventPublisher).publishEvent(any(VerificationEmailRequested.class));
    }

    @Test
    void verify_validToken_marksUserVerifiedAndDeletesTokens() {
        User user = unverifiedUser();
        EmailVerificationToken stored = new EmailVerificationToken();
        stored.setUser(user);
        stored.setExpiresAt(LocalDateTime.now().plusHours(1));
        given(tokenRepository.findByToken(emailVerificationService.hashToken("raw-token")))
                .willReturn(Optional.of(stored));

        emailVerificationService.verify("raw-token");

        assertThat(user.isEmailVerified()).isTrue();
        verify(userRepository).save(user);
        verify(tokenRepository).deleteByUser(user);
    }

    @Test
    void verify_unknownToken_throwsInvalidVerificationToken() {
        given(tokenRepository.findByToken(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verify("unknown"))
                .isInstanceOf(InvalidVerificationTokenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void verify_expiredToken_throwsVerificationTokenExpired() {
        User user = unverifiedUser();
        EmailVerificationToken stored = new EmailVerificationToken();
        stored.setUser(user);
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        given(tokenRepository.findByToken(any())).willReturn(Optional.of(stored));

        assertThatThrownBy(() -> emailVerificationService.verify("raw-token"))
                .isInstanceOf(VerificationTokenExpiredException.class);

        assertThat(user.isEmailVerified()).isFalse();
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).deleteByUser(any());
    }

    @Test
    void resendVerification_unknownEmail_isSilentNoOp() {
        given(userRepository.findByEmail("ghost@test.com")).willReturn(Optional.empty());

        emailVerificationService.resendVerification("ghost@test.com");

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resendVerification_alreadyVerified_isSilentNoOp() {
        User user = unverifiedUser();
        user.setEmailVerified(true);
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));

        emailVerificationService.resendVerification("alice@test.com");

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resendVerification_unverifiedUser_issuesNewToken() {
        User user = unverifiedUser();
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));
        given(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).willReturn(Optional.empty());
        given(tokenRepository.countByUserAndCreatedAtAfter(any(), any())).willReturn(0L);
        given(properties.dailyCap()).willReturn(5);
        given(properties.tokenExpirationHours()).willReturn(24L);
        given(tokenRepository.save(any(EmailVerificationToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        emailVerificationService.resendVerification("alice@test.com");

        verify(eventPublisher).publishEvent(any(VerificationEmailRequested.class));
    }

    @Test
    void resendVerification_mixedCaseEmail_findsLowercaseStoredUser() {
        User user = unverifiedUser();
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));
        given(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).willReturn(Optional.empty());
        given(tokenRepository.countByUserAndCreatedAtAfter(any(), any())).willReturn(0L);
        given(properties.dailyCap()).willReturn(5);
        given(properties.tokenExpirationHours()).willReturn(24L);
        given(tokenRepository.save(any(EmailVerificationToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        emailVerificationService.resendVerification("  Alice@Test.COM ");

        verify(eventPublisher).publishEvent(any(VerificationEmailRequested.class));
    }

    @Test
    void isEmailVerified_mixedCaseEmail_matchesLowercaseStoredUser() {
        User user = unverifiedUser();
        user.setEmailVerified(true);
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));

        assertThat(emailVerificationService.isEmailVerified("ALICE@Test.com")).isTrue();
    }

    @Test
    void isEmailVerified_verifiedUser_returnsTrue() {
        User user = unverifiedUser();
        user.setEmailVerified(true);
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));

        assertThat(emailVerificationService.isEmailVerified("alice@test.com")).isTrue();
    }

    @Test
    void isEmailVerified_unverifiedUser_returnsFalse() {
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(unverifiedUser()));

        assertThat(emailVerificationService.isEmailVerified("alice@test.com")).isFalse();
    }

    @Test
    void isEmailVerified_unknownEmail_returnsFalseNotError() {
        given(userRepository.findByEmail("ghost@test.com")).willReturn(Optional.empty());

        assertThat(emailVerificationService.isEmailVerified("ghost@test.com")).isFalse();
    }

    @Test
    void purgeExpiredTokens_deletesRowsPastTheCapWindow() {
        emailVerificationService.purgeExpiredTokens();

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tokenRepository).deleteByExpiresAtBefore(thresholdCaptor.capture());
        // 24h grace after expiry so rows still counting toward the daily cap survive.
        assertThat(thresholdCaptor.getValue()).isBefore(LocalDateTime.now().minusHours(23));
    }

    // ─── requestEmailChange ──────────────────────────────────────────────────────

    @Test
    void requestEmailChange_validNewEmail_savesEmailChangeTokenAndPublishesEvent() {
        User user = verifiedUser();
        given(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).willReturn(Optional.empty());
        given(tokenRepository.countByUserAndCreatedAtAfter(any(), any())).willReturn(0L);
        given(properties.dailyCap()).willReturn(5);
        given(properties.tokenExpirationHours()).willReturn(24L);
        given(userRepository.existsByEmailExcludingId("new@test.com", 1)).willReturn(false);
        given(tokenRepository.save(any(EmailVerificationToken.class))).willAnswer(invocation -> {
            EmailVerificationToken token = invocation.getArgument(0);
            token.setId(20L);
            return token;
        });

        emailVerificationService.requestEmailChange(user, "  New@Test.com ");

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        EmailVerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getTokenType()).isEqualTo(TokenType.EMAIL_CHANGE);
        assertThat(saved.getPendingEmail()).isEqualTo("new@test.com"); // sanitized: trimmed + lowercased
        assertThat(saved.getCancelToken()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(23));

        ArgumentCaptor<EmailChangeRequested> eventCaptor = ArgumentCaptor.forClass(EmailChangeRequested.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        EmailChangeRequested event = eventCaptor.getValue();
        assertThat(event.newEmail()).isEqualTo("new@test.com");
        assertThat(event.oldEmail()).isEqualTo("alice@test.com");
        assertThat(event.name()).isEqualTo("Alice");
        // Only hashes are persisted; the raw tokens live only in the emailed links.
        assertThat(saved.getToken()).isEqualTo(emailVerificationService.hashToken(event.confirmRawToken()));
        assertThat(saved.getCancelToken()).isEqualTo(emailVerificationService.hashToken(event.cancelRawToken()));
        // The account keeps its current address until the change is confirmed.
        assertThat(user.getEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void requestEmailChange_ownerRole_throwsOwnerEmailImmutableAndIssuesNothing() {
        User owner = new User(1, "Owner", "owner@test.com", new Role(Role.OWNER));
        owner.setEmailVerified(true);

        assertThatThrownBy(() -> emailVerificationService.requestEmailChange(owner, "new@test.com"))
                .isInstanceOf(OwnerEmailImmutableException.class);

        // Rejected before any token work — no uniqueness query, no save, no event.
        verify(userRepository, never()).existsByEmailExcludingId(any(), any());
        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestEmailChange_newEmailInUseByAnother_throwsUserAlreadyExists() {
        User user = verifiedUser();
        given(userRepository.existsByEmailExcludingId("taken@test.com", 1)).willReturn(true);

        assertThatThrownBy(() -> emailVerificationService.requestEmailChange(user, "taken@test.com"))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestEmailChange_sameAsCurrentEmail_throwsIllegalArgument() {
        User user = verifiedUser();

        assertThatThrownBy(() -> emailVerificationService.requestEmailChange(user, "  Alice@Test.com "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void requestEmailChange_withinCooldown_throwsIllegalState() {
        User user = verifiedUser();
        given(userRepository.existsByEmailExcludingId("new@test.com", 1)).willReturn(false);
        EmailVerificationToken recent = new EmailVerificationToken();
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        given(tokenRepository.findTopByUserOrderByCreatedAtDesc(user)).willReturn(Optional.of(recent));
        given(tokenRepository.countByUserAndCreatedAtAfter(any(), any())).willReturn(1L);
        given(properties.cooldownSeconds()).willReturn(60L);
        given(properties.dailyCap()).willReturn(5);

        assertThatThrownBy(() -> emailVerificationService.requestEmailChange(user, "new@test.com"))
                .isInstanceOf(IllegalStateException.class);

        verify(tokenRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── confirmEmailChange ──────────────────────────────────────────────────────

    @Test
    void confirmEmailChange_validToken_swapsEmailInvalidatesSessionAndPublishesCompletion() {
        User user = verifiedUser();
        EmailVerificationToken stored = emailChangeToken(user, "confirm-raw", "new@test.com",
                LocalDateTime.now().plusHours(1));
        given(tokenRepository.findByToken(emailVerificationService.hashToken("confirm-raw")))
                .willReturn(Optional.of(stored));
        given(userRepository.existsByEmailExcludingId("new@test.com", 1)).willReturn(false);

        emailVerificationService.confirmEmailChange("confirm-raw");

        assertThat(user.getEmail()).isEqualTo("new@test.com");
        assertThat(user.getTokenVersion()).isEqualTo(1); // sessions invalidated
        verify(userRepository).save(user);
        verify(tokenRepository).deleteByUserAndTokenType(user, TokenType.EMAIL_CHANGE);
        verify(refreshTokenService).deleteAllByUser(user);

        ArgumentCaptor<EmailChangeCompleted> eventCaptor = ArgumentCaptor.forClass(EmailChangeCompleted.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().oldEmail()).isEqualTo("alice@test.com");
        assertThat(eventCaptor.getValue().newEmail()).isEqualTo("new@test.com");
    }

    @Test
    void confirmEmailChange_verificationTypeToken_throwsInvalidAndDoesNotConsume() {
        User user = verifiedUser();
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUser(user);
        verificationToken.setTokenType(TokenType.VERIFICATION);
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(1));
        given(tokenRepository.findByToken(any())).willReturn(Optional.of(verificationToken));

        assertThatThrownBy(() -> emailVerificationService.confirmEmailChange("raw"))
                .isInstanceOf(InvalidVerificationTokenException.class);

        assertThat(user.getEmail()).isEqualTo("alice@test.com");
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).deleteAllByUser(any());
    }

    @Test
    void confirmEmailChange_unknownToken_throwsInvalid() {
        given(tokenRepository.findByToken(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.confirmEmailChange("nope"))
                .isInstanceOf(InvalidVerificationTokenException.class);
    }

    @Test
    void confirmEmailChange_expiredToken_throwsExpired() {
        User user = verifiedUser();
        EmailVerificationToken stored = emailChangeToken(user, "confirm-raw", "new@test.com",
                LocalDateTime.now().minusMinutes(1));
        given(tokenRepository.findByToken(any())).willReturn(Optional.of(stored));

        assertThatThrownBy(() -> emailVerificationService.confirmEmailChange("confirm-raw"))
                .isInstanceOf(VerificationTokenExpiredException.class);

        assertThat(user.getEmail()).isEqualTo("alice@test.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmEmailChange_newEmailTakenSinceRequest_throwsUserAlreadyExists() {
        User user = verifiedUser();
        EmailVerificationToken stored = emailChangeToken(user, "confirm-raw", "new@test.com",
                LocalDateTime.now().plusHours(1));
        given(tokenRepository.findByToken(any())).willReturn(Optional.of(stored));
        given(userRepository.existsByEmailExcludingId("new@test.com", 1)).willReturn(true);

        assertThatThrownBy(() -> emailVerificationService.confirmEmailChange("confirm-raw"))
                .isInstanceOf(UserAlreadyExistsException.class);

        assertThat(user.getEmail()).isEqualTo("alice@test.com");
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).deleteAllByUser(any());
    }

    // ─── cancelEmailChange ───────────────────────────────────────────────────────

    @Test
    void cancelEmailChange_validCancelToken_deletesPendingTokenWithoutChangingEmail() {
        User user = verifiedUser();
        EmailVerificationToken stored = emailChangeToken(user, "confirm-raw", "new@test.com",
                LocalDateTime.now().plusHours(1));
        given(tokenRepository.findByCancelToken(emailVerificationService.hashToken("cancel-raw")))
                .willReturn(Optional.of(stored));

        emailVerificationService.cancelEmailChange("cancel-raw");

        verify(tokenRepository).delete(stored);
        assertThat(user.getEmail()).isEqualTo("alice@test.com");
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).deleteAllByUser(any());
    }

    @Test
    void cancelEmailChange_unknownToken_throwsInvalid() {
        given(tokenRepository.findByCancelToken(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.cancelEmailChange("nope"))
                .isInstanceOf(InvalidVerificationTokenException.class);

        verify(tokenRepository, never()).delete(any(EmailVerificationToken.class));
    }

    private EmailVerificationToken emailChangeToken(User user, String rawConfirmToken,
                                                    String pendingEmail, LocalDateTime expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenType(TokenType.EMAIL_CHANGE);
        token.setPendingEmail(pendingEmail);
        token.setToken(emailVerificationService.hashToken(rawConfirmToken));
        token.setExpiresAt(expiresAt);
        return token;
    }
}
