package com.zeromail.worker;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.WatchRequest;
import com.google.api.services.gmail.model.WatchResponse;
import com.zeromail.core.gmail.exception.InvalidGrantException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.worker.config.WorkerProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    public GmailWatchScheduler(
            GmailConnectionRepository connectionRepository,
            GmailConnectionService connectionService,
            GmailApiClientFactory gmailApiClientFactory,
            RefreshTokenCipher refreshTokenCipher,
            WorkerProperties properties) {
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
        UUID gmailConnectionId = connection.getId();
        MailboxRef mailboxRef = new MailboxRef(tenantId, gmailConnectionId);
        try {
            String decryptedRefreshToken =
                    new String(
                            refreshTokenCipher.decrypt(
                                    connection.getRefreshTokenEncrypted(), tenantId.toString()),
                            StandardCharsets.UTF_8);
            GmailApiClientFactory.TokenRefreshResult tokenResult =
                    gmailApiClientFactory.refreshAccessToken(decryptedRefreshToken);
            Gmail gmail = gmailApiClientFactory.buildGmailClient(tokenResult.accessToken().value());

            WatchRequest watchRequest =
                    new WatchRequest()
                            .setLabelIds(List.of("INBOX"))
                            .setLabelFilterBehavior("include")
                            .setTopicName(topicName);

            WatchResponse response = gmail.users().watch("me", watchRequest).execute();
            long watchHistoryId = response.getHistoryId().longValueExact();
            Instant watchExpiresAt = Instant.ofEpochMilli(response.getExpiration());

            connectionService.recordWatchSuccess(mailboxRef, watchHistoryId, watchExpiresAt);
            log.info(
                    "event=gmail_watch_renewed tenantId={} gmailConnectionId={}",
                    tenantId,
                    gmailConnectionId);
        } catch (InvalidGrantException invalidGrantException) {
            connectionService.markDisconnected(mailboxRef);
            log.warn(
                    "event=gmail_watch_invalid_grant tenantId={} gmailConnectionId={}",
                    tenantId,
                    gmailConnectionId);
        } catch (Exception watchRenewalException) {
            int failures = connectionService.incrementWatchFailure(mailboxRef);
            String failureType = watchFailureClassName(watchRenewalException);
            GoogleFailureDetail googleDetail = extractGoogleFailureDetail(watchRenewalException);
            if (failures >= FAILURE_THRESHOLD) {
                connectionService.markWatchUnhealthy(mailboxRef);
                log.warn(
                        "event=gmail_watch_unhealthy_threshold tenantId={} gmailConnectionId={}"
                                + " failures={} failureType={} httpStatus={} googleReason={}",
                        tenantId,
                        gmailConnectionId,
                        failures,
                        failureType,
                        googleDetail.statusCode(),
                        googleDetail.reason());
            } else {
                log.warn(
                        "event=gmail_watch_renewal_failed tenantId={} gmailConnectionId={} attempt={}"
                                + " failureType={} httpStatus={} googleReason={}",
                        tenantId,
                        gmailConnectionId,
                        failures,
                        failureType,
                        googleDetail.statusCode(),
                        googleDetail.reason());
            }
        }
    }

    private static String watchFailureClassName(Exception watchRenewalException) {
        Throwable cause = watchRenewalException.getCause();
        Throwable failureToLog = cause == null ? watchRenewalException : cause;
        return failureToLog.getClass().getSimpleName();
    }

    /**
     * Surfaces the Gmail API HTTP status and Google error reason (e.g. {@code
     * insufficientPermissions}, {@code rateLimitExceeded}) for a failed {@code users.watch} renewal
     * so a persistently unhealthy mailbox is diagnosable from logs. Status code and reason are
     * technical metadata, not email content, so logging them honours the privacy convention; the
     * Google error message (which can echo request input) is deliberately not logged.
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
}
