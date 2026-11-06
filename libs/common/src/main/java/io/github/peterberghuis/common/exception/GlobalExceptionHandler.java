package io.github.peterberghuis.common.exception;

import io.github.peterberghuis.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Turns exceptions into {@link ErrorResponse} bodies.
 * <p>
 * It extends {@link ResponseEntityExceptionHandler} so that Spring MVC's own exceptions (unmapped route, wrong HTTP
 * method, unsupported media type, missing or mistyped request parameter, ...) keep the status Spring assigns them
 * instead of being swallowed by the catch-all handler below and reported as {@code 500}.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        return createErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        return createErrorResponse(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex, WebRequest request) {
        return createErrorResponse((HttpStatus) ex.getStatusCode(), ex.getReason(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: ", ex);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String message = "Validation failed: " + ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return toResponse(HttpStatus.BAD_REQUEST, message, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return toResponse(HttpStatus.BAD_REQUEST, "Malformed request body", headers, request);
    }

    /**
     * Every other Spring MVC exception ({@code 404} unmapped route, {@code 405}, {@code 415}, missing or mistyped
     * request parameters, ...) ends up here with the status Spring assigned to it.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String message;
        if (statusCode.is5xxServerError()) {
            log.error("Unexpected error occurred: ", ex);
            message = "An unexpected error occurred";
        } else {
            // Spring's own exceptions describe themselves in a ProblemDetail (e.g. "Required parameter 'q' is not present.")
            ProblemDetail problemDetail = body instanceof ProblemDetail pd ? pd
                    : ex instanceof org.springframework.web.ErrorResponse springError ? springError.getBody() : null;
            message = problemDetail != null && problemDetail.getDetail() != null
                    ? problemDetail.getDetail()
                    : reasonPhrase(statusCode);
        }
        return toResponse(statusCode, message, headers, request);
    }

    protected ResponseEntity<ErrorResponse> createErrorResponse(HttpStatus status, String message, WebRequest request) {
        return new ResponseEntity<>(buildErrorResponse(status, message, request), status);
    }

    private ResponseEntity<Object> toResponse(HttpStatusCode status, String message, HttpHeaders headers, WebRequest request) {
        return new ResponseEntity<>(buildErrorResponse(status, message, request), headers, status);
    }

    private ErrorResponse buildErrorResponse(HttpStatusCode status, String message, WebRequest request) {
        String path = "";
        if (request instanceof ServletWebRequest servletWebRequest) {
            path = servletWebRequest.getRequest().getRequestURI();
        }

        return ErrorResponse.builder()
                .status(status.value())
                .error(reasonPhrase(status))
                .message(message)
                .path(path)
                .build();
    }

    private static String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "";
    }
}
