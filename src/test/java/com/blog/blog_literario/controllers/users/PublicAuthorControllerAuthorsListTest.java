package com.blog.blog_literario.controllers.users;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.users.PublicAuthorSummaryResponse;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.users.PublicAuthorService;
import com.blog.blog_literario.support.WebMvcTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicAuthorController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class PublicAuthorControllerAuthorsListTest {

    @Autowired MockMvc mockMvc;

    @MockBean PublicAuthorService publicAuthorService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    void listAuthors_noAuth_returnsIdAndNameOnly() throws Exception {
        given(publicAuthorService.listPublishedAuthors()).willReturn(List.of(
                new PublicAuthorSummaryResponse(1, "Alice Munro"),
                new PublicAuthorSummaryResponse(2, "Bruno Schulz")));

        mockMvc.perform(get("/api/public/authors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Alice Munro"))
                .andExpect(jsonPath("$[1].name").value("Bruno Schulz"));
    }
}
