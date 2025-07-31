package com.blog.blog_literario.services;

import com.blog.blog_literario.dto.authDTO.authResponseDTO;
import com.blog.blog_literario.dto.authDTO.loginRequestDTO;
import com.blog.blog_literario.dto.usersDTO.userCreateDTO;

public interface AuthService {
    authResponseDTO register(userCreateDTO createDTO); // Método plantilla para registrar un usuario
    authResponseDTO login(loginRequestDTO loginDTO); // Método plantilla para login de usuario
}
