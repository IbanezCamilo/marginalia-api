package com.blog.blog_literario.services.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.blog.blog_literario.dto.admin.AdminResetPasswordRequest;
import com.blog.blog_literario.dto.users.CreateUserRequest;
import com.blog.blog_literario.dto.users.UpdateUserRequest;
import com.blog.blog_literario.dto.users.UserResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.AuthorRequestRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.RefreshTokenRepository;
import com.blog.blog_literario.repositories.RoleRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.StorageService;
import com.blog.blog_literario.services.users.UserCreationService;
import com.blog.blog_literario.services.users.UserUpdateService;
import com.blog.blog_literario.utils.UserValidator;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PostRepository postRepository;
    @Mock AuthorRequestRepository authorRequestRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserCreationService userCreationService;
    @Mock UserUpdateService userUpdateService;
    @Mock UserValidator userValidator;
    @Mock StorageService storageService;
    @Mock AdminActionLogService adminActionLogService;

    @InjectMocks AdminUserService adminUserService;

    private final Pageable pageable = PageRequest.of(0, 10);
    private final User admin = new User(2, "Admin", "admin@test.com", new Role(2, Role.ADMIN));

    @Test
    void getAllUsers_returnsMappedPage() {
        User user = new User(1, "Alice", "alice@test.com", new Role(1, Role.READER));
        given(userRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<UserResponse> result = adminUserService.getAllUsers(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(1);
        assertThat(result.getContent().get(0).role().name()).isEqualTo(Role.READER);
    }

    @Test
    void searchUsers_blankSearchTerm_delegatesToGetAllUsers() {
        given(userValidator.sanitizeInput("  ")).willReturn("");
        given(userRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(), pageable, 0));

        adminUserService.searchUsers("  ", pageable);

        verify(userRepository).findAll(pageable);
        verify(userRepository, never()).searchByNameOrEmail(any(), any());
    }

    @Test
    void searchUsers_nonBlankTerm_callsSearchByNameOrEmail() {
        User user = new User(1, "Alice", "alice@test.com", new Role(1, Role.READER));
        given(userValidator.sanitizeInput("ali")).willReturn("ali");
        given(userRepository.searchByNameOrEmail("ali", pageable)).willReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<UserResponse> result = adminUserService.searchUsers("ali", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository, never()).findAll(pageable);
    }

    @Test
    void getUsersByRole_unknownRole_throwsResourceNotFoundException() {
        given(roleRepository.findByName("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUsersByRole("UNKNOWN", pageable))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getUsersByRole_validRole_returnsMappedPage() {
        User user = new User(1, "Alice", "alice@test.com", new Role(1, Role.AUTHOR));
        given(roleRepository.findByName(Role.AUTHOR)).willReturn(Optional.of(new Role(Role.AUTHOR)));
        given(userRepository.findByRoleName(Role.AUTHOR, pageable)).willReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<UserResponse> result = adminUserService.getUsersByRole(Role.AUTHOR, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).role().name()).isEqualTo(Role.AUTHOR);
    }

    @Test
    void getUserById_existing_returnsUserResponse() {
        User user = new User(1, "Alice", "alice@test.com", new Role(1, Role.READER));
        given(userRepository.findById(1)).willReturn(Optional.of(user));

        UserResponse result = adminUserService.getUserById(1);

        assertThat(result.id()).isEqualTo(1);
        assertThat(result.email()).isEqualTo("alice@test.com");
    }

    @Test
    void getUserById_nonExistent_throwsResourceNotFoundException() {
        given(userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUserById(99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createUser_delegatesToUserCreationService_andRecordsAuditLog() {
        CreateUserRequest request = new CreateUserRequest("Alice", "alice@test.com", "password123", Role.READER);
        User createdUser = new User(1, "Alice", "alice@test.com", new Role(1, Role.READER));
        given(userCreationService.createUser("Alice", "alice@test.com", "password123", Role.READER))
                .willReturn(createdUser);
        given(userRepository.findById(2)).willReturn(Optional.of(admin));

        UserResponse result = adminUserService.createUser(2, request);

        assertThat(result.id()).isEqualTo(1);
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.role().name()).isEqualTo(Role.READER);
        verify(adminActionLogService).record(2, admin.getEmail(), "USER_CREATE", "USER", 1,
                "email=alice@test.com, role=" + Role.READER);
    }

    @Test
    void update_existingUser_delegatesToUserUpdateServiceAndSaves() {
        User existingUser = new User(1, "Alice", "alice@test.com", new Role(1, Role.READER));
        UpdateUserRequest request = new UpdateUserRequest("Bob", null, null);
        given(userRepository.findById(1)).willReturn(Optional.of(existingUser));
        given(userRepository.save(existingUser)).willReturn(existingUser);

        UserResponse result = adminUserService.update(2, 1, request);

        verify(userUpdateService).performUpdate(existingUser, "Bob", null, null, 2);
        verify(userRepository).save(existingUser);
        assertThat(result.id()).isEqualTo(1);
        verify(adminActionLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_roleChanged_passesActorIdToPerformUpdate() {
        // Audit logging for role changes is UserUpdateService.updateRole()'s
        // responsibility now (see UserUpdateServiceTest) — this only verifies
        // AdminUserService threads the acting admin's ID through correctly.
        User existingUser = new User(1, "Alice", "alice@test.com", new Role(1, Role.READER));
        UpdateUserRequest request = new UpdateUserRequest(null, null, Role.AUTHOR);
        given(userRepository.findById(1)).willReturn(Optional.of(existingUser));
        given(userRepository.save(existingUser)).willReturn(existingUser);

        adminUserService.update(2, 1, request);

        verify(userUpdateService).performUpdate(existingUser, null, null, Role.AUTHOR, 2);
    }

    @Test
    void update_nonExistentUser_throwsResourceNotFoundException() {
        UpdateUserRequest request = new UpdateUserRequest("Bob", null, null);
        given(userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.update(2, 99, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userUpdateService, never()).performUpdate(any(), any(), any(), any(), any());
    }

    @Test
    void deleteUser_existingUser_deletesPostsImagesProfileImageAndUser() {
        User user = new User(1, "Alice", "alice@test.com", new Role(1, Role.READER));
        user.setProfilePicture("profile.jpg");

        Post post1 = new Post();
        post1.setCoverImage("cover1.jpg");
        Post post2 = new Post();
        post2.setCoverImage("cover2.jpg");

        given(userRepository.findById(1)).willReturn(Optional.of(user));
        given(userRepository.findById(2)).willReturn(Optional.of(admin));
        given(postRepository.findAllByAuthorId(1)).willReturn(List.of(post1, post2));

        adminUserService.deleteUser(2, 1);

        verify(storageService).delete("cover1.jpg");
        verify(storageService).delete("cover2.jpg");
        verify(storageService).delete("profile.jpg");
        verify(postRepository).clearModeratedByForUser(1);
        verify(authorRequestRepository).clearResolvedByForUser(1);
        verify(refreshTokenRepository).deleteByUser(user);

        InOrder order = inOrder(postRepository, refreshTokenRepository, userRepository);
        order.verify(postRepository).deleteAllByAuthorId(1);
        order.verify(refreshTokenRepository).deleteByUser(user);
        order.verify(userRepository).deleteById(1);

        verify(adminActionLogService).record(2, admin.getEmail(), "USER_DELETE", "USER", 1,
                "email=alice@test.com, role=" + Role.READER);
    }

    @Test
    void deleteUser_nonExistentUser_throwsResourceNotFoundException() {
        given(userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.deleteUser(2, 99))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(postRepository, never()).findAllByAuthorId(any());
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_lastRemainingAdmin_throwsIllegalState_andDoesNotDelete() {
        User lastAdmin = new User(1, "Admin", "admin1@test.com", new Role(1, Role.ADMIN));
        given(userRepository.findById(1)).willReturn(Optional.of(lastAdmin));
        given(userRepository.countByRoleName(Role.ADMIN)).willReturn(1L);

        assertThatThrownBy(() -> adminUserService.deleteUser(2, 1))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).deleteById(any());
        verify(adminActionLogService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resetPassword_existingUser_updatesPasswordAndRecordsAuditLog() {
        User user = new User(1, "Alice", "alice@test.com", new Role(1, Role.READER));
        AdminResetPasswordRequest request = new AdminResetPasswordRequest("newPassword123");
        given(userRepository.findById(1)).willReturn(Optional.of(user));
        given(userRepository.findById(2)).willReturn(Optional.of(admin));

        adminUserService.resetPassword(2, 1, request);

        verify(userUpdateService).updatePassword(user, "newPassword123");
        verify(userRepository).save(user);
        verify(adminActionLogService).record(2, admin.getEmail(), "USER_PASSWORD_RESET", "USER", 1,
                "email=alice@test.com");
    }

    @Test
    void resetPassword_nonExistentUser_throwsResourceNotFoundException() {
        AdminResetPasswordRequest request = new AdminResetPasswordRequest("newPassword123");
        given(userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.resetPassword(2, 99, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userUpdateService, never()).updatePassword(any(), any());
    }

    @Test
    void countUsers_returnsRepositoryCount() {
        given(userRepository.count()).willReturn(42L);

        assertThat(adminUserService.countUsers()).isEqualTo(42L);
    }

    @Test
    void countUserPosts_returnsRepositoryCount() {
        given(userRepository.countPostsByAuthor(1)).willReturn(5L);

        assertThat(adminUserService.countUserPosts(1)).isEqualTo(5L);
    }
}
