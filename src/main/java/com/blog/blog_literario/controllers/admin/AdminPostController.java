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

import com.blog.blog_literario.dto.admin.AdminPostResponse;
import com.blog.blog_literario.dto.admin.AdminResetPostRequest;
import com.blog.blog_literario.dto.admin.AdminStatusUpdateRequest;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.security.UserDetailsImpl;
import com.blog.blog_literario.services.admin.AdminPostModerationService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin endpoints for post moderation. All routes require {@code ROLE_ADMIN}
 * (enforced in {@link com.blog.blog_literario.config.SecurityConfig}).
 */
@Tag(name = "Admin - Posts")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostModerationService adminService;

    /**
     * Returns a paginated list of posts. Optionally filter by {@code status}
     * (DRAFT, PUBLISHED, ARCHIVED, REJECTED); omit to return all statuses.
     */
    @GetMapping
    public Page<AdminPostResponse> listAll(
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

        return adminService.listAll(postStatus, pageable);
    }

    /**
     * Changes a post's status. Admins may perform any transition (including those
     * not available to authors such as ARCHIVED or REJECTED).
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<AdminPostResponse> updateStatus(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody AdminStatusUpdateRequest request) {

        Integer adminId = getAdminId(authentication);
        AdminPostResponse updated = adminService.updateStatus(adminId, id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Unlocks a permanently-blocked post (3 accumulated rejections), returning it to
     * {@code DRAFT} so the author can resubmit it. The request body is optional and
     * may carry a {@code moderationNote} explaining the unlock to the author.
     */
    @PutMapping("/{id}/reset")
    public ResponseEntity<AdminPostResponse> resetPost(
            Authentication authentication,
            @PathVariable Integer id,
            @RequestBody(required = false) AdminResetPostRequest request) {

        Integer adminId = getAdminId(authentication);
        String note = (request != null) ? request.moderationNote() : null;
        AdminPostResponse reset = adminService.resetPost(adminId, id, note);
        return ResponseEntity.ok(reset);
    }

    /** Hard-deletes a post and its cover image from storage. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    //Helpers
    private Integer getAdminId(Authentication authentication) {
        return ((UserDetailsImpl) authentication.getPrincipal()).getUser().getId();
    }
}
