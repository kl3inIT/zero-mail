package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantPauseService {

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditWriter adminAuditWriter;

    public TenantPauseService(JdbcTemplate jdbcTemplate, AdminAuditWriter adminAuditWriter) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
    }

    @Transactional
    public void pause(UUID tenantId, String reason, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        String beforeStateJson = tenantStateJson(targetTenantId);
        int updatedRows =
                jdbcTemplate.update(
                        "UPDATE tenants SET triage_paused = true WHERE id = ?", targetTenantId);
        if (updatedRows == 0) {
            throw new TenantNotFoundException(targetTenantId);
        }
        adminAuditWriter.append(
                AdminAuditAction.TENANT_PAUSED,
                "TENANT",
                targetTenantId,
                beforeStateJson,
                tenantStateJson(targetTenantId),
                reason,
                requestIp,
                requestId);
    }

    private String tenantStateJson(UUID tenantId) {
        return jdbcTemplate.queryForObject(
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
                )::text
                FROM tenants t
                LEFT JOIN gmail_connections gc ON gc.tenant_id = t.id
                WHERE t.id = ?
                """,
                String.class,
                tenantId);
    }
}
