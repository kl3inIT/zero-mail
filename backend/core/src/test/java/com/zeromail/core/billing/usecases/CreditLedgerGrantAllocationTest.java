package com.zeromail.core.billing.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.CreditGrantCategory;
import com.zeromail.core.billing.domain.CreditGrantStatus;
import com.zeromail.core.billing.domain.ReservationId;
import com.zeromail.core.billing.persistence.CreditGrantEntity;
import com.zeromail.core.billing.persistence.CreditGrantRepository;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CreditLedgerGrantAllocationTest extends PostgresContainerTest {

    @Autowired CreditLedger creditLedger;
    @Autowired CreditGrantRepository creditGrantRepository;
    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void reserve_spends_expiring_monthly_allowance_grant_before_paid_grant() {
        UUID tenantId = seedTenant();
        Instant now = Instant.now();
        UUID allowanceGrantId = seedCurrentMonthlyAllowanceGrant(tenantId, 5, 10);
        UUID paidGrantId =
                seedGrant(tenantId, CreditGrantCategory.PAID, 5, now.minusSeconds(60), null, 50);

        ReservationId reservationId =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditLedger.reserve(tenantId, CallSite.TRIAGE));

        assertThat(findReservationGrantId(reservationId)).isEqualTo(allowanceGrantId);
        assertThat(grantAvailableCredits(allowanceGrantId)).isEqualTo(4);
        assertThat(grantAvailableCredits(paidGrantId)).isEqualTo(5);
    }

    @Test
    void reserve_skips_expired_grant_and_uses_paid_grant() {
        UUID tenantId = seedTenant();
        Instant now = Instant.now();
        UUID expiredGrantId =
                seedGrant(
                        tenantId,
                        CreditGrantCategory.MONTHLY_ALLOWANCE,
                        5,
                        now.minusSeconds(172_800),
                        now.minusSeconds(86_400),
                        10);
        UUID paidGrantId =
                seedGrant(tenantId, CreditGrantCategory.PAID, 5, now.minusSeconds(60), null, 50);

        ReservationId reservationId =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditLedger.reserve(tenantId, CallSite.TRIAGE));

        assertThat(findReservationGrantId(reservationId)).isEqualTo(paidGrantId);
        assertThat(grantAvailableCredits(expiredGrantId)).isZero();
        assertThat(grantAvailableCredits(paidGrantId)).isEqualTo(4);
    }

    @Test
    void release_returns_credits_to_the_reserved_grant() {
        UUID tenantId = seedTenant();
        UUID allowanceGrantId = seedCurrentMonthlyAllowanceGrant(tenantId, 5, 10);

        ReservationId reservationId =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditLedger.reserve(tenantId, CallSite.DRAFT));

        assertThat(grantAvailableCredits(allowanceGrantId)).isEqualTo(3);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> creditLedger.release(reservationId));

        assertThat(grantAvailableCredits(allowanceGrantId)).isEqualTo(5);
    }

    @Test
    void zero_cost_call_site_creates_reservation_without_ledger_charge() {
        UUID tenantId = seedTenant();

        ReservationId reservationId =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditLedger.reserve(tenantId, CallSite.TRIAGE_DETERMINISTIC));

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> creditLedger.settle(reservationId));

        assertThat(creditLedger.balance(tenantId).availableCredits()).isZero();
        assertThat(countReserveEntries(tenantId, reservationId)).isZero();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "billing-grant-" + tenantId);
        attachZeroAllowancePlan(tenantId);
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

    private UUID seedCurrentMonthlyAllowanceGrant(UUID tenantId, int amountCredits, int priority) {
        UUID planId = UUID.randomUUID();
        UUID planPeriodId = UUID.randomUUID();
        String planCode = "TEST_MONTHLY_" + tenantId.toString().replace("-", "").substring(0, 16);
        Instant effectiveAt = Instant.now().minusSeconds(30);
        Instant expiresAt = effectiveAt.plusSeconds(86_400);
        jdbcTemplate.update(
                """
                        INSERT INTO billing_plan(
                            id, code, display_name, tier_rank, billing_cycle, currency,
                            price_vnd, monthly_credit_allowance, active, sort_order)
                        VALUES (?, ?, ?, 1, 'MONTH', 'VND', 0, ?, true, 1)
                        """,
                planId,
                planCode,
                "Test Monthly",
                amountCredits);
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
                        VALUES (?, ?, ?, 'ACTIVE', 'ADMIN', ?, ?, ?, 0, 'VND')
                        """,
                planPeriodId,
                tenantId,
                planId,
                Timestamp.from(effectiveAt),
                Timestamp.from(expiresAt),
                Timestamp.from(effectiveAt));

        UUID grantId = UUID.randomUUID();
        String referenceId = planCode + ":" + planPeriodId;
        CreditGrantEntity creditGrant =
                new CreditGrantEntity(
                        grantId,
                        tenantId,
                        CreditGrantCategory.MONTHLY_ALLOWANCE,
                        CreditGrantStatus.ACTIVE,
                        amountCredits,
                        effectiveAt,
                        expiresAt,
                        priority,
                        "PLAN_PERIOD",
                        referenceId);
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            creditGrantRepository.saveAndFlush(creditGrant);
                            creditLedgerEntryRepository.saveAndFlush(
                                    CreditLedgerEntryEntity.grant(
                                            UUID.randomUUID(),
                                            tenantId,
                                            amountCredits,
                                            grantId,
                                            "PLAN_PERIOD",
                                            tenantId + ":" + referenceId));
                        });
        return grantId;
    }

    private UUID seedGrant(
            UUID tenantId,
            CreditGrantCategory category,
            int amountCredits,
            Instant effectiveAt,
            Instant expiresAt,
            int priority) {
        UUID grantId = UUID.randomUUID();
        String referenceId = category.name() + "-" + grantId;
        CreditGrantEntity creditGrant =
                new CreditGrantEntity(
                        grantId,
                        tenantId,
                        category,
                        CreditGrantStatus.ACTIVE,
                        amountCredits,
                        effectiveAt,
                        expiresAt,
                        priority,
                        "TEST_GRANT",
                        referenceId);
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            creditGrantRepository.saveAndFlush(creditGrant);
                            creditLedgerEntryRepository.saveAndFlush(
                                    CreditLedgerEntryEntity.grant(
                                            UUID.randomUUID(),
                                            tenantId,
                                            amountCredits,
                                            grantId,
                                            "TEST_GRANT",
                                            referenceId));
                        });
        return grantId;
    }

    private UUID findReservationGrantId(ReservationId reservationId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT grant_id
                          FROM credit_reservation
                         WHERE id = ?
                        """,
                UUID.class,
                reservationId.value());
    }

    private Integer grantAvailableCredits(UUID grantId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(amount_credits), 0)::int
                          FROM credit_ledger_entry
                         WHERE grant_id = ?
                        """,
                Integer.class,
                grantId);
    }

    private Long countReserveEntries(UUID tenantId, ReservationId reservationId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM credit_ledger_entry
                         WHERE tenant_id = ? AND ref_id = ? AND kind = 'RESERVE'
                        """,
                Long.class,
                tenantId,
                reservationId.value().toString());
    }
}
