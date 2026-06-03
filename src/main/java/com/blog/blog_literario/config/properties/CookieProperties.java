package com.blog.blog_literario.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.cookie.*} from {@code application.yml}; controls JWT cookie attributes. */
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(boolean secure, String domain) {
}
