package com.zeromail.worker.notification.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.resend.services.emails.model.Tag;
import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.worker.notification.config.NotificationProperties;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResendEmailGateway {

    private final Resend resendClient;

    @Autowired
    public ResendEmailGateway(NotificationProperties notificationProperties) {
        this(notificationProperties, new Resend(notificationProperties.email().resend().apiKey()));
    }

    ResendEmailGateway(NotificationProperties notificationProperties, Resend resendClient) {
        Objects.requireNonNull(notificationProperties, "notificationProperties must not be null");
        this.resendClient = Objects.requireNonNull(resendClient, "resendClient must not be null");
    }

    public DispatchOutcome send(
            String fromAddress,
            String toAddress,
            String subject,
            String htmlBody,
            String textBody,
            String idempotencyKey) {
        CreateEmailOptions emailOptions =
                CreateEmailOptions.builder()
                        .from(fromAddress)
                        .to(toAddress)
                        .subject(subject)
                        .html(htmlBody)
                        .text(textBody)
                        .addHeader("Idempotency-Key", idempotencyKey)
                        .addTag(Tag.builder().name("category").value("digest").build())
                        .build();
        try {
            CreateEmailResponse response = resendClient.emails().send(emailOptions);
            return new DispatchOutcome.Success(response.getId());
        } catch (ResendException resendException) {
            Integer statusCode = resendException.getStatusCode();
            if (isPermanentFailure(statusCode)) {
                return new DispatchOutcome.PermanentFailure("resend_4xx_" + statusCode);
            }
            return new DispatchOutcome.TransientFailure("resend_transient_" + statusCode);
        } catch (RuntimeException runtimeException) {
            return new DispatchOutcome.TransientFailure("resend_transient_runtime");
        }
    }

    private static boolean isPermanentFailure(Integer statusCode) {
        return statusCode != null
                && (statusCode == 400
                        || statusCode == 401
                        || statusCode == 403
                        || statusCode == 404
                        || statusCode == 422);
    }
}
