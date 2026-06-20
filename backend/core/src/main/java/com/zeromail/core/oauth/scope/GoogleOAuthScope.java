package com.zeromail.core.oauth.scope;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Single source of truth for every Google OAuth scope this product is allowed to request.
 *
 * <p>Adding a new constant requires CASA-tier review (see per-constant JavaDoc for the verification
 * tier per developers.google.com/identity/protocols/oauth2/scopes). The literal URL never appears
 * anywhere else in production code (locked by D-01 / D-03 of Phase 12 CONTEXT.md); the companion
 * {@code OAuthScopeAllowListTest} source-text scanner (per RESEARCH.md §Pitfall 1) enforces this in
 * CI by walking {@code backend/{api,core,worker}/src/main/java} and failing on any literal scope
 * URL found outside this package.
 *
 * <p>Production call sites read the URL via {@link #value()}, e.g.:
 *
 * <pre>
 *   ClientRegistration.withRegistrationId("google-calendar")
 *       .scope(GoogleOAuthScope.CALENDAR_FREEBUSY.value(),
 *              GoogleOAuthScope.CALENDAR_EVENTS.value(),
 *              GoogleOAuthScope.CALENDAR_READONLY.value())
 *       ...
 * </pre>
 *
 * <p>{@link #fromValue(String)} is fail-loud per CONVENTIONS.md §4 — unknown scope URLs throw
 * {@link NoSuchElementException} instead of silently defaulting.
 *
 * <p>The {@code application.yml} {@code spring.security.oauth2.client.registration.google.scope}
 * block legitimately carries the same URL literals (YAML is the wire format Spring's OAuth2 Client
 * starter consumes); the source-text scanner only inspects {@code .java} files and so excludes YAML
 * by file-type filter rather than path whitelist.
 *
 * <p>Phase 15 will add only a {@code DRIVE_FILE} constant — by deliberate omission, broader {@code
 * drive}, {@code drive.readonly}, and {@code drive.metadata.readonly} scopes are unavailable to
 * production code.
 */
public enum GoogleOAuthScope {

    /** OIDC standard — non-sensitive. Introduced: v1.0 (Gmail login bundle). */
    OPENID("openid"),

    /** OIDC standard — non-sensitive. Introduced: v1.0 (Gmail login bundle). */
    PROFILE("profile"),

    /** OIDC standard — non-sensitive. Introduced: v1.0 (Gmail login bundle). */
    EMAIL("email"),

    /**
     * Gmail RW: read + label + draft + send. Tier: RESTRICTED (CASA Tier 2 — annual third-party
     * security assessment required). Introduced: v1.0.
     */
    GMAIL_MODIFY("https://www.googleapis.com/auth/gmail.modify"),

    /**
     * Calendar free/busy only — no event details or attendee lists. Tier: NON-SENSITIVE.
     * Introduced: v1.4 Phase 12 (Calendar Connection foundation).
     */
    CALENDAR_FREEBUSY("https://www.googleapis.com/auth/calendar.freebusy"),

    /**
     * Calendar event RW — required for Phase 13's {@code propose_meeting} rule action and Phase 14
     * booking-page event creation. Tier: SENSITIVE. Introduced: v1.4 Phase 12 (Calendar Connection
     * foundation).
     */
    CALENDAR_EVENTS("https://www.googleapis.com/auth/calendar.events"),

    /**
     * Calendar metadata read-only — required for {@code calendarList.list()} sub-calendar
     * enumeration at connect time. Tier: SENSITIVE. Introduced: v1.4 Phase 12.
     */
    CALENDAR_READONLY("https://www.googleapis.com/auth/calendar.readonly");

    private final String value;

    GoogleOAuthScope(String value) {
        this.value = value;
    }

    /** The literal scope URL (or OIDC token) Google's OAuth server expects. */
    public String value() {
        return value;
    }

    /**
     * Resolves a scope URL back to the matching enum constant. Fail-loud per CONVENTIONS.md §4:
     * unknown URLs throw {@link NoSuchElementException} instead of returning {@code null} or
     * silently defaulting — keeps the ledger authoritative.
     *
     * @throws NoSuchElementException if {@code url} is not a known approved scope
     */
    public static GoogleOAuthScope fromValue(String url) {
        return Stream.of(values())
                .filter(scope -> scope.value.equals(url))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown Google OAuth scope: " + url));
    }
}
