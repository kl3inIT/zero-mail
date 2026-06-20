/**
 * OAuth refresh-token AES-GCM storage facade. {@link com.zeromail.core.oauth.token.OAuthTokenStore}
 * is a thin {@code @Component} that delegates to the existing {@code
 * com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher} without modifying it — Calendar
 * (Phase 12) and any future OAuth surface share Gmail's already-tested AES-GCM envelope (single
 * key, single envelope, single rotation surface; D-14 Claude's Discretion in Phase 12 CONTEXT.md).
 *
 * <p>The {@code RowDiscriminator} parameter is informational at the facade boundary; it exists so
 * future per-discriminator behavior (separate keys per OAuth surface, per-row audit fields) is a
 * non-breaking change. Today both discriminators delegate to the identical {@code
 * RefreshTokenCipher.encrypt(plaintext, tenantId.toString())} envelope.
 */
@NamedInterface("token")
package com.zeromail.core.oauth.token;

import org.springframework.modulith.NamedInterface;
