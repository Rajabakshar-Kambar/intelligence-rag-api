package com.cloudspring.intelligence_rag_api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    /**
     * Cache name used by {@code @Cacheable} on the embedding service.
     * Keyed by SHA-256 of the input text; TTL of 1 hour prevents stale embeddings
     * after model updates. Max size of 10 000 entries caps heap usage at roughly
     * 10 000 × 1 024 floats × 4 bytes = ~40 MB.
     */
    public static final String EMBEDDING_CACHE = "embeddings";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(EMBEDDING_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats());
        return manager;
    }
}
