package com.blog.blog_literario.dto.posts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A draft can be created with no title and no category — only {@code status} is
 * always required. Title/category/content are enforced as a unit by
 * {@link com.blog.blog_literario.services.posts.MyPostCommandService} only when
 * {@code status} resolves to {@code PUBLISHED}.
 */
public record CreatePostRequest(
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String title,
        @Size(max = 100000, message = "El contenido excede el límite permitido")
        String content,
        Integer categoryId,
        @NotBlank(message = "El estado es obligatorio")
        String status
        ) {

}
