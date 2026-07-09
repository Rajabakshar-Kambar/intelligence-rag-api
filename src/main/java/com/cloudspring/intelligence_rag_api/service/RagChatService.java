package com.cloudspring.intelligence_rag_api.service;

import com.cloudspring.intelligence_rag_api.dto.ChatResponse;
import com.cloudspring.intelligence_rag_api.dto.RetrievedChunk;
import com.cloudspring.intelligence_rag_api.dto.SourceInfo;
import com.cloudspring.intelligence_rag_api.exception.LlmException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the RAG pipeline: retrieve → prompt → generate → respond.
 *
 * <p>This service is intentionally thin: each step is delegated to a
 * single-responsibility collaborator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagChatService {

    private final ChatClient chatClient;
    private final QdrantRetrieverService retrieverService;
    private final PromptService promptService;
    private final MeterRegistry meterRegistry;

    /**
     * Processes a user question through the full RAG pipeline.
     *
     * @param question the user's natural-language question
     * @return the model's answer with provenance sources
     */
    @Timed(value = "rag.chat.duration", description = "End-to-end RAG chat latency")
    public ChatResponse chat(String question) {
        List<RetrievedChunk> chunks = retrieverService.retrieve(question);

        if (chunks.isEmpty()) {
            meterRegistry.counter("rag.no_context").increment();
            log.info("[RagChat] No relevant chunks found for question_length={}",
                    question.length());
            return new ChatResponse(
                    "I could not find relevant information for your question in the "
                            + "available documents.",
                    List.of()
            );
        }

        String prompt = promptService.buildPrompt(question, chunks);
        String answer = callLlm(prompt);

        List<SourceInfo> sources = chunks.stream()
                .map(chunk -> SourceInfo.builder()
                        .documentName(chunk.getDocumentName())
                        .versionId(chunk.getVersionId())
                        .chunkIndex(chunk.getChunkIndex())
                        .score(chunk.getScore())
                        .build())
                .toList();

        log.info("[RagChat] Response generated. question_length={}, chunks_used={}, "
                        + "answer_length={}",
                question.length(),
                chunks.size(),
                answer.length());

        return new ChatResponse(answer, sources);
    }

    // ── LLM call with Resilience4j ─────────────────────────────────────────────

    @Retry(name = "bedrock-llm", fallbackMethod = "llmFallback")
    @CircuitBreaker(name = "bedrock-llm", fallbackMethod = "llmFallback")
    String callLlm(String prompt) {
        long start = System.currentTimeMillis();
        try {
            String answer = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.info("[RagChat] LLM call completed in {} ms",
                    System.currentTimeMillis() - start);
            return answer;
        } catch (Exception e) {
            throw new LlmException("Bedrock LLM call failed", e);
        }
    }

    @SuppressWarnings("unused")
    String llmFallback(String prompt, Throwable cause) {
        log.error("[RagChat] LLM fallback triggered. Cause: {}", cause.getMessage());
        throw new LlmException("Language model is unavailable after retries", cause);
    }
}
