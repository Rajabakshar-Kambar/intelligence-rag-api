package com.cloudspring.intelligence_rag_api.service;

import com.cloudspring.intelligence_rag_api.config.RagProperties;
import com.cloudspring.intelligence_rag_api.dto.RetrievedChunk;
import com.cloudspring.intelligence_rag_api.embedding.TitanEmbeddingAdapter;
import com.cloudspring.intelligence_rag_api.exception.RetrievalException;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points.ScoredPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QdrantRetrieverServiceTest {

    @Mock
    private QdrantClient qdrantClient;
    @Mock
    private TitanEmbeddingAdapter embeddingAdapter;

    private RagProperties ragProperties;
    private QdrantRetrieverService retrieverService;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties(
                "company-documents",
                5,      // topK
                0.60f,  // minScore
                2,      // fallbackTopK
                4,      // maxContextChunks
                0.60f,  // minAnswerableScore
                6000    // maxContextTokens
        );
        retrieverService = new QdrantRetrieverService(
                qdrantClient, embeddingAdapter, ragProperties);
    }

    /*@Test
    void retrieve_filtersChunksBelowMinScore() throws Exception {
        when(embeddingAdapter.embedAsList(any()))
                .thenReturn(List.of(0.1f, 0.2f, 0.3f));

        ScoredPoint highScore = buildScoredPoint("policy.pdf", "v1", 0, "content A", 0.85f);
        ScoredPoint lowScore  = buildScoredPoint("other.pdf",  "v1", 0, "content B", 0.30f);

        when(qdrantClient.searchAsync(any()))
                .thenReturn((ListenableFuture<List<ScoredPoint>>) CompletableFuture.completedFuture(List.of(highScore, lowScore)));

        List<RetrievedChunk> results = retrieverService.retrieve("vacation policy");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentName()).isEqualTo("policy.pdf");
        assertThat(results.get(0).getScore()).isGreaterThanOrEqualTo(0.60f);
    }*/

    @Test
    void retrieve_sortsResultsByScoreDescending() throws Exception {
        when(embeddingAdapter.embedAsList(any()))
                .thenReturn(List.of(0.1f, 0.2f));

        ScoredPoint medScore  = buildScoredPoint("doc1.pdf", "v1", 0, "medium", 0.70f);
        ScoredPoint highScore = buildScoredPoint("doc2.pdf", "v1", 0, "high",   0.90f);
        ScoredPoint lowScore  = buildScoredPoint("doc3.pdf", "v1", 0, "pass",   0.65f);

        when(qdrantClient.searchAsync(any()))
                .thenReturn((ListenableFuture<List<ScoredPoint>>) CompletableFuture.completedFuture(
                        List.of(medScore, highScore, lowScore)));

        List<RetrievedChunk> results = retrieverService.retrieve("test");

        assertThat(results).extracting(RetrievedChunk::getScore)
                .containsExactly(0.90f, 0.70f, 0.65f);
    }

    @Test
    void retrieve_respectsMaxContextChunksLimit() throws Exception {
        when(embeddingAdapter.embedAsList(any()))
                .thenReturn(List.of(0.1f));

        // Create 6 points all above min-score; maxContextChunks = 4
        List<ScoredPoint> points = List.of(
                buildScoredPoint("doc1.pdf", "v1", 0, "c1", 0.95f),
                buildScoredPoint("doc2.pdf", "v1", 1, "c2", 0.92f),
                buildScoredPoint("doc3.pdf", "v1", 2, "c3", 0.88f),
                buildScoredPoint("doc4.pdf", "v1", 3, "c4", 0.84f),
                buildScoredPoint("doc5.pdf", "v1", 4, "c5", 0.80f),
                buildScoredPoint("doc6.pdf", "v1", 5, "c6", 0.76f)
        );
        when(qdrantClient.searchAsync(any()))
                .thenReturn((ListenableFuture<List<ScoredPoint>>) CompletableFuture.completedFuture(points));

        List<RetrievedChunk> results = retrieverService.retrieve("test");

        assertThat(results).hasSize(4); // capped by maxContextChunks
    }

   /* @Test
    void retrieve_whenQdrantThrows_wrapsInRetrievalException() {
        when(embeddingAdapter.embedAsList(any()))
                .thenReturn(List.of(0.1f));
        when(qdrantClient.searchAsync(any()))
                .thenReturn((ListenableFuture<List<ScoredPoint>>) CompletableFuture.failedFuture(
                        new RuntimeException("connection refused")));

        assertThatThrownBy(() -> retrieverService.retrieve("test"))
                .isInstanceOf(RetrievalException.class)
                .hasMessageContaining("Qdrant search failed");
    }*/

    /*@Test
    void retrieve_whenNoResultsAboveThreshold_returnsEmptyList() throws Exception {
        when(embeddingAdapter.embedAsList(any()))
                .thenReturn(List.of(0.1f));

        ScoredPoint low1 = buildScoredPoint("doc1.pdf", "v1", 0, "text", 0.20f);
        ScoredPoint low2 = buildScoredPoint("doc2.pdf", "v1", 0, "text", 0.10f);

        when(qdrantClient.searchAsync(any()))
                .thenReturn((ListenableFuture<List<ScoredPoint>>) CompletableFuture.completedFuture(List.of(low1, low2)));

        List<RetrievedChunk> results = retrieverService.retrieve("obscure question");

        assertThat(results).isEmpty();
    }*/

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScoredPoint buildScoredPoint(String docName, String versionId,
                                         int chunkIndex, String content,
                                         float score) {
        Map<String, JsonWithInt.Value> payload = Map.of(
                "documentName", JsonWithInt.Value.newBuilder()
                        .setStringValue(docName).build(),
                "versionId", JsonWithInt.Value.newBuilder()
                        .setStringValue(versionId).build(),
                "chunkIndex", JsonWithInt.Value.newBuilder()
                        .setIntegerValue(chunkIndex).build(),
                "content", JsonWithInt.Value.newBuilder()
                        .setStringValue(content).build()
        );
        return ScoredPoint.newBuilder()
                .putAllPayload(payload)
                .setScore(score)
                .build();
    }
}