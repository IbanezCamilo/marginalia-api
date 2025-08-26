package com.blog.blog_literario.security;


import jakarta.servlet.FilterChain; // Pasa la petición al siguiente filtro en la cadena
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest; //Representa la Petición
import jakarta.servlet.http.HttpServletResponse; //Representa la Repuesta

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor // Crea el constructor para las variables con tipo final
public class JwtAuthenticationFilter extends  OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
    @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        // Extrae el token del encabezado Authorization
        final String authHeader = request.getHeader("Authorization");
        //System.out.println("Authorization Header: " + authHeader); Para pruebas
        final String jwt;
        final String userEmail;


        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // Si no hay token, continúa con la cadena de filtros
            return;
        }

        jwt = authHeader.substring(7); // Extrae el token sin "Bearer "
        userEmail = jwtService.extractUsername(jwt); // Extrae el email del token   
        System.out.println("Usuario Extraido: "+ userEmail);
        // Verifica si el usuario ya está autenticado
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var userDetails = this.userDetailsService.loadUserByUsername(userEmail); // Carga los detalles del usuario
            if (jwtService.isTokenValid(jwt, userDetails)) { // Verifica si el token es válido
                var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); // Establece los detalles de autenticación
                SecurityContextHolder.getContext().setAuthentication(authToken); // Guarda la autenticación en el contexto de seguridad
            }
            System.out.println("Token valido: "+jwtService.isTokenValid(jwt, userDetails));
        }
        filterChain.doFilter(request, response); // Continúa con la cadena de filtros

    }

}
