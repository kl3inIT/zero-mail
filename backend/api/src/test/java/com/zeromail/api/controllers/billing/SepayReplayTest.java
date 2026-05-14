package com.zeromail.api.controllers.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.dto.billing.SepayWebhookPayload;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.billing.domain.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;
import com.zeromail.core.tenant.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@ActiveProfiles("test")
class SepayReplayTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired BillingTopupIntentRepository billingTopupIntentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void replay_same_transaction_id_yields_single_topup() {
        UUID tenantId = seedTenant();
        String topupCode = "RPA8Y123";
        seedPendingIntent(tenantId, topupCode, 100_000L);
        SepayWebhookPayload payload =
                new SepayWebhookPayload(
                        1001L,
                        "VCB",
                        "2026-05-05 12:00:00",
                        "0123",
                        topupCode,
                        topupCode + " nap tien zeromail",
                        "in",
                        100_000L,
                        0L,
                        null,
                        "BANK-REF-1001",
                        "bank sms");

        ResponseEntity<String> firstResponse = postWebhook(payload);
        ResponseEntity<String> replayResponse = postWebhook(payload);

        assertThat(firstResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(replayResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(countTopupEntries(tenantId)).isEqualTo(1L);
        BillingTopupIntentEntity paidIntent = findIntentByCode(tenantId, topupCode);
        assertThat(paidIntent.getPaidAt()).isNotNull();
        assertThat(paidIntent.getSepayTransactionId()).isEqualTo("1001");
    }

    private ResponseEntity<String> postWebhook(SepayWebhookPayload payload) {
        return RestClient.create("http://localhost:" + port)
                .post()
                .uri("/api/billing/sepay/webhook")
                .header(HttpHeaders.AUTHORIZATION, "Apikey test-sepay-key-fixture")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(String.class);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "billing-replay-" + tenantId);
        return tenantId;
    }

    private void seedPendingIntent(UUID tenantId, String code, long amountVnd) {
        BillingTopupIntentEntity intent =
                new BillingTopupIntentEntity(
                        UUID.randomUUID(),
                        tenantId,
                        code,
                        amountVnd,
                        null,
                        "PKG_TEST",
                        "Test package",
                        Math.toIntExact(amountVnd / 1_000L),
                        BillingTopupIntentStatus.PENDING,
                        Instant.now().plus(Duration.ofHours(24)),
                        null,
                        null,
                        null,
                        null,
                        code,
                        null);
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
}
