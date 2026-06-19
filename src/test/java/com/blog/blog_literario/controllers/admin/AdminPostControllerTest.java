package com.blog.blog_literario.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.admin.AdminPostResponse;
import com.blog.blog_literario.dto.admin.AdminStatusUpdateRequest;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.admin.AdminPostModerationService;
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

@WebMvcTest(AdminPostController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class AdminPostControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AdminPostModerationService adminService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final AdminPostResponse SAMPLE = new AdminPostResponse(
            1,
            "Title",
            "title-slug",
            "PUBLISHED",
            "Publicado",
            "Author",
            "author@test.com",
            "Fiction",
            null,
            null,
            null,
            LocalDateTime.now(),
            0,
            false,
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    @Test
    void listAll_asAdmin_returns200() throws Exception {
        given(adminService.listAll(any(), any(Pageable.class))).willReturn(new PageImpl<>(List.of(SAMPLE)));

        mockMvc.perform(get("/api/admin/posts").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void listAll_asAuthor_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/posts").with(user("author").roles("AUTHOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAll_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(get("/api/admin/posts"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listAll_invalidStatusFilter_returns400WithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/admin/posts?status=BOGUS").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/bad-request"));
    }

    @Test
    void updateStatus_asAdmin_validStatus_returns200() throws Exception {
        given(adminService.updateStatus(eq(1), eq(1), any(AdminStatusUpdateRequest.class))).willReturn(SAMPLE);

        mockMvc.perform(put("/api/admin/posts/1/status")
                .with(authentication(TestSecurityFactory.asAdmin(1)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());

        verify(adminService).updateStatus(eq(1), eq(1), any(AdminStatusUpdateRequest.class));
    }

    @Test
    void updateStatus_asAdmin_blankStatus_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/posts/1/status")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetPost_asAdmin_withNote_returns200() throws Exception {
        given(adminService.resetPost(eq(1), eq(1), eq("Unlocked, try again"))).willReturn(SAMPLE);

        mockMvc.perform(put("/api/admin/posts/1/reset")
                .with(authentication(TestSecurityFactory.asAdmin(1)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"moderationNote\":\"Unlocked, try again\"}"))
                .andExpect(status().isOk());

        verify(adminService).resetPost(eq(1), eq(1), eq("Unlocked, try again"));
    }

    @Test
    void resetPost_asAdmin_noBody_returns200() throws Exception {
        given(adminService.resetPost(eq(1), eq(1), isNull())).willReturn(SAMPLE);

        mockMvc.perform(put("/api/admin/posts/1/reset")
                .with(authentication(TestSecurityFactory.asAdmin(1))))
                .andExpect(status().isOk());

        verify(adminService).resetPost(eq(1), eq(1), isNull());
    }

    @Test
    void resetPost_asAuthor_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/posts/1/reset")
                .with(user("author").roles("AUTHOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/posts/1").with(authentication(TestSecurityFactory.asAdmin(1))))
                .andExpect(status().isNoContent());

        verify(adminService).delete(eq(1), eq(1));
    }

    @Test
    void delete_asAuthor_returns403() throws Exception {
        mockMvc.perform(delete("/api/admin/posts/1").with(user("author").roles("AUTHOR")))
                .andExpect(status().isForbidden());
    }
}
