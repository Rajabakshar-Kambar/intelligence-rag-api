package com.cloudspring.intelligence_rag_api.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.rag")
@Validated
public record RagProperties(

        @NotBlank
        String collectionName,

        @Min(1) @Max(20)
        int topK,

        @DecimalMin("0.20") @DecimalMax("1.00")
        float minScore,

        @Min(1) @Max(10)
        int fallbackTopK,

        @Min(1) @Max(10)
        int maxContextChunks,

        @DecimalMin("0.20") @DecimalMax("1.00")
        float minAnswerableScore,

        // Maximum approximate token budget for the assembled context block.
        // One token ≈ 4 characters; Claude 3 Haiku context window is 200k tokens.
        // A conservative default of 6000 tokens (~24 000 chars) leaves ample
        // room for the system prompt and the model's response.
        @Min(1000) @Max(50000)
        int maxContextTokens
) {
}
