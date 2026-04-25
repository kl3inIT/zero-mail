package com.zeromail.api.security.events;

import java.time.Instant;

public record OAuth2TokenRefreshFailed(String tenantId, String errorCode, Instant at) {}
