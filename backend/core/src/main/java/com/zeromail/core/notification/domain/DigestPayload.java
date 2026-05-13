package com.zeromail.core.notification.domain;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record DigestPayload(
        Locale locale,
        UUID tenantId,
        LocalDate digestDayLocal,
        DigestTotals totals,
        List<DigestTopSender> topSenders,
        List<DigestRuleHit> topRules,
        URI ctaUrl,
        URI optOutUrl,
        boolean zeroActivity) {

    public DigestPayload {
        topSenders = List.copyOf(topSenders);
        topRules = List.copyOf(topRules);
    }
}
