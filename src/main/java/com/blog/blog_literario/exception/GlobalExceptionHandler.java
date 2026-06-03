package com.blog.blog_literario.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into RFC 9457 {@link ProblemDetail} responses.
 *
 * Each handler maps one exception type to an HTTP status and a typed problem URI so
 * clients can distinguish errors programmatically.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 404 — requested resource does not exist. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://blog-literario.com/errors/not-found"));
        return pd;
    }

    /** 409 — attempt to create a resource that already exists (duplicate email, etc.). */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://blog-literario.com/errors/conflict"));
        return pd;
    }

    /** 400 — caller passed an invalid argument (bad status value, malformed input, etc.). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://blog-literario.com/errors/bad-request"));
        return pd;
    }

    /** 409 — operation conflicts with current system state (e.g. duplicate pending request). */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://blog-literario.com/errors/conflict"));
        return pd;
    }
}
