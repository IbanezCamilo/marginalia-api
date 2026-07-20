package com.blog.blog_literario.events;

import com.blog.blog_literario.model.PostStatus;

/**
 * Domain event published when moderation changes a post's status. Published
 * inside the moderation transaction (only when the author should be notified —
 * see {@code PostModerationEventPublisher}) and consumed by an
 * {@code AFTER_COMMIT} listener that emails the author, so the notification can
 * never reference a change that was rolled back.
 *
 * <p>All recipient data rides in the event because the listener runs
 * asynchronously after commit, outside any transaction, and must not touch
 * repositories.
 *
 * @param postId         ID of the moderated post
 * @param postTitle      post title, never null ({@code "(sin título)"} for untitled drafts)
 * @param authorName     author's display name
 * @param authorEmail    author's email address
 * @param previousStatus status before the moderation action
 * @param newStatus      status after the moderation action
 * @param moderationNote moderator's note for the author (may be null)
 * @param idempotencyKey provider-level deduplication key, unique per action
 *                       ({@code post-moderation/<postId>/<uuid>})
 */
public record PostModerationStatusChanged(
        Integer postId,
        String postTitle,
        String authorName,
        String authorEmail,
        PostStatus previousStatus,
        PostStatus newStatus,
        String moderationNote,
        String idempotencyKey
) {
}
