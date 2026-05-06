package com.blog.blog_literario.services.users;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.users.UserProfileResponse;
import com.blog.blog_literario.dto.users.UserProfileUpdateRequest;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.LocalStorageService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final LocalStorageService localStorageService;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UserDetails userDetails) {
        User user = findByEmail(userDetails.getUsername());

        return toResponse(user);
    }

    // Actualizar datos del perfil de usuario
    public UserProfileResponse updateProfile(UserDetails userDetails, UserProfileUpdateRequest request) {
        User user = findByEmail(userDetails.getUsername());

        user.setName(request.name());
        user.setDescription(request.description());

        return toResponse(userRepository.save(user));

    }

    public String uploadProfileImage(UserDetails userDetails, MultipartFile imageFile) {
        User user = findByEmail(userDetails.getUsername());

        String imageUrl = localStorageService.save(imageFile, user.getProfilePicture());

        user.setProfilePicture(imageUrl);
        userRepository.save(user);

        return localStorageService.buildUrl(imageUrl);
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

    private String resolveProfilePicture(User user){
        
        if(user.getProfilePicture() != null){
            return localStorageService.buildUrl(user.getProfilePicture());
        }

        String encodedName = URLEncoder.encode(user.getName(), StandardCharsets.UTF_8);
        return "https://ui-avatars.com/api/?name=" + encodedName + "&background=random";
        
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getDescription(),
                resolveProfilePicture(user),
                user.getRole().getName(),
                user.getCreatedAt()
        );
    }
}
