package com.blog.blog_literario.services;

import java.util.List;

import com.blog.blog_literario.dto.userResponseDTO; // Inyección del userResponseDTO
import com.blog.blog_literario.dto.userCreateDTO; // Inyección del userCreateDTO
import com.blog.blog_literario.dto.userUpdateDTO; // Inyección del userUpdateDTO

public interface UserService {
    userResponseDTO createUser(userCreateDTO dto); // Método plantilla para crear un Usuario
    userResponseDTO updateUser(Integer id, userUpdateDTO dto); // Método plantilla para actualizar un Usuario
    void deleteUser(Integer id); // Método plantilla para eliminar un Usuario
    userResponseDTO getUserById(Integer id); // Método plantilla para obtener un Usuario por ID
    List<userResponseDTO> getAllUsers(); // Método plantilla para obtener Todos los Usuarios
}
