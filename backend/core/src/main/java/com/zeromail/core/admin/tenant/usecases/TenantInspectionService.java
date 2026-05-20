package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.tenant.projection.TenantActivitySnapshot;
import com.zeromail.core.admin.tenant.projection.TenantBillingSnapshot;
import com.zeromail.core.admin.tenant.projection.TenantDeletionPreview;
import com.zeromail.core.admin.tenant.projection.TenantDetailOverview;
import com.zeromail.core.admin.tenant.projection.TenantHealthSnapshot;
import com.zeromail.core.admin.tenant.projection.TenantListPage;
import com.zeromail.core.admin.tenant.projection.TenantListQuery;
import com.zeromail.core.admin.tenant.projection.TenantListRow;
import com.zeromail.core.admin.tenant.projection.TenantSpendSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantInspectionService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public TenantInspectionService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
    }

    @Transactional(readOnly = true)
    public TenantListPage listTenants(TenantListQuery query) {
        TenantListQuery tenantListQuery = Objects.requireNonNull(query, "query must not be null");
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("status", tenantListQuery.status())
                        .addValue("from", timestampOrNull(tenantListQuery.from()))
                        .addValue("to", timestampOrNull(tenantListQuery.to()))
                        .addValue("limit", tenantListQuery.limit() + 1)
                        .addValue("offset", tenantListQuery.offset());
        List<TenantListRow> rows =
                namedParameterJdbcTemplate.query(
                        """
                        SELECT tenant_id, created_at, gmail_account_email, status,
                               spend_bucket_7d
                        FROM (
                            SELECT t.id AS tenant_id,
                                   t.created_at AS created_at,
                                   gc.google_email AS gmail_account_email,
                                   CASE
                                       WHEN t.triage_paused THEN 'PAUSED'
                                       WHEN COALESCE(gc.status, 'DISCONNECTED') = 'DISCONNECTED'
                                           THEN 'DISCONNECTED'
                                       ELSE 'ACTIVE'
                                   END AS status,
                                   CASE
                                       WHEN COALESCE(spend.last_7d_call_count, 0) >= 100 THEN 'HIGH'
                                       WHEN COALESCE(spend.last_7d_call_count, 0) >= 10 THEN 'MEDIUM'
                                       ELSE 'LOW'
                                   END AS spend_bucket_7d
                            FROM tenants t
                            LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
                            LEFT JOIN (
                                SELECT tenant_id, COUNT(*)::int AS last_7d_call_count
                                FROM credit_ledger_entry
                                WHERE kind = 'RESERVE'
                                  AND created_at >= NOW() - INTERVAL '7 days'
                                GROUP BY tenant_id
                            ) spend ON spend.tenant_id = t.id
                        ) tenant_rows
                        WHERE (:status IS NULL OR status = :status)
                          AND (CAST(:from AS timestamptz) IS NULL OR created_at >= :from)
                          AND (CAST(:to AS timestamptz) IS NULL OR created_at <= :to)
                        ORDER BY created_at DESC, tenant_id DESC
                        LIMIT :limit OFFSET :offset
                        """,
                        parameters,
                        (resultSet, _) -> mapTenantListRow(resultSet));
        boolean hasNextPage = rows.size() > tenantListQuery.limit();
        List<TenantListRow> visibleRows =
                hasNextPage ? rows.subList(0, tenantListQuery.limit()) : rows;
        String nextCursor =
                hasNextPage
                        ? String.valueOf(tenantListQuery.offset() + tenantListQuery.limit())
                        : null;
        return new TenantListPage(visibleRows, nextCursor, hasNextPage);
    }

    @Transactional(readOnly = true)
    public TenantDetailOverview getOverview(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        return queryOne(
                """
                SELECT t.id AS tenant_id,
                       t.created_at AS created_at,
                       gc.google_email AS gmail_account_email,
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
                       COUNT(DISTINCT rules.id)::int AS rules_count
                FROM tenants t
                LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
                LEFT JOIN rules ON rules.tenant_id = t.id
                LEFT JOIN chat ON chat.tenant_id = t.id
                LEFT JOIN triage_audit triage ON triage.tenant_id = t.id
                LEFT JOIN mail_message_observed observed ON observed.tenant_id = t.id
                WHERE t.id = :tenantId
                GROUP BY t.id, t.created_at, t.triage_paused, gc.google_email, gc.status, gc.updated_at
                """,
                parametersForTenant(targetTenantId),
                this::mapTenantDetailOverview);
    }

    @Transactional(readOnly = true)
    public TenantHealthSnapshot getHealth(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        return queryOne(
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
                parametersForTenant(targetTenantId),
                this::mapTenantHealthSnapshot);
    }

    @Transactional(readOnly = true)
    public TenantBillingSnapshot getBilling(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        requireTenantExists(targetTenantId);
        MapSqlParameterSource parameters = parametersForTenant(targetTenantId);
        Integer creditsBalance =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT COALESCE(SUM(amount_credits), 0)::int
                        FROM credit_ledger_entry
                        WHERE tenant_id = :tenantId
                        """,
                        parameters,
                        Integer.class);
        Instant lastTopUpAt =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT MAX(created_at)
                        FROM credit_ledger_entry
                        WHERE tenant_id = :tenantId AND kind = 'TOPUP'
                        """,
                        parameters,
                        (resultSet, _) -> instantOrNull(resultSet, 1));
        return new TenantBillingSnapshot(
                creditsBalance == null ? 0 : creditsBalance, "PAY_AS_YOU_GO", lastTopUpAt);
    }

    @Transactional(readOnly = true)
    public TenantSpendSnapshot getSpend(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        requireTenantExists(targetTenantId);
        MapSqlParameterSource parameters = parametersForTenant(targetTenantId);
        Integer last7dCallCount =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)::int
                        FROM credit_ledger_entry
                        WHERE tenant_id = :tenantId
                          AND kind = 'RESERVE'
                          AND created_at >= NOW() - INTERVAL '7 days'
                        """,
                        parameters,
                        Integer.class);
        Integer last30dCallCount =
                namedParameterJdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)::int
                        FROM credit_ledger_entry
                        WHERE tenant_id = :tenantId
                          AND kind = 'RESERVE'
                          AND created_at >= NOW() - INTERVAL '30 days'
                        """,
                        parameters,
                        Integer.class);
        Map<String, Integer> perFeatureCallCount = new LinkedHashMap<>();
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
                parameters,
                (RowCallbackHandler)
                        resultSet ->
                                perFeatureCallCount.put(
                                        resultSet.getString("ref_type"),
                                        resultSet.getInt("call_count")));
        int safeLast7dCallCount = last7dCallCount == null ? 0 : last7dCallCount;
        int safeLast30dCallCount = last30dCallCount == null ? 0 : last30dCallCount;
        return new TenantSpendSnapshot(
                safeLast7dCallCount,
                safeLast30dCallCount,
                bucketForCallCount(safeLast7dCallCount),
                bucketForCallCount(safeLast30dCallCount),
                perFeatureCallCount);
    }

    @Transactional(readOnly = true)
    public TenantActivitySnapshot getActivity(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        return queryOne(
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
                        WHERE tenant_id = :tenantId) AS last_chat_model_selection
                FROM tenants
                WHERE id = :tenantId
                """,
                parametersForTenant(targetTenantId),
                this::mapTenantActivitySnapshot);
    }

    @Transactional(readOnly = true)
    public String getGmailAccountEmail(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        return namedParameterJdbcTemplate.query(
                """
                SELECT gc.google_email
                FROM tenants t
                LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
                WHERE t.id = :tenantId
                """,
                parametersForTenant(targetTenantId),
                resultSet -> resultSet.next() ? resultSet.getString("google_email") : null);
    }

    @Transactional(readOnly = true)
    public TenantDeletionPreview getDeletionPreview(UUID tenantId) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        requireTenantExists(targetTenantId);
        MapSqlParameterSource parameters = parametersForTenant(targetTenantId);
        return new TenantDeletionPreview(
                count("gmail_connections", parameters),
                count("chat", parameters),
                count("rules", parameters),
                count("triage_audit", parameters),
                count("chat_message", parameters),
                count("tenant_byok_credentials", parameters));
    }

    private void requireTenantExists(UUID tenantId) {
        Boolean exists =
                namedParameterJdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM tenants WHERE id = :tenantId)",
                        parametersForTenant(tenantId),
                        Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw new NoSuchElementException("Tenant not found: " + tenantId);
        }
    }

    private int count(String tableName, MapSqlParameterSource parameters) {
        Integer count =
                namedParameterJdbcTemplate.queryForObject(
                        "SELECT COUNT(*)::int FROM " + tableName + " WHERE tenant_id = :tenantId",
                        parameters,
                        Integer.class);
        return count == null ? 0 : count;
    }

    private static TenantListRow mapTenantListRow(ResultSet resultSet) throws SQLException {
        return new TenantListRow(
                resultSet.getObject("tenant_id", UUID.class),
                instantOrNull(resultSet, "created_at"),
                resultSet.getString("gmail_account_email"),
                resultSet.getString("status"),
                resultSet.getString("spend_bucket_7d"));
    }

    private TenantDetailOverview mapTenantDetailOverview(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TenantDetailOverview(
                resultSet.getObject("tenant_id", UUID.class),
                instantOrNull(resultSet, "created_at"),
                resultSet.getString("gmail_account_email"),
                resultSet.getString("status"),
                instantOrNull(resultSet, "last_activity_at"),
                resultSet.getInt("rules_count"));
    }

    private TenantHealthSnapshot mapTenantHealthSnapshot(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TenantHealthSnapshot(
                resultSet.getString("token_refresh_status"),
                instantOrNull(resultSet, "last_token_refresh_at"),
                resultSet.getString("watch_status"),
                instantOrNull(resultSet, "last_pubsub_push_at"),
                resultSet.getInt("pubsub_backlog_count"));
    }

    private TenantActivitySnapshot mapTenantActivitySnapshot(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TenantActivitySnapshot(
                resultSet.getInt("last_30d_rule_fire_count"),
                resultSet.getInt("chat_session_count"),
                instantOrNull(resultSet, "last_chat_session_at"),
                resultSet.getString("last_chat_model_selection"));
    }

    private <T> T queryOne(
            String sql,
            MapSqlParameterSource parameters,
            org.springframework.jdbc.core.RowMapper<T> rowMapper) {
        List<T> rows = namedParameterJdbcTemplate.query(sql, parameters, rowMapper);
        return rows.stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Tenant not found: " + parameters.getValue("tenantId")));
    }

    private static MapSqlParameterSource parametersForTenant(UUID tenantId) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId);
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

    private static String bucketForCallCount(int callCount) {
        if (callCount >= 100) {
            return "HIGH";
        }
        if (callCount >= 10) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
