package com.zeromail.api.security;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.UUID;

final class TenantActivitySessionAttributes {

    static final String TENANT_ID = "zeromail.tenantActivity.tenantId";
    static final String LOGIN_AT = "zeromail.tenantActivity.loginAt";

    private TenantActivitySessionAttributes() {}

    static void storeLogin(HttpSession session, UUID tenantId, Instant loginAt) {
        session.setAttribute(TENANT_ID, tenantId.toString());
        session.setAttribute(LOGIN_AT, loginAt.toString());
    }
}
