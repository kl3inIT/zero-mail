package com.zeromail.core.admin.tenant.projection;

public record TenantDeletionPreview(
        int gmailConnections,
        int chatSessions,
        int rules,
        int triageAudits,
        int chatMessages,
        int byokCredentials) {}
