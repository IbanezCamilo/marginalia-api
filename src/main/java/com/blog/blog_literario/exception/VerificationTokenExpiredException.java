package com.blog.blog_literario.exception;

/**
 * Exception thrown when an email verification token exists but has expired
 * (or was superseded by a newer one). Should result in HTTP 410 Gone response,
 * which the frontend uses to offer a resend.
 */
public class VerificationTokenExpiredException extends RuntimeException {

    public VerificationTokenExpiredException(String message) {
        super(message);
    }
}
