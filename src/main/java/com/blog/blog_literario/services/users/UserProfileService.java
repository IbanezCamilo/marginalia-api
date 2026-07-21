package com.blog.blog_literario.services.users;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.users.ChangeEmailRequest;
import com.blog.blog_literario.dto.users.ChangePasswordRequest;
import com.blog.blog_literario.dto.users.UserProfileResponse;
import com.blog.blog_literario.dto.users.UserProfileUpdateRequest;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.auth.EmailVerificationService;
import com.blog.blog_literario.services.auth.RefreshTokenService;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.images.StorageService;
import com.blog.blog_literario.utils.UserValidator;

import lombok.RequiredArgsConstructor;

/**
 * Service for authenticated users managing their own profile: reading, updating
 * profile data, and uploading or removing the profile picture.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AvatarResolver avatarResolver;
    private final PasswordEncoder passwordEncoder;
    private final UserUpdateService userUpdateService;
    private final UserValidator userValidator;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UserDetails userDetails) {
        User user = findByEmail(userDetails.getUsername());

        return toResponse(user);
    }

    public UserProfileResponse updateProfile(UserDetails userDetails, UserProfileUpdateRequest request) {
        User user = findByEmail(userDetails.getUsername());

        user.setName(userValidator.validateAndSanitizeName(request.name()));
        user.setDescription(request.description());

        return toResponse(userRepository.save(user));

    }

    /**
     * Replaces the user's profile picture. The previous image is deleted from storage
     * before the new one is saved to prevent accumulating orphaned files.
     *
     * @return the absolute URL of the newly uploaded image
     */
    public String uploadProfileImage(UserDetails userDetails, MultipartFile imageFile) {
        User user = findByEmail(userDetails.getUsername());

        String imageUrl = storageService.save(imageFile, user.getProfilePicture());

        user.setProfilePicture(imageUrl);
        userRepository.save(user);

        return storageService.buildUrl(imageUrl);
    }

    /**
     * Removes the user's profile picture and returns the generated fallback avatar URL.
     */
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

    /**
     * Changes the authenticated user's password after verifying their current one.
     */
    public void changePassword(UserDetails userDetails, ChangePasswordRequest request) {
        User user = findByEmail(userDetails.getUsername());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("La contraseña actual es incorrecta");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser diferente a la actual");
        }

        userUpdateService.updatePassword(user, request.newPassword());
        refreshTokenService.deleteAllByUser(user);
        userRepository.save(user);
    }

    /**
     * Starts a self-service email change after verifying the current password (same gate
     * as {@link #changePassword}). The heavy lifting — sanitization, uniqueness, throttle,
     * token issuance, and dispatching the confirm/cancel emails — is delegated to
     * {@link EmailVerificationService#requestEmailChange}. The account keeps its current
     * address until the new one is confirmed.
     *
     * @throws BadCredentialsException if the supplied current password is incorrect
     */
    public void requestEmailChange(UserDetails userDetails, ChangeEmailRequest request) {
        User user = findByEmail(userDetails.getUsername());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadCredentialsException("La contraseña actual es incorrecta");
        }

        emailVerificationService.requestEmailChange(user, request.newEmail());
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
