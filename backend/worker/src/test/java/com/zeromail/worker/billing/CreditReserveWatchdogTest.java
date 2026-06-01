package com.zeromail.worker.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.billing.domain.CallSite;
import com.zeromail.core.billing.domain.CreditReservationStatus;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.billing.persistence.CreditReservationEntity;
import com.zeromail.core.billing.persistence.CreditReservationRepository;
import com.zeromail.core.billing.usecases.CreditLedger;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.worker.PostgresContainerTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CreditReserveWatchdogTest extends PostgresContainerTest {

    @Autowired CreditReserveWatchdog watchdog;
    @Autowired CreditReservationRepository creditReservationRepository;
    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
    @Autowired CreditLedger creditLedger;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetBillingTables() {
        jdbcTemplate.execute("DELETE FROM credit_ledger_entry");
        jdbcTemplate.execute("DELETE FROM credit_reservation");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void stale_reservation_older_than_5_minutes_is_released() {
        UUID tenantId = seedTenant();
        UUID reservationId =
                seedPendingReservation(tenantId, 2, Instant.now().minus(Duration.ofMinutes(6)));

        watchdog.tick();

        CreditReservationStatus status = reservationStatus(tenantId, reservationId);
        assertThat(status).isEqualTo(CreditReservationStatus.RELEASED);
        assertThat(releaseEntryCount(tenantId, reservationId)).isEqualTo(1L);
        assertThat(releaseAmountCredits(tenantId, reservationId)).isEqualTo(2);
        int availableCredits =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> creditLedger.balance(tenantId).availableCredits());
        assertThat(availableCredits).isEqualTo(10);
    }

    @Test
    void tick_on_already_released_reservation_is_no_op() {
        UUID tenantId = seedTenant();
        UUID reservationId =
                seedPendingReservation(tenantId, 2, Instant.now().minus(Duration.ofMinutes(6)));

        watchdog.tick();
        long releaseRowsAfterFirstTick = releaseEntryCount(tenantId, reservationId);
        watchdog.tick();

        assertThat(releaseEntryCount(tenantId, reservationId)).isEqualTo(releaseRowsAfterFirstTick);
    }

    @Test
    void fresh_reservation_under_5_minutes_is_not_released() {
        UUID tenantId = seedTenant();
        UUID reservationId =
                seedPendingReservation(tenantId, 2, Instant.now().minus(Duration.ofMinutes(2)));

        watchdog.tick();

        assertThat(reservationStatus(tenantId, reservationId))
                .isEqualTo(CreditReservationStatus.PENDING);
        assertThat(releaseEntryCount(tenantId, reservationId)).isZero();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "billing-watchdog-" + tenantId);
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

    private UUID seedPendingReservation(UUID tenantId, int amountCredits, Instant createdAt) {
        UUID reservationId = UUID.randomUUID();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            creditLedgerEntryRepository.saveAndFlush(
                                    CreditLedgerEntryEntity.topup(
                                            UUID.randomUUID(),
                                            tenantId,
                                            10,
                                            "TEST-WATCHDOG-" + tenantId));
                            creditReservationRepository.saveAndFlush(
                                    new CreditReservationEntity(
                                            reservationId,
                                            tenantId,
                                            amountCredits,
                                            CallSite.TRIAGE,
                                            CreditReservationStatus.PENDING));
                            creditLedgerEntryRepository.saveAndFlush(
                                    CreditLedgerEntryEntity.reserve(
                                            UUID.randomUUID(),
                                            tenantId,
                                            amountCredits,
                                            reservationId));
                        });
        jdbcTemplate.update(
                "UPDATE credit_reservation SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                reservationId);
        return reservationId;
    }

    private CreditReservationStatus reservationStatus(UUID tenantId, UUID reservationId) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                creditReservationRepository
                                        .findById(reservationId)
                                        .orElseThrow()
                                        .getStatus());
    }

    private Long releaseEntryCount(UUID tenantId, UUID reservationId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM credit_ledger_entry
                 WHERE tenant_id = ? AND ref_type = 'RESERVATION' AND ref_id = ? AND kind = 'RELEASE'
                """,
                Long.class,
                tenantId,
                reservationId.toString());
    }

    private Integer releaseAmountCredits(UUID tenantId, UUID reservationId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT amount_credits
                  FROM credit_ledger_entry
                 WHERE tenant_id = ? AND ref_type = 'RESERVATION' AND ref_id = ? AND kind = 'RELEASE'
                """,
                Integer.class,
                tenantId,
                reservationId.toString());
    }
}
