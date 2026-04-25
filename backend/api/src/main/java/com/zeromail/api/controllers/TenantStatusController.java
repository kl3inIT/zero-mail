package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.TenantStatusResponse;
import com.zeromail.core.persistence.GmailConnectionRepository;
import com.zeromail.core.persistence.GmailConnectionStatus;
import com.zeromail.core.tenant.TenantContext;

@RestController
public class TenantStatusController {

    private final GmailConnectionRepository conns;

    public TenantStatusController(GmailConnectionRepository conns) {
        this.conns = conns;
    }

    @GetMapping("/tenant/status")
    public TenantStatusResponse status() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        return conns.findByTenantId(tenantId)
                .map(c -> new TenantStatusResponse(c.getStatus().name(), c.getGoogleEmail()))
                .orElse(new TenantStatusResponse(GmailConnectionStatus.NOT_CONNECTED.name(), null));
    }
}
