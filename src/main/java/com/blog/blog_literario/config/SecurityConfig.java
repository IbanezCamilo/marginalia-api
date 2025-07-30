package com.blog.blog_literario.config; // Ajusta según tu estructura

import com.blog.blog_literario.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor; //anotacion lombok para generar constructor "final"

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; //Declara tipos de peticion (GET, PUT etc...)
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
//Usa una base de datos para autenticar mediante usuario/contraseña.
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//Orquesta el proceso de autenticación.
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
//Permite definir como se protegeran las rutas HTTP
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//Utilizado para desactivar CSRF (Cross-Site Request Forgery).
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
//Intefaz para subir los archivos a la DB
import org.springframework.security.core.userdetails.UserDetailsService;
//Clases para encriptar contraseña del usuario
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
//Filtro de seguridad predeterminado de Spring
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    @Bean // Configuración de la cadena de filtros
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception{
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean // Encriptación de Contraseñas
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // PARA DEPURAR EN PRODUCCIÓN
    // @Bean
    // public SecurityFilterChain securityFilterChain(HttpSecurity http) throws
    // Exception {
    // http
    // .csrf().disable() // Solo mientras estás en desarrollo
    // .authorizeHttpRequests(auth -> auth
    // .anyRequest().permitAll()
    // );
    // return http.build();
    // }
}
