package com.blog.blog_literario.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.services.auth.RefreshTokenService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtService jwtService;
    @Mock UserDetailsService userDetailsService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock CookieUtil cookieUtil;

    private JwtAuthenticationFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService, refreshTokenService, cookieUtil);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_publicAuthRoute_skipsAuthenticationAndContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void doFilter_publicImagesRoute_skipsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/images/photo.jpg");
        request.setServletPath("/api/images/photo.jpg");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void doFilter_publicGetRoute_skipsAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/posts");
        request.setServletPath("/api/public/posts");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void doFilter_noCookiePresent_continuesChainUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/profile");
        request.setServletPath("/api/me/profile");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void doFilter_validJwtCookie_populatesSecurityContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/profile");
        request.setServletPath("/api/me/profile");
        request.setCookies(new Cookie("jwt", "valid-token"));

        User user = new User(1, "Alice", "alice@test.com", new Role(Role.AUTHOR));
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        given(jwtService.extractUsername("valid-token")).willReturn("alice@test.com");
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);
        given(jwtService.isTokenValid("valid-token", userDetails)).willReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(authentication.getName()).isEqualTo("alice@test.com");
    }

    @Test
    void doFilter_invalidJwtCookie_sends401AndStopsChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/profile");
        request.setServletPath("/api/me/profile");
        request.setCookies(new Cookie("jwt", "bad-token"));

        given(jwtService.extractUsername("bad-token")).willThrow(new RuntimeException("invalid token"));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void doFilter_expiredJwtCookie_continuesChainUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/profile");
        request.setServletPath("/api/me/profile");
        request.setCookies(new Cookie("jwt", "expired-token"));

        given(jwtService.extractUsername("expired-token"))
                .willThrow(new ExpiredJwtException((Header<?>) null, (Claims) null, "expired"));
        // cookieUtil.extractFromRequest returns null by default → no refresh attempted

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(cookieUtil).extractFromRequest(request, CookieUtil.REFRESH_COOKIE_NAME);
    }

    @Test
    void doFilter_expiredJwtWithValidRefreshCookie_silentlyRefreshesAndAuthenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/profile");
        request.setServletPath("/api/me/profile");
        request.setCookies(new Cookie("jwt", "expired-token"), new Cookie(CookieUtil.REFRESH_COOKIE_NAME, "raw-refresh"));

        User user = new User(1, "Alice", "alice@test.com", new Role(Role.AUTHOR));
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        RefreshTokenService.RotationResult rotationResult =
                new RefreshTokenService.RotationResult(user, "new-refresh-token");

        given(jwtService.extractUsername("expired-token"))
                .willThrow(new ExpiredJwtException((Header<?>) null, (Claims) null, "expired"));
        given(cookieUtil.extractFromRequest(request, CookieUtil.REFRESH_COOKIE_NAME)).willReturn("raw-refresh");
        given(refreshTokenService.rotate("raw-refresh")).willReturn(rotationResult);
        given(userDetailsService.loadUserByUsername("alice@test.com")).willReturn(userDetails);
        given(jwtService.generateToken(userDetails)).willReturn("new-access-token");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("alice@test.com");
        verify(cookieUtil).addJwtCookie(response, "new-access-token");
        verify(cookieUtil).addRefreshTokenCookie(response, "new-refresh-token");
    }

    @Test
    void doFilter_expiredJwtWithInvalidRefreshToken_continuesChainUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/profile");
        request.setServletPath("/api/me/profile");
        request.setCookies(new Cookie("jwt", "expired-token"), new Cookie(CookieUtil.REFRESH_COOKIE_NAME, "bad-refresh"));

        given(jwtService.extractUsername("expired-token"))
                .willThrow(new ExpiredJwtException((Header<?>) null, (Claims) null, "expired"));
        given(cookieUtil.extractFromRequest(request, CookieUtil.REFRESH_COOKIE_NAME)).willReturn("bad-refresh");
        given(refreshTokenService.rotate("bad-refresh"))
                .willThrow(new BadCredentialsException("Refresh token inválido"));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(cookieUtil, never()).addJwtCookie(any(), any());
        verify(cookieUtil, never()).addRefreshTokenCookie(any(), any());
    }

    @Test
    void doFilter_existingAuthenticationInContext_doesNotOverwrite() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/profile");
        request.setServletPath("/api/me/profile");
        request.setCookies(new Cookie("jwt", "valid-token"));

        User existingUser = new User(2, "Bob", "bob@test.com", new Role(Role.READER));
        UserDetailsImpl existingDetails = new UserDetailsImpl(existingUser);
        var existingAuth = new UsernamePasswordAuthenticationToken(existingDetails, null, existingDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        given(jwtService.extractUsername("valid-token")).willReturn("alice@test.com");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existingAuth);
        verify(userDetailsService, never()).loadUserByUsername(any());
    }
}
