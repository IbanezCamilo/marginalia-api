package com.blog.blog_literario.services.users;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.admin.AdminActionLogService;
import com.blog.blog_literario.utils.UserValidator;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Service for updating user information
 * Handles validation, sanitization, and persistence of user updates
 * Ensures data integrity for sensitive operations like email changes
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserUpdateService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final AdminActionLogService adminActionLogService;

    /**
     * Updates a user's name safely with sanitization
     * 
     * @param user the user entity to update
     * @param newName the new name
     * @throws IllegalArgumentException if name is blank after sanitization
     */
    public void updateName(@NonNull User user, String newName) {
        String sanitizedName = userValidator.validateAndSanitizeName(newName);
        user.setName(sanitizedName);
    }

    /**
     * Updates a user's email with uniqueness validation
     * Ensures the new email is not already used by another user
     * 
     * @param user the user entity to update
     * @param newEmail the new email address
     * @throws UserAlreadyExistsException if email is already used by another user
     */
    public void updateEmail(@NonNull User user, String newEmail) {
        String sanitizedEmail = userValidator.sanitizeEmail(newEmail);
        if (sanitizedEmail.isBlank()) {
            throw new IllegalArgumentException("El correo no puede estar vacío");
        }

        // Only validate uniqueness if email is actually changing
        if (!sanitizedEmail.equals(user.getEmail())) {
            if (userRepository.existsByEmailExcludingId(sanitizedEmail, user.getId())) {
                throw new UserAlreadyExistsException(
                    "El correo '" + sanitizedEmail + "' ya está en uso por otro usuario");
            }
            user.setEmail(sanitizedEmail);
        }
    }

    /**
     * Updates a user's role with validation, recording the change to the admin audit
     * trail. This is the single source of truth for role changes — every write path
     * that promotes or demotes a user (direct admin edit, author-request approval,
     * etc.) must go through this method so the "last admin" guard and the audit log
     * apply uniformly regardless of which endpoint triggered the change.
     *
     * @param user the user entity to update
     * @param newRoleName the new role name
     * @param actorId the ID of the admin performing the change (for audit logging)
     * @throws ResourceNotFoundException if the role or the actor doesn't exist
     * @throws IllegalStateException if {@code user} is the last remaining ADMIN
     */
    public void updateRole(@NonNull User user, String newRoleName, @NonNull Integer actorId) {
        if (newRoleName.isBlank()) {
            throw new IllegalArgumentException("El rol no puede estar vacío");
        }

        String previousRoleName = user.getRole().getName();

        // Avoid unnecessary query
        if (newRoleName.equals(previousRoleName)) {
            return;
        }

        if (user.getRole().isAdmin() && userRepository.countByRoleName(Role.ADMIN) <= 1) {
            throw new IllegalStateException(
                "No se puede quitar el rol de administrador al último admin del sistema");
        }

        var role = roleRepository.findByName(newRoleName)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Rol no encontrado: " + newRoleName));
        user.setRole(role);
        user.incrementTokenVersion();

        User actor = userRepository.findById(actorId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Usuario no encontrado con ID: " + actorId));
        adminActionLogService.record(
                actorId, actor.getEmail(), "USER_ROLE_CHANGE", "USER", user.getId(),
                previousRoleName + " -> " + newRoleName
        );
    }

    /**
     * Performs a complete user update with all validations
     * Can be called with null values to skip updates for specific fields
     *
     * @param user the user entity to update
     * @param newName the new name (null to skip)
     * @param newEmail the new email (null to skip)
     * @param newRoleName the new role name (null to skip)
     * @param actorId the ID of the admin performing the update (for audit logging of role changes)
     * @return the updated user entity (not persisted)
     */
    public User performUpdate(@NonNull User user, String newName, String newEmail, String newRoleName, @NonNull Integer actorId) {
        if (newName != null) {
            updateName(user, newName);
        }
        if (newEmail != null) {
            updateEmail(user, newEmail);
        }
        if (newRoleName != null) {
            updateRole(user, newRoleName, actorId);
        }
        return user;
    }

    /**
     * Encodes and sets a new password on {@code user}. Does not verify the current
     * password, nor whether it's different from the new one — callers needing those
     * checks (e.g. self-service password change) must perform them before calling
     * this method. Deliberately permissive for the admin-reset path: an admin never
     * sees the plaintext current password, so there's nothing to protect against here.
     */
    public void updatePassword(@NonNull User user, String newRawPassword) {
        user.setPassword(passwordEncoder.encode(newRawPassword));
        user.incrementTokenVersion();
    }
}
