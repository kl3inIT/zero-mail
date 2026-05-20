package com.zeromail.core.admin.tenant.persistence.lowlevel;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tenant lifecycle write + small read helpers used by the admin tenant mutation services
 * (Pause/Disconnect/Deletion). Centralises the {@code tenants} + {@code gmail_connections} schema
 * knowledge that previously lived in three separate services.
 */
@Repository
public class TenantStateRepository {

    private static final String STATE_JSON_SQL =
            """
            SELECT jsonb_build_object(
                'tenantId', t.id,
                'status', CASE
                    WHEN t.triage_paused THEN 'PAUSED'
                    WHEN COALESCE(gc.status, 'DISCONNECTED') = 'DISCONNECTED' THEN 'DISCONNECTED'
                    ELSE 'ACTIVE'
                END,
                'gmailAccountEmail', gc.google_email,
                'createdAt', t.created_at
            )::text AS state_json
            FROM tenants t
            LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
            WHERE t.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public TenantStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * @return JSON snapshot of tenant state, or {@code null} when no row matches.
     */
    public String findStateJson(UUID tenantId) {
        return jdbcTemplate.query(
                STATE_JSON_SQL,
                resultSet -> resultSet.next() ? resultSet.getString("state_json") : null,
                tenantId);
    }

    public boolean exists(UUID tenantId) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM tenants WHERE id = ?)",
                        Boolean.class,
                        tenantId);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * @return number of rows updated (1 on success, 0 when the tenant does not exist).
     */
    public int markPaused(UUID tenantId) {
        return jdbcTemplate.update(
                "UPDATE tenants SET triage_paused = true WHERE id = ?", tenantId);
    }

    public int deleteAllForTenant(String tableName, String tenantIdColumn, UUID tenantId) {
        return jdbcTemplate.update(
                "DELETE FROM " + tableName + " WHERE " + tenantIdColumn + " = ?", tenantId);
    }
}
