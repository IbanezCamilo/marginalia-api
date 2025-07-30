package com.blog.blog_literario.dto.usersDTO;

import com.blog.blog_literario.model.Rol;

public record userResponseDTO(
    Integer id,
    String nombre,
    String email,
    Rol rol
) {}
