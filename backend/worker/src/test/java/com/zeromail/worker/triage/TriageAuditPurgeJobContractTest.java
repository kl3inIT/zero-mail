package com.zeromail.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.worker.PostgresContainerTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TriageAuditPurgeJobContractTest extends PostgresContainerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-11T00:00:00Z");

    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTriageAuditTables() {
        jdbcTemplate.execute("DELETE FROM triage_audit");
        jdbcTemplate.execute("DELETE FROM tenants");
    }

    @Test
    void purge_deletes_aged_terminal_rows_by_decided_at() {
        UUID tenantId = seedTenant();
        UUID agedAppliedAuditId =
                seedAudit(
                        tenantId,
                        "gmail-message-aged-applied",
                        "APPLIED",
                        FIXED_NOW.minusSeconds(31 * 86_400),
                        FIXED_NOW.minusSeconds(31 * 86_400));
        UUID agedRevertedAuditId =
                seedAudit(
                        tenantId,
                        "gmail-message-aged-reverted",
                        "REVERTED",
                        FIXED_NOW.minusSeconds(31 * 86_400),
                        null);
        UUID freshAppliedAuditId =
                seedAudit(
                        tenantId,
                        "gmail-message-fresh-applied",
                        "APPLIED",
                        FIXED_NOW.minusSeconds(29 * 86_400),
                        FIXED_NOW.minusSeconds(29 * 86_400));
        UUID agedPendingAuditId =
                seedAudit(
                        tenantId,
                        "gmail-message-aged-pending",
                        "PENDING",
                        FIXED_NOW.minusSeconds(31 * 86_400),
                        null);

        TriageAuditPurgeBatch purgeBatch =
                new TriageAuditPurgeBatch(jdbcTemplate, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        TriageAuditPurgeJob purgeJob =
                new TriageAuditPurgeJob(purgeBatch, new SimpleMeterRegistry());

        int deletedCount = purgeJob.purge();

        assertThat(deletedCount).isEqualTo(2);
        assertThat(auditExists(agedAppliedAuditId)).isFalse();
        assertThat(auditExists(agedRevertedAuditId)).isFalse();
        assertThat(auditExists(freshAppliedAuditId)).isTrue();
        assertThat(auditExists(agedPendingAuditId)).isTrue();
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "triage-audit-purge-" + tenantId);
        return tenantId;
    }

    private UUID seedAudit(
            UUID tenantId,
            String gmailMessageId,
            String decision,
            Instant decidedAt,
            Instant appliedAt) {
        UUID auditId = UUID.randomUUID();
        jdbcTemplate.update(
                """
        INSERT INTO triage_audit (
          audit_id, tenant_id, gmail_message_id, gmail_thread_id, rule_id, rule_name_snapshot,
          action_type, args_hash, action_args_json, gmail_change_token, reason, decision,
          external_ref, failure_reason, decided_at, applied_at, reverted_at, attempt_count,
          last_attempt_at, lease_owner, created_at, updated_at, version
        )
        VALUES (
          ?, ?, ?, ?, ?, ?, 'ARCHIVE', ?, CAST(? AS jsonb), NULL, ?, ?,
          NULL, NULL, ?, ?, NULL, 0, NULL, NULL, ?, ?, 0
        )
        """,
                auditId,
                tenantId,
                gmailMessageId,
                "thread-" + gmailMessageId,
                UUID.randomUUID(),
                "Archive",
                actionHashFor(auditId),
                "{\"type\":\"ARCHIVE\"}",
                "test:purge",
                decision,
                Timestamp.from(decidedAt),
                appliedAt == null ? null : Timestamp.from(appliedAt),
                Timestamp.from(decidedAt),
                Timestamp.from(decidedAt));
        return auditId;
    }

    private boolean auditExists(UUID auditId) {
        Long auditCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM triage_audit WHERE audit_id = ?",
                        Long.class,
                        auditId);
        return auditCount != null && auditCount > 0;
    }

    private static byte[] actionHashFor(UUID auditId) {
        byte[] actionHash = new byte[32];
        long mostSignificantBits = auditId.getMostSignificantBits();
        long leastSignificantBits = auditId.getLeastSignificantBits();
        for (int byteIndex = 0; byteIndex < 8; byteIndex++) {
            actionHash[byteIndex] = (byte) (mostSignificantBits >>> (byteIndex * 8));
            actionHash[byteIndex + 8] = (byte) (leastSignificantBits >>> (byteIndex * 8));
        }
        return actionHash;
    }
}
