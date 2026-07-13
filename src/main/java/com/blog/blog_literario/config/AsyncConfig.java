package com.blog.blog_literario.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} execution, used by the email listeners so sending never
 * blocks the HTTP request thread. No custom executor: with
 * {@code spring.threads.virtual.enabled=true} Boot's auto-configured
 * {@code applicationTaskExecutor} already runs async tasks on virtual threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
