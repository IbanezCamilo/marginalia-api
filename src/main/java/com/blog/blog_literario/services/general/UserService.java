package com.blog.blog_literario.services.general;

import java.util.List;

import com.blog.blog_literario.dto.auth.registerRequestDTO;
import com.blog.blog_literario.dto.users.userCreateDTO;
import com.blog.blog_literario.dto.users.userResponseDTO;
import com.blog.blog_literario.dto.users.userUpdateDTO;

public interface UserService {

    void createUser(registerRequestDTO dto); // Crear un Usuario autenticado

    userResponseDTO createUserWithResponse(userCreateDTO dto); // Crear un usuario y retornar una respuesta

    userResponseDTO getUserById(Integer id); // Obtener un Usuario por ID

    userResponseDTO updateUserById(Integer id, userUpdateDTO dto); // Actualizar un Usuario

    List<userResponseDTO> getAllUsers(); // Obtener Todos los Usuarios

    //userProfileResponseDTO updateUserProfile(Integer id, userProfileUpdateDTO dto); // Actualizar el perfil de usuario basico por ID
    void deleteUser(Integer id); // Eliminar un Usuario

}
