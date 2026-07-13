package com.blog.blog_literario.config.properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code email.logo-url} from {@code application.properties}: the absolute URL
 * of the logo image embedded in outgoing emails. Decorative only — every email must
 * stay fully functional with images blocked ({@code alt} text carries the brand name).
 *
 * <p>The sibling key {@code email.provider} selects the {@code EmailService}
 * implementation via {@code @ConditionalOnProperty} and is intentionally not bound here.
 */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(String logoUrl) {
}
