package com.blog.blog_literario.exception;

/**
 * Thrown when an email change is attempted on the OWNER account. The owner's address is
 * managed exclusively through the {@code OWNER_EMAIL} environment variable: {@code
 * DataInitializer} reseeds the owner by that address on every startup, so a self-service
 * change would spawn a second, undeletable OWNER on the next restart. Maps to HTTP 403.
 */
public class OwnerEmailImmutableException extends RuntimeException {
    public OwnerEmailImmutableException(String message) {
        super(message);
    }
}
