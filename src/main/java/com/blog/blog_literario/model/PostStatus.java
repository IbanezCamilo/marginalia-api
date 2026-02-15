package com.blog.blog_literario.model;

public enum PostStatus {
    DRAFT("Borrador"),
    PUBLISHED("Publicado"),
    ARCHIVED("Archivado"),
    REJECTED("Rechazado");

    //display name for UI
    private final String displayName;

    PostStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    //is this post visible to public?
    public boolean isVisibleToPublic() {
        return this == PUBLISHED;
    }

    // can this post be edited by author?
    public boolean canBeEditedByAuthor() {
        return this == DRAFT || this == REJECTED;
    }

    // can this post be moderated by admin?
    public boolean canBeModerated() {
        return this == DRAFT || this == PUBLISHED;
    }

    // is this post active (not archived)?
    public boolean isActive() {
        return this != ARCHIVED;
    }

}
