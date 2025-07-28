package com.blog.blog_literario.services.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.userCreateDTO;
import com.blog.blog_literario.dto.userUpdateDTO;
import com.blog.blog_literario.entities.User;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.UserService;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository; //Inyección del Repositorio de Usuarios

    @Override
    public List<User> getAllUsers(){
        return userRepository.findAll(); //Retorna todos los usuarios del Repositorio
    }

    @Override
    public User getUserById(Integer id) {
        return userRepository.findById(id) // Verifica la existencia del usuario
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con ID: " + id)); // No existe
                                                                                                           // el usuario:
                                                                                                           // Lanza
                                                                                                           // Exepcion
    }

    @Override
    public User createUser(userCreateDTO dto) {
        // Validar si el correo ya existe
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("El correo ya está en uso");
        }

        // Crear nuevo usuario
        User nuevoUsuario = new User();
        nuevoUsuario.setNombre(dto.getNombre());
        nuevoUsuario.setEmail(dto.getEmail());
        nuevoUsuario.setPassword(dto.getPassword());
        nuevoUsuario.setRol(dto.getRol());

        // Guardar y retornar
        return userRepository.save(nuevoUsuario); // 201: Creado exitosamente
    }

    @Override
    public User updateUser(Integer id, userUpdateDTO dto) {
        // Verificar la existencia del usuario
        User usuarioExistente = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        // Actualizar el usuario
        usuarioExistente.setNombre(dto.getNombre());
        usuarioExistente.setEmail(dto.getEmail());
        usuarioExistente.setPassword(dto.getPassword());
        usuarioExistente.setRol(dto.getRol());

        // Guardar y retonar
        return userRepository.save(usuarioExistente);
    }

    @Override
    public void deleteUser(Integer id) {
        if (!userRepository.existsById(id)) // Verifica la existencia del Usuario
            throw new ResourceNotFoundException("No se encontró el Usuario con ID: " + id); // No existe el usuario:
                                                                                            // Lanza
                                                                                            // Exepcion
        userRepository.deleteById(id); // Existe el usuario: Elimina el usuario
    }
}
