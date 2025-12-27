package com.blog.blog_literario.services.general.impl;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.auth.authResponseDTO;
import com.blog.blog_literario.dto.auth.loginRequestDTO;
import com.blog.blog_literario.dto.auth.registerRequestDTO;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.general.AuthService;
import com.blog.blog_literario.services.general.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public authResponseDTO register(registerRequestDTO dto) {

        // Delega la creacion del usuario al userService
        userService.createUser(dto);

        // Cargar el usuario desde la DB
        UserDetails infoUsuario = userDetailsService.loadUserByUsername(dto.getEmail());
        System.out.println("Usuario registrado exitosamente: " + dto.getEmail());
        // Generar el token
        String token = jwtService.generateToken(infoUsuario);
        // Imprimir el token en consola
        System.out.println("Token generado: " + token);

        // Retornar el token
        return new authResponseDTO(token);
    }

    @Override
    public authResponseDTO login(loginRequestDTO dto) {
        try {
            // Autenticación
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            dto.email(),
                            dto.password()));

            // Cargar el usuario desde la DB
            UserDetails usuario = userDetailsService.loadUserByUsername(dto.email());
            // Generar el token
            String token = jwtService.generateToken(usuario);

            return new authResponseDTO(token);
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Crendenciales Inválidas");
        }
    }
}
