package com.blog.blog_literario.dto.categories;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(
        @NotBlank(message = "El nombre de la categoria no puede estar vacio")
        String name
        ) {

}
