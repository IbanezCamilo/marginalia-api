package com.blog.blog_literario.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Persistent email verification token entity.
 *
 * <p>The token value is stored as a SHA-256 hash — never as plain text; the raw
 * token only travels inside the verification link emailed to the user. Superseded
 * tokens are expired ({@code expiresAt = now}) rather than deleted so the resend
 * cooldown and daily cap can be derived from {@code createdAt}; a scheduled job
 * purges stale rows afterwards.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "email_verification_tokens",
    indexes = {
        @Index(name = "idx_email_verification_tokens_token",   columnList = "token"),
        @Index(name = "idx_email_verification_tokens_user_id", columnList = "user_id")
    }
)
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
