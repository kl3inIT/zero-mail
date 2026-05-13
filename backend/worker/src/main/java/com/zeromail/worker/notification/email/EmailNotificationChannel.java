package com.zeromail.worker.notification.email;

import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.core.notification.usecases.NotificationChannel;
import com.zeromail.worker.notification.config.NotificationProperties;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final ThymeleafDigestRenderer digestRenderer;
    private final ResendEmailGateway resendEmailGateway;
    private final NotificationProperties notificationProperties;

    public EmailNotificationChannel(
            ThymeleafDigestRenderer digestRenderer,
            ResendEmailGateway resendEmailGateway,
            NotificationProperties notificationProperties) {
        this.digestRenderer =
                Objects.requireNonNull(digestRenderer, "digestRenderer must not be null");
        this.resendEmailGateway =
                Objects.requireNonNull(resendEmailGateway, "resendEmailGateway must not be null");
        this.notificationProperties =
                Objects.requireNonNull(
                        notificationProperties, "notificationProperties must not be null");
    }

    @Override
    public DispatchOutcome dispatch(DigestPayload payload, String recipientAddress) {
        if (recipientAddress == null || recipientAddress.isBlank()) {
            return new DispatchOutcome.PermanentFailure("no_email_found");
        }

        String subject = digestRenderer.subject(payload);
        String htmlBody = digestRenderer.renderHtml(payload);
        String textBody = digestRenderer.renderText(payload);
        String idempotencyKey = payload.tenantId() + ":" + payload.digestDayLocal();
        DispatchOutcome outcome =
                resendEmailGateway.send(
                        notificationProperties.email().fromAddress(),
                        recipientAddress,
                        subject,
                        htmlBody,
                        textBody,
                        idempotencyKey);
        logOutcome(payload, outcome);
        return outcome;
    }

    private static void logOutcome(DigestPayload payload, DispatchOutcome outcome) {
        switch (outcome) {
            case DispatchOutcome.Success success ->
                    log.info(
                            "event=digest_dispatched tenantId={} digestDay={} externalId={}",
                            payload.tenantId(),
                            payload.digestDayLocal(),
                            success.externalId());
            case DispatchOutcome.PermanentFailure permanentFailure ->
                    log.info(
                            "event=digest_dispatch_failed tenantId={} digestDay={} reason={}",
                            payload.tenantId(),
                            payload.digestDayLocal(),
                            permanentFailure.reason());
            case DispatchOutcome.TransientFailure transientFailure ->
                    log.info(
                            "event=digest_dispatch_failed tenantId={} digestDay={} reason={}",
                            payload.tenantId(),
                            payload.digestDayLocal(),
                            transientFailure.reason());
        }
    }
}
