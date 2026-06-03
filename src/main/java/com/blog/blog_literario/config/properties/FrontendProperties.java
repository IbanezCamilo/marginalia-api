package com.blog.blog_literario.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code frontend.url} from {@code application.yml}; used as the allowed CORS origin. */
@ConfigurationProperties(prefix = "frontend")
public record FrontendProperties(String url) {
}
