package com.blog.blog_literario.services.general;

import java.util.List;

import com.blog.blog_literario.dto.auth.registerRequestDTO;
import com.blog.blog_literario.dto.users.userCreateDTO;
import com.blog.blog_literario.dto.users.userResponseDTO;
import com.blog.blog_literario.dto.users.userUpdateDTO;
//import com.blog.blog_literario.dto.usersDTO.userProfileUpdateDTO;
//import com.blog.blog_literario.dto.usersDTO.userProfileResponseDTO;

public interface UserService {

    //MÉTODOS PRINCIPALES DEL USUARIO
    void createUser(registerRequestDTO dto); // Método plantilla para crear un Usuario autenticado
    userResponseDTO createUserWithResponse(userCreateDTO dto); //Método plantilla para crear un usuario y retornar una respuesta
    userResponseDTO updateUser(Integer id, userUpdateDTO dto); // Método plantilla para actualizar un Usuario
    void deleteUser(Integer id); // Método plantilla para eliminar un Usuario
    userResponseDTO getUserById(Integer id); // Método plantilla para obtener un Usuario por ID
    List<userResponseDTO> getAllUsers(); // Método plantilla para obtener Todos los Usuarios

}
