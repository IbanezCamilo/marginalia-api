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

    @ManyToOne(fetch = FetchType.LAZY) // LAZY — avoids N+1 on list queries
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY) // LAZY — avoids N+1 on list queries
    @JoinColumn(name = "category_id", nullable = false)
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

    public boolean canBeEditedByAuthor() {
        return this.status.canBeEditedByAuthor();
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
