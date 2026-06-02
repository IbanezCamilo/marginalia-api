package com.blog.blog_literario.dto.posts;

import jakarta.validation.constraints.NotBlank;

public record PatchStatusRequest(
        @NotBlank(message = "El estado no puede estar vacío")
        String status
) {
}
