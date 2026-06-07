package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy;
import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side query for the bulk-unsubscribe candidate list (UNS-01).
 *
 * <p>The primary source is {@code cleanup_sender_projection}, a sender/day aggregate table shaped
 * for this screen. The service refreshes that table from durable observed metadata and, when the
 * table is still under-filled, from a bounded recent Gmail metadata working set. It deliberately
 * does not read {@code gmail_inbox_projection}: that table stores encrypted per-message display
 * metadata for the inbox list, so a cleanup sender list should not depend on decrypting hundreds of
 * message rows at request time.
 *
 * <p>Privacy invariant: this service never logs raw sender email values. Logs contain tenantId,
 * window, limits, counts, and source labels only.
 */
@Service
public class CandidateQueryService {

    private static final Logger log = LoggerFactory.getLogger(CandidateQueryService.class);

    private final CleanupSenderProjectionService cleanupSenderProjectionService;
    private final CleanupRecentInboxWorkingSetService cleanupRecentInboxWorkingSetService;
    private final Clock clock;

    public CandidateQueryService(
            CleanupSenderProjectionService cleanupSenderProjectionService,
            CleanupRecentInboxWorkingSetService cleanupRecentInboxWorkingSetService,
            Clock clock) {
        this.cleanupSenderProjectionService =
                Objects.requireNonNull(
                        cleanupSenderProjectionService,
                        "cleanupSenderProjectionService must not be null");
        this.cleanupRecentInboxWorkingSetService =
                Objects.requireNonNull(
                        cleanupRecentInboxWorkingSetService,
                        "cleanupRecentInboxWorkingSetService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Return up to {@code limit} candidate senders seen in {@code [now - window, now)} and NOT in
     * the tenant's suppression list. Projection-backed rows may have {@code NONE} when no
     * List-Unsubscribe provenance has been observed yet; the UI treats those as block/archive
     * candidates rather than direct unsubscribe candidates.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<UnsubscribeCandidateProjection> findCandidates(
            UUID tenantId, Duration window, int limit) {
        Objects.requireNonNull(window, "window must not be null");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be a positive Duration, was " + window);
        }
        Instant now = clock.instant();
        return findCandidates(tenantId, now.minus(window), now, limit);
    }

    /** Range-based overload powering the date-picker filter. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<UnsubscribeCandidateProjection> findCandidates(
            UUID tenantId, Instant fromInclusive, Instant toExclusive, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(fromInclusive, "fromInclusive must not be null");
        Objects.requireNonNull(toExclusive, "toExclusive must not be null");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("fromInclusive must be strictly before toExclusive");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0, was " + limit);
        }

        int effectiveLimit = Math.min(limit, UnsubscribeCampaignPolicy.MAX_CANDIDATE_SENDERS);
        Duration window = Duration.between(fromInclusive, toExclusive);

        int observedRefreshCount =
                cleanupSenderProjectionService.refreshFromObserved(
                        tenantId, fromInclusive, toExclusive);
        List<UnsubscribeCandidateProjection> candidates =
                cleanupSenderProjectionService.findCandidates(
                        tenantId, fromInclusive, toExclusive, effectiveLimit);
        if (candidates.size() >= effectiveLimit) {
            log.info(
                    "event=cleanup_candidates_queried tenantId={} window={} limit={} count={} source=cleanup_sender_projection observedRefreshCount={}",
                    tenantId,
                    window,
                    effectiveLimit,
                    candidates.size(),
                    observedRefreshCount);
            return candidates;
        }

        java.util.Optional<CleanupRecentInboxWorkingSetService.WorkingSet> recentInboxWorkingSet =
                cleanupRecentInboxWorkingSetService.findRecentInboxWorkingSet(tenantId, window);
        if (recentInboxWorkingSet.isPresent()) {
            CleanupRecentInboxWorkingSetService.WorkingSet workingSet =
                    recentInboxWorkingSet.orElseThrow();
            cleanupSenderProjectionService.upsertWorkingSet(tenantId, workingSet);
            List<UnsubscribeCandidateProjection> refreshedCandidates =
                    cleanupSenderProjectionService.findCandidates(
                            tenantId, fromInclusive, toExclusive, effectiveLimit);
            log.info(
                    "event=cleanup_candidates_queried tenantId={} window={} limit={} count={} source=cleanup_sender_projection_gmail_fill observedRefreshCount={} gmailCount={}",
                    tenantId,
                    window,
                    effectiveLimit,
                    refreshedCandidates.size(),
                    observedRefreshCount,
                    workingSet.senders().size());
            return refreshedCandidates;
        }

        log.info(
                "event=cleanup_candidates_queried tenantId={} window={} limit={} count={} source=cleanup_sender_projection observedRefreshCount={}",
                tenantId,
                window,
                effectiveLimit,
                candidates.size(),
                observedRefreshCount);
        return candidates;
    }
}
