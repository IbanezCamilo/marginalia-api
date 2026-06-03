package com.blog.blog_literario.services.users;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.utils.UserValidator;

import lombok.RequiredArgsConstructor;

/**
 * Shared service for creating new user accounts. Used by both the self-registration
 * flow ({@link com.blog.blog_literario.services.auth.AuthService}) and the admin
 * user-management flow.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserCreationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates and persists a new user after input sanitization and uniqueness checks.
     *
     * @param name     the user's display name (sanitized and validated)
     * @param email    the user's email address (must be unique)
     * @param password the raw password (will be BCrypt-encoded before storage)
     * @param roleName the role to assign (must exist in the database)
     * @throws RuntimeException          if the email is already in use
     * @throws ResourceNotFoundException if {@code roleName} does not exist
     */
    public User createUser(String name, String email,
                           String password, String roleName) {
        String sanitizedName = userValidator.validateAndSanitizeName(name);
        String sanitizedEmail = userValidator.validateAndSanitizeEmail(email);
        if (userRepository.existsByEmail(sanitizedEmail)) {
            throw new RuntimeException("El correo ya está en uso");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rol no encontrado: " + roleName));

        User user = new User();
        user.setName(sanitizedName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setProfilePicture(null);

        return userRepository.save(user);
    }
}
