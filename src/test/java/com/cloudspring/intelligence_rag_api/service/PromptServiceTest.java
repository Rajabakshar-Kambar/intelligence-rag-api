package com.cloudspring.intelligence_rag_api.service;

import com.cloudspring.intelligence_rag_api.config.RagProperties;
import com.cloudspring.intelligence_rag_api.dto.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptServiceTest {

    private PromptService promptService;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties(
                "company-documents",
                5, 0.60f, 2, 4, 0.60f, 6000
        );
        promptService = new PromptService(props);
        // Simulate @PostConstruct since we're not in a Spring context.
        // The template file is read from the classpath.
        promptService.loadTemplate();
    }

    @Test
    void sanitizeQuestion_wrapsInBoundaryTags() {
        String result = promptService.sanitizeQuestion("What is the leave policy?");
        assertThat(result).startsWith("<user_question>");
        assertThat(result).endsWith("</user_question>");
        assertThat(result).contains("What is the leave policy?");
    }

    @Test
    void sanitizeQuestion_stripsLeadingTrailingWhitespace() {
        String result = promptService.sanitizeQuestion("   trim me   ");
        assertThat(result).contains("trim me");
        assertThat(result).doesNotContain("   trim me   ");
    }

    @Test
    void buildContext_includesDocumentMetadata() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .documentName("policy.pdf")
                .versionId("v3")
                .chunkIndex(1)
                .content("Employees receive 20 days of annual leave.")
                .score(0.88f)
                .build();

        String context = promptService.buildContext(List.of(chunk));

        assertThat(context).contains("policy.pdf");
        assertThat(context).contains("v3");
        assertThat(context).contains("1");
        assertThat(context).contains("Employees receive 20 days of annual leave.");
    }

    @Test
    void buildContext_respectsTokenBudget() {
        // Create chunks whose combined content exceeds the budget.
        // Budget = 6000 tokens × 4 chars = 24 000 chars.
        String largeContent = "x".repeat(20_000);

        RetrievedChunk chunk1 = RetrievedChunk.builder()
                .documentName("doc1.pdf")
                .versionId("v1")
                .chunkIndex(0)
                .content(largeContent)
                .score(0.90f)
                .build();

        RetrievedChunk chunk2 = RetrievedChunk.builder()
                .documentName("doc2.pdf")
                .versionId("v1")
                .chunkIndex(0)
                .content(largeContent)
                .score(0.85f)
                .build();

        String context = promptService.buildContext(List.of(chunk1, chunk2));

        // Only chunk1 should fit; chunk2 pushes over budget.
        assertThat(context).contains("doc1.pdf");
        assertThat(context).doesNotContain("doc2.pdf");
    }

    @Test
    void buildContext_whenAllChunksBlank_returnsEmpty() {
        RetrievedChunk blank = RetrievedChunk.builder()
                .documentName("doc.pdf")
                .versionId("v1")
                .chunkIndex(0)
                .content("   ")
                .score(0.80f)
                .build();

        String context = promptService.buildContext(List.of(blank));
        assertThat(context).isEmpty();
    }

    @Test
    void buildPrompt_containsContextAndQuestion() {
        RetrievedChunk chunk = RetrievedChunk.builder()
                .documentName("hr.pdf")
                .versionId("v1")
                .chunkIndex(0)
                .content("HR policies are documented here.")
                .score(0.75f)
                .build();

        String prompt = promptService.buildPrompt("What is HR?", List.of(chunk));

        assertThat(prompt).contains("HR policies are documented here.");
        assertThat(prompt).contains("<user_question>");
        assertThat(prompt).contains("What is HR?");
    }
}