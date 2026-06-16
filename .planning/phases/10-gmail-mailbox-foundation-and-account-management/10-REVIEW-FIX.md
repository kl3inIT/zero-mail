---
phase: 10-gmail-mailbox-foundation-and-account-management
fixed_at: 2026-06-09T00:00:00Z
review_path: .planning/phases/10-gmail-mailbox-foundation-and-account-management/10-REVIEW.md
iteration: 1
findings_in_scope: 10
fixed: 10
skipped: 0
status: all_fixed
---

# Phase 10: Code Review Fix Report

**Fixed at:** 2026-06-09
**Source review:** .planning/phases/10-gmail-mailbox-foundation-and-account-management/10-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 10 (3 critical + 7 warning; Info findings excluded by `critical_warning` scope)
- Fixed: 10
- Skipped: 0

> Verification note: this repo's Java does not have a fast per-file syntax checker available in
> the isolated worktree (full Gradle compile is prohibitively slow on a fresh 3500-file worktree,
> and JetBrains MCP indexes the main project, not the worktree). Java fixes were verified via
> Tier-1 re-read of the edited regions plus careful inspection of imports/braces. The YAML
> migration fix was verified with a real YAML parse (Tier 2). Logic-sensitive fixes are flagged
> "requires human verification" below — run `./gradlew :backend:core:test` / boot the API before
> the phase proceeds to the verifier.

## Fixed Issues

### CR-01: `findByTenantId` returns `Optional` but multiple rows per tenant are now legal

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java`
**Commit:** 1b70358d
**Status:** fixed: requires human verification (logic-sensitive — changes which row legacy mutators target)
**Applied fix:** Took the reviewer's "compatibility shim that selects the primary row" option rather
than rewriting 8+ out-of-phase call sites. `findByTenantId` is now a `default` method backed by a
new ordered query `findPrimaryMailboxCandidatesByTenantId(tenantId, Limit.of(1))` that returns at
most one row, ordered primary-first, then CONNECTED, then most-recently connected, then by id. Every
legacy single-row caller (`currentStatus`, `upsert`, the watch/history mutators, `RecentInboxReadService`,
`GmailPreviewReadService`, `InboxBackfillService`, `GmailDeliveryProcessingService`, `GmailAccessGuard`,
invalid-grant listener) now deterministically operates on the tenant's primary/active mailbox instead
of throwing `NonUniqueResultException`.
**Human-verify focus:** confirm the "operate on primary" semantics are correct for each legacy mutator,
and that account-deletion revocation of NON-primary mailbox grants is acceptable as-is (see Notes).

### CR-02: Access-token cache never evicted on disconnect or reconnect

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java`, `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java`
**Commit:** 1fdba61f
**Applied fix:** Added `GmailApiClientFactory.evictAccessToken(UUID)` and invoked it from
`applyDisconnectedState` (covers both `disconnect(UUID)` and `disconnect(MailboxRef)` paths) and at
the end of `reconnect`. `buildClientForConnection` now re-verifies `status == CONNECTED` (and evicts)
before serving a cached token, so a stale cache entry can never outlive the grant.

### CR-03: Migration 119 primary backfill can mark a fully-disconnected tenant's row primary

**Files modified:** `backend/core/src/main/resources/db/changelog/changes/119-gmail-connections-multi-mailbox.yaml`
**Commit:** e0270a5e
**Applied fix:** Restricted the backfill `DISTINCT ON` to `WHERE status = 'CONNECTED'` so
disconnected-only tenants get no primary (no dead row occupying `uq_gmail_conn_primary`). The
promote-on-add/reconnect half of this finding is implemented in WR-01's commit. Verified the
changeset `119-gmail-connections-multi-mailbox` is NOT yet in the dev `DATABASECHANGELOG` (count 0
via psql), so editing it does not violate changelog-immutability convention 10.

### WR-01: `addConnection` always sets `isPrimary=false`

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java`
**Commit:** 72d82ec0
**Status:** fixed: requires human verification (primary-state logic)
**Applied fix:** Added `tenantHasConnectedPrimary(tenantId)` helper. `addConnection` now sets
`isPrimary = !tenantHasConnectedPrimary(...)`; `reconnect` promotes the reconnected mailbox to
primary when no CONNECTED primary exists (also satisfies CR-03's promote-on-reconnect requirement).

### WR-02: `assertNoActiveDuplicate` TOCTOU translation reports wrong error class on losers

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java`
**Commit:** df486c19
**Applied fix:** Replaced reflection-only constraint matching with `matchesDuplicateActiveEmailConstraint`,
which additionally accepts a SQLState `23505` (unique_violation) whose message text mentions the
partial-unique index name. Either signal now maps to the 409 `DuplicateActiveMailboxException`, so
translation no longer depends on driver-specific accessor presence.
**Follow-up (not blocking):** the reviewer also recommended adding a test that forces a real
partial-unique-index violation through `addConnection`. The code fix is in; a dedicated DB-backed
test was not added in this pass.

### WR-03: `setPrimary` relies on flush ordering to avoid a transient unique-index conflict

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java`, `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java`
**Commit:** 4a2ae82a
**Applied fix:** Added `clearPrimaryForTenantExcept(tenantId, keepMailboxId)` bulk `@Modifying`
UPDATE. `setPrimary` now clears all other primaries via that single statement before promoting the
target, removing the dependency on a load-bearing intermediate `flush()`.

### WR-04: Reconnect can re-CONNECT a mailbox whose email now duplicates another active mailbox

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java`
**Commit:** 72d82ec0
**Applied fix:** Added `assertNoActiveDuplicateExcludingTarget(tenantId, googleEmail, excludedMailboxId)`
and called it at the top of `reconnect` (excluding the target row) for a clean 409 instead of a raw
500. Reliable DB-violation translation is covered by WR-02.

### WR-05: `IllegalStateException` from gateway is an uncontrolled 500

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java`
**Commit:** e4f2cca5
**Applied fix:** `buildClientForMailbox` now throws `MailboxNotOwnedException` (404) when the row is
missing; `requireConnectedGrant` and the `buildClientForConnection` stale-cache guard throw
`MailboxDisconnectedException` (409). Both map through the `BusinessException -> ProblemDetail`
pipeline, replacing the unmapped 500s and free-text UUID stack traces.

### WR-06: `MailboxSummaryResponse` exposes sync pointers for disconnected mailboxes

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/projection/MailboxSummaryProjection.java`
**Commit:** 341e10b8
**Applied fix:** `MailboxSummaryProjection.from` now nulls `watchExpiresAt` and `lastSyncedHistoryId`
for non-CONNECTED rows, so the DISCONNECTED summary carries no stale internal watch/sync state.

### WR-07: New `ObjectMapper` allocated per token refresh

**Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java`
**Commit:** e4f2cca5
**Applied fix:** Hoisted `ObjectMapper` and `HttpClient` to shared, thread-safe instance fields and
reused them in `refreshAccessToken` instead of allocating per call.

## Skipped Issues

None — all in-scope findings were fixed.

(Info findings IN-01 through IN-04 were out of the `critical_warning` fix scope and were not
attempted. Note: CR-01's repository edit briefly removed and then restored `findByGoogleEmailIgnoreCase`
to keep IN-01 out of scope — the method is preserved unchanged.)

## Notes / residual risk for the developer

- **CR-01 account-deletion revocation gap (pre-existing, not introduced here):** `revokeGrantForCurrentTenant`
  / `revokeStoredRefreshToken(UUID)` / `tryStopWatch(UUID)` still operate on the single primary row via
  the shim, so on account deletion the Google-side grants of NON-primary mailboxes are not revoked at
  Google (the DB rows are still wiped by `TenantDataDeletionService`). Revoking ALL mailbox grants on
  deletion is a deeper multi-row change beyond the crash fix; flagged for a follow-up if CASA V13.1.5
  requires per-mailbox revocation on full-account deletion.
- **CR-01 / WR-01 are logic-sensitive** ("operate on primary" + primary-promotion). Tier 1/2 verify
  structure, not semantics. Run `:backend:core:test` and exercise the two-CONNECTED-mailboxes path
  before the verifier phase.
- **WR-02 test gap:** the constraint-translation code fix is in, but a DB-backed test forcing the
  partial-unique violation was not added.

---

_Fixed: 2026-06-09_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
