package com.blog.blog_literario.services.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.blog.blog_literario.config.properties.LockoutProperties;
import com.blog.blog_literario.dto.auth.AuthTokenPair;
import com.blog.blog_literario.dto.auth.LoginRequest;
import com.blog.blog_literario.dto.auth.RegisterRequest;
import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
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
    @Mock UserRepository userRepository;
    @Mock LockoutProperties lockoutProperties;

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
    void login_fiveFailedAttempts_locksAccountSoSixthAttemptFailsWithCorrectPassword() {
        var request = new LoginRequest("alice@test.com", "correct-password");
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));

        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));
        given(lockoutProperties.maxAttempts()).willReturn(5);
        given(lockoutProperties.durationMinutes()).willReturn(15);
        given(authenticationManager.authenticate(any()))
                .willThrow(new BadCredentialsException("Bad credentials"));

        // Five consecutive failures with the wrong password lock the account.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Credenciales inválidas");
        }
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now());

        // Sixth attempt fails even though the password is now correct — the pre-check
        // short-circuits before the password is ever verified.
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales inválidas");

        verify(authenticationManager, times(5)).authenticate(any());
    }

    @Test
    void login_successful_resetsFailedAttempts() {
        var request = new LoginRequest("alice@test.com", "password123");
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setFailedLoginAttempts(3);
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));
        given(authenticationManager.authenticate(any())).willReturn(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);
        given(jwtService.generateToken(userDetails, user.getTokenVersion())).willReturn("jwt-token");
        given(refreshTokenService.create(user)).willReturn("raw-refresh-token");

        AuthTokenPair result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void login_lockExpired_allowsNormalLoginAndResets() {
        var request = new LoginRequest("alice@test.com", "password123");
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // lock already expired
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));
        given(authenticationManager.authenticate(any())).willReturn(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);
        given(jwtService.generateToken(userDetails, user.getTokenVersion())).willReturn("jwt-token");
        given(refreshTokenService.create(user)).willReturn("raw-refresh-token");

        AuthTokenPair result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void login_lockedAccount_isRejectedWithSameMessageWithoutCheckingPassword() {
        var request = new LoginRequest("alice@test.com", "correct-password");
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setLockedUntil(LocalDateTime.now().plusMinutes(15)); // currently locked

        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales inválidas");

        // Password is never verified for a locked account — no leak that the account is locked.
        verify(authenticationManager, never()).authenticate(any());
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
