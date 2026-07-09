package com.cloudspring.intelligence_rag_api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String errorCode,
        String message,
        String correlationId,
        Instant timestamp
) {
    public static ErrorResponse of(String errorCode, String message, String correlationId) {
        return new ErrorResponse(errorCode, message, correlationId, Instant.now());
    }
}
