package com.zeromail.core.analytics.usecases;

import com.zeromail.core.analytics.domain.TimeSavedWeights;
import com.zeromail.core.analytics.domain.TimeWindow;
import com.zeromail.core.analytics.projection.ActionMixProjection;
import com.zeromail.core.analytics.projection.AnalyticsSummaryProjection;
import com.zeromail.core.analytics.projection.AutomationOpportunityProjection;
import com.zeromail.core.analytics.projection.CategoryLoadProjection;
import com.zeromail.core.analytics.projection.DailyLoadProjection;
import com.zeromail.core.analytics.projection.DomainLoadProjection;
import com.zeromail.core.analytics.projection.ReplyBucketProjection;
import com.zeromail.core.analytics.projection.RuleHitProjection;
import com.zeromail.core.analytics.projection.TopSenderProjection;
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
                    LIMIT 10
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

    private static final String DAILY_LOAD_SQL =
            """
                    WITH observed AS (
                        SELECT date_trunc('day', observed_at)::date AS bucket, count(*) AS observed
                        FROM mail_message_observed
                        WHERE tenant_id = ?
                          AND observed_at >= ?
                          AND observed_at < ?
                          AND 'INBOX' = ANY(label_ids)
                        GROUP BY bucket
                    ),
                    applied AS (
                        SELECT date_trunc('day', applied_at)::date AS bucket,
                               count(DISTINCT gmail_message_id) AS applied
                        FROM triage_audit
                        WHERE tenant_id = ?
                          AND applied_at >= ?
                          AND applied_at < ?
                          AND reverted_at IS NULL
                        GROUP BY bucket
                    ),
                    reverted AS (
                        SELECT date_trunc('day', reverted_at)::date AS bucket, count(*) AS reverted
                        FROM triage_audit
                        WHERE tenant_id = ?
                          AND reverted_at >= ?
                          AND reverted_at < ?
                        GROUP BY bucket
                    ),
                    buckets AS (
                        SELECT bucket FROM observed
                        UNION
                        SELECT bucket FROM applied
                        UNION
                        SELECT bucket FROM reverted
                    )
                    SELECT buckets.bucket::text AS bucket,
                           COALESCE(observed.observed, 0) AS observed,
                           COALESCE(applied.applied, 0) AS applied,
                           COALESCE(reverted.reverted, 0) AS reverted
                    FROM buckets
                    LEFT JOIN observed ON observed.bucket = buckets.bucket
                    LEFT JOIN applied ON applied.bucket = buckets.bucket
                    LEFT JOIN reverted ON reverted.bucket = buckets.bucket
                    ORDER BY buckets.bucket
                    """;

    private static final String ACTION_MIX_SQL =
            """
                    SELECT action_type,
                           count(*) FILTER (WHERE applied_at IS NOT NULL AND reverted_at IS NULL) AS applied,
                           count(*) FILTER (WHERE reverted_at IS NOT NULL) AS reverted,
                           count(*) FILTER (WHERE decision = 'FAILED') AS failed
                    FROM triage_audit
                    WHERE tenant_id = ?
                      AND decided_at >= ?
                      AND decided_at < ?
                    GROUP BY action_type
                    ORDER BY (count(*) FILTER (WHERE applied_at IS NOT NULL AND reverted_at IS NULL)) DESC,
                             action_type ASC
                    """;

    private static final String DOMAIN_LOAD_SQL =
            """
                    SELECT lower(split_part(sender_email, '@', 2)) AS domain, count(*) AS c
                    FROM mail_message_observed
                    WHERE tenant_id = ?
                      AND observed_at >= ?
                      AND observed_at < ?
                      AND sender_email IS NOT NULL
                      AND position('@' in sender_email) > 0
                      AND 'INBOX' = ANY(label_ids)
                    GROUP BY domain
                    ORDER BY c DESC, domain ASC
                    LIMIT 8
                    """;

    private static final String CATEGORY_LOAD_SQL =
            """
                    SELECT lower(replace(label_id, 'CATEGORY_', '')) AS category, count(*) AS c
                    FROM mail_message_observed observed_message
                    CROSS JOIN LATERAL unnest(label_ids) AS labels(label_id)
                    WHERE observed_message.tenant_id = ?
                      AND observed_message.observed_at >= ?
                      AND observed_message.observed_at < ?
                      AND label_id LIKE 'CATEGORY_%'
                      AND 'INBOX' = ANY(observed_message.label_ids)
                    GROUP BY category
                    ORDER BY c DESC, category ASC
                    """;

    private static final String REPLY_BUCKETS_SQL =
            """
                    SELECT bucket,
                           count(*) AS c,
                           count(*) FILTER (WHERE has_draft) AS with_draft
                    FROM thread_reply_status
                    WHERE tenant_id = ?
                      AND last_classified_at >= ?
                      AND last_classified_at < ?
                      AND NOT resolved
                    GROUP BY bucket
                    ORDER BY bucket ASC
                    """;

    private static final String NO_RULE_MATCHED_SQL =
            """
                    SELECT count(*)
                    FROM mail_message_observed observed_message
                    WHERE observed_message.tenant_id = ?
                      AND observed_message.observed_at >= ?
                      AND observed_message.observed_at < ?
                      AND 'INBOX' = ANY(observed_message.label_ids)
                      AND NOT EXISTS (
                          SELECT 1
                          FROM triage_audit audit
                          WHERE audit.tenant_id = observed_message.tenant_id
                            AND audit.gmail_message_id = observed_message.gmail_message_id
                            AND audit.decided_at >= ?
                            AND audit.decided_at < ?
                      )
                    """;

    private static final String OPPORTUNITY_ACTIONS_SQL =
            """
                    SELECT decision, count(*) AS c
                    FROM triage_audit
                    WHERE tenant_id = ?
                      AND decided_at >= ?
                      AND decided_at < ?
                      AND decision IN ('FAILED', 'PENDING')
                    GROUP BY decision
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
        List<DailyLoadProjection> dailyLoad =
                queryDailyLoad(tenantId, windowStartInclusive, windowEndExclusive);
        List<ActionMixProjection> actionMix =
                queryActionMix(tenantId, windowStartInclusive, windowEndExclusive);
        List<DomainLoadProjection> domainLoad =
                queryDomainLoad(tenantId, windowStartInclusive, windowEndExclusive);
        List<CategoryLoadProjection> categoryLoad =
                queryCategoryLoad(tenantId, windowStartInclusive, windowEndExclusive);
        List<ReplyBucketProjection> replyBuckets =
                queryReplyBuckets(tenantId, windowStartInclusive, windowEndExclusive);
        AutomationOpportunityProjection automationOpportunities =
                queryAutomationOpportunities(tenantId, windowStartInclusive, windowEndExclusive);

        log.info(
                "event=analytics_summary_computed tenantId={} windowStart={} windowEnd={}",
                tenantId,
                requestedWindow.startInclusive(),
                requestedWindow.endExclusive());
        return new AnalyticsSummaryProjection(
                volumeObserved,
                volumeApplied,
                timeSavedSeconds,
                topSenders,
                ruleHits,
                dailyLoad,
                actionMix,
                domainLoad,
                categoryLoad,
                replyBuckets,
                automationOpportunities);
    }

    private long queryCount(String queryText, Object... parameters) {
        Long count = jdbcTemplate.queryForObject(queryText, Long.class, parameters);
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

    private List<DailyLoadProjection> queryDailyLoad(
            UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
        return jdbcTemplate.query(
                DAILY_LOAD_SQL,
                (resultSet, rowNumber) ->
                        new DailyLoadProjection(
                                resultSet.getString("bucket"),
                                resultSet.getLong("observed"),
                                resultSet.getLong("applied"),
                                resultSet.getLong("reverted")),
                tenantId,
                windowStartInclusive,
                windowEndExclusive,
                tenantId,
                windowStartInclusive,
                windowEndExclusive,
                tenantId,
                windowStartInclusive,
                windowEndExclusive);
    }

    private List<ActionMixProjection> queryActionMix(
            UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
        return jdbcTemplate.query(
                ACTION_MIX_SQL,
                (resultSet, rowNumber) ->
                        new ActionMixProjection(
                                resultSet.getString("action_type"),
                                resultSet.getLong("applied"),
                                resultSet.getLong("reverted"),
                                resultSet.getLong("failed")),
                tenantId,
                windowStartInclusive,
                windowEndExclusive);
    }

    private List<DomainLoadProjection> queryDomainLoad(
            UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
        return jdbcTemplate.query(
                DOMAIN_LOAD_SQL,
                (resultSet, rowNumber) ->
                        new DomainLoadProjection(
                                resultSet.getString("domain"), resultSet.getLong("c")),
                tenantId,
                windowStartInclusive,
                windowEndExclusive);
    }

    private List<CategoryLoadProjection> queryCategoryLoad(
            UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
        return jdbcTemplate.query(
                CATEGORY_LOAD_SQL,
                (resultSet, rowNumber) ->
                        new CategoryLoadProjection(
                                resultSet.getString("category"), resultSet.getLong("c")),
                tenantId,
                windowStartInclusive,
                windowEndExclusive);
    }

    private List<ReplyBucketProjection> queryReplyBuckets(
            UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
        return jdbcTemplate.query(
                REPLY_BUCKETS_SQL,
                (resultSet, rowNumber) ->
                        new ReplyBucketProjection(
                                resultSet.getString("bucket"),
                                resultSet.getLong("c"),
                                resultSet.getLong("with_draft")),
                tenantId,
                windowStartInclusive,
                windowEndExclusive);
    }

    private AutomationOpportunityProjection queryAutomationOpportunities(
            UUID tenantId, Timestamp windowStartInclusive, Timestamp windowEndExclusive) {
        long noRuleMatched =
                queryCount(
                        NO_RULE_MATCHED_SQL,
                        tenantId,
                        windowStartInclusive,
                        windowEndExclusive,
                        windowStartInclusive,
                        windowEndExclusive);
        Map<String, Long> actionCountsByDecision = new HashMap<>();
        jdbcTemplate.query(
                OPPORTUNITY_ACTIONS_SQL,
                resultSet -> {
                    actionCountsByDecision.put(
                            resultSet.getString("decision"), resultSet.getLong("c"));
                },
                tenantId,
                windowStartInclusive,
                windowEndExclusive);
        return new AutomationOpportunityProjection(
                noRuleMatched,
                actionCountsByDecision.getOrDefault("FAILED", 0L),
                actionCountsByDecision.getOrDefault("PENDING", 0L));
    }
}
