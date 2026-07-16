package com.blog.blog_literario.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;

/**
 * Repository for {@link Post} entities.
 *
 * Provides queries for three access patterns: public (published posts only),
 * author-owned (all statuses for a given user), and admin moderation (filtered by
 * status, date range, or full-text search).
 */
public interface PostRepository extends JpaRepository<Post, Integer>, JpaSpecificationExecutor<Post> {

    /** Fetches author/category/moderatedBy eagerly to avoid N+1 queries when mapping to response DTOs. */
    @EntityGraph(attributePaths = {"author", "category", "moderatedBy"})
    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    /** Fetches author/category eagerly to avoid N+1 queries when mapping to response DTOs. */
    @EntityGraph(attributePaths = {"author", "category"})
    Page<Post> findByAuthorIdAndStatus(Integer authorId, PostStatus status, Pageable pageable);

    Optional<Post> findBySlugAndStatus(String slug, PostStatus status);

    /** Convenience overload for the most common public lookup — finds a published post by slug. */
    default Optional<Post> findPublishedBySlug(String slug) {
        return findBySlugAndStatus(slug, PostStatus.PUBLISHED);
    }

    /** Fetches author/category/moderatedBy eagerly to avoid N+1 queries when mapping to response DTOs. */
    @EntityGraph(attributePaths = {"author", "category", "moderatedBy"})
    Page<Post> findByAuthorId(Integer authorId, Pageable pageable);

    Optional<Post> findByIdAndAuthorId(Integer id, Integer authorId);

    List<Post> findByCategoryId(Integer categoryId);

    /** Used to guard against deleting a category that still has posts referencing it. */
    long countByCategoryId(Integer categoryId);

    /** Fetches author/category eagerly to avoid N+1 queries when mapping to response DTOs. */
    @EntityGraph(attributePaths = {"author", "category"})
    Page<Post> findByCategoryIdAndStatus(Integer categoryId, PostStatus status, Pageable pageable);

    /**
     * Full-text title search restricted to published posts.
     *
     * @param searchTerm substring to match (case-insensitive)
     */
    @Query("SELECT p FROM Post p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND p.status = com.blog.blog_literario.model.PostStatus.PUBLISHED")
    Page<Post> searchByTitle(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Full-text search across title and content, restricted to published posts.
     *
     * @param searchTerm substring to match (case-insensitive)
     */
    @Query("SELECT p FROM Post p WHERE (LOWER(p.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(p.content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND p.status = com.blog.blog_literario.model.PostStatus.PUBLISHED")
    Page<Post> searchInTitleOrContent(@Param("searchTerm") String searchTerm, Pageable pageable);

    boolean existsBySlug(String slug);

    /**
     * Checks whether a slug is already used by a different post.
     * Used during updates to enforce slug uniqueness while excluding the post being edited.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Post p WHERE p.slug = :slug AND p.id != :postId")
    boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("postId") Integer postId);

    /**
     * Returns all posts by a given author without pagination.
     * Used for bulk operations such as cascading deletes.
     */
    @Query("SELECT p FROM Post p WHERE p.author.id = :authorId")
    List<Post> findAllByAuthorId(@Param("authorId") Integer authorId);

    /** Deletes all posts authored by the given user; used when an author account is removed. */
    void deleteAllByAuthorId(Integer authorId);

    /**
     * Clears the {@code moderatedBy} reference on any post moderated by the given user,
     * preventing an orphaned foreign key when that user account is deleted.
     */
    @Modifying
    @Query("UPDATE Post p SET p.moderatedBy = NULL WHERE p.moderatedBy.id = :userId")
    void clearModeratedByForUser(@Param("userId") Integer userId);

    /**
     * Overrides {@link JpaRepository#findAll(Pageable)} to fetch author/category/moderatedBy
     * eagerly, avoiding N+1 queries when mapping to response DTOs (used for unfiltered
     * admin/moderator post listings).
     */
    @Override
    @EntityGraph(attributePaths = {"author", "category", "moderatedBy"})
    Page<Post> findAll(Pageable pageable);

    /** Convenience overload — returns all published posts; avoids repeating the status constant at call sites. */
    default Page<Post> findPublishedPosts(Pageable pageable) {
        return findByStatus(PostStatus.PUBLISHED, pageable);
    }

    default Page<Post> findPublishedByCategoryId(Integer categoryId, Pageable pageable) {
        return findByCategoryIdAndStatus(categoryId, PostStatus.PUBLISHED, pageable);
    }

    default Page<Post> findMyDrafts(Integer authorId, Pageable pageable) {
        return findByAuthorIdAndStatus(authorId, PostStatus.DRAFT, pageable);
    }

    /** Rows created before V7 whose word_count the startup backfill hasn't computed yet. */
    List<Post> findByWordCountIsNull();
}
