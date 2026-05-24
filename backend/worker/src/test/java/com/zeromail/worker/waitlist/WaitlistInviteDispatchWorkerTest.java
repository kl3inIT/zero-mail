package com.zeromail.worker.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeromail.core.notification.usecases.DispatchOutcome;
import com.zeromail.core.waitlist.domain.WaitlistStatus;
import com.zeromail.core.waitlist.persistence.WaitlistEmailEntity;
import com.zeromail.core.waitlist.persistence.WaitlistEmailRepository;
import com.zeromail.worker.notification.config.NotificationProperties;
import com.zeromail.worker.notification.email.ResendEmailGateway;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WaitlistInviteDispatchWorkerTest {

    private static final Instant NOW = Instant.parse("2026-05-22T10:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");

    private WaitlistEmailRepository repository;
    private WaitlistInviteRenderer renderer;
    private ResendEmailGateway resendEmailGateway;
    private NotificationProperties notificationProperties;
    private WaitlistInviteDispatchWorker worker;

    @BeforeEach
    void setUp() {
        repository = mock(WaitlistEmailRepository.class);
        renderer = new WaitlistInviteRenderer();
        resendEmailGateway = mock(ResendEmailGateway.class);
        notificationProperties =
                new NotificationProperties(
                        new NotificationProperties.EmailProperties(
                                new NotificationProperties.ResendProperties("re_test_key"),
                                "noreply@zero-mail.app"),
                        URI.create("https://zero-mail.app"));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        worker =
                new WaitlistInviteDispatchWorker(
                        repository, renderer, resendEmailGateway, notificationProperties, clock);
    }

    @Test
    void success_outcome_transitions_to_invited_and_clears_retry() {
        UUID id = UUID.randomUUID();
        WaitlistEmailEntity entity = approvedEntity(id, "alice@example.com");
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        when(resendEmailGateway.send(
                        any(), any(), any(), any(), any(), eq("waitlist-invite:" + id)))
                .thenReturn(new DispatchOutcome.Success("re_msg_123"));

        worker.dispatchOne(id, NOW);

        assertThat(entity.getStatus()).isEqualTo(WaitlistStatus.INVITED);
        assertThat(entity.getInviteSentAt()).isEqualTo(NOW);
        assertThat(entity.getInviteNextAttemptAt()).isNull();
        assertThat(entity.getInviteFailureReason()).isNull();
    }

    @Test
    void permanent_failure_transitions_to_invite_failed_with_reason() {
        UUID id = UUID.randomUUID();
        WaitlistEmailEntity entity = approvedEntity(id, "bob@example.com");
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        when(resendEmailGateway.send(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DispatchOutcome.PermanentFailure("resend_4xx_422"));

        worker.dispatchOne(id, NOW);

        assertThat(entity.getStatus()).isEqualTo(WaitlistStatus.INVITE_FAILED);
        assertThat(entity.getInviteSentAt()).isEqualTo(NOW);
        assertThat(entity.getInviteFailureReason()).isEqualTo("resend_4xx_422");
    }

    @Test
    void transient_failure_keeps_status_approved_and_schedules_retry() {
        UUID id = UUID.randomUUID();
        WaitlistEmailEntity entity = approvedEntity(id, "carol@example.com");
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        when(resendEmailGateway.send(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DispatchOutcome.TransientFailure("resend_transient_503"));

        worker.dispatchOne(id, NOW);

        assertThat(entity.getStatus()).isEqualTo(WaitlistStatus.APPROVED);
        assertThat(entity.getInviteSentAt()).isNull();
        assertThat(entity.getInviteNextAttemptAt())
                .isEqualTo(NOW.plus(java.time.Duration.ofMinutes(15)));
        assertThat(entity.getInviteFailureReason()).isEqualTo("resend_transient_503");
    }

    @Test
    void runtime_exception_during_dispatch_schedules_retry() {
        UUID id = UUID.randomUUID();
        WaitlistEmailEntity entity = approvedEntity(id, "dave@example.com");
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));
        when(resendEmailGateway.send(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        worker.dispatchOne(id, NOW);

        assertThat(entity.getStatus()).isEqualTo(WaitlistStatus.APPROVED);
        assertThat(entity.getInviteNextAttemptAt())
                .isEqualTo(NOW.plus(java.time.Duration.ofMinutes(15)));
        assertThat(entity.getInviteFailureReason()).isEqualTo("dispatch_exception");
    }

    @Test
    void missing_row_is_skipped_silently() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        worker.dispatchOne(id, NOW);
        // No exception, no Resend call.
    }

    @Test
    void non_approved_status_row_is_skipped() {
        UUID id = UUID.randomUUID();
        WaitlistEmailEntity entity = approvedEntity(id, "erin@example.com");
        entity.markInvited(NOW.minusSeconds(60));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));

        worker.dispatchOne(id, NOW);

        assertThat(entity.getStatus()).isEqualTo(WaitlistStatus.INVITED);
    }

    @Test
    void retry_window_in_future_skips_dispatch() {
        UUID id = UUID.randomUUID();
        WaitlistEmailEntity entity = approvedEntity(id, "frank@example.com");
        entity.scheduleInviteRetry(NOW.plus(java.time.Duration.ofMinutes(10)), "earlier_transient");
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(entity));

        worker.dispatchOne(id, NOW);

        // Status stays APPROVED, next_attempt_at unchanged.
        assertThat(entity.getStatus()).isEqualTo(WaitlistStatus.APPROVED);
        assertThat(entity.getInviteNextAttemptAt())
                .isEqualTo(NOW.plus(java.time.Duration.ofMinutes(10)));
    }

    private static WaitlistEmailEntity approvedEntity(UUID id, String email) {
        WaitlistEmailEntity entity = new WaitlistEmailEntity(id, email, "landing_page", null, null);
        entity.approve(ADMIN_ID, NOW.minusSeconds(120));
        return entity;
    }
}
