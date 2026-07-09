package com.cloudspring.intelligence_rag_api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.time.Duration;

/**
 * AWS SDK configuration.
 *
 * <p>Credentials are resolved at runtime via {@link DefaultCredentialsProvider}, which checks
 * the standard AWS credential chain in order:
 * <ol>
 *   <li>Environment variables (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY)</li>
 *   <li>Java system properties</li>
 *   <li>~/.aws/credentials profile file</li>
 *   <li>EC2/ECS/Lambda IAM instance/task role (recommended for production)</li>
 * </ol>
 * Never set explicit access-key / secret-key in application properties for production.
 * Use an IAM role attached to the compute resource instead.
 */
@Configuration
@RequiredArgsConstructor
public class AwsConfig {

    private final AwsProperties awsProperties;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(awsProperties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(
                                Duration.ofSeconds(awsProperties.apiCallAttemptTimeoutSeconds()))
                        .apiCallTimeout(
                                Duration.ofSeconds(awsProperties.apiCallTimeoutSeconds()))
                        .build())
                .build();
    }
}
