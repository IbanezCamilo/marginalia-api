package com.blog.blog_literario.dto.authDTO;

//Recibe el correo y contraseña del usuario
public record LoginRequestDTO(
    String email,
    String password
){}
