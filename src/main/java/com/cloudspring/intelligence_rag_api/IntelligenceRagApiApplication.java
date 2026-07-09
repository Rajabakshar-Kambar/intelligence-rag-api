package com.cloudspring.intelligence_rag_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;


@SpringBootApplication
@EnableCaching
@ConfigurationPropertiesScan
public class IntelligenceRagApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntelligenceRagApiApplication.class, args);
	}

}
