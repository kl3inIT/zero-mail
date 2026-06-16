package com.zeromail.core.gmail.usecases;

import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GmailMessageHeaders;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.inbox.persistence.GmailInboxSyncStateRepository;
import com.zeromail.core.inbox.usecases.InboxProjectionUpsertCommand;
import com.zeromail.core.inbox.usecases.InboxProjectionWriteService;
import com.zeromail.core.mailbox.MailboxRef;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Backfills the inbox projection for a tenant by fetching the most recent INBOX messages from Gmail
 * and UPSERTing each through {@link InboxProjectionWriteService}. Triggered from the worker after
 * the enqueuer schedules a job.
 *
 * <p>Phase A keeps the fetch shape close to the existing observed-message metadata fetch (no full
 * body, no inline image processing). When the read swap lands in Phase B, the projection will
 * already be populated by this service plus the Pub/Sub-driven incremental writes from wave 2.
 */
@Service
public class InboxBackfillService {

    private static final Logger log = LoggerFactory.getLogger(InboxBackfillService.class);

    private static final int BACKFILL_PAGE_SIZE = 100;

    /**
     * Upper bound on how many recent INBOX messages a single backfill run pulls into the
     * projection. Gmail returns newest-first, so this is a recent-window cap (à la Inbox Zero's
     * ~1000 lazy cap / Zero's recent-first index). It bounds first-connect backfill time and Gmail
     * quota: with {@link #BATCH_CHUNK_SIZE} = 50 this is at most {@code 500 / 50 = 10} metadata
     * batch round-trips. Older mail beyond this window is reached via the live-Gmail read fallback
     * or future deep-backfill phases, never a blocking full-mailbox sync.
     */
    private static final int BACKFILL_MAX_MESSAGES = 500;

    // Gmail allows up to 100 sub-requests per batch; chunk well under that for headroom and to
    // bound per-batch latency. Each chunk is one HTTP round-trip instead of one per message.
    private static final int BATCH_CHUNK_SIZE = 50;
    private static final Duration GMAIL_REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final String INBOX_LABEL = "INBOX";

    /**
     * Metadata headers we pull during backfill. Adds Subject on top of the observed-message fetch
     * set so the projection's subject_ciphertext column gets populated; List-Unsubscribe stays
     * outside scope here (that field belongs to the observed/triage path).
     */
    private static final List<String> METADATA_HEADERS = List.of("From", "Subject");

    /**
     * Gmail Users.messages.list returns only id + threadId; we still need a per-message {@code
     * .get} for label_ids + internalDate + snippet + headers. The {@code fields} mask keeps the
     * response surface minimal.
     */
    private static final String GET_FIELDS =
            "id,threadId,labelIds,internalDate,snippet,payload/headers,payload/parts/filename";

    private final GmailConnectionRepository gmailConnectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final InboxProjectionWriteService inboxProjectionWriteService;
    private final GmailInboxSyncStateRepository syncStateRepository;
    private final java.time.Clock clock;

    public InboxBackfillService(
            GmailConnectionRepository gmailConnectionRepository,
            GmailApiClientFactory gmailApiClientFactory,
            InboxProjectionWriteService inboxProjectionWriteService,
            GmailInboxSyncStateRepository syncStateRepository,
            java.time.Clock clock) {
        this.gmailConnectionRepository =
                Objects.requireNonNull(
                        gmailConnectionRepository, "gmailConnectionRepository must not be null");
        this.gmailApiClientFactory =
                Objects.requireNonNull(
                        gmailApiClientFactory, "gmailApiClientFactory must not be null");
        this.inboxProjectionWriteService =
                Objects.requireNonNull(
                        inboxProjectionWriteService,
                        "inboxProjectionWriteService must not be null");
        this.syncStateRepository =
                Objects.requireNonNull(syncStateRepository, "syncStateRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Run a backfill for one mailbox. Returns the number of projection rows written so the worker
     * can log it. Marks {@code sync_state} status appropriately on success / failure.
     */
    public int backfillMailbox(MailboxRef mailboxRef) {
        Objects.requireNonNull(mailboxRef, "mailboxRef must not be null");
        UUID tenantId = mailboxRef.tenantId();
        UUID gmailConnectionId = mailboxRef.gmailConnectionId();

        Optional<GmailConnectionEntity> connectionLookup =
                gmailConnectionRepository.findByIdAndTenantId(gmailConnectionId, tenantId);
        if (connectionLookup.isEmpty()) {
            log.info(
                    "event=inbox_backfill_skipped tenantId={} gmailConnectionId={} reason=not_connected",
                    tenantId,
                    gmailConnectionId);
            return 0;
        }
        GmailConnectionEntity connection = connectionLookup.orElseThrow();
        if (connection.getStatus() != GmailConnectionStatus.CONNECTED
                || connection.getRefreshTokenEncrypted() == null) {
            log.info(
                    "event=inbox_backfill_skipped tenantId={} gmailConnectionId={}"
                            + " reason=disconnected",
                    tenantId,
                    gmailConnectionId);
            return 0;
        }

        try {
            Gmail gmail =
                    gmailApiClientFactory.buildClientForMailbox(mailboxRef, GMAIL_REQUEST_TIMEOUT);
            List<Message> messageReferences = listInboxMessageReferences(gmail);

            // Fetch all per-message metadata in batched HTTP round-trips. The previous code did one
            // sequential .get() per message (~500ms each × up to 100), which was the dominant cause
            // of a cold inbox taking 30s-1min to populate. Batching collapses that to a couple of
            // round-trips. Pattern mirrors GmailPreviewReadService#fetchMessagesWithBatch.
            Map<String, Message> messagesById = fetchMessagesWithBatch(gmail, messageReferences);

            long highestHistoryId = 0L;
            int rowsWritten = 0;
            for (Message reference : messageReferences) {
                if (reference.getId() == null) {
                    continue;
                }
                Message gmailMessage = messagesById.get(reference.getId());
                if (gmailMessage == null) {
                    continue;
                }
                List<String> labelIds =
                        gmailMessage.getLabelIds() == null
                                ? List.of()
                                : List.copyOf(gmailMessage.getLabelIds());
                String senderEmail = extractSenderEmail(gmailMessage);
                if (senderEmail == null) {
                    // Sender hash requires a non-blank email; skip orphan rows during backfill.
                    continue;
                }
                String senderDisplayName = extractSenderDisplayName(gmailMessage);
                String subject = extractSubject(gmailMessage);
                String snippet = gmailMessage.getSnippet();
                boolean hasAttachment = detectAttachment(gmailMessage);
                long internalDateMillis =
                        gmailMessage.getInternalDate() == null
                                ? clock.instant().toEpochMilli()
                                : gmailMessage.getInternalDate();
                long historyId =
                        gmailMessage.getHistoryId() == null
                                ? 0L
                                : gmailMessage.getHistoryId().longValueExact();
                highestHistoryId = Math.max(highestHistoryId, historyId);

                inboxProjectionWriteService.upsert(
                        new InboxProjectionUpsertCommand(
                                tenantId,
                                gmailConnectionId,
                                gmailMessage.getId(),
                                gmailMessage.getThreadId(),
                                senderEmail,
                                senderDisplayName,
                                subject,
                                snippet,
                                hasAttachment,
                                Instant.ofEpochMilli(internalDateMillis),
                                labelIds,
                                historyId));
                rowsWritten++;
            }

            syncStateRepository.recordBackfillSuccess(
                    tenantId,
                    gmailConnectionId,
                    highestHistoryId == 0L ? null : highestHistoryId,
                    clock.instant());
            log.info(
                    "event=inbox_backfill_completed tenantId={} gmailConnectionId={} rowsWritten={}",
                    tenantId,
                    gmailConnectionId,
                    rowsWritten);
            return rowsWritten;
        } catch (InvalidGrantException invalidGrant) {
            syncStateRepository.recordBackfillFailure(
                    tenantId, gmailConnectionId, clock.instant(), "INVALID_GRANT");
            log.warn(
                    "event=inbox_backfill_failed tenantId={} gmailConnectionId={}"
                            + " reason=invalid_grant",
                    tenantId,
                    gmailConnectionId);
            return 0;
        } catch (IOException gmailFailure) {
            syncStateRepository.recordBackfillFailure(
                    tenantId, gmailConnectionId, clock.instant(), "GMAIL_IO");
            log.warn(
                    "event=inbox_backfill_failed tenantId={} gmailConnectionId={} reason=gmail_io"
                            + " exception={}",
                    tenantId,
                    gmailConnectionId,
                    gmailFailure.getClass().getSimpleName());
            return 0;
        }
    }

    /**
     * Page through {@code Users.messages.list} (INBOX, newest-first) accumulating message
     * references up to {@link #BACKFILL_MAX_MESSAGES}. The list call returns only id + threadId
     * (cheap, no metadata), so this is a handful of small round-trips; the expensive part is the
     * subsequent batched per-message metadata {@code .get}. Bounding the total keeps first-connect
     * backfill time and Gmail quota predictable rather than walking the entire mailbox.
     */
    private List<Message> listInboxMessageReferences(Gmail gmail) throws IOException {
        ArrayList<Message> references = new ArrayList<>();
        String pageToken = null;
        do {
            ListMessagesResponse listResponse =
                    gmail.users()
                            .messages()
                            .list("me")
                            .setLabelIds(List.of(INBOX_LABEL))
                            .setMaxResults((long) BACKFILL_PAGE_SIZE)
                            .setPageToken(pageToken)
                            .setFields("messages(id,threadId),nextPageToken")
                            .execute();
            if (listResponse.getMessages() != null) {
                references.addAll(listResponse.getMessages());
            }
            pageToken = listResponse.getNextPageToken();
        } while (pageToken != null && references.size() < BACKFILL_MAX_MESSAGES);

        if (references.size() > BACKFILL_MAX_MESSAGES) {
            return List.copyOf(references.subList(0, BACKFILL_MAX_MESSAGES));
        }
        return List.copyOf(references);
    }

    /**
     * Fetch metadata for every referenced message via Gmail batch requests (chunks of {@value
     * #BATCH_CHUNK_SIZE}). Returns a map keyed by Gmail message id; messages that fail their
     * individual sub-request are simply omitted (the row is skipped this run). An {@link
     * IOException} from a batch execute propagates so {@link #backfillMailbox} records the failure.
     */
    private Map<String, Message> fetchMessagesWithBatch(
            Gmail gmail, List<Message> messageReferences) throws IOException {
        LinkedHashMap<String, Message> messagesById = new LinkedHashMap<>();
        JsonBatchCallback<Message> callback =
                new JsonBatchCallback<>() {
                    @Override
                    public void onSuccess(Message message, HttpHeaders responseHeaders) {
                        if (message != null && message.getId() != null) {
                            messagesById.put(message.getId(), message);
                        }
                    }

                    @Override
                    public void onFailure(
                            GoogleJsonError googleJsonError, HttpHeaders responseHeaders) {
                        // Individual message failures are non-fatal: that row is skipped this run
                        // and will be picked up by a later Pub/Sub delta or backfill.
                    }
                };

        for (int start = 0; start < messageReferences.size(); start += BATCH_CHUNK_SIZE) {
            List<Message> chunk =
                    messageReferences.subList(
                            start, Math.min(start + BATCH_CHUNK_SIZE, messageReferences.size()));
            BatchRequest batchRequest = gmail.batch();
            int queued = 0;
            for (Message reference : chunk) {
                if (reference.getId() == null) {
                    continue;
                }
                gmail.users()
                        .messages()
                        .get("me", reference.getId())
                        .setFormat("metadata")
                        .setMetadataHeaders(METADATA_HEADERS)
                        .setFields(GET_FIELDS)
                        .queue(batchRequest, callback);
                queued++;
            }
            if (queued > 0) {
                batchRequest.execute();
            }
        }
        return messagesById;
    }

    private static String extractSenderEmail(Message gmailMessage) {
        String fromHeader =
                GmailMessageHeaders.firstValue(gmailMessage.getPayload(), "From").orElse(null);
        if (fromHeader == null || fromHeader.isBlank()) {
            return null;
        }
        int openAngle = fromHeader.lastIndexOf('<');
        int closeAngle = fromHeader.lastIndexOf('>');
        if (openAngle >= 0 && closeAngle > openAngle) {
            return fromHeader.substring(openAngle + 1, closeAngle).trim();
        }
        return fromHeader.trim();
    }

    private static String extractSenderDisplayName(Message gmailMessage) {
        String fromHeader =
                GmailMessageHeaders.firstValue(gmailMessage.getPayload(), "From").orElse(null);
        if (fromHeader == null) {
            return null;
        }
        int openAngle = fromHeader.lastIndexOf('<');
        if (openAngle <= 0) {
            return null;
        }
        String displayPart = fromHeader.substring(0, openAngle).trim();
        if (displayPart.startsWith("\"")
                && displayPart.endsWith("\"")
                && displayPart.length() > 1) {
            displayPart = displayPart.substring(1, displayPart.length() - 1).trim();
        }
        return displayPart.isBlank() ? null : displayPart;
    }

    private static String extractSubject(Message gmailMessage) {
        return GmailMessageHeaders.firstValue(gmailMessage.getPayload(), "Subject").orElse(null);
    }

    private static boolean detectAttachment(Message gmailMessage) {
        if (gmailMessage.getPayload() == null || gmailMessage.getPayload().getParts() == null) {
            return false;
        }
        return gmailMessage.getPayload().getParts().stream()
                .anyMatch(part -> part.getFilename() != null && !part.getFilename().isBlank());
    }
}
