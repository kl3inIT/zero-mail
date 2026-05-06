package com.zeromail.worker.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.worker.PostgresContainerTest;

class BillingIntentExpirySweeperTest extends PostgresContainerTest {

    @Autowired BillingIntentExpirySweeper sweeper;
    @Autowired BillingTopupIntentRepository billingTopupIntentRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetBillingTables() {
        jdbcTemplate.execute("DELETE FROM credit_ledger_entry");
        jdbcTemplate.execute("DELETE FROM credit_reservation");
        jdbcTemplate.execute("DELETE FROM billing_topup_intent");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    @Disabled("Wave 0 RED scaffold - production class lands in Plan 05")
    void expired_pending_intents_marked_EXPIRED() {
        UUID tenantId = seedTenant();
        UUID intentId = seedIntent(
                tenantId,
                "EXP12345",
                BillingTopupIntentStatus.PENDING,
                Instant.now().minus(Duration.ofHours(25)));

        sweeper.sweep();

        assertThat(intentStatus(tenantId, intentId)).isEqualTo(BillingTopupIntentStatus.EXPIRED);
    }

    @Test
    @Disabled("Wave 0 RED scaffold - production class lands in Plan 05")
    void paid_intent_not_touched_by_sweeper() {
        UUID tenantId = seedTenant();
        UUID intentId = seedIntent(
                tenantId,
                "PAID1234",
                BillingTopupIntentStatus.PAID,
                Instant.now().minus(Duration.ofHours(25)));

        sweeper.sweep();

        assertThat(intentStatus(tenantId, intentId)).isEqualTo(BillingTopupIntentStatus.PAID);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId, "billing-expiry-" + tenantId);
        return tenantId;
    }

    private UUID seedIntent(UUID tenantId, String code, BillingTopupIntentStatus status, Instant expiresAt) {
        UUID intentId = UUID.randomUUID();
        BillingTopupIntentEntity intent = new BillingTopupIntentEntity(
                intentId,
                tenantId,
                code,
                100_000L,
                status,
                expiresAt);
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() ->
                billingTopupIntentRepository.saveAndFlush(intent));
        return intentId;
    }

    private BillingTopupIntentStatus intentStatus(UUID tenantId, UUID intentId) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> billingTopupIntentRepository.findById(intentId).orElseThrow().getStatus());
    }
}
