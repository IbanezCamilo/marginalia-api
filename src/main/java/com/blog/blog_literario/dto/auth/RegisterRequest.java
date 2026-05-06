package com.blog.blog_literario.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "El nombre no puede estar vacío")
        String name,
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo no válido")
        String email,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password
        ) {

}
