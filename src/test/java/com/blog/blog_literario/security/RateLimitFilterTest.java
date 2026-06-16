package com.blog.blog_literario.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.config.properties.RateLimitProperties;
import com.blog.blog_literario.controllers.auth.AuthController;
import com.blog.blog_literario.dto.auth.AuthTokenPair;
import com.blog.blog_literario.services.auth.AuthService;
import com.blog.blog_literario.support.WebMvcTestConfig;
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
class RateLimitFilterTest {

    @Autowired MockMvc mockMvc;
    @Autowired RateLimitFilter rateLimitFilter;

    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    void register_rateLimitExceeded_returns429() throws Exception {
        given(authService.register(any())).willReturn(new AuthTokenPair("token", "refresh"));

        String body = "{\"name\":\"John\",\"email\":\"john@test.com\",\"password\":\"password123\"}";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @SuppressWarnings("unchecked")
    void evictStaleBuckets_removesEntriesOlderThan10Minutes() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(false));

        Field bucketsField = RateLimitFilter.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);
        Map<String, Object> buckets = (Map<String, Object>) bucketsField.get(filter);

        Class<?> entryClass = Class.forName("com.blog.blog_literario.security.RateLimitFilter$BucketEntry");
        Field lastUsedField = entryClass.getDeclaredField("lastUsed");
        lastUsedField.setAccessible(true);

        Method createBucket = RateLimitFilter.class.getDeclaredMethod("createBucket", RateLimitProfile.class);
        createBucket.setAccessible(true);
        Object freshBucket = createBucket.invoke(filter, RateLimitProfile.AUTH);

        java.lang.reflect.Constructor<?> ctor = entryClass.getDeclaredConstructor(
                io.github.bucket4j.Bucket.class);
        ctor.setAccessible(true);
        Object staleEntry = ctor.newInstance(freshBucket);
        lastUsedField.set(staleEntry, Instant.now().minusSeconds(700)); // 11+ minutes ago

        buckets.put("1.2.3.4:AUTH", staleEntry);
        assertThat(buckets).hasSize(1);

        filter.evictStaleBuckets();

        assertThat(buckets).isEmpty();
    }

    @Test
    void resolveProfile_authEndpoint_returnsAuth() {
        assertThat(rateLimitFilter.resolveProfile("/api/auth/login", "POST"))
                .isEqualTo(RateLimitProfile.AUTH);
    }

    @Test
    void resolveProfile_publicEndpoint_returnsPublic() {
        assertThat(rateLimitFilter.resolveProfile("/api/public/posts", "GET"))
                .isEqualTo(RateLimitProfile.PUBLIC);
    }

    @Test
    void resolveProfile_imagesEndpoint_returnsImages() {
        assertThat(rateLimitFilter.resolveProfile("/api/images/photo.jpg", "GET"))
                .isEqualTo(RateLimitProfile.IMAGES);
    }

    @Test
    void resolveProfile_postCoverImageUpload_returnsUpload() {
        assertThat(rateLimitFilter.resolveProfile("/api/me/posts/5/cover-image", "POST"))
                .isEqualTo(RateLimitProfile.UPLOAD);
    }

    @Test
    void resolveProfile_profileImageUpload_returnsUpload() {
        assertThat(rateLimitFilter.resolveProfile("/api/me/profile/image", "POST"))
                .isEqualTo(RateLimitProfile.UPLOAD);
    }

    @Test
    void resolveProfile_adminEndpoint_returnsNull() {
        assertThat(rateLimitFilter.resolveProfile("/api/admin/users", "GET"))
                .isNull();
    }

    @Test
    void resolveProfile_profileImageGet_returnsNull() {
        assertThat(rateLimitFilter.resolveProfile("/api/me/profile/image", "GET"))
                .isNull();
    }
}
