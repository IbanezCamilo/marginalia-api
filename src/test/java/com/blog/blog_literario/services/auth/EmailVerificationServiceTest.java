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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.blog.blog_literario.config.properties.EmailVerificationProperties;
import com.blog.blog_literario.events.VerificationEmailRequested;
import com.blog.blog_literario.exception.InvalidVerificationTokenException;
import com.blog.blog_literario.exception.VerificationTokenExpiredException;
import com.blog.blog_literario.model.EmailVerificationToken;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.EmailVerificationTokenRepository;
import com.blog.blog_literario.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock EmailVerificationTokenRepository tokenRepository;
    @Mock UserRepository userRepository;
    @Mock EmailVerificationProperties properties;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks EmailVerificationService emailVerificationService;

    private User unverifiedUser() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setEmailVerified(false);
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
        assertThat(event.idempotencyKey()).isEqualTo("verify-email/10");

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
    void purgeExpiredTokens_deletesRowsPastTheCapWindow() {
        emailVerificationService.purgeExpiredTokens();

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(tokenRepository).deleteByExpiresAtBefore(thresholdCaptor.capture());
        // 24h grace after expiry so rows still counting toward the daily cap survive.
        assertThat(thresholdCaptor.getValue()).isBefore(LocalDateTime.now().minusHours(23));
    }
}
