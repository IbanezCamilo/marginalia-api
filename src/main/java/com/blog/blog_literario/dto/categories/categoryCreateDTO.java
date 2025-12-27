package com.blog.blog_literario.dto.categories;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class categoryCreateDTO {

    @NotBlank(message = "El nombre no puede estar vacio")
    private String name;
    @NotBlank(message = "El slug no puede estar vacio")
    private String slug;
}
