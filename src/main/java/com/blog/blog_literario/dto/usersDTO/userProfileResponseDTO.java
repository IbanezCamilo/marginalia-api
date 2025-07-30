package com.blog.blog_literario.dto.usersDTO;

//DTO para devolver datos del perfil
public record userProfileResponseDTO(
    Integer id,
    String nombre,
    String descripcion,
    String fotoPerfil
){}
