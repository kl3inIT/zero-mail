package com.zeromail.worker.notification;

import com.zeromail.core.account.persistence.UserRepository;
import com.zeromail.core.notification.domain.ChannelType;
import com.zeromail.core.notification.domain.DigestPayload;
import com.zeromail.core.notification.usecases.DigestAlreadyClaimedException;
import com.zeromail.core.notification.usecases.DigestClaimRecord;
import com.zeromail.core.notification.usecases.DigestComposer;
import com.zeromail.core.notification.usecases.DigestDeliveryService;
import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.core.notification.usecases.NotificationChannel;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.worker.notification.config.NotificationProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DigestDispatchTenantWorker {

    private static final Duration TRANSIENT_RETRY_DELAY = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(DigestDispatchTenantWorker.class);

    private final DigestDeliveryService digestDeliveryService;
    private final DigestComposer digestComposer;
    private final NotificationChannel notificationChannel;
    private final UserRepository userRepository;
    private final NotificationProperties notificationProperties;

    public DigestDispatchTenantWorker(
            DigestDeliveryService digestDeliveryService,
            DigestComposer digestComposer,
            NotificationChannel notificationChannel,
            UserRepository userRepository,
            NotificationProperties notificationProperties) {
        this.digestDeliveryService =
                Objects.requireNonNull(
                        digestDeliveryService, "digestDeliveryService must not be null");
        this.digestComposer =
                Objects.requireNonNull(digestComposer, "digestComposer must not be null");
        this.notificationChannel =
                Objects.requireNonNull(notificationChannel, "notificationChannel must not be null");
        this.userRepository =
                Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.notificationProperties =
                Objects.requireNonNull(
                        notificationProperties, "notificationProperties must not be null");
    }

    public void dispatchOne(DigestDueTenant dueTenant, Instant referenceInstant) {
        ScopedValue.where(TenantContext.TENANT, dueTenant.tenantId().toString())
                .run(() -> dispatchOneWithinTenant(dueTenant, referenceInstant));
    }

    private void dispatchOneWithinTenant(DigestDueTenant dueTenant, Instant referenceInstant) {
        ZoneId tenantZone = ZoneId.of(dueTenant.timeZone());
        LocalDate digestDayLocal = dueTenant.digestDayLocal();
        LocalDateTime scheduledLocalDateTime = digestDayLocal.atTime(dueTenant.sendHourLocal(), 0);
        Instant sendMoment = scheduledLocalDateTime.atZone(tenantZone).toInstant();
        Locale locale = Locale.forLanguageTag(dueTenant.preferredLanguage());
        DigestClaimRecord claimRecord;
        try {
            claimRecord =
                    digestDeliveryService.claimPending(
                            dueTenant.tenantId(), digestDayLocal, referenceInstant);
        } catch (DigestAlreadyClaimedException _) {
            log.info(
                    "event=digest_already_claimed tenantId={} digestDay={}",
                    dueTenant.tenantId(),
                    digestDayLocal);
            return;
        }

        Optional<String> emailAddress = userRepository.findEmailByTenantId(dueTenant.tenantId());
        if (emailAddress.isEmpty() || emailAddress.orElseThrow().isBlank()) {
            digestDeliveryService.markFailed(claimRecord.deliveryId(), "no_email_found");
            log.info("event=digest_no_email_found tenantId={}", dueTenant.tenantId());
            return;
        }

        DigestPayload payload =
                digestComposer.compose(
                        dueTenant.tenantId(),
                        tenantZone,
                        locale,
                        digestDayLocal,
                        sendMoment,
                        notificationProperties.appBaseUrl());
        DispatchOutcome outcome;
        try {
            outcome = notificationChannel.dispatch(payload, emailAddress.orElseThrow());
        } catch (RuntimeException runtimeException) {
            digestDeliveryService.markRetryable(
                    claimRecord.deliveryId(),
                    "dispatch_exception",
                    nextAttemptAt(referenceInstant));
            throw runtimeException;
        }
        switch (outcome) {
            case DispatchOutcome.Success success ->
                    digestDeliveryService.markSent(
                            claimRecord.deliveryId(), ChannelType.EMAIL, success.externalId());
            case DispatchOutcome.PermanentFailure permanentFailure ->
                    digestDeliveryService.markFailed(
                            claimRecord.deliveryId(), permanentFailure.reason());
            case DispatchOutcome.TransientFailure transientFailure ->
                    digestDeliveryService.markRetryable(
                            claimRecord.deliveryId(),
                            transientFailure.reason(),
                            nextAttemptAt(referenceInstant));
        }
    }

    private static Instant nextAttemptAt(Instant referenceInstant) {
        return referenceInstant.plus(TRANSIENT_RETRY_DELAY);
    }
}
