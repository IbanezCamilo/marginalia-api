package com.blog.blog_literario.services.email;

import java.util.List;

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

    /**
     * Notifies the admins that a new author request was submitted.
     *
     * @param to             recipient addresses (all ADMIN/OWNER users); an empty
     *                       list is a no-op
     * @param requesterName  requester's display name
     * @param requesterEmail requester's email address
     * @param motivation     requester's free-text motivation (may be null)
     * @param adminPanelUrl  absolute frontend URL of the admin requests panel
     * @param idempotencyKey provider-level deduplication key for network retries,
     *                       unique per request (e.g. {@code author-request/<id>})
     */
    void sendAuthorRequestNotification(List<String> to, String requesterName, String requesterEmail,
            String motivation, String adminPanelUrl, String idempotencyKey);
}
