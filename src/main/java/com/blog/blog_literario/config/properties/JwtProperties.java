package com.blog.blog_literario.config.properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code jwt.secret}, {@code jwt.expiration} (ms), and {@code jwt.refresh-expiration} (ms) from {@code application.properties}. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expiration, long refreshExpiration) {
}
