package com.blog.blog_literario.services.general;

import com.blog.blog_literario.dto.auth.authResponseDTO;
import com.blog.blog_literario.dto.auth.loginRequestDTO;
import com.blog.blog_literario.dto.auth.registerRequestDTO;

public interface AuthService {
    authResponseDTO register(registerRequestDTO createDTO); // Método plantilla para registrar un usuario
    authResponseDTO login(loginRequestDTO loginDTO); // Método plantilla para login de usuario
}
