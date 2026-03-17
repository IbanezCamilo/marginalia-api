package com.blog.blog_literario.services.profile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.profile.UserProfileResponse;
import com.blog.blog_literario.dto.profile.UserProfileUpdateRequest;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.profile.UserProfileServiceV2;
import com.blog.blog_literario.services.general.ImageStorageService;   

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class UserProfileServiceV2 {

    private static final String FOTO_POR_DEFECTO = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/59/Marco_Aurelio_bronzo.JPG/960px-Marco_Aurelio_bronzo.JPG";

    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

    @Transactional(readOnly=true)
    public UserProfileResponse getUserProfile(UserDetails userDetails) {
        // Buscar el usuario por email
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(
                "Usuario no encontrado con email: " + userDetails.getUsername()));

        System.out.println("ProfilePicture: " + user.getProfilePicture());
                
        // Return user data
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getDescription(),
                user.getProfilePicture(),
                user.getRole().getName());
    }

    // Actualizar datos del perfil de usuario
    public UserProfileResponse updateProfile(UserDetails userDetails, UserProfileUpdateRequest request) {
        // Buscar el usuario
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado con email: " + userDetails.getUsername()));

        // Actualizar nombre
        user.setName(request.name());
        // Actualiza la descripción
        user.setDescription(request.getDescription());

        // Si se envió una URL de foto de perfil, actualizarla
        if (request.getProfilePicture() != null && !request.getProfilePicture().isEmpty()) {
            user.setProfilePicture(request.getProfilePicture());
        }

        // Guardar el nuevo usuario actualizado
        User updated = userRepository.save(user);

        // Actualizar y Retornar userResponse
        return new UserProfileResponse(
                updated.getId(),
                updated.getName(),
                updated.getEmail(),
                updated.getDescription(),
                updated.getProfilePicture(),
                updated.getRole().getName());
    }

    public UseProfileResponse uploadUserImage (UserDetails userDetails, MultipartFile imageFile){
        User user = userRepository.findByEmail(userDetails.getUsername())
                        .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado con email: " + userDetails.getUsername()));
    
        String imageUrl = imageStorageService.
                    }

    // Validar la foto de perfil
    // Si la foto es nula, vacía o igual a la foto por defecto, se devuelve la foto por defecto
    public static String obtenerFotoValida(String foto, String fotoPorDefecto) {
        return (foto != null && !foto.isEmpty() && !foto.equals(fotoPorDefecto)) ? foto : fotoPorDefecto;
    }
}

