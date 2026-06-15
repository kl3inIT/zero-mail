package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.zeromail.core.admin.tenant.usecases.TenantActivityRecorder;
import com.zeromail.core.admin.tenant.usecases.TenantActivityRequestContext;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionExpiredEvent;

class TenantActivityLogoutSuccessHandlerTest {

    @Test
    void onLogoutSuccess_records_session_duration_with_login_request_context() throws Exception {
        TenantActivityRecorder tenantActivityRecorder = mock(TenantActivityRecorder.class);
        Instant logoutAt = Instant.parse("2026-06-15T10:05:00Z");
        TenantActivityLogoutSuccessHandler logoutSuccessHandler =
                new TenantActivityLogoutSuccessHandler(
                        tenantActivityRecorder, Clock.fixed(logoutAt, ZoneOffset.UTC));

        UUID tenantId = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantActivitySessionAttributes.TENANT_ID, tenantId.toString());
        session.setAttribute(TenantActivitySessionAttributes.LOGIN_AT, "2026-06-15T10:00:00Z");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        logoutSuccessHandler.onLogoutSuccess(request, response, null);

        ArgumentCaptor<TenantActivityRequestContext> requestContextCaptor =
                ArgumentCaptor.forClass(TenantActivityRequestContext.class);
        verify(tenantActivityRecorder)
                .recordLogout(eq(tenantId), requestContextCaptor.capture(), eq(logoutAt), eq(300));
        assertThat(
                        Arrays.stream(TenantActivityRequestContext.class.getRecordComponents())
                                .map(RecordComponent::getName))
                .doesNotContain("ipAddress", "locationLabel", "deviceFamily", "userAgent");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void onSessionExpired_records_duration_until_expiry_with_login_request_context() {
        TenantActivityRecorder tenantActivityRecorder = mock(TenantActivityRecorder.class);
        Instant expiredAt = Instant.parse("2026-06-15T10:10:00Z");
        TenantActivitySessionExpiredListener sessionExpiredListener =
                new TenantActivitySessionExpiredListener(
                        tenantActivityRecorder, Clock.fixed(expiredAt, ZoneOffset.UTC));

        UUID tenantId = UUID.randomUUID();
        MapSession session = new MapSession();
        session.setAttribute(TenantActivitySessionAttributes.TENANT_ID, tenantId.toString());
        session.setAttribute(TenantActivitySessionAttributes.LOGIN_AT, "2026-06-15T10:00:00Z");

        sessionExpiredListener.onApplicationEvent(new SessionExpiredEvent(this, session));

        ArgumentCaptor<TenantActivityRequestContext> requestContextCaptor =
                ArgumentCaptor.forClass(TenantActivityRequestContext.class);
        verify(tenantActivityRecorder)
                .recordSessionExpired(
                        eq(tenantId), requestContextCaptor.capture(), eq(expiredAt), eq(600));
        assertThat(
                        Arrays.stream(TenantActivityRequestContext.class.getRecordComponents())
                                .map(RecordComponent::getName))
                .doesNotContain("ipAddress", "locationLabel", "deviceFamily", "userAgent");
    }
}
