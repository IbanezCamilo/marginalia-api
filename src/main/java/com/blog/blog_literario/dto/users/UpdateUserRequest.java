package com.blog.blog_literario.dto.users;

import com.blog.blog_literario.model.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for user update requests
 * All fields are optional but must be valid if provided
 */
public record UpdateUserRequest(
        @NotBlank(message = "El nombre no puede estar vacío")
        String name,
        
        @NotBlank(message = "El correo no puede estar vacío")
        @Email(message = "Correo no válido")
        String email,
        
        @NotNull(message = "El rol es obligatorio")
        String roleName
) {
}

