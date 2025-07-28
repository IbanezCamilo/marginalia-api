package com.blog.blog_literario.services.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.userCreateDTO;
import com.blog.blog_literario.dto.userResponseDTO;
import com.blog.blog_literario.dto.userUpdateDTO;
import com.blog.blog_literario.entities.User;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.UserService;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository; //Inyección del Repositorio de Usuarios

    @Autowired
    private PasswordEncoder passwordEncoder; //Inyección del Encriptador de Contraseñas

    @Override
    public List<userResponseDTO> getAllUsers(){
        return userRepository.findAll() //Retorna todos los usuarios del Repositorio
            .stream() //Se inicia el flujo de los usuarios
            .map(user -> new userResponseDTO( // Por cada usuario se crea un nuevo userResponseDTO
                user.getIdUsuario(),          // con los atributos correspondientes
                user.getNombre(),
                user.getEmail(),
                user.getRol()
            ))
            .toList(); // Se convierte en una lista para un manejo más flexible
    }

    @Override
    public userResponseDTO getUserById(Integer id) {
        User usuario = userRepository.findById(id) // Verifica la existencia del usuario
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con ID: " + id));
        
        //Retornar userResponse
        return new userResponseDTO(
            usuario.getIdUsuario(),
            usuario.getNombre(),
            usuario.getEmail(),
            usuario.getRol()
        );                                                                                 
    }

    @Override
    public userResponseDTO createUser(userCreateDTO dto) {
        // Validar si el correo ya existe
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("El correo ya está en uso");
        }

        // Crear nuevo usuario
        User nuevoUsuario = new User();
        nuevoUsuario.setNombre(dto.getNombre());
        nuevoUsuario.setEmail(dto.getEmail());
        //Creación y Encriptación de Contraseña
        nuevoUsuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        nuevoUsuario.setRol(dto.getRol());

        // Guardar el nuevo usuario creado
        User creado = userRepository.save(nuevoUsuario);
        
        //Crear y Retornar userResponse
        return new userResponseDTO(
            creado.getIdUsuario(),
            creado.getNombre(),
            creado.getEmail(),
            creado.getRol()
        );
    }

    @Override
    public userResponseDTO updateUser(Integer id, userUpdateDTO dto) {
        // Verificar la existencia del usuario
        User usuarioExistente = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // Actualizar el usuario
        usuarioExistente.setNombre(dto.getNombre());
        usuarioExistente.setEmail(dto.getEmail());
        //Actualización y Encriptación de Contraseña
        usuarioExistente.setPassword(passwordEncoder.encode(dto.getPassword()));
        usuarioExistente.setRol(dto.getRol());

        // Guardar el nuevo usuario actualizado
        User actualizado = userRepository.save(usuarioExistente);
        
        //Actualizar y Retornar userResponse
        return new userResponseDTO(
            actualizado.getIdUsuario(),
            actualizado.getNombre(),
            actualizado.getEmail(),
            actualizado.getRol()
        );
    }

    @Override
    public void deleteUser(Integer id) {
        if (!userRepository.existsById(id)) // Verifica la existencia del Usuario
            throw new ResourceNotFoundException("No se encontró el Usuario con ID: " + id);
            
        userRepository.deleteById(id); // Existe el usuario: Elimina el usuario
    }
}
