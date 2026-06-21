package com.zeromail.worker.triage;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.inbox.persistence.GmailInboxProjectionRepository;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.worker.triage.CalendarPartParser.ParseResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 12 W4 (Task 3) worker-side listener that runs AFTER_COMMIT on every {@link
 * MailMessageObserved}, fetches the message body via the existing Gmail API path, walks the MIME
 * parts for a {@code text/calendar} body, hands it to {@link CalendarPartParser}, and UPDATEs the
 * existing {@code gmail_inbox_projection} row with the resulting classification + DTSTART.
 *
 * <p><b>CAL-TRIAGE-04 invariant (the load-bearing one for this class):</b> the classifier reads the
 * Gmail message via {@link GmailApiClientFactory}. It MUST NEVER reach into Calendar OAuth scopes
 * or a CalendarApiClientFactory — this is what makes the calendar-aware triage feature work without
 * a Calendar grant. Enforced by {@code CalendarMessageClassifierNoOAuthTest} via a {@code
 * verify(calendarApiClientFactory, never())} mock spy.
 *
 * <p><b>CONVENTIONS.md §6 wire:</b> {@link MailMessageObserved} is a {@code core} integration event
 * that crosses the API/worker process boundary, so this listener uses a plain {@link
 * TransactionalEventListener}, NOT {@code @ApplicationModuleListener} (which is the in-process
 * variant for listeners inside {@code backend/core}).
 *
 * <p><b>Pitfall 5 — propagation:</b> the AFTER_COMMIT phase fires AFTER the publisher's TX has
 * committed; a same-thread {@code @Transactional} would join nothing and silently no-op the UPDATE.
 * We build a {@link TransactionTemplate} with {@code PROPAGATION_REQUIRES_NEW} so the UPDATE runs
 * in its own short TX. Failure during the UPDATE silently rolls back this TX without affecting the
 * original Pub/Sub commit.
 *
 * <p><b>Privacy:</b> on every log line we emit only stable ids + the enum classification — never
 * SUMMARY, ATTENDEE, or the iCal body itself (CLAUDE.md ARCH-02 / T-12-10).
 */
@Component
public class CalendarMessageClassifier {

    private static final Logger log = LoggerFactory.getLogger(CalendarMessageClassifier.class);

    private static final String MIME_TEXT_CALENDAR = "text/calendar";
    private static final String ICS_EXTENSION = ".ics";
    private static final String VCALENDAR_MARKER = "BEGIN:VCALENDAR";

    private final GmailApiClientFactory gmailApiClientFactory;
    private final CalendarPartParser calendarPartParser;
    private final GmailInboxProjectionRepository gmailInboxProjectionRepository;
    private final TransactionTemplate classifierTransactionTemplate;

    public CalendarMessageClassifier(
            GmailApiClientFactory gmailApiClientFactory,
            CalendarPartParser calendarPartParser,
            GmailInboxProjectionRepository gmailInboxProjectionRepository,
            PlatformTransactionManager platformTransactionManager) {
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.calendarPartParser = calendarPartParser;
        this.gmailInboxProjectionRepository = gmailInboxProjectionRepository;
        TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.classifierTransactionTemplate = template;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMailMessageObserved(MailMessageObserved observedEvent) {
        try {
            TenantContext.runWith(observedEvent.tenantId(), () -> classifySafely(observedEvent));
        } catch (RuntimeException unexpectedFailure) {
            // Phase 12 W4 D-10: "does NOT block Pub/Sub ingestion latency" — the AFTER_COMMIT
            // already fired so there's nothing to roll back on the publisher side. Swallow any
            // surprise runtime exception so a single bad invite cannot poison the worker's event
            // pump. Log only the gmailMessageId / tenantId; never the parse cause text.
            log.warn(
                    "event=calendar_classify_unexpected_failure tenantId={} gmailMessageId={}",
                    observedEvent.tenantId(),
                    observedEvent.gmailMessageId());
        }
    }

    private void classifySafely(MailMessageObserved observedEvent) {
        MailboxRef mailboxRef =
                new MailboxRef(observedEvent.tenantId(), observedEvent.gmailConnectionId());

        Optional<String> icsBody = fetchCalendarPartBody(mailboxRef, observedEvent);
        if (icsBody.isEmpty()) {
            // Most messages are not calendar-related — silent skip is the common case.
            return;
        }

        Optional<ParseResult> parseResult = calendarPartParser.parse(icsBody.get());
        if (parseResult.isEmpty()) {
            // Parser already silent-failed (privacy + DoS guards); nothing to UPDATE.
            return;
        }

        ParseResult result = parseResult.orElseThrow();
        Optional<java.time.Instant> eventDt = result.eventDt();
        if (eventDt.isEmpty()) {
            // The pin window derives from event_dt; without it the row would be classified but
            // never pinned (and the entity invariant forbids the half-set case anyway). Skip.
            log.info(
                    "event=calendar_classified_no_dtstart_skipped tenantId={} gmailMessageId={} messageClass={}",
                    observedEvent.tenantId(),
                    observedEvent.gmailMessageId(),
                    result.messageClass());
            return;
        }

        Integer updatedRowCount =
                classifierTransactionTemplate.execute(
                        _ ->
                                gmailInboxProjectionRepository.updateCalendarClassification(
                                        observedEvent.tenantId(),
                                        observedEvent.gmailConnectionId(),
                                        observedEvent.gmailMessageId(),
                                        result.messageClass().id(),
                                        eventDt.orElseThrow()));
        if (updatedRowCount == null || updatedRowCount == 0) {
            // The projection has no row yet for this message (Pub/Sub ahead of the UPSERT path).
            // Treat as benign — the next UPSERT will land the row and a subsequent re-observation
            // would re-trigger classification.
            log.info(
                    "event=calendar_classified_projection_missing tenantId={} gmailMessageId={}",
                    observedEvent.tenantId(),
                    observedEvent.gmailMessageId());
            return;
        }
        log.info(
                "event=calendar_classified tenantId={} gmailMessageId={} messageClass={}",
                observedEvent.tenantId(),
                observedEvent.gmailMessageId(),
                result.messageClass());
    }

    private Optional<String> fetchCalendarPartBody(
            MailboxRef mailboxRef, MailMessageObserved observedEvent) {
        try {
            Gmail gmailClient = gmailApiClientFactory.buildClientForMailbox(mailboxRef);
            Message gmailMessage =
                    gmailClient
                            .users()
                            .messages()
                            .get("me", observedEvent.gmailMessageId())
                            .setFormat("FULL")
                            .execute();
            if (gmailMessage == null || gmailMessage.getPayload() == null) {
                return Optional.empty();
            }
            return findCalendarPartBody(gmailMessage.getPayload());
        } catch (IOException gmailFailure) {
            // Treat any Gmail fetch failure (transient network, 404 message-gone, mailbox
            // disconnected since the event was published) as "skip classification". The
            // existing triage pipeline already retries the upstream MailMessageObserved on its
            // own schedule; we don't double-retry here.
            log.info(
                    "event=calendar_classify_fetch_skipped tenantId={} gmailMessageId={} cause={}",
                    observedEvent.tenantId(),
                    observedEvent.gmailMessageId(),
                    gmailFailure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Inbox Zero–style triple-check (D-09, RESEARCH §Pitfall 3): walk the multipart MIME tree
     * looking for (a) a part with {@code mimeType == text/calendar}, or (b) a part whose filename
     * ends with {@code .ics}, or (c) — only as a fallback when no structured calendar part is found
     * — a top-level body containing the literal {@code BEGIN:VCALENDAR} marker.
     */
    static Optional<String> findCalendarPartBody(MessagePart messagePart) {
        if (messagePart == null) {
            return Optional.empty();
        }
        // Path (a) + (b): structured calendar part.
        Optional<String> structuredHit = findStructuredCalendarPart(messagePart);
        if (structuredHit.isPresent()) {
            return structuredHit;
        }
        // Path (c) fallback: top-level body carrying a VCALENDAR marker (some forwarded invites
        // strip the .ics part but inline the body into text/plain). Cheap substring check; the
        // ical4j parse step will fail-empty if the body isn't actually a calendar.
        return findVcalendarBodyMarker(messagePart);
    }

    private static Optional<String> findStructuredCalendarPart(MessagePart messagePart) {
        if (messagePart == null) {
            return Optional.empty();
        }
        String mimeType = nullSafeMimeType(messagePart.getMimeType());
        String filename = messagePart.getFilename();
        boolean isCalendarMime = MIME_TEXT_CALENDAR.equals(mimeType);
        boolean isIcsAttachment =
                filename != null && filename.toLowerCase(Locale.ROOT).endsWith(ICS_EXTENSION);
        if (isCalendarMime || isIcsAttachment) {
            Optional<String> decoded = decodePartBody(messagePart);
            if (decoded.isPresent()) {
                return decoded;
            }
        }
        List<MessagePart> children = messagePart.getParts();
        if (children == null) {
            return Optional.empty();
        }
        for (MessagePart child : children) {
            Optional<String> childHit = findStructuredCalendarPart(child);
            if (childHit.isPresent()) {
                return childHit;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findVcalendarBodyMarker(MessagePart messagePart) {
        if (messagePart == null) {
            return Optional.empty();
        }
        Optional<String> decoded = decodePartBody(messagePart);
        if (decoded.isPresent() && decoded.get().contains(VCALENDAR_MARKER)) {
            return decoded;
        }
        List<MessagePart> children = messagePart.getParts();
        if (children == null) {
            return Optional.empty();
        }
        for (MessagePart child : children) {
            Optional<String> childHit = findVcalendarBodyMarker(child);
            if (childHit.isPresent()) {
                return childHit;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> decodePartBody(MessagePart messagePart) {
        MessagePartBody body = messagePart.getBody();
        if (body == null || body.getData() == null || body.getData().isEmpty()) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(body.getData());
            // Pitfall 4 charset variability: Gmail returns text/* parts in their declared charset,
            // but the MIME headers are not promoted onto MessagePart in a typed way. Default to
            // UTF-8 — ical4j tolerates ASCII-superset bodies, which covers the vast majority of
            // real-world invites.
            return Optional.of(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException decodeFailure) {
            // Body wasn't valid base64-url; treat as "no calendar body here" and move on.
            return Optional.empty();
        }
    }

    private static String nullSafeMimeType(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        int semicolonIndex = mimeType.indexOf(';');
        String mediaType = semicolonIndex >= 0 ? mimeType.substring(0, semicolonIndex) : mimeType;
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }
}
