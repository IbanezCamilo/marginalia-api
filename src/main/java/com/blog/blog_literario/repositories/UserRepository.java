package com.blog.blog_literario.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blog.blog_literario.model.User;

/**
 * Repository for User entity persistence operations
 * Provides CRUD operations and custom queries for user searches and filtering
 */
public interface UserRepository extends JpaRepository<User, Integer> {

    // ─── Basic Queries ─────────────────────────────────────────────────────────
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByName(String name);

    Optional<User> findByName(String name);

    // ─── Pagination & Search ──────────────────────────────────────────────────
    /**
     * Finds all users with pagination support
     * Useful for large user lists to prevent memory issues
     * 
     * @param pageable the pagination parameters (page, size, sort)
     * @return a page of users
     */
    Page<User> findAll(Pageable pageable);

    /**
     * Searches users by name or email with pagination
     * Case-insensitive search using LIKE pattern
     * 
     * @param searchTerm the search term (name or email)
     * @param pageable the pagination parameters
     * @return a page of matching users
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<User> searchByNameOrEmail(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Finds all users with a specific role
     * Useful for role-based operations (e.g., get all admins)
     * 
     * @param roleName the role name to filter by
     * @param pageable the pagination parameters
     * @return a page of users with the specified role
     */
    @Query("SELECT u FROM User u WHERE u.role.name = :roleName")
    Page<User> findByRoleName(@Param("roleName") String roleName, Pageable pageable);

    /**
     * Checks if another user exists with the given email (excluding current user)
     * Used for email uniqueness validation during updates
     * 
     * @param email the email to check
     * @param excludeId the user ID to exclude from the check
     * @return true if email exists for another user, false otherwise
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.id != :excludeId")
    boolean existsByEmailExcludingId(@Param("email") String email, @Param("excludeId") Integer excludeId);

    /**
     * Gets all users sorted by creation date
     * Used for admin dashboards and audit trails
     * 
     * @param pageable the pagination parameters (usually sorted by createdAt DESC)
     * @return a page of users sorted by creation date
     */
    @Query("SELECT u FROM User u ORDER BY u.createdAt DESC")
    Page<User> findAllSortedByCreationDate(Pageable pageable);

    // ─── Post Count Queries ────────────────────────────────────────────────────
    /**
     * Counts the number of posts authored by a specific user
     * Used to check if a user can be safely deleted
     *
     * @param userId the user's ID
     * @return the count of posts by the user
     */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.author.id = :userId")
    long countPostsByAuthor(@Param("userId") Integer userId);
}

