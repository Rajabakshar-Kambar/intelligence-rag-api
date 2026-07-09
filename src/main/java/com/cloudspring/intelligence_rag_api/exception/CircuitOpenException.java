package com.cloudspring.intelligence_rag_api.exception;

/**
 * Thrown when a Resilience4j circuit breaker is OPEN and the call is rejected.
 * Mapped to 503 Service Unavailable by the global handler.
 */
public class CircuitOpenException extends RagException {

    public CircuitOpenException(String message) {
        super(message);
    }
}
