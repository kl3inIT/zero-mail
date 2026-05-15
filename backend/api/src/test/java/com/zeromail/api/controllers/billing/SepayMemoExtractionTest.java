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

/**
 * Memo-extraction edge cases covering {@code BillingTopupService.extractCandidateIntentCodes}.
 *
 * <p>Real-money reconciliation must not silently drop a paid transfer. These tests pin two cases
 * that the original single-candidate extractor failed to credit:
 *
 * <ul>
 *   <li>Bank memo content with a leading 8-digit fragment (e.g. transaction date) BEFORE the Zero
 *       Mail intent code, such as {@code "20260505 ABC12345 nap tien"}.
 *   <li>SePay's detected {@code code} field is itself 8 Crockford characters but does not match any
 *       known pending intent, while the actual intent code appears later in {@code content}.
 * </ul>
 */
@ActiveProfiles("test")
class SepayMemoExtractionTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired BillingTopupIntentRepository billingTopupIntentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void content_with_leading_eight_digit_token_still_credits_intent_later_in_memo() {
        UUID tenantId = seedTenant();
        seedPendingIntent(tenantId, "ABC12345", 100_000L);

        ResponseEntity<String> response =
                postWebhook(
                        sepayPayload(7001L, null, "20260505 ABC12345 nap tien", 100_000L, null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(countTopupEntries(tenantId)).isEqualTo(1L);
        BillingTopupIntentEntity paidIntent = findIntentByCode(tenantId, "ABC12345");
        assertThat(paidIntent.getStatus()).isEqualTo(BillingTopupIntentStatus.PAID);
    }

    @Test
    void unknown_eight_character_code_field_falls_through_to_real_code_in_content() {
        UUID tenantId = seedTenant();
        seedPendingIntent(tenantId, "DEF67890", 100_000L);

        ResponseEntity<String> response =
                postWebhook(
                        sepayPayload(
                                7002L,
                                "ZZZZZZZZ",
                                "ref DEF67890 nap tien zeromail",
                                100_000L,
                                null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(countTopupEntries(tenantId)).isEqualTo(1L);
        BillingTopupIntentEntity paidIntent = findIntentByCode(tenantId, "DEF67890");
        assertThat(paidIntent.getStatus()).isEqualTo(BillingTopupIntentStatus.PAID);
    }

    @Test
    void reference_code_is_consulted_first_when_present() {
        UUID tenantId = seedTenant();
        seedPendingIntent(tenantId, "GHJ23456", 100_000L);

        ResponseEntity<String> response =
                postWebhook(sepayPayload(7003L, null, "nothing useful here", 100_000L, "GHJ23456"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(countTopupEntries(tenantId)).isEqualTo(1L);
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
                "billing-memo-" + tenantId);
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

    private static SepayWebhookPayload sepayPayload(
            long id, String code, String content, long transferAmount, String referenceCode) {
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
