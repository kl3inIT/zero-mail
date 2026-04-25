package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.TenantStatusResponse;
import com.zeromail.core.account.TenantConnectionService;
import com.zeromail.core.account.TenantConnectionView;
import com.zeromail.core.tenant.TenantContext;

@RestController
public class TenantStatusController {

    private final TenantConnectionService connectionService;

    public TenantStatusController(TenantConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/tenant/status")
    public TenantStatusResponse status() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        TenantConnectionView view = connectionService.currentStatus(tenantId);
        return new TenantStatusResponse(view.status(), view.googleEmail());
    }
}
