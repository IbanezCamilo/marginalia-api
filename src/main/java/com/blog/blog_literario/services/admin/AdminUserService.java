package com.blog.blog_literario.services.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.admin.AdminResetPasswordRequest;
import com.blog.blog_literario.dto.roles.RoleResponse;
import com.blog.blog_literario.dto.users.CreateUserRequest;
import com.blog.blog_literario.dto.users.UpdateUserRequest;
import com.blog.blog_literario.dto.users.UserResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.AuthorRequestRepository;
import com.blog.blog_literario.repositories.EmailVerificationTokenRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.RefreshTokenRepository;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.StorageService;
import com.blog.blog_literario.services.users.UserCreationService;
import com.blog.blog_literario.services.users.UserUpdateService;
import com.blog.blog_literario.utils.UserValidator;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Service for admin user management operations
 * Handles creation, retrieval, update, and deletion of users
 * Uses UserCreationService for user creation and UserUpdateService for updates
 * Provides advanced query capabilities like pagination and search
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PostRepository postRepository;
    private final AuthorRequestRepository authorRequestRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserCreationService userCreationService;
    private final UserUpdateService userUpdateService;
    private final UserValidator userValidator;
    private final StorageService storageService;
    private final AdminActionLogService adminActionLogService;

    // ─── Mapper ────────────────────────────────────────────────────────────────
    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                new RoleResponse(user.getRole().getId(), user.getRole().getName()),
                user.getCreatedAt()
        );
    }

    // ─── Queries ────────────────────────────────────────────────────────────────

    /**
     * Retrieves all users with pagination support
     * 
     * @param pageable pagination parameters (page, size, sort)
     * @return a page of user responses
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(@NonNull Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::toResponse);
    }

    /**
     * Searches for users by name or email
     * Case-insensitive search using partial matching
     * 
     * @param searchTerm the search term (part of name or email)
     * @param pageable pagination parameters
     * @return a page of matching user responses
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(@NonNull String searchTerm, @NonNull Pageable pageable) {
        String sanitizedTerm = userValidator.sanitizeInput(searchTerm);
        if (sanitizedTerm.isEmpty()) {
            return getAllUsers(pageable);
        }
        return userRepository.searchByNameOrEmail(sanitizedTerm, pageable)
                .map(this::toResponse);
    }

    /**
     * Finds all users with a specific role
     * 
     * @param roleName the role name to filter by (e.g., "ADMIN", "AUTHOR")
     * @param pageable pagination parameters
     * @return a page of users with the specified role
     * @throws ResourceNotFoundException if role doesn't exist
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersByRole(@NonNull String roleName, @NonNull Pageable pageable) {
        // Validate role exists
        roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rol no encontrado: " + roleName));
        return userRepository.findByRoleName(roleName, pageable)
                .map(this::toResponse);
    }

    /**
     * Retrieves a single user by ID
     * 
     * @param id the user's ID
     * @return the user response
     * @throws ResourceNotFoundException if user doesn't exist
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(@NonNull Integer id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con ID: " + id));
    }

    // ─── Commands ────────────────────────────────────────────────────────────────

    public UserResponse createUser(@NonNull Integer adminId, CreateUserRequest request) {
        User admin = getRequiredUser(adminId);

        if (Role.ADMIN.equalsIgnoreCase(request.roleName()) && !admin.getRole().isOwner()) {
            throw new IllegalStateException("Solo el propietario puede crear cuentas de administrador");
        }

        User createdUser = userCreationService.createUser(
                request.name(),
                request.email(),
                request.password(),
                request.roleName(),
                true); // admin-created accounts are vouched for — no email verification

        adminActionLogService.record(
                adminId, admin.getEmail(), "USER_CREATE", "USER", createdUser.getId(),
                "email=" + createdUser.getEmail() + ", role=" + createdUser.getRole().getName()
        );

        return toResponse(createdUser);
    }

    /**
     * Updates an existing user's information
     * Uses UserUpdateService to safely validate and update each field
     * Ensures email uniqueness across the system
     *
     * @param adminId the ID of the admin performing the update (for audit logging)
     * @param id the user's ID
     * @param request the update request with new name, email, and/or role
     * @return UserResponse with updated user details
     * @throws ResourceNotFoundException if user or role doesn't exist
     * @throws UserAlreadyExistsException if new email is already used
     * @throws IllegalStateException if {@code id} refers to the OWNER (immutable, even to
     *                                another OWNER), or if a non-OWNER actor targets an
     *                                ADMIN account or attempts to promote a user to ADMIN
     */
    public UserResponse update(@NonNull Integer adminId, @NonNull Integer id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado con ID: " + id));

        // OWNER is immutable through this endpoint — name/email/role alike, even by another OWNER
        if (user.getRole().isOwner()) {
            throw new IllegalStateException("El usuario propietario no puede ser modificado");
        }

        User actor = getRequiredUser(adminId);
        boolean targetIsAdmin = user.getRole().isAdmin();
        boolean promotingToAdmin = Role.ADMIN.equalsIgnoreCase(request.roleName());
        if ((targetIsAdmin || promotingToAdmin) && !actor.getRole().isOwner()) {
            throw new IllegalStateException("Solo el propietario puede modificar cuentas de administrador");
        }

        // Update fields using dedicated service; role changes are audit-logged
        // internally by UserUpdateService.updateRole()
        userUpdateService.performUpdate(
                user,
                request.name(),
                request.email(),
                request.roleName(),
                adminId
        );

        // A demoted admin can no longer review author requests — free any request
        // they were holding "under review" instead of waiting for the claim TTL
        if (targetIsAdmin && request.roleName() != null
                && !Role.ADMIN.equalsIgnoreCase(request.roleName())) {
            authorRequestRepository.clearClaimedByForUser(id);
        }

        return toResponse(userRepository.save(user));
    }

    /**
     * Deletes a user along with the data owned by that account — their posts (and cover
     * images), profile picture, refresh tokens, email verification tokens, and their
     * own author-request history.
     * References held on other users' rows (moderatedBy, resolvedBy) are nulled out first.
     *
     * @param adminId the ID of the admin performing the deletion (for audit logging)
     * @param id the user's ID to delete
     * @throws ResourceNotFoundException if the user doesn't exist
     * @throws IllegalStateException if the admin is trying to delete their own account,
     *                                if the user is the OWNER (never deletable, even by
     *                                another OWNER), or if a non-OWNER actor targets an
     *                                ADMIN account
     */
    public void deleteUser(@NonNull Integer adminId, @NonNull Integer id) {
        if (adminId.equals(id)) {
            throw new IllegalStateException("No puedes eliminar tu propia cuenta de administrador");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));

        // OWNER is immutable — never deletable, regardless of who's asking
        if (user.getRole().isOwner()) {
            throw new IllegalStateException("El usuario propietario no puede ser eliminado");
        }

        User admin = getRequiredUser(adminId);

        if (user.getRole().isAdmin() && !admin.getRole().isOwner()) {
            throw new IllegalStateException("Solo el propietario puede eliminar cuentas de administrador");
        }

        // Clear references from other users' rows so deleting this user doesn't
        // leave a dangling moderatedBy/resolvedBy/claimedBy foreign key behind
        postRepository.clearModeratedByForUser(id);
        authorRequestRepository.clearResolvedByForUser(id);
        authorRequestRepository.clearClaimedByForUser(id);

        // Delete the user's own author requests: requester_id is NOT NULL, so it
        // can't be nulled out — the request history goes away with the account
        authorRequestRepository.deleteByRequesterId(id);

        // Clean up post images
        postRepository.findAllByAuthorId(id)
                .forEach(p -> storageService.delete(p.getCoverImage()));
        postRepository.deleteAllByAuthorId(id);

        // Clean up profile picture
        storageService.delete(user.getProfilePicture());

        // Remove refresh tokens so an active session doesn't leave a dangling
        // foreign key behind
        refreshTokenRepository.deleteByUser(user);

        // Remove email verification tokens (unverified accounts always have one)
        // so the FK on email_verification_tokens doesn't block the delete
        emailVerificationTokenRepository.deleteByUser(user);

        userRepository.deleteById(id);

        adminActionLogService.record(
                adminId, admin.getEmail(), "USER_DELETE", "USER", id,
                "email=" + user.getEmail() + ", role=" + user.getRole().getName()
        );
    }

    /**
     * Gets the total count of users in the system
     * Useful for admin dashboards and statistics
     * 
     * @return the total number of users
     */
    @Transactional(readOnly = true)
    public long countUsers() {
        return userRepository.count();
    }

    /**
     * Gets the count of posts for a specific user
     * Useful to check before deletion operations
     * 
     * @param userId the user's ID
     * @return the number of posts authored by this user
     */
    @Transactional(readOnly = true)
    public long countUserPosts(@NonNull Integer userId) {
        return userRepository.countPostsByAuthor(userId);
    }

    /**
     * Resets a user's password without requiring their current one. Intended for
     * support flows where a user has lost access to their account.
     *
     * @param adminId the ID of the admin performing the reset (for audit logging)
     * @throws IllegalStateException if the target is the OWNER, or if a non-OWNER actor
     *                                targets an ADMIN account
     */
    public UserResponse resetPassword(@NonNull Integer adminId, @NonNull Integer id, @NonNull AdminResetPasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con ID: " + id));

        // OWNER's credentials are immutable through this endpoint
        if (user.getRole().isOwner()) {
            throw new IllegalStateException("La contraseña del propietario no puede ser modificada");
        }

        User admin = getRequiredUser(adminId);

        if (user.getRole().isAdmin() && !admin.getRole().isOwner()) {
            throw new IllegalStateException("Solo el propietario puede restablecer la contraseña de un administrador");
        }

        userUpdateService.updatePassword(user, request.newPassword());
        userRepository.save(user);

        adminActionLogService.record(
                adminId, admin.getEmail(), "USER_PASSWORD_RESET", "USER", id,
                "email=" + user.getEmail()
        );

        return toResponse(user);
    }

    private User getRequiredUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));
    }
}
