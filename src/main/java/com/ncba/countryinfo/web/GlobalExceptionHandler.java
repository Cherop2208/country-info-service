
package com.ncba.countryinfo.web;

import com.ncba.countryinfo.dto.ErrorResponse;
import com.ncba.countryinfo.exception.ExternalServiceException;
import com.ncba.countryinfo.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every failure to a consistent, user-friendly JSON body with the
 * appropriate HTTP status. Never leaks stack traces to API clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(
            MethodArgumentNotValidException ex,
            HttpServletRequest req) {

        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();

        return build(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                req,
                details,
                null
        );
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        PropertyReferenceException.class
    })
    public ResponseEntity<ErrorResponse> badRequest(
            Exception ex,
            HttpServletRequest req) {

        return build(
                HttpStatus.BAD_REQUEST,
                "Malformed request: check the JSON body, path and query parameters",
                req,
                null,
                ex
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> method(
            Exception ex,
            HttpServletRequest req) {

        return build(
                HttpStatus.METHOD_NOT_ALLOWED,
                ex.getMessage(),
                req,
                null,
                null
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> mediaType(
            Exception ex,
            HttpServletRequest req) {

        return build(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type must be application/json",
                req,
                null,
                null
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> noEndpoint(
            NoResourceFoundException ex,
            HttpServletRequest req) {

        return build(
                HttpStatus.NOT_FOUND,
                "No endpoint found for " + req.getRequestURI(),
                req,
                null,
                null
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(
            ResourceNotFoundException ex,
            HttpServletRequest req) {

        return build(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                req,
                null,
                null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(
            DataIntegrityViolationException ex,
            HttpServletRequest req) {

        return build(
                HttpStatus.CONFLICT,
                "The request conflicts with existing data (duplicate country?)",
                req,
                null,
                ex
        );
    }

    @ExceptionHandler({
        ExternalServiceException.class,
        CallNotPermittedException.class
    })
    public ResponseEntity<ErrorResponse> upstream(
            Exception ex,
            HttpServletRequest req) {

        log.error("Upstream SOAP failure: {}", ex.getMessage());

        ResponseEntity<ErrorResponse> response = build(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The country information provider is temporarily unavailable. Please retry shortly.",
                req,
                null,
                null
        );

        return ResponseEntity
                .status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(response.getBody());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(
            Exception ex,
            HttpServletRequest req) {

        // Preserve the status of Spring web exceptions.
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(
                    springError.getStatusCode().value()
            );

            String message = status == HttpStatus.NOT_FOUND
                    ? "No endpoint found for " + req.getRequestURI()
                    : springError.getBody().getDetail();

            return build(status, message, req, null, ex);
        }

        log.error("Unhandled exception", ex);

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Quote the correlationId when reporting it.",
                req,
                null,
                null
        );
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest req,
            List<String> details,
            Exception toLog) {

        if (toLog != null) {
            log.warn("{}: {}", status, toLog.getMessage());
        }

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY),
                details
        );

        return ResponseEntity.status(status).body(body);
    }
}
