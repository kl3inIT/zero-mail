package com.zeromail.core.triage.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * H-3 — {@code TriageAuditWriter.recordCleanupArchive(...)} persists an audit row with {@code
 * source='CLEANUP_CAMPAIGN'} that is distinguishable from {@code source='TRIAGE'} rows. The partial
 * index {@code idx_triage_audit_cleanup} (changelog 046) is what makes per-campaign lookups fast.
 *
 * <p>Wave 0 RED: both the {@code recordCleanupArchive} method and changelog 046 (which adds the
 * {@code source} column + partial index) are introduced in Plan 02 Task 4 + Plan 03 Task 4. Until
 * then the {@code source} column does not exist on {@code triage_audit} and the reflective method
 * lookup throws {@link NoSuchMethodException}.
 */
@SuppressWarnings("SqlResolve")
class TriageAuditWriterCleanupArchiveTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TriageAuditWriter triageAuditWriter;
    @Autowired TriageAuditRepository triageAuditRepository;

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogCapture() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogCapture() {
        rootLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void recordCleanupArchive_persistsRowWithSourceCleanupCampaign() throws Exception {
        UUID tenantId = seedTenant();
        UUID attemptId = UUID.randomUUID();

        withTenant(
                tenantId,
                () ->
                        invokeRecordCleanupArchive(
                                triageAuditWriter,
                                tenantId,
                                "msg-1",
                                attemptId,
                                "Label_42",
                                "boss@example.com"));

        String source =
                jdbcTemplate.queryForObject(
                        """
                        select source from triage_audit
                        where tenant_id = ? and gmail_message_id = ?
                        """,
                        String.class,
                        tenantId,
                        "msg-1");
        String actionType =
                jdbcTemplate.queryForObject(
                        """
                        select action_type from triage_audit
                        where tenant_id = ? and gmail_message_id = ?
                        """,
                        String.class,
                        tenantId,
                        "msg-1");
        String externalRef =
                jdbcTemplate.queryForObject(
                        """
                        select external_ref from triage_audit
                        where tenant_id = ? and gmail_message_id = ?
                        """,
                        String.class,
                        tenantId,
                        "msg-1");
        String gmailChangeToken =
                jdbcTemplate.queryForObject(
                        """
                        select gmail_change_token from triage_audit
                        where tenant_id = ? and gmail_message_id = ?
                        """,
                        String.class,
                        tenantId,
                        "msg-1");
        String sanitizedSenderEmail =
                jdbcTemplate.queryForObject(
                        """
                        select sanitized_sender_email from triage_audit
                        where tenant_id = ? and gmail_message_id = ?
                        """,
                        String.class,
                        tenantId,
                        "msg-1");

        assertThat(source).isEqualTo("CLEANUP_CAMPAIGN");
        assertThat(actionType).isEqualTo("ARCHIVE");
        assertThat(externalRef).isEqualTo(attemptId.toString());
        assertThat(gmailChangeToken).contains("Label_42");
        assertThat(sanitizedSenderEmail).isEqualTo("boss@example.com");
    }

    @Test
    void recordCleanupArchive_doesNotInterfereWithSourceTriageRows() throws Exception {
        UUID tenantId = seedTenant();
        UUID attemptId = UUID.randomUUID();
        UUID triageAuditId = UUID.randomUUID();
        // Pre-seed a TRIAGE-sourced row for the same sender. Schema column names align with
        // changelogs 025 / 040 (sanitized_subject) / 046 (source). The legacy `subject_excerpt` /
        // `matcher_evidence` names from the Wave 0 RED stub never matched the real schema and are
        // dropped here.
        jdbcTemplate.update(
                """
                insert into triage_audit(
                    audit_id, tenant_id, gmail_message_id, gmail_thread_id, sanitized_subject,
                    sanitized_sender_email, rule_id, reason, action_type, args_hash,
                    action_args_json, decision, created_at, decided_at,
                    attempt_count, source, updated_at, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, NOW(), NOW(), 1, 'TRIAGE', NOW(), 0)
                """,
                triageAuditId,
                tenantId,
                "msg-coexist",
                "thread-coexist",
                "Subject",
                "boss@example.com",
                null,
                "Archive",
                "ARCHIVE",
                new byte[32],
                "{\"type\":\"archive\"}",
                "APPLIED");

        withTenant(
                tenantId,
                () ->
                        invokeRecordCleanupArchive(
                                triageAuditWriter,
                                tenantId,
                                "msg-coexist",
                                attemptId,
                                "Label_42",
                                "boss@example.com"));

        List<String> sources =
                jdbcTemplate.queryForList(
                        """
                        select source from triage_audit
                        where tenant_id = ? and gmail_message_id = ?
                        order by source
                        """,
                        String.class,
                        tenantId,
                        "msg-coexist");
        assertThat(sources).containsExactlyInAnyOrder("CLEANUP_CAMPAIGN", "TRIAGE");
    }

    @Test
    void recordCleanupArchive_logsEventWithDomainOnly() throws Exception {
        UUID tenantId = seedTenant();
        UUID attemptId = UUID.randomUUID();

        withTenant(
                tenantId,
                () ->
                        invokeRecordCleanupArchive(
                                triageAuditWriter,
                                tenantId,
                                "msg-log",
                                attemptId,
                                "Label_42",
                                "boss@example.com"));

        List<String> formattedLogLines =
                logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(formattedLogLines)
                .as("event=triage_audit_cleanup_archive_recorded must be emitted with senderDomain")
                .anyMatch(
                        line ->
                                line.contains("event=triage_audit_cleanup_archive_recorded")
                                        && line.contains("senderDomain=example.com"));
        assertThat(formattedLogLines)
                .as("full email must not appear in any log line")
                .noneMatch(line -> line.contains("boss@example.com"));
    }

    private UUID seedTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "cleanup-archive-" + tenantId);
        return tenantId;
    }

    private static Object invokeRecordCleanupArchive(
            TriageAuditWriter triageAuditWriter,
            UUID tenantId,
            String gmailMessageId,
            UUID attemptId,
            String labelId,
            String sanitizedSenderEmail) {
        try {
            return TriageAuditWriter.class
                    .getMethod(
                            "recordCleanupArchive",
                            UUID.class,
                            String.class,
                            UUID.class,
                            String.class,
                            String.class)
                    .invoke(
                            triageAuditWriter,
                            tenantId,
                            gmailMessageId,
                            attemptId,
                            labelId,
                            sanitizedSenderEmail);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            throw new RuntimeException(reflectiveOperationException);
        }
    }

    private static void withTenant(UUID tenantId, Runnable runnable) {
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(runnable);
    }
}
