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
import java.util.concurrent.ConcurrentHashMap;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.controllers.auth.AuthController;
import com.blog.blog_literario.security.CookieUtil;
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
    @MockBean CookieUtil cookieUtil;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    void register_rateLimitExceeded_returns429() throws Exception {
        given(authService.register(any())).willReturn("token");

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
        RateLimitFilter filter = new RateLimitFilter();

        Field bucketsField = RateLimitFilter.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);
        Map<String, Object> buckets = (Map<String, Object>) bucketsField.get(filter);

        // Use the private BucketEntry constructor via reflection to create a stale entry
        Class<?> entryClass = Class.forName("com.blog.blog_literario.security.RateLimitFilter$BucketEntry");
        Field bucketField = entryClass.getDeclaredField("bucket");
        bucketField.setAccessible(true);
        Field lastUsedField = entryClass.getDeclaredField("lastUsed");
        lastUsedField.setAccessible(true);

        // Create a fresh BucketEntry via the filter's createBucket helper, then back-date it
        Method createBucket = RateLimitFilter.class.getDeclaredMethod("createBucket");
        createBucket.setAccessible(true);
        Object freshBucket = createBucket.invoke(filter);

        java.lang.reflect.Constructor<?> ctor = entryClass.getDeclaredConstructor(
                io.github.bucket4j.Bucket.class);
        ctor.setAccessible(true);
        Object staleEntry = ctor.newInstance(freshBucket);
        lastUsedField.set(staleEntry, Instant.now().minusSeconds(700)); // 11+ minutes ago

        buckets.put("1.2.3.4", staleEntry);
        assertThat(buckets).hasSize(1);

        filter.evictStaleBuckets();

        assertThat(buckets).isEmpty();
    }
}
