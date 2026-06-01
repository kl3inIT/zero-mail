package com.zeromail.core.billing.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.ReservationId;
import com.zeromail.core.billing.exception.IllegalLedgerStateException;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CreditLedgerSettleIdempotentTest extends PostgresContainerTest {

    @Autowired CreditLedger creditLedger;
    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void settle_twice_is_no_op() {
        UUID tenantId = seedTenantWithCredits(10);
        ReservationId reservationId = reserveTriage(tenantId);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            creditLedger.settle(reservationId);
                            assertThatCode(() -> creditLedger.settle(reservationId))
                                    .doesNotThrowAnyException();
                        });

        assertThat(countFinalizers(tenantId, reservationId, "SETTLE")).isEqualTo(1L);
    }

    @Test
    void release_twice_is_no_op() {
        UUID tenantId = seedTenantWithCredits(10);
        ReservationId reservationId = reserveTriage(tenantId);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            creditLedger.release(reservationId);
                            assertThatCode(() -> creditLedger.release(reservationId))
                                    .doesNotThrowAnyException();
                        });

        assertThat(countFinalizers(tenantId, reservationId, "RELEASE")).isEqualTo(1L);
    }

    @Test
    void release_after_settle_throws_IllegalLedgerStateException() {
        UUID tenantId = seedTenantWithCredits(10);
        ReservationId reservationId = reserveTriage(tenantId);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> creditLedger.settle(reservationId));

        assertThatThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                        .run(() -> creditLedger.release(reservationId)))
                .isInstanceOf(IllegalLedgerStateException.class);
    }

    @Test
    void settle_after_release_throws_IllegalLedgerStateException() {
        UUID tenantId = seedTenantWithCredits(10);
        ReservationId reservationId = reserveTriage(tenantId);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> creditLedger.release(reservationId));

        assertThatThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                        .run(() -> creditLedger.settle(reservationId)))
                .isInstanceOf(IllegalLedgerStateException.class);
    }

    private UUID seedTenantWithCredits(int startingCredits) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "billing-settle-" + tenantId);
        attachZeroAllowancePlan(tenantId);
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                creditLedgerEntryRepository.saveAndFlush(
                                        CreditLedgerEntryEntity.topup(
                                                UUID.randomUUID(),
                                                tenantId,
                                                startingCredits,
                                                "TEST-SEED-" + tenantId)));
        return tenantId;
    }

    private void attachZeroAllowancePlan(UUID tenantId) {
        UUID planId = UUID.randomUUID();
        String planCode = "TEST_ZERO_" + tenantId.toString().replace("-", "").substring(0, 16);
        jdbcTemplate.update(
                """
                INSERT INTO billing_plan(
                    id, code, display_name, tier_rank, billing_cycle, currency,
                    price_vnd, monthly_credit_allowance, active, sort_order)
                VALUES (?, ?, ?, 0, 'NONE', 'VND', 0, 0, true, 0)
                """,
                planId,
                planCode,
                "Test Zero");
        jdbcTemplate.update(
                """
                INSERT INTO plan_feature_permission(id, plan_id, feature_code, enabled)
                SELECT gen_random_uuid(), ?, code, true
                  FROM feature_catalog
                """,
                planId);
        jdbcTemplate.update(
                """
                INSERT INTO billing_plan_period(
                    id, tenant_id, plan_id, status, provider,
                    effective_at, expires_at, paid_at, amount_vnd, currency)
                VALUES (
                    ?, ?, ?, 'ACTIVE', 'ADMIN',
                    CURRENT_TIMESTAMP - INTERVAL '1 minute',
                    CURRENT_TIMESTAMP + INTERVAL '30 days',
                    CURRENT_TIMESTAMP - INTERVAL '1 minute',
                    0, 'VND')
                """,
                UUID.randomUUID(),
                tenantId,
                planId);
    }

    private ReservationId reserveTriage(UUID tenantId) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> creditLedger.reserve(tenantId, CallSite.TRIAGE));
    }

    private Long countFinalizers(UUID tenantId, ReservationId reservationId, String kind) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM credit_ledger_entry
                 WHERE tenant_id = ? AND ref_type = 'RESERVATION' AND ref_id = ? AND kind = ?
                """,
                Long.class,
                tenantId,
                reservationId.value().toString(),
                kind);
    }
}
