package com.blog.blog_literario.services.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.utils.UserValidator;

@ExtendWith(MockitoExtension.class)
class UserCreationServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserValidator userValidator;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserCreationService userCreationService;

    @Test
    void createUser_validInput_savesEncodedPasswordAndAssignedRole() {
        given(userValidator.validateAndSanitizeName("Alice")).willReturn("Alice");
        given(userValidator.validateAndSanitizeEmail("alice@test.com")).willReturn("alice@test.com");
        given(roleRepository.findByName(Role.READER)).willReturn(Optional.of(new Role(Role.READER)));
        given(passwordEncoder.encode("password123")).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User result = userCreationService.createUser("Alice", "alice@test.com", "password123", Role.READER);

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getEmail()).isEqualTo("alice@test.com");
        assertThat(result.getPassword()).isEqualTo("encoded-password");
        assertThat(result.getRole().getName()).isEqualTo(Role.READER);
        assertThat(result.getProfilePicture()).isNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_emailAlreadyExists_throwsUserAlreadyExistsException() {
        given(userValidator.validateAndSanitizeName("Alice")).willReturn("Alice");
        given(userValidator.validateAndSanitizeEmail("alice@test.com"))
                .willThrow(new UserAlreadyExistsException("El correo 'alice@test.com' ya está registrado"));

        assertThatThrownBy(() -> userCreationService.createUser("Alice", "alice@test.com", "password123", Role.READER))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_unknownRole_throwsResourceNotFoundException() {
        given(userValidator.validateAndSanitizeName("Alice")).willReturn("Alice");
        given(userValidator.validateAndSanitizeEmail("alice@test.com")).willReturn("alice@test.com");
        given(roleRepository.findByName("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userCreationService.createUser("Alice", "alice@test.com", "password123", "UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_ownerRole_throwsIllegalStateException() {
        assertThatThrownBy(() -> userCreationService.createUser("Alice", "alice@test.com", "password123", Role.OWNER))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any());
        verify(userValidator, never()).validateAndSanitizeName(any());
    }

    @Test
    void createUser_ownerRoleCaseInsensitive_throwsIllegalStateException() {
        assertThatThrownBy(() -> userCreationService.createUser("Alice", "alice@test.com", "password123", "owner"))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_sanitizesNameAndEmailViaValidator() {
        given(userValidator.validateAndSanitizeName("  Alice  ")).willReturn("Alice");
        given(userValidator.validateAndSanitizeEmail("  Alice@Test.com  ")).willReturn("alice@test.com");
        given(roleRepository.findByName(Role.READER)).willReturn(Optional.of(new Role(Role.READER)));
        given(passwordEncoder.encode("password123")).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        userCreationService.createUser("  Alice  ", "  Alice@Test.com  ", "password123", Role.READER);

        verify(userValidator).validateAndSanitizeName("  Alice  ");
        verify(userValidator).validateAndSanitizeEmail("  Alice@Test.com  ");
    }
}
