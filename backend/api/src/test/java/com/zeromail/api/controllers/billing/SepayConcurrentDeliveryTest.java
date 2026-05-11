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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * SePay endpoint replay-as-200 contract under concurrent duplicate delivery.
 *
 * <p>SePay treats anything other than HTTP 200 as a retry signal. Two concurrent webhooks for the
 * same intent must both observe a 200 response, exactly one {@code TOPUP} ledger row must exist,
 * and the intent must end up in {@code PAID}. The atomic conditional UPDATE on {@code
 * billing_topup_intent.status} is what makes this safe; this test pins the contract.
 */
@ActiveProfiles("test")
class SepayConcurrentDeliveryTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired BillingTopupIntentRepository billingTopupIntentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void two_concurrent_identical_webhooks_both_return_200_and_credit_once() throws Exception {
        UUID tenantId = seedTenant();
        seedPendingIntent(tenantId, "CNX12345", 100_000L);

        SepayWebhookPayload payload =
                sepayPayload(8001L, "CNX12345", "CNX12345 nap tien", 100_000L);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Integer> deliver = () -> postWebhook(payload).getStatusCode().value();
            Future<Integer> futureFirst = executor.submit(deliver);
            Future<Integer> futureSecond = executor.submit(deliver);

            int firstStatus = futureFirst.get();
            int secondStatus = futureSecond.get();

            assertThat(List.of(firstStatus, secondStatus)).containsExactly(200, 200);
        }

        assertThat(countTopupEntries(tenantId)).isEqualTo(1L);
        BillingTopupIntentEntity paidIntent = findIntentByCode(tenantId, "CNX12345");
        assertThat(paidIntent.getStatus()).isEqualTo(BillingTopupIntentStatus.PAID);
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
                "billing-concurrent-" + tenantId);
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

    private static SepayWebhookPayload sepayPayload(
            long id, String code, String content, long transferAmount) {
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
                code,
                "bank sms");
    }
}
