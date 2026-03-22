package com.blog.blog_literario.services.users;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.profile.UserProfileResponse;
import com.blog.blog_literario.dto.profile.UserProfileUpdateRequest;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.general.ImageStorageServiceV2;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class UserProfileServiceV2 {

    private final UserRepository userRepository;
    private final ImageStorageServiceV2 imageStorageService;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UserDetails userDetails) {
        // Buscar el usuario por email
        User user = findByEmail(userDetails.getUsername());

        // Return user data
        return toResponse(user);
    }

    // Actualizar datos del perfil de usuario
    public UserProfileResponse updateProfile(UserDetails userDetails, UserProfileUpdateRequest request) {
        // Buscar el usuario
        User user = findByEmail(userDetails.getUsername());
        // Actualizar nombre
        user.setName(request.name());
        // Actualiza la descripción
        user.setDescription(request.description());

        // Guardar el nuevo usuario actualizado
        return toResponse(userRepository.save(user));

    }

    public String uploadProfileImage(UserDetails userDetails, MultipartFile imageFile) {
        User user = findByEmail(userDetails.getUsername());

        String imageUrl = imageStorageService.saveImage(imageFile);
        user.setProfilePicture(imageUrl);
        userRepository.save(user);
        return imageUrl;
    }

    // Validar la foto de perfil
    // Si la foto es nula, vacía o igual a la foto por defecto, se devuelve la foto por defecto
    public static String obtenerFotoValida(String foto, String fotoPorDefecto) {
        return (foto != null && !foto.isEmpty() && !foto.equals(fotoPorDefecto)) ? foto : fotoPorDefecto;
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getDescription(),
                user.getProfilePicture(),
                user.getRole().getName(),
                user.getCreatedAt()
        );
    }
}
