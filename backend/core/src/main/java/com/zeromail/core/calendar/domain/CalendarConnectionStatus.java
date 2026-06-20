package com.zeromail.core.calendar.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Calendar connection lifecycle state machine per CAL-CONN-08 (three-state). Implements {@link
 * IdentifiedEnum} only — these states are unordered (REVOKED is not "greater than" DISCONNECTED in
 * any sortable sense), so per D-B5 we do NOT extend {@link
 * com.zeromail.core.shared.lang.OrderedEnum}.
 *
 * <p><b>D-C2 invariant:</b> {@link #id()} returns {@link Enum#name()} so
 * {@code @Enumerated(EnumType.STRING)} continues to persist the canonical id.
 *
 * <p><b>Transition matrix (state-machine reference; runtime enforcement lives in W2's {@code
 * CalendarConnectionService}):</b>
 *
 * <ul>
 *   <li>{@code (none) → CONNECTED}: successful OAuth round-trip lands a row in {@code
 *       calendar_connections} via {@code CalendarOAuthSuccessHandler} (W1, this plan).
 *   <li>{@code CONNECTED → DISCONNECTED}: user-initiated disconnect through the W3 frontend
 *       triggers the W2 cascade (delete preferences, flip status, evict access-token cache).
 *   <li>{@code CONNECTED → REVOKED}: Google-side revoke surfaces as {@code invalid_grant} on the
 *       next token-refresh attempt; W2's listener distinguishes user-disconnect from Google-revoke
 *       so the UI can offer a single "Connect again" CTA for both.
 * </ul>
 */
public enum CalendarConnectionStatus implements IdentifiedEnum {

    /**
     * Active grant — refresh token decrypts cleanly and the last access-token refresh succeeded.
     * The only status under which {@code CalendarApiClientFactory} mints a Google Calendar client;
     * any other status triggers {@code CalendarDisconnectedException}.
     */
    CONNECTED,

    /**
     * User-initiated disconnect through the Settings → Calendar frontend (W3). The row is kept
     * (audit trail + idempotent reconnect) but {@code mailbox_calendar_preferences} are wiped by
     * the W2 cascade. The {@code refresh_token_encrypted} column is set to {@code null} during
     * disconnect so a leaked DB dump cannot resurrect the grant.
     */
    DISCONNECTED,

    /**
     * Google-side revoke detected at refresh time (token endpoint returned {@code invalid_grant} or
     * {@code unauthorized_client}). Distinct from DISCONNECTED because the user did not initiate
     * the disconnect; W2's listener publishes a different domain event so the UI can show a
     * "re-grant required" banner instead of a neutral "not connected" state.
     */
    REVOKED;

    @Override
    public String id() {
        return name();
    }

    /** Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id. */
    public static CalendarConnectionStatus fromId(String id) {
        return Stream.of(values())
                .filter(status -> status.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        "Unknown CalendarConnectionStatus id: " + id));
    }
}
