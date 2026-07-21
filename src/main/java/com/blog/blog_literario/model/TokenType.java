package com.blog.blog_literario.model;

/**
 * Discriminates the two flows that share the {@code email_verification_tokens} table.
 *
 * <p>This is the single source of truth for a row's purpose — it is never inferred from
 * whether another column (e.g. {@code pending_email}) is set. Each redemption endpoint
 * asserts the type and rejects a token of the wrong type.
 */
public enum TokenType {

    /** Registration email verification: confirming the address the account was created with. */
    VERIFICATION,

    /** Self-service email change: confirming a new address before it replaces the current one. */
    EMAIL_CHANGE
}
