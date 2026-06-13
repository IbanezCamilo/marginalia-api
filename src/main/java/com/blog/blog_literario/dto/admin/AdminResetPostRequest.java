package com.blog.blog_literario.dto.admin;

/**
 * Optional request body for {@code PUT /api/admin/posts/{id}/reset}.
 *
 * <p>Used to unlock a permanently-blocked post (3 accumulated rejections), returning
 * it to {@code DRAFT} with its rejection count cleared. {@code moderationNote} is
 * optional and, if provided, is shown to the author explaining why the post was
 * unlocked.
 */
public record AdminResetPostRequest(
    String moderationNote
) {}
