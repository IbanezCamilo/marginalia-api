package com.blog.blog_literario.dto.users;

import jakarta.validation.constraints.Email;

/**
 * DTO for partial user update requests (PUT /api/admin/users/{id}).
 * All fields are optional — omit a field (or send null) to leave it unchanged.
 * Fields that are provided must be non-blank and valid.
 */
public record UpdateUserRequest(
        String name,

        @Email(message = "Correo no válido")
        String email,

        String roleName
) {
}

