package com.cloudspring.intelligence_rag_api.exception;

/**
 * Base exception for all RAG pipeline failures.
 * Subclasses carry semantic meaning so the exception handler can
 * return appropriate HTTP status codes without inspecting messages.
 */
public class RagException extends RuntimeException {

    public RagException(String message) {
        super(message);
    }

    public RagException(String message, Throwable cause) {
        super(message, cause);
    }
}
