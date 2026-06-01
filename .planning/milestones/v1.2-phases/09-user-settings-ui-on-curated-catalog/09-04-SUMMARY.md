---
phase: 09-user-settings-ui-on-curated-catalog
plan: 04
subsystem: api
tags: [byok, llm, spring-mvc, postgres, settings]

requires:
  - phase: 09-01
    provides: Phase 9 backend settings schema and user_byok_key persistence foundation
provides:
  - Shared ProviderConnectionTester for admin master keys and user BYOK
  - Stored-row user BYOK lifecycle service and resolver
  - /api/byok endpoints and DTOs for frontend settings consumption
  - /api/settings/ai/cost seven-day tenant cost endpoint
affects: [phase-09-settings-ui, byok, llm-routing, api-schema]

tech-stack:
  added: []
  patterns: [thin-controller-service-owned-transaction, stored-row-byok-lifecycle, enum-only-provider-test-response]

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/llm/byok/ProviderConnectionTester.java
    - backend/core/src/main/java/com/zeromail/core/llm/byok/UserByokService.java
    - backend/core/src/main/java/com/zeromail/core/llm/byok/ByokProviderResolver.java
    - backend/api/src/main/java/com/zeromail/api/controllers/byok/UserByokController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/settings/SettingsAiCostController.java
    - backend/core/src/main/java/com/zeromail/core/llm/cost/AiCostQueryService.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java
    - backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiChatModelFactory.java
    - backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java

key-decisions:
  - "Google user BYOK rejects custom base URLs with ai.byok.base_url_not_supported_for_provider; only the default Google GenAI endpoint is accepted."
  - "POST /api/byok/test-connection is stored-row only and has no request DTO/body contract; users must Save before Test."
  - "Legacy /api/llm/byok remains only as a 410 Gone shim until plan 09-06 deletes the old frontend path."
  - "Non-OK BYOK connection tests drop model IDs at the service boundary, so provider error payloads cannot travel through models[]."

patterns-established:
  - "User BYOK lifecycle is Save -> Test stored row -> Pick model -> Activate; every Save resets active, model, last test result, timestamp, and cached model list."
  - "ByokProviderResolver returns BYOK credentials only when active, model_id is selected, and last_test_result is OK; callers fall back to platform defaults otherwise."
  - "Settings AI cost is a tenant-wide SUM over llm_call_audit metadata only, with a 5-second transactional timeout and no per-feature breakdown."

requirements-completed: [SET-AI-01, SET-AI-02, SET-AI-03, SET-AI-04]

duration: 1 session
completed: 2026-05-27
---

# Phase 09-04: BYOK + Cost API Summary

**Stored-row user BYOK lifecycle with shared provider probing, active-tested-model routing, and tenant-wide seven-day AI cost API**

## Performance

- **Duration:** 1 resumed execution session
- **Completed:** 2026-05-27T04:50:00+07:00
- **Tasks:** 3
- **Files modified:** 47 across task commits

## Accomplishments

- Extracted `ProviderConnectionTester` so admin MKEY tests and user BYOK tests share the same provider probe path, including Anthropic `x-api-key` + `anthropic-version: 2023-06-01` behavior.
- Added `UserByokService` and `ByokProviderResolver` for the locked stored-row BYOK lifecycle, per-call credential resolution, API-key encryption, plaintext scrubbing, model membership validation, and per-tenant test rate limiting.
- Replaced legacy tenant BYOK resolver wiring in `LlmGatewayImpl` and `SpringAiChatModelFactory`; legacy `/api/llm/byok` now returns a 410 shim with `Location: /api/byok`.
- Added `/api/byok` and `/api/settings/ai/cost?window=7d` surfaces with DTO records that expose no plaintext keys or provider error bodies.
- Filled BYOK and cost tests for activation gates, allow-list rejection, base URL/SSRF rejection, plaintext response leak prevention, enum-only test responses, model capping, sentinel model dropping, and seven-day cost aggregation.

## Task Commits

1. **Task 1: Shared provider connection tester** - `fd585da3` (`feat(09-04): extract BYOK provider connection tester`)
2. **Task 2: User BYOK lifecycle resolver** - `367b649e` (`feat(09-04): implement user BYOK lifecycle resolver`)
3. **Task 3: BYOK and AI cost APIs** - `788a3548` (`feat(09-04): add user BYOK and AI cost APIs`)

## Verification

- `./gradlew.bat :backend:core:test --tests ProviderConnectionTesterSingleBindingTest --tests ByokResolutionIntegrationTest --tests ByokSaveResetsStateTest --tests UserByokKeySingleRowPerTenantTest --tests ByokTestConnectionRateLimitTest && ./gradlew.bat :backend:api:compileJava`
- `./gradlew.bat :backend:core:test --tests LlmGatewayByokRoutingTest --tests LlmGatewayCreditLifecycleTest --tests SpringAiChatModelFactoryTest`
- `./gradlew.bat :backend:api:compileTestJava`
- `./gradlew.bat :backend:api:test --tests ByokControllerIntegrationTest`
- `./gradlew.bat :backend:core:test --tests AiCostQueryService7DayTest --tests UserByokTestConnectionSentinelLeakTest`
- `./gradlew.bat :backend:api:test --tests ByokActivateGateModelMissingTest --tests ByokActivateGateNotTestedTest --tests ByokSaveProviderAllowListTest --tests ByokSaveBaseUrlValidationTest --tests ByokSaveSsrfRejectionTest --tests ByokResponseNeverEchoesPlaintextTest --tests ByokTestConnectionEnumOnlyTest --tests SettingsAiCostControllerTest`
- `git diff --check`

## Deviations from Plan

### Auto-fixed Issues

**1. Non-OK model payload dropped in UserByokService**
- **Found during:** Task 3 sentinel test implementation
- **Issue:** The controller hid `models` on non-OK results, but the service returned any model IDs supplied by the tester even when `result != OK`.
- **Fix:** `UserByokService.testConnection` now returns an empty model list for non-OK results and only persists/returns capped models for OK.
- **Verification:** `UserByokTestConnectionSentinelLeakTest`, `ByokTestConnectionEnumOnlyTest`
- **Committed in:** `788a3548`

**2. Controller tests use full API integration harness**
- **Found during:** Task 3 test implementation
- **Issue:** The plan suggested `@WebMvcTest`, but this repo's authenticated tenant endpoints already use `ApiPostgresTestBase` + `TestSessionSupport` so `TenantContext` and session auth match production behavior.
- **Fix:** BYOK controller tests run through the full API harness with only external probe/rate-limit/host resolution mocked.
- **Verification:** Task 3 API test command above
- **Committed in:** `788a3548`

**3. Provider allow-list test keeps service-owned error code**
- **Found during:** Task 3 DTO/controller implementation
- **Issue:** A strict four-provider validation regex would reject `OPENROUTER` as generic validation before `ProviderAllowList` could return `ai.byok.provider_not_allowed`.
- **Fix:** Request validation accepts known router IDs syntactically, while OpenAPI allowable values still expose only the four user BYOK providers; service allow-list remains the defense-in-depth gate.
- **Verification:** `ByokSaveProviderAllowListTest`
- **Committed in:** `788a3548`

**Total deviations:** 3 auto-fixed
**Impact on plan:** All changes tighten the intended API/security contract without adding new product scope.

## Issues Encountered

- JetBrains MCP `read_file`, `get_file_problems`, and build diagnostics timed out after the IntelliJ restart/reindex. Gradle compile/test gates were used as the reliable verification path.
- The first Task 3 commit attempt hit a stale `.git/index.lock`; no git process remained after a short wait, the lock was gone, and the retry succeeded.
- Two unrelated OAuth security tests were dirty before Task 2/3 and remain unstaged/uncommitted.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Backend BYOK and cost APIs are ready for FE consumption in later Phase 9 plans.
- `apps/web` generated API files still need regeneration in the frontend/codegen plan once the backend is running.
- Legacy `/api/llm/byok` and old frontend BYOK form deletion remains owned by plan 09-06.

---
*Phase: 09-user-settings-ui-on-curated-catalog*
*Completed: 2026-05-27*
