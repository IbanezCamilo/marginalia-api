package com.blog.blog_literario.controllers.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.util.Map;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.auth.AuthTokenPair;
import com.blog.blog_literario.exception.UserAlreadyExistsException;
import com.blog.blog_literario.security.CookieUtil;
import org.springframework.security.authentication.BadCredentialsException;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.auth.AuthService;
import com.blog.blog_literario.support.WebMvcTestConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired RateLimitFilter rateLimitFilter;

    @MockBean AuthService authService;
    @Autowired CookieUtil cookieUtil;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final AuthTokenPair TOKENS = new AuthTokenPair("test-jwt", "test-refresh");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetRateLimitBuckets() throws Exception {
        Field bucketsField = RateLimitFilter.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);
        ((Map<?, ?>) bucketsField.get(rateLimitFilter)).clear();
    }

    @Test
    void login_validCredentials_returns200AndSetsBothCookies() throws Exception {
        given(authService.login(any())).willReturn(TOKENS);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        verify(cookieUtil).addJwtCookie(any(HttpServletResponse.class), eq("test-jwt"));
        verify(cookieUtil).addRefreshTokenCookie(any(HttpServletResponse.class), eq("test-refresh"));
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@test.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_validRequest_returns201AndSetsBothCookies() throws Exception {
        given(authService.register(any())).willReturn(TOKENS);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"John\",\"email\":\"john@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        verify(cookieUtil).addJwtCookie(any(HttpServletResponse.class), eq("test-jwt"));
        verify(cookieUtil).addRefreshTokenCookie(any(HttpServletResponse.class), eq("test-refresh"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"John\",\"email\":\"john@test.com\",\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"John\",\"email\":\"not-an-email\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"john@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicateEmail_returns409WithProblemDetail() throws Exception {
        given(authService.register(any())).willThrow(new UserAlreadyExistsException("Email ya registrado"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"John\",\"email\":\"existing@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void refresh_validCookie_returns200AndSetsBothCookies() throws Exception {
        given(cookieUtil.extractFromRequest(any(), eq(CookieUtil.REFRESH_COOKIE_NAME))).willReturn("raw-refresh");
        given(authService.refresh("raw-refresh")).willReturn(TOKENS);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie(CookieUtil.REFRESH_COOKIE_NAME, "raw-refresh")))
                .andExpect(status().isOk());

        verify(cookieUtil).addJwtCookie(any(HttpServletResponse.class), eq("test-jwt"));
        verify(cookieUtil).addRefreshTokenCookie(any(HttpServletResponse.class), eq("test-refresh"));
    }

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        given(cookieUtil.extractFromRequest(any(), eq(CookieUtil.REFRESH_COOKIE_NAME))).willReturn(null);

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).refresh(any());
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        given(cookieUtil.extractFromRequest(any(), eq(CookieUtil.REFRESH_COOKIE_NAME))).willReturn("bad-token");
        given(authService.refresh("bad-token")).willThrow(new BadCredentialsException("Refresh token inválido"));

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie(CookieUtil.REFRESH_COOKIE_NAME, "bad-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withRefreshCookie_returns200AndClearsBothCookies() throws Exception {
        given(cookieUtil.extractFromRequest(any(), eq(CookieUtil.REFRESH_COOKIE_NAME))).willReturn("raw-refresh");
        given(cookieUtil.extractFromRequest(any(), eq(CookieUtil.NAME))).willReturn(null);

        mockMvc.perform(post("/api/auth/logout")
                .cookie(new Cookie(CookieUtil.REFRESH_COOKIE_NAME, "raw-refresh")))
                .andExpect(status().isOk());

        verify(authService).logout("raw-refresh", null);
        verify(cookieUtil).clearJwtCookie(any(HttpServletResponse.class));
        verify(cookieUtil).clearRefreshTokenCookie(any(HttpServletResponse.class));
    }

    @Test
    void logout_withoutRefreshCookie_returns200AndStillClearsCookies() throws Exception {
        given(cookieUtil.extractFromRequest(any(), eq(CookieUtil.REFRESH_COOKIE_NAME))).willReturn(null);
        given(cookieUtil.extractFromRequest(any(), eq(CookieUtil.NAME))).willReturn(null);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());

        verify(authService).logout(null, null);
        verify(cookieUtil).clearJwtCookie(any(HttpServletResponse.class));
        verify(cookieUtil).clearRefreshTokenCookie(any(HttpServletResponse.class));
    }

    @Test
    void login_badCredentials_returns401WithProblemDetail() throws Exception {
        given(authService.login(any())).willThrow(new BadCredentialsException("bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@test.com\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }
}
