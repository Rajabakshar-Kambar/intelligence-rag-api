package com.cloudspring.intelligence_rag_api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.aws")
@Validated
public record AwsProperties(

        @NotBlank
        String region,

        @NotBlank
        String embeddingModelId,

        // Timeout in seconds for a single Bedrock API call attempt.
        int apiCallAttemptTimeoutSeconds,

        // Timeout in seconds for the total Bedrock API call including retries.
        int apiCallTimeoutSeconds
) {
}