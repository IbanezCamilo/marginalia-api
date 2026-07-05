package com.blog.blog_literario.dto.posts;
//This DTO is used for updating a post, it contains all the fields that can be updated, but all of them are optional, so the client can choose which fields to update without needing to send all of them

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
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
    public UpdatePostRequest(String title, String content, Integer categoryId, String status) {
        this(title, content, categoryId, status, null, null);
    }

}
