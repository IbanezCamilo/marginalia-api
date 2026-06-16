package com.blog.blog_literario.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.blog.blog_literario.config.properties.CookieProperties;
import com.blog.blog_literario.config.properties.JwtProperties;

class CookieUtilTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0cy0xMjM0NTY3ODkw";

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
    }

    @Test
    void addJwtCookie_setsHttpOnlyCookieWithToken() {
        CookieUtil cookieUtil = new CookieUtil(new CookieProperties(false, null), new JwtProperties(SECRET, 86_400_000L, 604_800_000L));

        cookieUtil.addJwtCookie(response, "test-token");

        String cookieHeader = response.getHeader("Set-Cookie");
        assertThat(cookieHeader)
                .contains("jwt=test-token")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void addJwtCookie_secureFlagReflectsCookieProperties() {
        CookieUtil secureCookieUtil = new CookieUtil(new CookieProperties(true, null), new JwtProperties(SECRET, 86_400_000L, 604_800_000L));
        CookieUtil insecureCookieUtil = new CookieUtil(new CookieProperties(false, null), new JwtProperties(SECRET, 86_400_000L, 604_800_000L));

        secureCookieUtil.addJwtCookie(response, "test-token");
        assertThat(response.getHeader("Set-Cookie")).contains("Secure");

        response = new MockHttpServletResponse();
        insecureCookieUtil.addJwtCookie(response, "test-token");
        assertThat(response.getHeader("Set-Cookie")).doesNotContain("Secure");
    }

    @Test
    void addJwtCookie_maxAgeDerivedFromJwtExpiration() {
        CookieUtil cookieUtil = new CookieUtil(new CookieProperties(false, null), new JwtProperties(SECRET, 60_000L, 604_800_000L));

        cookieUtil.addJwtCookie(response, "test-token");

        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=60");
    }

    @Test
    void clearJwtCookie_setsEmptyValueAndMaxAgeZero() {
        CookieUtil cookieUtil = new CookieUtil(new CookieProperties(false, null), new JwtProperties(SECRET, 86_400_000L, 604_800_000L));

        cookieUtil.clearJwtCookie(response);

        String cookieHeader = response.getHeader("Set-Cookie");
        assertThat(cookieHeader)
                .startsWith("jwt=;")
                .contains("Max-Age=0");
    }

    @Test
    void addRefreshTokenCookie_setsNewCookieAtRootAndClearsLegacyPath() {
        CookieUtil cookieUtil = new CookieUtil(new CookieProperties(false, null), new JwtProperties(SECRET, 86_400_000L, 604_800_000L));

        cookieUtil.addRefreshTokenCookie(response, "raw-refresh-token");

        var headers = response.getHeaders("Set-Cookie");
        assertThat(headers).hasSize(2);
        assertThat(headers).anySatisfy(h -> assertThat(h)
                .contains("refresh_token=raw-refresh-token")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax"));
        assertThat(headers).anySatisfy(h -> assertThat(h)
                .startsWith("refresh_token=;")
                .contains("Path=/api/auth")
                .contains("Max-Age=0"));
    }

    @Test
    void clearRefreshTokenCookie_clearsRootPathAndLegacyPath() {
        CookieUtil cookieUtil = new CookieUtil(new CookieProperties(false, null), new JwtProperties(SECRET, 86_400_000L, 604_800_000L));

        cookieUtil.clearRefreshTokenCookie(response);

        var headers = response.getHeaders("Set-Cookie");
        assertThat(headers).hasSize(2);
        assertThat(headers).anySatisfy(h -> assertThat(h)
                .startsWith("refresh_token=;")
                .contains("Max-Age=0")
                .contains("Path=/")
                .doesNotContain("Path=/api/auth"));
        assertThat(headers).anySatisfy(h -> assertThat(h)
                .startsWith("refresh_token=;")
                .contains("Max-Age=0")
                .contains("Path=/api/auth"));
    }
}
