package com.blog.blog_literario.services;

import java.util.List;

import com.blog.blog_literario.dto.usersDTO.userCreateDTO;
import com.blog.blog_literario.dto.usersDTO.userResponseDTO;
import com.blog.blog_literario.dto.usersDTO.userUpdateDTO;
//import com.blog.blog_literario.dto.usersDTO.userProfileUpdateDTO;
//import com.blog.blog_literario.dto.usersDTO.userProfileResponseDTO;

public interface UserService {

    //MÉTODOS PRINCIPALES DEL USUARIO
    userResponseDTO createUser(userCreateDTO dto); // Método plantilla para crear un Usuario
    userResponseDTO updateUser(Integer id, userUpdateDTO dto); // Método plantilla para actualizar un Usuario
    void deleteUser(Integer id); // Método plantilla para eliminar un Usuario
    userResponseDTO getUserById(Integer id); // Método plantilla para obtener un Usuario por ID
    List<userResponseDTO> getAllUsers(); // Método plantilla para obtener Todos los Usuarios

    //----------------PROXIMO A IMPLEMENTAR-----------------------------
    
    //MÉTODOS DEL PERFIL-USUARIO 
   // userProfileResponseDTO updateUserProfile(Integer id, userProfileUpdateDTO dto);//Método plantilla para actualizar perfil
}
