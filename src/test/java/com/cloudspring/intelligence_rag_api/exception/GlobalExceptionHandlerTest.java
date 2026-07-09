package com.cloudspring.intelligence_rag_api.exception;

import com.cloudspring.intelligence_rag_api.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleEmbedding_returns502() {
        EmbeddingException ex = new EmbeddingException("secret internal detail",
                new RuntimeException("sdk error"));

        ResponseEntity<ErrorResponse> response = handler.handleEmbedding(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errorCode()).isEqualTo("EMBEDDING_ERROR");
        // Internal message must NOT leak to the caller
        assertThat(response.getBody().message()).doesNotContain("secret internal detail");
        assertThat(response.getBody().correlationId()).isNotBlank();
    }

    @Test
    void handleRetrieval_returns502() {
        RetrievalException ex = new RetrievalException("qdrant host unreachable",
                new RuntimeException());

        ResponseEntity<ErrorResponse> response = handler.handleRetrieval(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().errorCode()).isEqualTo("RETRIEVAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("qdrant host unreachable");
    }

    @Test
    void handleLlm_returns502() {
        LlmException ex = new LlmException("throttled by bedrock", new RuntimeException());

        ResponseEntity<ErrorResponse> response = handler.handleLlm(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().errorCode()).isEqualTo("LLM_ERROR");
        assertThat(response.getBody().message()).doesNotContain("throttled by bedrock");
    }

    @Test
    void handleCircuitOpen_returns503() {
        CircuitOpenException ex = new CircuitOpenException("circuit open");

        ResponseEntity<ErrorResponse> response = handler.handleCircuitOpen(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().errorCode()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void handleUnexpected_returns500AndNeverLeaksInternalMessage() {
        RuntimeException ex = new RuntimeException("NullPointerException at line 42");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message())
                .doesNotContain("NullPointerException")
                .doesNotContain("line 42");
        assertThat(response.getBody().correlationId()).isNotBlank();
    }

    @Test
    void allHandlers_includeTimestamp() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("x"));
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}