package com.blog.blog_literario.controllers;

import com.blog.blog_literario.dto.authDTO.loginRequestDTO;
import com.blog.blog_literario.dto.usersDTO.userCreateDTO;
import com.blog.blog_literario.dto.authDTO.authResponseDTO;
import com.blog.blog_literario.services.AuthService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid loginRequestDTO dto, BindingResult result) {
        // Validacion de Errores DTO
        if (result.hasErrors()) {
            // Si hay errores de validación, captura y devuelve una lista
            var errores = result.getFieldErrors()
                    .stream() // Inicia el flujo para recorrer la lista
                    .map(e -> e.getField() + ":" + e.getDefaultMessage()) // Estructura los errores en un string
                    .toList(); // Devuelve la lista de mensajes como strings
            return ResponseEntity.badRequest().body(errores); // devuelve un http 400 con la lista de errores
        }
        // Envio de datos al Service
        authResponseDTO authResponse = authService.login(dto);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid userCreateDTO dto, BindingResult result) {
        // Validacion de Errores DTO
        if (result.hasErrors()) {
            // Si hay errores de validación, captura y devuelve una lista
            var errores = result.getFieldErrors()
                    .stream() // Inicia el flujo para recorrer la lista
                    .map(e -> e.getField() + ":" + e.getDefaultMessage()) // Estructura los errores en un string
                    .toList(); // Devuelve la lista de mensajes como strings
            return ResponseEntity.badRequest().body(errores); // devuelve un http 400 con la lista de errores
        }

        // Envio de datos al Service
        authResponseDTO authResponse = authService.register(dto);
        System.out.println("Usuario registrado exitosamente: " + dto.getEmail());
        return ResponseEntity.ok(authResponse);
    }
}
