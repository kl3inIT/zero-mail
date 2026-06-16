package com.zeromail.core.admin.tenant.persistence.lowlevel;

import com.zeromail.core.admin.tenant.projection.TenantActivityEvent;
import com.zeromail.core.admin.tenant.projection.TenantActivitySnapshot;
import com.zeromail.core.admin.tenant.projection.TenantDetailOverview;
import com.zeromail.core.admin.tenant.projection.TenantHealthSnapshot;
import com.zeromail.core.admin.tenant.projection.TenantListQuery;
import com.zeromail.core.admin.tenant.projection.TenantListRow;
import com.zeromail.core.admin.tenant.projection.TenantListSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-side JDBC access for admin tenant inspection. Centralises schema knowledge for the
 * tenant-detail join graph (tenants × gmail_connections × credit_ledger_entry × chat × triage_audit
 * × mail_message_observed × rules × pubsub_delivery × assistant_settings).
 *
 * <p>Service-layer concerns kept out of here: pagination hasNextPage, NoSuchElementException
 * throwing, LOW/MEDIUM/HIGH bucket derivation.
 */
@Repository
public class TenantInspectionReadRepository {

    private static final String TENANT_ROWS_CTE =
            """
                    WITH tenant_base AS (
                        SELECT t.id AS tenant_id,
                               t.created_at AS created_at,
                               gc.google_email AS gmail_account_email,
                               COALESCE(gc.status, 'DISCONNECTED') AS gmail_connection_status,
                               gc.updated_at AS gmail_updated_at,
                               COALESCE(telegram.status, 'NO_CONNECTION') AS telegram_status,
                               telegram.last_active_at AS telegram_last_active_at,
                               telegram.linked_at AS telegram_linked_at,
                               CASE
                                   WHEN t.triage_paused THEN 'PAUSED'
                                   WHEN COALESCE(gc.status, 'DISCONNECTED') = 'DISCONNECTED'
                                       THEN 'DISCONNECTED'
                                   ELSE 'ACTIVE'
                               END AS status,
                               CASE
                                   WHEN gc.tenant_id IS NULL THEN 'NO_CONNECTION'
                                   WHEN gc.watch_expires_at IS NULL THEN 'NOT_WATCHING'
                                   WHEN gc.watch_expires_at < NOW() THEN 'EXPIRED'
                                   ELSE 'WATCHING'
                               END AS gmail_watch_status,
                               COALESCE(settings.auto_send_rules_enabled, true) AS auto_send_rules_enabled
                         FROM tenants t
                         LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
                         LEFT JOIN telegram_account telegram ON telegram.tenant_id = t.id
                         LEFT JOIN rule_automation_settings settings ON settings.tenant_id = t.id
                    ),
                    filtered_tenants AS (
                        SELECT *
                        FROM tenant_base
                         WHERE (CAST(:status AS text) IS NULL OR status = :status)
                           AND (CAST(:from AS timestamptz) IS NULL OR created_at >= :from)
                           AND (CAST(:to AS timestamptz) IS NULL OR created_at <= :to)
                           AND (
                               CAST(:email AS text) IS NULL
                               OR gmail_account_email ILIKE ('%' || :email || '%')
                           )
                     ),
                    rules_metrics AS (
                        SELECT filtered_tenants.tenant_id,
                               COUNT(rules.id)::int AS total_rules_count,
                               (COUNT(*) FILTER (WHERE rules.enabled))::int AS enabled_rules_count,
                               COALESCE(
                                   ARRAY_AGG(rules.display_name ORDER BY rules.order_index, rules.created_at)
                                       FILTER (WHERE rules.enabled AND rules.display_name IS NOT NULL),
                                   ARRAY[]::text[]
                               ) AS enabled_rule_names,
                               MAX(rules.updated_at) AS last_rule_updated_at
                        FROM filtered_tenants
                        LEFT JOIN rules ON rules.tenant_id = filtered_tenants.tenant_id
                        GROUP BY filtered_tenants.tenant_id
                    ),
                    observed_metrics AS (
                        SELECT filtered_tenants.tenant_id,
                               (COUNT(*) FILTER (
                                   WHERE observed.observed_at >= NOW() - INTERVAL '30 days'
                               ))::int AS observed_email_30d_count,
                               MAX(observed.observed_at) AS last_observed_at
                        FROM filtered_tenants
                        LEFT JOIN mail_message_observed observed
                            ON observed.tenant_id = filtered_tenants.tenant_id
                        GROUP BY filtered_tenants.tenant_id
                    ),
                    triage_metrics AS (
                        SELECT filtered_tenants.tenant_id,
                               (COUNT(*) FILTER (
                                   WHERE triage.created_at >= NOW() - INTERVAL '30 days'
                               ))::int AS triage_action_30d_count,
                               (COUNT(*) FILTER (
                                   WHERE triage.created_at >= NOW() - INTERVAL '30 days'
                                     AND triage.decision = 'FAILED'
                               ))::int AS failed_triage_action_30d_count,
                               (COUNT(*) FILTER (
                                   WHERE triage.created_at >= NOW() - INTERVAL '30 days'
                                     AND triage.action_type IN ('send_reply', 'forward_email', 'send_email')
                               ))::int AS outbound_action_30d_count,
                               (COUNT(*) FILTER (
                                   WHERE triage.created_at >= NOW() - INTERVAL '30 days'
                                     AND triage.action_type IN ('send_reply', 'forward_email', 'send_email')
                                     AND triage.decision IN (
                                         'REJECTED_BY_SAFETY_NET',
                                         'REJECTED_BY_SAFETY_POLICY'
                                     )
                               ))::int AS blocked_outbound_action_30d_count,
                               MAX(triage.created_at) AS last_triage_at
                        FROM filtered_tenants
                        LEFT JOIN triage_audit triage ON triage.tenant_id = filtered_tenants.tenant_id
                        GROUP BY filtered_tenants.tenant_id
                    ),
                    chat_metrics AS (
                        SELECT filtered_tenants.tenant_id,
                               (COUNT(chat.id) FILTER (WHERE chat.soft_deleted_at IS NULL))::int
                                   AS chat_session_count,
                               MAX(chat.updated_at) FILTER (WHERE chat.soft_deleted_at IS NULL)
                                   AS last_chat_session_at
                        FROM filtered_tenants
                        LEFT JOIN chat ON chat.tenant_id = filtered_tenants.tenant_id
                        GROUP BY filtered_tenants.tenant_id
                    ),
                    assistant_metrics AS (
                        SELECT filtered_tenants.tenant_id,
                               (COUNT(action_audit.id) FILTER (
                                   WHERE action_audit.created_at >= NOW() - INTERVAL '30 days'
                               ))::int AS assistant_action_30d_count,
                               MAX(action_audit.created_at) AS last_assistant_action_at
                        FROM filtered_tenants
                        LEFT JOIN assistant_action_audit action_audit
                            ON action_audit.tenant_id = filtered_tenants.tenant_id
                        GROUP BY filtered_tenants.tenant_id
                    ),
                    llm_metrics AS (
                        SELECT filtered_tenants.tenant_id,
                               (COUNT(llm_call.id) FILTER (
                                   WHERE llm_call.created_at >= NOW() - INTERVAL '30 days'
                               ))::int AS llm_call_30d_count,
                               MAX(llm_call.created_at) AS last_llm_call_at
                        FROM filtered_tenants
                        LEFT JOIN llm_call_audit llm_call ON llm_call.tenant_id = filtered_tenants.tenant_id
                        GROUP BY filtered_tenants.tenant_id
                    ),
                    credit_metrics AS (
                        SELECT filtered_tenants.tenant_id,
                               COALESCE(SUM(credit.amount_credits), 0)::int AS credit_balance,
                               (COUNT(*) FILTER (
                                   WHERE credit.kind = 'RESERVE'
                                     AND credit.created_at >= NOW() - INTERVAL '7 days'
                               ))::int AS reserve_7d_count,
                               MAX(credit.created_at) AS last_credit_at
                        FROM filtered_tenants
                        LEFT JOIN credit_ledger_entry credit
                            ON credit.tenant_id = filtered_tenants.tenant_id
                        GROUP BY filtered_tenants.tenant_id
                    ),
                    pubsub_metrics AS (
                        SELECT filtered_tenants.tenant_id,
                               (COUNT(*) FILTER (WHERE delivery.status = 'PENDING'))::int
                                   AS pubsub_backlog_count,
                               MAX(delivery.created_at) AS last_pubsub_at
                        FROM filtered_tenants
                        LEFT JOIN pubsub_delivery delivery
                            ON delivery.tenant_id = filtered_tenants.tenant_id
                        GROUP BY filtered_tenants.tenant_id
                    ),
                    tenant_rows AS (
                        SELECT filtered_tenants.tenant_id,
                               filtered_tenants.created_at,
                               filtered_tenants.gmail_account_email,
                               filtered_tenants.status,
                               filtered_tenants.gmail_connection_status,
                               CASE
                                   WHEN COALESCE(credit_metrics.reserve_7d_count, 0) >= 100 THEN 'HIGH'
                                   WHEN COALESCE(credit_metrics.reserve_7d_count, 0) >= 10 THEN 'MEDIUM'
                                   ELSE 'LOW'
                               END AS spend_bucket_7d,
                               latest_activity.activity_at AS last_activity_at,
                               latest_activity.activity_kind AS last_activity_kind,
                               COALESCE(rules_metrics.total_rules_count, 0) AS total_rules_count,
                               COALESCE(rules_metrics.enabled_rules_count, 0) AS enabled_rules_count,
                               COALESCE(rules_metrics.enabled_rule_names, ARRAY[]::text[])
                                   AS enabled_rule_names,
                               COALESCE(observed_metrics.observed_email_30d_count, 0)
                                   AS observed_email_30d_count,
                               COALESCE(triage_metrics.triage_action_30d_count, 0)
                                   AS triage_action_30d_count,
                               COALESCE(triage_metrics.failed_triage_action_30d_count, 0)
                                   AS failed_triage_action_30d_count,
                               COALESCE(triage_metrics.outbound_action_30d_count, 0)
                                   AS outbound_action_30d_count,
                                COALESCE(triage_metrics.blocked_outbound_action_30d_count, 0)
                                    AS blocked_outbound_action_30d_count,
                                COALESCE(chat_metrics.chat_session_count, 0) AS chat_session_count,
                                chat_metrics.last_chat_session_at,
                                COALESCE(assistant_metrics.assistant_action_30d_count, 0)
                                    AS assistant_action_30d_count,
                               COALESCE(llm_metrics.llm_call_30d_count, 0) AS llm_call_30d_count,
                               COALESCE(credit_metrics.credit_balance, 0) AS credit_balance,
                                COALESCE(pubsub_metrics.pubsub_backlog_count, 0) AS pubsub_backlog_count,
                                filtered_tenants.gmail_watch_status,
                                filtered_tenants.telegram_status,
                                filtered_tenants.telegram_last_active_at,
                                filtered_tenants.auto_send_rules_enabled
                        FROM filtered_tenants
                        LEFT JOIN rules_metrics
                            ON rules_metrics.tenant_id = filtered_tenants.tenant_id
                        LEFT JOIN observed_metrics
                            ON observed_metrics.tenant_id = filtered_tenants.tenant_id
                        LEFT JOIN triage_metrics
                            ON triage_metrics.tenant_id = filtered_tenants.tenant_id
                        LEFT JOIN chat_metrics
                            ON chat_metrics.tenant_id = filtered_tenants.tenant_id
                        LEFT JOIN assistant_metrics
                            ON assistant_metrics.tenant_id = filtered_tenants.tenant_id
                        LEFT JOIN llm_metrics
                            ON llm_metrics.tenant_id = filtered_tenants.tenant_id
                        LEFT JOIN credit_metrics
                            ON credit_metrics.tenant_id = filtered_tenants.tenant_id
                        LEFT JOIN pubsub_metrics
                            ON pubsub_metrics.tenant_id = filtered_tenants.tenant_id
                        CROSS JOIN LATERAL (
                            SELECT activity_at, activity_kind
                            FROM (
                                VALUES
                                    (filtered_tenants.created_at, 'TENANT_CREATED'),
                                    (filtered_tenants.gmail_updated_at, 'GMAIL_CONNECTION'),
                                    (rules_metrics.last_rule_updated_at, 'RULE'),
                                    (observed_metrics.last_observed_at, 'GMAIL_OBSERVED'),
                                    (triage_metrics.last_triage_at, 'TRIAGE'),
                                    (chat_metrics.last_chat_session_at, 'CHAT'),
                                    (
                                        COALESCE(
                                            filtered_tenants.telegram_last_active_at,
                                            filtered_tenants.telegram_linked_at
                                        ),
                                        'TELEGRAM'
                                    ),
                                    (assistant_metrics.last_assistant_action_at, 'ASSISTANT_ACTION'),
                                    (llm_metrics.last_llm_call_at, 'LLM')
                            ) AS activity_points(activity_at, activity_kind)
                            WHERE activity_at IS NOT NULL
                            ORDER BY activity_at DESC
                            LIMIT 1
                        ) latest_activity
                    )
                    """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public TenantInspectionReadRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
    }

    public List<TenantListRow> findTenantListRows(TenantListQuery tenantListQuery) {
        MapSqlParameterSource parameters =
                parametersForTenantList(tenantListQuery)
                        .addValue("limit", tenantListQuery.limit() + 1)
                        .addValue("offset", tenantListQuery.offset());
        return namedParameterJdbcTemplate.query(
                TENANT_ROWS_CTE
                        + """
                        SELECT tenant_id, created_at, gmail_account_email, status,
                               gmail_connection_status,
                               spend_bucket_7d, last_activity_at, last_activity_kind,
                               total_rules_count, enabled_rules_count,
                               enabled_rule_names,
                               observed_email_30d_count, triage_action_30d_count,
                               failed_triage_action_30d_count, outbound_action_30d_count,
                               blocked_outbound_action_30d_count, chat_session_count,
                               last_chat_session_at,
                               assistant_action_30d_count, llm_call_30d_count, credit_balance,
                               pubsub_backlog_count, gmail_watch_status,
                               telegram_status, telegram_last_active_at, auto_send_rules_enabled
                        FROM tenant_rows
                        ORDER BY created_at DESC, tenant_id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                parameters,
                (resultSet, _) -> mapTenantListRow(resultSet));
    }

    public TenantListSummary findTenantListSummary(TenantListQuery tenantListQuery) {
        Objects.requireNonNull(tenantListQuery, "tenantListQuery must not be null");
        MapSqlParameterSource parameters = parametersForTenantList(tenantListQuery);
        return namedParameterJdbcTemplate.queryForObject(
                TENANT_ROWS_CTE
                        + """
                        SELECT COUNT(*)::int AS total_count,
                               (COUNT(*) FILTER (WHERE status = 'ACTIVE'))::int AS active_count,
                               (COUNT(*) FILTER (WHERE status = 'PAUSED'))::int AS paused_count,
                               (COUNT(*) FILTER (WHERE status = 'DISCONNECTED'))::int
                                   AS disconnected_count,
                               (COUNT(*) FILTER (WHERE gmail_connection_status = 'CONNECTED'))::int
                                   AS gmail_connected_count,
                               (COUNT(*) FILTER (WHERE telegram_status = 'CONNECTED'))::int
                                   AS telegram_connected_count,
                               (COUNT(*) FILTER (
                                   WHERE last_activity_at >= NOW() - INTERVAL '24 hours'
                               ))::int AS active_last_24h_count,
                               (COUNT(*) FILTER (
                                   WHERE last_activity_at >= NOW() - INTERVAL '7 days'
                               ))::int AS active_last_7d_count,
                               (COUNT(*) FILTER (
                                   WHERE gmail_watch_status <> 'WATCHING'
                                      OR pubsub_backlog_count > 0
                               ))::int AS gmail_unhealthy_count,
                               COALESCE(SUM(
                                   failed_triage_action_30d_count
                                       + blocked_outbound_action_30d_count
                               ), 0)::int AS automation_failure_30d_count,
                               COALESCE(SUM(blocked_outbound_action_30d_count), 0)::int
                                   AS outbound_blocked_30d_count,
                               (COUNT(*) FILTER (WHERE credit_balance <= 0))::int
                                   AS low_credit_count
                        FROM tenant_rows
                        """,
                parameters,
                TenantInspectionReadRepository::mapTenantListSummary);
    }

    public Optional<TenantDetailOverview> findOverview(UUID tenantId) {
        return queryOptional(
                """
                        SELECT t.id AS tenant_id,
                               t.created_at AS created_at,
                               gc.google_email AS gmail_account_email,
                               COALESCE(gc.status, 'DISCONNECTED') AS gmail_connection_status,
                               COALESCE(telegram.status, 'NO_CONNECTION') AS telegram_status,
                               CASE
                                   WHEN t.triage_paused THEN 'PAUSED'
                                   WHEN COALESCE(gc.status, 'DISCONNECTED') = 'DISCONNECTED'
                                       THEN 'DISCONNECTED'
                                   ELSE 'ACTIVE'
                               END AS status,
                               GREATEST(
                                   COALESCE(MAX(chat.updated_at), t.created_at),
                                   COALESCE(MAX(triage.created_at), t.created_at),
                                   COALESCE(MAX(observed.observed_at), t.created_at),
                                   COALESCE(gc.updated_at, t.created_at)
                                ) AS last_activity_at,
                                COUNT(DISTINCT rules.id)::int AS rules_count,
                                (SELECT COUNT(*)::int
                                 FROM rules enabled_rules
                                 WHERE enabled_rules.tenant_id = t.id AND enabled_rules.enabled)
                                    AS enabled_rules_count,
                                (SELECT COALESCE(
                                     ARRAY_AGG(
                                         enabled_rule_names.display_name
                                         ORDER BY enabled_rule_names.order_index, enabled_rule_names.created_at
                                     ),
                                     ARRAY[]::text[]
                                 )
                                 FROM rules enabled_rule_names
                                 WHERE enabled_rule_names.tenant_id = t.id AND enabled_rule_names.enabled)
                                    AS enabled_rule_names
                         FROM tenants t
                         LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
                         LEFT JOIN telegram_account telegram ON telegram.tenant_id = t.id
                         LEFT JOIN rules ON rules.tenant_id = t.id
                         LEFT JOIN chat ON chat.tenant_id = t.id
                         LEFT JOIN triage_audit triage ON triage.tenant_id = t.id
                         LEFT JOIN mail_message_observed observed ON observed.tenant_id = t.id
                         WHERE t.id = :tenantId
                         GROUP BY t.id, t.created_at, t.triage_paused, gc.google_email, gc.status,
                                  gc.updated_at, telegram.status
                        """,
                parametersForTenant(tenantId),
                TenantInspectionReadRepository::mapTenantDetailOverview);
    }

    public Optional<TenantHealthSnapshot> findHealth(UUID tenantId) {
        return queryOptional(
                """
                        SELECT COALESCE(gc.status, 'NO_CONNECTION') AS token_refresh_status,
                               gc.updated_at AS last_token_refresh_at,
                               CASE
                                   WHEN gc.watch_expires_at IS NULL THEN 'NOT_WATCHING'
                                   WHEN gc.watch_expires_at < NOW() THEN 'EXPIRED'
                                   ELSE 'WATCHING'
                               END AS watch_status,
                               (SELECT MAX(created_at) FROM pubsub_delivery WHERE tenant_id = :tenantId)
                                   AS last_pubsub_push_at,
                               (SELECT COUNT(*)::int FROM pubsub_delivery
                                WHERE tenant_id = :tenantId AND status = 'PENDING') AS pubsub_backlog_count
                        FROM tenants t
                        LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
                        WHERE t.id = :tenantId
                        """,
                parametersForTenant(tenantId),
                TenantInspectionReadRepository::mapTenantHealthSnapshot);
    }

    public int findCreditsBalance(UUID tenantId) {
        Integer balance =
                namedParameterJdbcTemplate.queryForObject(
                        """
                                SELECT COALESCE(SUM(amount_credits), 0)::int
                                FROM credit_ledger_entry
                                WHERE tenant_id = :tenantId
                                """,
                        parametersForTenant(tenantId),
                        Integer.class);
        return balance == null ? 0 : balance;
    }

    public Instant findLastTopUpAt(UUID tenantId) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                        SELECT MAX(created_at)
                        FROM credit_ledger_entry
                        WHERE tenant_id = :tenantId AND kind = 'TOPUP'
                        """,
                parametersForTenant(tenantId),
                (resultSet, _) -> instantOrNull(resultSet, 1));
    }

    public String findCurrentPlanCode(UUID tenantId) {
        String planCode =
                namedParameterJdbcTemplate.queryForObject(
                        """
                                SELECT COALESCE((
                                    SELECT billing_plan.code
                                    FROM billing_plan_period plan_period
                                    JOIN billing_plan ON billing_plan.id = plan_period.plan_id
                                    WHERE plan_period.tenant_id = :tenantId
                                      AND plan_period.status = 'ACTIVE'
                                      AND plan_period.effective_at <= CURRENT_TIMESTAMP
                                      AND plan_period.expires_at > CURRENT_TIMESTAMP
                                    ORDER BY plan_period.effective_at DESC, plan_period.id DESC
                                    LIMIT 1
                                ), 'FREE')
                                """,
                        parametersForTenant(tenantId),
                        String.class);
        return planCode == null ? "FREE" : planCode;
    }

    public int countCallsSince(UUID tenantId, java.time.Duration window) {
        long days = window.toDays();
        MapSqlParameterSource parameters =
                parametersForTenant(tenantId).addValue("days", (int) days);
        Integer count =
                namedParameterJdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*)::int
                                FROM credit_ledger_entry
                                WHERE tenant_id = :tenantId
                                  AND kind = 'RESERVE'
                                  AND created_at >= NOW() - (:days || ' days')::interval
                                """,
                        parameters,
                        Integer.class);
        return count == null ? 0 : count;
    }

    public Map<String, Integer> findPerFeatureCallCount(UUID tenantId) {
        Map<String, Integer> perFeatureCallCount = new LinkedHashMap<>();
        RowCallbackHandler rowCallbackHandler =
                resultSet ->
                        perFeatureCallCount.put(
                                resultSet.getString("ref_type"), resultSet.getInt("call_count"));
        namedParameterJdbcTemplate.query(
                """
                        SELECT ref_type, COUNT(*)::int AS call_count
                        FROM credit_ledger_entry
                        WHERE tenant_id = :tenantId
                          AND kind = 'RESERVE'
                          AND created_at >= NOW() - INTERVAL '30 days'
                        GROUP BY ref_type
                        ORDER BY ref_type
                        """,
                parametersForTenant(tenantId),
                rowCallbackHandler);
        return perFeatureCallCount;
    }

    public Optional<TenantActivitySnapshot> findActivity(UUID tenantId) {
        Optional<TenantActivitySnapshot> tenantActivitySnapshot =
                queryOptional(
                        """
                                SELECT (SELECT COUNT(*)::int FROM triage_audit
                                        WHERE tenant_id = :tenantId
                                          AND created_at >= NOW() - INTERVAL '30 days') AS last_30d_rule_fire_count,
                                       (SELECT COUNT(*)::int FROM chat
                                        WHERE tenant_id = :tenantId AND soft_deleted_at IS NULL) AS chat_session_count,
                                       (SELECT MAX(updated_at) FROM chat
                                        WHERE tenant_id = :tenantId AND soft_deleted_at IS NULL) AS last_chat_session_at,
                                       (SELECT CONCAT_WS(':', provider_id, default_model)
                                        FROM assistant_settings
                                        WHERE tenant_id = :tenantId) AS last_chat_model_selection,
                                       (SELECT COUNT(*)::int
                                        FROM (
                                            SELECT occurred_at
                                            FROM tenant_activity_event
                                            WHERE tenant_id = :tenantId
                                            UNION ALL
                                            SELECT COALESCE(updated_at, connected_at, created_at)
                                            FROM gmail_connections
                                            WHERE tenant_id = :tenantId
                                            UNION ALL
                                            SELECT updated_at
                                            FROM rules
                                            WHERE tenant_id = :tenantId
                                            UNION ALL
                                            SELECT updated_at
                                            FROM chat
                                            WHERE tenant_id = :tenantId AND soft_deleted_at IS NULL
                                            UNION ALL
                                            SELECT created_at
                                            FROM triage_audit
                                            WHERE tenant_id = :tenantId
                                            UNION ALL
                                            SELECT created_at
                                            FROM assistant_action_audit
                                            WHERE tenant_id = :tenantId
                                            UNION ALL
                                            SELECT COALESCE(last_active_at, linked_at, updated_at, created_at)
                                            FROM telegram_account
                                            WHERE tenant_id = :tenantId
                                            UNION ALL
                                            SELECT created_at
                                            FROM llm_call_audit
                                            WHERE tenant_id = :tenantId
                                        ) seven_day_activity
                                        WHERE occurred_at >= NOW() - INTERVAL '7 days') AS total_activity_7d_count,
                                       (SELECT MAX(occurred_at)
                                        FROM tenant_activity_event
                                        WHERE tenant_id = :tenantId AND event_type = 'LOGIN') AS last_login_at,
                                       (SELECT SUM(duration_seconds)::int
                                        FROM tenant_activity_event
                                        WHERE tenant_id = :tenantId AND duration_seconds IS NOT NULL)
                                           AS total_app_duration_seconds
                                FROM tenants
                                WHERE id = :tenantId
                                """,
                        parametersForTenant(tenantId),
                        TenantInspectionReadRepository::mapTenantActivitySnapshot);
        return tenantActivitySnapshot.map(
                activitySnapshot ->
                        new TenantActivitySnapshot(
                                activitySnapshot.last30dRuleFireCount(),
                                activitySnapshot.chatSessionCount(),
                                activitySnapshot.lastChatSessionAt(),
                                activitySnapshot.lastChatModelSelection(),
                                activitySnapshot.totalActivity7dCount(),
                                activitySnapshot.lastLoginAt(),
                                activitySnapshot.totalAppDurationSeconds(),
                                findActivityEvents(tenantId)));
    }

    private List<TenantActivityEvent> findActivityEvents(UUID tenantId) {
        return namedParameterJdbcTemplate.query(
                """
                        WITH activity_events AS (
                            SELECT id AS event_id,
                                   occurred_at,
                                   event_type,
                                   CASE event_type
                                       WHEN 'LOGIN' THEN 'Đăng nhập'
                                       WHEN 'LOGOUT' THEN 'Đăng xuất'
                                       WHEN 'SESSION_EXPIRED' THEN 'Hết phiên'
                                       WHEN 'GMAIL_CONNECTED' THEN 'Kết nối Gmail'
                                       WHEN 'TELEGRAM_CONNECTED' THEN 'Kết nối Telegram'
                                       ELSE event_type
                                   END AS action_label,
                                   detail,
                                   event_status AS status,
                                   duration_seconds,
                                   source,
                                   false AS legacy_data_missing
                            FROM tenant_activity_event
                            WHERE tenant_id = :tenantId

                            UNION ALL

                            SELECT gen_random_uuid() AS event_id,
                                   COALESCE(gmail.updated_at, gmail.connected_at, gmail.created_at) AS occurred_at,
                                   CASE
                                       WHEN gmail.status = 'CONNECTED' THEN 'GMAIL_CONNECTED'
                                       ELSE 'GMAIL_DISCONNECTED'
                                   END AS event_type,
                                   'Kết nối Gmail' AS action_label,
                                   CASE
                                       WHEN gmail.status = 'CONNECTED' THEN 'Kết nối tài khoản Gmail thành công'
                                       ELSE 'Ngắt kết nối tài khoản Gmail'
                                    END AS detail,
                                    CASE WHEN gmail.status = 'CONNECTED' THEN 'SUCCESS' ELSE 'UNKNOWN' END AS status,
                                    NULL::int AS duration_seconds,
                                    'LEGACY_GMAIL' AS source,
                                    true AS legacy_data_missing
                            FROM gmail_connections gmail
                            WHERE gmail.tenant_id = :tenantId

                            UNION ALL

                            SELECT gen_random_uuid() AS event_id,
                                   rules.updated_at AS occurred_at,
                                   'RULE_UPDATED' AS event_type,
                                    CASE WHEN rules.enabled THEN 'Bật rule' ELSE 'Tắt rule' END AS action_label,
                                    CONCAT('Rule "', rules.display_name, '"') AS detail,
                                    'SUCCESS' AS status,
                                    NULL::int AS duration_seconds,
                                    'LEGACY_RULE' AS source,
                                    true AS legacy_data_missing
                            FROM rules
                            WHERE rules.tenant_id = :tenantId

                            UNION ALL

                            SELECT gen_random_uuid() AS event_id,
                                   chat.updated_at AS occurred_at,
                                   'CHAT_SESSION' AS event_type,
                                    'Chat' AS action_label,
                                    COALESCE(chat.title, 'Phiên chat Zero Mail') AS detail,
                                    'SUCCESS' AS status,
                                    NULL::int AS duration_seconds,
                                    'LEGACY_CHAT' AS source,
                                    true AS legacy_data_missing
                            FROM chat
                            WHERE chat.tenant_id = :tenantId AND chat.soft_deleted_at IS NULL

                            UNION ALL

                            SELECT gen_random_uuid() AS event_id,
                                   triage.created_at AS occurred_at,
                                   'TRIAGE_ACTION' AS event_type,
                                   'Triage' AS action_label,
                                   CONCAT('Action ', triage.action_type, ' - ', triage.decision) AS detail,
                                   CASE
                                       WHEN triage.decision IN ('APPLIED', 'COMMITTED') THEN 'SUCCESS'
                                       WHEN triage.decision LIKE 'REJECTED%' THEN 'BLOCKED'
                                        WHEN triage.decision = 'PENDING' THEN 'PENDING'
                                        ELSE 'FAILED'
                                    END AS status,
                                    NULL::int AS duration_seconds,
                                    'LEGACY_TRIAGE' AS source,
                                    true AS legacy_data_missing
                            FROM triage_audit triage
                            WHERE triage.tenant_id = :tenantId

                            UNION ALL

                            SELECT gen_random_uuid() AS event_id,
                                   action_audit.created_at AS occurred_at,
                                   'ASSISTANT_ACTION' AS event_type,
                                   'Assistant action' AS action_label,
                                    CONCAT(action_audit.tool_name, ' - ', action_audit.state) AS detail,
                                    CASE WHEN action_audit.state = 'COMMITTED' THEN 'SUCCESS' ELSE action_audit.state END
                                        AS status,
                                    NULL::int AS duration_seconds,
                                    'LEGACY_ASSISTANT' AS source,
                                    true AS legacy_data_missing
                            FROM assistant_action_audit action_audit
                            WHERE action_audit.tenant_id = :tenantId

                            UNION ALL

                            SELECT gen_random_uuid() AS event_id,
                                   COALESCE(telegram.last_active_at, telegram.linked_at, telegram.updated_at, telegram.created_at)
                                       AS occurred_at,
                                   CASE
                                       WHEN telegram.status = 'CONNECTED' THEN 'TELEGRAM_CONNECTED'
                                       WHEN telegram.status = 'BLOCKED' THEN 'TELEGRAM_BLOCKED'
                                       ELSE 'TELEGRAM_DISCONNECTED'
                                   END AS event_type,
                                   'Telegram' AS action_label,
                                   CASE
                                       WHEN telegram.status = 'CONNECTED' THEN 'Kết nối Telegram'
                                       WHEN telegram.status = 'BLOCKED' THEN 'Telegram bị chặn'
                                       ELSE 'Ngắt kết nối Telegram'
                                   END AS detail,
                                   CASE
                                       WHEN telegram.status = 'CONNECTED' THEN 'SUCCESS'
                                        WHEN telegram.status = 'BLOCKED' THEN 'BLOCKED'
                                        ELSE 'UNKNOWN'
                                    END AS status,
                                    NULL::int AS duration_seconds,
                                    'LEGACY_TELEGRAM' AS source,
                                    true AS legacy_data_missing
                            FROM telegram_account telegram
                            WHERE telegram.tenant_id = :tenantId

                            UNION ALL

                            SELECT gen_random_uuid() AS event_id,
                                   llm_call.created_at AS occurred_at,
                                   'LLM_CALL' AS event_type,
                                    'LLM' AS action_label,
                                    CONCAT(llm_call.feature, ' / ', llm_call.model_id) AS detail,
                                    'SUCCESS' AS status,
                                    NULL::int AS duration_seconds,
                                    'LEGACY_LLM' AS source,
                                    true AS legacy_data_missing
                            FROM llm_call_audit llm_call
                            WHERE llm_call.tenant_id = :tenantId
                        )
                        SELECT event_id, occurred_at, event_type, action_label, detail, status,
                               duration_seconds, source, legacy_data_missing
                        FROM activity_events
                        WHERE occurred_at IS NOT NULL
                        ORDER BY occurred_at DESC, action_label ASC
                        LIMIT 100
                        """,
                parametersForTenant(tenantId),
                TenantInspectionReadRepository::mapTenantActivityEvent);
    }

    public String findGmailAccountEmail(UUID tenantId) {
        return namedParameterJdbcTemplate.query(
                """
                        SELECT gc.google_email
                        FROM tenants t
                        LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
                        WHERE t.id = :tenantId
                        """,
                parametersForTenant(tenantId),
                resultSet -> resultSet.next() ? resultSet.getString("google_email") : null);
    }

    public int countTenantOwnedRows(String tableName, UUID tenantId) {
        Integer count =
                namedParameterJdbcTemplate.queryForObject(
                        "SELECT COUNT(*)::int FROM " + tableName + " WHERE tenant_id = :tenantId",
                        parametersForTenant(tenantId),
                        Integer.class);
        return count == null ? 0 : count;
    }

    public boolean exists(UUID tenantId) {
        Boolean exists =
                namedParameterJdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM tenants WHERE id = :tenantId)",
                        parametersForTenant(tenantId),
                        Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private <T> Optional<T> queryOptional(
            String sql,
            MapSqlParameterSource parameters,
            org.springframework.jdbc.core.RowMapper<T> rowMapper) {
        List<T> rows = namedParameterJdbcTemplate.query(sql, parameters, rowMapper);
        return rows.stream().findFirst();
    }

    private static TenantListRow mapTenantListRow(ResultSet resultSet) throws SQLException {
        return new TenantListRow(
                resultSet.getObject("tenant_id", UUID.class),
                instantOrNull(resultSet, "created_at"),
                resultSet.getString("gmail_account_email"),
                resultSet.getString("status"),
                resultSet.getString("gmail_connection_status"),
                resultSet.getString("spend_bucket_7d"),
                instantOrNull(resultSet, "last_activity_at"),
                resultSet.getString("last_activity_kind"),
                resultSet.getInt("total_rules_count"),
                resultSet.getInt("enabled_rules_count"),
                stringList(resultSet, "enabled_rule_names"),
                resultSet.getInt("observed_email_30d_count"),
                resultSet.getInt("triage_action_30d_count"),
                resultSet.getInt("failed_triage_action_30d_count"),
                resultSet.getInt("outbound_action_30d_count"),
                resultSet.getInt("blocked_outbound_action_30d_count"),
                resultSet.getInt("chat_session_count"),
                instantOrNull(resultSet, "last_chat_session_at"),
                resultSet.getInt("assistant_action_30d_count"),
                resultSet.getInt("llm_call_30d_count"),
                resultSet.getInt("credit_balance"),
                resultSet.getInt("pubsub_backlog_count"),
                resultSet.getString("gmail_watch_status"),
                resultSet.getString("telegram_status"),
                instantOrNull(resultSet, "telegram_last_active_at"),
                resultSet.getBoolean("auto_send_rules_enabled"));
    }

    private static TenantListSummary mapTenantListSummary(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TenantListSummary(
                resultSet.getInt("total_count"),
                resultSet.getInt("active_count"),
                resultSet.getInt("paused_count"),
                resultSet.getInt("disconnected_count"),
                resultSet.getInt("gmail_connected_count"),
                resultSet.getInt("telegram_connected_count"),
                resultSet.getInt("active_last_24h_count"),
                resultSet.getInt("active_last_7d_count"),
                resultSet.getInt("gmail_unhealthy_count"),
                resultSet.getInt("automation_failure_30d_count"),
                resultSet.getInt("outbound_blocked_30d_count"),
                resultSet.getInt("low_credit_count"));
    }

    private static TenantDetailOverview mapTenantDetailOverview(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TenantDetailOverview(
                resultSet.getObject("tenant_id", UUID.class),
                instantOrNull(resultSet, "created_at"),
                resultSet.getString("gmail_account_email"),
                resultSet.getString("status"),
                resultSet.getString("gmail_connection_status"),
                resultSet.getString("telegram_status"),
                instantOrNull(resultSet, "last_activity_at"),
                resultSet.getInt("rules_count"),
                resultSet.getInt("enabled_rules_count"),
                stringList(resultSet, "enabled_rule_names"));
    }

    private static TenantHealthSnapshot mapTenantHealthSnapshot(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TenantHealthSnapshot(
                resultSet.getString("token_refresh_status"),
                instantOrNull(resultSet, "last_token_refresh_at"),
                resultSet.getString("watch_status"),
                instantOrNull(resultSet, "last_pubsub_push_at"),
                resultSet.getInt("pubsub_backlog_count"));
    }

    private static TenantActivitySnapshot mapTenantActivitySnapshot(
            ResultSet resultSet, int rowNumber) throws SQLException {
        return new TenantActivitySnapshot(
                resultSet.getInt("last_30d_rule_fire_count"),
                resultSet.getInt("chat_session_count"),
                instantOrNull(resultSet, "last_chat_session_at"),
                resultSet.getString("last_chat_model_selection"),
                resultSet.getInt("total_activity_7d_count"),
                instantOrNull(resultSet, "last_login_at"),
                integerOrNull(resultSet, "total_app_duration_seconds"),
                List.of());
    }

    private static TenantActivityEvent mapTenantActivityEvent(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TenantActivityEvent(
                resultSet.getObject("event_id", UUID.class),
                instantOrNull(resultSet, "occurred_at"),
                resultSet.getString("event_type"),
                resultSet.getString("action_label"),
                resultSet.getString("detail"),
                resultSet.getString("status"),
                integerOrNull(resultSet, "duration_seconds"),
                resultSet.getString("source"),
                resultSet.getBoolean("legacy_data_missing"));
    }

    private static MapSqlParameterSource parametersForTenant(UUID tenantId) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId);
    }

    private static MapSqlParameterSource parametersForTenantList(TenantListQuery tenantListQuery) {
        return new MapSqlParameterSource()
                .addValue("status", tenantListQuery.status())
                .addValue("from", timestampOrNull(tenantListQuery.from()))
                .addValue("to", timestampOrNull(tenantListQuery.to()))
                .addValue("email", tenantListQuery.email());
    }

    private static Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instantOrNull(ResultSet resultSet, String columnName)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Instant instantOrNull(ResultSet resultSet, int columnIndex) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnIndex);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Integer integerOrNull(ResultSet resultSet, String columnName)
            throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private static List<String> stringList(ResultSet resultSet, String columnName)
            throws SQLException {
        java.sql.Array sqlArray = resultSet.getArray(columnName);
        if (sqlArray == null) {
            return List.of();
        }
        Object[] values = (Object[]) sqlArray.getArray();
        List<String> strings = new java.util.ArrayList<>(values.length);
        for (Object value : values) {
            if (value != null) {
                strings.add(value.toString());
            }
        }
        return strings;
    }
}
