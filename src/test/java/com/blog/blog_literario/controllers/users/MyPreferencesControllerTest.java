package com.blog.blog_literario.controllers.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.users.UserPreferenceService;
import com.blog.blog_literario.support.WebMvcTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MyPreferencesController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class MyPreferencesControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean UserPreferenceService userPreferenceService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    void getPreferences_authenticated_returnsResolvedMap() throws Exception {
        given(userPreferenceService.getResolved(any()))
                .willReturn(Map.of("notifications.post-moderation", "true"));

        mockMvc.perform(get("/api/me/preferences")
                .with(user("alice@test.com").roles("AUTHOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['notifications.post-moderation']").value("true"));
    }

    @Test
    void getPreferences_anonymous_isRejected() throws Exception {
        mockMvc.perform(get("/api/me/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePreferences_validBody_returnsResolvedMap() throws Exception {
        given(userPreferenceService.update(any(), any()))
                .willReturn(Map.of("notifications.post-moderation", "false"));

        mockMvc.perform(put("/api/me/preferences")
                .with(user("alice@test.com").roles("AUTHOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notifications.post-moderation\":\"false\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['notifications.post-moderation']").value("false"));
    }

    @Test
    void updatePreferences_unknownKey_returns400() throws Exception {
        given(userPreferenceService.update(any(), any()))
                .willThrow(new IllegalArgumentException("Preferencia desconocida: 'no.such.pref'"));

        mockMvc.perform(put("/api/me/preferences")
                .with(user("alice@test.com").roles("AUTHOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"no.such.pref\":\"true\"}"))
                .andExpect(status().isBadRequest());
    }
}
