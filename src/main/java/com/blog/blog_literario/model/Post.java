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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Column(name = "title", nullable = false, length = 200)
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

    @Column(name = "slug", nullable = false, unique = true, length = 250)
    private String slug;

    @Column(name = "cover_image", length = 500)
    private String coverImage;

    // RELATIONSHIPS
    @ManyToOne(fetch = FetchType.LAZY) // ✅ LAZY — loaded on demand
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY) // ✅ LAZY — loaded on demand
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // ============================================
    // LIFECYCLE CALLBACKS
    // ============================================
    /**
     * Sets timestamps automatically on entity creation
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
     * Updates the updatedAt timestamp on every modification
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

    // ============================================
    // CUSTOM CONSTRUCTOR (no timestamp fields)
    // ============================================
    public Post(String title, String content, PostStatus status, String slug,
            User author, Category category) {
        this.title = title;
        this.content = content;
        this.status = status != null ? status : PostStatus.DRAFT; //default status if not Null
        this.slug = slug;
        this.author = author;
        this.category = category;
    }

    // ============================================
    // UTILITY METHODS
    // ============================================
    public boolean isPublished() {
        return this.status == PostStatus.PUBLISHED;
    }

    public boolean isDraft() {
        return this.status == PostStatus.DRAFT;
    }

    public boolean canBeEditedByAuthor() {
        return this.status.canBeEditedByAuthor();
    }

    /**
     * Custom toString to avoid LazyInitializationException on lazy-loaded relations
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
