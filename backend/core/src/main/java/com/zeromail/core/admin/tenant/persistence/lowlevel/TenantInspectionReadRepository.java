package com.zeromail.core.admin.tenant.persistence.lowlevel;

import com.zeromail.core.admin.tenant.projection.TenantActivitySnapshot;
import com.zeromail.core.admin.tenant.projection.TenantDetailOverview;
import com.zeromail.core.admin.tenant.projection.TenantHealthSnapshot;
import com.zeromail.core.admin.tenant.projection.TenantListQuery;
import com.zeromail.core.admin.tenant.projection.TenantListRow;
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

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public TenantInspectionReadRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate =
                Objects.requireNonNull(
                        namedParameterJdbcTemplate, "namedParameterJdbcTemplate must not be null");
    }

    public List<TenantListRow> findTenantListRows(TenantListQuery tenantListQuery) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("status", tenantListQuery.status())
                        .addValue("from", timestampOrNull(tenantListQuery.from()))
                        .addValue("to", timestampOrNull(tenantListQuery.to()))
                        .addValue("limit", tenantListQuery.limit() + 1)
                        .addValue("offset", tenantListQuery.offset());
        return namedParameterJdbcTemplate.query(
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
    }

    public Optional<TenantDetailOverview> findOverview(UUID tenantId) {
        return queryOptional(
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
        return queryOptional(
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
                parametersForTenant(tenantId),
                TenantInspectionReadRepository::mapTenantActivitySnapshot);
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
                resultSet.getString("spend_bucket_7d"));
    }

    private static TenantDetailOverview mapTenantDetailOverview(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new TenantDetailOverview(
                resultSet.getObject("tenant_id", UUID.class),
                instantOrNull(resultSet, "created_at"),
                resultSet.getString("gmail_account_email"),
                resultSet.getString("status"),
                instantOrNull(resultSet, "last_activity_at"),
                resultSet.getInt("rules_count"));
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
                resultSet.getString("last_chat_model_selection"));
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
}
