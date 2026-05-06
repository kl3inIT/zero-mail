package com.zeromail.api.controllers.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.zeromail.api.dto.billing.SepayWebhookPayload;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.billing.model.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;
import com.zeromail.core.tenant.TenantContext;

@ActiveProfiles("test")
class SepayWebhookIntegrationTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired BillingTopupIntentRepository billingTopupIntentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @Disabled("Wave 0 RED scaffold - production class lands in Plan 04")
    void valid_apikey_payload_credits_ledger_idempotently() {
        UUID tenantId = seedTenant();
        seedPendingIntent(tenantId, "ABC12345", 100_000L);

        ResponseEntity<String> response = postWebhook(sepayPayload(999L, null, "ABC12345 nap tien zeromail", 100_000L,
                "ABC12345"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"success\":true");
        assertThat(countTopupEntries(tenantId)).isEqualTo(1L);
        assertThat(topupAmountCredits(tenantId, "999")).isEqualTo(100);
        BillingTopupIntentEntity paidIntent = billingTopupIntentRepository.findByCode("ABC12345").orElseThrow();
        assertThat(paidIntent.getStatus()).isEqualTo(BillingTopupIntentStatus.PAID);
    }

    private ResponseEntity<String> postWebhook(SepayWebhookPayload payload) {
        return RestClient.create("http://localhost:" + port).post()
                .uri("/api/billing/sepay/webhook")
                .header(HttpHeaders.AUTHORIZATION, "Apikey test-sepay-key-fixture")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(String.class);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId, "billing-sepay-" + tenantId);
        return tenantId;
    }

    private void seedPendingIntent(UUID tenantId, String code, long amountVnd) {
        BillingTopupIntentEntity intent = new BillingTopupIntentEntity(
                UUID.randomUUID(),
                tenantId,
                code,
                amountVnd,
                BillingTopupIntentStatus.PENDING,
                Instant.now().plus(Duration.ofHours(24)));
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() ->
                billingTopupIntentRepository.saveAndFlush(intent));
    }

    private Long countTopupEntries(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM credit_ledger_entry WHERE tenant_id = ? AND kind = 'TOPUP'",
                Long.class,
                tenantId);
    }

    private Integer topupAmountCredits(UUID tenantId, String sepayTransactionId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT amount_credits
                  FROM credit_ledger_entry
                 WHERE tenant_id = ? AND ref_type = 'PAYMENT_SEPAY' AND ref_id = ? AND kind = 'TOPUP'
                """,
                Integer.class,
                tenantId,
                sepayTransactionId);
    }

    private static SepayWebhookPayload sepayPayload(long id, String code, String content, long transferAmount,
                                                    String referenceCode) {
        return new SepayWebhookPayload(
                id,
                "VCB",
                "2026-05-05 12:00:00",
                "0123",
                code,
                content,
                "in",
                transferAmount,
                0L,
                null,
                referenceCode,
                "bank sms");
    }
}
