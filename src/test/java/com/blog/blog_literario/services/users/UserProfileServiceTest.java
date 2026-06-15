package com.blog.blog_literario.services.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.blog.blog_literario.dto.users.UserProfileResponse;
import com.blog.blog_literario.dto.users.UserProfileUpdateRequest;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.security.UserDetailsImpl;
import com.blog.blog_literario.services.images.AvatarResolver;
import com.blog.blog_literario.services.images.StorageService;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock StorageService storageService;
    @Mock AvatarResolver avatarResolver;

    @InjectMocks UserProfileService userProfileService;

    @Test
    void getUserProfile_existingUser_returnsProfileWithAvatar() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setDescription("Bio");
        user.setProfilePicture("avatar.jpg");
        UserDetails userDetails = new UserDetailsImpl(user);

        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));
        given(avatarResolver.resolve("avatar.jpg", "Alice")).willReturn("https://avatar-url");

        UserProfileResponse result = userProfileService.getUserProfile(userDetails);

        assertThat(result.id()).isEqualTo(1);
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(result.description()).isEqualTo("Bio");
        assertThat(result.profilePicture()).isEqualTo("https://avatar-url");
        assertThat(result.role()).isEqualTo(Role.READER);
    }

    @Test
    void getUserProfile_userNotFound_throwsUsernameNotFoundException() {
        User user = new User(1, "Ghost", "ghost@test.com", new Role(Role.READER));
        UserDetails userDetails = new UserDetailsImpl(user);

        given(userRepository.findByEmail("ghost@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getUserProfile(userDetails))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void updateProfile_validRequest_updatesNameAndDescriptionAndSaves() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        UserDetails userDetails = new UserDetailsImpl(user);
        UserProfileUpdateRequest request = new UserProfileUpdateRequest("Bob", "New bio");

        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));
        given(userRepository.save(user)).willReturn(user);

        UserProfileResponse result = userProfileService.updateProfile(userDetails, request);

        assertThat(user.getName()).isEqualTo("Bob");
        assertThat(user.getDescription()).isEqualTo("New bio");
        assertThat(result.name()).isEqualTo("Bob");
        assertThat(result.description()).isEqualTo("New bio");
    }

    @Test
    void uploadProfileImage_validFile_savesAndReturnsAbsoluteUrl() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setProfilePicture("old.jpg");
        UserDetails userDetails = new UserDetailsImpl(user);
        MockMultipartFile file = new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});

        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));
        given(storageService.save(file, "old.jpg")).willReturn("new.jpg");
        given(storageService.buildUrl("new.jpg")).willReturn("https://example.com/images/new.jpg");

        String result = userProfileService.uploadProfileImage(userDetails, file);

        assertThat(result).isEqualTo("https://example.com/images/new.jpg");
        assertThat(user.getProfilePicture()).isEqualTo("new.jpg");
        verify(userRepository).save(user);
    }

    @Test
    void deleteProfileImage_existingImage_deletesFromStorageAndReturnsFallbackAvatar() {
        User user = new User(1, "Alice", "alice@test.com", new Role(Role.READER));
        user.setProfilePicture("avatar.jpg");
        UserDetails userDetails = new UserDetailsImpl(user);

        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(user));
        given(avatarResolver.resolve(null, "Alice")).willReturn("https://ui-avatars.com/fallback");

        String result = userProfileService.deleteProfileImage(userDetails);

        verify(storageService).delete("avatar.jpg");
        assertThat(user.getProfilePicture()).isEqualTo("");
        assertThat(result).isEqualTo("https://ui-avatars.com/fallback");
        verify(userRepository).save(user);
    }

    @Test
    void getValidPhoto_staticHelper_returnsPhotoOrDefault() {
        assertThat(UserProfileService.getValidPhoto("photo.jpg", "default.jpg")).isEqualTo("photo.jpg");
        assertThat(UserProfileService.getValidPhoto(null, "default.jpg")).isEqualTo("default.jpg");
        assertThat(UserProfileService.getValidPhoto("", "default.jpg")).isEqualTo("default.jpg");
        assertThat(UserProfileService.getValidPhoto("default.jpg", "default.jpg")).isEqualTo("default.jpg");
    }
}
