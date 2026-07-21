package com.blog.blog_literario.services.email;

import java.util.List;

import com.blog.blog_literario.model.PostStatus;

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

    /**
     * Notifies an author that moderation changed one of their posts' status.
     *
     * @param to             author's email address
     * @param authorName     author's display name, used in the greeting
     * @param postTitle      post title (never null; callers substitute a fallback for untitled drafts)
     * @param previousStatus status before the moderation action
     * @param newStatus      status after the moderation action (drives subject/headline copy)
     * @param moderationNote moderator's note for the author (may be null)
     * @param postsUrl       absolute frontend URL of the author's posts list
     * @param idempotencyKey provider-level deduplication key for network retries,
     *                       unique per moderation action (e.g. {@code post-moderation/<postId>/<uuid>})
     */
    void sendPostModerationNotification(String to, String authorName, String postTitle,
            PostStatus previousStatus, PostStatus newStatus, String moderationNote,
            String postsUrl, String idempotencyKey);

    /**
     * Sends the confirmation link for a pending email change to the <em>new</em> address.
     * Clicking it commits the change.
     *
     * @param to             the new address being confirmed
     * @param userName       display name used in the greeting
     * @param confirmUrl     absolute frontend URL containing the raw confirm token
     * @param idempotencyKey provider-level deduplication key (e.g. {@code email-change/<hash>/confirm})
     */
    void sendEmailChangeConfirmation(String to, String userName, String confirmUrl, String idempotencyKey);

    /**
     * Notifies the <em>current</em> address that an email change was requested, with a link
     * to cancel it. Lets the owner abort a change they didn't initiate.
     *
     * @param to             the current (old) address
     * @param userName       display name used in the greeting
     * @param newEmail       the requested new address, shown so the owner knows what was asked
     * @param cancelUrl      absolute frontend URL containing the raw cancel token
     * @param idempotencyKey provider-level deduplication key (e.g. {@code email-change/<hash>/cancel})
     */
    void sendEmailChangeNotice(String to, String userName, String newEmail, String cancelUrl, String idempotencyKey);

    /**
     * Informs the <em>old</em> address that the email change has completed. Informational
     * only — no action link — closing the loop opened by {@link #sendEmailChangeNotice}.
     *
     * @param to             the former address
     * @param userName       display name used in the greeting
     * @param newEmail       the address the account now uses
     * @param idempotencyKey provider-level deduplication key (e.g. {@code email-change-done/<hash>})
     */
    void sendEmailChangeCompletedNotice(String to, String userName, String newEmail, String idempotencyKey);
}
