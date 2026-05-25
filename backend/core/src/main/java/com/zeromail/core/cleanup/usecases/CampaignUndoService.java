package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeCampaignPolicy;
import com.zeromail.core.cleanup.exception.CampaignNotFoundException;
import com.zeromail.core.cleanup.exception.UndoWindowExpiredException;
import com.zeromail.core.triage.usecases.TriageGmailWriter;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
 * UNS-07 — Reverse a previously-applied campaign: add INBOX back to every archived message and
 * remove the {@code Zero Mail/Unsubscribed} label so the messages re-appear in the user's inbox
 * exactly as they were before the campaign ran.
 *
 * <p>Window check (UNS-07b): {@link UnsubscribeCampaignPolicy#UNDO_WINDOW} (30 days) starts from
 * {@code unsubscribe_campaign.applied_at}. Past the window the only path to restore archived
 * messages is per-message manual action in Gmail — surfaced to the caller via {@link
 * UndoWindowExpiredException}.
 *
 * <p>Undo lookup path (H-3 Path A): every cleanup-driven {@code triage_audit} row carries {@code
 * source = 'CLEANUP_CAMPAIGN'} (changelog 046) and {@code external_ref} equal to the campaign id.
 * The undo query filters on both so it never touches rule-driven {@code source='TRIAGE'} audit rows
 * even when they target the same sender — the false-positive risk from earlier drafts is
 * eliminated.
 *
 * <p>Gmail write idempotency: {@link TriageGmailWriter#restoreToInbox} and {@link
 * TriageGmailWriter#removeLabel} both invoke {@code users.messages.modify}, which Google documents
 * as idempotent — re-running undo on already-restored messages is safe. Label lookup tolerates a
 * user who manually deleted the {@code Zero Mail/Unsubscribed} label in Gmail between apply and
 * undo: {@link TriageGmailWriter#lookupLabelId} returns {@link Optional#empty()} and we skip the
 * label-remove step (the message is still restored to INBOX).
 *
 * <p>Privacy invariant (UNS-09): per-message log line records the Gmail message id (stable opaque
 * identifier, not PII) and counts. The sender email is never written.
 */
@Service
@Transactional
@SuppressWarnings("SqlResolve")
public class CampaignUndoService {

    private static final Logger log = LoggerFactory.getLogger(CampaignUndoService.class);

    /**
     * H-3 Path A — Precise per-campaign archive lookup. {@code source = 'CLEANUP_CAMPAIGN'} keeps
     * rule-driven {@code source='TRIAGE'} audit rows for the same sender out of the result set.
     * {@code reverted_at IS NULL} skips rows that a prior undo invocation already restored.
     */
    private static final String UNDO_AUDIT_LOOKUP_SQL =
            "SELECT gmail_message_id "
                    + "FROM triage_audit "
                    + "WHERE tenant_id = ? "
                    + "  AND source = 'CLEANUP_CAMPAIGN' "
                    + "  AND external_ref = ? "
                    + "  AND reverted_at IS NULL";

    private static final String CAMPAIGN_LOOKUP_SQL =
            "SELECT id, applied_at, reverted_at, status "
                    + "FROM unsubscribe_campaign "
                    + "WHERE id = ? AND tenant_id = ?";

    private static final String MARK_CAMPAIGN_REVERTED_SQL =
            "UPDATE unsubscribe_campaign "
                    + "SET reverted_at = ?, "
                    + "    updated_at = NOW() "
                    + "WHERE id = ? AND tenant_id = ?";

    /**
     * H-3 Path A — undo marker on audit rows. The {@code source = 'CLEANUP_CAMPAIGN'} predicate
     * makes the statement a no-op against rule-driven audit rows even if the campaign id ever
     * collides with an unrelated audit external_ref (which it cannot, but defensive).
     */
    private static final String MARK_AUDIT_ROWS_REVERTED_SQL =
            "UPDATE triage_audit "
                    + "SET reverted_at = ?, "
                    + "    updated_at = NOW() "
                    + "WHERE tenant_id = ? "
                    + "  AND source = 'CLEANUP_CAMPAIGN' "
                    + "  AND external_ref = ? "
                    + "  AND reverted_at IS NULL";

    private final JdbcTemplate jdbcTemplate;
    private final TriageGmailWriter triageGmailWriter;
    private final Clock clock;

    public CampaignUndoService(
            JdbcTemplate jdbcTemplate, TriageGmailWriter triageGmailWriter, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.triageGmailWriter =
                Objects.requireNonNull(triageGmailWriter, "triageGmailWriter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public CampaignUndoResult undo(UUID tenantId, UUID campaignId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        Map<String, Object> campaignRow;
        try {
            campaignRow = jdbcTemplate.queryForMap(CAMPAIGN_LOOKUP_SQL, campaignId, tenantId);
        } catch (EmptyResultDataAccessException missingCampaign) {
            throw new CampaignNotFoundException(campaignId);
        }

        Object appliedAtValue = campaignRow.get("applied_at");
        if (!(appliedAtValue instanceof Timestamp appliedAtTimestamp)) {
            throw new IllegalStateException(
                    "Campaign has not been applied yet — undo is only valid post-COMPLETED");
        }
        Instant appliedAt = appliedAtTimestamp.toInstant();

        Object revertedAtValue = campaignRow.get("reverted_at");
        if (revertedAtValue instanceof Timestamp) {
            throw new IllegalStateException(
                    "Campaign already reverted — undo is a one-shot operation per UNS-07");
        }

        Instant now = clock.instant();
        Instant undoableUntil = UnsubscribeCampaignPolicy.undoableUntil(appliedAt);
        if (now.isAfter(undoableUntil)) {
            throw new UndoWindowExpiredException(
                    campaignId, appliedAt, UnsubscribeCampaignPolicy.UNDO_WINDOW);
        }

        List<String> archivedGmailMessageIds =
                jdbcTemplate.queryForList(
                        UNDO_AUDIT_LOOKUP_SQL, String.class, tenantId, campaignId.toString());

        int restoredCount = 0;
        Optional<String> unsubscribedLabelIdOptional;
        try {
            unsubscribedLabelIdOptional =
                    triageGmailWriter.lookupLabelId(
                            tenantId, UnsubscribeCampaignPolicy.UNSUBSCRIBED_LABEL_NAME);
        } catch (IOException labelLookupFailure) {
            throw new IllegalStateException(
                    "Unable to look up Gmail label '"
                            + UnsubscribeCampaignPolicy.UNSUBSCRIBED_LABEL_NAME
                            + "' for undo",
                    labelLookupFailure);
        }

        for (String gmailMessageId : archivedGmailMessageIds) {
            try {
                triageGmailWriter.restoreToInbox(tenantId, gmailMessageId);
                if (unsubscribedLabelIdOptional.isPresent()) {
                    triageGmailWriter.removeLabel(
                            tenantId, gmailMessageId, unsubscribedLabelIdOptional.get());
                }
                restoredCount++;
            } catch (IOException gmailWriteFailure) {
                // Partial undo is the documented outcome when Gmail rate-limits us mid-batch.
                // The audit row keeps reverted_at NULL so a subsequent undo retry can restore
                // the remaining messages without double-restoring the ones we already touched.
                log.warn(
                        "event=cleanup_undo_failed tenantId={} campaignId={} restoredCount={}",
                        tenantId,
                        campaignId,
                        restoredCount);
                throw new IllegalStateException(
                        "Gmail write failed during cleanup undo", gmailWriteFailure);
            }
        }

        Timestamp revertedAtTimestamp = Timestamp.from(now);
        jdbcTemplate.update(
                MARK_AUDIT_ROWS_REVERTED_SQL, revertedAtTimestamp, tenantId, campaignId.toString());
        jdbcTemplate.update(MARK_CAMPAIGN_REVERTED_SQL, revertedAtTimestamp, campaignId, tenantId);

        log.info(
                "event=cleanup_campaign_undone tenantId={} campaignId={} restoredCount={}",
                tenantId,
                campaignId,
                restoredCount);

        return new CampaignUndoResult(campaignId, restoredCount);
    }

    /** Returned to the controller — identifiers + counts only, no per-sender PII. */
    public record CampaignUndoResult(UUID campaignId, int restoredCount) {

        public CampaignUndoResult {
            Objects.requireNonNull(campaignId, "campaignId must not be null");
            if (restoredCount < 0) {
                throw new IllegalArgumentException(
                        "restoredCount must be >= 0, was " + restoredCount);
            }
        }
    }
}
