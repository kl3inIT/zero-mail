package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.core.account.TenantConnectionService;
import com.zeromail.core.tenant.TenantContext;

@RestController
public class DisconnectController {

    private final TenantConnectionService connectionService;

    public DisconnectController(TenantConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/tenant/disconnect")
    public void disconnect() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        connectionService.disconnect(tenantId);
    }
}
