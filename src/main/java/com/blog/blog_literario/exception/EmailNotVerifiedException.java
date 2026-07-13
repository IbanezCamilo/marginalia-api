package com.blog.blog_literario.exception;

/**
 * Exception thrown when an unverified account attempts to log in or refresh its session.
 * Should result in HTTP 403 Forbidden response.
 *
 * <p>Only thrown AFTER credentials have been validated, so it never acts as an
 * account-existence oracle, and it must never count as a failed login attempt.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
