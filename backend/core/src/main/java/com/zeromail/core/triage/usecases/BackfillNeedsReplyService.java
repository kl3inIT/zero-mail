package com.zeromail.core.triage.usecases;

import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.gmail.usecases.RecentInboxReadService;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxMessage;
import com.zeromail.core.gmail.usecases.RecentInboxReadService.RecentInboxPage;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.tenant.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One-off, operator-triggered backfill that classifies reply status for the most recent inbox
 * threads — so the "Cần trả lời" inbox has data before any new mail arrives (live classification is
 * event-driven per incoming message and does not retroactively scan existing mail).
 *
 * <p>It enumerates recent INBOX messages (newest first), keeps the newest message per thread, and
 * replays each through {@link TriageOrchestratorService#classifyReplyStatusForBackfill} — the exact
 * live inbound classification (LLM needs-reply + persist), with no rule evaluation and no Gmail
 * writes. Idempotent: re-running re-classifies the same threads in place.
 *
 * <p>Best-effort by design: a thread the reader already replied to still has its inbound message in
 * INBOX, so backfill may classify it TO_REPLY rather than AWAITING (the outbound event that would
 * have moved it never replays). The reader can resolve such threads; the going-forward live path is
 * exact.
 */
@Service
public class BackfillNeedsReplyService {

    private static final Logger log = LoggerFactory.getLogger(BackfillNeedsReplyService.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 20;

    private final RecentInboxReadService recentInboxReadService;
    private final TriageOrchestratorService triageOrchestratorService;
    private final GmailConnectionService gmailConnectionService;

    public BackfillNeedsReplyService(
            RecentInboxReadService recentInboxReadService,
            TriageOrchestratorService triageOrchestratorService,
            GmailConnectionService gmailConnectionService) {
        this.recentInboxReadService =
                Objects.requireNonNull(
                        recentInboxReadService, "recentInboxReadService must not be null");
        this.triageOrchestratorService =
                Objects.requireNonNull(
                        triageOrchestratorService, "triageOrchestratorService must not be null");
        this.gmailConnectionService =
                Objects.requireNonNull(
                        gmailConnectionService, "gmailConnectionService must not be null");
    }

    public BackfillResult backfill(UUID tenantId, int requestedLimit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        int limit = Math.clamp(requestedLimit <= 0 ? DEFAULT_LIMIT : requestedLimit, 1, MAX_LIMIT);
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> backfillTenantBound(tenantId, limit));
    }

    private BackfillResult backfillTenantBound(UUID tenantId, int limit) {
        Optional<MailboxRef> mailboxRef = primaryMailboxRef(tenantId);
        if (mailboxRef.isEmpty()) {
            log.info(
                    "event=needs_reply_backfill_skipped tenantId={} reason=not_connected",
                    tenantId);
            return new BackfillResult(0, 0, 0);
        }
        UUID gmailConnectionId = mailboxRef.orElseThrow().gmailConnectionId();
        Map<String, MailMessageObserved> newestMessageByThread =
                newestInboxMessagePerThread(mailboxRef.orElseThrow(), limit);
        int classified = 0;
        int failed = 0;
        for (MailMessageObserved observedEvent : newestMessageByThread.values()) {
            try {
                triageOrchestratorService.classifyReplyStatusForBackfill(observedEvent);
                classified++;
            } catch (RuntimeException backfillThreadFailure) {
                failed++;
                log.warn(
                        "event=needs_reply_backfill_thread_failed tenantId={} gmailConnectionId={} reason={}",
                        tenantId,
                        gmailConnectionId,
                        backfillThreadFailure.getClass().getSimpleName());
            }
        }
        log.info(
                "event=needs_reply_backfill_completed tenantId={} gmailConnectionId={} threadsScanned={} threadsClassified={} threadsFailed={}",
                tenantId,
                gmailConnectionId,
                newestMessageByThread.size(),
                classified,
                failed);
        return new BackfillResult(newestMessageByThread.size(), classified, failed);
    }

    private Map<String, MailMessageObserved> newestInboxMessagePerThread(
            MailboxRef mailboxRef, int limit) {
        UUID tenantId = mailboxRef.tenantId();
        UUID gmailConnectionId = mailboxRef.gmailConnectionId();
        // Gmail returns INBOX messages newest-first; keep the first (newest) message seen per
        // thread.
        LinkedHashMap<String, MailMessageObserved> newestByThread = new LinkedHashMap<>();
        String cursor = null;
        do {
            RecentInboxPage page =
                    recentInboxReadService.fetchPage(
                            tenantId, cursor, RecentInboxReadService.DEFAULT_PAGE_SIZE);
            for (RecentInboxMessage message : page.messages()) {
                String gmailThreadId = message.gmailThreadId();
                String gmailMessageId = message.gmailMessageId();
                if (gmailThreadId == null
                        || gmailThreadId.isBlank()
                        || gmailMessageId == null
                        || gmailMessageId.isBlank()) {
                    continue;
                }
                Instant observedAt =
                        message.receivedAt() == null ? Instant.EPOCH : message.receivedAt();
                newestByThread.putIfAbsent(
                        gmailThreadId,
                        new MailMessageObserved(
                                tenantId,
                                gmailConnectionId,
                                gmailMessageId,
                                gmailThreadId,
                                observedAt));
                if (newestByThread.size() >= limit) {
                    return newestByThread;
                }
            }
            cursor = page.nextCursor();
        } while (cursor != null && newestByThread.size() < limit);
        return newestByThread;
    }

    private Optional<MailboxRef> primaryMailboxRef(UUID tenantId) {
        return gmailConnectionService.primaryMailboxRef(tenantId);
    }

    public record BackfillResult(int threadsScanned, int threadsClassified, int threadsFailed) {}
}
