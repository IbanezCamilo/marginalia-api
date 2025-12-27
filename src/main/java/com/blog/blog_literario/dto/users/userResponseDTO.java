package com.blog.blog_literario.dto.users;

import com.blog.blog_literario.model.Role;

public record userResponseDTO(
        Integer id,
        String nombre,
        String email,
        Role role
        ) {

}
