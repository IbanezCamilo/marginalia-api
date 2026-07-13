package com.blog.blog_literario.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo no válido")
        String email
        ) {

}
