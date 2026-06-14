package com.blog.blog_literario.controllers.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blog.blog_literario.config.SecurityConfig;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MyProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
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
}
