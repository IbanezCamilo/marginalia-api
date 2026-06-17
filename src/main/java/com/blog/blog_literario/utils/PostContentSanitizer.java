package com.blog.blog_literario.utils;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Strips unsafe HTML from author-submitted post content before it is persisted.
 *
 * <p>Allows the minimal rich-text tag set a literary blog post needs (paragraphs,
 * basic inline formatting, links, lists, headings, blockquotes) and drops everything
 * else — including {@code <script>}, inline event handlers, and {@code javascript:} URLs —
 * so stored content can never execute script in a reader's or moderator's browser.
 */
public final class PostContentSanitizer {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(new HtmlPolicyBuilder()
                    .allowElements("h1", "h2", "h3")
                    .toFactory());

    private PostContentSanitizer() {
    }

    /** Returns {@code content} with all non-allow-listed tags and attributes removed. */
    public static String sanitize(String content) {
        if (content == null) {
            return null;
        }
        return POLICY.sanitize(content);
    }
}
