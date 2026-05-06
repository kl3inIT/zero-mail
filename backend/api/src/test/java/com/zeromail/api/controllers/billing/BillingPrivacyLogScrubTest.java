package com.zeromail.api.controllers.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.zeromail.api.dto.billing.SepayWebhookPayload;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.billing.model.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;
import com.zeromail.core.tenant.TenantContext;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Happy-path log scrub contract for SePay top-ups.
 *
 * <p>This test only covers the happy-path log scrub. The mismatch path, where D-I1 explicitly
 * permits {@code event=sepay_webhook_amount_mismatch intentVnd={} actualVnd={}} with numbers, is
 * covered by {@link SepayWebhookMismatchAuditEventTest}. Do NOT extend this scrub test to assert
 * against numeric amounts in the mismatch path; that contradicts the locked privacy contract.
 */
@ActiveProfiles("test")
class BillingPrivacyLogScrubTest extends ApiPostgresTestBase {

  @LocalServerPort int port;
  @Autowired BillingTopupIntentRepository billingTopupIntentRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  private Logger rootLogger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void attachLogCapture() {
    rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    listAppender = new ListAppender<>();
    listAppender.start();
    rootLogger.addAppender(listAppender);
  }

  @AfterEach
  void detachLogCapture() {
    listAppender.stop();
    rootLogger.detachAppender(listAppender);
  }

  @Test
  void successful_topup_happy_path_logs_no_payload_bytes() {
    UUID tenantId = seedTenant();
    seedPendingIntent(tenantId, "LOG12345", 100_000L);

    RestClient.create("http://localhost:" + port)
        .post()
        .uri("/api/billing/sepay/webhook")
        .header(HttpHeaders.AUTHORIZATION, "Apikey test-sepay-key-fixture")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new SepayWebhookPayload(
                999L,
                "VCB",
                "2026-05-05 12:00:00",
                "0123",
                "LOG12345",
                "LOG12345 nap tien zeromail",
                "in",
                100_000L,
                0L,
                null,
                "BANK-REF-999",
                "bank sms"))
        .retrieve()
        .toBodilessEntity();

    String combinedLogOutput = String.join("\n", capturedLogLines());
    assertThat(combinedLogOutput)
        .doesNotContain("100000")
        .doesNotContain("Apikey ")
        .doesNotContain("999")
        .doesNotContain("0123")
        .doesNotContain("bank sms");
  }

  private UUID seedTenant() {
    UUID tenantId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, "billing-log-" + tenantId);
    return tenantId;
  }

  private void seedPendingIntent(UUID tenantId, String code, long amountVnd) {
    BillingTopupIntentEntity intent =
        new BillingTopupIntentEntity(
            UUID.randomUUID(),
            tenantId,
            code,
            amountVnd,
            BillingTopupIntentStatus.PENDING,
            Instant.now().plus(Duration.ofHours(24)));
    ScopedValue.where(TenantContext.TENANT, tenantId.toString())
        .run(() -> billingTopupIntentRepository.saveAndFlush(intent));
  }

  private List<String> capturedLogLines() {
    return listAppender.list.stream()
        .map(
            loggingEvent ->
                loggingEvent.getFormattedMessage() + " " + loggingEvent.getMDCPropertyMap())
        .toList();
  }
}
