package com.blog.blog_literario.controllers.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.dto.users.PublicAuthorResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.users.PublicAuthorService;
import com.blog.blog_literario.support.WebMvcTestConfig;

@WebMvcTest(PublicAuthorController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class PublicAuthorControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean PublicAuthorService publicAuthorService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final PublicAuthorResponse SAMPLE_AUTHOR =
            new PublicAuthorResponse(1, "Alice", "Bio", "https://avatar-url");

    private static final PublicPostResponse SAMPLE_POST = new PublicPostResponse(
            "My Post", "Content", "my-post",
            1, "Alice", "Bio", "https://avatar-url",
            "Fiction", "fiction", null,
            new java.math.BigDecimal("0.25"), new java.math.BigDecimal("0.75"),
            LocalDateTime.now());

    @Test
    void getAuthor_existingId_returns200() throws Exception {
        given(publicAuthorService.getAuthorById(1)).willReturn(SAMPLE_AUTHOR);

        mockMvc.perform(get("/api/public/authors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void getAuthor_nonExistentId_returns404WithProblemDetail() throws Exception {
        given(publicAuthorService.getAuthorById(99))
                .willThrow(new ResourceNotFoundException("Author not found with ID: 99"));

        mockMvc.perform(get("/api/public/authors/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"));
    }

    @Test
    void getAuthorPosts_existingAuthor_returns200WithPage() throws Exception {
        given(publicAuthorService.getPublishedPostsByAuthor(eq(1), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(SAMPLE_POST)));

        mockMvc.perform(get("/api/public/authors/1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].slug").value("my-post"));
    }

    @Test
    void getAuthorPosts_nonExistentAuthor_returns404WithProblemDetail() throws Exception {
        given(publicAuthorService.getPublishedPostsByAuthor(eq(99), any(Pageable.class)))
                .willThrow(new ResourceNotFoundException("Author not found with ID: 99"));

        mockMvc.perform(get("/api/public/authors/99/posts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"));
    }

    @Test
    void getAuthor_noAuthRequired_returns200() throws Exception {
        given(publicAuthorService.getAuthorById(1)).willReturn(SAMPLE_AUTHOR);

        mockMvc.perform(get("/api/public/authors/1"))
                .andExpect(status().isOk());
    }
}
