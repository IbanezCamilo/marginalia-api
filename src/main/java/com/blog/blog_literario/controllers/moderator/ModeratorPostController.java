package com.blog.blog_literario.controllers.moderator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.moderator.ModeratorPostResponse;
import com.blog.blog_literario.dto.moderator.ModeratorStatusUpdateRequest;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.security.UserDetailsImpl;
import com.blog.blog_literario.services.moderator.ModeratorPostService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Moderator endpoints for post moderation. All routes require {@code ROLE_MODERATOR}
 * or {@code ROLE_ADMIN} (enforced in {@link com.blog.blog_literario.config.SecurityConfig}).
 *
 * <p>Unlike {@link com.blog.blog_literario.controllers.admin.AdminPostController},
 * moderators cannot touch {@code ARCHIVED} posts or reset permanently-blocked posts —
 * see {@link com.blog.blog_literario.services.moderator.ModeratorPostService} for the
 * allowed status transitions.
 */
@RestController
@RequestMapping("/api/moderator/posts")
@RequiredArgsConstructor
public class ModeratorPostController {

    private final ModeratorPostService moderatorService;

    /**
     * Returns a paginated list of posts. Optionally filter by {@code status}
     * (DRAFT, PUBLISHED, ARCHIVED, REJECTED); omit to return all statuses.
     */
    @GetMapping
    public Page<ModeratorPostResponse> listAll(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PostStatus postStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                postStatus = PostStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Estado inválido: '" + status + "'. Valores aceptados: DRAFT, PUBLISHED, ARCHIVED, REJECTED"
                );
            }
        }

        return moderatorService.listAll(postStatus, pageable);
    }

    /**
     * Changes a post's status, subject to the transitions allowed for moderators
     * (see {@link ModeratorPostService}). Rejecting a post requires a non-blank
     * {@code moderationNote} and increments its rejection count; the third rejection
     * automatically archives the post.
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<ModeratorPostResponse> updateStatus(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody ModeratorStatusUpdateRequest request) {

        Integer moderatorId = getModeratorId(authentication);
        ModeratorPostResponse updated = moderatorService.updateStatus(moderatorId, id, request);
        return ResponseEntity.ok(updated);
    }

    private Integer getModeratorId(Authentication authentication) {
        return ((UserDetailsImpl) authentication.getPrincipal()).getUser().getId();
    }
}