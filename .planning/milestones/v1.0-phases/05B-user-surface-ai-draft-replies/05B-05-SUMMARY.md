---
phase: 05B-user-surface-ai-draft-replies
plan: 05
subsystem: api
tags: [spring-mvc, springdoc, gmail-api, nextjs, openapi, i18n]

requires:
  - phase: 05B-03
    provides: On-demand draft generation service and draft safety exceptions.
  - phase: 05B-04
    provides: Audit and needs-reply keyset projection services.
provides:
  - Cursor-paginated triage audit REST endpoint.
  - Thread draft generation, resolve, and needs-reply inbox REST endpoints.
  - Live Gmail metadata display read path for needs-reply rows.
  - Regenerated web OpenAPI schema and localized backend error codes.
affects: [05B-06, web-needs-reply, draft-review, triage-audit]

tech-stack:
  added: []
  patterns:
    - Thin Spring MVC controllers delegating to core use-case/query services.
    - Core-owned Gmail BatchRequest metadata reads for API display rows.
    - Feature-owned frontend messages regenerated into locale bundles.

key-files:
  created:
    - backend/api/src/main/java/com/zeromail/api/controllers/thread/ThreadDraftController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/thread/NeedsReplyInboxController.java
    - backend/api/src/main/java/com/zeromail/api/dto/thread/ThreadDraftResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/thread/NeedsReplyRowResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/thread/NeedsReplyListResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/triage/AuditEntryResponse.java
    - backend/api/src/main/java/com/zeromail/api/dto/triage/AuditListResponse.java
    - backend/api/src/main/java/com/zeromail/api/error/InvalidCursorException.java
    - apps/web/features/needs-reply/messages.ts
  modified:
    - backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java
    - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
    - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
    - backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyBucket.java
    - apps/web/lib/api/schema.d.ts
    - apps/web/openapi/openapi.json
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json

key-decisions:
  - "Live needs-reply display metadata is fetched in core.gmail via GmailPreviewReadService, keeping Gmail client concerns out of backend/api."
  - "Needs-reply rows degrade display-only Gmail fields to null when live metadata fetch fails; projection ids/statuses still return."
  - "SafetyViolationException now maps to HTTP 422 per the 05B-05 contract instead of the older 500 mapping."
  - "A limit=50 needs-reply page costs about 50 Gmail quota units in one BatchRequest; add a short-TTL metadata cache if this becomes hot post-launch."

patterns-established:
  - "Two disjoint @RequestMapping(\"/api/threads\") controllers can coexist when method/path mappings do not overlap."
  - "Controller-bound cursor validation wraps KeysetCursor failures in InvalidCursorException for stable error.pagination.invalid_cursor responses."

requirements-completed: [DRFT-02, DRFT-04]

duration: 40min
completed: 2026-05-12
---

# Phase 05B Plan 05: REST and Web Contract Summary

**Draft reply and needs-reply REST endpoints with live Gmail metadata, localized errors, and regenerated typed web schema**

## Performance

- **Duration:** 40 min
- **Started:** 2026-05-12T22:45:00Z
- **Completed:** 2026-05-12T23:24:57Z
- **Tasks:** 3
- **Files modified:** 28

## Accomplishments

- Added `GET /api/triage/audit` with cursor validation and DTO mapping for audit entries including draft ids.
- Added `POST /api/threads/{gmailThreadId}/draft`, `POST /api/threads/{gmailThreadId}/resolve`, and `GET /api/threads` as two disjoint thread controllers.
- Added Gmail `threads.get(format=metadata)` BatchRequest reads in `GmailPreviewReadService` for subject, other party, and last activity display fields.
- Added draft/cursor error mappings, frontend error-code messages, regenerated OpenAPI JSON, and regenerated `schema.d.ts`.

## Task Commits

1. **Tasks 1-2: REST endpoints, core Gmail display read, and backend contracts** - `9acf614`
2. **Task 3: Frontend error messages and OpenAPI typed schema** - `15afb9c`

**Plan metadata:** pending in docs close-out commit.

## Files Created/Modified

- `backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java` - adds audit-list endpoint.
- `backend/api/src/main/java/com/zeromail/api/controllers/thread/ThreadDraftController.java` - draft generation and resolve endpoints.
- `backend/api/src/main/java/com/zeromail/api/controllers/thread/NeedsReplyInboxController.java` - needs-reply keyset inbox endpoint.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java` - batched Gmail thread metadata display reads.
- `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` - maps draft in-flight, draft failed, invalid cursor, and LLM safety violation.
- `apps/web/lib/api/schema.d.ts` - regenerated typed API schema with all four new paths.
- `apps/web/features/needs-reply/messages.ts` - source messages for draft/cursor backend errors.

## Decisions Made

- Gmail display reads stay in `core.gmail` rather than controller code, matching the backend boundary convention.
- Missing or failed live Gmail metadata returns degraded rows with null display fields instead of failing the whole inbox page.
- `ThreadReplyBucket.fromPublicSlug(...)` is case-insensitive so public slugs are tolerant without exposing enum ids.
- `SafetyViolationException` is now a 422 request-level failure as required by the plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Corrected stale safety-violation test expectation**
- **Found during:** Broad backend check
- **Issue:** `ByokControllerIntegrationTest` still expected LLM safety violations to return 500, conflicting with the 05B-05 must-have of 422.
- **Fix:** Updated the test to assert HTTP 422 while preserving the same `error.llm.safety_violation` code.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/controllers/llm/ByokControllerIntegrationTest.java`
- **Verification:** `./gradlew.bat :backend:core:check :backend:api:check`
- **Committed in:** `9acf614`

---

**Total deviations:** 1 auto-fixed (missing critical test update).
**Impact on plan:** The fix aligns existing coverage with the new locked error contract; no product scope change.

## Issues Encountered

- JetBrains `get_file_problems` timed out after the full Gradle run while the IDE was busy. Earlier production-file checks were clean before the final formatting pass, and `:backend:core:check :backend:api:check` passed after formatting and test updates.
- `generateOpenApiDocs` applied the already-planned Liquibase `030-thread-reply-status` changeset to the local dev database while booting Springdoc.

## Verification

- `./gradlew.bat :backend:api:test --tests "*TriageAuditController*" --tests "*AuditLogPagination*" --tests "*AuditLogMultiTenantLeak*"` - passed
- `./gradlew.bat :backend:api:test --tests "*ThreadDraftController*" --tests "*DraftLockContention*" --tests "*GlobalExceptionHandler*"` - passed
- `./gradlew.bat :backend:core:check :backend:api:check` - passed
- `./gradlew.bat :backend:api:generateOpenApiDocs` - passed
- `pnpm --filter web generate:api` - passed
- `pnpm -C apps/web i18n:check` - passed
- `pnpm -C apps/web typecheck` - passed
- `pnpm -C apps/web test -- __tests__/api/error-codes-parity.test.ts __tests__/i18n/messages.contract.test.ts` - passed
- `rg -n "drafts\(\)\.(send|update)" backend/api/src/main` - no matches

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 06 can consume the typed web schema for the needs-reply and draft-review UI. The main follow-up is operational: monitor Gmail quota for needs-reply inbox loads and add a 1-5 minute `(tenantId, gmailThreadId)` metadata cache if real usage makes the batched display read hot.

---
*Phase: 05B-user-surface-ai-draft-replies*
*Completed: 2026-05-12*
