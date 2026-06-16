package com.zeromail.api.security.events;

import java.time.Instant;

public record OAuth2TokenRefreshFailed(
        String tenantId, String gmailConnectionId, String errorCode, Instant at) {

    public OAuth2TokenRefreshFailed(String tenantId, String errorCode, Instant at) {
        this(tenantId, null, errorCode, at);
    }
}
