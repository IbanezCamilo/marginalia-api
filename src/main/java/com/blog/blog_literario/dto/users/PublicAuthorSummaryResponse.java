package com.blog.blog_literario.dto.users;

/** Minimal author identity for the catalog's author facet — nothing more than the dropdown needs. */
public record PublicAuthorSummaryResponse(Integer id, String name) {
}
