package com.blog.blog_literario.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
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

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "slug", nullable = false, unique = true, length = 250)
    private String slug;

    @Column(name = "cover_image", length = 500)
    private String coverImage;

    // RELATIONSHIPS
    @ManyToOne(fetch = FetchType.LAZY) // ✅ LAZY para mejor performance
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY) // ✅ LAZY para mejor performance
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    // ============================================
    // LIFECYCLE CALLBACKS
    // ============================================
    /**
     * ✅ Establece timestamps automáticamente al crear
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * ✅ Actualiza timestamp automáticamente al modificar
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ============================================
    // CUSTOM CONSTRUCTOR (sin timestamps)
    // ============================================
    /**
     * Constructor personalizado para crear posts Los timestamps se establecen
     * automáticamente con @PrePersist
     */
    public Post(String title, String content, String status, String slug,
            User author, Category category) {
        this.title = title;
        this.content = content;
        this.status = status;
        this.slug = slug;
        this.author = author;
        this.category = category;
    }

    // ============================================
    // UTILITY METHODS
    // ============================================
    /**
     * ✅ Método para verificar si el post está publicado
     */
    public boolean isPublished() {
        return "PUBLISHED".equalsIgnoreCase(status);
    }

    /**
     * ✅ Método para verificar si el post es borrador
     */
    public boolean isDraft() {
        return "DRAFT".equalsIgnoreCase(status);
    }

    /**
     * ✅ toString personalizado (Lombok @Data lo genera, pero sin relaciones)
     * Evita LazyInitializationException al hacer toString()
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
