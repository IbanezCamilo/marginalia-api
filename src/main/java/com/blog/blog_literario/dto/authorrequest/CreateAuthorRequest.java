package com.blog.blog_literario.dto.authorrequest;

import jakarta.validation.constraints.Size;

/**
 * Request body for submitting a new author request.
 *
 * motivation is optional — the user can submit without explaining themselves,
 * though the admin panel may show a warning for requests without motivation.
 * Max length matches the DB column (1000 chars).
 */
public record CreateAuthorRequest(
        @Size(max = 1000, message = "La motivación no puede superar los 1000 caracteres")
        String motivation
) {}