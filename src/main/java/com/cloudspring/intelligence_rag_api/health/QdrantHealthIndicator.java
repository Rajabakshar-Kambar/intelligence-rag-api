package com.cloudspring.intelligence_rag_api.health;

import io.qdrant.client.QdrantClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.actuate.health.Health;
//import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports Qdrant connectivity to Spring Boot Actuator.
 *
 * <p>Calls {@code listCollections()} which is a lightweight metadata-only RPC.
 * The result is not used — only whether the call succeeds within a short timeout.
 */
@Component("qdrant")
@RequiredArgsConstructor
@Slf4j
public class QdrantHealthIndicator implements HealthIndicator {

    private final QdrantClient qdrantClient;

    @Override
    public Health health() {
        try {
            qdrantClient.listCollectionsAsync().get();
            return Health.up().withDetail("store", "qdrant").build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down()
                    .withDetail("store", "qdrant")
                    .withDetail("reason", "interrupted")
                    .build();
        } catch (Exception e) {
            log.warn("[Health] Qdrant health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("store", "qdrant")
                    .withDetail("reason", "unreachable")
                    .build();
        }
    }
}
