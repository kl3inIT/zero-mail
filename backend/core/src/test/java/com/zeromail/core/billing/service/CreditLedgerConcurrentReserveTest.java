package com.zeromail.core.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.StructuredTaskScope;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.billing.model.CreditLedger;
import com.zeromail.core.billing.model.InsufficientCreditsException;
import com.zeromail.core.billing.model.ReservationId;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;

class CreditLedgerConcurrentReserveTest extends PostgresContainerTest {

    private static final int STARTING_CREDITS = 5;
    private static final int CONCURRENT_REQUESTS = 10;

    @Autowired CreditLedger creditLedger;
    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @Disabled("Wave 0 RED scaffold - production class lands in Plan 03")
    void ten_virtual_threads_reserve_against_available_5_yields_exactly_5_successes() throws Exception {
        UUID tenantId = seedTenant();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() ->
                creditLedgerEntryRepository.saveAndFlush(CreditLedgerEntryEntity.topup(
                        UUID.randomUUID(), tenantId, STARTING_CREDITS, "SEPAY-SEED-" + tenantId)));

        CountDownLatch simultaneousStart = new CountDownLatch(1);
        try (var taskScope = StructuredTaskScope.<ReservationId>open()) {
            var reservationTasks = java.util.stream.IntStream.range(0, CONCURRENT_REQUESTS)
                    .mapToObj(requestIndex -> taskScope.fork(() -> {
                        simultaneousStart.await();
                        try {
                            return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                                    .call(() -> creditLedger.reserve(tenantId, CallSite.TRIAGE));
                        } catch (InsufficientCreditsException insufficientCreditsException) {
                            return null;
                        }
                    }))
                    .toList();

            simultaneousStart.countDown();
            taskScope.join();

            long successfulReservations = reservationTasks.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .filter(java.util.Objects::nonNull)
                    .count();
            long rejectedReservations = reservationTasks.size() - successfulReservations;

            assertThat(successfulReservations).isEqualTo(STARTING_CREDITS);
            assertThat(rejectedReservations).isEqualTo(STARTING_CREDITS);
        }

        int availableCredits = ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> creditLedger.balance(tenantId).availableCredits());
        assertThat(availableCredits).isZero();
        assertThat(countLedgerEntries(tenantId)).isEqualTo(1L + STARTING_CREDITS);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId, "billing-reserve-" + tenantId);
        return tenantId;
    }

    private Long countLedgerEntries(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM credit_ledger_entry WHERE tenant_id = ?",
                Long.class,
                tenantId);
    }
}
