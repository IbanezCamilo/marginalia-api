package com.blog.blog_literario.exception;

/**
 * Exception thrown when an email verification token does not match any stored token.
 * Should result in HTTP 400 Bad Request response.
 */
public class InvalidVerificationTokenException extends RuntimeException {

    public InvalidVerificationTokenException(String message) {
        super(message);
    }
}
