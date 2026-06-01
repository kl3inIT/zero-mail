package com.zeromail.core.billing.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.CreditGrantCategory;
import com.zeromail.core.billing.domain.CreditGrantStatus;
import com.zeromail.core.billing.persistence.BillingPlanEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodEntity;
import com.zeromail.core.billing.persistence.BillingPlanPeriodRepository;
import com.zeromail.core.billing.persistence.BillingPlanRepository;
import com.zeromail.core.billing.persistence.CreditGrantEntity;
import com.zeromail.core.billing.persistence.CreditGrantRepository;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CreditGrantServiceTest extends PostgresContainerTest {

    @Autowired CreditGrantService creditGrantService;
    @Autowired CreditLedger creditLedger;
    @Autowired BillingPlanRepository billingPlanRepository;
    @Autowired CreditGrantRepository creditGrantRepository;
    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
    @Autowired BillingPlanPeriodRepository billingPlanPeriodRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void current_plan_allowance_is_idempotent_per_tenant_and_period() {
        UUID tenantId = seedTenant();

        Optional<CreditGrantResult> firstGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditGrantService.resetCurrentPlanAllowanceCredits(tenantId));
        Optional<CreditGrantResult> secondGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditGrantService.resetCurrentPlanAllowanceCredits(tenantId));

        assertThat(firstGrant).isPresent();
        assertThat(secondGrant).isPresent();
        assertThat(firstGrant.get().created()).isTrue();
        assertThat(secondGrant.get().created()).isFalse();
        assertThat(firstGrant.get().grantId()).isEqualTo(secondGrant.get().grantId());
        assertThat(countMonthlyAllowanceGrants(tenantId)).isEqualTo(1L);
        assertThat(countGrantLedgerEntries(tenantId)).isEqualTo(1L);
        assertThat(
                        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                .call(() -> creditLedger.balance(tenantId).availableCredits()))
                .isEqualTo(300);
    }

    @Test
    void current_plan_allowance_reset_expires_old_active_grant_balance() {
        UUID tenantId = seedTenant();
        UUID expiredGrantId = seedExpiredGrant(tenantId, 7);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> creditGrantService.resetCurrentPlanAllowanceCredits(tenantId));

        CreditGrantEntity expiredGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditGrantRepository.findById(expiredGrantId).orElseThrow());
        assertThat(expiredGrant.getStatus()).isEqualTo(CreditGrantStatus.EXPIRED);
        assertThat(grantAvailableCredits(expiredGrantId)).isZero();
        assertThat(countLedgerEntries(tenantId, "EXPIRE")).isEqualTo(1L);
    }

    @Test
    void plan_allowance_reset_replaces_previous_monthly_allowance_balance_on_plan_change() {
        UUID tenantId = seedTenant();
        BillingPlanEntity plusPlan = billingPlanRepository.findByCode("PLUS").orElseThrow();
        BillingPlanEntity proPlan = billingPlanRepository.findByCode("PRO").orElseThrow();
        Instant plusEffectiveAt = Instant.now().minusSeconds(60);
        BillingPlanPeriodEntity plusPlanPeriod =
                new BillingPlanPeriodEntity(
                        UUID.randomUUID(),
                        tenantId,
                        plusPlan.getId(),
                        "ACTIVE",
                        "ADMIN",
                        "test-plus-" + tenantId,
                        null,
                        null,
                        plusEffectiveAt,
                        plusEffectiveAt.plusSeconds(2_592_000),
                        plusEffectiveAt,
                        0,
                        "VND");
        billingPlanPeriodRepository.saveAndFlush(plusPlanPeriod);

        CreditGrantResult firstGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        creditGrantService
                                                .resetCurrentPlanAllowanceCredits(tenantId)
                                                .orElseThrow());
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                creditLedgerEntryRepository.saveAndFlush(
                                        CreditLedgerEntryEntity.adjustment(
                                                UUID.randomUUID(),
                                                tenantId,
                                                -1900,
                                                firstGrant.grantId(),
                                                "TEST_SPEND",
                                                "invoice-1-spend")));

        assertThat(tenantLedgerBalance(tenantId)).isEqualTo(100);

        plusPlanPeriod.markExpired();
        billingPlanPeriodRepository.saveAndFlush(plusPlanPeriod);
        Instant proEffectiveAt = Instant.now();
        billingPlanPeriodRepository.saveAndFlush(
                new BillingPlanPeriodEntity(
                        UUID.randomUUID(),
                        tenantId,
                        proPlan.getId(),
                        "ACTIVE",
                        "ADMIN",
                        "test-pro-" + tenantId,
                        null,
                        null,
                        proEffectiveAt,
                        proEffectiveAt.plusSeconds(2_592_000),
                        proEffectiveAt,
                        0,
                        "VND"));
        CreditGrantResult secondGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        creditGrantService
                                                .resetCurrentPlanAllowanceCredits(tenantId)
                                                .orElseThrow());
        CreditGrantResult duplicateSecondGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        creditGrantService
                                                .resetCurrentPlanAllowanceCredits(tenantId)
                                                .orElseThrow());

        CreditGrantEntity supersededGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(
                                () ->
                                        creditGrantRepository
                                                .findById(firstGrant.grantId())
                                                .orElseThrow());
        assertThat(supersededGrant.getStatus()).isEqualTo(CreditGrantStatus.EXPIRED);
        assertThat(grantAvailableCredits(firstGrant.grantId())).isZero();
        assertThat(secondGrant.created()).isTrue();
        assertThat(duplicateSecondGrant.created()).isFalse();
        assertThat(duplicateSecondGrant.grantId()).isEqualTo(secondGrant.grantId());
        assertThat(grantAvailableCredits(secondGrant.grantId())).isEqualTo(8000);
        assertThat(tenantLedgerBalance(tenantId)).isEqualTo(8000);
        assertThat(countLedgerEntries(tenantId, "EXPIRE")).isEqualTo(1L);
    }

    @Test
    void reserve_lazily_resets_current_plan_allowance() {
        UUID tenantId = seedTenant();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> creditLedger.reserve(tenantId, CallSite.TRIAGE));

        assertThat(countMonthlyAllowanceGrants(tenantId)).isEqualTo(1L);
        assertThat(
                        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                .call(() -> creditLedger.balance(tenantId).availableCredits()))
                .isEqualTo(299);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "billing-plan-allowance-" + tenantId);
        return tenantId;
    }

    private UUID seedExpiredGrant(UUID tenantId, int amountCredits) {
        UUID grantId = UUID.randomUUID();
        String referenceId = "EXPIRED-" + grantId;
        Instant now = Instant.now();
        CreditGrantEntity expiredGrant =
                new CreditGrantEntity(
                        grantId,
                        tenantId,
                        CreditGrantCategory.BETA,
                        CreditGrantStatus.ACTIVE,
                        amountCredits,
                        now.minusSeconds(172_800),
                        now.minusSeconds(86_400),
                        10,
                        "TEST_EXPIRED",
                        referenceId);
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            creditGrantRepository.saveAndFlush(expiredGrant);
                            creditLedgerEntryRepository.saveAndFlush(
                                    CreditLedgerEntryEntity.grant(
                                            UUID.randomUUID(),
                                            tenantId,
                                            amountCredits,
                                            grantId,
                                            "TEST_EXPIRED",
                                            referenceId));
                        });
        return grantId;
    }

    private Long countMonthlyAllowanceGrants(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM credit_grant
                         WHERE tenant_id = ? AND category = 'MONTHLY_ALLOWANCE'
                        """,
                Long.class,
                tenantId);
    }

    private Long countGrantLedgerEntries(UUID tenantId) {
        return countLedgerEntries(tenantId, "GRANT");
    }

    private Long countLedgerEntries(UUID tenantId, String kind) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM credit_ledger_entry
                         WHERE tenant_id = ? AND kind = ?
                        """,
                Long.class,
                tenantId,
                kind);
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

    private Integer tenantLedgerBalance(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(amount_credits), 0)::int
                          FROM credit_ledger_entry
                         WHERE tenant_id = ?
                        """,
                Integer.class,
                tenantId);
    }
}
