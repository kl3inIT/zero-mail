package com.zeromail.api.controllers.gmail;

import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DisconnectController {

    private final GmailConnectionService connectionService;

    public DisconnectController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/tenant/disconnect")
    public void disconnect() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        connectionService.disconnect(tenantId);
    }
}
