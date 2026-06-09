package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.rules.domain.RuleActionType;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.triage.domain.TriageActionResult;
import com.zeromail.core.triage.domain.TriageDecision;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationEntity;
import com.zeromail.core.triage.persistence.TenantProtectedSenderObservationRepository;
import com.zeromail.core.triage.persistence.TenantSenderOptInEntity;
import com.zeromail.core.triage.persistence.TenantSenderOptInRepository;
import com.zeromail.core.triage.persistence.TriageAuditEntity;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import com.zeromail.core.triage.persistence.TriageAuditWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class TriageAuditPersistenceContractTest extends PostgresContainerTest {

    private static final String TRIAGE_AUDIT_ENTITY =
            "com.zeromail.core.triage.persistence.TriageAuditEntity";
    private static final String TRIAGE_AUDIT_REPOSITORY =
            "com.zeromail.core.triage.persistence.TriageAuditRepository";
    private static final String TRIAGE_AUDIT_WRITER =
            "com.zeromail.core.triage.persistence.TriageAuditWriter";
    private static final String TRIAGE_DECISION = "com.zeromail.core.triage.domain.TriageDecision";

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TriageAuditRepository triageAuditRepository;
    @Autowired TriageAuditWriter triageAuditWriter;
    @Autowired TenantSenderOptInRepository tenantSenderOptInRepository;

    @Autowired
    TenantProtectedSenderObservationRepository tenantProtectedSenderObservationRepository;

    @Test
    void audit_persistence_contract_types_are_present() {
        assertFutureTypePresent(TRIAGE_AUDIT_ENTITY);
        assertFutureTypePresent(TRIAGE_AUDIT_REPOSITORY);
        assertFutureTypePresent(TRIAGE_AUDIT_WRITER);
        assertFutureTypePresent(TRIAGE_DECISION);
    }

    @Test
    void audit_round_trip_is_tenant_scoped_and_json_validated() {
        UUID tenantA = seedTenant("triage-audit-tenant-a");
        UUID tenantB = seedTenant("triage-audit-tenant-b");
        UUID ruleId = UUID.randomUUID();

        UUID auditId =
                withTenant(
                        tenantA,
                        () ->
                                triageAuditWriter
                                        .insertPending(
                                                tenantA,
                                                primaryMailboxId(tenantA),
                                                primaryMailboxId(tenantA),
                                                "gmail-message-1",
                                                "gmail-thread-1",
                                                "Subject excerpt",
                                                "sender@example.com",
                                                ruleId,
                                                "Archive receipts",
                                                RuleActionType.ARCHIVE,
                                                new TriageActionResult.Archive(),
                                                "matcher:node-archive")
                                        .orElseThrow());

        TriageAuditEntity tenantAVisibleAudit =
                withTenant(tenantA, () -> triageAuditRepository.findById(auditId).orElseThrow());
        Optional<TriageAuditEntity> tenantBVisibleAudit =
                withTenant(tenantB, () -> triageAuditRepository.findById(auditId));
        Optional<TriageAuditEntity> tenantQualifiedAudit =
                withTenant(
                        tenantA,
                        () -> triageAuditRepository.findByAuditIdAndTenantId(auditId, tenantA));

        assertThat(tenantAVisibleAudit.getTenantId()).isEqualTo(tenantA);
        assertThat(tenantAVisibleAudit.getActionArgsJson()).contains("archive");
        assertThat(tenantAVisibleAudit.getArgsHash()).hasSize(32);
        assertThat(tenantAVisibleAudit.getDecision()).isEqualTo(TriageDecision.PENDING);
        assertThat(tenantBVisibleAudit).isEmpty();
        assertThat(tenantQualifiedAudit).isPresent();

        assertThatThrownBy(
                        () ->
                                new TriageAuditEntity(
                                                UUID.randomUUID(),
                                                tenantA,
                                                primaryMailboxId(tenantA),
                                                primaryMailboxId(tenantA),
                                                "gmail-message-invalid",
                                                "gmail-thread-invalid",
                                                null,
                                                null,
                                                RuleActionType.ARCHIVE,
                                                new byte[32],
                                                "{\"type\":\"archive\",\"body\":\"must-not-be-accepted\"}",
                                                null,
                                                "matcher:node-invalid",
                                                TriageDecision.PENDING,
                                                null,
                                                null,
                                                Instant.now(),
                                                null,
                                                null,
                                                0,
                                                null,
                                                null)
                                        .getActionArgsJson())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stale_pending_rows_are_reclaimed_but_fresh_leases_are_not() {
        UUID tenantId = seedTenant("triage-audit-lease");
        UUID auditId = insertPendingArchive(tenantId, "gmail-message-lease");

        int firstClaim =
                triageAuditRepository.reclaimStalePending(auditId, tenantId, "worker-initial");
        int freshLeaseClaim =
                triageAuditRepository.reclaimStalePending(auditId, tenantId, "worker-fresh");
        jdbcTemplate.update(
                "update triage_audit set last_attempt_at = NOW() - INTERVAL '3 minutes' where audit_id = ?",
                auditId);
        int staleLeaseClaim =
                triageAuditRepository.reclaimStalePending(auditId, tenantId, "worker-reclaimed");

        TriageAuditEntity reclaimedAudit =
                withTenant(tenantId, () -> triageAuditRepository.findById(auditId).orElseThrow());

        assertThat(firstClaim).isEqualTo(1);
        assertThat(freshLeaseClaim).isZero();
        assertThat(staleLeaseClaim).isEqualTo(1);
        assertThat(reclaimedAudit.getAttemptCount()).isEqualTo(2);
        assertThat(reclaimedAudit.getLeaseOwner()).isEqualTo("worker-reclaimed");
    }

    @Test
    void transition_methods_are_tenant_scoped_and_clear_pending_lease() {
        UUID tenantId = seedTenant("triage-audit-transition");
        UUID otherTenantId = seedTenant("triage-audit-transition-other");
        UUID auditId = insertPendingArchive(tenantId, "gmail-message-transition");

        assertThat(
                        triageAuditRepository.reclaimStalePending(
                                auditId, tenantId, "worker-transition"))
                .isEqualTo(1);
        assertThat(
                        triageAuditRepository.markApplied(
                                auditId,
                                otherTenantId,
                                null,
                                "{\"removedLabelIds\":[\"INBOX\"]}",
                                null))
                .isZero();
        assertThat(
                        triageAuditRepository.markApplied(
                                auditId, tenantId, null, "{\"removedLabelIds\":[\"INBOX\"]}", null))
                .isEqualTo(1);

        TriageAuditEntity appliedAudit =
                withTenant(tenantId, () -> triageAuditRepository.findById(auditId).orElseThrow());
        assertThat(appliedAudit.getDecision()).isEqualTo(TriageDecision.APPLIED);
        assertThat(appliedAudit.getLeaseOwner()).isNull();
        assertThat(appliedAudit.getGmailChangeToken()).contains("removedLabelIds");

        assertThat(triageAuditRepository.markRevertPending(auditId, tenantId)).isEqualTo(1);
        TriageAuditEntity revertPendingAudit =
                withTenant(tenantId, () -> triageAuditRepository.findById(auditId).orElseThrow());
        assertThat(revertPendingAudit.getDecision()).isEqualTo(TriageDecision.REVERT_PENDING);

        assertThat(triageAuditRepository.markReverted(auditId, tenantId, Instant.now()))
                .isEqualTo(1);
        TriageAuditEntity revertedAudit =
                withTenant(tenantId, () -> triageAuditRepository.findById(auditId).orElseThrow());
        assertThat(revertedAudit.getDecision()).isEqualTo(TriageDecision.REVERTED);
    }

    @Test
    void terminal_insert_and_stuck_pending_select_are_available_for_follow_on_workers() {
        UUID tenantId = seedTenant("triage-audit-terminal");

        Optional<UUID> terminalAuditId =
                withTenant(
                        tenantId,
                        () ->
                                triageAuditWriter.insertTerminal(
                                        tenantId,
                                        primaryMailboxId(tenantId),
                                        primaryMailboxId(tenantId),
                                        "gmail-message-terminal",
                                        "gmail-thread-terminal",
                                        "Subject excerpt",
                                        "sender@example.com",
                                        null,
                                        null,
                                        RuleActionType.ARCHIVE,
                                        new TriageActionResult.Archive(),
                                        "sender-net:protected",
                                        TriageDecision.REJECTED_BY_SAFETY_NET));
        Optional<UUID> duplicateTerminalAuditId =
                withTenant(
                        tenantId,
                        () ->
                                triageAuditWriter.insertTerminal(
                                        tenantId,
                                        primaryMailboxId(tenantId),
                                        primaryMailboxId(tenantId),
                                        "gmail-message-terminal",
                                        "gmail-thread-terminal",
                                        "Subject excerpt",
                                        "sender@example.com",
                                        null,
                                        null,
                                        RuleActionType.ARCHIVE,
                                        new TriageActionResult.Archive(),
                                        "sender-net:protected",
                                        TriageDecision.REJECTED_BY_SAFETY_NET));
        UUID pendingAuditId = insertPendingArchive(tenantId, "gmail-message-stuck");
        jdbcTemplate.update(
                """
        update triage_audit
        set created_at = NOW() - INTERVAL '31 minutes',
            last_attempt_at = NOW() - INTERVAL '31 minutes'
        where audit_id = ?
        """,
                pendingAuditId);

        assertThat(terminalAuditId).isPresent();
        assertThat(duplicateTerminalAuditId).isEmpty();
        assertThat(
                        withTenant(
                                tenantId,
                                () ->
                                        triageAuditRepository.findStuckPendingForReaping(
                                                Instant.now().minusSeconds(30 * 60),
                                                PageRequest.of(0, 10))))
                .extracting(TriageAuditEntity::getAuditId)
                .contains(pendingAuditId);
    }

    @Test
    void pendingAuditCanStoreBlockedSafetyNetPatternForAuditBadge() {
        UUID tenantId = seedTenant("triage-audit-safety-net-badge");

        UUID auditId =
                withTenant(
                        tenantId,
                        () ->
                                triageAuditWriter
                                        .insertPending(
                                                tenantId,
                                                primaryMailboxId(tenantId),
                                                primaryMailboxId(tenantId),
                                                "gmail-message-safety-net-badge",
                                                "gmail-thread-safety-net-badge",
                                                "Subject excerpt",
                                                "sender@acme.com",
                                                UUID.randomUUID(),
                                                "Archive VIP",
                                                RuleActionType.ARCHIVE,
                                                new TriageActionResult.Archive(),
                                                "sender-net:protected",
                                                "@acme.com")
                                        .orElseThrow());

        TriageAuditEntity auditEntity =
                withTenant(tenantId, () -> triageAuditRepository.findById(auditId).orElseThrow());

        assertThat(auditEntity.getBlockedBySafetyNetPattern()).isEqualTo("@acme.com");
    }

    @Test
    void sender_safety_repositories_are_tenant_scoped_sources_for_future_endpoints() {
        UUID tenantId = seedTenant("triage-sender-net");
        String senderEmail = "founder@example.com";

        withTenant(
                tenantId,
                () -> {
                    tenantSenderOptInRepository.saveAndFlush(
                            new TenantSenderOptInEntity(UUID.randomUUID(), tenantId, senderEmail));
                    tenantProtectedSenderObservationRepository.saveAndFlush(
                            new TenantProtectedSenderObservationEntity(
                                    UUID.randomUUID(), tenantId, senderEmail, Instant.now()));
                    return null;
                });

        assertThat(
                        withTenant(
                                tenantId,
                                () ->
                                        tenantSenderOptInRepository.existsByTenantIdAndSenderEmail(
                                                tenantId, senderEmail)))
                .isTrue();
        assertThat(
                        withTenant(
                                tenantId,
                                () ->
                                        tenantProtectedSenderObservationRepository
                                                .findByTenantIdAndSenderEmail(
                                                        tenantId, senderEmail)))
                .isPresent();
        assertThat(
                        withTenant(
                                tenantId,
                                () ->
                                        tenantProtectedSenderObservationRepository.findByTenantId(
                                                tenantId)))
                .extracting(TenantProtectedSenderObservationEntity::getSenderEmail)
                .containsExactly(senderEmail);
    }

    private UUID insertPendingArchive(UUID tenantId, String gmailMessageId) {
        return withTenant(
                tenantId,
                () ->
                        triageAuditWriter
                                .insertPending(
                                        tenantId,
                                        primaryMailboxId(tenantId),
                                        primaryMailboxId(tenantId),
                                        gmailMessageId,
                                        "thread-" + gmailMessageId,
                                        "Subject " + gmailMessageId,
                                        "sender@example.com",
                                        UUID.randomUUID(),
                                        "Archive",
                                        RuleActionType.ARCHIVE,
                                        new TriageActionResult.Archive(),
                                        "matcher:" + gmailMessageId)
                                .orElseThrow());
    }

    private UUID seedTenant(String displayNamePrefix) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                displayNamePrefix + "-" + tenantId);
        jdbcTemplate.update(
                "insert into gmail_connections(id, tenant_id, google_email, status, is_primary) values (?, ?, ?, 'CONNECTED', true)",
                UUID.randomUUID(),
                tenantId,
                displayNamePrefix + "@example.com");
        return tenantId;
    }

    private UUID primaryMailboxId(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "select id from gmail_connections where tenant_id = ? and is_primary = true",
                UUID.class,
                tenantId);
    }

    private static <T> T withTenant(UUID tenantId, TenantOperation<T> tenantOperation) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(tenantOperation::run);
    }

    private static void assertFutureTypePresent(String futureTypeName) {
        assertThatCode(() -> Class.forName(futureTypeName))
                .as("Future Phase 4 production type must exist: " + futureTypeName)
                .doesNotThrowAnyException();
    }

    @FunctionalInterface
    private interface TenantOperation<T> {
        T run();
    }
}
