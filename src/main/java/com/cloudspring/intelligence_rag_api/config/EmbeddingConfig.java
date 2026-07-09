package com.cloudspring.intelligence_rag_api.config;

import com.cloudspring.intelligence_rag_api.embedding.TitanEmbeddingAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class EmbeddingConfig {

    /**
     * Registers TitanEmbeddingAdapter as a first-class Spring bean under its
     * concrete type. Spring will apply AOP proxies for @Cacheable, @Retry, and
     * @CircuitBreaker because it manages this object's lifecycle directly.
     *
     * QdrantRetrieverService injects TitanEmbeddingAdapter by concrete type,
     * so this registration satisfies that dependency.
     */
    @Bean
    public TitanEmbeddingAdapter titanEmbeddingAdapter(BedrockRuntimeClient bedrock,
                                                       ObjectMapper mapper,
                                                       AwsProperties awsProperties) {
        return new TitanEmbeddingAdapter(bedrock, mapper, awsProperties);
    }

    /**
     * Exposes the same TitanEmbeddingAdapter instance as the primary EmbeddingModel
     * for Spring AI's Qdrant vector store and any other Spring AI components that
     * inject EmbeddingModel by interface.
     *
     * Accepting TitanEmbeddingAdapter as a parameter here causes Spring to inject
     * the already-created (and already-proxied) bean, so both bean names point to
     * the same proxied instance.
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel(TitanEmbeddingAdapter titanEmbeddingAdapter) {
        return titanEmbeddingAdapter;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
