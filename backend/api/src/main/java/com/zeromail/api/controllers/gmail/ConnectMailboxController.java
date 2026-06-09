package com.zeromail.api.controllers.gmail;

import com.zeromail.api.security.OAuthIntentSnapshot;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gmail/mailboxes")
public class ConnectMailboxController {

    private static final Logger log = LoggerFactory.getLogger(ConnectMailboxController.class);
    private static final URI GOOGLE_OAUTH_RECONNECT_URI =
            URI.create("/oauth2/authorization/google?reconnect=true");

    private final GmailConnectionService connectionService;

    public ConnectMailboxController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/connect")
    public ResponseEntity<Void> connect(HttpServletRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        storePendingIntent(
                request,
                new OAuthIntentSnapshot(OAuthIntentSnapshot.INTENT_ADD_MAILBOX, null, tenantId));
        return oauthRedirect();
    }

    @GetMapping("/{gmailConnectionId}/reconnect")
    public ResponseEntity<Void> reconnect(
            @PathVariable UUID gmailConnectionId, HttpServletRequest request) {
        return prepareReconnect(gmailConnectionId, request);
    }

    @PostMapping("/{gmailConnectionId}/reconnect")
    public ResponseEntity<Void> reconnectFromValidationRoute(
            @PathVariable UUID gmailConnectionId, HttpServletRequest request) {
        return prepareReconnect(gmailConnectionId, request);
    }

    private ResponseEntity<Void> prepareReconnect(
            UUID gmailConnectionId, HttpServletRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        connectionService.resolveReconnectableConnectionOrThrow(tenantId, gmailConnectionId);
        storePendingIntent(
                request,
                new OAuthIntentSnapshot(
                        OAuthIntentSnapshot.INTENT_RECONNECT_MAILBOX, gmailConnectionId, tenantId));
        return oauthRedirect();
    }

    private void storePendingIntent(
            HttpServletRequest request, OAuthIntentSnapshot pendingIntentSnapshot) {
        request.getSession(true)
                .setAttribute(
                        OAuthIntentSnapshot.PENDING_INTENT_SESSION_ATTRIBUTE,
                        new OAuthIntentSnapshot(
                                pendingIntentSnapshot.intent(),
                                pendingIntentSnapshot.targetMailboxId(),
                                pendingIntentSnapshot.initiatingTenantId()));
        log.info(
                "event=oauth_pending_intent_stored tenantId={}",
                pendingIntentSnapshot.initiatingTenantId());
    }

    private static ResponseEntity<Void> oauthRedirect() {
        return ResponseEntity.status(HttpStatus.FOUND).location(GOOGLE_OAUTH_RECONNECT_URI).build();
    }
}
