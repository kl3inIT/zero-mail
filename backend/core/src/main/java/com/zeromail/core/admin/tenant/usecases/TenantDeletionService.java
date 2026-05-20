package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.tenant.projection.TenantDeletionPreview;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TenantDeletionService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantInspectionService tenantInspectionService;
    private final TenantDeletionRegistry tenantDeletionRegistry;
    private final TenantOAuthRevocationGateway tenantOAuthRevocationGateway;
    private final AdminAuditWriter adminAuditWriter;
    private final TransactionTemplate transactionTemplate;

    public TenantDeletionService(
            JdbcTemplate jdbcTemplate,
            TenantInspectionService tenantInspectionService,
            TenantDeletionRegistry tenantDeletionRegistry,
            TenantOAuthRevocationGateway tenantOAuthRevocationGateway,
            AdminAuditWriter adminAuditWriter,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.tenantInspectionService =
                Objects.requireNonNull(
                        tenantInspectionService, "tenantInspectionService must not be null");
        this.tenantDeletionRegistry =
                Objects.requireNonNull(
                        tenantDeletionRegistry, "tenantDeletionRegistry must not be null");
        this.tenantOAuthRevocationGateway =
                Objects.requireNonNull(
                        tenantOAuthRevocationGateway,
                        "tenantOAuthRevocationGateway must not be null");
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TenantDeletionPreview preview(UUID tenantId) {
        return tenantInspectionService.getDeletionPreview(tenantId);
    }

    public void delete(UUID tenantId, String reason, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        String beforeStateJson = tenantStateJson(targetTenantId);
        if (beforeStateJson == null) {
            throw new TenantNotFoundException(targetTenantId);
        }
        tenantOAuthRevocationGateway.revoke(targetTenantId);
        transactionTemplate.executeWithoutResult(
                _ -> {
                    adminAuditWriter.append(
                            AdminAuditAction.TENANT_DELETED,
                            "TENANT",
                            targetTenantId,
                            beforeStateJson,
                            null,
                            reason,
                            requestIp,
                            requestId);
                    for (TenantDeletionRegistry.TenantOwnedTable tenantOwnedTable :
                            tenantDeletionRegistry.orderedDeletionPath()) {
                        jdbcTemplate.update(
                                "DELETE FROM "
                                        + tenantOwnedTable.tableName()
                                        + " WHERE "
                                        + tenantOwnedTable.tenantIdColumn()
                                        + " = ?",
                                targetTenantId);
                    }
                });
    }

    private String tenantStateJson(UUID tenantId) {
        return jdbcTemplate.query(
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
                """,
                resultSet -> resultSet.next() ? resultSet.getString("state_json") : null,
                tenantId);
    }
}
