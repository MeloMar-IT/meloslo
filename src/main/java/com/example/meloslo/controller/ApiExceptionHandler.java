package com.example.meloslo.controller;

import com.example.meloslo.service.BadRequestException;
import com.example.meloslo.service.ConflictException;
import com.example.meloslo.service.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Maps specific exceptions to 404, 409 and 400 with a JSON body of status, error and message. */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String CONFLICT_MESSAGE =
            "The change conflicts with existing data (for example a duplicate name or a record that is still in use)";

    public record ApiError(int status, String error, String message) {
        public static ApiError of(HttpStatus status, String message) {
            return new ApiError(status.value(), status.getReasonPhrase(), message);
        }
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status, message));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException e) {
        return respond(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException e) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Backstop for races on unique constraints; the SQL text never reaches the client. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getClass().getSimpleName());
        return respond(HttpStatus.CONFLICT, CONFLICT_MESSAGE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String message = Stream.concat(
                        e.getBindingResult().getFieldErrors().stream()
                                .map(f -> f.getField() + ": " + f.getDefaultMessage()),
                        e.getBindingResult().getGlobalErrors().stream()
                                .map(g -> g.getObjectName() + ": " + g.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, message.isEmpty() ? "Validation failed" : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        if (e.getCause() instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            String path = jme.getPath().stream()
                    .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                    .collect(Collectors.joining("."))
                    .replace(".[", "[");
            return respond(HttpStatus.BAD_REQUEST, "Invalid value for field '" + path + "'");
        }
        return respond(HttpStatus.BAD_REQUEST, "Malformed request body");
    }
}
