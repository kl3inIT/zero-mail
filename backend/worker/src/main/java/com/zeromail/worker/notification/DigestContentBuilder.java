package com.zeromail.worker.notification;

import com.zeromail.core.gmail.usecases.RecentInboxReadService;
import com.zeromail.core.llm.usecases.DigestSummaryLine;
import com.zeromail.core.llm.usecases.DigestSummarySource;
import com.zeromail.core.llm.usecases.LlmGateway;
import com.zeromail.core.notification.domain.DigestContentEntry;
import com.zeromail.core.notification.domain.DigestContentSection;
import com.zeromail.core.triage.projection.DigestSourceItem;
import com.zeromail.core.triage.usecases.AuditLogQueryService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds the IZ-style content section of the weekly digest at send time, entirely from
 * privacy-clean sources.
 *
 * <p>The pointers (Gmail message id, sanitized subject, sanitized sender, rule-name snapshot) come
 * from {@code triage_audit} — already persisted when each {@code add_to_digest} rule fired, with no
 * body. For each pointer the body is fetched fresh from Gmail, handed to the LLM gateway as one
 * batched summarization call, and discarded. Neither the body nor the generated summaries are ever
 * persisted or logged. The audit window itself bounds the set, so there is no pending table to
 * drain and nothing to delete afterwards.
 *
 * <p>This orchestration lives in the worker (not the core notification module) because it spans the
 * triage, gmail, and llm modules; doing it here keeps the core notification module's dependency
 * surface unchanged. Every step is best-effort: any failure degrades to fewer sections, fewer
 * summaries, or none — the weekly digest still sends its stats.
 */
@Component
public class DigestContentBuilder {

    /** Caps both the audit scan and the single batched summarization call. */
    static final int MAX_DIGEST_ITEMS = 20;

    private static final Logger log = LoggerFactory.getLogger(DigestContentBuilder.class);

    private final AuditLogQueryService auditLogQueryService;
    private final RecentInboxReadService recentInboxReadService;
    private final LlmGateway llmGateway;

    public DigestContentBuilder(
            AuditLogQueryService auditLogQueryService,
            RecentInboxReadService recentInboxReadService,
            LlmGateway llmGateway) {
        this.auditLogQueryService =
                Objects.requireNonNull(
                        auditLogQueryService, "auditLogQueryService must not be null");
        this.recentInboxReadService =
                Objects.requireNonNull(
                        recentInboxReadService, "recentInboxReadService must not be null");
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway must not be null");
    }

    /**
     * Returns the content sections for {@code [windowStart, sendMoment)}, grouped by rule, most
     * active rule first. Returns an empty list when there is nothing to show or any stage fails.
     */
    public List<DigestContentSection> buildSections(
            UUID tenantId, Instant windowStart, Instant sendMoment) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        List<DigestSourceItem> items;
        try {
            items =
                    auditLogQueryService.findDigestSourceItems(
                            tenantId, windowStart, sendMoment, MAX_DIGEST_ITEMS);
        } catch (RuntimeException auditReadFailure) {
            log.warn(
                    "event=digest_content_audit_read_failed tenantId={} errorClass={}",
                    tenantId,
                    auditReadFailure.getClass().getSimpleName());
            return List.of();
        }
        if (items.isEmpty()) {
            return List.of();
        }

        Map<String, String> summaryByMessageId = summarize(tenantId, items);
        List<DigestContentSection> sections = groupByRule(items, summaryByMessageId);
        log.info(
                "event=digest_content_built tenantId={} itemCount={} summaryCount={} sectionCount={}",
                tenantId,
                items.size(),
                summaryByMessageId.size(),
                sections.size());
        return sections;
    }

    private Map<String, String> summarize(UUID tenantId, List<DigestSourceItem> items) {
        List<DigestSummarySource> sources = new ArrayList<>(items.size());
        for (DigestSourceItem item : items) {
            Optional<String> body =
                    recentInboxReadService.fetchPlainTextForDigest(tenantId, item.gmailMessageId());
            body.ifPresent(
                    content ->
                            sources.add(new DigestSummarySource(item.gmailMessageId(), content)));
        }
        if (sources.isEmpty()) {
            return Map.of();
        }
        List<DigestSummaryLine> lines;
        try {
            lines = llmGateway.summarizeDigestItems(sources);
        } catch (RuntimeException summarizationFailure) {
            log.warn(
                    "event=digest_content_summary_failed tenantId={} errorClass={}",
                    tenantId,
                    summarizationFailure.getClass().getSimpleName());
            return Map.of();
        }
        Map<String, String> summaryByMessageId = new HashMap<>(lines.size());
        for (DigestSummaryLine line : lines) {
            if (line.summary() != null && !line.summary().isBlank()) {
                summaryByMessageId.put(line.ref(), line.summary());
            }
        }
        return summaryByMessageId;
    }

    private static List<DigestContentSection> groupByRule(
            List<DigestSourceItem> items, Map<String, String> summaryByMessageId) {
        // Items arrive most-recent first; LinkedHashMap preserves that order so the rule whose most
        // recent message is newest leads the digest.
        Map<String, List<DigestContentEntry>> entriesByRule = new LinkedHashMap<>();
        for (DigestSourceItem item : items) {
            String ruleName = item.ruleNameSnapshot() == null ? "" : item.ruleNameSnapshot();
            entriesByRule
                    .computeIfAbsent(ruleName, _ -> new ArrayList<>())
                    .add(
                            new DigestContentEntry(
                                    item.sanitizedSenderEmail(),
                                    item.sanitizedSubject(),
                                    summaryByMessageId.get(item.gmailMessageId())));
        }
        List<DigestContentSection> sections = new ArrayList<>(entriesByRule.size());
        entriesByRule.forEach(
                (ruleName, entries) -> sections.add(new DigestContentSection(ruleName, entries)));
        return sections;
    }
}
