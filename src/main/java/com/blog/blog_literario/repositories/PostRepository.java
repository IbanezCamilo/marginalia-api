package com.blog.blog_literario.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;

public interface PostRepository extends JpaRepository<Post, Integer> {

    //Public --Read Only--
    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    Page<Post> findByAuthorIdAndStatus(Integer authorId, PostStatus status, Pageable pageable);

    Optional<Post> findBySlugAndStatus(String slug, PostStatus status);

    default Optional<Post> findPublishedBySlug(String slug) {
        return findBySlugAndStatus(slug, PostStatus.PUBLISHED);
    }

    //Private --Legacy--
    Page<Post> findByAuthorId(Integer authorId, Pageable pageable);

    Optional<Post> findByIdAndAuthorId(Integer id, Integer authorId);

    List<Post> findByCategoryId(Integer categoryId);

    Page<Post> findByCategoryIdAndStatus(Integer categoryId, PostStatus status, Pageable pageable);

    // Search with custom query: paginated search by title for published posts
    @Query("SELECT p FROM Post p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND p.status = com.blog.blog_literario.model.PostStatus.PUBLISHED")
    Page<Post> searchByTitle(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Search with custom query: paginated search by content for published posts
    @Query("SELECT p FROM Post p WHERE (LOWER(p.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(p.content) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND p.status = com.blog.blog_literario.model.PostStatus.PUBLISHED")
    Page<Post> searchInTitleOrContent(@Param("searchTerm") String searchTerm, Pageable pageable);

    boolean existsBySlug(String slug);

    // Check if a slug exists for another post (used in update to ensure uniqueness)
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Post p WHERE p.slug = :slug AND p.id != :postId")
    boolean existsBySlugAndIdNot(@Param("slug") String slug, @Param("postId") Integer postId);

    /**
     * Finds all posts by a specific user without pagination
     */
    @Query("SELECT p FROM Post p WHERE p.user.id = :userId")
    List<Post> findAllByUserId(@Param("userId") Integer userId);

    /**
     * Deletes all posts authored by a specific user
     */
    void deleteAllByUserId(Integer userId);

    //AUDIT AND MODERATION
    //filter multiples status
    Page<Post> findByStatusIn(List<PostStatus> statuses, Pageable pageable);

    //count by status in list
    Long countByStatusIn(List<PostStatus> statuses);

    //Search with Date Range
    List<Post> findByStatusAndCreatedAtAfter(PostStatus status, LocalDateTime date);

    List<Post> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    //Convinience methods
    default Page<Post> findPublishedPosts(Pageable pageable) {
        return findByStatus(PostStatus.PUBLISHED, pageable);
    }

    default Page<Post> findPublishedByCategoryId(Integer categoryId, Pageable pageable) {
        return findByCategoryIdAndStatus(categoryId, PostStatus.PUBLISHED, pageable);
    }

    default Page<Post> findMyDrafts(Integer authorId, Pageable pageable) {
        return findByAuthorIdAndStatus(authorId, PostStatus.DRAFT, pageable);
    }
}
