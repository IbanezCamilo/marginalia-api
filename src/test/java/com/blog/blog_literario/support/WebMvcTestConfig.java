package com.blog.blog_literario.support;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.config.properties.JwtProperties;
import com.blog.blog_literario.config.properties.RateLimitProperties;
import com.blog.blog_literario.config.properties.StorageProperties;
import com.blog.blog_literario.security.CookieUtil;
import com.blog.blog_literario.services.auth.RefreshTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

@TestConfiguration
@EnableConfigurationProperties({FrontendProperties.class, RateLimitProperties.class, JwtProperties.class,
        StorageProperties.class})
public class WebMvcTestConfig {

    @MockBean CookieUtil cookieUtil;
    @MockBean RefreshTokenService refreshTokenService;

}
