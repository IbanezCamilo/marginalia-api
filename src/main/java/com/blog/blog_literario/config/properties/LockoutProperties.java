package com.blog.blog_literario.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Temporary account-lockout policy for failed login attempts.
 *
 * @param maxAttempts     consecutive failed logins that lock the account
 * @param durationMinutes how long the lock lasts, in minutes
 */
@ConfigurationProperties(prefix = "app.security.lockout")
public record LockoutProperties(int maxAttempts, int durationMinutes) {}
