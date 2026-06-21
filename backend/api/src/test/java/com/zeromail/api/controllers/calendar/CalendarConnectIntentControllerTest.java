package com.zeromail.api.controllers.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.zeromail.api.dto.calendar.CalendarConnectIntentRequest;
import com.zeromail.api.dto.calendar.CalendarConnectIntentResponse;
import com.zeromail.api.security.OAuthIntentSnapshot;
import com.zeromail.core.gmail.exception.MailboxNotOwnedException;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Plain-unit (no Spring context) tests for {@link CalendarConnectIntentController}. The controller
 * is thin per CONVENTIONS §1 — only collaborators mocked are {@code GmailConnectionService}
 * (mailbox ownership guard). Session interactions go through a real {@link MockHttpServletRequest}
 * so the {@code PENDING_INTENT_SESSION_ATTRIBUTE} round-trip is exercised end-to-end.
 *
 * <p>Wire-format defaults ({@code @Schema}, Bean Validation) are covered by springdoc OpenAPI emit
 * at build time + the API integration test gate.
 */
class CalendarConnectIntentControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");
    private static final UUID MAILBOX_ID = UUID.fromString("00000000-0000-0000-0000-00000000bbbb");

    private final GmailConnectionService gmailConnectionService =
            mock(GmailConnectionService.class);

    @Test
    void prepareConnect_stampsPendingIntentAndReturnsAuthorizationUrl() {
        CalendarConnectIntentController controller =
                new CalendarConnectIntentController(gmailConnectionService);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        given(gmailConnectionService.resolveOwnedConnectionOrThrow(TENANT_ID, MAILBOX_ID))
                .willReturn(mock(GmailConnectionEntity.class));

        CalendarConnectIntentResponse response =
                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                        .call(
                                () ->
                                        controller.prepareConnect(
                                                new CalendarConnectIntentRequest(MAILBOX_ID),
                                                servletRequest));

        assertThat(response.authorizationUrl()).isEqualTo("/oauth2/authorization/google-calendar");
        // Same-origin literal URL — never user-derived (mitigates T-12-09 open redirect).
        assertThat(response.authorizationUrl()).startsWith("/");

        HttpSession session = servletRequest.getSession(false);
        assertThat(session).isNotNull();
        Object stamped = session.getAttribute(OAuthIntentSnapshot.PENDING_INTENT_SESSION_ATTRIBUTE);
        assertThat(stamped).isInstanceOf(OAuthIntentSnapshot.class);
        OAuthIntentSnapshot snapshot = (OAuthIntentSnapshot) stamped;
        assertThat(snapshot.intent()).isEqualTo(OAuthIntentSnapshot.INTENT_CALENDAR_CONNECT);
        assertThat(snapshot.targetMailboxId()).isEqualTo(MAILBOX_ID);
        assertThat(snapshot.initiatingTenantId()).isEqualTo(TENANT_ID);
        then(gmailConnectionService).should().resolveOwnedConnectionOrThrow(TENANT_ID, MAILBOX_ID);
    }

    @Test
    void prepareConnect_persistsInitiatingSecurityContextWhenAuthenticated() {
        CalendarConnectIntentController controller =
                new CalendarConnectIntentController(gmailConnectionService);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        given(gmailConnectionService.resolveOwnedConnectionOrThrow(TENANT_ID, MAILBOX_ID))
                .willReturn(mock(GmailConnectionEntity.class));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("user-principal", "n/a");
        SecurityContext outerContext = SecurityContextHolder.getContext();
        try {
            SecurityContext currentContext = new SecurityContextImpl(authentication);
            SecurityContextHolder.setContext(currentContext);

            ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                    .call(
                            () ->
                                    controller.prepareConnect(
                                            new CalendarConnectIntentRequest(MAILBOX_ID),
                                            servletRequest));
        } finally {
            SecurityContextHolder.setContext(outerContext);
        }

        HttpSession session = servletRequest.getSession(false);
        assertThat(session).isNotNull();
        Object securityContextAttribute =
                session.getAttribute(
                        OAuthIntentSnapshot.INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE);
        assertThat(securityContextAttribute).isInstanceOf(SecurityContext.class);
        SecurityContext storedContext = (SecurityContext) securityContextAttribute;
        assertThat(storedContext.getAuthentication().getPrincipal()).isEqualTo("user-principal");
    }

    @Test
    void prepareConnect_propagatesMailboxNotOwnedForCrossTenantAccess() {
        CalendarConnectIntentController controller =
                new CalendarConnectIntentController(gmailConnectionService);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        given(gmailConnectionService.resolveOwnedConnectionOrThrow(TENANT_ID, MAILBOX_ID))
                .willThrow(new MailboxNotOwnedException(TENANT_ID, MAILBOX_ID));

        assertThatExceptionOfType(MailboxNotOwnedException.class)
                .isThrownBy(
                        () ->
                                ScopedValue.where(TenantContext.TENANT, TENANT_ID.toString())
                                        .call(
                                                () ->
                                                        controller.prepareConnect(
                                                                new CalendarConnectIntentRequest(
                                                                        MAILBOX_ID),
                                                                servletRequest)));

        // No intent stamped on session when ownership check fails — attacker cannot stamp arbitrary
        // mailboxIds on their own session (mitigates T-12-08 tampering).
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            assertThat(session.getAttribute(OAuthIntentSnapshot.PENDING_INTENT_SESSION_ATTRIBUTE))
                    .isNull();
        }
    }
}
