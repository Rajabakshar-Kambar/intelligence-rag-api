package com.cloudspring.intelligence_rag_api.embedding;

import com.cloudspring.intelligence_rag_api.config.AwsProperties;
import com.cloudspring.intelligence_rag_api.config.CacheConfig;
import com.cloudspring.intelligence_rag_api.exception.EmbeddingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.cache.annotation.Cacheable;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Spring AI {@link EmbeddingModel} adapter for Amazon Bedrock Titan Embed Text v2.
 *
 * <p>This is the single embedding implementation in the project. The previous
 * {@code TitanEmbeddingService} (a plain {@code @Service}) and
 * {@code CustomTitanEmbeddingModel} duplicated the same Bedrock call — both are
 * replaced by this class.
 *
 * <p>Results are cached by a SHA-256 digest of the input text, so repeated
 * identical questions avoid Bedrock round-trips. The cache is configured in
 * {@link CacheConfig}.
 *
 * <p>Resilience4j {@code @Retry} and {@code @CircuitBreaker} wrap the Bedrock call.
 * Configuration lives in {@code application.yml} under
 * {@code resilience4j.retry.instances.bedrock-embedding} and
 * {@code resilience4j.circuitbreaker.instances.bedrock-embedding}.
 *
 * <p>The {@code dimensions()} method returns the fixed output dimensionality
 * of Titan Embed Text v2 (1 024). If the model ID is changed to a different
 * Titan variant, this value must be updated accordingly.
 */
@Slf4j
@RequiredArgsConstructor
public class TitanEmbeddingAdapter implements EmbeddingModel {

    private final BedrockRuntimeClient bedrock;
    private final ObjectMapper mapper;
    private final AwsProperties awsProperties;

    // ── EmbeddingModel interface ───────────────────────────────────────────────

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;
        for (String text : request.getInstructions()) {
            float[] vector = embedText(text);
            embeddings.add(new Embedding(vector, index++));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        String content = document.getText();
        if (content == null || content.isBlank()) {
            log.warn("Received blank document content for embedding; returning empty vector");
            return new float[0];
        }
        return embedText(content);
    }

    @Override
    public int dimensions() {
        // Titan Embed Text v2 fixed output dimensionality.
        return 1024;
    }

    // ── Public method used by QdrantRetrieverService ───────────────────────────

    /**
     * Embeds a single text string and returns the vector as {@code List<Float>}.
     * Results are Caffeine-cached keyed by a SHA-256 digest of the input.
     */
    @Cacheable(value = CacheConfig.EMBEDDING_CACHE, key = "#root.target.sha256(#text)")
    public List<Float> embedAsList(String text) {
        float[] vector = embedText(text);
        List<Float> result = new ArrayList<>(vector.length);
        for (float v : vector) {
            result.add(v);
        }
        return result;
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    @Retry(name = "bedrock-embedding", fallbackMethod = "embeddingFallback")
    @CircuitBreaker(name = "bedrock-embedding", fallbackMethod = "embeddingFallback")
    float[] embedText(String text) {
        try {
            String body = mapper.writeValueAsString(Map.of("inputText", text));

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(awsProperties.embeddingModelId())
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(body))
                    .build();

            InvokeModelResponse response = bedrock.invokeModel(request);
            JsonNode json = mapper.readTree(response.body().asUtf8String());
            JsonNode embeddingNode = json.get("embedding");

            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = embeddingNode.get(i).floatValue();
            }
            return vector;

        } catch (Exception e) {
            throw new EmbeddingException("Titan embedding call failed", e);
        }
    }

    @SuppressWarnings("unused") // called by Resilience4j fallback
    float[] embeddingFallback(String text, Throwable cause) {
        log.error("Embedding fallback triggered for text length={}. Cause: {}",
                text.length(), cause.getMessage());
        throw new EmbeddingException(
                "Embedding service is temporarily unavailable after retries", cause);
    }

    /**
     * Used as the Caffeine cache key: SHA-256 hex of the text.
     * Public so that the SpEL expression in {@code @Cacheable} can reference it.
     */
    public String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            // SHA-256 is always available in the JVM; this branch is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
