package com.blog.blog_literario.exception;

import java.net.URI;
import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Translates domain exceptions into RFC 9457 {@link ProblemDetail} responses.
 *
 * Each handler maps one exception type to an HTTP status and a typed problem URI so
 * clients can distinguish errors programmatically.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
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

    /**
     * 409 — the row was modified by a concurrent transaction (@Version conflict).
     * The raw message names entity classes, so a fixed user-facing detail is used instead.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "La solicitud fue modificada por otra persona al mismo tiempo. Recarga la página e inténtalo de nuevo.");
        pd.setType(URI.create("https://blog-literario.com/errors/conflict"));
        return pd;
    }

    /** 400 — bean validation failed; detail lists all field-level violation messages. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setType(URI.create("https://blog-literario.com/errors/validation"));
        return pd;
    }

    /** 400 — a required query or form parameter is missing from the request. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'");
        pd.setType(URI.create("https://blog-literario.com/errors/bad-request"));
        return pd;
    }

    /** 403 — credentials are correct but the account's email is not verified yet. */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ProblemDetail handleEmailNotVerified(EmailNotVerifiedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setType(URI.create("https://blog-literario.com/errors/email-not-verified"));
        return pd;
    }

    /** 403 — the OWNER account's email is environment-managed and cannot be self-changed. */
    @ExceptionHandler(OwnerEmailImmutableException.class)
    public ProblemDetail handleOwnerEmailImmutable(OwnerEmailImmutableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setType(URI.create("https://blog-literario.com/errors/forbidden"));
        return pd;
    }

    /** 400 — verification token does not match any stored token. */
    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ProblemDetail handleInvalidVerificationToken(InvalidVerificationTokenException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://blog-literario.com/errors/invalid-verification-token"));
        return pd;
    }

    /** 410 — verification token expired or was superseded; the client should offer a resend. */
    @ExceptionHandler(VerificationTokenExpiredException.class)
    public ProblemDetail handleVerificationTokenExpired(VerificationTokenExpiredException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        pd.setType(URI.create("https://blog-literario.com/errors/verification-token-expired"));
        return pd;
    }

    /** 401 — supplied credentials are incorrect. */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        pd.setType(URI.create("https://blog-literario.com/errors/unauthorized"));
        return pd;
    }

    /** 404 — no controller or static resource matches the request path. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(NoResourceFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso no encontrado");
        pd.setType(URI.create("https://blog-literario.com/errors/not-found"));
        return pd;
    }

    /** 500 — unexpected error; the original message is logged but never exposed to the client. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedError(Exception ex) {
        log.error("Unexpected error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ha ocurrido un error inesperado. Inténtalo de nuevo.");
        pd.setType(URI.create("https://blog-literario.com/errors/internal"));
        return pd;
    }
}
