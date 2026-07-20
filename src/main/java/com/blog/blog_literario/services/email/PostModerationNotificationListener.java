package com.blog.blog_literario.services.email;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.events.PostModerationStatusChanged;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Emails the author once the moderation transaction has committed —
 * {@code AFTER_COMMIT} guarantees the notification can never reference a status
 * change that was rolled back, and {@code @Async} keeps the HTTP response from
 * waiting on the email provider.
 *
 * <p>Failures are logged, never rethrown: the moderation result stands
 * regardless of email delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostModerationNotificationListener {

    private final EmailService emailService;
    private final FrontendProperties frontendProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostModerationStatusChanged(PostModerationStatusChanged event) {
        try {
            String postsUrl = frontendProperties.url() + "/user/posts";
            emailService.sendPostModerationNotification(
                    event.authorEmail(), event.authorName(), event.postTitle(),
                    event.previousStatus(), event.newStatus(), event.moderationNote(),
                    postsUrl, event.idempotencyKey());
        } catch (Exception e) {
            log.error("Failed to send post moderation notification for post {}", event.postId(), e);
        }
    }
}
