package com.blog.blog_literario.controllers.profile;

import com.blog.blog_literario.dto.profile.userProfileResponseDTO;
import com.blog.blog_literario.dto.profile.userProfileUpdateDTO;
import com.blog.blog_literario.services.secundary.UserProfileService;

import jakarta.validation.Valid; //Jakarta para validaciones
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*; // Anotaciones para crear controladores REST


@RequiredArgsConstructor
@RestController // Indica que esta clase es un controlador REST
@RequestMapping("/api/profile") // Define la ruta base para las peticiones a este controlador
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping // para ver el perfil
    // param: Se obtiene el usuario actualmente autenticado
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        userProfileResponseDTO usuario = userProfileService.getUserProfile(userDetails);
        return ResponseEntity.ok(usuario); // status 200 = ok
    }

    @PutMapping // Método para actualizar un usuario existente
    public ResponseEntity<?> updateUser(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody userProfileUpdateDTO dto,
            BindingResult result) {
        // Validacion de Errores DTO
        if (result.hasErrors()) {
            // Si hay errores de validación, captura y devuelve una lista
            var errores = result.getFieldErrors()
                    .stream() // Inicia el flujo para recorrer la lista
                    .map(e -> e.getField() + ":" + e.getDefaultMessage()) // Estructura los errores en un string
                    .toList(); // Devuelve la lista de mensajes como strings
            return ResponseEntity.badRequest().body(errores); // devuelve un http 400 con la lista de errores
        }

        // Guardar y retornar
        userProfileResponseDTO usuarioActualizado = userProfileService.updateUserProfile(userDetails, dto); // Envia y
                                                                                                            // retorna
                                                                                                            // datos al
                                                                                                            // userService
        return ResponseEntity.status(201).body(usuarioActualizado); // 201: Guardado correctamente
    }

    /*
     * -------------IMPLEMENTACION DEL PERFIL A
     * FUTURO------------------------------------
     * 
     * @PatchMapping("/{id}/profile") // Método para actualizar parcialmente un
     * usuario
     * public ResponseEntity<?> updateProfile(@PathVariable Integer
     * id, @Valid @ModelAttribute userProfileUpdateDTO dto, BindingResult result){
     * //Validacion de Errores DTO
     * if(result.hasErrors()){
     * //Si hay errores de validación, captura y devuelve una lista
     * var errores = result.getFieldErrors()
     * .stream() // Inicia el flujo para recorrer la lista
     * .map(e -> e.getField() + ":" + e.getDefaultMessage()) //Estructura los
     * errores en un string
     * .toList(); // Devuelve la lista de mensajes como strings
     * return ResponseEntity.badRequest().body(errores); //devuelve un http 400 con
     * la lista de errores
     * }
     * 
     * //Guardar y retornar
     * userResponseDTO perfilActualizado =
     * }
     */
}
