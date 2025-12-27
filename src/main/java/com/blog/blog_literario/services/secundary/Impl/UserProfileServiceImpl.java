package com.blog.blog_literario.services.secundary.Impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.profile.userProfileResponseDTO;
import com.blog.blog_literario.dto.profile.userProfileUpdateDTO;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.secundary.UserProfileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private static final String FOTO_POR_DEFECTO = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/59/Marco_Aurelio_bronzo.JPG/960px-Marco_Aurelio_bronzo.JPG";

    @Autowired
    private UserRepository userRepository;

    // Mostrar Información en el perfil del usuario
    public userProfileResponseDTO getUserProfile(UserDetails userDetails) {
        // Buscar el usuario por email
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(
                "Usuario no encontrado con email: " + userDetails.getUsername()));

        // Devolver DTO con los datos
        return new userProfileResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getDescription(),
                user.getProfilePicture(),
                user.getRole().getName());
    }

    // Actualizar datos del perfil de usuario
    public userProfileResponseDTO updateUserProfile(UserDetails userDetails, userProfileUpdateDTO dto) {
        // Buscar el usuario
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(
                "Usuario no encontrado con email: " + userDetails.getUsername()));

        // Actualizar nombre
        user.setName(dto.getNombre());
        // Actualiza la descripción
        // Si la descripción es nula, se deja como está
        user.setDescription(dto.getDescripcion());

        // Si se envió una URL de foto de perfil, actualizarla
        if (dto.getFotoPerfil() != null && !dto.getFotoPerfil().isEmpty()) {
            user.setProfilePicture(dto.getFotoPerfil());
        }

        // Guardar el nuevo usuario actualizado
        User updated = userRepository.save(user);

        // Actualizar y Retornar userResponse
        return new userProfileResponseDTO(
                updated.getId(),
                updated.getName(),
                updated.getEmail(),
                updated.getDescription(),
                updated.getProfilePicture(),
                updated.getRole().getName());
    }

    // Validar la foto de perfil
    // Si la foto es nula, vacía o igual a la foto por defecto, se devuelve la foto por defecto
    public static String obtenerFotoValida(String foto, String fotoPorDefecto) {
        return (foto != null && !foto.isEmpty() && !foto.equals(fotoPorDefecto)) ? foto : fotoPorDefecto;
    }
}
