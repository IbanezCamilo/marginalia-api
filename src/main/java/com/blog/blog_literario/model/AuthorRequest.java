package com.blog.blog_literario.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a READER's request to obtain the AUTHOR role.
 *
 * Business rules (enforced at the service layer):
 *   - Only users with role READER may submit requests.
 *   - A user may only have one PENDING request at a time.
 *   - Only PENDING requests can be approved or rejected.
 *
 * approve() and reject() encapsulate the state transition
 * so the service does not set fields directly.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "author_requests",
    indexes = {
        @Index(name = "idx_author_request_requester_status", columnList = "requester_id, status"),
        @Index(name = "idx_author_request_status_created",   columnList = "status, created_at")
    }
)
public class AuthorRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // ─── Who is requesting ─────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    // ─── Request content ───────────────────────────────────────────────────────

    /**
     * Free-text explanation of why the user wants to become an AUTHOR.
     * Optional but recommended — helps the admin make a better decision.
     */
    @Column(name = "motivation", length = 1000)
    private String motivation;

    // ─── Status ────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuthorRequestStatus status = AuthorRequestStatus.PENDING;

    // ─── Resolution ────────────────────────────────────────────────────────────

    /**
     * Admin's note on approval or rejection.
     * On rejection, this gives the user feedback on what to improve.
     */
    @Column(name = "admin_note", length = 500)
    private String adminNote;

    /**
     * The admin who processed the request.
     * Null while the request is PENDING.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    // ─── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Set when the request is approved or rejected.
     * Null while the request is PENDING.
     */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ─── Utility methods ───────────────────────────────────────────────────────

    public boolean isPending()  { return status.isPending();  }
    public boolean isApproved() { return status.isApproved(); }
    public boolean isRejected() { return status.isRejected(); }

    /**
     * Approves this request.
     * Note: the service is responsible for changing the requester's role to AUTHOR.
     * This method only updates the request state itself.
     */
    public void approve(User admin, String note) {
        this.status     = AuthorRequestStatus.APPROVED;
        this.resolvedBy = admin;
        this.adminNote  = note;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * Rejects this request.
     * The requester keeps their READER role.
     */
    public void reject(User admin, String note) {
        this.status     = AuthorRequestStatus.REJECTED;
        this.resolvedBy = admin;
        this.adminNote  = note;
        this.resolvedAt = LocalDateTime.now();
    }
}