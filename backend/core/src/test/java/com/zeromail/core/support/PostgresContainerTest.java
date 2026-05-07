package com.zeromail.core.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for integration tests that require a real Postgres 17 instance with the Liquibase
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
    POSTGRES = new PostgreSQLContainer<>("postgres:17.6").withDatabaseName("zeromail_test");
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
    r.add("spring.session.store-type", () -> "none");
    r.add(
        "spring.autoconfigure.exclude",
        () ->
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration");
    // Test-only AES-256 key (32 zero bytes, base64-encoded). RefreshTokenCryptoConfig
    // requires this to construct the cipher bean during context boot.
    r.add(
        "zeromail.crypto.refresh-token-key-base64",
        () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    // GmailApiClientFactory is in backend/core and is constructed by this test context.
    r.add(
        "spring.security.oauth2.client.registration.google.client-id", () -> "test-google-client");
    r.add(
        "spring.security.oauth2.client.registration.google.client-secret",
        () -> "test-google-secret");
    // ZeroMailCoreProperties.billing is loaded by core billing tests and requires SePay settings.
    r.add("zeromail.billing.sepay.webhook-api-key", () -> "test-sepay-key-fixture");
    r.add("zeromail.billing.vnd-per-credit", () -> "1000");
    r.add("zeromail.billing.max-pending-intents-per-tenant", () -> "5");
    r.add("zeromail.billing.intent-expiry", () -> "PT24H");
    // Spring AI starters are on the core classpath in Phase 02C. The gateway adapter is not
    // exercised by these persistence tests, but auto-configuration still needs placeholder keys.
    r.add("spring.ai.openai.api-key", () -> "test-openai-key");
    r.add("spring.ai.anthropic.api-key", () -> "test-anthropic-key");
  }
}
