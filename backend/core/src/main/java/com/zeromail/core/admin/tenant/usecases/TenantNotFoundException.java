package com.zeromail.core.admin.tenant.usecases;

import com.zeromail.core.admin.shared.AdminBusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.UUID;

public class TenantNotFoundException extends AdminBusinessException {

    public TenantNotFoundException(UUID tenantId) {
        super("Tenant not found: " + tenantId);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.NOT_FOUND;
    }

    @Override
    public String errorCode() {
        return "error.admin.tenant_not_found";
    }

    @Override
    public String logEvent() {
        return "admin_tenant_not_found";
    }

    @Override
    public String detail() {
        return "The requested tenant could not be located.";
    }
}
