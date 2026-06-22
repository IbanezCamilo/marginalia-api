package com.blog.blog_literario.dto.posts;
//This DTO is used for updating a post, it contains all the fields that can be updated, but all of them are optional, so the client can choose which fields to update without needing to send all of them

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String title,
        @Size(max = 100000, message = "El contenido excede el límite permitido")
        String content,
        Integer categoryId,
        @NotBlank(message = "El estado es obligatorio")
        String status
        ) {

}
