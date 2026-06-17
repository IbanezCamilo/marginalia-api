package com.blog.blog_literario.controllers.users;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.users.ChangePasswordRequest;
import com.blog.blog_literario.dto.users.UserProfileResponse;
import com.blog.blog_literario.dto.users.UserProfileUpdateRequest;
import com.blog.blog_literario.services.users.UserProfileService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints for an authenticated user to read and update their own profile,
 * including profile picture upload and removal.
 */
@Tag(name = "My Profile")
@SecurityRequirement(name = "cookieAuth")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/me/profile")
public class MyProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProfileService.getUserProfile(userDetails));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateUser(@AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.updateProfile(userDetails, request));
    }

    @DeleteMapping("/image")
    public ResponseEntity<Map<String, String>> deleteProfileImage(@AuthenticationPrincipal UserDetails userDetails) {
        String avatarUrl = userProfileService.deleteProfileImage(userDetails);
        return ResponseEntity.ok(Map.of("imageUrl", avatarUrl));
    }

    /**
     * Uploads a new profile picture. The Content-Type header is validated before
     * delegating to the storage service, which also checks magic bytes.
     */
    @PostMapping("/image")
    public ResponseEntity<Map<String, String>> uploadProfileImage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("image") MultipartFile imageFile) {

        if (imageFile.isEmpty()) {
            throw new IllegalArgumentException("No se ha proporcionado ninguna imagen");
        }

        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen");
        }

        String imageUrl = userProfileService.uploadProfileImage(userDetails, imageFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("imageUrl", imageUrl));
    }

    /**
     * Changes the authenticated user's password. Requires the current password
     * for verification.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {

        userProfileService.changePassword(userDetails, request);
        return ResponseEntity.ok().build();
    }
}
