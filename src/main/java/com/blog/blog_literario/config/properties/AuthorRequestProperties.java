package com.blog.blog_literario.config.properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code author-request.claim-ttl-minutes} from {@code application.properties}.
 *
 * <p>The TTL bounds how long an admin's "under review" claim on an author request
 * stays active without being released — if the admin closes the tab without
 * cancelling, the claim simply expires and another admin can take over.
 */
@ConfigurationProperties(prefix = "author-request")
public record AuthorRequestProperties(long claimTtlMinutes) {
}
