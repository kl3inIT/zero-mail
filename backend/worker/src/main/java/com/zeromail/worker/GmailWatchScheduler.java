package com.zeromail.worker;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.WatchRequest;
import com.google.api.services.gmail.model.WatchResponse;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.service.GmailApiClientFactory;
import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.gmail.service.InvalidGrantException;
import com.zeromail.core.tenant.TenantContext;

@Component
public class GmailWatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(GmailWatchScheduler.class);
    private static final int BATCH_SIZE = 50;
    private static final int FAILURE_THRESHOLD = 3;

    private final GmailConnectionRepository connectionRepository;
    private final GmailConnectionService connectionService;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;
    private final String topicName;

    public GmailWatchScheduler(GmailConnectionRepository connectionRepository,
                               GmailConnectionService connectionService,
                               GmailApiClientFactory gmailApiClientFactory,
                               RefreshTokenCipher refreshTokenCipher,
                               @Value("${google.pubsub.topic-name}") String topicName) {
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.refreshTokenCipher = refreshTokenCipher;
        this.topicName = topicName;
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        List<GmailConnectionEntity> batch = connectionRepository.findConnectionsNeedingWatchRenewal(BATCH_SIZE);
        for (GmailConnectionEntity conn : batch) {
            ScopedValue.where(TenantContext.TENANT, conn.getTenantId().toString())
                    .run(() -> processWatchRenewal(conn));
        }
    }

    private void processWatchRenewal(GmailConnectionEntity conn) {
        UUID tenantId = conn.getTenantId();
        try {
            String decryptedToken = new String(
                    refreshTokenCipher.decrypt(conn.getRefreshTokenEncrypted(), tenantId.toString()),
                    StandardCharsets.UTF_8);
            GmailApiClientFactory.TokenRefreshResult tokenResult =
                    gmailApiClientFactory.refreshAccessToken(decryptedToken);
            Gmail gmail = gmailApiClientFactory.buildGmailClient(tokenResult.accessToken().value());

            WatchRequest watchRequest = new WatchRequest()
                    .setLabelIds(List.of("INBOX"))
                    .setLabelFilterBehavior("include")
                    .setTopicName(topicName);

            WatchResponse response = gmail.users().watch("me", watchRequest).execute();
            long watchHistoryId = response.getHistoryId().longValue();
            Instant watchExpiresAt = Instant.ofEpochMilli(response.getExpiration());

            connectionService.recordWatchSuccess(tenantId, watchHistoryId, watchExpiresAt);
            log.info("event=gmail_watch_renewed tenantId={}", tenantId);
        } catch (InvalidGrantException e) {
            connectionService.markDisconnected(tenantId);
            log.warn("event=gmail_watch_invalid_grant tenantId={}", tenantId);
        } catch (Exception e) {
            connectionService.incrementWatchFailure(tenantId);
            int failures = conn.getWatchConsecutiveFailures() + 1;
            if (failures >= FAILURE_THRESHOLD) {
                connectionService.markWatchUnhealthy(tenantId);
                log.warn("event=gmail_watch_unhealthy_threshold tenantId={}", tenantId);
            } else {
                log.warn("event=gmail_watch_renewal_failed tenantId={} attempt={}", tenantId, failures);
            }
        }
    }
}
