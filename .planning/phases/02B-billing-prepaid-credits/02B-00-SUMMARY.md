---
phase: 02B
plan: 00
subsystem: testing
tags: [billing, prepaid-credits, wave-0-red, junit, spring-boot, postgres]

# Dependency graph
requires:
  - phase: 02B-02
    provides: "Billing domain model symbols used by the RED tests that can already compile-link after Plan 02."
provides:
  - "17 Wave 0 RED-by-design billing contract tests across core, api, and worker modules."
  - "Worker PostgresContainerTest visibility widened for sub-package billing tests."
  - "02B validation frontmatter marks Nyquist and Wave 0 completion."
affects: [02B-03-credit-ledger-service, 02B-04-api-surface, 02B-05-worker-schedulers, 02B-06-verification-closure]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Wave 0 contract tests intentionally compile RED until later phase plans land the referenced production symbols."
    - "Cross-package PostgresContainerTest imports are explicit in billing integration tests."

key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/service/SepayApiKeyVerifierTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/service/TopupCodeGeneratorTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/persistence/CreditLedgerEntryUniqueTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookIntegrationTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayReplayTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayBadAuthTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceControllerTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceMultiTenantLeakTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookMismatchAuditEventTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingInsufficientCreditsTest.java
    - backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java
    - backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java
  modified:
    - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
    - .planning/phases/02B-billing-prepaid-credits/02B-VALIDATION.md

key-decisions:
  - "Accepted the Phase 02B-only Wave 0 compile-RED window exactly as documented in the plan; no production stubs were added."
  - "Kept SePay mismatch audit coverage on valid Crockford code ABCD2345 so later implementation tests the amount-mismatch path, not unknown-code handling."

patterns-established:
  - "Future-symbol billing tests stay disabled at runtime but still compile-link against the planned production API."
  - "Worker billing tests extend the parent package test base only after PostgresContainerTest is public."

requirements-completed: [BILL-01, BILL-02, BILL-03, BILL-04, BILL-05, BILL-06, BILL-07]

# Metrics
duration: 13min
completed: 2026-05-06
---

# Phase 02B Plan 00: Wave0 Tests Summary

**Wave 0 RED contract tests for the prepaid credit ledger, SePay webhook, billing balance, and worker reservation cleanup.**

## Performance

- **Duration:** 13 min
- **Started:** 2026-05-06T05:21:59Z
- **Completed:** 2026-05-06T05:34:04Z
- **Tasks:** 4/4
- **Files modified:** 19 plan files plus this summary

## Accomplishments

- Added 17 Wave 0 billing test scaffolds across `backend/core`, `backend/api`, and `backend/worker`.
- Widened worker `PostgresContainerTest` to `public abstract class` so sub-package worker tests can extend it.
- Flipped `02B-VALIDATION.md` to `nyquist_compliant: true` and `wave_0_complete: true`, with all 17 Wave 0 test entries checked.
- Preserved the plan's RED-by-design contract: no production placeholders or stubs were introduced to make the tests compile early.

## Task Commits

Each task was committed atomically:

1. **Task 0: Widen worker PostgresContainerTest** - `8c42fd2` (test)
2. **Task 1: Create core billing RED scaffolds** - `bb8cd33` (test)
3. **Task 2: Create API billing RED scaffolds** - `1542187` (test)
4. **Task 3: Create worker billing RED scaffolds and flip validation** - `da020d0` (test)

## Files Created/Modified

- `backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java` - concurrent reserve contract for exactly 5 successful reservations out of 10.
- `backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java` - settle/release idempotency and illegal transition contract.
- `backend/core/src/test/java/com/zeromail/core/billing/service/SepayApiKeyVerifierTest.java` - API key verifier contract.
- `backend/core/src/test/java/com/zeromail/core/billing/service/TopupCodeGeneratorTest.java` - Crockford top-up code generation contract.
- `backend/core/src/test/java/com/zeromail/core/billing/persistence/CreditLedgerEntryUniqueTest.java` - ledger uniqueness contract.
- `backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java` - CallSite membership and BYOK boundary contract.
- `backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java` - billing Modulith and raw-JdbcTemplate boundary contract.
- `backend/api/src/test/java/com/zeromail/api/controllers/billing/*.java` - SePay webhook, replay, bad auth, balance, tenant isolation, privacy, mismatch audit, and insufficient-credit HTTP contracts.
- `backend/worker/src/test/java/com/zeromail/worker/billing/*.java` - watchdog stale-release and intent-expiry contracts.
- `backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java` - visibility widened to public abstract.
- `.planning/phases/02B-billing-prepaid-credits/02B-VALIDATION.md` - Wave 0 validation status updated.

## Decisions Made

- Followed the plan's explicit `[wave-0-red]` convention for all task commits because these tests intentionally reference classes that land in Plans 03, 04, and 05.
- Left production code untouched except for the worker test-base visibility change declared by the plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed AssertJ ambiguity in concurrent reserve scaffold**
- **Found during:** Task 1 (core billing RED scaffolds)
- **Issue:** `assertThat(ScopedValue.call(...))` produced an ambiguous AssertJ call shape in the scaffold before the expected future-symbol RED errors could be isolated.
- **Fix:** Stored the balance result in an explicit `int availableCredits` local before asserting.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java`
- **Verification:** `./gradlew :backend:core:compileTestJava` then failed only on planned future billing symbols.
- **Committed in:** `bb8cd33`

---

**Total deviations:** 1 auto-fixed (Rule 1 bug)
**Impact on plan:** The fix was local to the RED scaffold and preserved the planned contract shape.

## Verification

- `17` expected Wave 0 test files exist; missing count `0`.
- Every Wave 0 test file contains at least one `@Disabled` annotation.
- All five `PostgresContainerTest` extenders include explicit cross-package imports.
- `.planning/phases/02B-billing-prepaid-credits/02B-VALIDATION.md` has `nyquist_compliant: true` and `wave_0_complete: true`; checked Wave 0 count is `17`.
- `SepayWebhookMismatchAuditEventTest.java` contains the valid `ABCD2345` fixture and `event=sepay_webhook_amount_mismatch`; the old `"MISMATCH"` literal is absent.

Expected RED compile gates:

- `./gradlew :backend:core:compileTestJava` - RED with planned future-symbol errors for billing persistence/service classes, including `CreditLedgerEntryEntity`, `CreditLedgerEntryRepository`, `SepayApiKeyVerifier`, and `TopupCodeGenerator`.
- `./gradlew :backend:api:compileTestJava` - RED with planned future-symbol errors for `api.dto.billing` and `core.billing.persistence` classes, including `SepayWebhookPayload`, `BillingBalanceResponse`, `BillingTopupIntentEntity`, and `BillingTopupIntentRepository`.
- `./gradlew :backend:worker:compileTestJava` - RED with planned future-symbol errors for worker schedulers and billing persistence classes, including `CreditReserveWatchdog`, `BillingIntentExpirySweeper`, `CreditReservationRepository`, and `BillingTopupIntentRepository`.

## Known Stubs

- The Wave 0 tests intentionally reference future production classes from Plans 03, 04, and 05. This is the planned RED contract, not an incomplete implementation stub.
- Stub-pattern scan found only false positives: SLF4J `{}` placeholders in test Javadoc and one nullable authorization-header test fixture branch.

## Authentication Gates

None.

## Issues Encountered

- The repository-local `node_modules/@gsd-build/sdk` CLI path was absent, so GSD SDK operations use the `gsd-sdk` executable on PATH when needed.

## User Setup Required

None - no external service configuration required by this Wave 0 test plan.

## Next Phase Readiness

Ready for Plans 03, 04, and 05 to land the production symbols and flip these contract tests from RED toward GREEN. Plan 06 still owns the final `./gradlew clean check` GREEN gate for Phase 02B.

## Self-Check: PASSED

- All 17 Wave 0 test files, the worker test base, validation file, and this summary exist.
- Task commits found in git history: `8c42fd2`, `bb8cd33`, `1542187`, `da020d0`.
- No tracked file deletions were introduced by the plan commits.

---
*Phase: 02B-billing-prepaid-credits*
*Completed: 2026-05-06*
