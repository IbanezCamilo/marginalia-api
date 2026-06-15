package com.blog.blog_literario.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Debe escribir un correo")
        @Email(message = "Correo no válido")
        String email,
        @NotBlank(message = "Debe ingresar una contraseña")
        String password
        ) {

}
