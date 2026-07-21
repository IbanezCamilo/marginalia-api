package com.blog.blog_literario.services.email;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.events.EmailChangeCompleted;
import com.blog.blog_literario.events.EmailChangeRequested;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns email-change events into their emails once the originating transaction has
 * committed — {@code AFTER_COMMIT} guarantees the links can never reference a token that
 * was rolled back, and {@code @Async} keeps the HTTP response off the email provider.
 *
 * <p>On request, two emails go out: a confirmation link to the new address and a cancel
 * link to the current one. On completion, an informational notice goes to the old address.
 * Failures are logged, never rethrown — the request/confirm endpoints are the recovery path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChangeNotificationListener {

    private final EmailService emailService;
    private final FrontendProperties frontendProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailChangeRequested(EmailChangeRequested event) {
        try {
            String confirmUrl = frontendProperties.url() + "/confirm-email-change?token=" + event.confirmRawToken();
            String cancelUrl = frontendProperties.url() + "/cancel-email-change?token=" + event.cancelRawToken();
            // Distinct idempotency keys per email: one row's request fans out to two sends,
            // and Resend rejects a reused key carrying a different body.
            emailService.sendEmailChangeConfirmation(
                    event.newEmail(), event.name(), confirmUrl, event.idempotencyKey() + "/confirm");
            emailService.sendEmailChangeNotice(
                    event.oldEmail(), event.name(), event.newEmail(), cancelUrl, event.idempotencyKey() + "/cancel");
        } catch (Exception e) {
            log.error("Failed to send email-change request notifications to {}", event.newEmail(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailChangeCompleted(EmailChangeCompleted event) {
        try {
            emailService.sendEmailChangeCompletedNotice(
                    event.oldEmail(), event.name(), event.newEmail(), event.idempotencyKey());
        } catch (Exception e) {
            log.error("Failed to send email-change completed notice to {}", event.oldEmail(), e);
        }
    }
}
