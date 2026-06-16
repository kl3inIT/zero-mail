package com.zeromail.core.gmail.persistence.lowlevel;

import java.util.UUID;

public record TenantMailboxRef(UUID tenantId, UUID gmailConnectionId) {}
