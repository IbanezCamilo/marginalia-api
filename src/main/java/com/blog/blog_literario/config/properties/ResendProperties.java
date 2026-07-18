package com.blog.blog_literario.config.properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code resend.api-key}, {@code resend.from}, and {@code resend.notifications-from}
 * from {@code application.properties}.
 *
 * <p>Only used when {@code email.provider=resend}. The API key comes from the
 * {@code RESEND_API_KEY} environment variable — use a {@code sending_access} key,
 * never a {@code full_access} one. Both sender addresses must be on a domain
 * verified in the Resend dashboard, e.g. {@code Marginalia <no-reply@notifications.example.com>}.
 * {@code from} sends account emails (verification); {@code notificationsFrom} sends
 * internal notifications to the staff (new author requests).
 */
@ConfigurationProperties(prefix = "resend")
public record ResendProperties(String apiKey, String from, String notificationsFrom) {
}
