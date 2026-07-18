package com.blog.blog_literario.services.authorrequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.config.properties.AuthorRequestProperties;
import com.blog.blog_literario.dto.authorrequest.AuthorRequestResponse;
import com.blog.blog_literario.events.AuthorRequestSubmitted;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.AuthorRequest;
import com.blog.blog_literario.model.AuthorRequestStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.AuthorRequestRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.users.UserUpdateService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Business logic for the author request system.
 *
 * Responsibilities:
 *   - Allow a READER to submit a request to become an AUTHOR.
 *   - Allow an admin to approve or reject a PENDING request.
 *   - Enforce all business rules at the service layer,
 *     independently of the HTTP layer above it.
 *
 * Business rules enforced here:
 *   1. Only users with role READER may submit requests.
 *   2. A user may only have one PENDING request at a time.
 *   3. Only PENDING requests can be approved or rejected.
 *   4. Approving a request promotes the requester's role to AUTHOR.
 *   5. While another admin holds an active review claim on a request,
 *      nobody else may claim or resolve it (claims expire after
 *      author-request.claim-ttl-minutes).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthorRequestService {

    private final AuthorRequestRepository authorRequestRepository;
    private final UserRepository userRepository;
    private final UserUpdateService userUpdateService;
    private final AuthorRequestProperties authorRequestProperties;
    private final ApplicationEventPublisher eventPublisher;

    private static final DateTimeFormatter CLAIM_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    // ─── READER operations ─────────────────────────────────────────────────────

    /**
     * Submits a new author request on behalf of the authenticated READER.
     *
     * @param requesterId ID of the authenticated READER
     * @param motivation  optional free-text explanation (max 1000 chars)
     * @return the newly created request
     * @throws IllegalStateException     if user is not a READER or already has a PENDING request
     * @throws ResourceNotFoundException if user does not exist
     */
    public AuthorRequestResponse createRequest(
            @NonNull Integer requesterId,
            String motivation
    ) {
        User requester = findUserById(requesterId);

        // Rule 1: only READERs can request — AUTHORs and ADMINs have no reason to
        if (!requester.getRoleName().equals(Role.READER)) {
            throw new IllegalStateException(
                "Only users with the READER role can submit an author request."
            );
        }

        // Rule 2: prevent duplicate PENDING requests for the same user
        if (authorRequestRepository.existsByRequesterIdAndStatus(
                requesterId, AuthorRequestStatus.PENDING)) {
            throw new IllegalStateException(
                "You already have a pending request. Wait for it to be reviewed before submitting a new one."
            );
        }

        AuthorRequest request = new AuthorRequest();
        request.setRequester(requester);
        request.setMotivation(motivation);
        // Status defaults to PENDING via the field initializer in the entity

        AuthorRequest saved = authorRequestRepository.save(request);

        // Notify all admins by email once this transaction commits. The email list
        // travels in the event because the AFTER_COMMIT listener runs outside any
        // transaction and must not touch repositories. Request IDs are unique and a
        // request is created exactly once, so the id makes a per-request idempotency key.
        List<String> adminEmails = userRepository.findEmailsByRoleNames(List.of(Role.ADMIN, Role.OWNER));
        eventPublisher.publishEvent(new AuthorRequestSubmitted(
                saved.getId(), requester.getName(), requester.getEmail(),
                motivation, adminEmails, "author-request/" + saved.getId()));

        return toResponse(saved);
    }

    /**
     * Returns the READER's currently active (PENDING) request.
     *
     * @param requesterId ID of the authenticated user
     * @return the active request
     * @throws ResourceNotFoundException if no PENDING request exists
     */
    @Transactional(readOnly = true)
    public AuthorRequestResponse getMyActiveRequest(@NonNull Integer requesterId) {
        return authorRequestRepository
                .findByRequesterIdAndStatus(requesterId, AuthorRequestStatus.PENDING)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active author request found for this user."));
    }

    /**
     * Returns the full request history for the authenticated user, paginated.
     * Includes PENDING, APPROVED, and REJECTED requests.
     *
     * @param requesterId ID of the authenticated user
     * @param pageable    pagination parameters
     */
    @Transactional(readOnly = true)
    public Page<AuthorRequestResponse> getMyRequests(
            @NonNull Integer requesterId,
            Pageable pageable
    ) {
        return authorRequestRepository
                .findByRequesterId(requesterId, pageable)
                .map(this::toResponse);
    }

    // ─── Admin operations ──────────────────────────────────────────────────────

    /**
     * Lists all author requests, optionally filtered by status.
     * Passing null as statusStr returns all requests regardless of status.
     *
     * @param statusStr optional status filter: "PENDING", "APPROVED", or "REJECTED"
     * @param pageable  pagination parameters
     * @throws IllegalArgumentException if statusStr is not a valid status value
     */
    @Transactional(readOnly = true)
    public Page<AuthorRequestResponse> listAll(String statusStr, Pageable pageable) {
        AuthorRequestStatus status = parseStatusOrNull(statusStr);
        return authorRequestRepository
                .findAllByStatus(status, pageable)
                .map(this::toResponse);
    }

    /**
     * Approves a PENDING request and promotes the requester to AUTHOR.
     *
     * The role change and the request update happen in the same transaction —
     * if either fails, both are rolled back, leaving the DB in a consistent state.
     *
     * @param requestId ID of the request to approve
     * @param adminId   ID of the admin performing the action
     * @param note      optional note for the requester (e.g. a welcome message)
     * @throws ResourceNotFoundException if request or admin does not exist
     * @throws IllegalStateException     if request is not PENDING
     */
    public AuthorRequestResponse approve(
            @NonNull Integer requestId,
            @NonNull Integer adminId,
            String note
    ) {
        AuthorRequest request = findActionableRequest(requestId, adminId);
        User admin = findUserById(adminId);

        // Promote the requester's role to AUTHOR — delegates to UserUpdateService so
        // the same guard and audit-log behavior used by direct admin role edits applies here too
        userUpdateService.updateRole(request.getRequester(), Role.AUTHOR, adminId);
        userRepository.save(request.getRequester());

        // Mark the request as resolved
        request.approve(admin, note);

        return toResponse(authorRequestRepository.save(request));
    }

    /**
     * Rejects a PENDING request. The requester keeps their READER role.
     *
     * The adminNote is optional but strongly recommended —
     * it lets the user understand the reason and improve before reapplying.
     *
     * @param requestId ID of the request to reject
     * @param adminId   ID of the admin performing the action
     * @param note      optional explanation of the rejection
     * @throws ResourceNotFoundException if request or admin does not exist
     * @throws IllegalStateException     if request is not PENDING
     */
    public AuthorRequestResponse reject(
            @NonNull Integer requestId,
            @NonNull Integer adminId,
            String note
    ) {
        AuthorRequest request = findActionableRequest(requestId, adminId);
        User admin = findUserById(adminId);

        request.reject(admin, note);

        return toResponse(authorRequestRepository.save(request));
    }

    /**
     * Returns the count of PENDING requests.
     * Designed for the admin panel notification badge —
     * cheaper than loading the full paginated list.
     */
    @Transactional(readOnly = true)
    public long countPending() {
        return authorRequestRepository.countByStatus(AuthorRequestStatus.PENDING);
    }

    /**
     * Claims a PENDING request for review ("under review" indicator).
     *
     * Succeeds when the request is unclaimed, already claimed by this admin
     * (refreshing the timestamp), or the previous claim has expired.
     *
     * @param requestId ID of the request to claim
     * @param adminId   ID of the admin opening the resolution modal
     * @return the updated request, including the fresh claim
     * @throws ResourceNotFoundException if request or admin does not exist
     * @throws IllegalStateException     if the request is not PENDING, or another
     *                                   admin holds an active claim on it
     */
    public AuthorRequestResponse claim(@NonNull Integer requestId, @NonNull Integer adminId) {
        AuthorRequest request = findActionableRequest(requestId, adminId);
        User admin = findUserById(adminId);

        request.claim(admin);

        return toResponse(authorRequestRepository.save(request));
    }

    /**
     * Releases this admin's review claim on a request (modal closed without resolving).
     *
     * Deliberately a no-op when the claim is held by someone else, already gone,
     * or the request is resolved — release fires from a dialog-cancel path, and
     * failing loudly on those harmless races would only produce confusing errors.
     *
     * @param requestId ID of the request to release
     * @param adminId   ID of the admin cancelling the review
     * @throws ResourceNotFoundException if the request does not exist
     */
    public void release(@NonNull Integer requestId, @NonNull Integer adminId) {
        AuthorRequest request = authorRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Author request not found with ID: " + requestId));

        if (request.getClaimedBy() != null && request.getClaimedBy().getId().equals(adminId)) {
            request.releaseClaim();
            authorRequestRepository.save(request);
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /**
     * Loads a request by ID and asserts it is still PENDING.
     * Centralizes the "can this request be acted upon?" check
     * so approve() and reject() don't duplicate the logic.
     */
    private AuthorRequest findPendingRequest(Integer requestId) {
        AuthorRequest request = authorRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Author request not found with ID: " + requestId));

        // Rule 3: only PENDING requests can be resolved
        if (!request.isPending()) {
            throw new IllegalStateException(
                "Request " + requestId + " has already been resolved and cannot be modified.");
        }

        return request;
    }

    /**
     * Loads a PENDING request and asserts this admin may act on it.
     * Centralizes rule 5 for claim(), approve(), and reject():
     * another admin's claim only blocks while it is still active.
     */
    private AuthorRequest findActionableRequest(Integer requestId, Integer adminId) {
        AuthorRequest request = findPendingRequest(requestId);

        if (isClaimedByOther(request, adminId)) {
            throw new IllegalStateException(
                "La solicitud ya está siendo revisada por " + request.getClaimedBy().getName()
                    + " desde las " + request.getClaimedAt().format(CLAIM_TIME_FORMAT) + ".");
        }

        return request;
    }

    /**
     * A claim counts only while it is younger than the configured TTL —
     * expired claims are treated exactly like no claim at all.
     */
    private boolean isClaimActive(AuthorRequest request) {
        return request.getClaimedAt() != null
                && request.getClaimedAt()
                        .plusMinutes(authorRequestProperties.claimTtlMinutes())
                        .isAfter(LocalDateTime.now());
    }

    private boolean isClaimedByOther(AuthorRequest request, Integer adminId) {
        return request.getClaimedBy() != null
                && !request.getClaimedBy().getId().equals(adminId)
                && isClaimActive(request);
    }

    /**
     * Loads a user by ID or throws a descriptive exception.
     */
    private User findUserById(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId));
    }

    /**
     * Parses a status string into the enum, or returns null if blank.
     * Null signals "no filter" to the repository query.
     *
     * @throws IllegalArgumentException if the string is non-blank but invalid
     */
    private AuthorRequestStatus parseStatusOrNull(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) return null;
        try {
            return AuthorRequestStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid status: '" + statusStr + "'. Accepted values: PENDING, APPROVED, REJECTED"
            );
        }
    }

    /**
     * Maps an AuthorRequest entity to its response DTO.
     * resolvedByName is null while the request is PENDING.
     * Claim fields are exposed only while the claim is active, so clients can
     * treat "claim fields non-null" as "under review" without knowing the TTL.
     */
    private AuthorRequestResponse toResponse(AuthorRequest r) {
        boolean claimActive = isClaimActive(r);
        return new AuthorRequestResponse(
                r.getId(),
                r.getRequester().getId(),
                r.getRequester().getName(),
                r.getRequester().getEmail(),
                r.getMotivation(),
                r.getStatus().name(),
                r.getStatus().getDisplayName(),
                r.getAdminNote(),
                r.getResolvedBy() != null ? r.getResolvedBy().getName() : null,
                r.getResolvedAt(),
                claimActive ? r.getClaimedBy().getId() : null,
                claimActive ? r.getClaimedBy().getName() : null,
                claimActive ? r.getClaimedAt() : null,
                r.getCreatedAt()
        );
    }
}