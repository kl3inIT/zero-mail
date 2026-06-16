---
phase: 10-gmail-mailbox-foundation-and-account-management
plan: 04
subsystem: gmail
tags: [gmail, mailbox, jpa, service, testcontainers]

requires:
  - phase: 10-02
    provides: multi-mailbox schema, primary column, ownership repository lookup
  - phase: 10-03
    provides: MailboxRef and mailbox-scoped Gmail client factory
provides:
  - Mailbox ownership and reconnectable resolver contract
  - Transactional set-primary behavior
  - Metadata-only mailbox list projection
  - Mailbox-scoped disconnect with primary auto-promote
  - Add/reconnect persistence helpers for OAuth success routing
affects: [gmail, mailbox, oauth, account-management, api, phase-11]

tech-stack:
  added: []
  patterns:
    - Service-owned transactions for mailbox state changes
    - Postgres partial unique indexes backed by friendly BusinessException translation
    - JDBC read-back in integration tests to avoid first-level cache false positives

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/gmail/exception/MailboxNotOwnedException.java
    - backend/core/src/main/java/com/zeromail/core/gmail/exception/MailboxDisconnectedException.java
    - backend/core/src/main/java/com/zeromail/core/gmail/exception/DuplicateActiveMailboxException.java
    - backend/core/src/main/java/com/zeromail/core/gmail/projection/MailboxSummaryProjection.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/shared/error/ErrorCodes.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java
    - backend/core/src/test/java/com/zeromail/core/gmail/usecases/GmailConnectionServiceDisconnectTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/GmailConnectionUniquenessTest.java

key-decisions:
  - "Disconnecting the primary mailbox auto-promotes the next CONNECTED mailbox in the same transaction; if none remain, zero primary is legal."
  - "resolveOwnedConnectionOrThrow is CONNECTED-only, while resolveReconnectableConnectionOrThrow permits every existing mailbox status."
  - "Duplicate active Gmail address handling checks uq_gmail_conn_active_email by constraint name, not by blanket data-integrity catch."

patterns-established:
  - "Mailbox-scoped disconnect uses MailboxRef and does not call the deprecated tenant-only Gmail client lookup."
  - "Primary switch clears existing primary rows, flushes, then sets the target to satisfy the partial unique index ordering."

requirements-completed: [WSP-04, WSP-05, WSP-06, WSP-07, GMA-02, GMA-03, GMA-05, GMA-06, AUD-04]

duration: 23min
completed: 2026-06-09
---

# Phase 10 Plan 04: Mailbox Ownership and State Machine Summary

**Fail-closed mailbox ownership seam with transactional primary switching, metadata summaries, and mailbox-scoped disconnect/add/reconnect helpers**

## Performance

- **Duration:** 23 min
- **Started:** 2026-06-09T05:24:22Z
- **Completed:** 2026-06-09T05:46:59Z
- **Tasks:** 4 completed
- **Files modified:** 8

## Accomplishments

- Added `MailboxNotOwnedException`, `MailboxDisconnectedException`, and `DuplicateActiveMailboxException` as `BusinessException` subtypes mapped through the existing API error handler.
- Added `MailboxSummaryProjection` with metadata-only mailbox fields and no ciphertext/body/prompt data.
- Implemented `resolveOwnedConnectionOrThrow(...)` as a CONNECTED-only ownership guard and `resolveReconnectableConnectionOrThrow(...)` as a separate reconnect guard.
- Implemented transactional `setPrimary(...)` with clear-old, flush, then set-target ordering.
- Implemented `listMailboxes(...)`, duplicate-active pre-checking, and `uq_gmail_conn_active_email` constraint-name translation.
- Implemented `disconnect(MailboxRef)` with stop-watch, token revoke, DB status flip, idempotency, and primary auto-promote.
- Implemented `addConnection(...)` and `reconnect(...)` persistence helpers for Plan 10-05 OAuth success handling.
- Updated the old Gmail uniqueness test to assert the new multi-mailbox invariant.

## Task Commits

Each task was committed atomically:

1. **Task 1: mailbox business errors and projection** - `1191d68d` (feat)
2. **Task 2: ownership resolver, set-primary, list, duplicate seam** - `babc3096` (feat)
3. **Task 3: mailbox-scoped disconnect state machine and tests** - `f0b10cee` (feat)
4. **Task 4: add/reconnect helpers** - `e9e630c0` (feat)

**Plan metadata:** committed with this summary.

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/gmail/exception/MailboxNotOwnedException.java` - 404 business exception for missing/not-owned mailbox ids.
- `backend/core/src/main/java/com/zeromail/core/gmail/exception/MailboxDisconnectedException.java` - 409 business exception for non-CONNECTED usable-mailbox operations.
- `backend/core/src/main/java/com/zeromail/core/gmail/exception/DuplicateActiveMailboxException.java` - 409 business exception for duplicate active Gmail addresses.
- `backend/core/src/main/java/com/zeromail/core/gmail/projection/MailboxSummaryProjection.java` - Metadata-only mailbox list projection.
- `backend/core/src/main/java/com/zeromail/core/shared/error/ErrorCodes.java` - Adds mailbox not-found and duplicate-active dotted codes.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java` - Adds ownership resolvers, primary switch, list, disconnect, add, reconnect, and duplicate constraint mapping.
- `backend/core/src/test/java/com/zeromail/core/gmail/usecases/GmailConnectionServiceDisconnectTest.java` - Adds three mailbox-scoped `disconnect(MailboxRef)` cases.
- `backend/core/src/test/java/com/zeromail/core/gmail/persistence/GmailConnectionUniquenessTest.java` - Updates the stale one-mailbox-per-tenant invariant to the new duplicate-active-email invariant.

## Decisions Made

- Primary-on-disconnect uses A1 auto-promote: if the disconnected row was primary, the next CONNECTED mailbox becomes primary in the same transaction; if none remain, there is no primary.
- `resolveOwnedConnectionOrThrow(...)` throws `MailboxDisconnectedException` for every non-CONNECTED status, not only DISCONNECTED.
- `resolveReconnectableConnectionOrThrow(...)` returns owned rows regardless of status so reconnect can repair DISCONNECTED, PENDING, or NOT_CONNECTED rows.
- Constraint-name extraction is reflective to avoid moving the Postgres driver from runtime-only to main compile scope.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Updated stale one-mailbox uniqueness test**
- **Found during:** Task 2 (set-primary and duplicate seam verification)
- **Issue:** `GmailConnectionUniquenessTest` still asserted `uq_gmail_connections_tenant_id`, which changeset 119 intentionally removed.
- **Fix:** Rewrote the test to allow different active Gmail addresses for the same tenant and reject a duplicate active lowercased address.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/gmail/persistence/GmailConnectionUniquenessTest.java`
- **Verification:** Included in core test compilation and covered by the targeted duplicate-active test run.
- **Committed in:** `babc3096`

**2. [Rule 3 - Blocking] Ordered set-primary updates around the partial unique index**
- **Found during:** Task 4 targeted test run
- **Issue:** The first set-primary implementation could let Hibernate flush the target `is_primary=true` before clearing the old primary, tripping `uq_gmail_conn_primary`.
- **Fix:** Clear existing primary rows, call `flush()`, then set and `saveAndFlush(...)` the target row.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java`
- **Verification:** `SetPrimaryTransactionalTest` passed in the targeted test run.
- **Committed in:** `e9e630c0`

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 blocking).
**Impact on plan:** Both fixes preserve the intended schema and service invariants; no new product scope was added.

## Issues Encountered

- Corrected the Plan 10-03 ArchUnit allow-list owner for `ToneContextBuilder$GmailSentMailSource` in `ef10cc77` after ArchUnit reported the actual inner-class caller.
- Deprecation warnings for legacy `buildClientForTenant` callers remain intentional migration pressure.

## Verification

- `./gradlew :backend:core:compileTestJava` - passed.
- `./gradlew :backend:core:test --tests "*GmailConnectionServiceDisconnect*"` - passed after mailbox-scoped tests landed.
- `./gradlew :backend:core:test --tests "*SetPrimary*" --tests "*GmailConnectionServiceDisconnect*" --tests "*DuplicateActiveEmail*" --tests "*GmailApiClientFactoryMailboxCache*" --tests "*GmailClientLookupBoundary*"` - passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 10-05. OAuth success handling can call `addConnection(...)` and `reconnect(...)` without implementing persistence itself.

---
*Phase: 10-gmail-mailbox-foundation-and-account-management*
*Completed: 2026-06-09*
