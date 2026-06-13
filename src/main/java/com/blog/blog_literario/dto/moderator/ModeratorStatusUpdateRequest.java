package com.blog.blog_literario.dto.moderator;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/moderator/posts/{id}/status}.
 *
 * <p>{@code status} must be one of {@code DRAFT}, {@code PUBLISHED}, {@code ARCHIVED},
 * or {@code REJECTED}; {@link com.blog.blog_literario.services.moderator.ModeratorPostService}
 * enforces which transitions are allowed from the post's current status.
 */
public record ModeratorStatusUpdateRequest(

        @NotBlank(message = "El estado no puede estar vacío")
        String status,

        /**
         * Note for the author. Required by the service when status = REJECTED.
         * Optional for all other transitions.
         */
        String moderationNote

) {}