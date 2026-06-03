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
 * Handles user self-registration and authentication.
 *
 * <p>Both operations return a signed JWT string which the caller is responsible for
 * writing to the response cookie ({@link com.blog.blog_literario.security.CookieUtil}).
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
     * Creates a new user account with the READER role and returns a JWT for the session.
     *
     * @param request the registration payload (name, email, password)
     * @return a signed JWT string
     * @throws com.blog.blog_literario.exception.UserAlreadyExistsException if the email is already registered
     */
    public String register(RegisterRequest request) {
        User newUser = userCreationService.createUser(
                request.name(),
                request.email(),
                request.password(),
                Role.READER  // Default role for self-registered users
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(newUser.getEmail());

        return jwtService.generateToken(userDetails);
    }

    /**
     * Authenticates a user by email and password and returns a JWT for the session.
     *
     * @param request the login payload (email, password)
     * @return a signed JWT string
     * @throws BadCredentialsException if the email or password is incorrect
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
