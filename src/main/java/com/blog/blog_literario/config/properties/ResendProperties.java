package com.blog.blog_literario.config.properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code resend.api-key} and {@code resend.from} from {@code application.properties}.
 *
 * <p>Only used when {@code email.provider=resend}. The API key comes from the
 * {@code RESEND_API_KEY} environment variable — use a {@code sending_access} key,
 * never a {@code full_access} one. {@code from} must be an address on a domain
 * verified in the Resend dashboard, e.g. {@code Marginalia <no-reply@notifications.example.com>}.
 */
@ConfigurationProperties(prefix = "resend")
public record ResendProperties(String apiKey, String from) {
}
