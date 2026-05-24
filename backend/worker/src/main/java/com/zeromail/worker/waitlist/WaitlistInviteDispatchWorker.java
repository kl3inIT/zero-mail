package com.zeromail.worker.waitlist;

import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.core.waitlist.domain.WaitlistStatus;
import com.zeromail.core.waitlist.persistence.WaitlistEmailEntity;
import com.zeromail.core.waitlist.persistence.WaitlistEmailRepository;
import com.zeromail.worker.notification.config.NotificationProperties;
import com.zeromail.worker.notification.email.ResendEmailGateway;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-row processor for waitlist invite dispatch. Called by {@link WaitlistInviteDispatchScheduler}
 * for each id returned by the claim query.
 *
 * <p>Each {@link #dispatchOne(UUID, Instant)} call runs inside its own {@link Transactional}
 * boundary so a single failure does not roll back sibling rows in the same cron tick. The Resend
 * idempotency key is {@code waitlist-invite:<waitlistId>} so a retry after a network blip will
 * surface the prior provider id instead of creating a duplicate send.
 */
@Component
public class WaitlistInviteDispatchWorker {

    private static final Logger LOG = LoggerFactory.getLogger(WaitlistInviteDispatchWorker.class);
    private static final Duration TRANSIENT_RETRY_DELAY = Duration.ofMinutes(15);

    private final WaitlistEmailRepository waitlistEmailRepository;
    private final WaitlistInviteRenderer waitlistInviteRenderer;
    private final ResendEmailGateway resendEmailGateway;
    private final NotificationProperties notificationProperties;
    private final Clock clock;

    public WaitlistInviteDispatchWorker(
            WaitlistEmailRepository waitlistEmailRepository,
            WaitlistInviteRenderer waitlistInviteRenderer,
            ResendEmailGateway resendEmailGateway,
            NotificationProperties notificationProperties,
            Clock clock) {
        this.waitlistEmailRepository =
                Objects.requireNonNull(
                        waitlistEmailRepository, "waitlistEmailRepository must not be null");
        this.waitlistInviteRenderer =
                Objects.requireNonNull(
                        waitlistInviteRenderer, "waitlistInviteRenderer must not be null");
        this.resendEmailGateway =
                Objects.requireNonNull(resendEmailGateway, "resendEmailGateway must not be null");
        this.notificationProperties =
                Objects.requireNonNull(
                        notificationProperties, "notificationProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public void dispatchOne(UUID waitlistId, Instant referenceInstant) {
        WaitlistEmailEntity entity =
                waitlistEmailRepository.findByIdForUpdate(waitlistId).orElse(null);
        if (entity == null) {
            LOG.info("event=waitlist.invite.skip reason=row_disappeared waitlistId={}", waitlistId);
            return;
        }
        if (!isDueForDispatch(entity, referenceInstant)) {
            LOG.info(
                    "event=waitlist.invite.skip reason=not_due waitlistId={} status={}",
                    waitlistId,
                    entity.getStatus());
            return;
        }

        URI loginUrl = URI.create(notificationProperties.appBaseUrl().toString()).resolve("/login");
        String subject = waitlistInviteRenderer.subject();
        String htmlBody = waitlistInviteRenderer.renderHtml(loginUrl);
        String textBody = waitlistInviteRenderer.renderText(loginUrl);
        String idempotencyKey = "waitlist-invite:" + waitlistId;

        DispatchOutcome outcome;
        try {
            outcome =
                    resendEmailGateway.send(
                            notificationProperties.email().fromAddress(),
                            entity.getEmail(),
                            subject,
                            htmlBody,
                            textBody,
                            idempotencyKey);
        } catch (RuntimeException dispatchFailure) {
            Instant now = clock.instant();
            entity.scheduleInviteRetry(now.plus(TRANSIENT_RETRY_DELAY), "dispatch_exception");
            LOG.warn(
                    "event=waitlist.invite.dispatch_exception waitlistId={}",
                    waitlistId,
                    dispatchFailure);
            return;
        }

        Instant now = clock.instant();
        switch (outcome) {
            case DispatchOutcome.Success success -> {
                entity.markInvited(now);
                LOG.info(
                        "event=waitlist.invite.sent waitlistId={} externalId={}",
                        waitlistId,
                        success.externalId());
            }
            case DispatchOutcome.PermanentFailure permanentFailure -> {
                entity.markInviteFailed(permanentFailure.reason(), now);
                LOG.info(
                        "event=waitlist.invite.permanent_fail waitlistId={} reason={}",
                        waitlistId,
                        permanentFailure.reason());
            }
            case DispatchOutcome.TransientFailure transientFailure -> {
                entity.scheduleInviteRetry(
                        now.plus(TRANSIENT_RETRY_DELAY), transientFailure.reason());
                LOG.info(
                        "event=waitlist.invite.transient_fail waitlistId={} reason={} nextAttempt={}",
                        waitlistId,
                        transientFailure.reason(),
                        now.plus(TRANSIENT_RETRY_DELAY));
            }
        }
    }

    private boolean isDueForDispatch(WaitlistEmailEntity entity, Instant referenceInstant) {
        if (entity.getStatus() == WaitlistStatus.APPROVED) {
            Instant nextAttemptAt = entity.getInviteNextAttemptAt();
            return nextAttemptAt == null || !nextAttemptAt.isAfter(referenceInstant);
        }
        return false;
    }
}
