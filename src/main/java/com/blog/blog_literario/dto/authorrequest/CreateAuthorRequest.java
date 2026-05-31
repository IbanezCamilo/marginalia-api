package com.blog.blog_literario.dto.authorrequest;

/**
 * Request body for submitting a new author request.
 *
 * motivation is optional — the user can submit without explaining themselves,
 * though the admin panel may show a warning for requests without motivation.
 * Max length matches the DB column (1000 chars).
 */
public record CreateAuthorRequest(
        String motivation
) {}