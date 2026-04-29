package com.zeromail.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.zeromail.api.Application;

/**
 * Base for API-side integration tests. Starts a singleton Postgres 17.6 container, applies
 * the Liquibase schema, and disables redis-backed session in favor of an in-memory
 * MapSessionRepository contributed by TestSessionConfig — so Spring Security session
 * resolution still works end-to-end without requiring a live redis at test time.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiPostgresTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:17.6").withDatabaseName("zeromail_api_test");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.liquibase.enabled", () -> "true");
        r.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.yaml");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // Session: still routed through Spring Session, but backed by an in-memory MapSessionRepository
        // contributed by TestSessionConfig instead of redis. SessionAutoConfiguration sees the bean and
        // skips redis lookup. Cookie name remains ZEROMAIL_SESSION from application.yml.
        r.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.session.RedisSessionAutoConfiguration");
        r.add("spring.session.store-type", () -> "none");
        // Stub Google OAuth client registrations so OAuth2 client autoconfig picks up no-op values
        // (we never actually contact Google in tests).
        r.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-google-client");
        r.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-google-secret");
        // Phase 01.5: google-gmail registration deleted — single bundled google registration only.
        // Test-only AES-256 key (32 zero bytes, base64-encoded).
        r.add("zeromail.crypto.refresh-token-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        r.add("pubsub.push-audience-url", () -> "https://test.example/internal/pubsub/gmail");
        r.add("pubsub.sa-principal-email", () -> "pubsub-sa@test-project.iam.gserviceaccount.com");
    }
}
