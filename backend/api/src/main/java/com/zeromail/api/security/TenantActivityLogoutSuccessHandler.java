package com.zeromail.api.security;

import com.zeromail.core.admin.tenant.usecases.TenantActivityRecorder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class TenantActivityLogoutSuccessHandler implements LogoutSuccessHandler {

    private static final Logger log =
            LoggerFactory.getLogger(TenantActivityLogoutSuccessHandler.class);

    private final TenantActivityRecorder tenantActivityRecorder;
    private final Clock clock;
    private final LogoutSuccessHandler delegate = new HttpStatusReturningLogoutSuccessHandler();

    public TenantActivityLogoutSuccessHandler(
            TenantActivityRecorder tenantActivityRecorder, Clock clock) {
        this.tenantActivityRecorder =
                Objects.requireNonNull(
                        tenantActivityRecorder, "tenantActivityRecorder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void onLogoutSuccess(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            Authentication authentication)
            throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            recordLogout(session);
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // Already invalidated by the container; returning 200 still mirrors prior behavior.
            }
        }
        delegate.onLogoutSuccess(request, response, authentication);
    }

    private void recordLogout(HttpSession session) {
        SessionActivityState sessionActivityState = SessionActivityState.from(session);
        if (sessionActivityState == null) {
            return;
        }
        Instant logoutAt = clock.instant();
        try {
            tenantActivityRecorder.recordLogout(
                    sessionActivityState.tenantId(),
                    sessionActivityState.requestContext(),
                    logoutAt,
                    sessionActivityState.durationSeconds(logoutAt));
        } catch (RuntimeException recordingFailure) {
            log.warn(
                    "event=tenant_activity_logout_record_failed tenantId={} failureClass={}",
                    sessionActivityState.tenantId(),
                    recordingFailure.getClass().getSimpleName());
        }
    }
}
