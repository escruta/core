package com.escruta.core.exceptions;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateFieldException.class)
    public ProblemDetail handleDuplicateField(DuplicateFieldException ex) {
        return ex.toProblemDetail();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials() {
        return createProblem(
                HttpStatus.UNAUTHORIZED,
                "Authentication failed",
                "It seems something went wrong. Please try signing in again."
        );
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ProblemDetail handleForbidden(Exception ex) {
        return createProblem(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "You don't have permission to perform this action. Please contact support if you believe this is an error."
        );
    }

    @ExceptionHandler(AccountStatusException.class)
    public ProblemDetail handleAccountStatus(AccountStatusException ex) {
        return createProblem(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "Your account access has been restricted. Please contact support for assistance."
        );
    }

    @ExceptionHandler({NoResourceFoundException.class, EntityNotFoundException.class})
    public ProblemDetail handleNotFound(Exception ex) {
        return createProblem(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                "The item you're looking for can't be found. Please check the URL or try a different search."
        );
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail handleBadRequest(Exception ex) {
        return createProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "Something went wrong with your request. Please check your input and try again."
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return createProblem(
                HttpStatus.CONFLICT,
                "Conflict",
                "This item was modified by another session. Refresh and try again."
        );
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex) {
        logger.debug("Async request no longer usable (client disconnected): {}", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(f -> String.format("'%s' %s", f.getField(), f.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        return createProblem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "Please fix the highlighted fields and try again."
        );
    }

    @ExceptionHandler(TransientAiException.class)
    public ProblemDetail handleAiRetryable() {
        return createProblem(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests",
                "Too many requests. Please wait a moment and try again."
        );
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ProblemDetail handleAiFatal(NonTransientAiException ex) {
        HttpStatusCode status = switch (ex.getMessage()) {
            case String s when s.contains("400") -> HttpStatus.BAD_REQUEST;
            case String s when s.contains("429") -> HttpStatus.TOO_MANY_REQUESTS;
            case String s when s.contains("503") -> HttpStatus.SERVICE_UNAVAILABLE;
            case String s when s.contains("504") -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        logger.warn("AI Error: {}", status);
        return ProblemDetail.forStatusAndDetail(
                status,
                "The AI service is experiencing issues. Please try again in a few minutes."
        );
    }

    @ExceptionHandler({RuntimeException.class, Exception.class})
    public ProblemDetail handleGeneric(Exception ex) {
        logger.error("Unexpected error: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
        return createProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "It seems something went wrong on our end. Please try again later."
        );
    }

    private ProblemDetail createProblem(HttpStatusCode status, String title, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, title);
        pd.setProperty("message", message);
        return pd;
    }
}
