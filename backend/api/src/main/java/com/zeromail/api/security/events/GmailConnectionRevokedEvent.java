package com.zeromail.api.security.events;

import java.time.Instant;
import java.util.UUID;

public record GmailConnectionRevokedEvent(UUID tenantId, Instant at) {}
