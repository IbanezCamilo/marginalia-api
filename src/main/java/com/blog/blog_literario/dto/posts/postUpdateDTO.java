package com.blog.blog_literario.dto.posts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class postUpdateDTO {
    @NotBlank(message = "El titulo debe ser obligatorio")
    @Size(max = 100, message = "El titulo no puede tener más de 100 caracteres")
    private String title;
    @NotBlank(message = "El contenido no puede estar vacio")
    private String content;
    @NotBlank
    private String status;
    @NotBlank
    private String slug;
    @NotBlank
    private String coverImage;
    @NotNull
    private Integer categoryId;
}
