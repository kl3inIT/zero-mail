---
phase: 01-foundation-safety-infrastructure
plan: 06
status: complete
completed: 2026-04-25
---

# Plan 01-06 — AES-GCM-256 Refresh-Token Envelope Cipher

## What shipped

- `com.zeromail.core.crypto.RefreshTokenCipher` — AES-GCM-256 envelope cipher.
  - Envelope format: `[key_version:int32 | nonce:12 | ciphertext:variable]` (D-G2).
  - 96-bit nonce drawn from `SecureRandom` per encrypt call.
  - `tenantId` bound as AAD via `cipher.updateAAD(tenantId.getBytes(UTF_8))` — a ciphertext written for tenant A cannot be decrypted as tenant B (row-swap defense).
  - Forward-compatible key rotation via `Map<Integer, SecretKey>` version map; rotation is a batch re-encrypt, no schema change.
  - `decrypt(...)` with an unknown `key_version` throws `IllegalStateException("unknown key version N")`.
  - All `GeneralSecurityException` paths wrapped as `IllegalStateException`.
- `RefreshTokenCryptoConfig` — `@Bean RefreshTokenCipher` reads `zeromail.crypto.refresh-token-key-base64`, base64-decodes, asserts the key is exactly 32 bytes (AES-256), and constructs the cipher with version `1`.
- `backend/api/src/main/resources/application.yml` — added:
  - `spring.cloud.gcp.secretmanager.enabled: ${GCP_SECRET_MANAGER_ENABLED:false}` (gated by env, defaults off so dev works without GCP creds).
  - `zeromail.crypto.refresh-token-key-base64: ${REFRESH_TOKEN_KEY_BASE64:${sm://oauth-refresh-token-key-v1/versions/1:}}` — env var first, then GCP Secret Manager, then empty (the empty fallback trips the cipher's 32-byte check at boot, which is the desired hard failure).
- `crypto/package-info.java` — `@ApplicationModule(displayName = "Crypto", allowedDependencies = {})`.

### Tests

- `RefreshTokenCipherTest` — 4 cases:
  - `round_trip` — encrypt + decrypt with the same tenant returns the original plaintext.
  - `tenant_aad_mismatch_fails` — decrypt with a different tenantId throws `IllegalStateException` (wrapped `AEADBadTagException`).
  - `unknown_version_rejected` — mangling the version bytes to `9` produces `IllegalStateException("unknown key version 9")`.
  - `envelope_contains_version_and_nonce` — empty plaintext still produces ≥ 16 bytes of envelope; bytes [0..3] decode to int 1.
- `NonceUniquenessTest.ten_thousand_unique_nonces` — 10 000 consecutive encryptions yield 10 000 unique 96-bit nonces (HashSet size assertion).

## Verification

- `./gradlew :backend:core:test --tests "com.zeromail.core.crypto.*"` → BUILD SUCCESSFUL (5 tests, all pass).
- `grep "AES/GCM/NoPadding" backend/core/src/main/java/com/zeromail/core/crypto/RefreshTokenCipher.java` → match.
- `grep "updateAAD" backend/core/src/main/java/com/zeromail/core/crypto/RefreshTokenCipher.java` → match.
- `grep "import java.security.GeneralSecurityException;" backend/core/src/main/java/com/zeromail/core/crypto/RefreshTokenCipher.java` → match (WARNING-6 pin).
- `grep "sm://oauth-refresh-token-key-v1" backend/api/src/main/resources/application.yml` → match.

## Requirements satisfied

- **AUTH-03 (in part)** — encryption-at-rest primitive for refresh tokens is ready. The cascading delete endpoint that completes AUTH-03 is owned by plan 01-07.

## Decisions implemented

- D-G1 — Single global AES-256 key sourced from GCP Secret Manager (with env-var fallback for dev/test).
- D-G2 — Versioned envelope `[key_version | nonce | ciphertext]` so future key rotation is non-breaking.

## Notes for downstream plans

- **Plan 01-05** must inject `RefreshTokenCipher` into the Gmail second-leg success handler and call `cipher.encrypt(refreshTokenBytes, tenantId.toString())` before persisting to `gmail_connections.refresh_token_encrypted`. Plain refresh-token bytes never live on the entity (FND-03 + AUTH-03).
- Test assertion target for plan 05's Gmail-callback test: the persisted `bytea` must NOT contain the UTF-8 bytes of the original token; the captured log stream must NOT contain it either.
- For local dev, `REFRESH_TOKEN_KEY_BASE64` can be set to any 32-byte base64 string (e.g. `openssl rand -base64 32`). For prod, point Secret Manager to the production key version.
