/**
 * Refresh-token AES-GCM envelope cipher and its Spring configuration.
 * Domain-internal: this stays inside gmail/ until a second consumer materializes
 * (per CONTEXT D-C2 — Phase 2C BYOK MAY promote to {@code shared/crypto/}, but
 * speculative shared infrastructure is forbidden in Phase 1.2).
 */
package com.zeromail.core.gmail.persistence.crypto;
