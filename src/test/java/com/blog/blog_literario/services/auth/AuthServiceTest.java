package com.blog.blog_literario.services.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.blog.blog_literario.dto.auth.LoginRequest;
import com.blog.blog_literario.dto.auth.RegisterRequest;
import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.UserDetailsImpl;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.users.UserCreationService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserDetailsServiceImpl userDetailsService;
    @Mock JwtService jwtService;
    @Mock AuthenticationManager authenticationManager;
    @Mock UserCreationService userCreationService;

    @InjectMocks AuthService authService;

    @Test
    void register_validRequest_createsUserAndReturnsJwt() {
        var request = new RegisterRequest("Alice", "alice@test.com", "password123");
        User newUser = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        UserDetailsImpl userDetails = new UserDetailsImpl(newUser);

        given(userCreationService.createUser("Alice", "alice@test.com", "password123", Role.READER))
                .willReturn(newUser);
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);
        given(jwtService.generateToken(userDetails)).willReturn("jwt-token");

        String token = authService.register(request);

        assertThat(token).isEqualTo("jwt-token");
        verify(userCreationService).createUser("Alice", "alice@test.com", "password123", Role.READER);
    }

    @Test
    void register_duplicateEmail_propagatesUserAlreadyExistsException() {
        var request = new RegisterRequest("Alice", "alice@test.com", "password123");

        given(userCreationService.createUser(any(), any(), any(), any()))
                .willThrow(new UserAlreadyExistsException("El correo 'alice@test.com' ya está registrado"));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("alice@test.com");
    }

    @Test
    void login_validCredentials_returnsJwt() {
        var request = new LoginRequest("alice@test.com", "password123");
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        given(authenticationManager.authenticate(any())).willReturn(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);
        given(jwtService.generateToken(userDetails)).willReturn("jwt-token");

        String token = authService.login(request);

        assertThat(token).isEqualTo("jwt-token");
    }

    @Test
    void login_invalidCredentials_throwsBadCredentialsException() {
        var request = new LoginRequest("alice@test.com", "wrong-password");

        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales inválidas");
    }
}
