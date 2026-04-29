package com.zeromail.core.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal @SpringBootApplication entrypoint for backend/core's integration tests.
 * Real Boot apps live in backend/api and backend/worker; this class exists only so that
 * @SpringBootTest can boot a Postgres-backed context against the persistence module.
 *
 * Explicit @EntityScan / @EnableJpaRepositories are required because the application classes
 * scanned by @SpringBootApplication (e.g. AccountService) inject repositories that live in
 * a sibling package; relying on Boot's auto-config default base package is not enough.
 */
@SpringBootApplication(scanBasePackages = "com.zeromail.core")
@ConfigurationPropertiesScan(basePackages = "com.zeromail.core")
@EntityScan(basePackages = "com.zeromail.core")
@EnableJpaRepositories(basePackages = "com.zeromail.core")
public class ZeroMailCoreTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZeroMailCoreTestApplication.class, args);
    }
}
