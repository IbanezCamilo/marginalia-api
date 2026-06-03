package com.blog.blog_literario.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.base-url} from {@code application.yml}; used to build absolute resource URLs. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl) {
}
