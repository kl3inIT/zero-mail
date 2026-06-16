package com.zeromail.api.security;

import com.zeromail.core.admin.tenant.usecases.TenantActivityRequestContext;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.session.Session;

record SessionActivityState(
        UUID tenantId, Instant loginAt, TenantActivityRequestContext requestContext) {

    static SessionActivityState from(HttpSession session) {
        return from(
                session.getAttribute(TenantActivitySessionAttributes.TENANT_ID),
                session.getAttribute(TenantActivitySessionAttributes.LOGIN_AT));
    }

    static SessionActivityState from(Session session) {
        return from(
                session.getAttribute(TenantActivitySessionAttributes.TENANT_ID),
                session.getAttribute(TenantActivitySessionAttributes.LOGIN_AT));
    }

    int durationSeconds(Instant endedAt) {
        long durationSeconds = Math.max(0, Duration.between(loginAt, endedAt).getSeconds());
        return durationSeconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) durationSeconds;
    }

    private static SessionActivityState from(Object tenantIdValue, Object loginAtValue) {
        if (!(tenantIdValue instanceof String tenantIdText)
                || !(loginAtValue instanceof String loginAtText)) {
            return null;
        }
        try {
            return new SessionActivityState(
                    UUID.fromString(tenantIdText),
                    Instant.parse(loginAtText),
                    new TenantActivityRequestContext());
        } catch (DateTimeParseException | IllegalArgumentException invalidSessionState) {
            return null;
        }
    }
}
