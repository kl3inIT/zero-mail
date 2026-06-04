package com.zeromail.core.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for integration tests that require a real Postgres 18 instance with the Liquibase
 * schema applied. Subclasses inherit the container plus dynamic datasource properties; Spring
 * Boot's Liquibase auto-config does the schema push on context start.
 *
 * <p>Singleton container pattern: one container started once at static init, shared across all
 * subclass test classes for the JVM lifetime. Avoids per-class container recycling that leaves
 * Spring's cached test context bound to a now-stale dynamic port.
 *
 * <p>Requires Docker on the host.
 */
@SpringBootTest(classes = ZeroMailCoreTestApplication.class)
public abstract class PostgresContainerTest {

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES =
                new PostgreSQLContainer<>("postgres:18.4")
                        .withDatabaseName("zeromail_test")
                        // CI test suite caches many @SpringBootTest contexts in parallel — each
                        // one holds a HikariCP pool of ~30 connections (see hikari override
                        // below). The default Postgres max_connections=100 is enough for ~3
                        // contexts. Bump to 500 so ~15 cached contexts × 30 pool fit with room
                        // for the container's own bookkeeping.
                        .withCommand("postgres", "-c", "max_connections=500");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        // Cap HikariCP per-context so the test-context cache (many cached contexts) does not
        // exhaust the shared container's connection pool. 30 leaves headroom for the
        // concurrent-load tests (LlmGatewayByokRoutingTest.multitenant_no_key_leak fires ~99
        // virtual threads → queues briefly then drains) while still letting ~15 cached
        // contexts fit under max_connections=500.
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
        r.add("spring.datasource.hikari.minimum-idle", () -> "0");
        r.add("spring.liquibase.enabled", () -> "true");
        r.add(
                "spring.liquibase.change-log",
                () -> "classpath:db/changelog/db.changelog-master.yaml");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.session.store-type", () -> "none");
        r.add(
                "spring.autoconfigure.exclude",
                () ->
                        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration,"
                                + "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration,"
                                + "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration");
        // Test-only AES-256 key (32 zero bytes, base64-encoded). RefreshTokenCryptoConfig
        // requires this to construct the cipher bean during context boot.
        r.add(
                "zero-mail.crypto.refresh-token-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        // Test-only keys for the Gmail inbox projection cipher (Phase A wave 0).
        r.add(
                "zero-mail.crypto.inbox-projection-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        r.add(
                "zero-mail.crypto.inbox-projection-sender-hash-key-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        r.add(
                "zero-mail.admin.audit.hmac-kek-base64",
                () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        // GmailApiClientFactory is in backend/core and is constructed by this test context.
        r.add(
                "spring.security.oauth2.client.registration.google.client-id",
                () -> "test-google-client");
        r.add(
                "spring.security.oauth2.client.registration.google.client-secret",
                () -> "test-google-secret");
        // Spring AI starters are on the core classpath in Phase 02C. The gateway adapter is not
        // exercised by these persistence tests, but auto-configuration still needs placeholder
        // keys.
        r.add("spring.ai.openai.api-key", () -> "test-openai-key");
        r.add("spring.ai.anthropic.api-key", () -> "test-anthropic-key");
        r.add("spring.ai.google.genai.api-key", () -> "test-google-genai-key");
        r.add("spring.ai.model.embedding", () -> "none");
        r.add("spring.ai.model.embedding.text", () -> "none");
        r.add("spring.ai.model.embedding.multimodal", () -> "none");
        r.add("spring.ai.deepseek.api-key", () -> "test-deepseek-key");
        r.add("zero-mail.llm.platform.api-key", () -> "test-platform-llm-key");
    }
}
