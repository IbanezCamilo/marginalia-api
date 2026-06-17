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

import com.blog.blog_literario.dto.auth.AuthTokenPair;
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
    @Mock RefreshTokenService refreshTokenService;

    @InjectMocks AuthService authService;

    @Test
    void register_validRequest_createsUserAndReturnsTokenPair() {
        var request = new RegisterRequest("Alice", "alice@test.com", "password123");
        User newUser = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        UserDetailsImpl userDetails = new UserDetailsImpl(newUser);

        given(userCreationService.createUser("Alice", "alice@test.com", "password123", Role.READER))
                .willReturn(newUser);
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);
        given(jwtService.generateToken(userDetails, newUser.getTokenVersion())).willReturn("jwt-token");
        given(refreshTokenService.create(newUser)).willReturn("raw-refresh-token");

        AuthTokenPair result = authService.register(request);

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
        verify(userCreationService).createUser("Alice", "alice@test.com", "password123", Role.READER);
        verify(refreshTokenService).create(newUser);
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
    void login_validCredentials_returnsTokenPair() {
        var request = new LoginRequest("alice@test.com", "password123");
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        given(authenticationManager.authenticate(any())).willReturn(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);
        given(jwtService.generateToken(userDetails, user.getTokenVersion())).willReturn("jwt-token");
        given(refreshTokenService.create(user)).willReturn("raw-refresh-token");

        AuthTokenPair result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
        verify(refreshTokenService).create(user);
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

    @Test
    void refresh_validToken_rotatesAndReturnsNewTokenPair() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        var rotationResult = new RefreshTokenService.RotationResult(user, "new-raw-refresh");

        given(refreshTokenService.rotate("old-raw-refresh")).willReturn(rotationResult);
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);
        given(jwtService.generateToken(userDetails, user.getTokenVersion())).willReturn("new-jwt-token");

        AuthTokenPair result = authService.refresh("old-raw-refresh");

        assertThat(result.accessToken()).isEqualTo("new-jwt-token");
        assertThat(result.refreshToken()).isEqualTo("new-raw-refresh");
    }

    @Test
    void logout_withRawToken_deletesRefreshToken() {
        authService.logout("raw-refresh-token", null);
        verify(refreshTokenService).deleteByRawToken("raw-refresh-token");
    }

    @Test
    void logout_withJwt_deletesRefreshTokenByUser() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        given(jwtService.extractUsernameAllowExpired("jwt-token")).willReturn("alice@test.com");
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);

        authService.logout(null, "jwt-token");

        verify(refreshTokenService).deleteAllByUser(user);
    }
}
