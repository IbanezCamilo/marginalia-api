package com.blog.blog_literario.controllers.posts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.posts.MyPostResponse;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.posts.MyPostCommandService;
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

@WebMvcTest(MyPostController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class MyPostControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean MyPostCommandService myService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final MyPostResponse SAMPLE_RESPONSE = new MyPostResponse(
            1, "Test Title", "Content", "DRAFT", "test-title",
            "Test Author", 1, "Fiction", null, null, null,
            // Moderation fields
            null,
            0,
            true,
            false
    );

    @Test
    void list_asAuthor_returns200() throws Exception {
        given(myService.list(eq(42), any(Pageable.class))).willReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/me/posts")
                .with(authentication(TestSecurityFactory.asAuthor(42))))
                .andExpect(status().isOk());
    }

    @Test
    void list_asReader_returns403() throws Exception {
        mockMvc.perform(get("/api/me/posts")
                .with(authentication(TestSecurityFactory.asReader(10))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(get("/api/me/posts"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void create_asAuthor_validBody_delegatesUserIdFromPrincipal() throws Exception {
        given(myService.create(eq(42), any())).willReturn(SAMPLE_RESPONSE);

        mockMvc.perform(post("/api/me/posts")
                .with(authentication(TestSecurityFactory.asAuthor(42)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test Title Post\",\"content\":\"Content\",\"categoryId\":1,\"status\":\"DRAFT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(myService).create(eq(42), any());
    }

    @Test
    void create_asAuthor_blankTitleDraft_returns201() throws Exception {
        // A draft may be saved with no title — only enforced once it's published.
        mockMvc.perform(post("/api/me/posts")
                .with(authentication(TestSecurityFactory.asAuthor(42)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\",\"content\":\"Content\",\"categoryId\":1,\"status\":\"DRAFT\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void create_asAuthor_titleTooLong_returns400() throws Exception {
        String tooLong = "a".repeat(201);
        mockMvc.perform(post("/api/me/posts")
                .with(authentication(TestSecurityFactory.asAuthor(42)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + tooLong + "\",\"content\":\"Content\",\"categoryId\":1,\"status\":\"DRAFT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_asAuthor_nullCategoryIdDraft_returns201() throws Exception {
        // A draft may be saved with no category — only enforced once it's published.
        mockMvc.perform(post("/api/me/posts")
                .with(authentication(TestSecurityFactory.asAuthor(42)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test Title Post\",\"content\":\"Content\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void create_asAuthor_publishedWithoutTitleOrCategory_returns400() throws Exception {
        given(myService.create(eq(42), any()))
                .willThrow(new IllegalArgumentException("El título es obligatorio para publicar."));

        mockMvc.perform(post("/api/me/posts")
                .with(authentication(TestSecurityFactory.asAuthor(42)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Content\",\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchStatus_asAuthor_validStatus_returns200() throws Exception {
        given(myService.updateStatus(eq(42), eq(1), eq("PUBLISHED"))).willReturn(SAMPLE_RESPONSE);

        mockMvc.perform(patch("/api/me/posts/1/status")
                .with(authentication(TestSecurityFactory.asAuthor(42)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void patchStatus_asAuthor_blankStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/me/posts/1/status")
                .with(authentication(TestSecurityFactory.asAuthor(42)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_asAuthor_returns204() throws Exception {
        mockMvc.perform(delete("/api/me/posts/1")
                .with(authentication(TestSecurityFactory.asAuthor(42))))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_asReader_returns403() throws Exception {
        mockMvc.perform(delete("/api/me/posts/1")
                .with(authentication(TestSecurityFactory.asReader(10))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_asModerator_returns201() throws Exception {
        given(myService.create(eq(5), any())).willReturn(SAMPLE_RESPONSE);

        mockMvc.perform(post("/api/me/posts")
                .with(authentication(TestSecurityFactory.asModerator(5)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test Title Post\",\"content\":\"Content\",\"categoryId\":1,\"status\":\"DRAFT\"}"))
                .andExpect(status().isCreated());
    }
}
