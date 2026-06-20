package com.zeromail.api.security;

import com.zeromail.core.oauth.scope.GoogleOAuthScope;

/**
 * Convenience accessor for the small set of Google OAuth scope strings used directly by the Spring
 * Security OAuth2 callback handlers in this package (granted-scope checks, gmail-scope audit
 * serialization). Delegates to {@link GoogleOAuthScope} so the canonical scope URL stays in the
 * ledger enum (Phase 12 INFRA-01 D-01 / D-03); this class only exposes the legacy field names
 * existing callers already depend on.
 *
 * <p>Do NOT add new literal scope URLs here — use {@link GoogleOAuthScope} directly at call sites
 * that need a new scope. This class survives only to avoid a ripple-rewrite of the existing Gmail
 * OAuth callback flow.
 */
public final class OAuthScopes {

    /**
     * Equivalent to {@link GoogleOAuthScope#GMAIL_MODIFY}.{@link GoogleOAuthScope#value() value()}.
     */
    public static final String GMAIL_MODIFY = GoogleOAuthScope.GMAIL_MODIFY.value();

    /**
     * Prefix shared by every {@code gmail.*} scope URL — used by callers that need to filter the
     * granted-scope set down to its Gmail-scoped subset. Derived from {@link #GMAIL_MODIFY} so the
     * literal URL only lives in {@link GoogleOAuthScope}.
     */
    public static final String GMAIL_PREFIX =
            GMAIL_MODIFY.substring(0, GMAIL_MODIFY.lastIndexOf('.') + 1);

    private OAuthScopes() {}
}
