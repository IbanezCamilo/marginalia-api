package com.blog.blog_literario.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code owner.email} and {@code owner.password} from {@code application.properties}. */
@ConfigurationProperties(prefix = "owner")
public record OwnerProperties(String email, String password) {
}
