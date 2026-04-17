package com.tradingsaas.tradingcore.adapter.in.web;

import com.tradingsaas.tradingcore.domain.exception.EmailAlreadyExistsException;
import com.tradingsaas.tradingcore.domain.exception.InsufficientSubscriptionException;
import com.tradingsaas.tradingcore.domain.exception.InvalidCredentialsException;
import com.tradingsaas.tradingcore.domain.exception.TokenBlacklistedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
class GlobalExceptionHandler {

    record ErrorResponse(int status, String error, String message,
                         List<Map<String, String>> details, Instant timestamp, String path) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> details = ex.getBindingResult().getAllErrors().stream()
                .filter(e -> e instanceof FieldError)
                .map(e -> {
                    FieldError fe = (FieldError) e;
                    return Map.of("field", fe.getField(), "message", fe.getDefaultMessage());
                })
                .toList();
        return new ErrorResponse(400, "Bad Request", "Validation failed", details,
                Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleEmailExists(EmailAlreadyExistsException ex, HttpServletRequest req) {
        return new ErrorResponse(409, "Conflict", ex.getMessage(), List.of(),
                Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest req) {
        return new ErrorResponse(401, "Unauthorized", "Invalid email or password", List.of(),
                Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(TokenBlacklistedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse handleTokenBlacklisted(TokenBlacklistedException ex, HttpServletRequest req) {
        return new ErrorResponse(401, "Unauthorized", "Token has been revoked", List.of(),
                Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(InsufficientSubscriptionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ErrorResponse handleInsufficientSubscription(InsufficientSubscriptionException ex, HttpServletRequest req) {
        return new ErrorResponse(403, "Forbidden", ex.getMessage(), List.of(),
                Instant.now(), req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ErrorResponse handleUnexpected(Exception ex, HttpServletRequest req) {
        return new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred",
                List.of(), Instant.now(), req.getRequestURI());
    }
}
