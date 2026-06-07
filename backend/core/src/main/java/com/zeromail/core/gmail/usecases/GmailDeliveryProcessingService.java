package com.zeromail.core.gmail.usecases;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.event.MailOutboundObserved;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GmailMessageHeaders;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.MailMessageObservedRepository;
import com.zeromail.core.gmail.persistence.PubSubDeliveryEntity;
import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.inbox.usecases.InboxProjectionUpsertCommand;
import com.zeromail.core.inbox.usecases.InboxProjectionWriteService;
import com.zeromail.core.shared.privacy.EmailAddressCanonicalizer;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GmailDeliveryProcessingService {

    private static final Logger log = LoggerFactory.getLogger(GmailDeliveryProcessingService.class);

    /**
     * Per-tenant Gmail messages.get fanout cap. Kept well under Gmail's 250 quota-units/sec
     * per-user budget (~1 message fetch ≈ 5 units), leaving headroom for other concurrent calls
     * within the same tenant.
     */
    private static final int GMAIL_FETCH_CONCURRENCY = 8;

    private final PubSubDeliveryRepository deliveryRepository;
    private final MailMessageObservedRepository observedRepository;
    private final InboxProjectionWriteService inboxProjectionWriteService;
    private final GmailConnectionService connectionService;
    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EmailAddressCanonicalizer emailAddressCanonicalizer;
    private final TransactionTemplate observationTransaction;

    public GmailDeliveryProcessingService(
            PubSubDeliveryRepository deliveryRepository,
            MailMessageObservedRepository observedRepository,
            InboxProjectionWriteService inboxProjectionWriteService,
            GmailConnectionService connectionService,
            GmailConnectionRepository connectionRepository,
            GmailApiClientFactory gmailApiClientFactory,
            ApplicationEventPublisher applicationEventPublisher,
            EmailAddressCanonicalizer emailAddressCanonicalizer,
            PlatformTransactionManager transactionManager) {
        this.deliveryRepository = deliveryRepository;
        this.observedRepository = observedRepository;
        this.inboxProjectionWriteService = inboxProjectionWriteService;
        this.connectionService = connectionService;
        this.connectionRepository = connectionRepository;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.applicationEventPublisher = applicationEventPublisher;
        this.emailAddressCanonicalizer = emailAddressCanonicalizer;
        this.observationTransaction = new TransactionTemplate(transactionManager);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processDelivery(PubSubDeliveryEntity delivery) {
        UUID tenantId = delivery.getTenantId();
        long webhookHistoryId = delivery.getHistoryId();

        try {
            GmailConnectionEntity connection =
                    connectionRepository
                            .findByTenantId(tenantId)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "No connection for tenantId: " + tenantId));

            byte[] encryptedRefreshToken = connection.getRefreshTokenEncrypted();
            if (encryptedRefreshToken == null || encryptedRefreshToken.length == 0) {
                throw new NonRetryableGmailDeliveryException();
            }
            Gmail gmail;
            try {
                gmail = gmailApiClientFactory.buildClientForConnection(connection, tenantId);
            } catch (IllegalArgumentException
                    | IllegalStateException
                    | NullPointerException tokenDecryptionFailure) {
                throw new NonRetryableGmailDeliveryException(tokenDecryptionFailure);
            }

            Long savedHistoryPointer = connection.getLastSyncedHistoryId();
            if (savedHistoryPointer == null) {
                connectionService.markHistoryLost(tenantId, webhookHistoryId);
                deliveryRepository.updateStatus(delivery.getId(), "PROCESSED");
                log.warn(
                        "event=gmail_history_missing_pointer tenantId={} new_pointer={}",
                        tenantId,
                        webhookHistoryId);
                return;
            }

            int newObservations = 0;
            String pageToken = null;
            do {
                var historyListRequest =
                        gmail.users()
                                .history()
                                .list("me")
                                .setStartHistoryId(BigInteger.valueOf(savedHistoryPointer))
                                .setHistoryTypes(List.of("messageAdded"))
                                .setMaxResults(500L);
                if (pageToken != null) {
                    historyListRequest.setPageToken(pageToken);
                }
                ListHistoryResponse historyResponse = historyListRequest.execute();
                newObservations += observeInboxMessages(gmail, tenantId, historyResponse);
                pageToken = historyResponse.getNextPageToken();
            } while (pageToken != null);

            connectionRepository.updateLastSyncedHistoryIdMonotonic(tenantId, webhookHistoryId);
            deliveryRepository.updateStatus(delivery.getId(), "PROCESSED");
            log.info(
                    "event=gmail_history_processed tenantId={} batch_size=1 new_observations={}",
                    tenantId,
                    newObservations);
        } catch (GoogleJsonResponseException googleResponseException) {
            if (googleResponseException.getStatusCode() == 404) {
                connectionService.markHistoryLost(tenantId, webhookHistoryId);
                deliveryRepository.updateStatus(delivery.getId(), "PROCESSED");
                log.warn(
                        "event=gmail_history_lost tenantId={} expired_history_id={} new_pointer={}",
                        tenantId,
                        delivery.getHistoryId(),
                        webhookHistoryId);
            } else {
                handleRetryableFailure(delivery, tenantId, googleResponseException);
            }
        } catch (InvalidGrantException invalidGrantException) {
            connectionService.markDisconnected(tenantId);
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn("event=gmail_oauth_revoked tenantId={}", tenantId);
        } catch (NonRetryableGmailDeliveryException nonRetryableDeliveryException) {
            handleNonRetryableFailure(delivery, tenantId, nonRetryableDeliveryException);
        } catch (Exception processingException) {
            handleRetryableFailure(delivery, tenantId, processingException);
        }
    }

    private int observeInboxMessages(
            Gmail gmail, UUID tenantId, ListHistoryResponse historyResponse)
            throws java.io.IOException, InterruptedException {
        List<History> historyList = historyResponse.getHistory();
        if (historyList == null) {
            return 0;
        }

        List<PendingFetch> pendingFetches = collectPendingFetches(historyList);
        if (pendingFetches.isEmpty()) {
            return 0;
        }

        Semaphore concurrencyLimiter = new Semaphore(GMAIL_FETCH_CONCURRENCY);
        List<StructuredTaskScope.Subtask<Integer>> subtasks =
                new ArrayList<>(pendingFetches.size());
        try (var scope = StructuredTaskScope.<Integer>open()) {
            for (PendingFetch pendingFetch : pendingFetches) {
                subtasks.add(
                        scope.fork(
                                () -> {
                                    concurrencyLimiter.acquire();
                                    try {
                                        return fetchAndObserve(
                                                gmail,
                                                tenantId,
                                                pendingFetch.history(),
                                                pendingFetch.gmailMessageId());
                                    } finally {
                                        concurrencyLimiter.release();
                                    }
                                }));
            }
            scope.join();
        }

        int newObservations = 0;
        for (StructuredTaskScope.Subtask<Integer> subtask : subtasks) {
            if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                newObservations += subtask.get();
                continue;
            }
            // Preserve original fail-fast semantics: any Gmail fetch failure aborts the
            // batch so the outer retry envelope reprocesses the entire history range.
            // insertObservedIfAbsent dedup ensures already-persisted observations are
            // not duplicated when Pub/Sub redelivers.
            Throwable failureCause = subtask.exception();
            if (failureCause instanceof java.io.IOException ioFailure) {
                throw ioFailure;
            }
            if (failureCause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new RuntimeException(failureCause);
        }
        return newObservations;
    }

    private List<PendingFetch> collectPendingFetches(List<History> historyList) {
        List<PendingFetch> pendingFetches = new ArrayList<>();
        for (History history : historyList) {
            if (history.getMessagesAdded() == null) {
                continue;
            }
            for (HistoryMessageAdded addedMessage : history.getMessagesAdded()) {
                Message historyMessage = addedMessage.getMessage();
                if (historyMessage == null || historyMessage.getId() == null) {
                    continue;
                }
                pendingFetches.add(new PendingFetch(history, historyMessage.getId()));
            }
        }
        return pendingFetches;
    }

    private int fetchAndObserve(Gmail gmail, UUID tenantId, History history, String gmailMessageId)
            throws java.io.IOException {
        Message gmailMessage;
        try {
            gmailMessage =
                    gmail.users()
                            .messages()
                            .get("me", gmailMessageId)
                            .setFormat("metadata")
                            .setMetadataHeaders(
                                    List.of("From", "List-Unsubscribe", "List-Unsubscribe-Post"))
                            .setFields("id,threadId,labelIds,internalDate,payload/headers")
                            .execute();
        } catch (GoogleJsonResponseException messageFetchFailure) {
            if (messageFetchFailure.getStatusCode() == 404) {
                // The message was deleted between the history record being written and this fetch,
                // so there is nothing to observe. Skip it instead of letting the 404 abort the
                // whole batch and dead-letter the delivery — that would strand the history pointer
                // (it only advances on a fully successful batch) and trap the tenant in a permanent
                // redelivery-death loop where every Pub/Sub push re-hits the same gone message, so
                // no inbound mail is ever observed and triage never runs.
                log.warn(
                        "event=gmail_message_fetch_skipped tenantId={} httpStatus=404"
                                + " googleReason=notFound",
                        tenantId);
                return 0;
            }
            throw messageFetchFailure;
        }
        List<String> labelIds = gmailMessage.getLabelIds();
        if (labelIds == null || (!labelIds.contains("INBOX") && !labelIds.contains("SENT"))) {
            return 0;
        }
        String senderEmail = extractSanitizedSenderEmail(gmailMessage);
        String senderName = extractSanitizedSenderName(gmailMessage);
        GmailPreviewReadService.ListUnsubscribeExtraction listUnsubscribeExtraction =
                extractListUnsubscribeFromMessage(gmailMessage);
        return insertObservationAndPublishEvents(
                tenantId,
                history,
                gmailMessage,
                labelIds,
                senderEmail,
                senderName,
                listUnsubscribeExtraction);
    }

    private static GmailPreviewReadService.ListUnsubscribeExtraction
            extractListUnsubscribeFromMessage(Message gmailMessage) {
        var payload = gmailMessage.getPayload();
        return GmailPreviewReadService.extractListUnsubscribe(
                GmailMessageHeaders.firstValue(payload, "List-Unsubscribe").orElse(null),
                GmailMessageHeaders.firstValue(payload, "List-Unsubscribe-Post").orElse(null));
    }

    private record PendingFetch(History history, String gmailMessageId) {}

    private int insertObservationAndPublishEvents(
            UUID tenantId,
            History history,
            Message gmailMessage,
            List<String> labelIds,
            String senderEmail,
            String senderName,
            GmailPreviewReadService.ListUnsubscribeExtraction listUnsubscribeExtraction) {
        long historyId = history.getId().longValueExact();
        Integer insertedCount =
                observationTransaction.execute(
                        transactionStatus -> {
                            int newRowCount =
                                    observedRepository.insertObservedIfAbsent(
                                            tenantId,
                                            gmailMessage.getId(),
                                            gmailMessage.getThreadId(),
                                            historyId,
                                            labelIds.toArray(new String[0]),
                                            gmailMessage.getInternalDate(),
                                            senderEmail,
                                            senderName,
                                            listUnsubscribeExtraction.url(),
                                            listUnsubscribeExtraction.mailto(),
                                            listUnsubscribeExtraction.oneClick());
                            if (newRowCount == 1) {
                                publishObservedEvents(tenantId, gmailMessage, labelIds);
                            }
                            return newRowCount;
                        });
        // Always refresh the inbox projection — the observed table is append-once
        // (insertObservedIfAbsent
        // returns 0 on redelivery) but the projection MUST track current label / state changes
        // (e.g. UNREAD → read, INBOX → archived) every time we see a history record for the
        // message.
        // The projection write runs in its own REQUIRES_NEW transaction so a failure here does not
        // roll back the observed insert above (which is part of the same delivery ack contract).
        if (senderEmail != null && !senderEmail.isBlank()) {
            inboxProjectionWriteService.upsert(
                    new InboxProjectionUpsertCommand(
                            tenantId,
                            gmailMessage.getId(),
                            gmailMessage.getThreadId(),
                            senderEmail,
                            null,
                            null,
                            null,
                            false,
                            Instant.ofEpochMilli(gmailMessage.getInternalDate()),
                            labelIds,
                            historyId));
        }
        return insertedCount == null ? 0 : insertedCount;
    }

    private void publishObservedEvents(UUID tenantId, Message gmailMessage, List<String> labelIds) {
        Instant observedAt = Instant.now();
        if (labelIds.contains("INBOX") && !labelIds.contains("SENT")) {
            applicationEventPublisher.publishEvent(
                    new MailMessageObserved(
                            tenantId,
                            gmailMessage.getId(),
                            gmailMessage.getThreadId(),
                            observedAt));
            log.info(
                    "event=mail_message_observed_published tenantId={} gmailMessageId={}",
                    tenantId,
                    gmailMessage.getId());
        }
        if (labelIds.contains("SENT")) {
            applicationEventPublisher.publishEvent(
                    new MailOutboundObserved(
                            tenantId,
                            gmailMessage.getThreadId(),
                            gmailMessage.getId(),
                            observedAt));
            log.info(
                    "event=mail_outbound_observed_published tenantId={} gmailMessageId={}",
                    tenantId,
                    gmailMessage.getId());
        }
    }

    private String extractSanitizedSenderEmail(Message gmailMessage) {
        return GmailMessageHeaders.firstValue(gmailMessage.getPayload(), "From")
                .flatMap(this::canonicalizeSenderEmail)
                .orElse(null);
    }

    private String extractSanitizedSenderName(Message gmailMessage) {
        return GmailMessageHeaders.firstValue(gmailMessage.getPayload(), "From")
                .flatMap(emailAddressCanonicalizer::extractDisplayName)
                .orElse(null);
    }

    private java.util.Optional<String> canonicalizeSenderEmail(String rawSenderEmail) {
        try {
            return java.util.Optional.of(emailAddressCanonicalizer.canonicalize(rawSenderEmail));
        } catch (IllegalArgumentException senderEmailParseFailure) {
            return java.util.Optional.empty();
        }
    }

    private void handleRetryableFailure(
            PubSubDeliveryEntity delivery, UUID tenantId, Exception processingException) {
        int attempts = delivery.getAttempts();
        String failureType = failureClassName(processingException);
        GoogleFailureDetail googleDetail = extractGoogleFailureDetail(processingException);
        if (attempts >= 3) {
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn(
                    "event=gmail_delivery_dead tenantId={} attempts={} failureType={} httpStatus={}"
                            + " googleReason={}",
                    tenantId,
                    attempts,
                    failureType,
                    googleDetail.statusCode(),
                    googleDetail.reason());
        } else {
            deliveryRepository.releaseForRetry(delivery.getId(), Instant.now().plusSeconds(30));
            log.warn(
                    "event=gmail_delivery_retry tenantId={} attempt={} failureType={} httpStatus={}"
                            + " googleReason={}",
                    tenantId,
                    attempts,
                    failureType,
                    googleDetail.statusCode(),
                    googleDetail.reason());
        }
    }

    private void handleNonRetryableFailure(
            PubSubDeliveryEntity delivery,
            UUID tenantId,
            NonRetryableGmailDeliveryException nonRetryableDeliveryException) {
        int attempts = delivery.getAttempts();
        deliveryRepository.updateStatus(delivery.getId(), "DEAD");
        log.warn(
                "event=gmail_delivery_dead tenantId={} attempts={} failureType={} retryable=false",
                tenantId,
                attempts,
                failureClassName(nonRetryableDeliveryException));
    }

    private static String failureClassName(Exception processingException) {
        Throwable cause = processingException.getCause();
        Throwable failureToLog = cause == null ? processingException : cause;
        return failureToLog.getClass().getSimpleName();
    }

    /**
     * Surfaces the Gmail API HTTP status and Google error reason (e.g. {@code rateLimitExceeded},
     * {@code userRateLimitExceeded}, {@code insufficientPermissions}) so a stuck per-tenant
     * ingestion loop is diagnosable from logs. The status code and reason are technical metadata,
     * not email content, so logging them honours the privacy convention; the Google error message
     * (which can echo request input) is deliberately not logged.
     */
    private record GoogleFailureDetail(int statusCode, String reason) {}

    private static GoogleFailureDetail extractGoogleFailureDetail(Throwable failure) {
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth < 5) {
            if (current instanceof GoogleJsonResponseException googleResponseException) {
                return new GoogleFailureDetail(
                        googleResponseException.getStatusCode(),
                        extractGoogleReason(googleResponseException));
            }
            current = current.getCause();
            depth++;
        }
        return new GoogleFailureDetail(-1, "none");
    }

    private static String extractGoogleReason(GoogleJsonResponseException googleResponseException) {
        try {
            var errorDetails = googleResponseException.getDetails();
            if (errorDetails != null
                    && errorDetails.getErrors() != null
                    && !errorDetails.getErrors().isEmpty()) {
                String firstReason = errorDetails.getErrors().get(0).getReason();
                if (firstReason != null && !firstReason.isBlank()) {
                    return firstReason;
                }
            }
        } catch (RuntimeException reasonExtractionFailure) {
            return "unparseable";
        }
        return "unknown";
    }

    private static final class NonRetryableGmailDeliveryException extends RuntimeException {

        private NonRetryableGmailDeliveryException() {
            super();
        }

        private NonRetryableGmailDeliveryException(Throwable cause) {
            super(cause);
        }
    }
}
