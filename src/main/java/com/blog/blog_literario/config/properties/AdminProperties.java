package com.blog.blog_literario.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code admin.email} and {@code admin.password} from {@code application.yml}. */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(String email, String password) {
}
