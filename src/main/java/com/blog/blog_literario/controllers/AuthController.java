package com.blog.blog_literario.controllers;

import com.blog.blog_literario.dto.authDTO.LoginRequestDTO;
import com.blog.blog_literario.dto.authDTO.authResponseDTO;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.userDetailsServiceImpl;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final userDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequestDTO loginRequestDto){
        try {
            //Autenticación
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequestDto.email(),
                    loginRequestDto.password()
                )
            );

            //Cargar el usuario desde la DB
            UserDetails usuario = userDetailsService.loadUserByUsername(loginRequestDto.email());
            //Generar el token
            String token = jwtService.generateToken(usuario);

            //Retornar respuesta
            return ResponseEntity.ok(new authResponseDTO(token));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).build(); // 401: No autorizado
        }
    }
}
