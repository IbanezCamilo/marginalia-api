package com.blog.blog_literario.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;

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
import com.blog.blog_literario.controllers.auth.AuthController;
import com.blog.blog_literario.dto.auth.AuthTokenPair;
import com.blog.blog_literario.services.auth.AuthService;
import com.blog.blog_literario.support.WebMvcTestConfig;

/**
 * Regression test for the percent-encoding rate-limit bypass.
 *
 * <p>Spring decodes {@code %61} → {@code 'a'} when matching handlers, so
 * {@code /api/%61uth/login} still routes to {@link AuthController#login}. If
 * {@code RateLimitFilter} classifies on the raw, undecoded {@code getRequestURI()} it sees
 * {@code /api/%61uth/login}, which does not start with {@code /api/auth/}, so the request
 * escapes the AUTH brute-force limit (10/min). The filter must classify on the decoded path.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class RateLimitEncodedPathBypassTest {

    private static final String ENCODED_LOGIN = "/api/%61uth/login"; // %61 == 'a'
    private static final String BODY = "{\"email\":\"user@test.com\",\"password\":\"password123\"}";

    @Autowired MockMvc mockMvc;
    @Autowired RateLimitFilter rateLimitFilter;

    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        given(authService.login(any())).willReturn(new AuthTokenPair("test-jwt", "test-refresh"));

        Field bucketsField = RateLimitFilter.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);
        ((Map<?, ?>) bucketsField.get(rateLimitFilter)).clear();
    }

    /** (a) Proves Spring decodes the percent-encoded path and dispatches it to the controller. */
    @Test
    void encodedAuthPath_isRoutedToController() throws Exception {
        mockMvc.perform(post(URI.create(ENCODED_LOGIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    /** (b) The encoded variant must be throttled exactly like the plain AUTH path: 11th call → 429. */
    @Test
    void encodedAuthPath_isRateLimitedLikeAuth() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post(URI.create(ENCODED_LOGIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post(URI.create(ENCODED_LOGIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isTooManyRequests());
    }
}
