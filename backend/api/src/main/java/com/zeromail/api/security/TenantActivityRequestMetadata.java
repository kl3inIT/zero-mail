package com.zeromail.api.security;

import com.zeromail.core.admin.tenant.usecases.TenantActivityRequestContext;
import jakarta.servlet.http.HttpServletRequest;

final class TenantActivityRequestMetadata {

    private TenantActivityRequestMetadata() {}

    static TenantActivityRequestContext from(HttpServletRequest request) {
        return new TenantActivityRequestContext();
    }
}
