package com.blog.blog_literario.controllers.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.admin.AdminPostResponse;
import com.blog.blog_literario.dto.admin.AdminStatusUpdateRequest;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.services.posts.AdminPostModerationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin endpoints for post moderation. All routes require {@code ROLE_ADMIN}
 * (enforced in {@link com.blog.blog_literario.config.SecurityConfig}).
 */
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
            @PathVariable Integer id,
            @Valid @RequestBody AdminStatusUpdateRequest request) {

        AdminPostResponse updated = adminService.updateStatus(id, request);
        return ResponseEntity.ok(updated);
    }

    /** Hard-deletes a post and its cover image from storage. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
