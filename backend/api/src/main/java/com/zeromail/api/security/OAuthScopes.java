package com.zeromail.api.security;

/**
 * Bundled-scope string constants for the single Google OAuth registration (Phase 01.5 D-A1).
 * Mirrors the {@code ErrorCodes.java} shape: {@code public final class} + private ctor + {@code
 * public static final String} constants. Eliminates scope-string duplication across the success
 * handler, resolver, and tests (REVIEW §5 finding).
 */
public final class OAuthScopes {

    public static final String GMAIL_MODIFY = "https://www.googleapis.com/auth/gmail.modify";
    public static final String GMAIL_SETTINGS_BASIC =
            "https://www.googleapis.com/auth/gmail.settings.basic";

    /** Prefix shared by all gmail.* scopes — used for scope-filtering in handlers. */
    public static final String GMAIL_PREFIX = "https://www.googleapis.com/auth/gmail.";

    private OAuthScopes() {}
}
