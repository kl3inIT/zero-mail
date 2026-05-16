---
phase: 02C-llm-gateway
plan: 06
subsystem: llm-billing
tags: [llm-gateway, credit-ledger, billing, byok, testcontainers, jtokkit]

requires:
  - phase: 02B
    provides: CreditLedger reserve / settle / release lifecycle and HTTP 402 mapping
  - phase: 02C
    provides: LlmGateway platform path, BYOK branch, sanitizer, and action validator
provides:
  - Platform LLM calls are credit-gated through CreditLedger before model invocation
  - BYOK and drift-check calls bypass the credit ledger by construction
  - Gateway credit lifecycle regression tests cover success, safety, sanitization, BYOK, insufficient-credit, concurrency, and drift paths
affects: [phase-03-rules, phase-04-triage, phase-05-ux, billing, observability]

tech-stack:
  added: []
  patterns: [reserve-settle-release gateway lifecycle, lazy jtokkit registry, API test Hikari pool cap]

key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayCreditLifecycleTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
    - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayByokRoutingTest.java
    - backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JtokkitConfig.java
    - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java

key-decisions:
  - "InsufficientCreditsException handling remains rethrow-only after the privacy log line; no metric was added because the existing HTTP 402 mapping is the user-facing contract."
  - "BYOK tenants and driftCheck remain explicitly non-billable; both paths are verified with negative ledger-interaction tests."
  - "JTokkit uses the documented lazy EncodingRegistry so API test contexts do not preload every vocabulary."
  - "API integration tests cap Hikari pools to two connections per cached Spring context to avoid exhausting the singleton Testcontainers Postgres instance."

patterns-established:
  - "Platform LLM calls reserve before model invocation, settle only after parse/validation succeeds, and release on SafetyViolationException or arbitrary RuntimeException."
  - "Gateway settle failures are logged and rethrown without release to avoid double-adjusting a potentially finalized reservation."
  - "Platform-path tests that expect model invocation must seed prepaid credits after Plan 06."

requirements-completed: [LLM-04, LLM-10]

duration: 90 min
completed: 2026-05-08
---

# Phase 02C Plan 06: Credit Cap Wiring Summary

**Credit-gated platform LLM calls with BYOK and drift exemptions proven by gateway lifecycle tests**

## Performance

- **Duration:** 90 min
- **Started:** 2026-05-08T09:00:00+07:00
- **Completed:** 2026-05-08T10:31:00+07:00
- **Tasks:** 1
- **Files modified:** 5

## Accomplishments

- Wrapped the `LlmGatewayImpl.chat()` platform path with `CreditLedger.reserve` before the model call, `settle` on success, and `release` on safety/model failure.
- Preserved the Plan 05a BYOK early return so BYOK calls do not reserve, settle, or release credits.
- Kept `driftCheck()` as a platform-cost operation with no ledger interaction.
- Added the Micrometer `llm_safety_violation_cost_absorbed_total{tenantId}` counter after releasing reservations for safety violations.
- Added `LlmGatewayCreditLifecycleTest` with all 7 planned scenarios, including 100 concurrent calls with 50 successful settlements, 50 releases, and final available balance of 50 from an initial 100 credits.

## Task Commits

1. **Task 1: Wire CreditLedger reserve/settle/release into LlmGatewayImpl platform path + integration test** - `fa5d693` (feat)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` - Adds the platform credit lifecycle wrapper, insufficient-credit privacy log, safety absorption counter, and explicit BYOK/drift ledger-skip comments.
- `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayCreditLifecycleTest.java` - Covers platform success, safety release, pre-reserve sanitization failure, BYOK skip, insufficient-credit block, 100-call concurrency reconciliation, and drift skip.
- `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayByokRoutingTest.java` - Seeds credits for platform-fallback fixtures now that no-BYOK platform calls are billable.
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JtokkitConfig.java` - Switches to the documented lazy registry to avoid loading unused vocabularies in each Spring test context.
- `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java` - Caps test datasource pools so cached API contexts do not exhaust the singleton Postgres container.

## Decisions Made

- Insufficient-credit handling only logs `event=llm_call_blocked_insufficient_credits tenantId={} callSite={}` and rethrows `InsufficientCreditsException`; no balance amount or reservation data is logged.
- `settle()` failure does not call `release()` because a partially successful settle could make release a double-adjustment risk; the failure is logged for operational reconciliation.
- The lazy jtokkit registry is a safe infrastructure fix because only `CL100K_BASE` is requested by the sanitizer and the registry remains cache-backed and thread-safe.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] BYOK routing tests needed funded platform tenants**
- **Found during:** Task 1 verification
- **Issue:** Existing Plan 05 BYOK routing tests expected no-BYOK calls to reach the platform model but did not seed credits. Plan 06 correctly blocks unfunded platform calls with `InsufficientCreditsException`.
- **Fix:** Updated platform-fallback fixtures in `LlmGatewayByokRoutingTest` to seed prepaid credits and clean ledger rows for the fixed tenant.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayByokRoutingTest.java`
- **Verification:** Targeted gateway test suite passed.
- **Committed in:** `fa5d693`

**2. [Rule 3 - Blocking] API contexts exhausted heap while preloading all jtokkit vocabularies**
- **Found during:** Full backend verification
- **Issue:** `:backend:api:test` failed with `OutOfMemoryError: Java heap space` while creating the `encodingRegistry` bean.
- **Fix:** Switched `JtokkitConfig` from `Encodings.newDefaultEncodingRegistry()` to `Encodings.newLazyEncodingRegistry()`, matching current jtokkit docs for loading vocabularies only on first access.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JtokkitConfig.java`
- **Verification:** BYOK controller and global exception-handler API slices passed, then full API tests passed.
- **Committed in:** `fa5d693`

**3. [Rule 3 - Blocking] API tests exhausted Testcontainers Postgres connections**
- **Found during:** Full backend verification after the jtokkit fix
- **Issue:** `:backend:api:test` failed with PostgreSQL `too many clients already` because cached Spring contexts opened too many default-size Hikari pools against one Postgres container.
- **Fix:** Added test-only Hikari `maximum-pool-size=2` and `minimum-idle=0` in `ApiPostgresTestBase`.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java`
- **Verification:** Full API tests and the combined backend command passed.
- **Committed in:** `fa5d693`

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** All fixes were required to make the planned lifecycle behavior testable and to keep existing regression suites valid under the new credit gate. No product scope was added.

## Issues Encountered

- Initial full backend verification exposed environmental/test-fixture limits rather than product regressions: eager tokenizer loading and large cached datasource pools. Both were fixed in production-safe/test-only ways and verified.

## Verification

- `bash` acceptance greps: creditLedger field `1`, reserve `1`, settle `1`, release `2`, safety counter `1`, insufficient-credit log `1`, BYOK/drift comments `2`, Plan 06 markers `0`, `new LlmChatRequest` count `3`, `SystemPrompts.TRIAGE_SYSTEM_PROMPT` count `3`, `platformChatClient` `0`, reservation-id gateway log grep `0`.
- `./gradlew.bat :backend:core:test --tests "LlmGatewayCreditLifecycleTest" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayActionValidatorTest" --tests "LlmGatewayByokRoutingTest" --tests "LlmGatewayMultiTenantLeakTest"` - passed.
- `./gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.llm.ByokControllerIntegrationTest" --tests "com.zeromail.api.error.GlobalExceptionHandlerSafetyTest"` - passed after lazy jtokkit registry fix.
- `./gradlew.bat :backend:api:test` - passed after API test pool cap.
- `./gradlew.bat :backend:worker:test` - passed.
- `./gradlew.bat :backend:core:test :backend:api:test :backend:worker:test` - passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 07 can call `gateway.driftCheck(prompt)` from `DriftDetectionJob`; it uses the platform key path, does not touch the ledger, and returns a validated `ToolCallResult`.
- Plan 08 can render HTTP 402 from platform `chat()` as `errors.llm.insufficientCredits.{title,body}` while BYOK calls remain exempt from prepaid credit charging.

---
*Phase: 02C-llm-gateway*
*Completed: 2026-05-08*
