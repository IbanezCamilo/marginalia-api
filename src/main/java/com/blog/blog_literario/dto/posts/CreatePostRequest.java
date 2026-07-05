package com.blog.blog_literario.dto.posts;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A draft can be created with no title and no category — only {@code status} is
 * always required. Title/category/content are enforced as a unit by
 * {@link com.blog.blog_literario.services.posts.MyPostCommandService} only when
 * {@code status} resolves to {@code PUBLISHED}.
 *
 * <p>{@code focalX}/{@code focalY} are the normalized ([0,1]) cover-image focal point;
 * {@code null} leaves the post's default (center, 0.5).
 */
public record CreatePostRequest(
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String title,
        @Size(max = 100000, message = "El contenido excede el límite permitido")
        String content,
        Integer categoryId,
        @NotBlank(message = "El estado es obligatorio")
        String status,
        @DecimalMin(value = "0.0", message = "El punto focal debe estar entre 0 y 1")
        @DecimalMax(value = "1.0", message = "El punto focal debe estar entre 0 y 1")
        BigDecimal focalX,
        @DecimalMin(value = "0.0", message = "El punto focal debe estar entre 0 y 1")
        @DecimalMax(value = "1.0", message = "El punto focal debe estar entre 0 y 1")
        BigDecimal focalY
        ) {

    /** Backward-compatible constructor for callers that don't supply a focal point. */
    public CreatePostRequest(String title, String content, Integer categoryId, String status) {
        this(title, content, categoryId, status, null, null);
    }

}
