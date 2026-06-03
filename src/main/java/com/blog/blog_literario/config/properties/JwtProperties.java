package com.blog.blog_literario.config.properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code jwt.secret} (Base64 HMAC key) and {@code jwt.expiration} (ms) from {@code application.yml}. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expiration) {
}
