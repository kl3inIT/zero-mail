package com.zeromail.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.zeromail.core.analytics.domain.TimeWindow;
import com.zeromail.core.analytics.projection.ActionMixProjection;
import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.projection.CategoryLoadProjection;
import com.zeromail.core.analytics.projection.DailyLoadProjection;
import com.zeromail.core.analytics.projection.DomainLoadProjection;
import com.zeromail.core.analytics.projection.ReplyBucketProjection;
import com.zeromail.core.analytics.projection.RuleHitProjection;
import com.zeromail.core.analytics.projection.TopSenderProjection;
import com.zeromail.core.analytics.usecases.AnalyticsSummaryQueryService;
import com.zeromail.core.support.PostgresContainerTest;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings("SqlResolve")
class AnalyticsSummaryQueryServiceTest extends PostgresContainerTest {

    @Autowired AnalyticsSummaryQueryService analyticsSummaryQueryService;

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void summarize_is_tenant_scoped_closed_window_and_filters_inbox_observed_messages() {
        UUID tenantA = seedTenant("analytics-summary-a");
        UUID tenantB = seedTenant("analytics-summary-b");
        Instant windowStartInclusive = Instant.parse("2026-05-01T00:00:00Z");
        Instant windowEndExclusive = Instant.parse("2026-05-08T00:00:00Z");

        insertObserved(
                tenantA,
                "analytics-a-observed-alpha",
                windowStartInclusive,
                "alpha@example.test",
                "INBOX",
                "CATEGORY_UPDATES");
        insertObserved(
                tenantA,
                "analytics-a-observed-bravo",
                windowStartInclusive.plusSeconds(60),
                "bravo@example.test",
                "INBOX",
                "CATEGORY_PROMOTIONS");
        insertObserved(
                tenantA,
                "analytics-a-observed-charlie",
                windowStartInclusive.plusSeconds(120),
                "charlie@example.test",
                "INBOX",
                "CATEGORY_UPDATES");
        insertObserved(
                tenantA,
                "analytics-a-observed-sent-a",
                windowStartInclusive.plusSeconds(180),
                "sent-a@example.test",
                "SENT");
        insertObserved(
                tenantA,
                "analytics-a-observed-sent-b",
                windowStartInclusive.plusSeconds(240),
                "sent-b@example.test",
                "SENT");
        insertObserved(
                tenantA,
                "analytics-a-observed-end-boundary",
                windowEndExclusive,
                "boundary@example.test",
                "INBOX");
        insertObserved(
                tenantA,
                "analytics-a-observed-before-window",
                windowStartInclusive.minusSeconds(1),
                "before@example.test",
                "INBOX");
        insertObserved(
                tenantB,
                "analytics-b-observed-sentinel",
                windowStartInclusive.plusSeconds(300),
                "tenant-b-sentinel@example.test",
                "INBOX");

        insertAudit(
                tenantA,
                "analytics-a-audit-priority-label",
                "Priority Rule",
                "label",
                "APPLIED",
                windowStartInclusive.plusSeconds(10),
                windowStartInclusive.plusSeconds(20),
                null);
        insertAudit(
                tenantA,
                "analytics-a-audit-priority-reverted",
                "Priority Rule",
                "archive",
                "REVERTED",
                windowStartInclusive.plusSeconds(30),
                windowStartInclusive.plusSeconds(40),
                windowStartInclusive.plusSeconds(50));
        insertAudit(
                tenantA,
                "analytics-a-audit-priority-label",
                "Priority Rule",
                "archive",
                "APPLIED",
                windowStartInclusive.plusSeconds(52),
                windowStartInclusive.plusSeconds(54),
                null);
        insertAudit(
                tenantA,
                "analytics-a-audit-alpha",
                "Alpha Tie Rule",
                "save_draft",
                "APPLIED",
                windowStartInclusive.plusSeconds(60),
                windowStartInclusive.plusSeconds(70),
                null);
        insertAudit(
                tenantA,
                "analytics-a-audit-beta",
                "Beta Tie Rule",
                "archive",
                "APPLIED",
                windowStartInclusive.plusSeconds(80),
                windowStartInclusive.plusSeconds(90),
                null);
        insertAudit(
                tenantA,
                "analytics-a-audit-end-boundary",
                "Boundary Rule",
                "label",
                "APPLIED",
                windowEndExclusive,
                windowEndExclusive,
                null);
        insertAudit(
                tenantB,
                "analytics-b-audit-sentinel",
                "Tenant B Sentinel",
                "save_draft",
                "APPLIED",
                windowStartInclusive.plusSeconds(100),
                windowStartInclusive.plusSeconds(110),
                null);
        insertReplyStatus(
                tenantA,
                "analytics-a-thread-to-reply",
                "TO_REPLY",
                windowStartInclusive.plusSeconds(200),
                true,
                false);
        insertReplyStatus(
                tenantA,
                "analytics-a-thread-awaiting",
                "AWAITING_THEIR_REPLY",
                windowStartInclusive.plusSeconds(220),
                false,
                false);
        insertReplyStatus(
                tenantA,
                "analytics-a-thread-resolved",
                "TO_REPLY",
                windowStartInclusive.plusSeconds(240),
                true,
                true);
        insertReplyStatus(
                tenantB,
                "analytics-b-thread-sentinel",
                "TO_REPLY",
                windowStartInclusive.plusSeconds(260),
                true,
                false);

        AnalyticsSummaryProjection summary =
                analyticsSummaryQueryService.summarize(
                        tenantA, TimeWindow.between(windowStartInclusive, windowEndExclusive));

        assertThat(summary.volumeObserved()).isEqualTo(3);
        assertThat(summary.volumeApplied()).isEqualTo(3);
        assertThat(summary.timeSavedSeconds()).isEqualTo(250);
        assertThat(summary.topSenders())
                .extracting(TopSenderProjection::senderEmail, TopSenderProjection::count)
                .containsExactly(
                        tuple("alpha@example.test", 1L),
                        tuple("bravo@example.test", 1L),
                        tuple("charlie@example.test", 1L));
        assertThat(summary.topSenders())
                .extracting(TopSenderProjection::senderEmail)
                .doesNotContain(
                        "sent-a@example.test",
                        "sent-b@example.test",
                        "boundary@example.test",
                        "tenant-b-sentinel@example.test");
        assertThat(summary.ruleHits())
                .extracting(
                        RuleHitProjection::ruleName,
                        RuleHitProjection::decisions,
                        RuleHitProjection::applied,
                        RuleHitProjection::reverted)
                .containsExactly(
                        tuple("Priority Rule", 3L, 2L, 1L),
                        tuple("Alpha Tie Rule", 1L, 1L, 0L),
                        tuple("Beta Tie Rule", 1L, 1L, 0L));
        assertThat(summary.dailyLoad())
                .extracting(
                        DailyLoadProjection::day,
                        DailyLoadProjection::observed,
                        DailyLoadProjection::applied,
                        DailyLoadProjection::reverted)
                .containsExactly(tuple("2026-05-01", 3L, 3L, 1L));
        assertThat(summary.actionMix())
                .extracting(
                        ActionMixProjection::actionType,
                        ActionMixProjection::applied,
                        ActionMixProjection::reverted,
                        ActionMixProjection::failed)
                .containsExactly(
                        tuple("archive", 2L, 1L, 0L),
                        tuple("label", 1L, 0L, 0L),
                        tuple("save_draft", 1L, 0L, 0L));
        assertThat(summary.domainLoad())
                .extracting(DomainLoadProjection::domain, DomainLoadProjection::count)
                .containsExactly(tuple("example.test", 3L));
        assertThat(summary.categoryLoad())
                .extracting(CategoryLoadProjection::category, CategoryLoadProjection::count)
                .containsExactly(tuple("updates", 2L), tuple("promotions", 1L));
        assertThat(summary.replyBuckets())
                .extracting(
                        ReplyBucketProjection::bucket,
                        ReplyBucketProjection::count,
                        ReplyBucketProjection::withDraft)
                .containsExactly(tuple("AWAITING_THEIR_REPLY", 1L, 0L), tuple("TO_REPLY", 1L, 1L));
        assertThat(summary.automationOpportunities().noRuleMatched()).isEqualTo(3);
        assertThat(summary.automationOpportunities().failedActions()).isZero();
        assertThat(summary.automationOpportunities().pendingActions()).isZero();
    }

    @Test
    void summarize_skips_null_senders_without_excluding_them_from_volume() {
        UUID tenantId = seedTenant("analytics-summary-null-sender");
        Instant windowStartInclusive = Instant.parse("2026-05-09T00:00:00Z");
        Instant windowEndExclusive = Instant.parse("2026-05-10T00:00:00Z");
        insertObserved(
                tenantId,
                "analytics-null-sender-visible",
                windowStartInclusive.plusSeconds(10),
                "visible@example.test",
                "INBOX");
        insertObserved(
                tenantId,
                "analytics-null-sender-hidden",
                windowStartInclusive.plusSeconds(20),
                null,
                "INBOX");

        AnalyticsSummaryProjection summary =
                analyticsSummaryQueryService.summarize(
                        tenantId, TimeWindow.between(windowStartInclusive, windowEndExclusive));

        assertThat(summary.volumeObserved()).isEqualTo(2);
        assertThat(summary.topSenders())
                .extracting(TopSenderProjection::senderEmail, TopSenderProjection::count)
                .containsExactly(tuple("visible@example.test", 1L));
    }

    private UUID seedTenant(String displayNamePrefix) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                displayNamePrefix + "-" + tenantId);
        return tenantId;
    }

    private void insertObserved(
            UUID tenantId,
            String gmailMessageId,
            Instant observedAt,
            String senderEmail,
            String... labelIds) {
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement preparedStatement =
                            connection.prepareStatement(
                                    """
                                            insert into mail_message_observed(
                                                tenant_id, gmail_message_id, gmail_thread_id, history_id,
                                                label_ids, internal_date, sender_email, observed_at
                                            )
                                            values (?, ?, ?, ?, ?, ?, ?, ?)
                                            """);
                    preparedStatement.setObject(1, tenantId);
                    preparedStatement.setString(2, gmailMessageId);
                    preparedStatement.setString(3, "thread-" + gmailMessageId);
                    preparedStatement.setLong(4, Math.abs((long) gmailMessageId.hashCode()));
                    preparedStatement.setArray(5, connection.createArrayOf("text", labelIds));
                    preparedStatement.setLong(6, observedAt.toEpochMilli());
                    if (senderEmail == null) {
                        preparedStatement.setNull(7, Types.VARCHAR);
                    } else {
                        preparedStatement.setString(7, senderEmail);
                    }
                    preparedStatement.setTimestamp(8, Timestamp.from(observedAt));
                    return preparedStatement;
                });
    }

    private void insertAudit(
            UUID tenantId,
            String gmailMessageId,
            String ruleName,
            String actionType,
            String decision,
            Instant decidedAt,
            Instant appliedAt,
            Instant revertedAt) {
        jdbcTemplate.update(
                """
                        insert into triage_audit(
                            audit_id, tenant_id, gmail_message_id, gmail_thread_id, rule_id,
                            rule_name_snapshot, action_type, args_hash, action_args_json, reason, decision,
                            decided_at, applied_at, reverted_at, created_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                tenantId,
                gmailMessageId,
                "thread-" + gmailMessageId,
                UUID.randomUUID(),
                ruleName,
                actionType,
                new byte[32],
                "{\"type\":\"" + actionType + "\"}",
                "analytics fixture",
                decision,
                Timestamp.from(decidedAt),
                timestampOrNull(appliedAt),
                timestampOrNull(revertedAt),
                Timestamp.from(decidedAt));
    }

    private void insertReplyStatus(
            UUID tenantId,
            String gmailThreadId,
            String bucket,
            Instant lastClassifiedAt,
            boolean hasDraft,
            boolean resolved) {
        jdbcTemplate.update(
                """
                        insert into thread_reply_status(
                            id, tenant_id, gmail_thread_id, bucket, last_classified_message_id,
                            last_classified_at, has_draft, draft_id, resolved, created_at, updated_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                tenantId,
                gmailThreadId,
                bucket,
                "message-" + gmailThreadId,
                Timestamp.from(lastClassifiedAt),
                hasDraft,
                hasDraft ? "draft-" + gmailThreadId : null,
                resolved,
                Timestamp.from(lastClassifiedAt),
                Timestamp.from(lastClassifiedAt));
    }

    private static Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
