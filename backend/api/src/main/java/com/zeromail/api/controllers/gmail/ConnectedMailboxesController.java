package com.zeromail.api.controllers.gmail;

import com.zeromail.api.dto.gmail.MailboxSummaryResponse;
import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.gmail.usecases.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gmail/mailboxes")
public class ConnectedMailboxesController {

    private final GmailConnectionService connectionService;

    public ConnectedMailboxesController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<MailboxSummaryResponse> listMailboxes() {
        UUID tenantId = TenantContext.currentTenantUuid();
        return connectionService.listMailboxes(tenantId).stream()
                .map(MailboxSummaryResponse::from)
                .toList();
    }

    @PostMapping("/{gmailConnectionId}/primary")
    public ResponseEntity<Void> setPrimary(@PathVariable UUID gmailConnectionId) {
        return setPrimaryMailbox(gmailConnectionId);
    }

    @PostMapping("/{gmailConnectionId}/set-primary")
    public ResponseEntity<Void> setPrimaryFromValidationRoute(
            @PathVariable UUID gmailConnectionId) {
        return setPrimaryMailbox(gmailConnectionId);
    }

    @PostMapping("/{gmailConnectionId}/disconnect")
    public ResponseEntity<Void> disconnect(@PathVariable UUID gmailConnectionId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        connectionService.disconnect(new MailboxRef(tenantId, gmailConnectionId));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> setPrimaryMailbox(UUID gmailConnectionId) {
        UUID tenantId = TenantContext.currentTenantUuid();
        connectionService.setPrimary(tenantId, gmailConnectionId);
        return ResponseEntity.noContent().build();
    }
}
