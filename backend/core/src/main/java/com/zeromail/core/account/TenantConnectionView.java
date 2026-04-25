package com.zeromail.core.account;

/**
 * Service-layer view of a tenant's Gmail connection. Returned by
 * {@link TenantConnectionService} so controllers do not need to depend on persistence types.
 *
 * <p>{@code status} is the {@code GmailConnectionStatus} enum name (e.g. {@code NOT_CONNECTED},
 * {@code CONNECTED}, {@code DISCONNECTED}); the controller forwards it as a string in the
 * HTTP DTO.
 */
public record TenantConnectionView(String status, String googleEmail) {

    public static TenantConnectionView notConnected() {
        return new TenantConnectionView("NOT_CONNECTED", null);
    }
}
