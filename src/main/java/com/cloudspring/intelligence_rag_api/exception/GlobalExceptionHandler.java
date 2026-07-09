package com.cloudspring.intelligence_rag_api.exception;

import com.cloudspring.intelligence_rag_api.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String correlationId = newCorrelationId();
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[{}] Validation failure: {}", correlationId, details);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", details, correlationId));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String correlationId = newCorrelationId();
        log.warn("[{}] Constraint violation: {}", correlationId, ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", ex.getMessage(), correlationId));
    }

    @ExceptionHandler(EmbeddingException.class)
    public ResponseEntity<ErrorResponse> handleEmbedding(EmbeddingException ex) {
        String correlationId = newCorrelationId();
        log.error("[{}] Embedding failure", correlationId, ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("EMBEDDING_ERROR",
                        "Failed to generate embedding for your query. Please try again.",
                        correlationId));
    }

    @ExceptionHandler(RetrievalException.class)
    public ResponseEntity<ErrorResponse> handleRetrieval(RetrievalException ex) {
        String correlationId = newCorrelationId();
        log.error("[{}] Retrieval failure", correlationId, ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("RETRIEVAL_ERROR",
                        "Document retrieval is temporarily unavailable. Please try again.",
                        correlationId));
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ErrorResponse> handleLlm(LlmException ex) {
        String correlationId = newCorrelationId();
        log.error("[{}] LLM failure", correlationId, ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("LLM_ERROR",
                        "Language model is temporarily unavailable. Please try again.",
                        correlationId));
    }

    @ExceptionHandler(CircuitOpenException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(CircuitOpenException ex) {
        String correlationId = newCorrelationId();
        log.warn("[{}] Circuit breaker open: {}", correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("SERVICE_UNAVAILABLE",
                        "Service is temporarily degraded. Please retry in a moment.",
                        correlationId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        String correlationId = newCorrelationId();
        // Log the full exception internally; never send it to the client.
        log.error("[{}] Unexpected error", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR",
                        "An unexpected error occurred. Please contact support with reference: "
                                + correlationId,
                        correlationId));
    }

    private String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
