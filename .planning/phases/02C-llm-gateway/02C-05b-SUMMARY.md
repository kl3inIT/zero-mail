---
phase: 02C-llm-gateway
plan: 05b
subsystem: llm-gateway
tags: [byok, rest, spring-mvc, configuration-properties, privacy]

requires:
  - phase: 02C-05a
    provides: "BYOK gateway internals, endpoint validator, InvalidByokException, and gateway BYOK routing"
provides:
  - "POST /api/llm/byok/validate, POST /api/llm/byok, and GET /api/llm/byok"
  - "ByokService validate/save/current core service with server-side upstream key probing"
  - "Core BYOK command/result records and API DTO records"
  - "GlobalExceptionHandler mappings for BYOK, sanitization, and LLM safety failures"
  - "Canonical zero-mail configuration namespace with scoped api/worker property binders"
affects: [02C-06, 02C-07, 02C-08, byok-frontend, billing, api-openapi]

tech-stack:
  added: []
  patterns:
    - "Root shared backend properties bind under zero-mail; runnable-specific properties bind under zero-mail.api and zero-mail.worker"
    - "API controllers translate API DTOs to core command records; core services never import api DTO packages"
    - "BYOK save re-runs the same upstream probe used by validate before encrypting and persisting"

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/llm/service/ByokService.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/ByokValidateCommand.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/ByokValidateResult.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/ByokSaveCommand.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/ByokSaveResult.java
    - backend/core/src/main/java/com/zeromail/core/llm/model/ByokCurrent.java
    - backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java
    - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateRequest.java
    - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokValidateResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveRequest.java
    - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokSaveResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/llm/ByokCurrentResponse.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java
    - backend/api/src/main/java/com/zeromail/api/config/ZeroMailApiProperties.java
    - backend/worker/src/main/java/com/zeromail/worker/config/ZeroMailWorkerProperties.java
    - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
    - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
    - backend/api/src/main/resources/application.yml
    - backend/worker/src/main/resources/application.yml

requirements-completed: [LLM-03]

duration: 64min
completed: 2026-05-08
---

# Phase 02C Plan 05b: BYOK REST Surface Summary

**BYOK validate/save/current REST surface is implemented with core-owned commands, server-side re-validation, encrypted persistence, and metadata-only responses.**

## Performance

- **Duration:** 64 min
- **Completed:** 2026-05-08T00:13:43+07:00
- **Tasks:** 1 TDD task plus user-directed configuration namespace correction
- **Files modified:** 46

## Accomplishments

- Added `ByokService` with `validate`, `save`, and `current` methods. `save` validates the endpoint and re-runs the upstream key probe before encrypting with `RefreshTokenCipher` and upserting `tenant_byok_credentials`.
- Added `ByokController` under `/api/llm/byok` with thin DTO-to-core-command mapping for validate/save/current.
- Added API DTO records and core command/result records so `backend/core` has no dependency on `backend/api`.
- Added `GlobalExceptionHandler` mappings for `SafetyViolationException`, `SanitizationException`, and `InvalidByokException`, while preserving the existing `InsufficientCreditsException` 402 mapping.
- Converted the configuration namespace to canonical kebab-case:
  - shared/core: `zero-mail.*`
  - API-only: `zero-mail.api.*`
  - worker-only: `zero-mail.worker.*`
- Removed separate LLM `@ConfigurationProperties` binders and nested LLM platform/BYOK properties under `ZeroMailCoreProperties`.

## Task Commits

1. **RED: BYOK REST tests** - `644ccb6` (`test`)
2. **Docs: subproject config ownership** - `ff94a9b` (`docs`)
3. **GREEN: BYOK REST surface** - `731afa4` (`feat`)

## Decisions Made

- The duplicate root `@ConfigurationProperties(prefix = "zeromail")` pattern was retired instead of suppressing the IDE warning. The final shape uses unique prefixes: `zero-mail`, `zero-mail.api`, and `zero-mail.worker`.
- Spring Boot's kebab-case recommendation is now followed for Zero Mail app configuration keys. Redis namespace `zeromail:session` remains unchanged because it is not a configuration prefix.
- BYOK endpoint host extraction for `current()` uses `URI.create(endpoint).getHost()`. There is no regex in the host extraction path; paths, query strings, and keys are not returned.
- Logback scrub filters were not extended in this plan. The new BYOK service logs only tenant id, provider, models count, and opaque reason tags; it never logs endpoint URLs or key bytes. Existing scrub patterns still cover `Bearer`, `x-api-key`, and `apiKey`-style accidental strings.

## Deviations from Plan

### Auto-fixed Issues

**1. [User correction] Single root core properties binder**
- **Issue:** Separate LLM/BYOK properties classes duplicated the configuration binding shape and conflicted with the user's rule that subprojects own their runtime YAML while shared properties stay under the root core binder.
- **Fix:** Nested LLM platform/BYOK properties inside `ZeroMailCoreProperties`, deleted the separate LLM property classes, and updated injections/tests to use the root binder.
- **Verification:** Focused configuration binding tests passed.

**2. [IDE warning] Duplicate root prefix removed**
- **Issue:** `ZeroMailCoreProperties`, `ZeroMailApiProperties`, and `ZeroMailWorkerProperties` all used the same root prefix.
- **Fix:** Moved API-only properties to `zero-mail.api` and worker-only properties to `zero-mail.worker`.
- **Verification:** JetBrains selected-file build passed with no problems.

## Acceptance Results

- DTO record count: `5`.
- Core BYOK record count: `5`.
- `ByokService` has no `com.zeromail.api` imports.
- `ByokService` contains both core-command signatures: `validate(UUID, ByokValidateCommand)` and `save(UUID, ByokSaveCommand)`.
- Endpoint validator is called from both validate/save paths before outbound HTTP or persistence.
- `save()` re-runs the upstream provider probe before persistence.
- No hardcoded `/v1/models` or `/v1/messages` suffix exists in `ByokService`; URL joining appends only `models` or `messages`.
- `ByokController` exposes three methods under `/api/llm/byok`.
- The three new exception handlers and four LLM error code constants exist.

## Verification

- Passed: `./gradlew.bat --no-daemon --max-workers=1 :backend:core:test --tests "com.zeromail.core.llm.gateway.springai.ZeroMailLlmPropertiesTest" --tests "com.zeromail.core.llm.byok.ZeroMailLlmByokPropertiesBindingTest"`
- Passed: `./gradlew.bat --no-daemon --max-workers=1 :backend:api:test --tests "com.zeromail.api.controllers.GmailPubSubControllerIntegrationTest" --tests "com.zeromail.api.controllers.PubSubIdempotencyTest" --tests "com.zeromail.api.controllers.llm.ByokControllerIntegrationTest"`
- Passed: `./gradlew.bat --no-daemon --max-workers=1 :backend:worker:test --tests "com.zeromail.worker.GmailWatchSchedulerTest"`
- Passed: `./gradlew.bat --no-daemon --max-workers=1 :backend:core:test --tests "ByokServiceTest" :backend:api:test --tests "ByokControllerIntegrationTest"`
- Passed: `./gradlew.bat --no-daemon --max-workers=1 :backend:worker:test`
- Passed: JetBrains selected-file build for modified Java property/controller/service files.
- Failed due environment resource limit: `./gradlew.bat --no-daemon --max-workers=1 :backend:core:test :backend:api:test :backend:worker:test` and standalone `:backend:api:test` both hit API test JVM `OutOfMemoryError`. At retry time the machine had about 2.1 GB free RAM, so raising heap further was not practical.

## Plan 08 Pointer

Regenerate the frontend schema before wiring the BYOK UI:

```shell
pnpm generate:api
```

This should pick up the new `/api/llm/byok/validate`, `/api/llm/byok` save, and `/api/llm/byok` current endpoints.

## Known Stubs

None.

## Threat Flags

None beyond the planned BYOK key/endpoint trust boundary. Tests cover upstream rejection, SSRF endpoint rejection, validate-before-save, encrypted storage, and metadata-only current responses.

## Next Phase Readiness

Ready for Plan 06. BYOK saves now bypass platform LLM cost by design; Plan 06 should wrap credit reserve/settle/release around the platform call path only.

## Self-Check: PASSED

- Verified summary and created files exist on disk.
- Verified task commit `731afa4` exists in git history.

---
*Phase: 02C-llm-gateway*
*Completed: 2026-05-08*
