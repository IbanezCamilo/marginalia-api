package com.blog.blog_literario.dto.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for self-service email change (PUT /api/me/profile/email). Requires the current
 * password for verification, mirroring {@link ChangePasswordRequest}. The change is not
 * applied until the new address is confirmed via the emailed link.
 */
public record ChangeEmailRequest(
        @NotBlank(message = "El nuevo correo es obligatorio")
        @Email(message = "El correo no es válido")
        @Size(max = 100, message = "El correo no puede superar los 100 caracteres")
        String newEmail,

        @NotBlank(message = "La contraseña actual es obligatoria")
        String currentPassword
) {
}
