package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class AdminTenantAccess {

    private final AdminAuditWriter adminAuditWriter;

    public AdminTenantAccess(AdminAuditWriter adminAuditWriter) {
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
    }

    public <T> T readOnly(UUID tenantId, Supplier<T> supplier) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        adminAuditWriter.writeReadEvent(
                adminUser, "TENANT_INSPECTION", "TENANT_INSPECTION", targetTenantId);
        return runTenantScoped(targetTenantId, supplier);
    }

    public <T> T deletionPreview(UUID tenantId, Supplier<T> supplier) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        adminAuditWriter.writeReadEvent(
                adminUser, "TENANT_DELETION_PREVIEW", "TENANT_DELETION_PREVIEW", targetTenantId);
        return runTenantScoped(targetTenantId, supplier);
    }

    public <T> T crossTenantList(Supplier<T> supplier) {
        AdminUser adminUser = AdminContext.currentOrThrow();
        adminAuditWriter.writeReadEvent(adminUser, "TENANT_LIST", "TENANT_LIST", null);
        return supplier.get();
    }

    public <T> T runTenantScoped(UUID tenantId, Supplier<T> supplier) {
        UUID targetTenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        ResultBox<T> resultBox = new ResultBox<>();
        ScopedValue.where(TenantContext.TENANT, targetTenantId.toString())
                .run(() -> resultBox.value = supplier.get());
        return resultBox.value;
    }

    private static final class ResultBox<T> {
        private T value;
    }
}
