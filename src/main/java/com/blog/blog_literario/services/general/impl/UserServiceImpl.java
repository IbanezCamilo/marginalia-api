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
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.general.UserService;

import jakarta.validation.constraints.NotNull;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository; // Inyección del Repositorio de Usuarios

    @Autowired
    private RoleRepository roleRepository; // Inyección del Repositorio de Roles

    @Autowired
    private PasswordEncoder passwordEncoder; // Inyección del Encriptador de Contraseñas

    @Override
    public List<userResponseDTO> getAllUsers() {
        return userRepository.findAll() // Retorna todos los usuarios del Repositorio
                .stream() // Se inicia el flujo de los usuarios
                .map(user -> new userResponseDTO( // Por cada usuario se crea un nuevo userResponseDTO
                user.getId(), // con los atributos correspondientes
                user.getName(),
                user.getEmail(),
                user.getRole()))
                .toList(); // Se convierte en una lista para un manejo más flexible
    }
    
    @Override
    public userResponseDTO getUserById(Integer id) {
        User user = userRepository.findById(id) // Verifica la existencia del usuario
                .orElseThrow(() -> new ResourceNotFoundException(
                "No se encontró el usuario con ID: " + id));

        // Retornar userResponse
        return new userResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole());
    }
    //Crea un usuario por medio de la autenticacion y registro

    @Override
    public void createUser(registerRequestDTO dto) {
        // Validar si el correo ya existe
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("El correo ya está en uso");
        }

        // Agrega el rol por defecto desde la DB
        Role defaultRole = roleRepository.findByName("AUTOR")
                .orElseThrow(() -> new ResourceNotFoundException("ROL NO ENCONTRADO"));

        // Crear nuevo usuario
        User newUser = new User();
        newUser.setName(dto.getNombre());
        newUser.setEmail(dto.getEmail());
        // Creación y Encriptación de Contraseña
        newUser.setPassword(passwordEncoder.encode(dto.getPassword()));
        //Asigna un rol por defecto
        newUser.setRole(defaultRole);
        // Asigna una foto de perfil por defecto
        newUser.setProfilePicture("https://servidor.com/images/default-avatar.png");

        // Guardar el nuevo usuario creado
        userRepository.save(newUser);
    }

    //Permite al administrador crear un usuario desde una interfaz única
    @Override
    public userResponseDTO createUserWithResponse(userCreateDTO dto) {
        // Validar si el correo ya existe
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("El correo ya está en uso");
        }

        // Crear nuevo usuario
        User newUser = new User();
        newUser.setName(dto.getName());
        newUser.setEmail(dto.getEmail());
        // Creación y Encriptación de Contraseña
        newUser.setPassword(passwordEncoder.encode(dto.getPassword()));
        //El rol lo asigna el administrador que crea el usuario
        newUser.setRole(dto.getRole());
        // Se asigna una foto de perfil por defecto
        newUser.setProfilePicture("https://servidor.com/images/default-avatar.png");

        // Guardar el nuevo usuario creado
        userRepository.save(newUser);

        // Retornar userResponse
        return new userResponseDTO(
                newUser.getId(),
                newUser.getName(),
                newUser.getEmail(),
                newUser.getRole());
    }

    @Override
    public userResponseDTO updateUserById(Integer id, userUpdateDTO dto) {
        // Verificar la existencia del usuario
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                "Usuario no encontrado con ID: " + id));

        // Actualizar campos basicos
        existingUser.setName(dto.getName());
        existingUser.setEmail(dto.getEmail());
        existingUser.setRole(dto.getRole());

        // Guardar el nuevo usuario actualizado
        User updated = userRepository.save(existingUser);

        // Actualizar y Retornar userResponse
        return new userResponseDTO(
                updated.getId(),
                updated.getName(),
                updated.getEmail(),
                updated.getRole());
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
