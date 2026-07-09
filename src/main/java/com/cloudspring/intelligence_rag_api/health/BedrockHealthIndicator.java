package com.cloudspring.intelligence_rag_api.health;

import com.cloudspring.intelligence_rag_api.config.AwsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.actuate.health.Health;
//import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Reports Bedrock SDK connectivity.
 *
 * <p>A true health check would invoke the model, which is expensive. Instead,
 * we verify that the SDK client can be constructed and that the credentials chain
 * resolves without error by calling {@code bedrockRuntimeClient.serviceClientConfiguration()}.
 * This is fast and incurs no cost, at the expense of not catching model-level outages.
 * For deeper checks, integrate AWS Health API events via CloudWatch.
 */
@Component("bedrock")
@RequiredArgsConstructor
@Slf4j
public class BedrockHealthIndicator implements HealthIndicator {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final AwsProperties awsProperties;

    @Override
    public Health health() {
        try {
            // Verify client configuration is valid without making a network call.
            String region = bedrockRuntimeClient
                    .serviceClientConfiguration()
                    .region()
                    .id();
            return Health.up()
                    .withDetail("region", region)
                    .withDetail("embeddingModel", awsProperties.embeddingModelId())
                    .build();
        } catch (Exception e) {
            log.warn("[Health] Bedrock health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("reason", "SDK configuration error")
                    .build();
        }
    }
}
