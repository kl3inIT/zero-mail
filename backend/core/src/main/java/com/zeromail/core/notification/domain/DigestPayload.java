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
        boolean zeroActivity,
        List<DigestContentSection> contentSections) {

    public DigestPayload {
        topSenders = List.copyOf(topSenders);
        topRules = List.copyOf(topRules);
        contentSections = List.copyOf(contentSections);
    }

    /**
     * Backward-compatible constructor for the stats-only digest (no content sections). Delegates to
     * the canonical constructor with an empty content list.
     */
    public DigestPayload(
            Locale locale,
            UUID tenantId,
            LocalDate digestDayLocal,
            DigestTotals totals,
            List<DigestTopSender> topSenders,
            List<DigestRuleHit> topRules,
            URI ctaUrl,
            URI optOutUrl,
            boolean zeroActivity) {
        this(
                locale,
                tenantId,
                digestDayLocal,
                totals,
                topSenders,
                topRules,
                ctaUrl,
                optOutUrl,
                zeroActivity,
                List.of());
    }

    public boolean hasContentSections() {
        return !contentSections.isEmpty();
    }

    /**
     * Returns a copy of this payload with the given content sections. Used by the worker to graft
     * the IZ-style content digest onto the stats-only payload produced by the core composer,
     * keeping the cross-module audit/Gmail/LLM orchestration out of the core notification module.
     */
    public DigestPayload withContentSections(List<DigestContentSection> contentSections) {
        return new DigestPayload(
                locale,
                tenantId,
                digestDayLocal,
                totals,
                topSenders,
                topRules,
                ctaUrl,
                optOutUrl,
                zeroActivity,
                contentSections);
    }
}
