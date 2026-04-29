package com.zeromail.core.gmail.model;

/**
 * Read-side projection for Gmail connection status. This is intentionally not a
 * persistence entity and not an API wire DTO.
 */
public record GmailConnectionProjection(String status, String ingestionHealth, String googleEmail) {

    public static GmailConnectionProjection notConnected() {
        return new GmailConnectionProjection("NOT_CONNECTED", "HEALTHY", null);
    }
}
