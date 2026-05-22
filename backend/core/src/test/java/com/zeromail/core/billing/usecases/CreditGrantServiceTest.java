package com.zeromail.core.billing.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.CreditGrantCategory;
import com.zeromail.core.billing.domain.CreditGrantStatus;
import com.zeromail.core.billing.exception.InsufficientCreditsException;
import com.zeromail.core.billing.persistence.CreditGrantEntity;
import com.zeromail.core.billing.persistence.CreditGrantRepository;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CreditGrantServiceTest extends PostgresContainerTest {

    @Autowired CreditGrantService creditGrantService;
    @Autowired CreditLedger creditLedger;
    @Autowired CreditGrantRepository creditGrantRepository;
    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void current_beta_grant_is_idempotent_per_tenant_and_period() {
        UUID tenantId = seedTenant();

        Optional<CreditGrantResult> firstGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditGrantService.grantCurrentBetaCredits(tenantId));
        Optional<CreditGrantResult> secondGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditGrantService.grantCurrentBetaCredits(tenantId));

        assertThat(firstGrant).isPresent();
        assertThat(secondGrant).isPresent();
        assertThat(firstGrant.get().created()).isTrue();
        assertThat(secondGrant.get().created()).isFalse();
        assertThat(firstGrant.get().grantId()).isEqualTo(secondGrant.get().grantId());
        assertThat(countBetaGrants(tenantId)).isEqualTo(1L);
        assertThat(countGrantLedgerEntries(tenantId)).isEqualTo(1L);
        assertThat(
                        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                .call(() -> creditLedger.balance(tenantId).availableCredits()))
                .isEqualTo(300);
    }

    @Test
    void grant_current_beta_credits_expires_old_active_grant_balance() {
        UUID tenantId = seedTenant();
        UUID expiredGrantId = seedExpiredGrant(tenantId, 7);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> creditGrantService.grantCurrentBetaCredits(tenantId));

        CreditGrantEntity expiredGrant =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditGrantRepository.findById(expiredGrantId).orElseThrow());
        assertThat(expiredGrant.getStatus()).isEqualTo(CreditGrantStatus.EXPIRED);
        assertThat(grantAvailableCredits(expiredGrantId)).isZero();
        assertThat(countLedgerEntries(tenantId, "EXPIRE")).isEqualTo(1L);
    }

    @Test
    void reserve_lazily_grants_current_beta_credits() {
        UUID tenantId = seedTenant();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(() -> creditLedger.reserve(tenantId, CallSite.TRIAGE));

        assertThat(countBetaGrants(tenantId)).isEqualTo(1L);
        assertThat(
                        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                .call(() -> creditLedger.balance(tenantId).availableCredits()))
                .isEqualTo(299);
    }

    @Test
    void daily_hard_cap_rejects_reservation_after_one_hundred_beta_credits() {
        UUID tenantId = seedTenant();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                IntStream.range(0, 100)
                                        .forEach(
                                                ignoredIndex ->
                                                        creditLedger.reserve(
                                                                tenantId, CallSite.TRIAGE)));

        assertThatThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                        .run(() -> creditLedger.reserve(tenantId, CallSite.TRIAGE)))
                .isInstanceOf(InsufficientCreditsException.class);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "billing-beta-" + tenantId);
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

    private Long countBetaGrants(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM credit_grant
                 WHERE tenant_id = ? AND category = 'BETA'
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
}
