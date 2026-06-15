package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.triage.domain.TriageUndoPolicy;
import com.zeromail.core.triage.projection.AuditLogPage;
import com.zeromail.core.triage.projection.AuditLogPageQuery;
import com.zeromail.core.triage.projection.DigestSourceItem;
import com.zeromail.core.triage.usecases.AuditLogQueryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class AuditLogQueryServiceTest extends PostgresContainerTest {

    @Autowired AuditLogQueryService auditLogQueryService;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void page_is_tenant_scoped_ordered_and_uses_full_precision_next_cursor() {
        UUID tenantA = seedTenant("audit-query-a");
        UUID tenantB = seedTenant("audit-query-b");
        UUID newestAuditId = UUID.fromString("00000000-0000-0000-0000-000000000503");
        UUID secondAuditId = UUID.fromString("00000000-0000-0000-0000-000000000502");
        UUID oldestAuditId = UUID.fromString("00000000-0000-0000-0000-000000000501");
        Instant newest = Instant.parse("2026-05-12T10:00:00.123456Z");
        Instant second = Instant.parse("2026-05-12T10:00:00.123455Z");
        Instant oldest = Instant.parse("2026-05-12T10:00:00.123454Z");
        insertAudit(tenantA, newestAuditId, "gmail-message-newest", "archive", newest, null);
        insertAudit(
                tenantA,
                secondAuditId,
                "gmail-message-second",
                "save_draft",
                second,
                "draft-second");
        insertAudit(tenantA, oldestAuditId, "gmail-message-oldest", "archive", oldest, null);
        insertAudit(
                tenantB,
                UUID.randomUUID(),
                "gmail-message-other",
                "archive",
                newest.plusSeconds(1),
                null);

        AuditLogPage firstPage =
                auditLogQueryService.page(
                        tenantA, new AuditLogPageQuery(2, null, null, null, null));

        assertThat(firstPage.items())
                .extracting(auditLogRow -> auditLogRow.auditId())
                .containsExactly(newestAuditId, secondAuditId);
        KeysetCursor nextCursor = KeysetCursor.decode(firstPage.nextCursor()).orElseThrow();
        assertThat(nextCursor.timestamp()).isEqualTo(second);
        assertThat(nextCursor.id()).isEqualTo(secondAuditId.toString());

        AuditLogPage secondPage =
                auditLogQueryService.page(
                        tenantA,
                        new AuditLogPageQuery(2, firstPage.nextCursor(), null, null, null));
        assertThat(secondPage.items())
                .extracting(auditLogRow -> auditLogRow.auditId())
                .containsExactly(oldestAuditId);
        assertThat(secondPage.nextCursor()).isNull();

        AuditLogPage draftOnlyPage =
                auditLogQueryService.page(
                        tenantA, new AuditLogPageQuery(10, null, "save_draft", null, null));
        assertThat(draftOnlyPage.items()).hasSize(1);
        assertThat(draftOnlyPage.items().getFirst().draftId()).isEqualTo("draft-second");
        assertThat(draftOnlyPage.items().getFirst().undoableUntil())
                .isEqualTo(TriageUndoPolicy.undoableUntil(second));
    }

    @Test
    void findDigestSourceItems_returns_only_applied_unreverted_digest_rows_within_window() {
        UUID tenant = seedTenant("digest-source");
        UUID otherTenant = seedTenant("digest-other");
        Instant windowStart = Instant.parse("2026-05-10T00:00:00Z");
        Instant sendMoment = Instant.parse("2026-05-17T00:00:00Z");
        Instant inWindowRecent = Instant.parse("2026-05-16T09:00:00Z");
        Instant inWindowOlder = Instant.parse("2026-05-11T09:00:00Z");

        insertDigestAudit(
                tenant,
                "msg-recent",
                "Newsletters",
                "news@example.test",
                "This week in tech",
                "add_to_digest",
                "APPLIED",
                inWindowRecent,
                null);
        insertDigestAudit(
                tenant,
                "msg-older",
                "Receipts",
                "billing@example.test",
                "Invoice 4021",
                "add_to_digest",
                "APPLIED",
                inWindowOlder,
                null);
        // Excluded: a different action type.
        insertDigestAudit(
                tenant,
                "msg-archive",
                "Receipts",
                "x@example.test",
                "Archived",
                "archive",
                "APPLIED",
                inWindowRecent,
                null);
        // Excluded: the digest tag was later reverted.
        insertDigestAudit(
                tenant,
                "msg-reverted",
                "Newsletters",
                "y@example.test",
                "Reverted",
                "add_to_digest",
                "REVERTED",
                inWindowRecent,
                Instant.parse("2026-05-16T10:00:00Z"));
        // Excluded: rule fired but was blocked, never applied.
        insertDigestAudit(
                tenant,
                "msg-rejected",
                "Newsletters",
                "z@example.test",
                "Rejected",
                "add_to_digest",
                "REJECTED_BY_SAFETY_POLICY",
                inWindowRecent,
                null);
        // Excluded: applied before the window opened.
        insertDigestAudit(
                tenant,
                "msg-stale",
                "Newsletters",
                "old@example.test",
                "Stale",
                "add_to_digest",
                "APPLIED",
                Instant.parse("2026-05-01T09:00:00Z"),
                null);
        // Excluded: belongs to another tenant.
        insertDigestAudit(
                otherTenant,
                "msg-other",
                "Newsletters",
                "o@example.test",
                "Other tenant",
                "add_to_digest",
                "APPLIED",
                inWindowRecent,
                null);

        List<DigestSourceItem> items =
                auditLogQueryService.findDigestSourceItems(tenant, windowStart, sendMoment, 20);

        assertThat(items)
                .extracting(DigestSourceItem::gmailMessageId)
                .containsExactly("msg-recent", "msg-older");
        DigestSourceItem mostRecent = items.getFirst();
        assertThat(mostRecent.sanitizedSubject()).isEqualTo("This week in tech");
        assertThat(mostRecent.sanitizedSenderEmail()).isEqualTo("news@example.test");
        assertThat(mostRecent.ruleNameSnapshot()).isEqualTo("Newsletters");
    }

    private UUID seedTenant(String displayNamePrefix) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                displayNamePrefix + "-" + tenantId);
        return tenantId;
    }

    private void insertAudit(
            UUID tenantId,
            UUID auditId,
            String gmailMessageId,
            String actionType,
            Instant createdAt,
            String externalReference) {
        jdbcTemplate.update(
                """
                insert into triage_audit(
                    audit_id, tenant_id, source_mailbox_id, executing_mailbox_id,
                    gmail_message_id, gmail_thread_id, rule_name_snapshot,
                    action_type, args_hash, action_args_json, reason, decision, external_ref,
                    decided_at, applied_at, created_at
                )
                values (?, ?, '00000000-0000-4000-8000-0000000000c1',
                    '00000000-0000-4000-8000-0000000000c1',
                    ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?)
                """,
                auditId,
                tenantId,
                gmailMessageId,
                "thread-" + gmailMessageId,
                "Rule " + gmailMessageId,
                actionType,
                new byte[32],
                actionArgsJson(actionType),
                "matched",
                "APPLIED",
                externalReference,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private void insertDigestAudit(
            UUID tenantId,
            String gmailMessageId,
            String ruleNameSnapshot,
            String sanitizedSenderEmail,
            String sanitizedSubject,
            String actionType,
            String decision,
            Instant appliedAt,
            Instant revertedAt) {
        jdbcTemplate.update(
                """
                insert into triage_audit(
                    audit_id, tenant_id, source_mailbox_id, executing_mailbox_id,
                    gmail_message_id, gmail_thread_id,
                    sanitized_subject, sanitized_sender_email, rule_id, rule_name_snapshot,
                    action_type, args_hash, action_args_json, reason, decision,
                    decided_at, applied_at, reverted_at, created_at
                )
                values (?, ?, '00000000-0000-4000-8000-0000000000c1',
                    '00000000-0000-4000-8000-0000000000c1',
                    ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                gmailMessageId,
                "thread-" + gmailMessageId,
                sanitizedSubject,
                sanitizedSenderEmail,
                UUID.randomUUID(),
                ruleNameSnapshot,
                actionType,
                new byte[32],
                actionArgsJson(actionType),
                "matched",
                decision,
                Timestamp.from(appliedAt),
                Timestamp.from(appliedAt),
                revertedAt == null ? null : Timestamp.from(revertedAt),
                Timestamp.from(appliedAt));
    }

    private static String actionArgsJson(String actionType) {
        return switch (actionType) {
            case "save_draft" ->
                    "{\"type\":\"save_draft\",\"body\":\"[generated]\",\"gmailThreadId\":\"thread\"}";
            case "archive" -> "{\"type\":\"archive\"}";
            case "add_to_digest" -> "{\"type\":\"add_to_digest\"}";
            default -> "{\"type\":\"label\",\"labelName\":\"Zero Mail\"}";
        };
    }
}
