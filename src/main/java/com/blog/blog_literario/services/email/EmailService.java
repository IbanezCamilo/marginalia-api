package com.blog.blog_literario.services.email;

/**
 * Outbound transactional email. Implementations are selected via the
 * {@code email.provider} property: {@code resend} ({@link ResendEmailService})
 * or {@code logging} ({@link LoggingEmailService}, the default for local dev and tests).
 *
 * <p>Implementations must never throw on delivery failure — sending happens after
 * the business transaction has committed, so there is nothing left to roll back.
 * They log the failure instead; the resend endpoint is the user-facing recovery path.
 */
public interface EmailService {

    /**
     * Sends the account verification email.
     *
     * @param to              recipient address
     * @param userName        display name used in the greeting
     * @param verificationUrl absolute frontend URL containing the raw verification token
     * @param idempotencyKey  provider-level deduplication key for network retries,
     *                        unique per issued token (e.g. {@code verify-email/<token-hash>})
     */
    void sendVerificationEmail(String to, String userName, String verificationUrl, String idempotencyKey);
}
