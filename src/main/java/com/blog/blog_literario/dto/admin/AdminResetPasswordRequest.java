package com.blog.blog_literario.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for an admin-initiated password reset (PUT /api/admin/users/{id}/password).
 * Unlike the self-service flow, this does not require the current password.
 */
public record AdminResetPasswordRequest(
        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
        String newPassword
) {
}
