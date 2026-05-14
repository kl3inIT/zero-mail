package com.zeromail.core.analytics.projection;

import com.zeromail.core.analytics.domain.TimeSavedWeights;
import com.zeromail.core.analytics.domain.TimeWindow;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsSummaryQueryService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSummaryQueryService.class);

    private static final String OBSERVED_VOLUME_SQL =
            """
            SELECT count(*)
            FROM mail_message_observed
            WHERE tenant_id = ?
              AND observed_at >= ?
              AND observed_at < ?
              AND 'INBOX' = ANY(label_ids)
            """;

    private static final String APPLIED_VOLUME_SQL =
            """
            SELECT count(DISTINCT gmail_message_id)
            FROM triage_audit
            WHERE tenant_id = ?
              AND applied_at >= ?
              AND applied_at < ?
              AND reverted_at IS NULL
            """;

    private static final String TIME_SAVED_SQL =
            """
            SELECT action_type, count(*)
            FROM triage_audit
            WHERE tenant_id = ?
              AND applied_at >= ?
              AND applied_at < ?
              AND reverted_at IS NULL
            GROUP BY action_type
            """;

    private static final String TOP_SENDERS_SQL =
            """
            SELECT sender_email, count(*) AS c
            FROM mail_message_observed
            WHERE tenant_id = ?
              AND observed_at >= ?
              AND observed_at < ?
              AND sender_email IS NOT NULL
              AND 'INBOX' = ANY(label_ids)
            GROUP BY sender_email
            ORDER BY c DESC, sender_email ASC
            LIMIT 3
            """;

    private static final String RULE_HITS_SQL =
            """
            SELECT rule_name_snapshot,
                   count(*) AS decisions,
                   count(*) FILTER (WHERE applied_at IS NOT NULL AND reverted_at IS NULL) AS applied,
                   count(*) FILTER (WHERE reverted_at IS NOT NULL) AS reverted
            FROM triage_audit
            WHERE tenant_id = ?
              AND decided_at >= ?
              AND decided_at < ?
            GROUP BY rule_name_snapshot
            ORDER BY decisions DESC, rule_name_snapshot ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsSummaryQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryProjection summarize(UUID tenantId, TimeWindow window) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        TimeWindow requestedWindow = Objects.requireNonNull(window, "window must not be null");
        Timestamp windowStartInclusive = Timestamp.from(requestedWindow.startInclusive());
        Timestamp windowEndExclusive = Timestamp.from(requestedWindow.endExclusive());

        long volumeObserved =
                queryCount(OBSERVED_VOLUME_SQL, tenantId, windowStartInclusive, windowEndExclusive);
        long volumeApplied =
                queryCount(APPLIED_VOLUME_SQL, tenantId, windowStartInclusive, windowEndExclusive);
        Map<String, Long> appliedByActionType =
                queryAppliedByActionType(tenantId, windowStartInclusive, windowEndExclusive);
        long timeSavedSeconds = TimeSavedWeights.computeSeconds(appliedByActionType);
        List<TopSenderProjection> topSenders =
                queryTopSenders(tenantId, windowStartInclusive, windowEndExclusive);
        List<RuleHitProjection> ruleHits =
                queryRuleHits(tenantId, windowStartInclusive, windowEndExclusive);

        log.info(
                "event=analytics_summary_computed tenantId={} windowStart={} windowEnd={}",
                tenantId,
                requestedWindow.startInclusive(),
                requestedWindow.endExclusive());
        return new AnalyticsSummaryProjection(
                volumeObserved, volumeApplied, timeSavedSeconds, topSenders, ruleHits);
    }

    private long queryCount(
            String sql,
            UUID tenantId,
            Timestamp windowStartInclusive,
            Timestamp windowEndExclusive) {
        Long count =
                jdbcTemplate.queryForObject(
                        sql, Long.class, tenantId, windowStartInclusive, windowEndExclusive);
        return count == null ? 0L : count;
    }

    private Map<String, Long> queryAppliedByActionType(
            UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
        Map<String, Long> appliedByActionType = new HashMap<>();
        jdbcTemplate.query(
                TIME_SAVED_SQL,
                resultSet -> {
                    String actionType = resultSet.getString("action_type");
                    if (actionType == null) {
                        return;
                    }
                    appliedByActionType.merge(
                            actionType,
                            resultSet.getLong(2),
                            (previousCount, duplicateCount) -> {
                                throw new IllegalStateException(
                                        "Duplicate action_type in time-saved query: " + actionType);
                            });
                },
                tenantId,
                windowStartInclusive,
                windowEndExclusive);
        return appliedByActionType;
    }

    private List<TopSenderProjection> queryTopSenders(
            UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
        return jdbcTemplate.query(
                TOP_SENDERS_SQL,
                (resultSet, rowNumber) ->
                        new TopSenderProjection(
                                resultSet.getString("sender_email"), resultSet.getLong("c")),
                tenantId,
                windowStartInclusive,
                windowEndExclusive);
    }

    private List<RuleHitProjection> queryRuleHits(
            UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
        return jdbcTemplate.query(
                RULE_HITS_SQL,
                (resultSet, rowNumber) ->
                        new RuleHitProjection(
                                resultSet.getString("rule_name_snapshot"),
                                resultSet.getLong("decisions"),
                                resultSet.getLong("applied"),
                                resultSet.getLong("reverted")),
                tenantId,
                windowStartInclusive,
                windowEndExclusive);
    }
}
