package com.blog.blog_literario.dto.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/admin/posts/{id}/status}.
 *
 * <p>{@code status} must be one of {@code DRAFT}, {@code PUBLISHED}, {@code ARCHIVED},
 * or {@code REJECTED}. {@code moderationNote} is shown to the author and is required
 * by {@link com.blog.blog_literario.services.admin.AdminPostModerationService} when
 * {@code status = REJECTED}; optional for all other transitions.
 */
public record AdminStatusUpdateRequest(
        @NotBlank(message = "El estado no puede estar vacío")
        String status,
        String moderationNote
        ) {

}
