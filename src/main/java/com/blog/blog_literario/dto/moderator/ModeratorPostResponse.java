package com.blog.blog_literario.dto.moderator;

import java.time.LocalDateTime;

/**
 * Moderator-facing view of a post, returned by {@code /api/moderator/posts/**}.
 *
 * <p>Similar to {@code AdminPostResponse} but scoped to what a moderator needs:
 * {@code moderationNote}/{@code moderatedByName}/{@code moderatedAt} describe the
 * most recent moderation action, {@code rejectionCount} tracks accumulated
 * rejections, {@code isPermanentlyBlocked} is {@code true} at 3 rejections (only an
 * admin can reset it), and {@code isLastAttempt} flags posts where the next
 * rejection will permanently block them.
 */
public record ModeratorPostResponse(
        Integer id,
        String title,
        String slug,
        String status,
        String statusDisplayName,
        Integer authorId,
        String authorName,
        String categoryName,
        String coverImage,
        String moderationNote,
        String moderatedByName,
        LocalDateTime moderatedAt,
        int rejectionCount,
        boolean isPermanentlyBlocked,
        boolean isLastAttempt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean featured
    ) {}
