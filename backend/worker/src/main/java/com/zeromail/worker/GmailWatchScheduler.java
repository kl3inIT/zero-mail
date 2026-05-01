package com.zeromail.worker;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.zeromail.worker.config.ZeroMailWorkerProperties;

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
                               ZeroMailWorkerProperties properties) {
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.refreshTokenCipher = refreshTokenCipher;
        this.topicName = properties.gmail().pubsub().topicName();
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        List<GmailConnectionEntity> connectionsNeedingRenewal =
                connectionRepository.findConnectionsNeedingWatchRenewal(BATCH_SIZE);
        for (GmailConnectionEntity connection : connectionsNeedingRenewal) {
            ScopedValue.where(TenantContext.TENANT, connection.getTenantId().toString())
                    .run(() -> processWatchRenewal(connection));
        }
    }

    private void processWatchRenewal(GmailConnectionEntity connection) {
        UUID tenantId = connection.getTenantId();
        try {
            String decryptedRefreshToken = new String(
                    refreshTokenCipher.decrypt(connection.getRefreshTokenEncrypted(), tenantId.toString()),
                    StandardCharsets.UTF_8);
            GmailApiClientFactory.TokenRefreshResult tokenResult =
                    gmailApiClientFactory.refreshAccessToken(decryptedRefreshToken);
            Gmail gmail = gmailApiClientFactory.buildGmailClient(tokenResult.accessToken().value());

            WatchRequest watchRequest = new WatchRequest()
                    .setLabelIds(List.of("INBOX"))
                    .setLabelFilterBehavior("include")
                    .setTopicName(topicName);

            WatchResponse response = gmail.users().watch("me", watchRequest).execute();
            long watchHistoryId = response.getHistoryId().longValueExact();
            Instant watchExpiresAt = Instant.ofEpochMilli(response.getExpiration());

            connectionService.recordWatchSuccess(tenantId, watchHistoryId, watchExpiresAt);
            log.info("event=gmail_watch_renewed tenantId={}", tenantId);
        } catch (InvalidGrantException invalidGrantException) {
            connectionService.markDisconnected(tenantId);
            log.warn("event=gmail_watch_invalid_grant tenantId={}", tenantId);
        } catch (Exception watchRenewalException) {
            connectionService.incrementWatchFailure(tenantId);
            int failures = connection.getWatchConsecutiveFailures() + 1;
            if (failures >= FAILURE_THRESHOLD) {
                connectionService.markWatchUnhealthy(tenantId);
                log.warn("event=gmail_watch_unhealthy_threshold tenantId={}", tenantId);
            } else {
                log.warn("event=gmail_watch_renewal_failed tenantId={} attempt={}", tenantId, failures);
            }
        }
    }
}
