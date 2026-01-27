package com.blog.blog_literario.dto.profile;

//DTO para devolver datos del perfil
public record userProfileResponseDTO(
        Integer id,
        String name,
        String email,
        String description,
        String profilePicture, // para guardar la url de la foto de perfil
        String role
        ) {

}
