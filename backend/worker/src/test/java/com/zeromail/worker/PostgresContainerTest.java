package com.zeromail.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.worker.test.MockGmailHistoryServer;

@SpringBootTest(classes = ZeroMailWorkerApplication.class)
public abstract class PostgresContainerTest {

  protected static final PostgreSQLContainer<?> POSTGRES;
  protected static final MockGmailHistoryServer GMAIL;

  @Autowired private RefreshTokenCipher cipher;

  static {
    POSTGRES = new PostgreSQLContainer<>("postgres:17.6").withDatabaseName("zeromail_worker_test");
    POSTGRES.start();
    GMAIL = new MockGmailHistoryServer();
    try {
      GMAIL.start();
    } catch (IOException ioException) {
      throw new ExceptionInInitializerError(ioException);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry dynamicPropertyRegistry) {
    dynamicPropertyRegistry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    dynamicPropertyRegistry.add("spring.datasource.username", POSTGRES::getUsername);
    dynamicPropertyRegistry.add("spring.datasource.password", POSTGRES::getPassword);
    dynamicPropertyRegistry.add("spring.liquibase.enabled", () -> "true");
    dynamicPropertyRegistry.add(
        "spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.yaml");
    dynamicPropertyRegistry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    dynamicPropertyRegistry.add(
        "spring.security.oauth2.client.registration.google.client-id", () -> "test-google-client");
    dynamicPropertyRegistry.add(
        "spring.security.oauth2.client.registration.google.client-secret",
        () -> "test-google-secret");
    dynamicPropertyRegistry.add(
        "zeromail.crypto.refresh-token-key-base64",
        () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    dynamicPropertyRegistry.add("zeromail.gmail.pubsub.topic-name", () -> "projects/test/topics/gmail");
    dynamicPropertyRegistry.add("zeromail.gmail.api-root-url", GMAIL::baseUrl);
    dynamicPropertyRegistry.add("zeromail.gmail.oauth-token-url", () -> GMAIL.baseUrl() + "token");
    dynamicPropertyRegistry.add("zeromail.billing.sepay.webhook-api-key", () -> "test-sepay-key-fixture");
    dynamicPropertyRegistry.add("zeromail.billing.vnd-per-credit", () -> "1000");
    dynamicPropertyRegistry.add("zeromail.billing.max-pending-intents-per-tenant", () -> "5");
    dynamicPropertyRegistry.add("zeromail.billing.intent-expiry", () -> "PT24H");
    // Spring AI starters are on the worker classpath in Phase 02C. Worker tests do not exercise
    // the gateway adapter yet, but auto-configuration still requires placeholder keys.
    dynamicPropertyRegistry.add("spring.ai.openai.api-key", () -> "test-openai-key");
    dynamicPropertyRegistry.add("spring.ai.anthropic.api-key", () -> "test-anthropic-key");
    dynamicPropertyRegistry.add("zero-mail.llm.platform.api-key", () -> "test-platform-llm-key");
  }

  protected byte[] encryptedRefreshToken(UUID tenantId) {
    return cipher.encrypt(
        "worker-refresh-token".getBytes(StandardCharsets.UTF_8), tenantId.toString());
  }
}
