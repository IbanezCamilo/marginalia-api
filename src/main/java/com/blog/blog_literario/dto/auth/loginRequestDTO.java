package com.blog.blog_literario.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record loginRequestDTO(//Recibe el correo y contraseña del usuario
    @NotBlank(message="Debe escribir un correo")    
    String email,
    @NotBlank(message="Debe ingresar una contraseña")
    String password
){}
