package com.zeromail.core.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.shared.pagination.KeysetCursor;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.triage.projection.AuditLogPage;
import com.zeromail.core.triage.projection.AuditLogPageQuery;
import com.zeromail.core.triage.projection.AuditLogQueryService;
import java.sql.Timestamp;
import java.time.Instant;
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
                    audit_id, tenant_id, gmail_message_id, gmail_thread_id, rule_name_snapshot,
                    action_type, args_hash, action_args_json, reason, decision, external_ref,
                    decided_at, created_at
                )
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)
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
                Timestamp.from(createdAt));
    }

    private static String actionArgsJson(String actionType) {
        return switch (actionType) {
            case "save_draft" ->
                    "{\"type\":\"save_draft\",\"body\":\"[generated]\",\"gmailThreadId\":\"thread\"}";
            case "archive" -> "{\"type\":\"archive\"}";
            default -> "{\"type\":\"label\",\"labelName\":\"Zero Mail\"}";
        };
    }
}
