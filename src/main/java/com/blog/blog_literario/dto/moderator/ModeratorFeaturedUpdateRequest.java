package com.blog.blog_literario.dto.moderator;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /api/moderator/posts/{id}/featured}.
 *
 * <p>{@code featured} marks a post as editorially curated so it surfaces first in the
 * public catalog. Only {@code PUBLISHED} posts can be featured;
 * {@link com.blog.blog_literario.services.moderator.ModeratorPostService#updateFeatured}
 * enforces that rule (un-featuring is allowed in any status).
 */
public record ModeratorFeaturedUpdateRequest(

        @NotNull(message = "El campo featured es obligatorio")
        Boolean featured

) {}
