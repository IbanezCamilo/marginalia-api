package com.blog.blog_literario.controllers.posts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.posts.PublicPostResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.posts.PublicPostQueryService;
import com.blog.blog_literario.support.WebMvcTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicPostController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class PublicPostControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean PublicPostQueryService publicPostQueryService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final PublicPostResponse SAMPLE_POST = new PublicPostResponse(
            "Spring Boot Guide", "Content here", "spring-boot-guide",
            1, "Alice", "Author bio", null,
            "Technology", "technology", null, LocalDateTime.now());

    @Test
    void list_noAuth_returns200WithPage() throws Exception {
        given(publicPostQueryService.listPublishedPosts(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(SAMPLE_POST)));

        mockMvc.perform(get("/api/public/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].slug").value("spring-boot-guide"));
    }

    @Test
    void list_withCategoryId_delegatesFilterToService() throws Exception {
        given(publicPostQueryService.listPublishedPosts(eq(5), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/public/posts?categoryId=5"))
                .andExpect(status().isOk());

        verify(publicPostQueryService).listPublishedPosts(eq(5), any(Pageable.class));
    }

    @Test
    void getBySlug_existingSlug_returns200() throws Exception {
        given(publicPostQueryService.getBySlug("spring-boot-guide")).willReturn(SAMPLE_POST);

        mockMvc.perform(get("/api/public/posts/spring-boot-guide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("spring-boot-guide"))
                .andExpect(jsonPath("$.title").value("Spring Boot Guide"));
    }

    @Test
    void getBySlug_nonExistentSlug_returns404WithProblemDetail() throws Exception {
        given(publicPostQueryService.getBySlug("missing-slug"))
                .willThrow(new ResourceNotFoundException("Post not found: missing-slug"));

        mockMvc.perform(get("/api/public/posts/missing-slug"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
