package com.zeromail.core.gmail.usecases;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.event.MailOutboundObserved;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.MailMessageObservedRepository;
import com.zeromail.core.gmail.persistence.PubSubDeliveryEntity;
import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.shared.privacy.EmailAddressCanonicalizer;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final GmailConnectionService connectionService;
    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EmailAddressCanonicalizer emailAddressCanonicalizer;
    private final TransactionTemplate observationTransaction;

    public GmailDeliveryProcessingService(
            PubSubDeliveryRepository deliveryRepository,
            MailMessageObservedRepository observedRepository,
            GmailConnectionService connectionService,
            GmailConnectionRepository connectionRepository,
            GmailApiClientFactory gmailApiClientFactory,
            RefreshTokenCipher refreshTokenCipher,
            ApplicationEventPublisher applicationEventPublisher,
            EmailAddressCanonicalizer emailAddressCanonicalizer,
            PlatformTransactionManager transactionManager) {
        this.deliveryRepository = deliveryRepository;
        this.observedRepository = observedRepository;
        this.connectionService = connectionService;
        this.connectionRepository = connectionRepository;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.refreshTokenCipher = refreshTokenCipher;
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

            String decryptedRefreshToken =
                    decryptRefreshToken(connection.getRefreshTokenEncrypted(), tenantId);
            GmailApiClientFactory.TokenRefreshResult tokenResult =
                    gmailApiClientFactory.refreshAccessToken(decryptedRefreshToken);
            Gmail gmail = gmailApiClientFactory.buildGmailClient(tokenResult.accessToken().value());

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
        Message gmailMessage =
                gmail.users()
                        .messages()
                        .get("me", gmailMessageId)
                        .setFormat("metadata")
                        .setMetadataHeaders(List.of("From"))
                        .setFields("id,threadId,labelIds,internalDate,payload/headers")
                        .execute();
        List<String> labelIds = gmailMessage.getLabelIds();
        if (labelIds == null || (!labelIds.contains("INBOX") && !labelIds.contains("SENT"))) {
            return 0;
        }
        String senderEmail = extractSanitizedSenderEmail(gmailMessage);
        return insertObservationAndPublishEvents(
                tenantId, history, gmailMessage, labelIds, senderEmail);
    }

    private record PendingFetch(History history, String gmailMessageId) {}

    private int insertObservationAndPublishEvents(
            UUID tenantId,
            History history,
            Message gmailMessage,
            List<String> labelIds,
            String senderEmail) {
        Integer insertedCount =
                observationTransaction.execute(
                        transactionStatus -> {
                            int newRowCount =
                                    observedRepository.insertObservedIfAbsent(
                                            tenantId,
                                            gmailMessage.getId(),
                                            gmailMessage.getThreadId(),
                                            history.getId().longValueExact(),
                                            labelIds.toArray(new String[0]),
                                            gmailMessage.getInternalDate(),
                                            senderEmail);
                            if (newRowCount == 1) {
                                publishObservedEvents(tenantId, gmailMessage, labelIds);
                            }
                            return newRowCount;
                        });
        return insertedCount == null ? 0 : insertedCount;
    }

    private void publishObservedEvents(UUID tenantId, Message gmailMessage, List<String> labelIds) {
        Instant observedAt = Instant.now();
        applicationEventPublisher.publishEvent(
                new MailMessageObserved(
                        tenantId, gmailMessage.getId(), gmailMessage.getThreadId(), observedAt));
        log.info(
                "event=mail_message_observed_published tenantId={} gmailMessageId={}",
                tenantId,
                gmailMessage.getId());
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
        if (gmailMessage.getPayload() == null || gmailMessage.getPayload().getHeaders() == null) {
            return null;
        }
        return gmailMessage.getPayload().getHeaders().stream()
                .filter(header -> "From".equalsIgnoreCase(header.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .flatMap(this::canonicalizeSenderEmail)
                .orElse(null);
    }

    private java.util.Optional<String> canonicalizeSenderEmail(String rawSenderEmail) {
        try {
            return java.util.Optional.of(emailAddressCanonicalizer.canonicalize(rawSenderEmail));
        } catch (IllegalArgumentException senderEmailParseFailure) {
            return java.util.Optional.empty();
        }
    }

    private String decryptRefreshToken(byte[] encryptedRefreshToken, UUID tenantId) {
        if (encryptedRefreshToken == null || encryptedRefreshToken.length == 0) {
            throw new NonRetryableGmailDeliveryException();
        }
        byte[] decryptedRefreshTokenBytes;
        try {
            decryptedRefreshTokenBytes =
                    refreshTokenCipher.decrypt(encryptedRefreshToken, tenantId.toString());
        } catch (IllegalArgumentException
                | IllegalStateException
                | NullPointerException tokenDecryptionFailure) {
            throw new NonRetryableGmailDeliveryException(tokenDecryptionFailure);
        }
        if (decryptedRefreshTokenBytes == null || decryptedRefreshTokenBytes.length == 0) {
            throw new NonRetryableGmailDeliveryException();
        }
        try {
            String decryptedRefreshToken =
                    new String(decryptedRefreshTokenBytes, StandardCharsets.UTF_8);
            if (decryptedRefreshToken.isBlank()) {
                throw new NonRetryableGmailDeliveryException();
            }
            return decryptedRefreshToken;
        } finally {
            Arrays.fill(decryptedRefreshTokenBytes, (byte) 0);
        }
    }

    private void handleRetryableFailure(
            PubSubDeliveryEntity delivery, UUID tenantId, Exception processingException) {
        int attempts = delivery.getAttempts();
        String failureType = failureClassName(processingException);
        if (attempts >= 3) {
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn(
                    "event=gmail_delivery_dead tenantId={} attempts={} failureType={}",
                    tenantId,
                    attempts,
                    failureType);
        } else {
            deliveryRepository.releaseForRetry(delivery.getId(), Instant.now().plusSeconds(30));
            log.warn(
                    "event=gmail_delivery_retry tenantId={} attempt={} failureType={}",
                    tenantId,
                    attempts,
                    failureType);
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

    private static final class NonRetryableGmailDeliveryException extends RuntimeException {

        private NonRetryableGmailDeliveryException() {
            super();
        }

        private NonRetryableGmailDeliveryException(Throwable cause) {
            super(cause);
        }
    }
}
