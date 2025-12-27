package com.blog.blog_literario.controllers.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.users.userCreateDTO;
import com.blog.blog_literario.dto.users.userResponseDTO;
import com.blog.blog_literario.dto.users.userUpdateDTO;
import com.blog.blog_literario.services.general.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        List<userResponseDTO> usuarios = userService.getAllUsers();
        return ResponseEntity.ok(usuarios); // status 200 = OK
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Integer id) {
        userResponseDTO usuario = userService.getUserById(id);
        return ResponseEntity.ok(usuario); // status 200 = OK
    }

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody userCreateDTO dto, BindingResult result) {
        if (result.hasErrors()) {
            // Captura y devuelve una lista
            var errores = result.getFieldErrors()
                    .stream()
                    .map(e -> e.getField() + ":" + e.getDefaultMessage())
                    .toList();
            return ResponseEntity.badRequest().body(errores); // status 400: bad request
        }

        userResponseDTO usuarioCreado = userService.createUserWithResponse(dto); // Envia y retorna datos al userService

        return ResponseEntity.status(201).body(usuarioCreado); // status = 201: Guardado exitosamente
    }

    //Actualizar por ID (SOLO PARA ADMINS)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserComplete(@PathVariable Integer id, @Valid @RequestBody userUpdateDTO dto,
            BindingResult result) {
        if (result.hasErrors()) {
            // Captura y devuelve una lista
            var errores = result.getFieldErrors()
                    .stream()
                    .map(e -> e.getField() + ":" + e.getDefaultMessage())
                    .toList();
            return ResponseEntity.badRequest().body(errores);
        }

        userResponseDTO usuarioActualizado = userService.updateUserById(id, dto);
        return ResponseEntity.status(201).body(usuarioActualizado); // 201: Guardado correctamente
    }

    @DeleteMapping("/{id}") // Método para eliminar un usuario por ID
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build(); // 204 No content: Eliminado exitosamente
    }
}
