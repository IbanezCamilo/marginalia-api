package com.blog.blog_literario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class postUpdateDTO {
    @NotBlank(message = "El titulo debe ser obligatorio")
    @Size(max = 100, message = "El titulo no puede tener más de 100 caracteres")
    private String titulo;
    @NotBlank(message = "El contenido no puede estar vacio")
    private String contenido;
    @NotBlank
    private String estado;
    @NotBlank
    private String slug;
    @NotBlank
    private String imagenPortada;
    @NotNull
    private Integer idCategoria;
}
