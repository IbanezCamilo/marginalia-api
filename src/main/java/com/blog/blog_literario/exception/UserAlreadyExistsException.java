package com.blog.blog_literario.exception;

/**
 * Exception thrown when attempting to create a user with an email that already exists
 * Should result in HTTP 409 Conflict response
 */
public class UserAlreadyExistsException extends RuntimeException {
    
    public UserAlreadyExistsException(String message) {
        super(message);
    }
    
    public UserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
