package com.blog.blog_literario.model;

/**
 * Lifecycle states of a {@link Post}.
 *
 * Transitions:
 *   DRAFT → PUBLISHED  (author submits; admin may also publish directly)
 *   PUBLISHED → ARCHIVED  (admin archives)
 *   DRAFT / PUBLISHED → REJECTED  (admin rejects)
 *   REJECTED → DRAFT  (author revises and re-submits)
 */
public enum PostStatus {
    DRAFT("Borrador"),
    PUBLISHED("Publicado"),
    ARCHIVED("Archivado"),
    REJECTED("Rechazado");

    private final String displayName;

    PostStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Only PUBLISHED posts are visible in the public feed. */
    public boolean isVisibleToPublic() {
        return this == PUBLISHED;
    }

    /** Authors may only edit DRAFT or REJECTED posts; published/archived ones are locked. */
    public boolean canBeEditedByAuthor() {
        return this == DRAFT || this == REJECTED;
    }

    /** Admin moderation (approve/reject) applies only to DRAFT and PUBLISHED posts. */
    public boolean canBeModerated() {
        return this == DRAFT || this == PUBLISHED;
    }

    /** Returns false only for ARCHIVED posts. */
    public boolean isActive() {
        return this != ARCHIVED;
    }

}
