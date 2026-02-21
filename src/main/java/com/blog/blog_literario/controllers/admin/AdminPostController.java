package com.blog.blog_literario.controllers.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.blog.blog_literario.dto.admin.AdminPostResponse;
import com.blog.blog_literario.dto.admin.AdminStatusUpdateRequest;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.services.posts.AdminPostModerationService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminPostController {

    private final AdminPostModerationService adminService;

    @Autowired
    public AdminPostController(AdminPostModerationService adminService) {
        this.adminService = adminService;
    }

    /**
     * GET /api/admin/posts
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
     * PUT /api/admin/posts/{id}/status Changes the status of a post (approval,
     * rejection, archived). Admin can perform any status transition. Requires
     * ROLE_ADMIN (protected in SecurityConfig)
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<AdminPostResponse> updateStatus(
            @PathVariable Integer id,
            @Valid @RequestBody AdminStatusUpdateRequest request) {

        AdminPostResponse updated = adminService.updateStatus(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/admin/posts/{id} Administrative deletion (hard delete).
     * Requires ROLE_ADMIN (protected in SecurityConfig)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
