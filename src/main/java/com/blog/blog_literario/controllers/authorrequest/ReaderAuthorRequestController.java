package com.blog.blog_literario.controllers.authorrequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.authorrequest.AuthorRequestResponse;
import com.blog.blog_literario.dto.authorrequest.CreateAuthorRequest;
import com.blog.blog_literario.security.UserDetailsImpl;
import com.blog.blog_literario.services.authorrequest.AuthorRequestService;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints available to authenticated READERs.
 *
 * A READER can:
 *   - Submit a request to become an AUTHOR (POST)
 *   - Check the status of their active request (GET active)
 *   - View their full request history (GET history)
 *
 * Authorization is handled by SecurityConfig:
 *   /api/me/** requires any authenticated user.
 * The service layer enforces that only READERs can submit requests.
 *
 * Base path: /api/me/author-request
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/author-request")
public class ReaderAuthorRequestController {

    private final AuthorRequestService authorRequestService;

    /**
     * POST /api/me/author-request
     *
     * Submits a new request to obtain the AUTHOR role.
     * The motivation field is optional but recommended —
     * it gives the admin context to make a better decision.
     *
     * Returns 201 Created with the new request on success.
     * Returns 409 Conflict if the user already has a PENDING request.
     * Returns 400 Bad Request if the user is not a READER.
     */
    @PostMapping
    public ResponseEntity<AuthorRequestResponse> submitRequest(
            Authentication authentication,
            @RequestBody(required = false) CreateAuthorRequest dto
    ) {
        Integer requesterId = extractUserId(authentication);
        String motivation = dto != null ? dto.motivation() : null;

        AuthorRequestResponse response = authorRequestService.createRequest(requesterId, motivation);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * GET /api/me/author-request/active
     *
     * Returns the user's current PENDING request.
     * Useful to show the user that their request is under review
     * and prevent them from submitting another one.
     *
     * Returns 200 OK with the request, or 404 if none exists.
     */
    @GetMapping("/active")
    public ResponseEntity<AuthorRequestResponse> getActiveRequest(
            Authentication authentication
    ) {
        Integer requesterId = extractUserId(authentication);
        return ResponseEntity.ok(authorRequestService.getMyActiveRequest(requesterId));
    }

    /**
     * GET /api/me/author-request/history
     *
     * Returns the full history of requests submitted by this user,
     * paginated. Includes PENDING, APPROVED, and REJECTED requests.
     *
     * Useful so the user can see if a past request was rejected
     * and what note the admin left (adminNote field).
     */
    @GetMapping("/history")
    public ResponseEntity<Page<AuthorRequestResponse>> getHistory(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable
    ) {
        Integer requesterId = extractUserId(authentication);
        return ResponseEntity.ok(authorRequestService.getMyRequests(requesterId, pageable));
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extracts the authenticated user's ID from the security context.
     * Mirrors the same pattern used in MyPostController.
     */
    private Integer extractUserId(Authentication authentication) {
        return ((UserDetailsImpl) authentication.getPrincipal()).getUser().getId();
    }
}