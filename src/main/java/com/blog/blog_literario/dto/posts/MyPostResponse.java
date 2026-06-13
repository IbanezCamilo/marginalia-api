package com.blog.blog_literario.dto.posts;

import java.time.LocalDateTime;

/**
 * Author-facing view of one of their own posts, returned by {@code /api/me/posts/**}.
 *
 * <p>The moderation fields let the author UI explain why a post was rejected and
 * whether it can still be resubmitted:
 * <ul>
 *   <li>{@code moderationNote} — feedback left by the moderator/admin on the last action.</li>
 *   <li>{@code rejectionCount} — how many times this post has been rejected (max 3).</li>
 *   <li>{@code canBeResubmitted} — {@code true} if the author can still move it back to {@code DRAFT}.</li>
 *   <li>{@code isLastAttempt} — {@code true} if one more rejection will permanently block the post.</li>
 * </ul>
 */
public record MyPostResponse(
        Integer id,
        String title,
        String content,
        String status,
        String slug,
        String authorName,
        Integer categoryId,
        String categoryName,
        String coverImage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        //Moderation fields
        String moderationNote,
        int rejectionCount,
        boolean canBeResubmitted,
        boolean isLastAttempt
        ) {
}
