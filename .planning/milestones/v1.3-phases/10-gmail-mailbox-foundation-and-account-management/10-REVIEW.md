---
phase: 10-gmail-mailbox-foundation-and-account-management
reviewed: 2026-06-09T00:00:00Z
depth: standard
files_reviewed: 36
files_reviewed_list:
  - backend/api/src/main/java/com/zeromail/api/controllers/gmail/ConnectedMailboxesController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/gmail/ConnectMailboxController.java
  - backend/api/src/main/java/com/zeromail/api/dto/gmail/MailboxSummaryResponse.java
  - backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java
  - backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java
  - backend/api/src/main/java/com/zeromail/api/security/IntentCarryingAuthorizationRequestRepository.java
  - backend/api/src/main/java/com/zeromail/api/security/LoginRedirectAuthenticationFailureHandler.java
  - backend/api/src/main/java/com/zeromail/api/security/OAuthIntentSnapshot.java
  - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
  - backend/api/src/test/java/com/zeromail/api/arch/GmailClientLookupBoundaryTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/gmail/MailboxOwnershipSeamTest.java
  - backend/api/src/test/java/com/zeromail/api/security/GoogleOAuthSuccessHandlerTest.java
  - backend/api/src/test/java/com/zeromail/api/security/IntentCarryingRepositoryTest.java
  - backend/api/src/test/java/com/zeromail/api/security/OAuthIntentRoutingTest.java
  - backend/core/src/main/java/com/zeromail/core/gmail/exception/DuplicateActiveMailboxException.java
  - backend/core/src/main/java/com/zeromail/core/gmail/exception/MailboxDisconnectedException.java
  - backend/core/src/main/java/com/zeromail/core/gmail/exception/MailboxNotOwnedException.java
  - backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java
  - backend/core/src/main/java/com/zeromail/core/gmail/gateway/MailboxRef.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java
  - backend/core/src/main/java/com/zeromail/core/gmail/projection/MailboxSummaryProjection.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java
  - backend/core/src/main/java/com/zeromail/core/shared/error/ErrorCodes.java
  - backend/core/src/main/resources/db/changelog/changes/119-gmail-connections-multi-mailbox.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/core/src/test/java/com/zeromail/core/arch/GmailClientLookupBoundaryTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/gateway/GmailApiClientFactoryMailboxCacheTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/migration/Migration119Test.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/DuplicateActiveEmailTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/GmailConnectionUniquenessTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/RefreshTokenCipherContinuityTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/SetPrimaryTransactionalTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/support/OldSingleAccountFixture.java
  - backend/core/src/test/java/com/zeromail/core/gmail/usecases/GmailConnectionServiceDisconnectTest.java
findings:
  critical: 3
  warning: 7
  info: 4
  total: 14
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-06-09
**Depth:** standard
**Files Reviewed:** 36
**Status:** issues_found

## Summary

Phase 10 relaxes the historic one-Gmail-per-tenant invariant to support multiple connected mailboxes per tenant, adds `is_primary` / `display_purpose` columns, a partial unique index on `(tenant_id, lower(google_email)) WHERE status='CONNECTED'`, and a mailbox-scoped client/cache path (`MailboxRef`, `buildClientForMailbox`). The OAuth intent-routing (add/reconnect) machinery is well thought through and the new mailbox-scoped disconnect/primary-promotion logic is solid in isolation.

The dominant problem is that **the schema migration removes the per-tenant unique constraint but the data-access layer still assumes at most one row per tenant.** `GmailConnectionRepository.findByTenantId(...)` returns `Optional<GmailConnectionEntity>` and is called from at least eight production code paths. With two CONNECTED mailboxes (the whole point of this phase) Spring Data will throw `IncorrectResultSizeDataAccessException` at runtime. This is a correctness/data-integrity landmine that the phase's own feature makes reachable. Two further BLOCKERs concern stale-token cache reuse after disconnect/reconnect and the migration's primary backfill for tenants with no CONNECTED row.

## Critical Issues

### CR-01: `findByTenantId` returns `Optional` but multiple rows per tenant are now legal — throws at runtime

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java:14`
**Issue:**
Migration 119 (`119-gmail-connections-multi-mailbox.yaml:30`) drops `uq_gmail_connections_tenant_id`, intentionally allowing many `gmail_connections` rows per tenant. But the derived query

```java
Optional<GmailConnectionEntity> findByTenantId(UUID tenantId);
```

still expects 0..1 rows. Spring Data executes this as `getSingleResult()`-style access; when a tenant has two mailboxes (one CONNECTED + one DISCONNECTED, or two CONNECTED with different emails — both now valid per `DuplicateActiveEmailTest` / `GmailConnectionUniquenessTest`) it throws `IncorrectResultSizeDataAccessException` / `NonUniqueResultException`.

This method is consumed by many hot paths that run on the live triage/ingestion and account surfaces, e.g.:
- `GmailConnectionService.currentStatus` (`:68`), `markDisconnected(UUID)` (`:199`), `revokeStoredRefreshToken(UUID)` (`:262`), `tryStopWatch(UUID)` (`:312`), `deleteForCurrentTenant` (`:358`), `upsert` (`:388`), and all the `markHistory*/markWatch*/recordWatch*/increment*/clearForReconnect` mutators.
- `RecentInboxReadService:530`, `GmailPreviewReadService:142/179/321`, `InboxBackfillService:115`, `GmailDeliveryProcessingService:89`, `GmailAccessGuard` (`:56`), and the invalid-grant listener.

The moment a single tenant connects a second mailbox (or disconnects one and reconnects/adds another), every one of these calls is a potential 500/crash. The Phase 10 disconnect tests pass only because they assert against a single seeded row via `findByTenantId` after disconnect; they never exercise the two-CONNECTED-rows path through these callers.

**Fix:**
Decide the contract per call site and stop using a single-result `findByTenantId` for the now-multi-row table. Options:
- For "the active mailbox(es)" reads, return a collection and let callers pick primary:
```java
List<GmailConnectionEntity> findByTenantId(UUID tenantId);
// or, for the common "operate on primary" case:
Optional<GmailConnectionEntity> findByTenantIdAndIsPrimaryTrue(UUID tenantId);
Optional<GmailConnectionEntity> findFirstByTenantIdAndStatusOrderByIsPrimaryDesc(
        UUID tenantId, GmailConnectionStatus status);
```
- Migrate every legacy single-row caller (`currentStatus`, `upsert`, the watch/history mutators, `RecentInboxReadService`, `GmailPreviewReadService`, `InboxBackfillService`, `GmailDeliveryProcessingService`, `GmailAccessGuard`, invalid-grant listener) onto an explicit primary/mailbox-scoped lookup. These are *not* in the Phase 10 file list but the Phase 10 migration is what breaks them, so the schema change must not ship until they are converted (or a compatibility shim that selects the primary row is added).

### CR-02: Access-token cache is never evicted on disconnect or reconnect — stale/cross-grant token reuse

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java:49,154,171`
**Issue:**
`accessTokenCache` is keyed by `gmailConnectionId` and only evicted inside `buildClientForConnection` when a refresh raises `InvalidGrantException` (`:168`). Disconnect and reconnect both mutate the same `gmailConnectionId` row but go through `GmailConnectionService`, which has no reference to the cache:
- `disconnect(MailboxRef)` / `markDisconnected` set status DISCONNECTED and null the ciphertext, but a cached, still-valid access token (TTL up to ~59 min) remains in `accessTokenCache`. Any code holding a `MailboxRef` that calls `buildClientForConnection` directly (it does not re-check status — only `buildClientForMailbox` calls `requireConnectedGrant`) can keep issuing Gmail API calls against a mailbox the user just disconnected/revoked. That contradicts the CASA V13.1.5 disconnect intent the disconnect path is explicitly built to satisfy.
- `reconnect` reuses the same `gmailConnectionId` and writes a *new* refresh token. The cache still holds the access token derived from the *old* grant until its TTL expires, so post-reconnect calls may run on the stale token.

`reconnect` also calls `setLastSyncedHistoryId(null)` etc., but nothing evicts `accessTokenCache.get(gmailConnectionId)`.

**Fix:**
Expose an eviction hook on `GmailApiClientFactory` and call it from `GmailConnectionService` whenever a connection's grant changes:
```java
// GmailApiClientFactory
public void evictAccessToken(UUID gmailConnectionId) {
    accessTokenCache.remove(gmailConnectionId);
}
```
Invoke it in `applyDisconnectedState`/`markDisconnected(MailboxRef)`, `disconnect(UUID)`, and `reconnect(...)` (after the row is updated). Additionally, have `buildClientForConnection` re-verify `status == CONNECTED` before serving a cached token so a stale cache entry can never outlive the grant.

### CR-03: Primary backfill in migration 119 can mark a fully-disconnected tenant's row primary — collides with new active rows later

**File:** `backend/core/src/main/resources/db/changelog/changes/119-gmail-connections-multi-mailbox.yaml:42-51`
**Issue:**
The backfill picks one `is_primary=true` row per tenant via `DISTINCT ON (tenant_id) ... ORDER BY tenant_id, (status='CONNECTED') DESC, connected_at NULLS LAST, id`. For a tenant whose only rows are DISCONNECTED (e.g. user disconnected before the migration), this still sets a DISCONNECTED row to `is_primary=true`. The runtime invariant the code relies on (`promoteNextPrimaryMailbox`, `setPrimary`, `MailboxSummaryProjection.isPrimary`) is "the primary is a CONNECTED mailbox." A DISCONNECTED primary then:
- occupies the `uq_gmail_conn_primary` partial unique slot, so when the tenant later reconnects/adds a CONNECTED mailbox, `addConnection` sets `isPrimary=false` (`GmailConnectionService:422`) and nothing promotes the new CONNECTED row — the tenant is left with a DISCONNECTED "primary" and no CONNECTED primary, and any "operate on primary" path (once CR-01 is fixed to use primary) targets a dead mailbox.

There is also no guard that the backfill leaves a tenant with zero primaries when all rows are non-CONNECTED but you would *prefer* no primary; the current behavior silently promotes a dead row instead.

**Fix:**
Restrict the backfill to CONNECTED rows so disconnected-only tenants get no primary, and let the first reconnect/add promote:
```sql
UPDATE gmail_connections gc SET is_primary = true
WHERE gc.id IN (
  SELECT DISTINCT ON (tenant_id) id
  FROM gmail_connections
  WHERE status = 'CONNECTED'
  ORDER BY tenant_id, connected_at NULLS LAST, id
);
```
Correspondingly, make `addConnection`/`reconnect` promote the row to primary when the tenant currently has no CONNECTED primary (mirror `promoteNextPrimaryMailbox`), otherwise a fresh single-mailbox tenant created via `addConnection` is left with `is_primary=false` and no primary at all (see WR-01).

## Warnings

### WR-01: `addConnection` always sets `isPrimary=false` — a tenant's only/first connected mailbox is never primary

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java:422`
**Issue:**
`addConnection` unconditionally calls `gmailConnection.setPrimary(false)`. If this is the tenant's first/only CONNECTED mailbox (e.g. all prior rows disconnected, or a tenant provisioned purely through the add-mailbox intent), the tenant ends up with zero primary mailboxes. Every "primary" consumer (and the CR-01 fix that routes single-row callers through primary) then finds nothing. The disconnect-driven `promoteNextPrimaryMailbox` only runs when a *primary* is disconnected, so it never repairs this state.
**Fix:** Set primary when the tenant has no existing CONNECTED primary:
```java
boolean tenantHasPrimary = connectionRepository
        .findByTenantIdOrderByIsPrimaryDesc(tenantId).stream()
        .anyMatch(c -> c.isPrimary() && c.getStatus() == GmailConnectionStatus.CONNECTED);
gmailConnection.setPrimary(!tenantHasPrimary);
```

### WR-02: `assertNoActiveDuplicate` TOCTOU race against the partial unique index reports the wrong error class on losers

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java:407,425-427,538-551`
**Issue:**
`addConnection` does a read-time `assertNoActiveDuplicate` then relies on `rethrowDuplicateActiveMailboxIfMatched` to translate the DB constraint violation. The read-check is a pure race window (two concurrent add-mailbox callbacks for the same email both pass the check, both insert). That is correctly caught by the unique index, but `rethrowDuplicateActiveMailboxIfMatched` re-throws the original `DataIntegrityViolationException` for any constraint whose name it cannot extract — and the constraint-name extraction is reflection-based against driver-specific exception shapes (`getConstraintName`/`getServerErrorMessage().getConstraint()`). If the PG driver/exception nesting in Boot 4 does not expose those names, a genuine duplicate surfaces as a generic 500 `error.dataIntegrity` instead of the intended `409 error.gmail.mailbox.duplicate_active`. No test exercises the constraint-violation translation path (only the read-side `assertNoActiveDuplicate` is implicitly covered).
**Fix:** Add a test that forces a real partial-unique-index violation through `addConnection` and asserts `DuplicateActiveMailboxException`. Prefer matching on the SQLState `23505` plus constraint substring in the message chain rather than only reflective getters, so translation does not depend on driver-specific accessor presence.

### WR-03: `setPrimary` clears other primaries then flushes, relying on flush ordering to avoid a unique-index transient conflict

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java:119-139`
**Issue:**
`setPrimary` clears the old primary (`save` + `flush`) and then sets the target primary (`saveAndFlush`). This works only because of the explicit `connectionRepository.flush()` at `:133` between the clear and the set. It is fragile: any future refactor that removes the intermediate flush, or a Hibernate reordering, will attempt to have two `is_primary=true` rows in the same transaction and hit `uq_gmail_conn_primary`. There is also no re-resolution that the target is still CONNECTED after the loop (it was resolved before the loop body). The intent ("exactly one primary") is enforced by a manual two-step instead of an atomic statement.
**Fix:** Make it a single atomic UPDATE pair or a deferred-constraint approach. Simplest robust form:
```java
connectionRepository.clearPrimaryForTenant(tenantId);   // UPDATE ... SET is_primary=false WHERE tenant_id=? 
connectionRepository.flush();
target.setPrimary(true);
connectionRepository.saveAndFlush(target);
```
and keep the partial unique index, but document that the intermediate flush is load-bearing. Better: make `uq_gmail_conn_primary` a `DEFERRABLE INITIALLY DEFERRED` constraint so ordering inside the transaction stops mattering.

### WR-04: Reconnect can re-CONNECT a mailbox whose email now duplicates another active mailbox, surfacing a raw 500

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java:434-465`
**Issue:**
`reconnect` flips the target row back to CONNECTED. If, between disconnect and reconnect, the tenant added another CONNECTED mailbox with the *same* google_email (possible: the disconnected row freed the partial-unique slot, an add created a new CONNECTED row for that address), the `saveAndFlush` violates `uq_gmail_conn_active_email`. `rethrowDuplicateActiveMailboxIfMatched` is applied here too, so it *may* map to 409 — but only if reflective constraint-name extraction works (see WR-02). The reconnect path also never re-checks `assertNoActiveDuplicate` defensively before flipping status.
**Fix:** Call `assertNoActiveDuplicate(tenantId, gmailConnection.getGoogleEmail())` at the top of `reconnect` (excluding the target row itself) for a clean 409, and ensure the DB-violation translation is reliable per WR-02.

### WR-05: `IllegalStateException` from `buildClientForMailbox` / `requireConnectedGrant` is an uncontrolled 500, not a domain error

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java:103-109,178-195`
**Issue:**
When a `MailboxRef` points at a missing or non-CONNECTED mailbox, the factory throws `IllegalStateException` with a message embedding `tenantId` and `gmailConnectionId`. These are technical exceptions: they bypass the `BusinessException` → `ProblemDetail` pipeline (so the client gets a generic 500 and an unlocalized error), and the message string puts tenant/connection UUIDs into a stack trace that may be logged outside the structured-privacy log format. Mailbox-not-found / not-connected are already modeled as `MailboxNotOwnedException` / `MailboxDisconnectedException`.
**Fix:** Throw the existing domain exceptions (or a dedicated gateway exception that maps through the error pipeline) instead of `IllegalStateException`, and keep UUIDs out of free-text messages that land in logs.

### WR-06: `MailboxSummaryResponse` requires `connectedAt`-less variants but `from(...)` can emit null primary/health-consistent rows without a status guard

**File:** `backend/api/src/main/java/com/zeromail/api/dto/gmail/MailboxSummaryResponse.java:8-25`; `backend/core/.../projection/MailboxSummaryProjection.java:19-30`
**Issue:**
The response marks `status`, `isPrimary`, `ingestionHealth` as required and `connectedAt`/`watchExpiresAt`/`lastSyncedHistoryId` as nullable, which is fine. But `listMailboxes` (`GmailConnectionService:142`) returns *all* rows including DISCONNECTED ones (status set, ciphertext null, `is_primary` forced false on disconnect). The summary exposes `lastSyncedHistoryId` and `watchExpiresAt` for disconnected mailboxes — minor info leak of internal sync pointers for a mailbox the user no longer has connected, and no filtering/ordering contract is asserted in a test. Not a privacy BLOCKER (no email body/token), but the API contract for what a DISCONNECTED summary contains is undefined.
**Fix:** Decide and test the DISCONNECTED projection contract (null out watch/sync pointers for non-CONNECTED rows, or document that they are retained), and add a test asserting `listMailboxes` ordering (primary first) and disconnected-row shape.

### WR-07: New `ObjectMapper` allocated per token refresh

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java:224`
**Issue:**
`refreshAccessToken` does `new ObjectMapper()` and `HttpClient.newHttpClient()` on every call. Correctness-wise the `ObjectMapper` allocation is harmless but it is repeated on a hot OAuth path and inconsistent with the rest of the codebase (Jackson 3 / Boot-managed mapper injection). Flagged as quality, not performance scope.
**Fix:** Inject a shared `tools.jackson.databind.ObjectMapper` (or a singleton `JsonMapper`) and a shared `HttpClient` field; reuse across calls.

## Info

### IN-01: `findByGoogleEmailIgnoreCase` is unused dead code

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java:20`
**Issue:** No production or test caller references `findByGoogleEmailIgnoreCase` (grep across `backend/` returns only the declaration). It is also semantically dangerous now that the same email can exist across tenants — a tenant-less lookup invites cross-tenant leakage if someone wires it up later.
**Fix:** Remove it, or scope it `findByTenantIdAndGoogleEmailIgnoreCase` if a lookup is actually needed.

### IN-02: Duplicated single-row vs mailbox-scoped method pairs in `GmailConnectionService`

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java:259-350`
**Issue:** `revokeStoredRefreshToken(UUID)`/`revokeStoredRefreshToken(MailboxRef)` and `tryStopWatch(UUID)`/`tryStopWatch(MailboxRef)` are near-duplicates; the UUID variants additionally suffer from CR-01 (single-row `findByTenantId`). The MailboxRef variant zeroes the decrypted token bytes (`Arrays.fill`, `:299`) while the UUID variant does not (`:266-271`) — an inconsistency in handling of decrypted secrets.
**Fix:** Once CR-01 is resolved, delete the UUID-scoped duplicates in favor of the `MailboxRef` versions, and ensure all decrypted-token byte arrays are zeroed in a `finally`.

### IN-03: `OAuthIntentSnapshot` exposes two names for the same constant

**File:** `backend/api/src/main/java/com/zeromail/api/security/OAuthIntentSnapshot.java:15,18`
**Issue:** `PENDING_INTENT_SESSION_ATTRIBUTE` and `PENDING_SESSION_ATTRIBUTE` are aliases for the same string; tests use the alias while production uses the canonical name. Two public names for one value invites drift.
**Fix:** Keep one constant; update the test (`OAuthIntentRoutingTest`) to reference the canonical `PENDING_INTENT_SESSION_ATTRIBUTE`.

### IN-04: `disconnect(MailboxRef)` early-returns on non-CONNECTED but does not re-null an already-null ciphertext idempotently across statuses

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java:172-178`
**Issue:** `disconnect(MailboxRef)` returns early when status != CONNECTED, which makes the second call a no-op (good, and tested). But for a row stuck in a `PENDING`/`NOT_CONNECTED` state that still holds a ciphertext (shouldn't happen, but the schema allows it), disconnect silently does nothing — no revoke, no ciphertext clear. The `setPrimary`/`resolveOwnedConnectionOrThrow` paths treat those statuses as 409, so a defensive disconnect-as-cleanup is unavailable.
**Fix:** Consider clearing ciphertext + revoking for any non-DISCONNECTED status with a present ciphertext, rather than gating strictly on CONNECTED.

---

_Reviewed: 2026-06-09_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
