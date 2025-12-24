package com.blog.blog_literario.services.general.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.auth.registerRequestDTO;
import com.blog.blog_literario.dto.users.userCreateDTO;
import com.blog.blog_literario.dto.users.userResponseDTO;
import com.blog.blog_literario.dto.users.userUpdateDTO;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Rol;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RolRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.general.UserService;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository; // Inyección del Repositorio de Usuarios

    @Autowired
    private RolRepository rolRepository; // Inyección del Repositorio de Roles

    @Autowired
    private PasswordEncoder passwordEncoder; // Inyección del Encriptador de Contraseñas

    @Override
    public List<userResponseDTO> getAllUsers() {
        return userRepository.findAll() // Retorna todos los usuarios del Repositorio
                .stream() // Se inicia el flujo de los usuarios
                .map(user -> new userResponseDTO( // Por cada usuario se crea un nuevo userResponseDTO
                user.getIdUsuario(), // con los atributos correspondientes
                user.getNombre(),
                user.getEmail(),
                user.getRol()))
                .toList(); // Se convierte en una lista para un manejo más flexible
    }

    @Override
    public userResponseDTO getUserById(Integer id) {
        User usuario = userRepository.findById(id) // Verifica la existencia del usuario
                .orElseThrow(() -> new ResourceNotFoundException(
                "No se encontró el usuario con ID: " + id));

        // Retornar userResponse
        return new userResponseDTO(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol());
    }
    //Crea un usuario por medio de la autenticacion y registro

    @Override
    public void createUser(registerRequestDTO dto) {
        // Validar si el correo ya existe
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("El correo ya está en uso");
        }

        // Agrega el rol por defecto desde la DB
        Rol rolDefault = rolRepository.findByNombre("AUTOR")
                .orElseThrow(() -> new ResourceNotFoundException("ROL NO ENCONTRADO"));

        // Crear nuevo usuario
        User nuevoUsuario = new User();
        nuevoUsuario.setNombre(dto.getNombre());
        nuevoUsuario.setEmail(dto.getEmail());
        // Creación y Encriptación de Contraseña
        nuevoUsuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        //Asigna un rol por defecto
        nuevoUsuario.setRol(rolDefault);
        // Asigna una foto de perfil por defecto
        nuevoUsuario.setFotoPerfil("https://servidor.com/images/default-avatar.png");

        // Guardar el nuevo usuario creado
        userRepository.save(nuevoUsuario);
    }

    //Permite al administrador crear un usuario desde una interfaz única
    @Override
    public userResponseDTO createUserWithResponse(userCreateDTO dto) {
        // Validar si el correo ya existe
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("El correo ya está en uso");
        }

        // Crear nuevo usuario
        User nuevoUsuario = new User();
        nuevoUsuario.setNombre(dto.getNombre());
        nuevoUsuario.setEmail(dto.getEmail());
        // Creación y Encriptación de Contraseña
        nuevoUsuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        //El rol lo asigna el administrador que crea el usuario
        nuevoUsuario.setRol(dto.getRol());
        // Se asigna una foto de perfil por defecto
        nuevoUsuario.setFotoPerfil("https://servidor.com/images/default-avatar.png");

        // Guardar el nuevo usuario creado
        userRepository.save(nuevoUsuario);

        // Retornar userResponse
        return new userResponseDTO(
                nuevoUsuario.getIdUsuario(),
                nuevoUsuario.getNombre(),
                nuevoUsuario.getEmail(),
                nuevoUsuario.getRol());
    }

    @Override
    public userResponseDTO updateUserById(Integer id, userUpdateDTO dto) {
        // Verificar la existencia del usuario
        User usuarioExistente = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                "Usuario no encontrado con ID: " + id));

        // Actualizar campos basicos
        usuarioExistente.setNombre(dto.getNombre());
        usuarioExistente.setEmail(dto.getEmail());
        usuarioExistente.setRol(dto.getRol());

        // Guardar el nuevo usuario actualizado
        User actualizado = userRepository.save(usuarioExistente);

        // Actualizar y Retornar userResponse
        return new userResponseDTO(
                actualizado.getIdUsuario(),
                actualizado.getNombre(),
                actualizado.getEmail(),
                actualizado.getRol());
    }

//     @Override
//     public userProfileResponseDTO updateUserProfile(Integer id, userProfileUpdateDTO dto) {
//         // Buscar usuario
//         User usuarioExistente = userRepository.findById(id)
//                 .orElseThrow(() -> new ResourceNotFoundException(
//                 "Usuario no encontrado con ID: " + id));
//         // Actualizar solo campos del perfil
//         usuarioExistente.setNombre(dto.getNombre());
//         usuarioExistente.setDescripcion(dto.getDescripcion());
//         // Si se envió foto de perfil, actualizarla
//         if (dto.getFotoPerfil() != null && !dto.getFotoPerfil().isEmpty()) {
//             usuarioExistente.setFotoPerfil(dto.getFotoPerfil());
//         }
//         // Guardar
//         User actualizado = userRepository.save(usuarioExistente);
//         // Retornar DTO de perfil
//         return new userProfileResponseDTO(
//                 actualizado.getIdUsuario(),
//                 actualizado.getNombre(),
//                 actualizado.getEmail(),
//                 actualizado.getDescripcion(),
//                 actualizado.getFotoPerfil(),
//                 actualizado.getRol().getNombre());
//     }
    @Override
    public void deleteUser(Integer id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("No se encontró el Usuario con ID: " + id);
        }

        userRepository.deleteById(id); // Elimina el usuario
    }

}
