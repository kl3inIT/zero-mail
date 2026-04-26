package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.TenantStatusResponse;
import com.zeromail.core.gmail.model.GmailConnectionView;
import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;

@RestController
public class TenantStatusController {

    private final GmailConnectionService connectionService;

    public TenantStatusController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/tenant/status")
    public TenantStatusResponse status() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        GmailConnectionView view = connectionService.currentStatus(tenantId);
        return toResponse(view);
    }

    private static TenantStatusResponse toResponse(GmailConnectionView view) {
        return new TenantStatusResponse(view.status(), view.googleEmail());
    }
}
