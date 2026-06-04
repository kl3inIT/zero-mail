package com.zeromail.api.support;

import com.zeromail.api.ZeroMailApiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for API-side integration tests. Starts a singleton Postgres 18.4 container, applies the
 * Liquibase schema, and disables redis-backed session in favor of an in-memory MapSessionRepository
 * contributed by TestSessionConfig — so Spring Security session resolution still works end-to-end
 * without requiring a live redis at test time.
 */
@SpringBootTest(
        classes = ZeroMailApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiPostgresTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES;

    @Autowired private JdbcTemplate jdbcTemplate;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:18.4").withDatabaseName("zeromail_api_test");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        dynamicPropertyRegistry.add("spring.datasource.username", POSTGRES::getUsername);
        dynamicPropertyRegistry.add("spring.datasource.password", POSTGRES::getPassword);
        dynamicPropertyRegistry.add("spring.liquibase.enabled", () -> "true");
        dynamicPropertyRegistry.add(
                "spring.liquibase.change-log",
                () -> "classpath:db/changelog/db.changelog-master.yaml");
        dynamicPropertyRegistry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        dynamicPropertyRegistry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
        dynamicPropertyRegistry.add("spring.datasource.hikari.minimum-idle", () -> "0");
        // Session: still routed through Spring Session, but backed by an in-memory
        // MapSessionRepository
        // contributed by TestSessionConfig instead of redis. SessionAutoConfiguration sees the bean
        // and
        // skips redis lookup. Cookie name remains ZEROMAIL_SESSION from application.yml.
        dynamicPropertyRegistry.add(
                "spring.autoconfigure.exclude",
                () ->
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.session.RedisSessionAutoConfiguration");
        dynamicPropertyRegistry.add("spring.session.store-type", () -> "none");
        // Stub Google OAuth client registrations so OAuth2 client autoconfig picks up no-op values
        // (we never actually contact Google in tests).
        dynamicPropertyRegistry.add(
                "spring.security.oauth2.client.registration.google.client-id",
                () -> "test-google-client");
        dynamicPropertyRegistry.add(
                "spring.security.oauth2.client.registration.google.client-secret",
                () -> "test-google-secret");
        // Phase 01.5: google-gmail registration deleted — single bundled google registration only.
        // Test-only AES-256 key (32 zero bytes, base64-encoded).
        dynamicPropertyRegistry.add(
                "zero-mail.crypto.refresh-token-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        dynamicPropertyRegistry.add(
                "zero-mail.crypto.inbox-projection-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        dynamicPropertyRegistry.add(
                "zero-mail.crypto.inbox-projection-sender-hash-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        dynamicPropertyRegistry.add(
                "zero-mail.admin.audit.hmac-kek-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        dynamicPropertyRegistry.add(
                "zero-mail.api.gmail.pubsub.push-audience-url",
                () -> "https://test.example/internal/pubsub/gmail");
        dynamicPropertyRegistry.add(
                "zero-mail.api.gmail.pubsub.sa-principal-email",
                () -> "pubsub-sa@test-project.iam.gserviceaccount.com");
        // Spring AI starters are present transitively in API test contexts after Phase 02C wiring.
        // The
        // gateway itself is not exercised here, but model auto-configuration still requires keys.
        dynamicPropertyRegistry.add("spring.ai.openai.api-key", () -> "test-openai-key");
        dynamicPropertyRegistry.add("spring.ai.anthropic.api-key", () -> "test-anthropic-key");
        dynamicPropertyRegistry.add(
                "zero-mail.llm.platform.api-key", () -> "test-platform-llm-key");
    }

    @BeforeEach
    void cleanBillingTables() {
        jdbcTemplate.execute(
                """
        TRUNCATE TABLE
          credit_ledger_entry,
          credit_reservation
        RESTART IDENTITY CASCADE
        """);
    }
}
