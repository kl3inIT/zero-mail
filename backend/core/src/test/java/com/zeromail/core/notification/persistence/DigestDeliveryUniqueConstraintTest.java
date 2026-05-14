package com.zeromail.core.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.support.PostgresContainerTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class DigestDeliveryUniqueConstraintTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void tenantAndLocalDay_uniqueConstraintRejectsSecondDeliveryRow() {
        UUID tenantId = seedTenant();
        LocalDate digestDayLocal = LocalDate.of(2026, 5, 13);

        insertDigestDelivery(tenantId, digestDayLocal);

        assertThatThrownBy(() -> insertDigestDelivery(tenantId, digestDayLocal))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long rowCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM digest_delivery
                        WHERE tenant_id = ? AND digest_day_local = ?
                        """,
                        Long.class,
                        tenantId,
                        digestDayLocal);
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void digestDeliveryTable_carriesExternalReferenceAndRetryColumns() {
        assertThat(columnExists("digest_delivery", "external_ref")).isTrue();
        assertThat(columnExists("digest_delivery", "next_attempt_at")).isTrue();
        assertThat(indexDefinition("idx_digest_delivery_reaper"))
                .contains("status")
                .contains("created_at");
        assertThat(constraintDefinition("uq_digest_delivery_tenant_day"))
                .contains("UNIQUE (tenant_id, digest_day_local)");
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "tenant-" + tenantId);
        return tenantId;
    }

    private void insertDigestDelivery(UUID tenantId, LocalDate digestDayLocal) {
        jdbcTemplate.update(
                """
                INSERT INTO digest_delivery(
                  id, tenant_id, digest_day_local, status, channel, attempt_count
                )
                VALUES (?, ?, ?, 'PENDING', 'EMAIL', 1)
                """,
                UUID.randomUUID(),
                tenantId,
                digestDayLocal);
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_name = ? AND column_name = ?
                        """,
                        Integer.class,
                        tableName,
                        columnName);
        return count != null && count == 1;
    }

    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, indexName);
    }

    private String constraintDefinition(String constraintName) {
        return jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
                String.class,
                constraintName);
    }
}
