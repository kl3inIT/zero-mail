---
phase: 04-triage-convergence-hero
plan: 06
subsystem: api
tags: [spring-boot, springdoc-openapi, nextjs, i18n, gmail-triage, safety]

requires:
  - phase: 04-triage-convergence-hero
    provides: "04-00/04-02/04-04 triage audit persistence, shadow-mode storage, Gmail writer, and sender safety-net services"
provides:
  - "Tenant-scoped triage audit undo service with inverse Gmail writes and 30-day enforcement"
  - "REST endpoints for undo, tenant shadow-mode, and sender safety-net opt-in/listing"
  - "Triage error codes, ProblemDetail mappings, vi/en i18n keys, OpenAPI spec, and generated TypeScript schema"
affects: [phase-05-triage-ui, frontend-api-client, gmail-write-safety]

tech-stack:
  added: []
  patterns:
    - "Thin Spring MVC controllers resolving TenantContext and delegating to core services"
    - "Generated i18n bundles from apps/web/features/**/messages.ts"
    - "Springdoc hermetic OpenAPI emit with Windows-safe port"

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/triage/application/TriageUndoService.java
    - backend/core/src/main/java/com/zeromail/core/triage/application/UndoAuditCommand.java
    - backend/core/src/main/java/com/zeromail/core/triage/application/UndoAuditResult.java
    - backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageTenantController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/triage/SenderSafetyNetController.java
    - backend/api/src/main/java/com/zeromail/api/dto/triage/package-info.java
    - backend/api/src/main/java/com/zeromail/api/dto/triage/UndoAuditResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/triage/TriageShadowModeRequest.java
    - backend/api/src/main/java/com/zeromail/api/dto/triage/TriageShadowModeResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/triage/ProtectedSendersResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/triage/SenderOptInResponse.java
    - apps/web/features/triage/messages.ts
  modified:
    - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
    - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
    - backend/api/build.gradle.kts
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json
    - apps/web/openapi/openapi.json
    - apps/web/lib/api/schema.d.ts

key-decisions:
  - "Use error.triage.* dotted codes with generated errors.triage.* frontend messages."
  - "Move the springdoc emit port from 59080 to 59280 because 59080 is inside this Windows TCP excluded range."

patterns-established:
  - "Undo endpoints return generic ProblemDetail detail while preserving machine-readable triage error codes."
  - "Sender opt-in logs only senderEmailHash while the response echoes the canonicalized address."

requirements-completed: [TRG-06, TRG-07, TRG-08]

duration: 24min
completed: 2026-05-11
---

# Phase 04 Plan 06: Triage REST Undo Surface Summary

**Tenant-scoped Gmail triage undo plus REST endpoints for undo, shadow-mode, and sender safety-net controls**

## Performance

- **Duration:** 24 min
- **Started:** 2026-05-11T13:00:00Z
- **Completed:** 2026-05-11T13:24:00Z
- **Tasks:** 2
- **Files modified:** 22

## Accomplishments

- Added `TriageUndoService` with tenant ownership, APPLIED-state, 30-day undo window, inverse Gmail writer calls, `markReverted`, and privacy-safe logging.
- Added thin API controllers for `POST /api/triage/audit/{auditId}/undo`, `PATCH /api/tenant/triage/shadow-mode`, `GET /api/triage/sender-safety-net`, and `POST /api/triage/sender-safety-net/{senderEmail}/opt-in`.
- Added triage DTO records, error codes, `GlobalExceptionHandler` mappings, vi/en messages, regenerated OpenAPI JSON, and regenerated `schema.d.ts`.

## Task Commits

1. **Task 1: TriageUndoService compute-inverse + flip-decision** - `eab99d5` (feat)
2. **Task 2: Three thin triage controllers + DTOs + ErrorCodes + handlers** - `2beabc7` (feat)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/triage/application/TriageUndoService.java` - undo orchestration, inverse Gmail writes, audit transition, metrics/logging.
- `backend/core/src/main/java/com/zeromail/core/triage/application/UndoAuditCommand.java` - tenant-scoped undo command.
- `backend/core/src/main/java/com/zeromail/core/triage/application/UndoAuditResult.java` - undo result for API mapping.
- `backend/core/src/main/java/com/zeromail/core/triage/exception/TriageAuditNotFoundException.java` - non-leaking not-found signal for missing or cross-tenant audit ids.
- `backend/core/src/main/java/com/zeromail/core/triage/exception/TriageUndoWriteFailedException.java` - retryable inverse-write failure signal.
- `backend/api/src/main/java/com/zeromail/api/controllers/triage/*.java` - triage undo, shadow-mode, and sender safety-net endpoints.
- `backend/api/src/main/java/com/zeromail/api/dto/triage/*.java` - REST request/response DTO records and package interface.
- `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java` - triage undo and safety error codes.
- `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` - triage exception to `ProblemDetail` mappings.
- `backend/api/build.gradle.kts` - OpenAPI emit port moved outside the local Windows excluded TCP range.
- `apps/web/features/triage/messages.ts` - source triage error messages.
- `apps/web/i18n/messages/{vi,en}.json` - generated i18n bundles.
- `apps/web/openapi/openapi.json` and `apps/web/lib/api/schema.d.ts` - regenerated OpenAPI and typed client schema.

## Decisions Made

- Used `/api/...` method mappings for new controllers to match the existing rules/billing/llm API controllers and generated schema.
- Kept sender email privacy split: response returns the canonicalized sender email, logs use only the canonical hash.
- Moved OpenAPI generation from port `59080` to `59280` because Windows reserved `59049-59148` on this machine.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Moved springdoc OpenAPI emit port**
- **Found during:** Task 2 (OpenAPI/schema regeneration)
- **Issue:** `:backend:api:generateOpenApiDocs` repeatedly failed because port `59080` is inside the local Windows TCP excluded range `59049-59148`.
- **Fix:** Updated `backend/api/build.gradle.kts` to use port `59280` for the hermetic springdoc emitter and matching `apiDocsUrl`.
- **Verification:** `.\gradlew.bat :backend:api:generateOpenApiDocs --console=plain` passed; `pnpm generate:api` regenerated `apps/web/lib/api/schema.d.ts`.
- **Committed in:** `2beabc7`

---

**Total deviations:** 1 auto-fixed (Rule 3 blocking)
**Impact on plan:** No product scope change. The fix makes the existing codegen path runnable on this Windows checkout.

## Issues Encountered

- First OpenAPI generation attempt failed before serving docs because the forked app started before `backend:core:jar` was available for a billing security class. Retrying after the jar existed progressed to the actual port conflict.
- The generated i18n bundles still include pre-existing legal-page placeholder copy and BYOK placeholder labels from earlier phases. No 04-06 triage endpoint or DTO is stubbed.

## Known Stubs

- `apps/web/i18n/messages/en.json:354` and `apps/web/i18n/messages/vi.json:354` contain pre-existing generated legal-page placeholder body copy. Not introduced by this plan.
- `apps/web/i18n/messages/en.json:359` and `apps/web/i18n/messages/vi.json:359` contain pre-existing generated terms-page placeholder body copy. Not introduced by this plan.

## Threat Flags

None - new network endpoints and sender-email logging behavior were already covered by the plan threat model.

## User Setup Required

None - no external service configuration required.

## Verification

- `.\gradlew.bat :backend:core:compileJava --console=plain` - passed before Task 1 commit.
- `.\gradlew.bat :backend:core:test --tests "*TriageUndoServiceContractTest" --console=plain` - passed before Task 1 commit and again during final verification.
- `.\gradlew.bat :backend:api:compileJava --console=plain` - passed.
- `.\gradlew.bat :backend:api:test --tests "*TriageUndoControllerContractTest" --tests "*TriageTenantControllerContractTest" --tests "*SenderSafetyNetControllerContractTest" --console=plain` - passed.
- `.\gradlew.bat :backend:core:compileJava :backend:api:compileJava --console=plain` - passed.
- `.\gradlew.bat :backend:api:generateOpenApiDocs --console=plain` - passed after the port fix.
- `pnpm generate:api` from `apps/web` - passed.
- `pnpm i18n:check` from `apps/web` - passed.
- `rg "triage/audit|sender-safety-net|triage/shadow-mode" apps/web/lib/api/schema.d.ts` - confirmed all new endpoints are present.

## Next Phase Readiness

Phase 5 can consume the generated typed API client for triage undo, shadow-mode, and sender safety-net UI work. The undo path preserves Gmail safety: only label/archive/draft inverse writes are exposed, and unsupported actions fail loud.

## Self-Check: PASSED

- Confirmed summary, undo service, three controllers, and generated schema files exist.
- Confirmed task commits `eab99d5` and `2beabc7` exist in git history.

---
*Phase: 04-triage-convergence-hero*
*Completed: 2026-05-11*
