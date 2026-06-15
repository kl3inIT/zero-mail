package com.zeromail.api.security;

import com.zeromail.core.admin.tenant.usecases.TenantActivityRecorder;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.session.Session;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.stereotype.Component;

@Component
public class TenantActivitySessionExpiredListener
        implements ApplicationListener<SessionExpiredEvent> {

    private static final Logger log =
            LoggerFactory.getLogger(TenantActivitySessionExpiredListener.class);

    private final TenantActivityRecorder tenantActivityRecorder;
    private final Clock clock;

    public TenantActivitySessionExpiredListener(
            TenantActivityRecorder tenantActivityRecorder, Clock clock) {
        this.tenantActivityRecorder =
                Objects.requireNonNull(
                        tenantActivityRecorder, "tenantActivityRecorder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void onApplicationEvent(SessionExpiredEvent event) {
        Session session = event.getSession();
        if (session == null) {
            return;
        }
        SessionActivityState sessionActivityState = SessionActivityState.from(session);
        if (sessionActivityState == null) {
            return;
        }
        Instant expiredAt = clock.instant();
        try {
            tenantActivityRecorder.recordSessionExpired(
                    sessionActivityState.tenantId(),
                    sessionActivityState.requestContext(),
                    expiredAt,
                    sessionActivityState.durationSeconds(expiredAt));
        } catch (RuntimeException recordingFailure) {
            log.warn(
                    "event=tenant_activity_session_expired_record_failed tenantId={} failureClass={}",
                    sessionActivityState.tenantId(),
                    recordingFailure.getClass().getSimpleName());
        }
    }
}
