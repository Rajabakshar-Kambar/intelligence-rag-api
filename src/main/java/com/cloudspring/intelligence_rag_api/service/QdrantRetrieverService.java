package com.cloudspring.intelligence_rag_api.service;

import com.cloudspring.intelligence_rag_api.config.RagProperties;
import com.cloudspring.intelligence_rag_api.dto.RetrievedChunk;
import com.cloudspring.intelligence_rag_api.embedding.TitanEmbeddingAdapter;
import com.cloudspring.intelligence_rag_api.exception.CircuitOpenException;
import com.cloudspring.intelligence_rag_api.exception.RetrievalException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Retrieves semantically similar document chunks from Qdrant.
 *
 * <p>The retrieval pipeline:
 * <ol>
 *   <li>Embed the user's question via {@link TitanEmbeddingAdapter} (cached).</li>
 *   <li>Issue an ANN search against Qdrant.</li>
 *   <li>Filter results below {@code app.rag.min-score} (default 0.60).</li>
 *   <li>Return up to {@code app.rag.max-context-chunks} results.</li>
 * </ol>
 *
 * <p>Full chunk content is intentionally logged only at DEBUG level to prevent
 * confidential HR document contents from appearing in INFO/WARN/ERROR log streams.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QdrantRetrieverService {

    private final QdrantClient qdrantClient;
    private final TitanEmbeddingAdapter embeddingAdapter;
    private final RagProperties ragProperties;

    /**
     * Retrieves and scores document chunks relevant to the given question.
     *
     * @param question the user's natural-language question
     * @return filtered, sorted list of retrieved chunks (never null, may be empty)
     */
    @Retry(name = "qdrant", fallbackMethod = "retrievalFallback")
    @CircuitBreaker(name = "qdrant", fallbackMethod = "retrievalFallback")
    public List<RetrievedChunk> retrieve(String question) {
        long start = System.currentTimeMillis();

        List<Float> queryVector = embeddingAdapter.embedAsList(question);

        SearchPoints searchRequest = SearchPoints.newBuilder()
                .setCollectionName(ragProperties.collectionName())
                .addAllVector(queryVector)
                .setLimit(ragProperties.topK())
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                .build();

        List<ScoredPoint> points;
        try {
            points = qdrantClient.searchAsync(searchRequest).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetrievalException("Qdrant search interrupted", e);
        } catch (ExecutionException e) {
            throw new RetrievalException("Qdrant search failed", e.getCause());
        }

        List<RetrievedChunk> filtered = points.stream()
                .map(this::toRetrievedChunk)
                .filter(chunk -> chunk.getScore() != null
                        && chunk.getScore() >= ragProperties.minScore())
                .sorted(Comparator.comparing(RetrievedChunk::getScore).reversed())
                .limit(ragProperties.maxContextChunks())
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        log.info("[Retriever] question_length={}, raw_results={}, filtered={}, "
                        + "min_score={}, elapsed_ms={}",
                question.length(),
                points.size(),
                filtered.size(),
                ragProperties.minScore(),
                elapsed);

        log.info("[Retriever] raw scores: {}", points.stream()
                .map(r -> r.getScore())
                .toList());

        if (log.isDebugEnabled()) {
            filtered.forEach(chunk ->
                    log.debug("[Retriever] doc={}, chunk={}, score={}, preview={}",
                            chunk.getDocumentName(),
                            chunk.getChunkIndex(),
                            chunk.getScore(),
                            preview(chunk.getContent())));
        }

        return filtered;
    }

    // ── Resilience4j fallback ──────────────────────────────────────────────────

    @SuppressWarnings("unused")
    List<RetrievedChunk> retrievalFallback(String question, CallNotPermittedException ex) {
        throw new CircuitOpenException(
                "Qdrant circuit breaker is open; retrieval unavailable");
    }

    @SuppressWarnings("unused")
    List<RetrievedChunk> retrievalFallback(String question, Throwable ex) {
        throw new RetrievalException("Qdrant retrieval failed after retries", ex);
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private RetrievedChunk toRetrievedChunk(ScoredPoint point) {
        Map<String, JsonWithInt.Value> payload = point.getPayloadMap();
        return RetrievedChunk.builder()
                .documentName(getString(payload, "documentName"))
                .versionId(getString(payload, "versionId"))
                .chunkIndex(getInteger(payload, "chunkIndex"))
                .content(getString(payload, "content"))
                .score(point.getScore())
                .build();
    }

    private String getString(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null) {
            return null;
        }
        return value.hasStringValue() ? value.getStringValue() : null;
    }

    private Integer getInteger(Map<String, JsonWithInt.Value> payload, String key) {
        JsonWithInt.Value value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value.hasIntegerValue()) {
            return (int) value.getIntegerValue();
        }
        if (value.hasStringValue()) {
            try {
                return Integer.parseInt(value.getStringValue());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 120 ? content : content.substring(0, 120) + "…";
    }
}
