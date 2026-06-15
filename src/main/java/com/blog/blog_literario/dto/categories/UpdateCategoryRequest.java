package com.blog.blog_literario.dto.categories;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank(message = "El nombre de la categoria no puede estar vacio")
        @Size(min = 2, max = 100, message = "El nombre de la categoría debe tener entre 2 y 100 caracteres")
        String name
        ) {

}
