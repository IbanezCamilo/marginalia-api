package com.blog.blog_literario.events;

import java.util.List;

/**
 * Domain event published when a READER submits a new author request. Published
 * inside the transaction that persists the request, and consumed by an
 * {@code AFTER_COMMIT} listener that notifies the admins by email — so the
 * notification can never reference a request that was rolled back.
 *
 * <p>The admin email list rides in the event because the listener runs
 * asynchronously after commit, outside any transaction, and must not touch
 * repositories.
 *
 * @param requestId      ID of the persisted request
 * @param requesterName  requester's display name
 * @param requesterEmail requester's email address
 * @param motivation     requester's free-text motivation (may be null)
 * @param adminEmails    addresses of every ADMIN/OWNER user at submission time
 * @param idempotencyKey provider-level deduplication key, unique per request
 *                       (e.g. {@code author-request/<id>})
 */
public record AuthorRequestSubmitted(
        Integer requestId,
        String requesterName,
        String requesterEmail,
        String motivation,
        List<String> adminEmails,
        String idempotencyKey
) {
}
