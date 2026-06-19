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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.admin.AdminResetPasswordRequest;
import com.blog.blog_literario.dto.users.CreateUserRequest;
import com.blog.blog_literario.dto.users.UpdateUserRequest;
import com.blog.blog_literario.dto.users.UserResponse;
import com.blog.blog_literario.security.UserDetailsImpl;
import com.blog.blog_literario.services.admin.AdminUserService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for admin user management
 * Provides endpoints for CRUD operations on users with pagination and search
 * All endpoints require ADMIN role authorization
 * 
 * Base path: /api/admin/users
 */
@Tag(name = "Admin - Users")
@SecurityRequirement(name = "cookieAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    // ─── Read Operations ───────────────────────────────────────────────────────

    /**
     * Retrieves all users with pagination support
     * Supports sorting by any field (default: by creation date descending)
     * 
     * GET /api/admin/users
     * GET /api/admin/users?page=0&size=20&sort=name,asc
     * 
     * @param pageable pagination parameters (page, size, sort)
     * @return a paginated list of users
     */
    @GetMapping
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminUserService.getAllUsers(pageable));
    }

    /**
     * Searches for users by name or email
     * Case-insensitive search supporting partial matches
     * 
     * GET /api/admin/users/search?q=john&page=0&size=20
     * 
     * @param searchTerm the search term (searches in name and email)
     * @param pageable pagination parameters
     * @return a paginated list of matching users
     */
    @GetMapping("/search")
    public ResponseEntity<Page<UserResponse>> searchUsers(
            @RequestParam(name = "q", defaultValue = "") String searchTerm,
            @PageableDefault(size = 20, page = 0, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(adminUserService.searchUsers(searchTerm, pageable));
    }

    /**
     * Retrieves all users with a specific role
     * Useful for filtering users by role (e.g., all admins or all authors)
     * 
     * GET /api/admin/users/role/ADMIN?page=0&size=20
     * 
     * @param roleName the role name to filter by (ADMIN, AUTHOR, READER)
     * @param pageable pagination parameters
     * @return a paginated list of users with the specified role
     */
    @GetMapping("/role/{roleName}")
    public ResponseEntity<Page<UserResponse>> getUsersByRole(
            @PathVariable String roleName,
            @PageableDefault(size = 20, page = 0)
            Pageable pageable) {
        return ResponseEntity.ok(adminUserService.getUsersByRole(roleName, pageable));
    }

    /**
     * Retrieves a specific user by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Integer id) {
        return ResponseEntity.ok(adminUserService.getUserById(id));
    }

    // ─── Create Operation ──────────────────────────────────────────────────────

    /**
     * Creates a new user with the specified role
     * Admin can assign any role during creation
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest dto) {
        return ResponseEntity.status(201).body(adminUserService.createUser(dto));
    }

    // ─── Update Operation ──────────────────────────────────────────────────────

    /**
     * Updates an existing user's information
     * Can update name, email, and role
     * Email uniqueness is validated (must be unique unless unchanged)
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody UpdateUserRequest dto) {
        Integer adminId = getAdminId(authentication);
        return ResponseEntity.ok(adminUserService.update(adminId, id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(Authentication authentication, @PathVariable Integer id) {
        Integer adminId = getAdminId(authentication);
        adminUserService.deleteUser(adminId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resets a user's password. Does not require the user's current password —
     * for use when a user has lost access and contacted support.
     */
    @PutMapping("/{id}/password")
    public ResponseEntity<UserResponse> resetPassword(
            Authentication authentication,
            @PathVariable Integer id,
            @Valid @RequestBody AdminResetPasswordRequest dto) {
        Integer adminId = getAdminId(authentication);
        return ResponseEntity.ok(adminUserService.resetPassword(adminId, id, dto));
    }

    private Integer getAdminId(Authentication authentication) {
        return ((UserDetailsImpl) authentication.getPrincipal()).getUser().getId();
    }
}

