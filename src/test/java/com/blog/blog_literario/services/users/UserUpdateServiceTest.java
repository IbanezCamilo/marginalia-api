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
import com.blog.blog_literario.services.admin.AdminActionLogService;
import com.blog.blog_literario.utils.UserValidator;

@ExtendWith(MockitoExtension.class)
class UserUpdateServiceTest {

    private static final Integer ACTOR_ID = 99;

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock UserValidator userValidator;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AdminActionLogService adminActionLogService;

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

        assertThatThrownBy(() -> userUpdateService.updateRole(user, "   ", ACTOR_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateRole_sameRoleName_skipsRepositoryLookup() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));

        userUpdateService.updateRole(user, Role.READER, ACTOR_ID);

        verify(roleRepository, never()).findByName(any());
        verify(adminActionLogService, never()).record(any(), any(), any(), any(), any(), any());
        assertThat(user.getRole().getName()).isEqualTo(Role.READER);
        assertThat(user.getTokenVersion()).isEqualTo(0);
    }

    @Test
    void updateRole_unknownRole_throwsResourceNotFoundException() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(roleRepository.findByName("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userUpdateService.updateRole(user, "UNKNOWN", ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRole_validNewRole_setsRoleAndRecordsAuditLog() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        User actor = new User(ACTOR_ID, "Admin", "admin@test.com", new Role(Role.ADMIN));
        given(roleRepository.findByName(Role.AUTHOR)).willReturn(Optional.of(new Role(Role.AUTHOR)));
        given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(actor));

        userUpdateService.updateRole(user, Role.AUTHOR, ACTOR_ID);

        assertThat(user.getRole().getName()).isEqualTo(Role.AUTHOR);
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(adminActionLogService).record(
                ACTOR_ID, "admin@test.com", "USER_ROLE_CHANGE", "USER", 1,
                Role.READER + " -> " + Role.AUTHOR);
    }

    @Test
    void updateRole_unknownActor_throwsResourceNotFoundException() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        given(roleRepository.findByName(Role.AUTHOR)).willReturn(Optional.of(new Role(Role.AUTHOR)));
        given(userRepository.findById(ACTOR_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userUpdateService.updateRole(user, Role.AUTHOR, ACTOR_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRole_lastRemainingAdmin_throwsIllegalState_andDoesNotChangeRole() {
        User admin = new User(1, "Admin", "admin@test.com", new Role(Role.ADMIN));
        given(userRepository.countByRoleName(Role.ADMIN)).willReturn(1L);

        assertThatThrownBy(() -> userUpdateService.updateRole(admin, Role.AUTHOR, ACTOR_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(admin.getRole().getName()).isEqualTo(Role.ADMIN);
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void updateRole_targetIsOwner_throwsIllegalState_andDoesNotChangeRole() {
        User owner = new User(1, "Owner", "owner@test.com", new Role(Role.OWNER));

        assertThatThrownBy(() -> userUpdateService.updateRole(owner, Role.ADMIN, ACTOR_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(owner.getRole().getName()).isEqualTo(Role.OWNER);
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void updateRole_newRoleIsOwner_throwsIllegalState_andDoesNotChangeRole() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.ADMIN));

        assertThatThrownBy(() -> userUpdateService.updateRole(user, Role.OWNER, ACTOR_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(user.getRole().getName()).isEqualTo(Role.ADMIN);
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void updateRole_newRoleIsOwnerCaseInsensitive_throwsIllegalState() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));

        assertThatThrownBy(() -> userUpdateService.updateRole(user, "owner", ACTOR_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(user.getRole().getName()).isEqualTo(Role.READER);
    }

    @Test
    void updateRole_notLastAdmin_demotesSuccessfully() {
        User admin = new User(1, "Admin", "admin@test.com", new Role(Role.ADMIN));
        User actor = new User(ACTOR_ID, "OtherAdmin", "other-admin@test.com", new Role(Role.ADMIN));
        given(userRepository.countByRoleName(Role.ADMIN)).willReturn(2L);
        given(roleRepository.findByName(Role.AUTHOR)).willReturn(Optional.of(new Role(Role.AUTHOR)));
        given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(actor));

        userUpdateService.updateRole(admin, Role.AUTHOR, ACTOR_ID);

        assertThat(admin.getRole().getName()).isEqualTo(Role.AUTHOR);
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
    void updatePassword_samePasswordAsCurrent_doesNotThrow() {
        // Deliberately permissive: this is the shared method behind the admin-reset
        // path too, where there's no plaintext current password to compare against.
        // The "new != current" UX guard lives only in the self-service caller
        // (UserProfileService.changePassword()).
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setPassword("current-hash");
        given(passwordEncoder.encode("samePassword123")).willReturn("current-hash");

        userUpdateService.updatePassword(user, "samePassword123");

        assertThat(user.getPassword()).isEqualTo("current-hash");
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void performUpdate_allNullFields_returnsUserUnchanged() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));

        User result = userUpdateService.performUpdate(user, null, null, null, ACTOR_ID);

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

        User result = userUpdateService.performUpdate(user, "Bob", null, null, ACTOR_ID);

        assertThat(result.getName()).isEqualTo("Bob");
        assertThat(result.getEmail()).isEqualTo("alice@test.com");
        verify(userValidator, never()).sanitizeEmail(any());
        verify(roleRepository, never()).findByName(any());
    }
}
