package com.zeromail.core.rules.catalog.usecases;

import java.util.Map;
import java.util.UUID;

public record RuleCatalogAuditEvent(
        RuleCatalogAuditAction action,
        String targetKind,
        UUID targetId,
        Map<String, ?> beforeState,
        Map<String, ?> afterState,
        String reason,
        String requestIp,
        UUID requestId) {}
