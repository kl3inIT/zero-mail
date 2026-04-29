package com.zeromail.core.gmail.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.MailMessageObservedRepository;
import com.zeromail.core.gmail.persistence.PubSubDeliveryEntity;
import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;

@Service
@Transactional
public class GmailDeliveryProcessingService {

    private static final Logger log = LoggerFactory.getLogger(GmailDeliveryProcessingService.class);
    private static final long HISTORY_GAP_CAP = 500L;

    private final PubSubDeliveryRepository deliveryRepository;
    private final MailMessageObservedRepository observedRepository;
    private final GmailConnectionService connectionService;
    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;

    public GmailDeliveryProcessingService(PubSubDeliveryRepository deliveryRepository,
                                          MailMessageObservedRepository observedRepository,
                                          GmailConnectionService connectionService,
                                          GmailConnectionRepository connectionRepository,
                                          GmailApiClientFactory gmailApiClientFactory,
                                          RefreshTokenCipher refreshTokenCipher) {
        this.deliveryRepository = deliveryRepository;
        this.observedRepository = observedRepository;
        this.connectionService = connectionService;
        this.connectionRepository = connectionRepository;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.refreshTokenCipher = refreshTokenCipher;
    }

    public void processDelivery(PubSubDeliveryEntity delivery) {
        UUID tenantId = delivery.getTenantId();
        long webhookHistoryId = delivery.getHistoryId();

        try {
            GmailConnectionEntity conn = connectionRepository.findByTenantId(tenantId)
                    .orElseThrow(() -> new IllegalStateException("No connection for tenantId: " + tenantId));

            String decryptedToken = new String(
                    refreshTokenCipher.decrypt(conn.getRefreshTokenEncrypted(), tenantId.toString()),
                    StandardCharsets.UTF_8);
            GmailApiClientFactory.TokenRefreshResult tokenResult =
                    gmailApiClientFactory.refreshAccessToken(decryptedToken);
            Gmail gmail = gmailApiClientFactory.buildGmailClient(tokenResult.accessToken().value());

            long startHistoryId = conn.getLastSyncedHistoryId() != null
                    ? conn.getLastSyncedHistoryId()
                    : webhookHistoryId;
            if (webhookHistoryId - startHistoryId > HISTORY_GAP_CAP) {
                long skipped = webhookHistoryId - startHistoryId;
                startHistoryId = webhookHistoryId - HISTORY_GAP_CAP;
                log.warn("event=gmail_history_gap_truncated tenantId={} skipped={}", tenantId, skipped);
            }

            ListHistoryResponse historyResponse = gmail.users()
                    .history()
                    .list("me")
                    .setStartHistoryId(BigInteger.valueOf(startHistoryId))
                    .setHistoryTypes(List.of("messageAdded"))
                    .setLabelId("INBOX")
                    .setMaxResults(500L)
                    .execute();

            if (historyResponse.getNextPageToken() != null) {
                log.warn("event=gmail_history_pagination_dropped tenantId={}", tenantId);
            }

            int newObservations = observeInboxMessages(gmail, tenantId, historyResponse);

            connectionRepository.updateLastSyncedHistoryIdMonotonic(tenantId, webhookHistoryId);
            deliveryRepository.updateStatus(delivery.getId(), "PROCESSED");
            log.info("event=gmail_history_processed tenantId={} batch_size=1 new_observations={}",
                    tenantId, newObservations);
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                connectionService.markHistoryLost(tenantId, webhookHistoryId);
                deliveryRepository.updateStatus(delivery.getId(), "PROCESSED");
                log.warn("event=gmail_history_lost tenantId={} expired_history_id={} new_pointer={}",
                        tenantId, delivery.getHistoryId(), webhookHistoryId);
            } else {
                handleRetryableFailure(delivery, tenantId);
            }
        } catch (InvalidGrantException e) {
            connectionService.markDisconnected(tenantId);
            deliveryRepository.updateStatus(delivery.getId(), "DEAD");
            log.warn("event=gmail_oauth_revoked tenantId={}", tenantId);
        } catch (Exception e) {
            handleRetryableFailure(delivery, tenantId);
        }
    }

    private int observeInboxMessages(Gmail gmail, UUID tenantId, ListHistoryResponse historyResponse)
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
            for (HistoryMessageAdded added : history.getMessagesAdded()) {
                Message historyMessage = added.getMessage();
                if (historyMessage == null || historyMessage.getId() == null) {
                    continue;
                }

                Message msg = gmail.users()
                        .messages()
                        .get("me", historyMessage.getId())
                        .setFormat("metadata")
                        .setFields("id,threadId,labelIds,internalDate")
                        .execute();
                List<String> labelIds = msg.getLabelIds();
                if (labelIds == null || !labelIds.contains("INBOX")) {
                    continue;
                }

                int inserted = observedRepository.insertObservedIfAbsent(
                        tenantId,
                        msg.getId(),
                        msg.getThreadId(),
                        history.getId().longValue(),
                        labelIds.toArray(new String[0]),
                        msg.getInternalDate());
                if (inserted == 1) {
                    newObservations++;
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
