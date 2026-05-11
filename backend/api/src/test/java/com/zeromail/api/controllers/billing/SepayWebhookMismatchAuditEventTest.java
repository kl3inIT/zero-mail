package com.zeromail.api.controllers.billing;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.api.dto.billing.SepayWebhookPayload;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.billing.domain.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;
import com.zeromail.core.tenant.TenantContext;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@ActiveProfiles("test")
class SepayWebhookMismatchAuditEventTest extends ApiPostgresTestBase {

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
    void amount_mismatch_emits_audit_event_with_vnd_numbers() {
        UUID tenantId = seedTenant();
        String code = "ABCD2345";
        long sensitiveSepayTransactionId = 987_654_321_987_654_321L;
        String sensitiveAccountNumber = "PRIVATE-ACCOUNT-SENTINEL";
        seedPendingIntent(tenantId, code, 50_000L);

        ResponseEntity<String> response =
                RestClient.create("http://localhost:" + port)
                        .post()
                        .uri("/api/billing/sepay/webhook")
                        .header(HttpHeaders.AUTHORIZATION, "Apikey test-sepay-key-fixture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                new SepayWebhookPayload(
                                        sensitiveSepayTransactionId,
                                        "VCB",
                                        "2026-05-05 12:00:00",
                                        sensitiveAccountNumber,
                                        code,
                                        code + " nap tien zeromail",
                                        "in",
                                        99_000L,
                                        0L,
                                        null,
                                        "BANK-REF-XYZ",
                                        "bank sms"))
                        .retrieve()
                        .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"success\":true");
        assertThat(countTopupEntries(tenantId)).isZero();
        assertThat(findIntentByCode(tenantId, code).getStatus())
                .isEqualTo(BillingTopupIntentStatus.PENDING);

        String combinedLogOutput = String.join("\n", capturedLogLines());
        assertThat(combinedLogOutput)
                .contains("event=sepay_webhook_amount_mismatch")
                .contains("intentVnd=50000")
                .contains("actualVnd=99000")
                .doesNotContain("event=sepay_unknown_code")
                .doesNotContain("Apikey ")
                .doesNotContain(String.valueOf(sensitiveSepayTransactionId))
                .doesNotContain(sensitiveAccountNumber);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "billing-mismatch-" + tenantId);
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

    private Long countTopupEntries(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM credit_ledger_entry WHERE tenant_id = ? AND kind = 'TOPUP'",
                Long.class,
                tenantId);
    }

    private BillingTopupIntentEntity findIntentByCode(UUID tenantId, String code) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> billingTopupIntentRepository.findByCode(code).orElseThrow());
    }

    private List<String> capturedLogLines() {
        return listAppender.list.stream()
                .map(
                        loggingEvent ->
                                loggingEvent.getFormattedMessage()
                                        + " "
                                        + loggingEvent.getMDCPropertyMap())
                .toList();
    }
}
