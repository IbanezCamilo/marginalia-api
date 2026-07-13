package com.blog.blog_literario.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "El token de verificación es obligatorio")
        String token
        ) {

}
