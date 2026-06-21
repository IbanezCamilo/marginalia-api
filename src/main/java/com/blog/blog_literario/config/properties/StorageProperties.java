package com.blog.blog_literario.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code storage.local.*} from {@code application.properties}. */
@ConfigurationProperties(prefix = "storage.local")
public record StorageProperties(String uploadDir) {}
