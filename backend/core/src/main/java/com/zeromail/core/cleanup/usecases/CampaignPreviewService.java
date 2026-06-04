package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy;
import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import com.zeromail.core.cleanup.exception.CampaignCapExceededException;
import com.zeromail.core.cleanup.persistence.SenderSuppressionRepository;
import com.zeromail.core.cleanup.usecases.CleanupRecentInboxWorkingSetService.SenderWorkingSet;
import com.zeromail.core.cleanup.usecases.CleanupRecentInboxWorkingSetService.WorkingSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UNS-03 — Dry-run preview for a bulk-unsubscribe campaign. Validates the caller's sender selection
 * against the {@link UnsubscribeCampaignPolicy} caps and returns a per-sender preview row that the
 * UI renders as a confirmation screen before the user invokes {@link CampaignExecuteService}.
 *
 * <p>Cap checks run in two passes — distinct-sender count first (UNS-03a), aggregate history
 * message count second (UNS-03b). Both raise {@link CampaignCapExceededException} with the matching
 * {@link CampaignCapExceededException.Kind} so the controller can map to the correct error code
 * without case-splitting on exception type.
 *
 * <p>Per-sender lookup reads {@code mail_message_observed} for the 30-day window matching {@link
 * CandidateQueryService}'s default, and cross-checks {@code sender_suppression} via both {@code
 * sender_email} and {@code sender_domain} so a tenant-blocked sender never enters a campaign even
 * when the controller forgot to filter.
 *
 * <p>Privacy invariant (UNS-09): the log line records counts only, never the targeted sender list.
 */
@Service
@Transactional(readOnly = true)
public class CampaignPreviewService {

    private static final Logger log = LoggerFactory.getLogger(CampaignPreviewService.class);

    /** History window mirrors {@code CandidateQueryService} default (UNS-01 / D-09). */
    private static final Duration HISTORY_WINDOW = Duration.ofDays(30);

    private static final String PER_SENDER_AGGREGATE_SQL =
            "SELECT COUNT(*) AS message_count, "
                    + "       BOOL_OR(list_unsubscribe_one_click) AS has_one_click, "
                    + "       BOOL_OR(list_unsubscribe_mailto IS NOT NULL) AS has_mailto "
                    + "FROM mail_message_observed "
                    + "WHERE tenant_id = ? "
                    + "  AND sender_email = ? "
                    + "  AND observed_at >= ? "
                    + "  AND observed_at < ?";

    private final JdbcTemplate jdbcTemplate;
    private final SenderSuppressionRepository senderSuppressionRepository;
    private final CleanupRecentInboxWorkingSetService cleanupRecentInboxWorkingSetService;
    private final Clock clock;

    public CampaignPreviewService(
            JdbcTemplate jdbcTemplate,
            SenderSuppressionRepository senderSuppressionRepository,
            CleanupRecentInboxWorkingSetService cleanupRecentInboxWorkingSetService,
            Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.senderSuppressionRepository =
                Objects.requireNonNull(
                        senderSuppressionRepository,
                        "senderSuppressionRepository must not be null");
        this.cleanupRecentInboxWorkingSetService =
                Objects.requireNonNull(
                        cleanupRecentInboxWorkingSetService,
                        "cleanupRecentInboxWorkingSetService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Build a preview for the selection. {@link CampaignCapExceededException} fires when the
     * selection exceeds either policy cap; the controller maps that to HTTP 422 with the matching
     * error code.
     */
    public CampaignPreviewResult preview(UUID tenantId, List<String> senderEmails) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(senderEmails, "senderEmails must not be null");

        if (senderEmails.size() > UnsubscribeCampaignPolicy.MAX_SENDERS_PER_CAMPAIGN) {
            throw new CampaignCapExceededException(
                    CampaignCapExceededException.Kind.TOO_MANY_SENDERS,
                    senderEmails.size(),
                    UnsubscribeCampaignPolicy.MAX_SENDERS_PER_CAMPAIGN);
        }

        Optional<WorkingSet> recentInboxWorkingSet =
                cleanupRecentInboxWorkingSetService.findRecentInboxWorkingSet(
                        tenantId, HISTORY_WINDOW);

        List<PerSenderPreview> perSender = new ArrayList<>(senderEmails.size());
        long archivableHistoryCount = 0L;
        for (String senderEmail : senderEmails) {
            PerSenderPreview senderPreview =
                    lookupPerSenderPreview(tenantId, senderEmail, recentInboxWorkingSet);
            perSender.add(senderPreview);
            if (senderPreview.willArchive()) {
                archivableHistoryCount += senderPreview.messageCount();
            }
        }

        if (archivableHistoryCount > UnsubscribeCampaignPolicy.MAX_HISTORY_MESSAGES_PER_CAMPAIGN) {
            int clampedHistoryCount =
                    archivableHistoryCount > Integer.MAX_VALUE
                            ? Integer.MAX_VALUE
                            : (int) archivableHistoryCount;
            throw new CampaignCapExceededException(
                    CampaignCapExceededException.Kind.TOO_MANY_MESSAGES,
                    clampedHistoryCount,
                    UnsubscribeCampaignPolicy.MAX_HISTORY_MESSAGES_PER_CAMPAIGN);
        }

        log.info(
                "event=cleanup_campaign_previewed tenantId={} senderCount={} archivableHistoryCount={}",
                tenantId,
                perSender.size(),
                archivableHistoryCount);

        return new CampaignPreviewResult(perSender, (int) archivableHistoryCount);
    }

    private PerSenderPreview lookupPerSenderPreview(
            UUID tenantId, String senderEmail, Optional<WorkingSet> recentInboxWorkingSet) {
        String normalizedSenderEmail = requireText(senderEmail, "senderEmail");
        Optional<SenderWorkingSet> senderWorkingSet =
                recentInboxWorkingSet.flatMap(
                        workingSet -> workingSet.findSender(normalizedSenderEmail));
        // Trust the live working set only when its preview parse actually found a usable
        // List-Unsubscribe handle. If the working set has the sender but reports NONE, fall through
        // to the observed-DB aggregate below: the webhook parser that populates
        // mail_message_observed can recognise a header the Gmail-preview parser misses (e.g.
        // Facebook's List-Unsubscribe), and that DB row is exactly what the candidate list showed
        // the user as "Unsubscribe". Returning a working-set NONE here is what made execute reject
        // (409 "no archivable senders") senders the list had offered as unsubscribable.
        if (senderWorkingSet.isPresent()
                && senderWorkingSet.orElseThrow().unsubscribeMethod() != UnsubscribeMethod.NONE) {
            return fromWorkingSet(senderWorkingSet.orElseThrow());
        }

        String senderDomain = extractDomain(normalizedSenderEmail);

        boolean suppressed =
                senderSuppressionRepository
                                .findByTenantIdAndSenderEmail(tenantId, normalizedSenderEmail)
                                .isPresent()
                        || senderSuppressionRepository
                                .findByTenantIdAndSenderDomain(tenantId, senderDomain)
                                .isPresent();

        Instant now = clock.instant();
        Instant windowStartInclusive = now.minus(HISTORY_WINDOW);
        Timestamp windowStartTimestamp = Timestamp.from(windowStartInclusive);
        Timestamp windowEndTimestamp = Timestamp.from(now);

        long messageCount = 0L;
        boolean hasOneClick = false;
        boolean hasMailto = false;
        try {
            Map<String, Object> aggregateRow =
                    jdbcTemplate.queryForMap(
                            PER_SENDER_AGGREGATE_SQL,
                            tenantId,
                            normalizedSenderEmail,
                            windowStartTimestamp,
                            windowEndTimestamp);
            Object messageCountValue = aggregateRow.get("message_count");
            if (messageCountValue instanceof Number messageCountNumber) {
                messageCount = messageCountNumber.longValue();
            }
            hasOneClick = Boolean.TRUE.equals(aggregateRow.get("has_one_click"));
            hasMailto = Boolean.TRUE.equals(aggregateRow.get("has_mailto"));
        } catch (EmptyResultDataAccessException emptyAggregateRow) {
            // No observed rows for this sender — leave counts at zero and method NONE.
        }

        UnsubscribeMethod unsubscribeMethod;
        if (hasOneClick) {
            unsubscribeMethod = UnsubscribeMethod.ONE_CLICK;
        } else if (hasMailto) {
            unsubscribeMethod = UnsubscribeMethod.MAILTO;
        } else {
            unsubscribeMethod = UnsubscribeMethod.NONE;
        }

        return new PerSenderPreview(
                normalizedSenderEmail, senderDomain, unsubscribeMethod, messageCount, suppressed);
    }

    private static PerSenderPreview fromWorkingSet(SenderWorkingSet senderWorkingSet) {
        return new PerSenderPreview(
                senderWorkingSet.senderEmail(),
                senderWorkingSet.senderDomain(),
                senderWorkingSet.unsubscribeMethod(),
                senderWorkingSet.messageCount(),
                false);
    }

    private static String extractDomain(String senderEmail) {
        int atIndex = senderEmail.lastIndexOf('@');
        if (atIndex < 0 || atIndex == senderEmail.length() - 1) {
            return "unknown";
        }
        return senderEmail.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    /**
     * Aggregate preview result handed to controllers. {@code archivableHistoryCount} reflects only
     * senders that {@link PerSenderPreview#willArchive()} — suppressed and {@code NONE}-method
     * senders are excluded so the cap check matches the worker's actual archive behavior.
     */
    public record CampaignPreviewResult(
            List<PerSenderPreview> perSender, int archivableHistoryCount) {

        public CampaignPreviewResult {
            perSender =
                    List.copyOf(Objects.requireNonNull(perSender, "perSender must not be null"));
            if (archivableHistoryCount < 0) {
                throw new IllegalArgumentException(
                        "archivableHistoryCount must be >= 0, was " + archivableHistoryCount);
            }
        }
    }

    /**
     * Per-sender preview row. {@link #riskBadge()} surfaces the UI badge string ({@code "SAFE"},
     * {@code "NO_HEADER_DISABLED"}, {@code "SUPPRESSED_BLOCKED"}) — the frontend localizes the
     * label. {@link #willArchive()} is the single source of truth for whether the executor will
     * include this sender.
     */
    public record PerSenderPreview(
            String senderEmail,
            String senderDomain,
            UnsubscribeMethod unsubscribeMethod,
            long messageCount,
            boolean suppressed) {

        public PerSenderPreview {
            Objects.requireNonNull(senderEmail, "senderEmail must not be null");
            Objects.requireNonNull(senderDomain, "senderDomain must not be null");
            Objects.requireNonNull(unsubscribeMethod, "unsubscribeMethod must not be null");
            if (messageCount < 0L) {
                throw new IllegalArgumentException(
                        "messageCount must be >= 0, was " + messageCount);
            }
        }

        public String riskBadge() {
            if (suppressed) {
                return "SUPPRESSED_BLOCKED";
            }
            if (unsubscribeMethod == UnsubscribeMethod.NONE) {
                return "NO_HEADER_DISABLED";
            }
            return "SAFE";
        }

        public boolean willArchive() {
            return !suppressed && unsubscribeMethod != UnsubscribeMethod.NONE;
        }
    }
}
