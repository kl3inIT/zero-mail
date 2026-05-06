package com.zeromail.core.billing.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;

class CreditLedgerEntryUniqueTest extends PostgresContainerTest {

    @Autowired CreditLedgerEntryRepository creditLedgerEntryRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @Disabled("Wave 0 RED scaffold - production class lands in Plan 03")
    void unique_constraint_blocks_duplicate_ref_type_id_kind() {
        UUID tenantId = seedTenant();

        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
            creditLedgerEntryRepository.saveAndFlush(CreditLedgerEntryEntity.topup(
                    UUID.randomUUID(), tenantId, 25, "SEPAY-TX-1"));

            assertThatThrownBy(() -> creditLedgerEntryRepository.saveAndFlush(CreditLedgerEntryEntity.topup(
                    UUID.randomUUID(), tenantId, 25, "SEPAY-TX-1")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        });
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId, "billing-entry-unique-" + tenantId);
        return tenantId;
    }
}
