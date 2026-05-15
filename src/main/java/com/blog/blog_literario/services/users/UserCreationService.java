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

@Service
@Transactional
@RequiredArgsConstructor
public class UserCreationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;

    public User createUser(String name, String email, 
                           String password, String roleName) {    
        // Validate and sanitize inputs
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