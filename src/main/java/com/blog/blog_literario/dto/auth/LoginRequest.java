package com.blog.blog_literario.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Debe escribir un correo")
        String email,
        @NotBlank(message = "Debe ingresar una contraseña")
        String password
        ) {

}
