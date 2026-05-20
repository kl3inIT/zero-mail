package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TenantDisconnectService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantOAuthRevocationGateway tenantOAuthRevocationGateway;
    private final AdminAuditWriter adminAuditWriter;
    private final TransactionTemplate transactionTemplate;

    public TenantDisconnectService(
            JdbcTemplate jdbcTemplate,
            TenantOAuthRevocationGateway tenantOAuthRevocationGateway,
            AdminAuditWriter adminAuditWriter,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.tenantOAuthRevocationGateway =
                Objects.requireNonNull(
                        tenantOAuthRevocationGateway,
                        "tenantOAuthRevocationGateway must not be null");
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void disconnect(UUID tenantId, String reason, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (!tenantExists(targetTenantId)) {
            throw new TenantNotFoundException(targetTenantId);
        }
        tenantOAuthRevocationGateway.revoke(targetTenantId);
        transactionTemplate.executeWithoutResult(
                _ ->
                        adminAuditWriter.append(
                                AdminAuditAction.TENANT_DISCONNECTED,
                                "TENANT",
                                targetTenantId,
                                null,
                                tenantStateJson(targetTenantId),
                                reason,
                                requestIp,
                                requestId));
    }

    private boolean tenantExists(UUID tenantId) {
        Boolean exists =
                jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM tenants WHERE id = ?)",
                        Boolean.class,
                        tenantId);
        return Boolean.TRUE.equals(exists);
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
