package com.zeromail.core.gmail.model;

/**
 * Service-layer view of a tenant's Gmail connection. Returned by
 * {@code GmailConnectionService} so controllers do not need to depend on persistence types.
 *
 * <p>{@code status} is the {@code GmailConnectionStatus} enum name (e.g. {@code NOT_CONNECTED},
 * {@code CONNECTED}, {@code DISCONNECTED}); the controller forwards it as a string in the
 * HTTP DTO.
 */
public record GmailConnectionView(String status, String googleEmail) {

    public static GmailConnectionView notConnected() {
        return new GmailConnectionView("NOT_CONNECTED", null);
    }
}
