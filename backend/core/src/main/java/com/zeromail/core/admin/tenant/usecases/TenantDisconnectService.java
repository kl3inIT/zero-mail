package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.tenant.persistence.lowlevel.TenantStateRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TenantDisconnectService {

    private final TenantStateRepository tenantStateRepository;
    private final TenantOAuthRevocationGateway tenantOAuthRevocationGateway;
    private final AdminAuditWriter adminAuditWriter;
    private final TransactionTemplate transactionTemplate;

    public TenantDisconnectService(
            TenantStateRepository tenantStateRepository,
            TenantOAuthRevocationGateway tenantOAuthRevocationGateway,
            AdminAuditWriter adminAuditWriter,
            PlatformTransactionManager transactionManager) {
        this.tenantStateRepository =
                Objects.requireNonNull(
                        tenantStateRepository, "tenantStateRepository must not be null");
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
        if (!tenantStateRepository.exists(targetTenantId)) {
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
                                tenantStateRepository.findStateJson(targetTenantId),
                                reason,
                                requestIp,
                                requestId));
    }
}
