package com.blog.blog_literario.services.secundary.Impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.profile.userProfileResponseDTO;
import com.blog.blog_literario.dto.profile.userProfileUpdateDTO;
import com.blog.blog_literario.dto.users.userResponseDTO;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.secundary.UserProfileService;

import java.io.IOException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private static final String FOTO_POR_DEFECTO = "https://servidor.com/images/default-avatar.png";

    @Autowired
    private UserRepository userRepository; // Inyección del repositorio de usuarios

    // Método para mostrar Información en el perfil del usuario
    public userProfileResponseDTO getUserProfile(UserDetails userDetails) {
        // Buscar el usuario
        User usuario = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(
                "Usuario no encontrado con email: " + userDetails.getUsername()));

        // Devolver DTO con los datos
        return new userProfileResponseDTO(
                usuario.getIdUsuario(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getDescripcion(),
                usuario.getFotoPerfil(),
                usuario.getRol().getNombre());
    }

    // Método para actualizar datos del perfil de usuario
    public userProfileResponseDTO updateUserProfile(UserDetails userDetails, userProfileUpdateDTO dto) {
        // Buscar el usuario
        User usuario = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(
                "Usuario no encontrado con email: " + userDetails.getUsername()));

        // Actualizar el usuario
        usuario.setNombre(dto.getNombre());
        // Si la descripción es nula, se deja como está
        usuario.setDescripcion(dto.getDescripcion()); // Actualiza la descripción

        if (dto.getFotoPerfil() != null && !dto.getFotoPerfil().isEmpty()) {
            usuario.setFotoPerfil(dto.getFotoPerfil());
        }

        // Guardar el nuevo usuario actualizado
        User actualizado = userRepository.save(usuario);

        // Actualizar y Retornar userResponse
        return new userProfileResponseDTO(
                actualizado.getIdUsuario(),
                actualizado.getNombre(),
                actualizado.getEmail(),
                actualizado.getDescripcion(),
                actualizado.getFotoPerfil(),
                actualizado.getRol().getNombre());
    }

    // Método para validar la foto de perfil
    // Si la foto es nula, vacía o igual a la foto por defecto, se devuelve la foto por defecto
    public static String obtenerFotoValida(String foto, String fotoPorDefecto) {
        return (foto != null && !foto.isEmpty() && !foto.equals(fotoPorDefecto)) ? foto : fotoPorDefecto;
    }
}
