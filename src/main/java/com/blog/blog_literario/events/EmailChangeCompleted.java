package com.blog.blog_literario.events;

/**
 * Published when an email change is confirmed and the address has been swapped. An
 * {@code AFTER_COMMIT} listener sends an informational notice (no action link) to the
 * old address, closing the loop for the previous owner: the change they were told was
 * pending has now taken effect, and they are told where to turn if it wasn't them.
 *
 * @param oldEmail        the former address (notice recipient)
 * @param newEmail        the address the account now uses
 * @param name            recipient display name
 * @param idempotencyKey  provider-level deduplication key, unique per confirmed change
 */
public record EmailChangeCompleted(
        String oldEmail,
        String newEmail,
        String name,
        String idempotencyKey) {
}
