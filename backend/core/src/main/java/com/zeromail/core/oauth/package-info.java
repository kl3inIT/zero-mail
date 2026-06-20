/**
 * OAuth-related shared infrastructure used by Gmail (v1.0+) and Calendar (v1.4 Phase 12+) and any
 * future Google OAuth scopes (Drive — Phase 15+). Two leaf packages:
 *
 * <ul>
 *   <li>{@code scope} — the {@link com.zeromail.core.oauth.scope.GoogleOAuthScope} ledger enum and
 *       its companion allow-list source-text scanner test.
 *   <li>{@code token} — {@link com.zeromail.core.oauth.token.OAuthTokenStore} thin facade over the
 *       existing AES-GCM {@code RefreshTokenCipher} so Gmail and Calendar persistence share one
 *       envelope without duplicating crypto code.
 * </ul>
 */
@ApplicationModule(
        displayName = "OAuth",
        allowedDependencies = {"gmail :: persistence.crypto"})
package com.zeromail.core.oauth;

import org.springframework.modulith.ApplicationModule;
