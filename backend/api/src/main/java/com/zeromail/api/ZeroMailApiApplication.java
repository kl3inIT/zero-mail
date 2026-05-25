package com.zeromail.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "zero-mail")
@SpringBootApplication(
        scanBasePackages = {"com.zeromail.api", "com.zeromail.core"},
        excludeName = {
            "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration",
            "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration"
        })
@ConfigurationPropertiesScan(basePackages = "com.zeromail")
@EntityScan(basePackages = "com.zeromail.core")
@EnableJpaRepositories(basePackages = "com.zeromail.core")
public class ZeroMailApiApplication {
    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ZeroMailApiApplication.class, args);
    }
}
