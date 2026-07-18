package com.blog.blog_literario.services.email;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.events.AuthorRequestSubmitted;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Notifies the admins by email once the transaction that created an author
 * request has committed — {@code AFTER_COMMIT} guarantees the notification can
 * never reference a request that was rolled back, and {@code @Async} keeps the
 * HTTP response from waiting on the email provider.
 *
 * <p>Failures are logged, never rethrown: the request itself is safely stored
 * and remains visible in the admin panel regardless of email delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorRequestNotificationListener {

    private final EmailService emailService;
    private final FrontendProperties frontendProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuthorRequestSubmitted(AuthorRequestSubmitted event) {
        try {
            String adminPanelUrl = frontendProperties.url() + "/user/solicitudes";
            emailService.sendAuthorRequestNotification(
                    event.adminEmails(), event.requesterName(), event.requesterEmail(),
                    event.motivation(), adminPanelUrl, event.idempotencyKey());
        } catch (Exception e) {
            log.error("Failed to send author request notification for request {}", event.requestId(), e);
        }
    }
}
