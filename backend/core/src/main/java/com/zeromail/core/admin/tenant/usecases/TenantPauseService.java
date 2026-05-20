package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.tenant.persistence.lowlevel.TenantStateRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantPauseService {

    private final TenantStateRepository tenantStateRepository;
    private final AdminAuditWriter adminAuditWriter;

    public TenantPauseService(
            TenantStateRepository tenantStateRepository, AdminAuditWriter adminAuditWriter) {
        this.tenantStateRepository =
                Objects.requireNonNull(
                        tenantStateRepository, "tenantStateRepository must not be null");
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
    }

    @Transactional
    public void pause(UUID tenantId, String reason, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        String beforeStateJson = tenantStateRepository.findStateJson(targetTenantId);
        int updatedRows = tenantStateRepository.markPaused(targetTenantId);
        if (updatedRows == 0) {
            throw new TenantNotFoundException(targetTenantId);
        }
        adminAuditWriter.append(
                AdminAuditAction.TENANT_PAUSED,
                "TENANT",
                targetTenantId,
                beforeStateJson,
                tenantStateRepository.findStateJson(targetTenantId),
                reason,
                requestIp,
                requestId);
    }
}
