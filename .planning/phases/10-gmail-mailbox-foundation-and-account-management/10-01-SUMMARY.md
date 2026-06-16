---
phase: 10-gmail-mailbox-foundation-and-account-management
plan: 01
subsystem: testing
tags: [spring-boot, postgres, liquibase, oauth2, gmail, mailbox]
requires:
  - phase: 10-gmail-mailbox-foundation-and-account-management
    provides: Phase 10 context, validation strategy, and mailbox migration research
provides:
  - Wave 0 RED validation scaffolds for migration, mailbox ownership, OAuth intent routing, token-cache isolation, and primary mailbox behavior
  - Shared old-single-account Gmail fixture with AES-GCM refresh-token ciphertext
affects: [phase-10, phase-11, gmail, oauth, mailbox-scoping]
tech-stack:
  added: []
  patterns: [PostgresContainerTest migration guard, RestClient LocalServerPort ownership guard, OAuth authorization-request session shim test]
key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/gmail/support/OldSingleAccountFixture.java
    - backend/core/src/test/java/com/zeromail/core/gmail/migration/Migration119Test.java
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/DuplicateActiveEmailTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/SetPrimaryTransactionalTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/RefreshTokenCipherContinuityTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/gateway/GmailApiClientFactoryMailboxCacheTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/gmail/MailboxOwnershipSeamTest.java
    - backend/api/src/test/java/com/zeromail/api/security/OAuthIntentRoutingTest.java
    - backend/api/src/test/java/com/zeromail/api/security/IntentCarryingRepositoryTest.java
  modified: []
key-decisions:
  - "Migration119Test uses an isolated scratch schema and executes changeset 119 forward SQL so the old single-account row is truly seeded before the migration under test."
  - "OAuth RED tests pin server-side session snapshots as the only add/reconnect branch authority; URL intent parameters must not drive branch selection."
patterns-established:
  - "Wave 0 mailbox tests may compile-fail only on planned future symbols; accidental dependency failures are fixed immediately."
requirements-completed: [VER-01, WSP-02, GMA-03, GMA-06, WSP-05, WSP-06, GMA-05, GMA-07, AUD-04]
duration: 42 min
completed: 2026-06-09
---

# Phase 10 Plan 01: Wave 0 Validation Spine Summary

**RED mailbox-foundation tests for migration safety, token-cache isolation, ownership fail-closed behavior, and OAuth intent routing**

## Performance

- **Duration:** 42 min
- **Started:** 2026-06-09T04:15:00Z
- **Completed:** 2026-06-09T04:57:00Z
- **Tasks:** 3
- **Files modified:** 9 created

## Accomplishments

- Added the old single-account Gmail fixture with AES-GCM ciphertext generated under tenant-based AAD.
- Added migration/persistence tests for changeset 119, duplicate-active mailbox rejection, primary uniqueness, and cipher continuity.
- Added the mailbox token-cache isolation test that fails if mailbox B reuses mailbox A's tenant-keyed cached token.
- Added API/security RED tests for mailbox ownership 404/409, reconnect reachability, server-side OAuth intent snapshots, stale-intent cleanup, and Redis-dirty session persistence.

## Task Commits

1. **Task 1: Old-single-account fixture + migration/persistence scaffolds** - `731ddc14` (`test`)
2. **Task 2: Factory cache-isolation unit test scaffold** - `bd806226` (`test`)
3. **Task 3: API-tier OAuth + ownership scaffolds** - `7d1907fa` (`test`)

**Plan metadata:** this SUMMARY commit.

## Files Created/Modified

- `backend/core/src/test/java/com/zeromail/core/gmail/support/OldSingleAccountFixture.java` - reusable tenant/Gmail row fixture with known refresh-token plaintext and ciphertext.
- `backend/core/src/test/java/com/zeromail/core/gmail/migration/Migration119Test.java` - scratch-schema migration assertion for backfill and ciphertext byte identity.
- `backend/core/src/test/java/com/zeromail/core/gmail/persistence/DuplicateActiveEmailTest.java` - partial duplicate-active-email index and multi-mailbox coexistence guard.
- `backend/core/src/test/java/com/zeromail/core/gmail/persistence/SetPrimaryTransactionalTest.java` - primary uniqueness and future `setPrimary` transactional contract.
- `backend/core/src/test/java/com/zeromail/core/gmail/persistence/RefreshTokenCipherContinuityTest.java` - D-11 tenant-AAD continuity guard.
- `backend/core/src/test/java/com/zeromail/core/gmail/gateway/GmailApiClientFactoryMailboxCacheTest.java` - D-10 cache-key isolation guard for `MailboxRef`.
- `backend/api/src/test/java/com/zeromail/api/controllers/gmail/MailboxOwnershipSeamTest.java` - RestClient ownership state matrix for mailbox endpoints.
- `backend/api/src/test/java/com/zeromail/api/security/OAuthIntentRoutingTest.java` - resolver/success/failure stale-intent and branch-authority tests.
- `backend/api/src/test/java/com/zeromail/api/security/IntentCarryingRepositoryTest.java` - authorization-request repository session-copy test.

## Decisions Made

- `Migration119Test` applies changeset 119 SQL in an isolated scratch schema instead of trying to mutate the already-migrated shared `public` schema. This keeps the test faithful to "seed old shape, then migrate" while preserving the shared Testcontainers context.
- API ownership tests use `RestClient + @LocalServerPort` and the existing `TestSessionSupport` pattern, not MockMvc, so servlet-filter tenant binding is exercised.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Removed direct `org.postgresql.util.PSQLException` dependency**
- **Found during:** Task 1 compile gate
- **Issue:** `org.postgresql.util.PSQLException` is not exposed on the test compile classpath, producing an unintended compile failure unrelated to Phase 10 future symbols.
- **Fix:** Asserted on the root-cause message without importing the concrete Postgres driver exception type.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/gmail/persistence/DuplicateActiveEmailTest.java`
- **Verification:** `./gradlew :backend:core:compileTestJava` then failed only on the planned future `GmailConnectionService.setPrimary(UUID, UUID)` method.
- **Committed in:** `731ddc14`

---

**Total deviations:** 1 auto-fixed (blocking compile hygiene).
**Impact on plan:** No scope change; the RED surface is now limited to planned Phase 10 symbols.

## Issues Encountered

- `./gradlew :backend:core:compileTestJava` currently fails on planned future symbols: `findByIdAndTenantId`, `MailboxRef`, `buildClientForMailbox`, and `setPrimary`.
- `./gradlew :backend:api:compileTestJava` currently fails on planned future symbols: `OAuthIntentSnapshot` and `IntentCarryingAuthorizationRequestRepository`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 02 can now land changeset 119, entity fields, and repository ownership lookup against existing RED tests. Plans 03-06 must turn the remaining RED symbols green without weakening these assertions.

## Self-Check: PASSED

- All 8 test classes plus 1 shared fixture were created at declared paths.
- Acceptance greps confirmed `OldSingleAccountFixture`, `is_primary`, `uq_gmail_conn_active_email`, `uq_gmail_conn_primary`, `setPrimary`, `MailboxRef`, and `buildClientForMailbox` references.
- No production source under `backend/*/src/main` was modified by this plan.

---
*Phase: 10-gmail-mailbox-foundation-and-account-management*
*Completed: 2026-06-09*
