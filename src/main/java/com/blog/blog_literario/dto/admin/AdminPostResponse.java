package com.blog.blog_literario.dto.admin;

import java.time.LocalDateTime;

/**
 * Admin-facing view of a post, returned by {@code /api/admin/posts/**}.
 *
 * <p>Includes moderation metadata not exposed to public/author views:
 * {@code moderationNote}/{@code moderatedByName}/{@code moderatedAt} describe the
 * most recent moderation action, {@code rejectionCount} tracks how many times the
 * post has been rejected, and {@code isPermanentlyBlocked} is {@code true} once
 * that count reaches 3 (only {@code PUT /api/admin/posts/{id}/reset} can clear it).
 */
public record AdminPostResponse(
        Integer id,
        String title,
        String slug,
        String status,
        String statusDisplayName,
        String authorName,
        String authorEmail,
        String categoryName,
        String coverImage,
        String moderationNote,
        String moderatedByName,
        LocalDateTime moderatedAt,
        int rejectionCount,
        boolean isPermanentlyBlocked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
