package com.zeromail.api.controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zeromail.api.dto.gmail.GmailConnectionStatusResponse;
import com.zeromail.core.gmail.model.GmailConnectionView;
import com.zeromail.core.gmail.service.GmailConnectionService;
import com.zeromail.core.tenant.TenantContext;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code GET /gmail/connection/status} — returns the current Gmail-connection lifecycle
 * state for the authenticated tenant.
 *
 * <p><b>Phase 1.2.1 rename (D-D4):</b> URL changed from {@code /tenant/status} to
 * {@code /gmail/connection/status} and response renamed from {@code TenantStatusResponse}
 * to {@link GmailConnectionStatusResponse} because the payload has always been Gmail
 * connection state, never tenant-level data. Project is pre-launch — clean break preferred
 * over a deprecated alias (D-D4 explicit rejection of transitional path).
 *
 * <p><b>File-name vs class-name carve-out:</b> per CONTEXT line 131 the file remains
 * {@code TenantStatusController.java} (class name unchanged) — the API rename happens at
 * the URL + Tag + DTO surface, not at the internal Java symbol. Future readers: do not
 * rename this file in a follow-up phase without updating Spring's component scan
 * conventions and any documentation that references the class.
 *
 * <p><b>{@code toResponse(view)} helper:</b> matches the {@code MeController} convention
 * (Plan 01.2-06 Pattern 8 — controllers map view-model records to wire DTOs via a private
 * static helper, never inline {@code new}).
 */
@RestController
@Tag(name = "gmail")
public class TenantStatusController {

    private final GmailConnectionService connectionService;

    public TenantStatusController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/gmail/connection/status")
    public GmailConnectionStatusResponse status() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        GmailConnectionView view = connectionService.currentStatus(tenantId);
        return toResponse(view);
    }

    private static GmailConnectionStatusResponse toResponse(GmailConnectionView view) {
        return new GmailConnectionStatusResponse(view.status(), view.googleEmail());
    }
}
