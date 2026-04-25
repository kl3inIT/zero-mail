package com.zeromail.core.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal @SpringBootApplication entrypoint for backend/core's integration tests.
 * Real Boot apps live in backend/api and backend/worker; this class exists only so that
 * @SpringBootTest can boot a Postgres-backed context against the persistence module.
 *
 * scanBasePackages drives JPA entity discovery and Spring Data repository scanning via
 * Boot's auto-config (@EntityScan / @EnableJpaRepositories are no longer required when the
 * scan base already covers the persistence package).
 */
@SpringBootApplication(scanBasePackages = "com.zeromail.core")
public class CoreTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreTestApplication.class, args);
    }
}
