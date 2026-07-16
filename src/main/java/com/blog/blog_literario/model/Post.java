package com.blog.blog_literario.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Blog post entity.
 *
 * A post belongs to exactly one author ({@link User}) and one {@link Category}.
 * Status transitions are managed by the service layer; the entity only enforces
 * timestamps via JPA lifecycle callbacks.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    /** Nullable: a draft may exist with no title until the author publishes it. */
    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status = PostStatus.DRAFT; // default value

    /** Nullable: only generated once the post has a title; Postgres permits multiple NULLs under UNIQUE. */
    @Column(name = "slug", unique = true, length = 250)
    private String slug;

    @Column(name = "cover_image", length = 500)
    private String coverImage;

    /** Horizontal cover focal point, normalized to [0,1]; rendered by the frontend as CSS object-position. Defaults to center. */
    @Column(name = "focal_x", nullable = false, precision = 4, scale = 3)
    private BigDecimal focalX = new BigDecimal("0.5");

    /** Vertical cover focal point, normalized to [0,1]; rendered by the frontend as CSS object-position. Defaults to center. */
    @Column(name = "focal_y", nullable = false, precision = 4, scale = 3)
    private BigDecimal focalY = new BigDecimal("0.5");

    /** Editorial curation flag set by moderators/admins; featured posts surface first in the public catalog. */
    @Column(name = "featured", nullable = false)
    private boolean featured = false;

    /** Feedback left by a moderator/admin, shown to the author (e.g. why a post was rejected). */
    @Column(name = "moderation_note", length=500)
    private String moderationNote;

    /** Number of times this post has been rejected; reaching 3 permanently blocks resubmission. */
    @Column(name = "rejection_count", nullable = false)
    private int rejectionCount = 0;

    /** Timestamp of the most recent moderation action (status change, rejection, or reset). */
    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    /** Moderator or admin who performed the most recent moderation action. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private User moderatedBy;

    @ManyToOne(fetch = FetchType.LAZY) // LAZY — avoids N+1 on list queries
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    /** Nullable: a draft may exist with no category until the author publishes it. */
    @ManyToOne(fetch = FetchType.LAZY) // LAZY — avoids N+1 on list queries
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * Sets timestamps automatically on entity creation.
     * Also records {@code publishedAt} when the initial status is PUBLISHED.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        // If created as PUBLISHED, set publishedAt
        if (status == PostStatus.PUBLISHED) {
            publishedAt = now;
        }
    }

    /**
     * Updates the {@code updatedAt} timestamp on every modification.
     * Records {@code publishedAt} the first time the post transitions to PUBLISHED.
     */
    @PreUpdate
    protected void onUpdate() {
        LocalDateTime now = LocalDateTime.now();
        updatedAt = now;
        // Set publishedAt when status changes to PUBLISHED (and it wasn't already set)
        if (status == PostStatus.PUBLISHED && publishedAt == null) {
            publishedAt = now;
        }
    }

    public Post(String title, String content, PostStatus status, String slug,
            User author, Category category) {
        this.title = title;
        this.content = content;
        this.status = status != null ? status : PostStatus.DRAFT;
        this.slug = slug;
        this.author = author;
        this.category = category;
    }

    public boolean isPublished() {
        return this.status == PostStatus.PUBLISHED;
    }

    public boolean isDraft() {
        return this.status == PostStatus.DRAFT;
    }

    public boolean isRejected() {
        return this.status == PostStatus.REJECTED;
    }

    public boolean isArchived() {
        return this.status == PostStatus.ARCHIVED;
    }

    /** True while the author may still resubmit a rejected post (fewer than 3 rejections, not archived). */
    public boolean canBeResubmitted() {
        return rejectionCount < 3 && status != PostStatus.ARCHIVED;
    }

    /** True once the post has accumulated 3 rejections; only an admin reset via {@link #resetForAuthor()} can unblock it. */
    public boolean isPermanentlyBlocked() {
        return rejectionCount >= 3;
    }

    /** True if the post has 2 rejections, meaning the next rejection will permanently block it. */
    public boolean isLastAttempt() {
        return rejectionCount == 2;
    }

    public boolean canBeEditedByAuthor() {
        return this.status.canBeEditedByAuthor();
    }

    /** Records who performed the most recent moderation action, when, and any feedback for the author. */
    public void recordModeration(User moderator, String note) {
        this.moderatedBy  = moderator;
        this.moderatedAt  = LocalDateTime.now();
        this.moderationNote = note;
    }

    public void incrementRejectionCount() {
        this.rejectionCount++;
    }

    /**
     * Clears moderation state and returns the post to {@code DRAFT}, allowing the
     * author to resubmit a post that was previously permanently blocked.
     * Used by {@link com.blog.blog_literario.services.admin.AdminPostModerationService#resetPost}.
     */
    public void resetForAuthor() {
        this.status = PostStatus.DRAFT;
        this.rejectionCount = 0;
        this.moderationNote = null;
        this.moderatedBy    = null;
        this.moderatedAt    = null;
    }

    /**
     * Custom toString to avoid {@link org.hibernate.LazyInitializationException}
     * on lazy-loaded relations.
     */
    @Override
    public String toString() {
        return "Post{"
                + "id=" + id
                + ", title='" + title + '\''
                + ", status='" + status + '\''
                + ", slug='" + slug + '\''
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}
