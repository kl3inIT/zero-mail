package com.zeromail.api.controllers;

import java.time.Instant;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.core.persistence.GmailConnectionRepository;
import com.zeromail.core.persistence.GmailConnectionStatus;
import com.zeromail.core.tenant.TenantContext;

@RestController
public class DisconnectController {

    private final GmailConnectionRepository conns;

    public DisconnectController(GmailConnectionRepository conns) {
        this.conns = conns;
    }

    @PostMapping("/tenant/disconnect")
    @Transactional
    public void disconnect() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        conns.findByTenantId(tenantId).ifPresent(c -> {
            c.setStatus(GmailConnectionStatus.DISCONNECTED);
            c.setDisconnectedAt(Instant.now());
            conns.save(c);
        });
    }
}
