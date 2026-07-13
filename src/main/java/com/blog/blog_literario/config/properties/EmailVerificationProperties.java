package com.blog.blog_literario.config.properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code verification.token-expiration-hours}, {@code verification.cooldown-seconds},
 * and {@code verification.daily-cap} from {@code application.properties}.
 *
 * <p>The cooldown and daily cap throttle verification-email sends per account — they
 * protect the Resend quota and prevent a third party from flooding someone's inbox
 * through the public resend endpoint.
 */
@ConfigurationProperties(prefix = "verification")
public record EmailVerificationProperties(long tokenExpirationHours, long cooldownSeconds, int dailyCap) {
}
