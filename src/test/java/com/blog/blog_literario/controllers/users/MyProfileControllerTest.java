package com.blog.blog_literario.controllers.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.springframework.security.authentication.BadCredentialsException;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.users.UserProfileResponse;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.users.UserProfileService;
import com.blog.blog_literario.support.WebMvcTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MyProfileController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class MyProfileControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserProfileService userProfileService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    void uploadImage_serviceThrows_returns500WithGenericMessage() throws Exception {
        given(userProfileService.uploadProfileImage(any(), any()))
                .willThrow(new RuntimeException("disk full: /var/data/images"));

        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/me/profile/image")
                .file(imageFile)
                .with(user("user@test.com").roles("READER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Ha ocurrido un error inesperado. Inténtalo de nuevo."))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("disk full"))));
    }

    @Test
    void uploadImage_emptyFile_returns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/me/profile/image")
                .file(emptyFile)
                .with(user("user@test.com").roles("READER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImage_nonImageContentType_returns400() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "image", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/me/profile/image")
                .file(pdfFile)
                .with(user("user@test.com").roles("READER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getProfile_authenticatedUser_returns200WithProfileData() throws Exception {
        given(userProfileService.getUserProfile(any())).willReturn(new UserProfileResponse(
                1, "Alice", "user@test.com", "Bio", "https://avatar-url", "READER", LocalDateTime.now()));

        mockMvc.perform(get("/api/me/profile")
                .with(user("user@test.com").roles("READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    void updateProfile_validRequest_returns200() throws Exception {
        given(userProfileService.updateProfile(any(), any())).willReturn(new UserProfileResponse(
                1, "Bob", "user@test.com", "New bio", "https://avatar-url", "READER", LocalDateTime.now()));

        mockMvc.perform(put("/api/me/profile")
                .with(user("user@test.com").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bob\",\"description\":\"New bio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob"));
    }

    @Test
    void updateProfile_blankName_returns400() throws Exception {
        mockMvc.perform(put("/api/me/profile")
                .with(user("user@test.com").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"description\":\"Bio\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/validation"));
    }

    @Test
    void updateProfile_nameTooShort_returns400() throws Exception {
        mockMvc.perform(put("/api/me/profile")
                .with(user("user@test.com").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"description\":\"Bio\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/validation"));
    }

    @Test
    void updateProfile_descriptionTooLong_returns400() throws Exception {
        String longDescription = "a".repeat(501);

        mockMvc.perform(put("/api/me/profile")
                .with(user("user@test.com").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alice\",\"description\":\"" + longDescription + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/validation"));
    }

    @Test
    void deleteProfileImage_returns200WithImageUrl() throws Exception {
        given(userProfileService.deleteProfileImage(any())).willReturn("https://ui-avatars.com/fallback");

        mockMvc.perform(delete("/api/me/profile/image")
                .with(user("user@test.com").roles("READER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://ui-avatars.com/fallback"));
    }

    @Test
    void uploadImage_validImage_returns201WithImageUrl() throws Exception {
        byte[] jpegBytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", jpegBytes);

        given(userProfileService.uploadProfileImage(any(), any())).willReturn("https://example.com/images/new.jpg");

        mockMvc.perform(multipart("/api/me/profile/image")
                .file(imageFile)
                .with(user("user@test.com").roles("READER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/images/new.jpg"));
    }

    @Test
    void getProfile_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(get("/api/me/profile"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void changeEmail_validRequest_returns202AndDelegates() throws Exception {
        mockMvc.perform(put("/api/me/profile/email")
                .with(user("user@test.com").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"new@test.com\",\"currentPassword\":\"secret123\"}"))
                .andExpect(status().isAccepted());

        verify(userProfileService).requestEmailChange(any(), any());
    }

    @Test
    void changeEmail_invalidEmail_returns400() throws Exception {
        mockMvc.perform(put("/api/me/profile/email")
                .with(user("user@test.com").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"not-an-email\",\"currentPassword\":\"secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/validation"));

        verify(userProfileService, never()).requestEmailChange(any(), any());
    }

    @Test
    void changeEmail_blankPassword_returns400() throws Exception {
        mockMvc.perform(put("/api/me/profile/email")
                .with(user("user@test.com").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"new@test.com\",\"currentPassword\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(userProfileService, never()).requestEmailChange(any(), any());
    }

    @Test
    void changeEmail_wrongCurrentPassword_returns401() throws Exception {
        willThrow(new BadCredentialsException("La contraseña actual es incorrecta"))
                .given(userProfileService).requestEmailChange(any(), any());

        mockMvc.perform(put("/api/me/profile/email")
                .with(user("user@test.com").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"new@test.com\",\"currentPassword\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeEmail_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(put("/api/me/profile/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newEmail\":\"new@test.com\",\"currentPassword\":\"secret123\"}"))
                .andExpect(status().is4xxClientError());
    }
}
