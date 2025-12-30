package com.blog.blog_literario.controllers.profile;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult; //Jakarta para validaciones
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.profile.userProfileResponseDTO; // Anotaciones para crear controladores REST
import com.blog.blog_literario.dto.profile.userProfileUpdateDTO;
import com.blog.blog_literario.services.secundary.UserProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/user/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

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
}
