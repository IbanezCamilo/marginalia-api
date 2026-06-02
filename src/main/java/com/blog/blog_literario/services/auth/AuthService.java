package com.blog.blog_literario.services.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.auth.LoginRequest;
import com.blog.blog_literario.dto.auth.RegisterRequest;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.users.UserCreationService;

import lombok.RequiredArgsConstructor;

/**
 * Service for user authentication and registration
 * Handles login and user self-registration (signup)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {
    
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserCreationService userCreationService;

    /**
     * Registers a new user with self-signup
     * Automatically assigns the AUTHOR role to new registrants
     * 
     * @param request the registration request with name, email, password
     * @return AuthResponse with JWT token
     */
    public String register(RegisterRequest request) {
        // Create user with default AUTHOR role
        User newUser = userCreationService.createUser(
                request.name(),
                request.email(),
                request.password(),
                Role.READER  // Default role for self-registered users
        );

        // Generate JWT token
        UserDetails userDetails = userDetailsService.loadUserByUsername(newUser.getEmail());
        
        return jwtService.generateToken(userDetails);
    }

    /**
     * Authenticates a user with email and password
     * 
     * @param request the login request with email and password
     * @return String with JWT token
     * @throws BadCredentialsException if credentials are invalid
     */
    @Transactional(readOnly = true)
    public String login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
            
            return jwtService.generateToken(userDetails);
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
    }
}

