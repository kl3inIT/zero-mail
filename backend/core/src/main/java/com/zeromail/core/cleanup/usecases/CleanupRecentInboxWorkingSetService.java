package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeMethod;
import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailPreviewMessage;
import com.zeromail.core.gmail.usecases.GmailPreviewReadService.GmailPreviewReadUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared recent-100 Gmail working set for cleanup unsubscribe flows.
 *
 * <p>The candidate list, preview dialog, and worker all need to reason over the same source when
 * Gmail live reads are available. Keeping that source here prevents the UI from showing a sender
 * from the Gmail preview while preview/execute later falls back to stale DB-only rows.
 *
 * <p>Privacy invariant: this service reads Gmail metadata only. It never requests body evidence,
 * never persists message content, and never logs sender emails, unsubscribe URLs, or mailto values.
 */
@Service
public class CleanupRecentInboxWorkingSetService {

    private static final Logger log =
            LoggerFactory.getLogger(CleanupRecentInboxWorkingSetService.class);
    private static final int RECENT_INBOX_CANDIDATE_SAMPLE_SIZE = 100;
    private static final Duration RECENT_INBOX_CANDIDATE_FETCH_BUDGET = Duration.ofSeconds(5);

    private final GmailPreviewReadService gmailPreviewReadService;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public CleanupRecentInboxWorkingSetService(
            GmailPreviewReadService gmailPreviewReadService,
            JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.gmailPreviewReadService =
                Objects.requireNonNull(
                        gmailPreviewReadService, "gmailPreviewReadService must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Return sender aggregates from the tenant's latest Gmail INBOX sample. An empty Optional means
     * Gmail could not be read and the caller should use its existing DB fallback path.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<WorkingSet> findRecentInboxWorkingSet(UUID tenantId, Duration window) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(window, "window must not be null");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be a positive Duration, was " + window);
        }

        Instant windowEndExclusive = clock.instant();
        Instant windowStartInclusive = windowEndExclusive.minus(window);
        List<GmailPreviewMessage> recentInboxMessages;
        try {
            recentInboxMessages =
                    gmailPreviewReadService.fetchRecentInboxMessages(
                            tenantId,
                            RECENT_INBOX_CANDIDATE_SAMPLE_SIZE,
                            false,
                            RECENT_INBOX_CANDIDATE_FETCH_BUDGET);
        } catch (GmailPreviewReadUnavailableException unavailableException) {
            log.info(
                    "event=cleanup_recent_inbox_unavailable tenantId={} reason={}",
                    tenantId,
                    unavailableException.reason());
            return Optional.empty();
        }

        Map<String, SenderAccumulator> accumulatorsBySenderEmail = new LinkedHashMap<>();
        for (GmailPreviewMessage previewMessage : recentInboxMessages) {
            if (previewMessage.internalDate().isBefore(windowStartInclusive)
                    || !previewMessage.internalDate().isBefore(windowEndExclusive)) {
                continue;
            }
            if (!previewMessage.listUnsubscribePresent()
                    || previewMessage.sanitizedSenderEmail().isBlank()) {
                continue;
            }
            SenderAccumulator senderAccumulator =
                    accumulatorsBySenderEmail.computeIfAbsent(
                            previewMessage.sanitizedSenderEmail(),
                            senderEmail ->
                                    new SenderAccumulator(
                                            senderEmail, previewMessage.sanitizedSenderDomain()));
            senderAccumulator.include(previewMessage);
        }

        ArrayList<SenderWorkingSet> senders = new ArrayList<>();
        for (SenderAccumulator senderAccumulator : accumulatorsBySenderEmail.values()) {
            if (!isSuppressed(
                    tenantId, senderAccumulator.senderEmail(), senderAccumulator.senderDomain())) {
                senders.add(senderAccumulator.toWorkingSet());
            }
        }
        senders.sort(
                Comparator.comparingLong(SenderWorkingSet::messageCount)
                        .reversed()
                        .thenComparing(SenderWorkingSet::senderEmail));
        log.info(
                "event=cleanup_recent_inbox_working_set tenantId={} window={} senderCount={} messageCount={}",
                tenantId,
                window,
                senders.size(),
                senders.stream().mapToLong(SenderWorkingSet::messageCount).sum());
        return Optional.of(new WorkingSet(senders));
    }

    private boolean isSuppressed(UUID tenantId, String senderEmail, String senderDomain) {
        Boolean suppressed =
                jdbcTemplate.queryForObject(
                        """
                                SELECT EXISTS (
                                    SELECT 1
                                    FROM sender_suppression
                                    WHERE tenant_id = ?
                                      AND (sender_email = ? OR sender_domain = ?)
                                )
                                """,
                        Boolean.class,
                        tenantId,
                        senderEmail,
                        senderDomain);
        return Boolean.TRUE.equals(suppressed);
    }

    public record WorkingSet(List<SenderWorkingSet> senders) {

        public WorkingSet {
            senders = List.copyOf(Objects.requireNonNull(senders, "senders must not be null"));
        }

        public Optional<SenderWorkingSet> findSender(String senderEmail) {
            if (senderEmail == null || senderEmail.isBlank()) {
                return Optional.empty();
            }
            return senders.stream()
                    .filter(senderWorkingSet -> senderWorkingSet.senderEmail().equals(senderEmail))
                    .findFirst();
        }

        public List<UnsubscribeCandidateProjection> toCandidateProjections(int limit) {
            return senders.stream()
                    .limit(Math.max(0, limit))
                    .map(SenderWorkingSet::toCandidateProjection)
                    .toList();
        }
    }

    public record SenderWorkingSet(
            String senderEmail,
            String senderDomain,
            long messageCount,
            Instant lastSeenAt,
            UnsubscribeMethod unsubscribeMethod,
            List<String> gmailMessageIds,
            String listUnsubscribeUrl,
            String listUnsubscribeMailto) {

        public SenderWorkingSet {
            Objects.requireNonNull(senderEmail, "senderEmail must not be null");
            senderDomain = Objects.requireNonNullElse(senderDomain, "");
            Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
            Objects.requireNonNull(unsubscribeMethod, "unsubscribeMethod must not be null");
            gmailMessageIds =
                    List.copyOf(
                            Objects.requireNonNull(
                                    gmailMessageIds, "gmailMessageIds must not be null"));
            if (messageCount < 0L) {
                throw new IllegalArgumentException(
                        "messageCount must be >= 0, was " + messageCount);
            }
        }

        public UnsubscribeCandidateProjection toCandidateProjection() {
            return new UnsubscribeCandidateProjection(
                    senderEmail, senderDomain, messageCount, lastSeenAt, unsubscribeMethod, false);
        }
    }

    private static final class SenderAccumulator {

        private final String senderEmail;
        private final String senderDomain;
        private final ArrayList<String> gmailMessageIds = new ArrayList<>();
        private long messageCount;
        private Instant lastSeenAt = Instant.EPOCH;
        private boolean oneClickPresent;
        private boolean mailtoPresent;
        private String listUnsubscribeUrl;
        private String listUnsubscribeMailto;

        private SenderAccumulator(String senderEmail, String senderDomain) {
            this.senderEmail = Objects.requireNonNull(senderEmail, "senderEmail must not be null");
            this.senderDomain = Objects.requireNonNullElse(senderDomain, "");
        }

        private void include(GmailPreviewMessage previewMessage) {
            messageCount++;
            gmailMessageIds.add(previewMessage.gmailMessageId());
            if (previewMessage.internalDate().isAfter(lastSeenAt)) {
                lastSeenAt = previewMessage.internalDate();
            }
            oneClickPresent = oneClickPresent || previewMessage.listUnsubscribeOneClick();
            mailtoPresent = mailtoPresent || previewMessage.listUnsubscribeMailto() != null;
            if (listUnsubscribeUrl == null && previewMessage.listUnsubscribeUrl() != null) {
                listUnsubscribeUrl = previewMessage.listUnsubscribeUrl();
            }
            if (listUnsubscribeMailto == null && previewMessage.listUnsubscribeMailto() != null) {
                listUnsubscribeMailto = previewMessage.listUnsubscribeMailto();
            }
        }

        private SenderWorkingSet toWorkingSet() {
            return new SenderWorkingSet(
                    senderEmail,
                    senderDomain,
                    messageCount,
                    lastSeenAt,
                    unsubscribeMethod(),
                    gmailMessageIds,
                    listUnsubscribeUrl,
                    listUnsubscribeMailto);
        }

        private UnsubscribeMethod unsubscribeMethod() {
            if (oneClickPresent) {
                return UnsubscribeMethod.ONE_CLICK;
            }
            if (mailtoPresent) {
                return UnsubscribeMethod.MAILTO;
            }
            return UnsubscribeMethod.NONE;
        }

        private String senderEmail() {
            return senderEmail;
        }

        private String senderDomain() {
            return senderDomain;
        }
    }
}
