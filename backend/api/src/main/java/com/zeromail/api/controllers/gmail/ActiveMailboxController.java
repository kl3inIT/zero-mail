package com.zeromail.api.controllers.gmail;

import com.zeromail.api.dto.gmail.ActiveMailboxResponse;
import com.zeromail.api.security.ActiveMailboxResolver;
import com.zeromail.core.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gmail/active-mailbox")
public class ActiveMailboxController {

    private final ActiveMailboxResolver activeMailboxResolver;

    public ActiveMailboxController(ActiveMailboxResolver activeMailboxResolver) {
        this.activeMailboxResolver = activeMailboxResolver;
    }

    @GetMapping
    public ResponseEntity<ActiveMailboxResponse> activeMailbox(HttpServletRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return activeMailboxResolver
                .resolveConnectionOrPrimary(request, tenantId)
                .map(ActiveMailboxResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{gmailConnectionId}")
    public ActiveMailboxResponse setActiveMailbox(
            @PathVariable UUID gmailConnectionId, HttpServletRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        return ActiveMailboxResponse.from(
                activeMailboxResolver.setActive(request, tenantId, gmailConnectionId));
    }
}
