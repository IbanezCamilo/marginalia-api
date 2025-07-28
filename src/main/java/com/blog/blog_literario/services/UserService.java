package com.blog.blog_literario.services;

import java.util.List;

import com.blog.blog_literario.dto.userCreateDTO; // Inyección del userCreateDTO
import com.blog.blog_literario.dto.userUpdateDTO; // Inyección del userUpdateDTO
import com.blog.blog_literario.entities.User; // Inyección de la Entidad User

public interface UserService {
    User createUser(userCreateDTO dto); // Método plantilla para crear un Usuario
    User updateUser(Integer id, userUpdateDTO dto); // Método plantilla para actualizar un Usuario
    void deleteUser(Integer id); // Método plantilla para eliminar un Usuario
    User getUserById(Integer id); // Método plantilla para obtener un Usuario por ID
    List<User> getAllUsers(); // Método plantilla para obtener Todos los Usuarios
}
