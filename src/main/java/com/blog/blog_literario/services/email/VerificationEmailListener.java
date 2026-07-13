package com.blog.blog_literario.services.email;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.events.VerificationEmailRequested;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends the verification email once the transaction that created the token has
 * committed — {@code AFTER_COMMIT} guarantees the link can never reference a token
 * that was rolled back, and {@code @Async} keeps the HTTP response from waiting on
 * the email provider.
 *
 * <p>Failures are logged, never rethrown: the user can always trigger a new email
 * through the resend endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationEmailListener {

    private final EmailService emailService;
    private final FrontendProperties frontendProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationEmailRequested(VerificationEmailRequested event) {
        try {
            String verificationUrl = frontendProperties.url() + "/verify-email?token=" + event.rawToken();
            emailService.sendVerificationEmail(
                    event.email(), event.name(), verificationUrl, event.idempotencyKey());
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", event.email(), e);
        }
    }
}
