package com.cloudspring.intelligence_rag_api.service;

import com.cloudspring.intelligence_rag_api.config.RagProperties;
import com.cloudspring.intelligence_rag_api.dto.RetrievedChunk;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the final prompt submitted to the LLM.
 *
 * <h3>Prompt injection mitigation</h3>
 * The user question is wrapped in explicit XML-style delimiters
 * ({@code <user_question>…</user_question>}). The system prompt instructs
 * the model to treat everything inside those tags as untrusted user input.
 * This does not make injection impossible, but it meaningfully raises the
 * bar: the model must be convinced to break out of a labelled boundary.
 *
 * <h3>Token budget</h3>
 * Titan Embed v2 and Claude 3 Haiku both have large context windows, but
 * very long context increases cost and latency. The budget is enforced by
 * measuring the approximate token count of each chunk ({@code chars / 4})
 * and stopping once the budget is exhausted. Chunks are added in
 * descending relevance-score order, so the most relevant content is always
 * included.
 *
 * <h3>Template management</h3>
 * The prompt template lives in {@code resources/promptTemplates/hr-assistant.st}
 * and is loaded once at startup. The template uses two named placeholders:
 * {@code {CONTEXT}} and {@code {QUESTION}}. If the template file changes,
 * the service must be restarted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptService {

    private static final String TEMPLATE_PATH = "promptTemplates/hr-assistant.st";
    private static final String CONTEXT_PLACEHOLDER = "{CONTEXT}";
    private static final String QUESTION_PLACEHOLDER = "{QUESTION}";

    // Rough chars-per-token estimate for English prose.
    private static final int CHARS_PER_TOKEN = 4;

    private final RagProperties ragProperties;

    private String promptTemplate;

    @PostConstruct
    void loadTemplate() {
        try (InputStream is = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            promptTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.info("[PromptService] Loaded prompt template from {}", TEMPLATE_PATH);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load prompt template from classpath: " + TEMPLATE_PATH, e);
        }
    }

    /**
     * Assembles the full prompt for the LLM.
     *
     * @param question the raw user question (will be wrapped in injection-boundary tags)
     * @param chunks   retrieved chunks in descending score order
     * @return fully assembled prompt string
     */
    public String buildPrompt(String question, List<RetrievedChunk> chunks) {
        String context = buildContext(chunks);
        String safeQuestion = sanitizeQuestion(question);
        return promptTemplate
                .replace(CONTEXT_PLACEHOLDER, context)
                .replace(QUESTION_PLACEHOLDER, safeQuestion);
    }

    /**
     * Assembles the context block from retrieved chunks, respecting the token budget.
     * Chunks are included in score-descending order until the budget is exhausted.
     */
    String buildContext(List<RetrievedChunk> chunks) {
        int budgetChars = ragProperties.maxContextTokens() * CHARS_PER_TOKEN;
        int usedChars = 0;

        List<RetrievedChunk> selected = new ArrayList<>();
        for (RetrievedChunk chunk : chunks) {
            String content = chunk.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (usedChars + content.length() > budgetChars) {
                log.debug("[PromptService] Token budget ({} tokens) reached after {} chunks",
                        ragProperties.maxContextTokens(), selected.size());
                break;
            }
            selected.add(chunk);
            usedChars += content.length();
        }

        StringBuilder sb = new StringBuilder();
        for (RetrievedChunk chunk : selected) {
            sb.append("[SOURCE: ")
                    .append(nullSafe(chunk.getDocumentName()))
                    .append(" | version: ")
                    .append(nullSafe(chunk.getVersionId()))
                    .append(" | chunk: ")
                    .append(chunk.getChunkIndex())
                    .append("]\n")
                    .append(chunk.getContent())
                    .append("\n\n---\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * Wraps the user question in injection-boundary delimiters.
     * Leading/trailing whitespace is stripped to prevent whitespace-based bypass attempts.
     */
    String sanitizeQuestion(String question) {
        return "<user_question>\n"
                + question.strip()
                + "\n</user_question>";
    }

    private String nullSafe(String value) {
        return value != null ? value : "unknown";
    }
}