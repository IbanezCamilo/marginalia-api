package com.blog.blog_literario.model;

/**
 * Possible states of a request to obtain the AUTHOR role.
 *
 * Lifecycle:
 *   PENDING  → APPROVED  (admin approves, user is promoted to AUTHOR)
 *   PENDING  → REJECTED  (admin rejects, user remains as READER)
 *
 * A READER may only have one PENDING request at a time.
 * If rejected, they can submit a new request (new row, PENDING again).
 */
public enum AuthorRequestStatus {

    PENDING("Pendiente"),
    APPROVED("Aprobada"),
    REJECTED("Rechazada");

    private final String displayName;

    AuthorRequestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isPending()  { return this == PENDING;  }
    public boolean isApproved() { return this == APPROVED; }
    public boolean isRejected() { return this == REJECTED; }
}