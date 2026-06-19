package com.blog.blog_literario.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Immutable record of a destructive or workflow-overriding action performed by an
 * ADMIN — e.g. forcing a post status transition, unblocking a permanently-rejected
 * post, deleting a post, resetting a user's password, or changing a user's role.
 *
 * <p>{@code adminEmail} is captured at write time (not just {@code adminId}) so the
 * log entry remains readable even if the admin account is later deleted.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "admin_action_logs",
    indexes = {
        @Index(name = "idx_admin_action_log_admin_id", columnList = "admin_id"),
        @Index(name = "idx_admin_action_log_target", columnList = "target_type, target_id"),
        @Index(name = "idx_admin_action_log_created_at", columnList = "created_at")
    }
)
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "admin_id", nullable = false)
    private Integer adminId;

    @Column(name = "admin_email", nullable = false, length = 255)
    private String adminEmail;

    /** e.g. {@code POST_STATUS_FORCE}, {@code POST_RESET}, {@code POST_DELETE}, {@code USER_PASSWORD_RESET}, {@code USER_ROLE_CHANGE}. */
    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    /** e.g. {@code POST}, {@code USER}. */
    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Integer targetId;

    /** Free-text description of what changed (old → new value, note/reason). */
    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
