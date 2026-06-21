package com.blog.blog_literario.controllers.image;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.support.WebMvcTestConfig;

@WebMvcTest(ImageController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class ImageControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private Path uploadDir;
    private Path testFile;

    @BeforeEach
    void setUp() throws IOException {
        uploadDir = Paths.get("test-uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        testFile = uploadDir.resolve("sample.png");
        Files.write(testFile, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(testFile);
    }

    @Test
    void getImage_existingFile_returns200WithContentType() throws Exception {
        mockMvc.perform(get("/api/images/{filename}", "sample.png"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void getImage_nonExistentFile_returns404WithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/images/{filename}", "missing.png"))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.type")
                        .value("https://blog-literario.com/errors/not-found"));
    }

    @Test
    void getImage_pathTraversalAttempt_returns400FromSecurityFirewall() throws Exception {
        // Spring Security's StrictHttpFirewall rejects encoded traversal sequences
        // before the request reaches the controller's own path-containment check.
        mockMvc.perform(get("/api/images/{filename}", "..%2F..%2Fpom.xml"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getImage_noAuthRequired_returns200() throws Exception {
        mockMvc.perform(get("/api/images/{filename}", "sample.png"))
                .andExpect(status().isOk());
    }
}
