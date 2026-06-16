package com.zeromail.core.cleanup.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.cleanup.domain.SuppressionReason;
import com.zeromail.core.cleanup.projection.SenderSuppressionProjection;
import com.zeromail.core.cleanup.usecases.SuppressionCrudService.AddSuppressionCommand;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * UNS-02 — Suppression list CRUD + auto-add heuristic (Wave 2 / Plan 04, flipped from Wave 0 RED to
 * GREEN).
 *
 * <ul>
 *   <li>{@link SuppressionCrudService} — manual add/remove of {@code (sender_email | sender_domain,
 *       reason, created_at)}, exactly one of {@code sender_email} / {@code sender_domain} NOT NULL.
 *   <li>{@link SuppressionAutoAddService} — heuristic: if user replied to {@code sender_email} ≥1
 *       time in the last 90 days (via SAVE_DRAFT / APPLIED triage audit), auto-insert with {@code
 *       reason='replied'}.
 * </ul>
 */
@SuppressWarnings("SqlResolve")
class SuppressionServiceTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired SuppressionCrudService suppressionCrudService;

    @Autowired SuppressionAutoAddService suppressionAutoAddService;

    @Test
    void addSenderEmail_isPersisted() {
        UUID tenantId = seedTenant();

        SenderSuppressionProjection projection =
                withTenant(
                        tenantId,
                        () ->
                                suppressionCrudService.addManual(
                                        tenantId,
                                        new AddSuppressionCommand(
                                                "boss@example.com",
                                                null,
                                                SuppressionReason.MANUAL)));

        Long rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from sender_suppression where id = ?",
                        Long.class,
                        projection.id());
        assertThat(rowCount).as("row must be persisted").isEqualTo(1L);
        assertThat(projection.senderEmail()).isEqualTo("boss@example.com");
        assertThat(projection.senderDomain()).isNull();
        assertThat(projection.reason()).isEqualTo(SuppressionReason.MANUAL);
    }

    @Test
    void addSenderDomain_isPersisted() {
        UUID tenantId = seedTenant();

        SenderSuppressionProjection projection =
                withTenant(
                        tenantId,
                        () ->
                                suppressionCrudService.addManual(
                                        tenantId,
                                        new AddSuppressionCommand(
                                                null, "blocked.test", SuppressionReason.MANUAL)));

        Long rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from sender_suppression where id = ?",
                        Long.class,
                        projection.id());
        assertThat(rowCount).as("row must be persisted").isEqualTo(1L);
        assertThat(projection.senderEmail()).isNull();
        assertThat(projection.senderDomain()).isEqualTo("blocked.test");
    }

    @Test
    void add_rejectsBothNull() {
        UUID tenantId = seedTenant();

        assertThatThrownBy(
                        () ->
                                withTenant(
                                        tenantId,
                                        () ->
                                                suppressionCrudService.addManual(
                                                        tenantId,
                                                        new AddSuppressionCommand(
                                                                null,
                                                                null,
                                                                SuppressionReason.MANUAL))))
                .as("must reject when both sender_email and sender_domain are null")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void add_rejectsBothNonNull() {
        UUID tenantId = seedTenant();

        assertThatThrownBy(
                        () ->
                                withTenant(
                                        tenantId,
                                        () ->
                                                suppressionCrudService.addManual(
                                                        tenantId,
                                                        new AddSuppressionCommand(
                                                                "boss@example.com",
                                                                "example.com",
                                                                SuppressionReason.MANUAL))))
                .as("must reject when both sender_email and sender_domain are set")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void remove_byId_softCheckRowAbsent() {
        UUID tenantId = seedTenant();
        SenderSuppressionProjection added =
                withTenant(
                        tenantId,
                        () ->
                                suppressionCrudService.addManual(
                                        tenantId,
                                        new AddSuppressionCommand(
                                                "removable@example.com",
                                                null,
                                                SuppressionReason.MANUAL)));

        TenantContext.runWith(tenantId, () -> suppressionCrudService.remove(tenantId, added.id()));

        Long rowCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from sender_suppression where id = ?",
                        Long.class,
                        added.id());
        assertThat(rowCount).as("row must be removed").isZero();
    }

    @Test
    void autoAddAfterUserRepliedOnce_within90d_appearsAsRepliedReason() {
        UUID tenantId = seedTenant();
        String repliedSenderEmail = "newsletter@replied.test";
        // Seed an APPLIED SAVE_DRAFT row from "30 days ago" so the 90-day window catches it
        // regardless of test wall-clock.
        Instant repliedAt = Instant.now().minusSeconds(60L * 60L * 24L * 30L);
        seedUserReplyAudit(tenantId, repliedSenderEmail, repliedAt);

        int insertedCount =
                withTenant(tenantId, () -> suppressionAutoAddService.scanAndAutoAdd(tenantId));

        assertThat(insertedCount).as("one new suppression row must be added").isEqualTo(1);
        String reason =
                jdbcTemplate.queryForObject(
                        """
                        select reason from sender_suppression
                        where tenant_id = ? and sender_email = ?
                        """,
                        String.class,
                        tenantId,
                        repliedSenderEmail);
        assertThat(reason).as("auto-add must set reason='replied'").isEqualTo("replied");
    }

    private static <T> T withTenant(UUID tenantId, java.util.function.Supplier<T> supplier) {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString()).call(supplier::get);
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "suppression-" + tenantId);
        return tenantId;
    }

    private void seedUserReplyAudit(UUID tenantId, String senderEmail, Instant repliedAt) {
        // Mirrors the triage_audit schema columns that exist after Phase 7 ships. The
        // SuppressionAutoAddService scan looks for action_type='SAVE_DRAFT' + decision='APPLIED'
        // rows whose sanitized_sender_email is not yet on the suppression list.
        jdbcTemplate.update(
                """
                insert into triage_audit(
                    audit_id, tenant_id, source_mailbox_id, executing_mailbox_id,
                    gmail_message_id, gmail_thread_id, sanitized_subject,
                    sanitized_sender_email, rule_id, reason, action_type, args_hash,
                    action_args_json, decision, created_at, decided_at, attempt_count)
                values (?, ?, '00000000-0000-4000-8000-0000000000c1',
                    '00000000-0000-4000-8000-0000000000c1',
                    ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                "reply-source-" + UUID.randomUUID(),
                "reply-thread-" + UUID.randomUUID(),
                "Subject",
                senderEmail,
                null,
                "user replied",
                "SAVE_DRAFT",
                new byte[32],
                "{\"type\":\"save_draft\"}",
                "APPLIED",
                java.sql.Timestamp.from(repliedAt),
                java.sql.Timestamp.from(repliedAt),
                1);
    }
}
