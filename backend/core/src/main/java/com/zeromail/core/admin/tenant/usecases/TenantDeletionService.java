package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.tenant.persistence.lowlevel.TenantStateRepository;
import com.zeromail.core.admin.tenant.projection.TenantDeletionPreview;
import com.zeromail.core.tenant.usecases.TenantDataDeletionService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TenantDeletionService {

    private final TenantStateRepository tenantStateRepository;
    private final TenantInspectionService tenantInspectionService;
    private final TenantDataDeletionService tenantDataDeletionService;
    private final TenantOAuthRevocationGateway tenantOAuthRevocationGateway;
    private final AdminAuditWriter adminAuditWriter;
    private final TransactionTemplate transactionTemplate;

    public TenantDeletionService(
            TenantStateRepository tenantStateRepository,
            TenantInspectionService tenantInspectionService,
            TenantDataDeletionService tenantDataDeletionService,
            TenantOAuthRevocationGateway tenantOAuthRevocationGateway,
            AdminAuditWriter adminAuditWriter,
            PlatformTransactionManager transactionManager) {
        this.tenantStateRepository =
                Objects.requireNonNull(
                        tenantStateRepository, "tenantStateRepository must not be null");
        this.tenantInspectionService =
                Objects.requireNonNull(
                        tenantInspectionService, "tenantInspectionService must not be null");
        this.tenantDataDeletionService =
                Objects.requireNonNull(
                        tenantDataDeletionService, "tenantDataDeletionService must not be null");
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
        String beforeStateJson = tenantStateRepository.findStateJson(targetTenantId);
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
                    tenantDataDeletionService.deleteTenantData(targetTenantId);
                });
    }
}
