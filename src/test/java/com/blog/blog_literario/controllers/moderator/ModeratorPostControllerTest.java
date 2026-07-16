package com.blog.blog_literario.controllers.moderator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.moderator.ModeratorFeaturedUpdateRequest;
import com.blog.blog_literario.dto.moderator.ModeratorPostResponse;
import com.blog.blog_literario.dto.moderator.ModeratorStatusUpdateRequest;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.moderator.ModeratorPostService;
import com.blog.blog_literario.support.TestSecurityFactory;
import com.blog.blog_literario.support.WebMvcTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ModeratorPostController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class ModeratorPostControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ModeratorPostService moderatorService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final ModeratorPostResponse SAMPLE = new ModeratorPostResponse(
            1,
            "Title",
            "title-slug",
            "PUBLISHED",
            "Publicado",
            2,
            "Author",
            "Fiction",
            null,
            null,
            null,
            null,
            0,
            false,
            false,
            LocalDateTime.now(),
            LocalDateTime.now(),
            false
    );

    @Test
    void listAll_asModerator_returns200() throws Exception {
        given(moderatorService.listAll(any(), any(Pageable.class))).willReturn(new PageImpl<>(List.of(SAMPLE)));

        mockMvc.perform(get("/api/moderator/posts")
                .with(authentication(TestSecurityFactory.asModerator(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listAll_asAdmin_returns200() throws Exception {
        given(moderatorService.listAll(any(), any(Pageable.class))).willReturn(new PageImpl<>(List.of(SAMPLE)));

        mockMvc.perform(get("/api/moderator/posts")
                .with(authentication(TestSecurityFactory.asAdmin(1))))
                .andExpect(status().isOk());
    }

    @Test
    void listAll_asAuthor_returns403() throws Exception {
        mockMvc.perform(get("/api/moderator/posts")
                .with(authentication(TestSecurityFactory.asAuthor(1))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAll_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(get("/api/moderator/posts"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listAll_invalidStatusFilter_returns400WithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/moderator/posts?status=BOGUS")
                .with(authentication(TestSecurityFactory.asModerator(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/bad-request"));
    }

    @Test
    void updateStatus_asModerator_validStatus_returns200() throws Exception {
        given(moderatorService.updateStatus(eq(7), eq(1), any(ModeratorStatusUpdateRequest.class))).willReturn(SAMPLE);

        mockMvc.perform(put("/api/moderator/posts/1/status")
                .with(authentication(TestSecurityFactory.asModerator(7)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());

        verify(moderatorService).updateStatus(eq(7), eq(1), any(ModeratorStatusUpdateRequest.class));
    }

    @Test
    void updateStatus_asModerator_blankStatus_returns400() throws Exception {
        mockMvc.perform(put("/api/moderator/posts/1/status")
                .with(authentication(TestSecurityFactory.asModerator(7)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_asAuthor_returns403() throws Exception {
        mockMvc.perform(put("/api/moderator/posts/1/status")
                .with(authentication(TestSecurityFactory.asAuthor(1)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateFeatured_asModerator_returns200() throws Exception {
        given(moderatorService.updateFeatured(eq(7), eq(1), any(ModeratorFeaturedUpdateRequest.class)))
                .willReturn(SAMPLE);

        mockMvc.perform(put("/api/moderator/posts/1/featured")
                .with(authentication(TestSecurityFactory.asModerator(7)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featured\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featured").value(false));

        verify(moderatorService).updateFeatured(eq(7), eq(1), any(ModeratorFeaturedUpdateRequest.class));
    }

    @Test
    void updateFeatured_asAdmin_returns200() throws Exception {
        given(moderatorService.updateFeatured(eq(3), eq(1), any(ModeratorFeaturedUpdateRequest.class)))
                .willReturn(SAMPLE);

        mockMvc.perform(put("/api/moderator/posts/1/featured")
                .with(authentication(TestSecurityFactory.asAdmin(3)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featured\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateFeatured_asAuthor_returns403() throws Exception {
        mockMvc.perform(put("/api/moderator/posts/1/featured")
                .with(authentication(TestSecurityFactory.asAuthor(1)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featured\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateFeatured_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(put("/api/moderator/posts/1/featured")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featured\":true}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void updateFeatured_nullFeatured_returns400() throws Exception {
        mockMvc.perform(put("/api/moderator/posts/1/featured")
                .with(authentication(TestSecurityFactory.asModerator(7)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"featured\":null}"))
                .andExpect(status().isBadRequest());
    }
}
