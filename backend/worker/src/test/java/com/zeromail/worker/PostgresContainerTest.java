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
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.liquibase.enabled", () -> "true");
    r.add("spring.liquibase.change-log", () -> "classpath:db/changelog/db.changelog-master.yaml");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    r.add(
        "spring.security.oauth2.client.registration.google.client-id", () -> "test-google-client");
    r.add(
        "spring.security.oauth2.client.registration.google.client-secret",
        () -> "test-google-secret");
    r.add(
        "zeromail.crypto.refresh-token-key-base64",
        () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    r.add("zeromail.gmail.pubsub.topic-name", () -> "projects/test/topics/gmail");
    r.add("zeromail.gmail.api-root-url", GMAIL::baseUrl);
    r.add("zeromail.gmail.oauth-token-url", () -> GMAIL.baseUrl() + "token");
    r.add("zeromail.billing.sepay.webhook-api-key", () -> "test-sepay-key-fixture");
    r.add("zeromail.billing.vnd-per-credit", () -> "1000");
    r.add("zeromail.billing.max-pending-intents-per-tenant", () -> "5");
    r.add("zeromail.billing.intent-expiry", () -> "PT24H");
  }

  protected byte[] encryptedRefreshToken(UUID tenantId) {
    return cipher.encrypt(
        "worker-refresh-token".getBytes(StandardCharsets.UTF_8), tenantId.toString());
  }
}
