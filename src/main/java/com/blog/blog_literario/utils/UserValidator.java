package com.blog.blog_literario.utils;

import org.springframework.stereotype.Component;

import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Utility class for user validation
 * Centralizes all user-related validation logic to avoid duplication
 */
@Component
@RequiredArgsConstructor
public class UserValidator {

    private final UserRepository userRepository;

    /**
     * Validates that a user with the given email doesn't already exist
     * @param email the email to check
     * @throws UserAlreadyExistsException if email is already registered
     */
    public void validateEmailUniqueness(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("El correo '" + email + "' ya está registrado");
        }
    }

    /**
     * Sanitizes user input by trimming whitespace
     * @param input the input string to sanitize
     * @return the trimmed string, or empty string if null
     */
    public String sanitizeInput(String input) {
        return (input != null) ? input.trim() : "";
    }

    /**
     * Validates and sanitizes email
     * @param email the email to validate and sanitize
     * @return the sanitized email
     * @throws UserAlreadyExistsException if email already exists
     */
    public String validateAndSanitizeEmail(String email) {
        String sanitized = sanitizeInput(email).toLowerCase();
        validateEmailUniqueness(sanitized);
        return sanitized;
    }

    /**
     * Validates and sanitizes user name
     * @param name the name to validate and sanitize
     * @return the sanitized name
     */
    public String validateAndSanitizeName(String name) {
        String sanitized = sanitizeInput(name);
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        return sanitized;
    }
}
