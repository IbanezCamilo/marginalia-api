package com.blog.blog_literario.controllers.users;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.users.UserProfileUpdateRequest;
import com.blog.blog_literario.services.users.UserProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints for an authenticated user to read and update their own profile,
 * including profile picture upload and removal.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/me/profile")
public class MyProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userProfileService.getUserProfile(userDetails));
    }

    @PutMapping
    public ResponseEntity<?> updateUser(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody UserProfileUpdateRequest request,
            BindingResult result) {
        if (result.hasErrors()) {
            var error = result.getFieldErrors()
                    .stream()
                    .map(e -> e.getField() + ":" + e.getDefaultMessage())
                    .toList();
            return ResponseEntity.badRequest().body(error);
        }

        try {
            return ResponseEntity.ok(userProfileService.updateProfile(userDetails, request));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al actualizar el perfil: " + e.getMessage());
        }
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
    public ResponseEntity<?> uploadProfileImage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("image") MultipartFile imageFile) {

        if (imageFile.isEmpty()) {
            return ResponseEntity.badRequest().body("No se ha proporcionado ninguna imagen");
        }

        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El archivo debe ser una imagen"));
        }

        try {
            String imageUrl = userProfileService.uploadProfileImage(userDetails, imageFile);
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error al subir la imagen: " + e.getMessage());
        }
    }
}
