package com.blog.blog_literario.dto.posts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
        @NotBlank(message = "El título no puede estar vacío")
        @Size(min = 5, max = 200, message = "El título debe tener entre 5 y 200 caracteres")
        String title,
        @Size(max = 100000, message = "El contenido excede el límite permitido")
        String content,
        @NotNull(message = "La categoría es obligatoria")
        Integer categoryId,
        @NotBlank(message = "El estado es obligatorio")
        String status
        ) {

}
