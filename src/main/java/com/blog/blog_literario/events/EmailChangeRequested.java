package com.blog.blog_literario.events;

/**
 * Published when a user requests an email change. An {@code AFTER_COMMIT} listener turns
 * this into two emails: a confirmation link to the new address and a cancel link to the
 * current (old) address. Published inside the transaction that persists the token so the
 * links can never reference a row that was rolled back.
 *
 * @param newEmail          the requested new address (confirmation link recipient)
 * @param oldEmail          the current address (cancel-link recipient)
 * @param name              recipient display name
 * @param confirmRawToken   unhashed confirm token to embed in the new-address link
 * @param cancelRawToken    unhashed cancel token to embed in the old-address link
 * @param idempotencyKey    provider-level dedup base, unique per issued token; the listener
 *                          suffixes it per email so the two sends never collide
 */
public record EmailChangeRequested(
        String newEmail,
        String oldEmail,
        String name,
        String confirmRawToken,
        String cancelRawToken,
        String idempotencyKey) {
}
