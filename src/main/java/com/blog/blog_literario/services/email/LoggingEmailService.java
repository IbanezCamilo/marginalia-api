package com.blog.blog_literario.services.email;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.model.PostStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Logging implementation of {@link EmailService}, active when
 * {@code email.provider=logging} or when the property is missing (local dev, tests).
 *
 * <p>Writes the verification URL to the log instead of sending anything, so the
 * full registration flow can be exercised without a Resend API key.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    @Override
    public void sendVerificationEmail(String to, String userName, String verificationUrl, String idempotencyKey) {
        log.info("[email.provider=logging] Verification email for {} <{}>: {}", userName, to, verificationUrl);
    }

    @Override
    public void sendAuthorRequestNotification(List<String> to, String requesterName, String requesterEmail,
            String motivation, String adminPanelUrl, String idempotencyKey) {
        log.info("[email.provider=logging] Author request notification to {} — {} <{}> wrote: {}",
                to, requesterName, requesterEmail, motivation);
    }

    @Override
    public void sendPostModerationNotification(String to, String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote,
            String postsUrl, String idempotencyKey) {
        log.info("[email.provider=logging] Post moderation notification for {} <{}>: \"{}\" {} -> {} (nota: {})",
                authorName, to, postTitle, previousStatus.name(), newStatus.name(), moderationNote);
    }
}
