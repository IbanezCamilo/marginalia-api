package com.blog.blog_literario.services.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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
}
