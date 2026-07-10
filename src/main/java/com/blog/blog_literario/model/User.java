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
 * Application user entity.
 *
 * Every user has exactly one {@link Role} (READER, AUTHOR, or ADMIN) which
 * determines their access level. The role is loaded eagerly because it is
 * required on every authenticated request.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users",
        indexes = {
            @Index(name = "idx_user_email", columnList = "email"),
            @Index(name = "idx_user_role", columnList = "role_id")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "profile_picture", length = 500)
    private String profilePicture;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @ManyToOne(fetch = FetchType.EAGER) // EAGER — role is always needed for every authenticated request
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * Returns true if the user has the admin role.
     */
    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }

    /**
     * Returns true if the user has the author role.
     */
    public boolean isAuthor() {
        return role != null && role.isAuthor();
    }

    /**
     * Returns the role name, or {@code "UNKNOWN"} if no role is set.
     */
    public String getRoleName() {
        return role != null ? role.getName() : "UNKNOWN";
    }

    /**
     * Invalidates every access token issued before this call. Must be invoked
     * whenever the role or password changes, so {@code JwtService} rejects
     * tokens minted under the old state even if they haven't expired yet.
     */
    public void incrementTokenVersion() {
        this.tokenVersion = (this.tokenVersion == null ? 0 : this.tokenVersion) + 1;
    }

    /**
     * Constructor for read-only projections — omits password and audit fields.
     */
    public User(Integer id, String name, String email, Role role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }
}
