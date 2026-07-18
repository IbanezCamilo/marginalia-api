package com.blog.blog_literario.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.blog.blog_literario.model.AuthorRequest;
import com.blog.blog_literario.model.AuthorRequestStatus;

@Repository
public interface AuthorRequestRepository extends JpaRepository<AuthorRequest, Integer> {

    // ─── READER queries ────────────────────────────────────────────────────────

    /**
     * Finds the active (PENDING) request for a specific user.
     * Used by the service to check if the user already has a pending request
     * before allowing them to submit a new one.
     */
    Optional<AuthorRequest> findByRequesterIdAndStatus(
            Integer requesterId,
            AuthorRequestStatus status
    );

    /**
     * Returns the full request history for a user, paginated.
     * Includes all statuses: PENDING, APPROVED, REJECTED.
     */
    Page<AuthorRequest> findByRequesterId(
            Integer requesterId,
            Pageable pageable
    );

    /**
     * Checks whether a user already has a PENDING request.
     * More efficient than loading the full entity —
     * used as a guard before creating a new request.
     */
    boolean existsByRequesterIdAndStatus(
            Integer requesterId,
            AuthorRequestStatus status
    );

    // ─── Admin queries ─────────────────────────────────────────────────────────

    /**
     * Lists all requests filtered by status, paginated.
     * Passing null as status returns all requests regardless of status.
     * Used by the admin panel to show pending, approved, or rejected requests.
     */
    @Query("""
        SELECT r FROM AuthorRequest r
        WHERE (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt ASC
    """)
    Page<AuthorRequest> findAllByStatus(
            @Param("status") AuthorRequestStatus status,
            Pageable pageable
    );

    /**
     * Counts requests by status.
     * Used specifically for the PENDING count in the admin notification badge —
     * cheaper than loading a full paginated result just to count.
     */
    long countByStatus(AuthorRequestStatus status);

    /**
     * Clears the {@code resolvedBy} reference on any request resolved by the given
     * admin, preventing an orphaned foreign key when that admin account is deleted.
     */
    @Modifying
    @Query("UPDATE AuthorRequest r SET r.resolvedBy = NULL WHERE r.resolvedBy.id = :userId")
    void clearResolvedByForUser(@Param("userId") Integer userId);

    /**
     * Clears any review claim held by the given admin, preventing an orphaned
     * foreign key when that admin account is deleted (and freeing the requests
     * immediately when the admin is demoted, instead of waiting for the claim TTL).
     */
    @Modifying
    @Query("UPDATE AuthorRequest r SET r.claimedBy = NULL, r.claimedAt = NULL WHERE r.claimedBy.id = :userId")
    void clearClaimedByForUser(@Param("userId") Integer userId);

    /**
     * Deletes every request submitted by the given user. Because {@code requester_id}
     * is NOT NULL it can't be nulled out like {@code resolvedBy}, so the user's own
     * request history is removed along with their account when they are deleted.
     */
    @Modifying
    @Query("DELETE FROM AuthorRequest r WHERE r.requester.id = :userId")
    void deleteByRequesterId(@Param("userId") Integer userId);
}