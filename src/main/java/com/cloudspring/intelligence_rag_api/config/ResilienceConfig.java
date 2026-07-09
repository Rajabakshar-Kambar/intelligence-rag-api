package com.cloudspring.intelligence_rag_api.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds Resilience4j metrics to Micrometer so they appear in /actuator/prometheus.
 * The circuit-breaker and retry instances themselves are configured in application.yml
 * under resilience4j.circuitbreaker / resilience4j.retry.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry) {
        TaggedCircuitBreakerMetrics metrics =
                TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    @Bean
    public TaggedRetryMetrics retryMetrics(
            RetryRegistry retryRegistry,
            MeterRegistry meterRegistry) {
        TaggedRetryMetrics metrics = TaggedRetryMetrics.ofRetryRegistry(retryRegistry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }
}