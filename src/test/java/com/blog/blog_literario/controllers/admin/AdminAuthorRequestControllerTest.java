package com.blog.blog_literario.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.authorrequest.AuthorRequestResponse;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.authorrequest.AuthorRequestService;
import com.blog.blog_literario.support.TestSecurityFactory;
import com.blog.blog_literario.support.WebMvcTestConfig;

@WebMvcTest(AdminAuthorRequestController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class AdminAuthorRequestControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AuthorRequestService authorRequestService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final AuthorRequestResponse SAMPLE = new AuthorRequestResponse(
            1, 2, "Alice", "alice@test.com",
            "I want to write", "PENDING", "Pendiente",
            null, null, null, LocalDateTime.now()
    );

    @Test
    void listAll_asAdmin_returns200() throws Exception {
        given(authorRequestService.listAll(any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(SAMPLE)));

        mockMvc.perform(get("/api/admin/author-requests").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void listAll_withStatusFilter_passesStatusToService() throws Exception {
        given(authorRequestService.listAll(eq("PENDING"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(SAMPLE)));

        mockMvc.perform(get("/api/admin/author-requests?status=PENDING").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(authorRequestService).listAll(eq("PENDING"), any(Pageable.class));
    }

    @Test
    void listAll_invalidStatusFilter_returns400WithProblemDetail() throws Exception {
        given(authorRequestService.listAll(eq("BOGUS"), any(Pageable.class)))
                .willThrow(new IllegalArgumentException("Invalid status: 'BOGUS'. Accepted values: PENDING, APPROVED, REJECTED"));

        mockMvc.perform(get("/api/admin/author-requests?status=BOGUS").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/bad-request"));
    }

    @Test
    void listAll_asReader_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/author-requests").with(user("reader").roles("READER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingCount_asAdmin_returns200WithLongBody() throws Exception {
        given(authorRequestService.countPending()).willReturn(5L);

        mockMvc.perform(get("/api/admin/author-requests/pending-count").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(5));
    }

    @Test
    void approve_asAdmin_returns200() throws Exception {
        given(authorRequestService.approve(eq(1), eq(1), any())).willReturn(SAMPLE);

        mockMvc.perform(put("/api/admin/author-requests/1/approve")
                .with(authentication(TestSecurityFactory.asAdmin(1)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"adminNote\":\"Welcome aboard\"}"))
                .andExpect(status().isOk());

        verify(authorRequestService).approve(eq(1), eq(1), eq("Welcome aboard"));
    }

    @Test
    void approve_nonExistentRequest_returns404WithProblemDetail() throws Exception {
        given(authorRequestService.approve(eq(99), eq(1), any()))
                .willThrow(new com.blog.blog_literario.exception.ResourceNotFoundException("Author request not found with ID: 99"));

        mockMvc.perform(put("/api/admin/author-requests/99/approve")
                .with(authentication(TestSecurityFactory.asAdmin(1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"));
    }

    @Test
    void approve_nonPendingRequest_returns409WithProblemDetail() throws Exception {
        given(authorRequestService.approve(eq(1), eq(1), any()))
                .willThrow(new IllegalStateException("Request 1 has already been resolved and cannot be modified."));

        mockMvc.perform(put("/api/admin/author-requests/1/approve")
                .with(authentication(TestSecurityFactory.asAdmin(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/conflict"));
    }

    @Test
    void reject_asAdmin_returns200() throws Exception {
        given(authorRequestService.reject(eq(1), eq(1), any())).willReturn(SAMPLE);

        mockMvc.perform(put("/api/admin/author-requests/1/reject")
                .with(authentication(TestSecurityFactory.asAdmin(1))))
                .andExpect(status().isOk());

        verify(authorRequestService).reject(eq(1), eq(1), eq(null));
    }

    @Test
    void reject_asAuthor_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/author-requests/1/reject")
                .with(user("author").roles("AUTHOR")))
                .andExpect(status().isForbidden());
    }
}
