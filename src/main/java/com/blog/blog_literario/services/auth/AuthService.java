package com.blog.blog_literario.services.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.auth.AuthResponse;
import com.blog.blog_literario.dto.auth.LoginRequest;
import com.blog.blog_literario.dto.auth.RegisterRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.UserDetailsServiceImpl;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("El correo ya está en uso");
        }

        Role defaultRole = roleRepository.findByName(Role.AUTHOR)
                .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));

        User newUser = new User();
        newUser.setName(request.nombre());
        newUser.setEmail(request.email());
        newUser.setPassword(passwordEncoder.encode(request.password()));
        newUser.setRole(defaultRole);
        newUser.setProfilePicture("https://ui-avatars.com/api/?name=" + request.nombre() + "&background=random");

        userRepository.save(newUser);

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(token);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.email());
            String token = jwtService.generateToken(userDetails);

            return new AuthResponse(token);
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
    }
}
