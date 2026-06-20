package com.zeromail.core.admin.overview.persistence.lowlevel;

import com.zeromail.core.admin.overview.projection.AdminOverviewActionDistribution;
import com.zeromail.core.admin.overview.projection.AdminOverviewDailyActivityPoint;
import com.zeromail.core.admin.overview.projection.AdminOverviewKpis;
import com.zeromail.core.admin.overview.projection.AdminOverviewQuery;
import com.zeromail.core.admin.overview.projection.AdminOverviewTopActivityTenant;
import com.zeromail.core.admin.overview.projection.AdminOverviewTopSpendTenant;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminOverviewReadRepository {

    private static final int QUERY_TIMEOUT_SECONDS = 15;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public AdminOverviewReadRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
        namedParameterJdbcTemplate.getJdbcTemplate().setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    public AdminOverviewKpis findKpis(AdminOverviewQuery adminOverviewQuery, Instant activeCutoff) {
        MapSqlParameterSource parameters =
                rangeParameters(adminOverviewQuery)
                        .addValue("activeCutoff", Timestamp.from(activeCutoff));
        return namedParameterJdbcTemplate.queryForObject(
                """
                WITH recent_activity AS (
                    SELECT tenants.id AS tenant_id
                    FROM tenants
                    WHERE tenants.created_at >= :activeCutoff
                      AND tenants.created_at < :to

                    UNION ALL

                    SELECT observed.tenant_id
                    FROM mail_message_observed observed
                    WHERE observed.observed_at >= :activeCutoff
                      AND observed.observed_at < :to

                    UNION ALL

                    SELECT triage.tenant_id
                    FROM triage_audit triage
                    WHERE triage.created_at >= :activeCutoff
                      AND triage.created_at < :to

                    UNION ALL

                    SELECT llm_call.tenant_id
                    FROM llm_call_audit llm_call
                    WHERE llm_call.created_at >= :activeCutoff
                      AND llm_call.created_at < :to

                    UNION ALL

                    SELECT assistant_action.tenant_id
                    FROM assistant_action_audit assistant_action
                    WHERE assistant_action.created_at >= :activeCutoff
                      AND assistant_action.created_at < :to

                    UNION ALL

                    SELECT chat.tenant_id
                    FROM chat
                    WHERE chat.updated_at >= :activeCutoff
                      AND chat.updated_at < :to
                      AND chat.soft_deleted_at IS NULL
                ),
                credit_balance AS (
                    SELECT credit.tenant_id,
                           COALESCE(SUM(credit.amount_credits), 0) AS credit_balance
                    FROM credit_ledger_entry credit
                    GROUP BY credit.tenant_id
                )
                SELECT
                    (SELECT COUNT(*)::int FROM tenants) AS total_tenants,
                    (
                        SELECT COUNT(DISTINCT gmail.tenant_id)::int
                        FROM gmail_connections gmail
                        WHERE gmail.status = 'CONNECTED'
                    ) AS gmail_connected_tenants,
                    (
                        SELECT COUNT(DISTINCT recent_activity.tenant_id)::int
                        FROM recent_activity
                        WHERE recent_activity.tenant_id IS NOT NULL
                    ) AS active_last_7d_tenants,
                    (
                        SELECT COUNT(*)::int
                        FROM mail_message_observed observed
                        WHERE observed.observed_at >= :from
                          AND observed.observed_at < :to
                    ) AS observed_email_count,
                    (
                        SELECT COUNT(*)::int
                        FROM triage_audit triage
                        WHERE triage.created_at >= :from
                          AND triage.created_at < :to
                    ) AS triage_action_count,
                    (
                        SELECT COUNT(*)::int
                        FROM triage_audit triage
                        WHERE triage.created_at >= :from
                          AND triage.created_at < :to
                          AND triage.decision = 'FAILED'
                    ) AS failed_triage_action_count,
                    (
                        SELECT COUNT(*)::int
                        FROM triage_audit triage
                        WHERE triage.created_at >= :from
                          AND triage.created_at < :to
                          AND triage.action_type IN ('send_reply', 'forward_email', 'send_email')
                    ) AS outbound_action_count,
                    (
                        SELECT COUNT(*)::int
                        FROM triage_audit triage
                        WHERE triage.created_at >= :from
                          AND triage.created_at < :to
                          AND triage.action_type IN ('send_reply', 'forward_email', 'send_email')
                          AND triage.decision IN (
                              'REJECTED_BY_SAFETY_NET',
                              'REJECTED_BY_SAFETY_POLICY'
                          )
                    ) AS blocked_outbound_action_count,
                    (
                        SELECT COUNT(*)::int
                        FROM llm_call_audit llm_call
                        WHERE llm_call.created_at >= :from
                          AND llm_call.created_at < :to
                    ) AS llm_call_count,
                    (
                        SELECT COALESCE(SUM(llm_call.charged_credits), 0)::bigint
                        FROM llm_call_audit llm_call
                        WHERE llm_call.created_at >= :from
                          AND llm_call.created_at < :to
                    ) AS llm_charged_credits,
                    (
                        SELECT COALESCE(SUM(llm_call.total_cost_usd), 0)
                        FROM llm_call_audit llm_call
                        WHERE llm_call.created_at >= :from
                          AND llm_call.created_at < :to
                    ) AS llm_cost_usd,
                    (
                        SELECT COUNT(*)::int
                        FROM tenants tenant
                        WHERE NOT EXISTS (
                            SELECT 1
                            FROM gmail_connections connected_gmail
                            WHERE connected_gmail.tenant_id = tenant.id
                              AND connected_gmail.status = 'CONNECTED'
                        )
                    ) AS gmail_unhealthy_tenants,
                    (
                        SELECT COUNT(*)::int
                        FROM pubsub_delivery delivery
                        WHERE delivery.status = 'PENDING'
                    ) AS pubsub_backlog_count,
                    (
                        SELECT COUNT(*)::int
                        FROM processing_job job
                        WHERE job.status = 'DEAD_LETTER'
                    ) AS dead_letter_job_count,
                    (
                        SELECT COUNT(*)::int
                        FROM tenants tenant
                        LEFT JOIN credit_balance ON credit_balance.tenant_id = tenant.id
                        WHERE COALESCE(credit_balance.credit_balance, 0) < 100
                    ) AS low_credit_tenant_count
                """,
                parameters,
                (resultSet, _) ->
                        new AdminOverviewKpis(
                                resultSet.getInt("total_tenants"),
                                resultSet.getInt("gmail_connected_tenants"),
                                resultSet.getInt("active_last_7d_tenants"),
                                resultSet.getInt("observed_email_count"),
                                resultSet.getInt("triage_action_count"),
                                resultSet.getInt("failed_triage_action_count"),
                                resultSet.getInt("outbound_action_count"),
                                resultSet.getInt("blocked_outbound_action_count"),
                                resultSet.getInt("llm_call_count"),
                                resultSet.getLong("llm_charged_credits"),
                                resultSet.getBigDecimal("llm_cost_usd"),
                                resultSet.getInt("gmail_unhealthy_tenants"),
                                resultSet.getInt("pubsub_backlog_count"),
                                resultSet.getInt("dead_letter_job_count"),
                                resultSet.getInt("low_credit_tenant_count")));
    }

    public List<AdminOverviewDailyActivityPoint> findDailyActivity(
            AdminOverviewQuery adminOverviewQuery) {
        return namedParameterJdbcTemplate.query(
                """
                WITH days AS (
                    SELECT generate_series(
                        date_trunc('day', CAST(:from AS timestamptz)),
                        date_trunc('day', CAST(:to AS timestamptz)) - INTERVAL '1 day',
                        INTERVAL '1 day'
                    ) AS bucket_start
                ),
                observed_by_day AS (
                    SELECT date_trunc('day', observed.observed_at) AS bucket_start,
                           COUNT(*)::int AS observed_email_count
                    FROM mail_message_observed observed
                    WHERE observed.observed_at >= :from
                      AND observed.observed_at < :to
                    GROUP BY date_trunc('day', observed.observed_at)
                ),
                triage_by_day AS (
                    SELECT date_trunc('day', triage.created_at) AS bucket_start,
                           COUNT(*)::int AS triage_action_count,
                           (COUNT(*) FILTER (WHERE triage.decision = 'FAILED'))::int
                               AS failed_triage_action_count
                    FROM triage_audit triage
                    WHERE triage.created_at >= :from
                      AND triage.created_at < :to
                    GROUP BY date_trunc('day', triage.created_at)
                )
                SELECT TO_CHAR(days.bucket_start AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS bucket_date,
                       COALESCE(observed_by_day.observed_email_count, 0)
                           AS observed_email_count,
                       COALESCE(triage_by_day.triage_action_count, 0)
                           AS triage_action_count,
                       COALESCE(triage_by_day.failed_triage_action_count, 0)
                           AS failed_triage_action_count
                FROM days
                LEFT JOIN observed_by_day ON observed_by_day.bucket_start = days.bucket_start
                LEFT JOIN triage_by_day ON triage_by_day.bucket_start = days.bucket_start
                ORDER BY days.bucket_start ASC
                """,
                rangeParameters(adminOverviewQuery),
                (resultSet, _) ->
                        new AdminOverviewDailyActivityPoint(
                                resultSet.getString("bucket_date"),
                                resultSet.getInt("observed_email_count"),
                                resultSet.getInt("triage_action_count"),
                                resultSet.getInt("failed_triage_action_count")));
    }

    public List<AdminOverviewActionDistribution> findActionDistribution(
            AdminOverviewQuery adminOverviewQuery) {
        return namedParameterJdbcTemplate.query(
                """
                WITH counts AS (
                    SELECT
                        (
                            SELECT COUNT(*)::int
                            FROM mail_message_observed observed
                            WHERE observed.observed_at >= :from
                              AND observed.observed_at < :to
                        ) AS observed_email_count,
                        (
                            SELECT COUNT(*)::int
                            FROM triage_audit triage
                            WHERE triage.created_at >= :from
                              AND triage.created_at < :to
                              AND triage.decision NOT IN (
                                  'FAILED',
                                  'REJECTED_BY_SAFETY_NET',
                                  'REJECTED_BY_SAFETY_POLICY'
                              )
                              AND triage.action_type NOT IN (
                                  'send_reply',
                                  'forward_email',
                                  'send_email'
                              )
                        ) AS triage_success_count,
                        (
                            SELECT COUNT(*)::int
                            FROM triage_audit triage
                            WHERE triage.created_at >= :from
                              AND triage.created_at < :to
                              AND triage.action_type IN (
                                  'send_reply',
                                  'forward_email',
                                  'send_email'
                              )
                              AND triage.decision NOT IN (
                                  'FAILED',
                                  'REJECTED_BY_SAFETY_NET',
                                  'REJECTED_BY_SAFETY_POLICY'
                              )
                        ) AS outbound_success_count,
                        (
                            SELECT COUNT(*)::int
                            FROM triage_audit triage
                            WHERE triage.created_at >= :from
                              AND triage.created_at < :to
                              AND (
                                  triage.decision = 'FAILED'
                                  OR triage.decision IN (
                                      'REJECTED_BY_SAFETY_NET',
                                      'REJECTED_BY_SAFETY_POLICY'
                                  )
                              )
                        ) AS failed_or_blocked_count
                )
                SELECT 'OBSERVED_EMAIL' AS bucket_key,
                       'Email quan sát' AS bucket_label,
                       counts.observed_email_count AS bucket_count
                FROM counts
                UNION ALL
                SELECT 'TRIAGE_ACTION' AS bucket_key,
                       'Triage thành công' AS bucket_label,
                       counts.triage_success_count AS bucket_count
                FROM counts
                UNION ALL
                SELECT 'OUTBOUND_ACTION' AS bucket_key,
                       'Outbound actions' AS bucket_label,
                       counts.outbound_success_count AS bucket_count
                FROM counts
                UNION ALL
                SELECT 'FAILED_OR_BLOCKED' AS bucket_key,
                       'Lỗi / Bị chặn' AS bucket_label,
                       counts.failed_or_blocked_count AS bucket_count
                FROM counts
                """,
                rangeParameters(adminOverviewQuery),
                (resultSet, _) ->
                        new AdminOverviewActionDistribution(
                                resultSet.getString("bucket_key"),
                                resultSet.getString("bucket_label"),
                                resultSet.getInt("bucket_count")));
    }

    public List<AdminOverviewTopActivityTenant> findTopActivityTenants(
            AdminOverviewQuery adminOverviewQuery, int limit) {
        MapSqlParameterSource parameters =
                rangeParameters(adminOverviewQuery).addValue("limit", limit);
        return namedParameterJdbcTemplate.query(
                """
                WITH selected_gmail AS (
                    SELECT DISTINCT ON (gmail.tenant_id)
                           gmail.tenant_id,
                           gmail.google_email AS primary_email
                    FROM gmail_connections gmail
                    ORDER BY gmail.tenant_id,
                             gmail.is_primary DESC,
                             CASE WHEN gmail.status = 'CONNECTED' THEN 0 ELSE 1 END ASC,
                             gmail.connected_at DESC NULLS LAST,
                             gmail.id ASC
                ),
                owner_user AS (
                    SELECT DISTINCT ON (users.tenant_id)
                           users.tenant_id,
                           users.email AS owner_email
                    FROM users
                    ORDER BY users.tenant_id, users.created_at ASC, users.id ASC
                ),
                observed_metrics AS (
                    SELECT observed.tenant_id,
                           COUNT(*)::int AS observed_email_count
                    FROM mail_message_observed observed
                    WHERE observed.observed_at >= :from
                      AND observed.observed_at < :to
                    GROUP BY observed.tenant_id
                ),
                triage_metrics AS (
                    SELECT triage.tenant_id,
                           COUNT(*)::int AS triage_action_count,
                           (COUNT(*) FILTER (WHERE triage.decision = 'FAILED'))::int
                               AS failed_triage_action_count,
                           (COUNT(*) FILTER (
                               WHERE triage.action_type IN (
                                   'send_reply',
                                   'forward_email',
                                   'send_email'
                               )
                           ))::int AS outbound_action_count,
                           (COUNT(*) FILTER (
                               WHERE triage.action_type IN (
                                   'send_reply',
                                   'forward_email',
                                   'send_email'
                               )
                                 AND triage.decision IN (
                                     'REJECTED_BY_SAFETY_NET',
                                     'REJECTED_BY_SAFETY_POLICY'
                                 )
                           ))::int AS blocked_outbound_action_count
                    FROM triage_audit triage
                    WHERE triage.created_at >= :from
                      AND triage.created_at < :to
                    GROUP BY triage.tenant_id
                )
                SELECT tenants.id AS tenant_id,
                       tenants.display_name AS tenant_display_name,
                       owner_user.owner_email,
                       selected_gmail.primary_email,
                       COALESCE(observed_metrics.observed_email_count, 0)
                           AS observed_email_count,
                       COALESCE(triage_metrics.triage_action_count, 0)
                           AS triage_action_count,
                       COALESCE(triage_metrics.failed_triage_action_count, 0)
                           AS failed_triage_action_count,
                       COALESCE(triage_metrics.outbound_action_count, 0)
                           AS outbound_action_count,
                       COALESCE(triage_metrics.blocked_outbound_action_count, 0)
                           AS blocked_outbound_action_count,
                       CASE
                           WHEN COALESCE(triage_metrics.triage_action_count, 0) = 0 THEN 0
                           ELSE ROUND(
                               (
                                   COALESCE(triage_metrics.failed_triage_action_count, 0)::numeric
                                   / triage_metrics.triage_action_count
                               ) * 100,
                               4
                           )
                       END AS failure_rate_percent
                FROM tenants
                LEFT JOIN owner_user ON owner_user.tenant_id = tenants.id
                LEFT JOIN selected_gmail ON selected_gmail.tenant_id = tenants.id
                LEFT JOIN observed_metrics ON observed_metrics.tenant_id = tenants.id
                LEFT JOIN triage_metrics ON triage_metrics.tenant_id = tenants.id
                WHERE COALESCE(observed_metrics.observed_email_count, 0)
                    + COALESCE(triage_metrics.triage_action_count, 0) > 0
                ORDER BY observed_email_count DESC,
                         triage_action_count DESC,
                         tenants.display_name ASC,
                         tenants.id ASC
                LIMIT :limit
                """,
                parameters,
                (resultSet, _) ->
                        new AdminOverviewTopActivityTenant(
                                resultSet.getObject("tenant_id", UUID.class),
                                resultSet.getString("tenant_display_name"),
                                resultSet.getString("owner_email"),
                                resultSet.getString("primary_email"),
                                resultSet.getInt("observed_email_count"),
                                resultSet.getInt("triage_action_count"),
                                resultSet.getInt("failed_triage_action_count"),
                                resultSet.getInt("outbound_action_count"),
                                resultSet.getInt("blocked_outbound_action_count"),
                                resultSet.getBigDecimal("failure_rate_percent").doubleValue()));
    }

    public List<AdminOverviewTopSpendTenant> findTopSpendTenants(
            AdminOverviewQuery adminOverviewQuery, int limit) {
        MapSqlParameterSource parameters =
                rangeParameters(adminOverviewQuery).addValue("limit", limit);
        return namedParameterJdbcTemplate.query(
                """
                WITH selected_gmail AS (
                    SELECT DISTINCT ON (gmail.tenant_id)
                           gmail.tenant_id,
                           gmail.google_email AS primary_email
                    FROM gmail_connections gmail
                    ORDER BY gmail.tenant_id,
                             gmail.is_primary DESC,
                             CASE WHEN gmail.status = 'CONNECTED' THEN 0 ELSE 1 END ASC,
                             gmail.connected_at DESC NULLS LAST,
                             gmail.id ASC
                ),
                owner_user AS (
                    SELECT DISTINCT ON (users.tenant_id)
                           users.tenant_id,
                           users.email AS owner_email
                    FROM users
                    ORDER BY users.tenant_id, users.created_at ASC, users.id ASC
                ),
                spend_metrics AS (
                    SELECT llm_call.tenant_id,
                           COUNT(*)::int AS llm_call_count,
                           COALESCE(SUM(llm_call.charged_credits), 0)::bigint
                               AS charged_credits,
                           COALESCE(SUM(llm_call.total_cost_usd), 0) AS total_cost_usd
                    FROM llm_call_audit llm_call
                    WHERE llm_call.created_at >= :from
                      AND llm_call.created_at < :to
                    GROUP BY llm_call.tenant_id
                )
                SELECT tenants.id AS tenant_id,
                       tenants.display_name AS tenant_display_name,
                       owner_user.owner_email,
                       selected_gmail.primary_email,
                       spend_metrics.llm_call_count,
                       spend_metrics.charged_credits,
                       spend_metrics.total_cost_usd
                FROM spend_metrics
                JOIN tenants ON tenants.id = spend_metrics.tenant_id
                LEFT JOIN owner_user ON owner_user.tenant_id = tenants.id
                LEFT JOIN selected_gmail ON selected_gmail.tenant_id = tenants.id
                ORDER BY spend_metrics.charged_credits DESC,
                         spend_metrics.llm_call_count DESC,
                         tenants.display_name ASC,
                         tenants.id ASC
                LIMIT :limit
                """,
                parameters,
                (resultSet, _) ->
                        new AdminOverviewTopSpendTenant(
                                resultSet.getObject("tenant_id", UUID.class),
                                resultSet.getString("tenant_display_name"),
                                resultSet.getString("owner_email"),
                                resultSet.getString("primary_email"),
                                resultSet.getInt("llm_call_count"),
                                resultSet.getLong("charged_credits"),
                                costOrZero(resultSet.getBigDecimal("total_cost_usd"))));
    }

    private static MapSqlParameterSource rangeParameters(AdminOverviewQuery adminOverviewQuery) {
        return new MapSqlParameterSource()
                .addValue("from", Timestamp.from(adminOverviewQuery.from()))
                .addValue("to", Timestamp.from(adminOverviewQuery.to()));
    }

    private static BigDecimal costOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
