package com.zeromail.core.inbox.usecases;

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
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Backfills the inbox projection for a tenant by fetching the most recent INBOX messages from
 * Gmail and UPSERTing each through {@link InboxProjectionWriteService}. Triggered from the worker
 * after the enqueuer schedules a job.
 *
 * <p>Phase A keeps the fetch shape close to the existing observed-message metadata fetch (no full
 * body, no inline image processing). When the read swap lands in Phase B, the projection will
 * already be populated by this service plus the Pub/Sub-driven incremental writes from wave 2.
 */
@Service
public class InboxBackfillService {

    private static final Logger log = LoggerFactory.getLogger(InboxBackfillService.class);

    private static final int BACKFILL_PAGE_SIZE = 100;
    private static final Duration GMAIL_REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final String INBOX_LABEL = "INBOX";

    /**
     * Metadata headers we pull during backfill. Adds Subject on top of the observed-message fetch
     * set so the projection's subject_ciphertext column gets populated; List-Unsubscribe stays
     * outside scope here (that field belongs to the observed/triage path).
     */
    private static final List<String> METADATA_HEADERS = List.of("From", "Subject");

    /**
     * Gmail Users.messages.list returns only id + threadId; we still need a per-message {@code .get}
     * for label_ids + internalDate + snippet + headers. The {@code fields} mask keeps the response
     * surface minimal.
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
     * Run a backfill for one tenant. Returns the number of projection rows written so the worker
     * can log it. Marks {@code sync_state} status appropriately on success / failure.
     */
    public int backfillTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        Optional<GmailConnectionEntity> connectionLookup =
                gmailConnectionRepository.findByTenantId(tenantId);
        if (connectionLookup.isEmpty()) {
            log.info(
                    "event=inbox_backfill_skipped tenantId={} reason=not_connected", tenantId);
            return 0;
        }
        GmailConnectionEntity connection = connectionLookup.orElseThrow();
        if (connection.getStatus() != GmailConnectionStatus.CONNECTED
                || connection.getRefreshTokenEncrypted() == null) {
            log.info(
                    "event=inbox_backfill_skipped tenantId={} reason=disconnected", tenantId);
            return 0;
        }

        try {
            Gmail gmail =
                    gmailApiClientFactory.buildClientForConnection(
                            connection, tenantId, GMAIL_REQUEST_TIMEOUT);
            ListMessagesResponse listResponse =
                    gmail.users()
                            .messages()
                            .list("me")
                            .setLabelIds(List.of(INBOX_LABEL))
                            .setMaxResults((long) BACKFILL_PAGE_SIZE)
                            .setFields("messages(id,threadId)")
                            .execute();
            List<Message> messageReferences =
                    listResponse.getMessages() == null ? List.of() : listResponse.getMessages();

            long highestHistoryId = 0L;
            int rowsWritten = 0;
            for (Message reference : messageReferences) {
                if (reference.getId() == null) {
                    continue;
                }
                Message gmailMessage = fetchMessageMetadata(gmail, reference.getId());
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
                    tenantId, highestHistoryId == 0L ? null : highestHistoryId, clock.instant());
            log.info(
                    "event=inbox_backfill_completed tenantId={} rowsWritten={}",
                    tenantId,
                    rowsWritten);
            return rowsWritten;
        } catch (InvalidGrantException invalidGrant) {
            syncStateRepository.recordBackfillFailure(
                    tenantId, clock.instant(), "INVALID_GRANT");
            log.warn("event=inbox_backfill_failed tenantId={} reason=invalid_grant", tenantId);
            return 0;
        } catch (IOException gmailFailure) {
            syncStateRepository.recordBackfillFailure(
                    tenantId, clock.instant(), "GMAIL_IO");
            log.warn(
                    "event=inbox_backfill_failed tenantId={} reason=gmail_io exception={}",
                    tenantId,
                    gmailFailure.getClass().getSimpleName());
            return 0;
        }
    }

    private Message fetchMessageMetadata(Gmail gmail, String gmailMessageId) {
        try {
            return gmail.users()
                    .messages()
                    .get("me", gmailMessageId)
                    .setFormat("metadata")
                    .setMetadataHeaders(METADATA_HEADERS)
                    .setFields(GET_FIELDS)
                    .execute();
        } catch (IOException fetchFailure) {
            log.warn(
                    "event=inbox_backfill_fetch_message_failed tenantId_message={} exception={}",
                    gmailMessageId,
                    fetchFailure.getClass().getSimpleName());
            return null;
        }
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
        if (displayPart.startsWith("\"") && displayPart.endsWith("\"") && displayPart.length() > 1) {
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
