package com.cloudspring.intelligence_rag_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Optional CORS configuration — only needed if a browser-based frontend
 * (e.g. a demo UI for interviews) calls this API directly instead of
 * server-to-server. Not required for curl/Postman/service-to-service usage.
 *
 * <p>{@code app.cors.allowed-origins} is a comma-separated list, empty by
 * default so CORS stays fully locked down unless explicitly configured.
 * Never use "*" together with credentials in production.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        if (!allowedOrigins.isBlank()) {
            config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        }
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Api-Key"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
