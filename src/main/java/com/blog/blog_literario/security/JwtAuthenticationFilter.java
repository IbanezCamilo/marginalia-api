package com.blog.blog_literario.security;

import java.io.IOException; // Pasa la petición al siguiente filtro en la cadena

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; //Representa la Petición
import org.springframework.security.core.context.SecurityContextHolder; //Representa la Repuesta
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor // Crea el constructor para las variables con tipo final
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
        final String path = request.getServletPath();
        final String method = request.getMethod();

        if (isPublicRoute(path, method)) {
            filterChain.doFilter(request, response); // Si la ruta es pública, continúa con la cadena de filtros
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // Si no hay token, continúa con la cadena de filtros
            return;
        }

        try {

            jwt = authHeader.substring(7); // Extrae el token sin "Bearer "
            userEmail = jwtService.extractUsername(jwt); // Extrae el email del token   
            System.out.println("Usuario Extraido: " + userEmail);
            // Verifica si el usuario ya está autenticado
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var userDetails = this.userDetailsService.loadUserByUsername(userEmail); // Carga los detalles del usuario
                if (jwtService.isTokenValid(jwt, userDetails)) { // Verifica si el token es válido
                    var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); // Establece los detalles de autenticación
                    SecurityContextHolder.getContext().setAuthentication(authToken); // Guarda la autenticación en el contexto de seguridad
                    System.out.println("Authorities: " + userDetails.getAuthorities());
                }
                System.out.println("Token valido: " + jwtService.isTokenValid(jwt, userDetails));
            }
        } catch (Exception e) {
            System.out.println("ERROR en JwtAuthenticationFilter: " + e.getMessage());
            e.printStackTrace();
        }
        filterChain.doFilter(request, response); // Continúa con la cadena de filtros

    }

    private boolean isPublicRoute(String path, String method) {
        if (path.startsWith("/api/auth/")
                || path.startsWith("/api/images/")) {
            return true;
        }

        if ("GET".equals(method)) {
            if (path.startsWith("/api/posts/") || path.startsWith("/api/categories/")) {
                return true;
            }
        }
        return false;
    }

}
