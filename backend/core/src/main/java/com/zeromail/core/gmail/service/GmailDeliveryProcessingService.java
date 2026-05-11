package com.zeromail.core.gmail.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.event.MailMessageObserved;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.MailMessageObservedRepository;
import com.zeromail.core.gmail.persistence.PubSubDeliveryEntity;
import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GmailDeliveryProcessingService {

    private static final Logger log = LoggerFactory.getLogger(GmailDeliveryProcessingService.class);

    private final PubSubDeliveryRepository deliveryRepository;
    private final MailMessageObservedRepository observedRepository;
    private final GmailConnectionService connectionService;
    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public GmailDeliveryProcessingService(
            PubSubDeliveryRepository deliveryRepository,
            MailMessageObservedRepository observedRepository,
            GmailConnectionService connectionService,
            GmailConnectionRepository connectionRepository,
            GmailApiClientFactory gmailApiClientFactory,
            RefreshTokenCipher refreshTokenCipher,
            ApplicationEventPublisher applicationEventPublisher) {
        this.deliveryRepository = deliveryRepository;
        this.observedRepository = observedRepository;
        this.connectionService = connectionService;
        this.connectionRepository = connectionRepository;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.refreshTokenCipher = refreshTokenCipher;
        this.applicationEventPublisher = applicationEventPublisher;
    }

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
                    new String(
                            refreshTokenCipher.decrypt(
                                    connection.getRefreshTokenEncrypted(), tenantId.toString()),
                            StandardCharsets.UTF_8);
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
                                .setLabelId("INBOX")
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
                handleRetryableFailure(delivery, tenantId);
            }
        } catch (InvalidGrantException invalidGrantException) {
            connectionService.markDisconnected(tenantId);
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn("event=gmail_oauth_revoked tenantId={}", tenantId);
        } catch (Exception processingException) {
            handleRetryableFailure(delivery, tenantId);
        }
    }

    private int observeInboxMessages(
            Gmail gmail, UUID tenantId, ListHistoryResponse historyResponse)
            throws java.io.IOException {
        int newObservations = 0;
        List<History> historyList = historyResponse.getHistory();
        if (historyList == null) {
            return newObservations;
        }

        for (History history : historyList) {
            if (history.getMessagesAdded() == null) {
                continue;
            }
            for (HistoryMessageAdded addedMessage : history.getMessagesAdded()) {
                Message historyMessage = addedMessage.getMessage();
                if (historyMessage == null || historyMessage.getId() == null) {
                    continue;
                }

                Message gmailMessage =
                        gmail.users()
                                .messages()
                                .get("me", historyMessage.getId())
                                .setFormat("metadata")
                                .setFields("id,threadId,labelIds,internalDate")
                                .execute();
                List<String> labelIds = gmailMessage.getLabelIds();
                if (labelIds == null || !labelIds.contains("INBOX")) {
                    continue;
                }

                int insertedCount =
                        observedRepository.insertObservedIfAbsent(
                                tenantId,
                                gmailMessage.getId(),
                                gmailMessage.getThreadId(),
                                history.getId().longValueExact(),
                                labelIds.toArray(new String[0]),
                                gmailMessage.getInternalDate());
                if (insertedCount == 1) {
                    newObservations++;
                    Instant observedAt = Instant.now();
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
            }
        }
        return newObservations;
    }

    private void handleRetryableFailure(PubSubDeliveryEntity delivery, UUID tenantId) {
        int attempts = delivery.getAttempts();
        if (attempts >= 3) {
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn("event=gmail_delivery_dead tenantId={} attempts={}", tenantId, attempts);
        } else {
            deliveryRepository.releaseForRetry(delivery.getId(), Instant.now().plusSeconds(30));
            log.warn("event=gmail_delivery_retry tenantId={} attempt={}", tenantId, attempts);
        }
    }
}
