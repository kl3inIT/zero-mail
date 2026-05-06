---
phase: 02B-billing-prepaid-credits
plan: 02
subsystem: backend-domain-model
tags: [spring-modulith, java-25, billing, credit-ledger, identified-enum]

requires:
  - phase: 01.2-domain-owned-persistence-restructuring
    provides: Per-domain Modulith package shape and lowlevel marker convention.
  - phase: 01.2.1-shared-base-entity-and-enum-standard
    provides: IdentifiedEnum and records/classes domain conventions.
provides:
  - core.billing Modulith leaf package declaration
  - CreditLedger cross-phase interface for Phase 2C
  - Billing call-site and lifecycle enums
  - ReservationId and CreditBalance value records
  - Billing exception types for later API mappings
affects: [02B-03-credit-ledger-service, 02B-04-api-surface, 02C-llm-gateway]

tech-stack:
  added: []
  patterns:
    - Spring Modulith leaf package with explicit allowedDependencies
    - IdentifiedEnum fromId fail-loud enums with domain-revealing lambdas
    - Model-package interface as cross-phase contract

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/billing/package-info.java
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/package-info.java
    - backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java
    - backend/core/src/main/java/com/zeromail/core/billing/model/CreditReservationStatus.java
    - backend/core/src/main/java/com/zeromail/core/billing/model/BillingTopupIntentStatus.java
    - backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java
    - backend/core/src/main/java/com/zeromail/core/billing/model/ReservationId.java
    - backend/core/src/main/java/com/zeromail/core/billing/model/CreditBalance.java
    - backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java
    - backend/core/src/main/java/com/zeromail/core/billing/model/IllegalLedgerStateException.java
  modified: []

key-decisions:
  - "CreditLedger lives in core.billing.model so Phase 2C can depend on the public model surface without crossing into service implementation."
  - "core.billing allowedDependencies are tenant, shared.persistence, and shared.lang only; the privacy module is not referenced by dependency literal or prose."
  - "BYOK remains a gateway-side skip in Phase 2C; CallSite has no BYOK member."

patterns-established:
  - "Billing enums implement IdentifiedEnum and use fromId lambdas named callSite/status/intentStatus."
  - "InsufficientCreditsException carries no balance data; API mapping in Plan 04 owns the 402 response."

requirements-completed: [BILL-02, BILL-03, BILL-06, BILL-07]

duration: 8min
completed: 2026-05-06
---

# Phase 02B Plan 02: Domain Model Summary

**Billing model contract with a Modulith leaf package, locked call-site costs, reservation handles, balance projection, and Phase 2C BYOK boundary Javadoc.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-05-06T05:19:53Z
- **Completed:** 2026-05-06T05:27:27Z
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments

- Created `core.billing` as a Spring Modulith leaf with only `tenant`, `shared.persistence`, and `shared.lang` dependencies.
- Added the public billing model surface: `CreditLedger`, `CallSite`, reservation/intent statuses, records, and exceptions.
- Documented the Phase 2C reserve/settle/release lifecycle and the explicit BYOK ledger-bypass contract.

## Task Commits

1. **Task 1: Modulith package-info declarations + sub-package marker** - `0d0f280` (`feat`)
2. **Task 2: Domain enums + value records + exceptions in core.billing.model** - `4c01bf2` (`feat`)
3. **Task 3: CreditLedger interface + BYOK exemption Javadoc** - `7e93efa` (`feat`)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/billing/package-info.java` - Billing Modulith leaf declaration and package-level contract notes.
- `backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/package-info.java` - Native-SQL allowlist marker for Plan 03 advisory-lock helper.
- `backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java` - Locked billable call sites `TRIAGE(1)`, `DRAFT(2)`, `PREVIEW(1)`.
- `backend/core/src/main/java/com/zeromail/core/billing/model/CreditReservationStatus.java` - Reservation lifecycle enum.
- `backend/core/src/main/java/com/zeromail/core/billing/model/BillingTopupIntentStatus.java` - SePay top-up intent lifecycle enum.
- `backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java` - Cross-phase ledger interface for reserve, settle, release, and balance.
- `backend/core/src/main/java/com/zeromail/core/billing/model/ReservationId.java` - UUID wrapper for reservation handles.
- `backend/core/src/main/java/com/zeromail/core/billing/model/CreditBalance.java` - Available/held credits projection.
- `backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java` - Privacy-safe insufficient balance exception.
- `backend/core/src/main/java/com/zeromail/core/billing/model/IllegalLedgerStateException.java` - Forbidden ledger transition exception.

## Decisions Made

- Followed the plan's model-package placement so Phase 2C imports only public domain contracts.
- Kept BYOK out of `CallSite`; Phase 2C owns the skip before calling `CreditLedger.reserve`.
- Avoided the literal forbidden privacy-module dependency string in `billing/package-info.java` so the plan verifier's negative grep remains durable.

## Deviations from Plan

None - plan executed exactly as written.

**Total deviations:** 0 auto-fixed.
**Impact on plan:** No scope expansion.

## Issues Encountered

- Initial `:backend:core:compileJava` hit a transient Spring configuration metadata processor error (`End of input`) before touching billing model symbols. A rerun succeeded without code changes.
- `:backend:core:check` currently fails because the concurrently committed `02B-00` Wave 0 RED tests reference Plan 03 service/persistence classes (`CreditLedgerEntryEntity`, `CreditLedgerEntryRepository`, `SepayApiKeyVerifier`, `TopupCodeGenerator`) that intentionally do not exist yet. This is outside 02B-02's write scope and matches the Phase 02B RED-window convention documented in `02B-REVIEWS.md`.

## Verification

- `PLAN_FILE_CHECKS_PASS` - all 10 declared files exist and locked strings/signatures are present.
- `.\gradlew.bat :backend:core:compileJava` - BUILD SUCCESSFUL.
- `.\gradlew.bat :backend:core:check` - FAILS in `compileTestJava` on external Wave 0 RED tests from 02B-00, not this plan's production sources.

## Known Stubs

None. Stub scan only matched the intentional log-format text `tenantId={}` in `CreditLedger` Javadoc.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for `02B-03-credit-ledger-service`: the model package and `CreditLedger` interface compile, and the lowlevel marker package is in place for the advisory-lock helper.

## Self-Check: PASSED

- All 10 created source files and this summary file exist on disk.
- Task commits `0d0f280`, `4c01bf2`, and `7e93efa` exist in git history.
- Verification caveat is documented: `:backend:core:check` is blocked by external Wave 0 RED tests from `02B-00`; `:backend:core:compileJava` is green for this plan's production sources.

---
*Phase: 02B-billing-prepaid-credits*
*Completed: 2026-05-06*
