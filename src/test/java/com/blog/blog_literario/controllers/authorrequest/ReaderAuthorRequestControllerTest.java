package com.blog.blog_literario.controllers.authorrequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.authorrequest.AuthorRequestService;
import com.blog.blog_literario.support.TestSecurityFactory;
import com.blog.blog_literario.support.WebMvcTestConfig;

@WebMvcTest(ReaderAuthorRequestController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class ReaderAuthorRequestControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AuthorRequestService authorRequestService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final AuthorRequestResponse SAMPLE = new AuthorRequestResponse(
            1, 2, "Bob", "bob@test.com",
            "I want to write", "PENDING", "Pendiente",
            null, null, null, LocalDateTime.now()
    );

    @Test
    void submitRequest_asReader_returns201() throws Exception {
        given(authorRequestService.createRequest(eq(2), eq("I want to write"))).willReturn(SAMPLE);

        mockMvc.perform(post("/api/me/author-request")
                .with(authentication(TestSecurityFactory.asReader(2)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivation\":\"I want to write\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void submitRequest_noBody_returns201() throws Exception {
        given(authorRequestService.createRequest(eq(2), eq(null))).willReturn(SAMPLE);

        mockMvc.perform(post("/api/me/author-request")
                .with(authentication(TestSecurityFactory.asReader(2))))
                .andExpect(status().isCreated());
    }

    @Test
    void submitRequest_alreadyPending_returns409WithProblemDetail() throws Exception {
        given(authorRequestService.createRequest(eq(2), any()))
                .willThrow(new IllegalStateException("You already have a pending request and cannot submit another one."));

        mockMvc.perform(post("/api/me/author-request")
                .with(authentication(TestSecurityFactory.asReader(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/conflict"));
    }

    @Test
    void submitRequest_nonReaderRole_returns409WithProblemDetail() throws Exception {
        given(authorRequestService.createRequest(eq(2), any()))
                .willThrow(new IllegalStateException("Only users with the READER role can submit an author request."));

        mockMvc.perform(post("/api/me/author-request")
                .with(authentication(TestSecurityFactory.asReader(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/conflict"));
    }

    @Test
    void submitRequest_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(post("/api/me/author-request"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getActiveRequest_asAuthenticatedUser_returns200() throws Exception {
        given(authorRequestService.getMyActiveRequest(2)).willReturn(SAMPLE);

        mockMvc.perform(get("/api/me/author-request/active")
                .with(authentication(TestSecurityFactory.asReader(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getActiveRequest_noActiveRequest_returns404WithProblemDetail() throws Exception {
        given(authorRequestService.getMyActiveRequest(2))
                .willThrow(new ResourceNotFoundException("No active author request found for this user."));

        mockMvc.perform(get("/api/me/author-request/active")
                .with(authentication(TestSecurityFactory.asReader(2))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"));
    }

    @Test
    void getHistory_asAuthenticatedUser_returns200WithPage() throws Exception {
        given(authorRequestService.getMyRequests(eq(2), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(SAMPLE)));

        mockMvc.perform(get("/api/me/author-request/history")
                .with(authentication(TestSecurityFactory.asReader(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }
}
