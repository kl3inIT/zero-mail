package com.zeromail.api.controllers.calendar;

import com.zeromail.api.dto.calendar.CalendarConnectIntentRequest;
import com.zeromail.api.dto.calendar.CalendarConnectIntentResponse;
import com.zeromail.api.security.OAuthIntentSnapshot;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 12 W3 (CAL-CONN-01 / D-06) — prepares a Google Calendar OAuth round-trip by stamping the
 * active {@code mailboxId} on Spring Session via {@link
 * OAuthIntentSnapshot#PENDING_INTENT_SESSION_ATTRIBUTE}. Mirrors Phase 10's {@code
 * ConnectMailboxController.storePendingIntent} pattern for Gmail add-mailbox / reconnect flows.
 *
 * <p>The frontend POSTs {@code {mailboxId}}; this controller validates that the caller owns the
 * mailbox (via {@link GmailConnectionService#resolveOwnedConnectionOrThrow(UUID, UUID)} — throws
 * {@code MailboxNotOwnedException} → 404 from {@code GlobalExceptionHandler}), stamps the intent
 * snapshot, and returns the canonical {@code /oauth2/authorization/google-calendar} URL the browser
 * then navigates to.
 *
 * <p>On callback (after the user consents at Google), {@link
 * com.zeromail.api.security.GoogleAuthorizationRequestResolver} consumes the pending intent and
 * promotes it onto the {@code OAuth2AuthorizationRequest} attributes, then {@link
 * com.zeromail.api.security.IntentCarryingAuthorizationRequestRepository} promotes the snapshot to
 * the callback session attribute, and {@link com.zeromail.api.security.CalendarOAuthSuccessHandler}
 * reads {@code snapshot.targetMailboxId()} to seed D-06 default role rows.
 *
 * <p>Privacy: this controller logs only {@code event=calendar_connect_intent_stamped tenantId={}
 * mailboxId={}} — no email, no token bytes.
 */
@RestController
@RequestMapping("/api/calendar")
@Tag(name = "calendar")
public class CalendarConnectIntentController {

    private static final Logger log =
            LoggerFactory.getLogger(CalendarConnectIntentController.class);

    /**
     * Same-origin canonical URL Spring Security exposes for the {@code google-calendar} client
     * registration. Hardcoded — never derived from request input — so this surface cannot be turned
     * into an open redirect (threat T-12-09).
     */
    static final String CALENDAR_AUTHORIZATION_URL = "/oauth2/authorization/google-calendar";

    private final GmailConnectionService gmailConnectionService;

    public CalendarConnectIntentController(GmailConnectionService gmailConnectionService) {
        this.gmailConnectionService = gmailConnectionService;
    }

    @Operation(
            summary = "Prepare Google Calendar OAuth round-trip for the given mailbox",
            description =
                    "Stamps the active mailboxId on Spring Session so the OAuth success handler can"
                            + " seed default role rows for that mailbox only. Returns the canonical"
                            + " authorization URL the browser then navigates to.")
    @PostMapping("/connect-intent")
    public CalendarConnectIntentResponse prepareConnect(
            @Valid @RequestBody CalendarConnectIntentRequest request,
            HttpServletRequest servletRequest) {
        UUID tenantId = TenantContext.currentTenantUuid();
        UUID mailboxId = request.mailboxId();

        // Validate mailbox ownership BEFORE stamping intent — an attacker cannot use this endpoint
        // to stamp an arbitrary mailboxId on their own session because
        // resolveOwnedConnectionOrThrow
        // throws MailboxNotOwnedException (→ 404) for cross-tenant or unknown mailboxes.
        gmailConnectionService.resolveOwnedConnectionOrThrow(tenantId, mailboxId);

        HttpSession session = servletRequest.getSession(true);
        session.setAttribute(
                OAuthIntentSnapshot.PENDING_INTENT_SESSION_ATTRIBUTE,
                new OAuthIntentSnapshot(
                        OAuthIntentSnapshot.INTENT_CALENDAR_CONNECT, mailboxId, tenantId));
        storeInitiatingSecurityContext(session);

        log.info(
                "event=calendar_connect_intent_stamped tenantId={} mailboxId={}",
                tenantId,
                mailboxId);

        return new CalendarConnectIntentResponse(CALENDAR_AUTHORIZATION_URL);
    }

    /**
     * Persist the initiating security context so {@code CalendarOAuthSuccessHandler} can recover
     * the user identity on callback. Mirrors {@code
     * ConnectMailboxController.storeInitiatingSecurityContext}.
     */
    private static void storeInitiatingSecurityContext(HttpSession session) {
        SecurityContext currentSecurityContext = SecurityContextHolder.getContext();
        Authentication currentAuthentication = currentSecurityContext.getAuthentication();
        if (currentAuthentication == null) {
            return;
        }
        session.setAttribute(
                OAuthIntentSnapshot.INITIATING_SECURITY_CONTEXT_SESSION_ATTRIBUTE,
                new SecurityContextImpl(currentAuthentication));
    }
}
