package com.blog.blog_literario.services.users;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.users.UserProfileResponse;
import com.blog.blog_literario.dto.users.UserProfileUpdateRequest;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.images.StorageService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AvatarResolver avatarResolver;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UserDetails userDetails) {
        User user = findByEmail(userDetails.getUsername());

        return toResponse(user);
    }

    // Update user profile data
    public UserProfileResponse updateProfile(UserDetails userDetails, UserProfileUpdateRequest request) {
        User user = findByEmail(userDetails.getUsername());

        user.setName(request.name());
        user.setDescription(request.description());

        return toResponse(userRepository.save(user));

    }

    public String uploadProfileImage(UserDetails userDetails, MultipartFile imageFile) {
        User user = findByEmail(userDetails.getUsername());

        String imageUrl = storageService.save(imageFile, user.getProfilePicture());

        user.setProfilePicture(imageUrl);
        userRepository.save(user);

        return storageService.buildUrl(imageUrl);
    }

    public String deleteProfileImage(UserDetails userDetails) {
        User user = findByEmail(userDetails.getUsername());

        storageService.delete(user.getProfilePicture());
        user.setProfilePicture("");
        userRepository.save(user);

        return avatarResolver.resolve(null, user.getName());
    }

    public static String getValidPhoto(String photo, String defaultPhoto) {
        return (photo != null && !photo.isEmpty() && !photo.equals(defaultPhoto)) ? photo : defaultPhoto;
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
                avatarResolver.resolve(user.getProfilePicture(), user.getName()),
                user.getRole().getName(),
                user.getCreatedAt()
        );
    }
}
