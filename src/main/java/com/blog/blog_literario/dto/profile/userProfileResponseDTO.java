package com.blog.blog_literario.dto.profile;

//DTO para devolver datos del perfil
public record userProfileResponseDTO(
    Integer id,
    String nombre,
    String email,
    String descripcion,
    String fotoPerfil, // para guardar la url de la foto de perfil
    String rol
){}
