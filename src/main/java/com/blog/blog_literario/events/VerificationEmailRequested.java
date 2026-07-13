package com.blog.blog_literario.events;

/**
 * Domain event published when a verification email should go out — on registration
 * and on resend. Published inside the transaction that persists the token, and
 * consumed by an {@code AFTER_COMMIT} listener, so the email is only sent once the
 * user and token rows are durably committed.
 *
 * @param email          recipient address
 * @param name           recipient display name
 * @param rawToken       unhashed verification token to embed in the link
 * @param idempotencyKey provider-level deduplication key, unique per issued token
 */
public record VerificationEmailRequested(String email, String name, String rawToken, String idempotencyKey) {
}
