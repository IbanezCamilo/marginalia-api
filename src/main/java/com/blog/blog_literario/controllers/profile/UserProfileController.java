package com.blog.blog_literario.controllers.profile;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult; //Jakarta para validaciones
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.profile.userProfileResponseDTO; // Anotaciones para crear controladores REST
import com.blog.blog_literario.dto.profile.userProfileUpdateDTO;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.general.ImageStorageService;
import com.blog.blog_literario.services.secundary.UserProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/user/profile")
public class UserProfileController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ImageStorageService imageStorageService;
    @Autowired
    private UserProfileService userProfileService;

    @GetMapping
    // param: Obtiene el usuario Autenticado
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        userProfileResponseDTO user = userProfileService.getUserProfile(userDetails);
        return ResponseEntity.ok(user); // status 200 = ok
    }

    @PutMapping
    public ResponseEntity<?> updateUser(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody userProfileUpdateDTO dto,
            BindingResult result) {
        // Validacion de Errores DTO
        if (result.hasErrors()) {
            var error = result.getFieldErrors()
                    .stream()
                    .map(e -> e.getField() + ":" + e.getDefaultMessage())
                    .toList();
            return ResponseEntity.badRequest().body(error);
        }

        try {
            //Envia datos al userService
            userProfileResponseDTO userUpdated = userProfileService.updateUserProfile(userDetails, dto);

            return ResponseEntity.ok(userUpdated);
        } catch (Exception e) {
            //status 500: internal error
            return ResponseEntity.status(500).body("Error al actualizar el perfil " + e.getMessage());
        }
    }

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadProfileImage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("image") MultipartFile image) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String imageUrl = imageStorageService.saveImage(image);

        user.setProfilePicture(imageUrl); //replace the image
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));

    }
}
