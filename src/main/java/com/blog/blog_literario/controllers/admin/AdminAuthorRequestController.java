package com.blog.blog_literario.controllers.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.authorrequest.AuthorRequestResponse;
import com.blog.blog_literario.dto.authorrequest.ResolveAuthorRequest;
import com.blog.blog_literario.security.UserDetailsImpl;
import com.blog.blog_literario.services.authorrequest.AuthorRequestService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Admin endpoints for managing author requests.
 *
 * An admin can:
 *   - List all requests, optionally filtered by status (GET)
 *   - Claim a PENDING request for review (PUT claim)
 *   - Release their own claim (DELETE claim)
 *   - Approve a PENDING request (PUT approve)
 *   - Reject a PENDING request (PUT reject)
 *
 * Authorization is handled by SecurityConfig:
 *   /api/admin/** requires ROLE_ADMIN.
 *
 * Base path: /api/admin/author-requests
 */
@Tag(name = "Admin - Author Requests")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/author-requests")
public class AdminAuthorRequestController {

    private final AuthorRequestService authorRequestService;

    /**
     * GET /api/admin/author-requests
     * GET /api/admin/author-requests?status=PENDING
     *
     * Lists all author requests with optional status filter.
     * Defaults to showing PENDING requests first (sorted by createdAt ASC)
     * so the oldest requests are always at the top of the admin panel.
     *
     * The status param accepts: PENDING, APPROVED, REJECTED.
     * Omitting it returns all requests regardless of status.
     */
    @GetMapping
    public ResponseEntity<Page<AuthorRequestResponse>> listAll(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(authorRequestService.listAll(status, pageable));
    }

    /**
     * GET /api/admin/author-requests/pending-count
     *
     * Returns the number of PENDING requests.
     * Designed for the admin panel badge/notification dot —
     * a lightweight call that avoids loading the full list.
     */
    @GetMapping("/pending-count")
    public ResponseEntity<Long> pendingCount() {
        return ResponseEntity.ok(authorRequestService.countPending());
    }

    /**
     * PUT /api/admin/author-requests/{id}/claim
     *
     * Claims a PENDING request for review ("under review" indicator).
     * Called when an admin opens the resolution modal, so other admins see
     * who is already looking at the request before they act on it.
     *
     * Succeeds when the request is unclaimed, claimed by this same admin
     * (refreshes the claim), or the previous claim has expired.
     *
     * Returns 200 OK with the updated request, including the fresh claim.
     * Returns 404 if the request does not exist.
     * Returns 409 Conflict if the request is not PENDING, or another admin
     * holds an active claim (the detail says who and since when).
     */
    @PutMapping("/{id}/claim")
    public ResponseEntity<AuthorRequestResponse> claim(
            @PathVariable Integer id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(authorRequestService.claim(id, extractUserId(authentication)));
    }

    /**
     * DELETE /api/admin/author-requests/{id}/claim
     *
     * Releases this admin's review claim (modal closed without resolving),
     * instead of leaving the claim to expire by TTL.
     *
     * Idempotent: releasing a request you no longer hold (unclaimed, taken
     * over after expiry, or already resolved) is a harmless no-op.
     *
     * Returns 204 No Content.
     * Returns 404 if the request does not exist.
     */
    @DeleteMapping("/{id}/claim")
    public ResponseEntity<Void> releaseClaim(
            @PathVariable Integer id,
            Authentication authentication
    ) {
        authorRequestService.release(id, extractUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/admin/author-requests/{id}/approve
     *
     * Approves a PENDING request.
     * This promotes the requester's role from READER to AUTHOR.
     *
     * The adminNote field in the body is optional on approval,
     * but can be used to welcome the new author or set expectations.
     *
     * Returns 200 OK with the updated request.
     * Returns 404 if the request does not exist.
     * Returns 409 Conflict if the request is not PENDING, or another admin
     * holds an active review claim on it.
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<AuthorRequestResponse> approve(
            @PathVariable Integer id,
            Authentication authentication,
            @RequestBody(required = false) ResolveAuthorRequest dto
    ) {
        Integer adminId = extractUserId(authentication);
        String note = dto != null ? dto.adminNote() : null;

        return ResponseEntity.ok(authorRequestService.approve(id, adminId, note));
    }

    /**
     * PUT /api/admin/author-requests/{id}/reject
     *
     * Rejects a PENDING request.
     * The requester keeps their READER role.
     *
     * adminNote is optional but strongly recommended on rejection —
     * it lets the user understand why and what to improve
     * before submitting a new request.
     *
     * Returns 200 OK with the updated request.
     * Returns 404 if the request does not exist.
     * Returns 409 Conflict if the request is not PENDING, or another admin
     * holds an active review claim on it.
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<AuthorRequestResponse> reject(
            @PathVariable Integer id,
            Authentication authentication,
            @RequestBody(required = false) ResolveAuthorRequest dto
    ) {
        Integer adminId = extractUserId(authentication);
        String note = dto != null ? dto.adminNote() : null;

        return ResponseEntity.ok(authorRequestService.reject(id, adminId, note));
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts the authenticated admin's ID from the security context.
     */
    private Integer extractUserId(Authentication authentication) {
        return ((UserDetailsImpl) authentication.getPrincipal()).getUser().getId();
    }
}