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
class UserUpdateServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserValidator userValidator;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserUpdateService userUpdateService;

    @Test
    void updateName_validName_setsUserName() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(userValidator.validateAndSanitizeName("Bob")).willReturn("Bob");

        userUpdateService.updateName(user, "Bob");

        assertThat(user.getName()).isEqualTo("Bob");
    }

    @Test
    void updateEmail_sameEmail_noUniquenessCheckPerformed() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(userValidator.sanitizeEmail("alice@test.com")).willReturn("alice@test.com");

        userUpdateService.updateEmail(user, "alice@test.com");

        verify(userRepository, never()).existsByEmailExcludingId(any(), any());
        assertThat(user.getEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void updateEmail_blankAfterSanitize_throwsIllegalArgumentException() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(userValidator.sanitizeEmail("   ")).willReturn("");

        assertThatThrownBy(() -> userUpdateService.updateEmail(user, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateEmail_duplicateEmail_throwsUserAlreadyExistsException() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(userValidator.sanitizeEmail("bob@test.com")).willReturn("bob@test.com");
        given(userRepository.existsByEmailExcludingId("bob@test.com", 1)).willReturn(true);

        assertThatThrownBy(() -> userUpdateService.updateEmail(user, "bob@test.com"))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void updateEmail_newUniqueEmail_updatesUser() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(userValidator.sanitizeEmail("bob@test.com")).willReturn("bob@test.com");
        given(userRepository.existsByEmailExcludingId("bob@test.com", 1)).willReturn(false);

        userUpdateService.updateEmail(user, "bob@test.com");

        assertThat(user.getEmail()).isEqualTo("bob@test.com");
    }

    @Test
    void updateRole_blankRole_throwsIllegalArgumentException() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));

        assertThatThrownBy(() -> userUpdateService.updateRole(user, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateRole_sameRoleName_skipsRepositoryLookup() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));

        userUpdateService.updateRole(user, Role.READER);

        verify(roleRepository, never()).findByName(any());
        assertThat(user.getRole().getName()).isEqualTo(Role.READER);
        assertThat(user.getTokenVersion()).isEqualTo(0);
    }

    @Test
    void updateRole_unknownRole_throwsResourceNotFoundException() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(roleRepository.findByName("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userUpdateService.updateRole(user, "UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRole_validNewRole_setsRole() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(roleRepository.findByName(Role.AUTHOR)).willReturn(Optional.of(new Role(Role.AUTHOR)));

        userUpdateService.updateRole(user, Role.AUTHOR);

        assertThat(user.getRole().getName()).isEqualTo(Role.AUTHOR);
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void updatePassword_encodesAndBumpsTokenVersion() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(passwordEncoder.encode("newPassword123")).willReturn("encoded-hash");

        userUpdateService.updatePassword(user, "newPassword123");

        assertThat(user.getPassword()).isEqualTo("encoded-hash");
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void performUpdate_allNullFields_returnsUserUnchanged() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));

        User result = userUpdateService.performUpdate(user, null, null, null);

        assertThat(result).isSameAs(user);
        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getEmail()).isEqualTo("alice@test.com");
        assertThat(result.getRole().getName()).isEqualTo(Role.READER);
        verify(userValidator, never()).validateAndSanitizeName(any());
        verify(userValidator, never()).sanitizeEmail(any());
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void performUpdate_partialFields_onlyUpdatesProvidedFields() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(userValidator.validateAndSanitizeName("Bob")).willReturn("Bob");

        User result = userUpdateService.performUpdate(user, "Bob", null, null);

        assertThat(result.getName()).isEqualTo("Bob");
        assertThat(result.getEmail()).isEqualTo("alice@test.com");
        verify(userValidator, never()).sanitizeEmail(any());
        verify(roleRepository, never()).findByName(any());
    }
}
