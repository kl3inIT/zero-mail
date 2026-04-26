package com.zeromail.api.dto.gmail;

/**
 * Response body for {@code GET /gmail/connection/status}. Phase 1.2.1 D-D4 rename from
 * {@code TenantStatusResponse} — the payload has always been Gmail connection state
 * ({@code connectionStatus} + {@code googleEmail}), never tenant-level data, so the rename
 * aligns Java symbol with the API surface ahead of any external consumer.
 */
public record GmailConnectionStatusResponse(String connectionStatus, String googleEmail) {}
