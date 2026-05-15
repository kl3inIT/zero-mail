---
phase: 03-rules-engine
plan: "07"
subsystem: rules-api
tags: [rules-engine, api, openapi, tenant-isolation, privacy, spring-mvc]

requires:
  - phase: 03-rules-engine
    provides: Rule management, preview, compile, and template materialization core services
  - phase: 03-rules-engine
    provides: Template catalog and selected-onboarding template materialization contracts
provides:
  - Thin Spring MVC rules API controller for compile, CRUD, reorder, preview, and templates
  - Frontend-facing rules DTO records with tagged compile response statuses
  - Stable privacy-safe error codes under error.rules.*
  - Regenerated OpenAPI JSON and generated frontend TypeScript schema
  - Integration coverage for tenant isolation, validation, privacy, and generated API contracts
affects: [03-rules-engine, 03-08-rules-ui, frontend-api-client, triage-convergence]

tech-stack:
  added: []
  patterns:
    - Controllers translate HTTP to service commands only and never inject repositories
    - Rules API returns safe DTOs with entityVersion and lastPreviewedEntityVersion
    - First rules list read uses Cache-Control no-store because template materialization is idempotent but stateful

key-files:
  created:
    - backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java
    - backend/api/src/main/java/com/zeromail/api/dto/rules/RuleDtos.java
    - backend/api/src/main/java/com/zeromail/api/dto/rules/package-info.java
    - backend/api/src/main/java/com/zeromail/api/error/RuleApiException.java
    - backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerIntegrationTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerTenantIsolationTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerPrivacyTest.java
  modified:
    - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
    - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/ActionIntentJsonValidator.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleStatusView.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleValidationException.java
    - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewService.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateMaterializationService.java
    - apps/web/openapi/openapi.json
    - apps/web/lib/api/schema.d.ts

key-decisions:
  - "RulesController stays thin by calling core service entrypoints and mapping domain views to DTO records privately."
  - "RuleCompileResponse uses tagged status values compiled, clarificationRequired, and invalid instead of overloading ProblemDetail validation errors."
  - "GET /api/rules returns Cache-Control no-store because it materializes selected onboarding templates before listing rules."
  - "Preview endpoints are deterministic/read-only and never map to billing insufficient-credit errors; only compile can return HTTP 402 error.billing.insufficient."
  - "STATE.md and ROADMAP.md were left untouched per user/orchestrator single-writer constraint."

patterns-established:
  - "Rules API DTOs expose entityVersion and lastPreviewedEntityVersion for optimistic UI contracts; ambiguous frontend version naming is avoided."
  - "Template materialization responses include skippedTemplates with key plus safe reason enum only."
  - "Rule API ProblemDetail params are stable, allow-listed, and never echo raw source text, Gmail content, prompts, completions, or tokens."

requirements-completed: [RULE-01, RULE-02, RULE-03, RULE-04, RULE-05, RULE-06, RULE-07]

duration: continued execution
completed: 2026-05-10
---

# Phase 03 Plan 07: Rules API and OpenAPI Summary

**Typed rules HTTP API with privacy-safe DTOs, tenant isolation tests, and regenerated frontend OpenAPI schema.**

## Performance

- **Duration:** Continued execution after handoff; exact original start time was not available in this resumed context.
- **Started:** Not recorded by predecessor executor.
- **Completed:** 2026-05-10T05:01:19+07:00
- **Tasks:** 2 completed
- **Files modified:** 17

## Accomplishments

- Added `RulesController` under `/api/rules` with list/get/compile/create/update/enable/reorder/delete/saved-preview/draft-preview/template-list/template-materialize endpoints.
- Added rules DTO records with Jakarta validation and tagged compile responses for `compiled`, `clarificationRequired`, and `invalid`.
- Added stable `error.rules.*` mappings for compile validation, clarification-required misuse, not found, preview required, invalid sample size, invalid reorder, version mismatch, unsafe action, and Gmail preview unavailable.
- Regenerated `apps/web/openapi/openapi.json` and `apps/web/lib/api/schema.d.ts` through the existing Gradle and pnpm scripts.
- Added integration tests for success flows, validation failures, insufficient credits on compile only, tenant isolation, reorder all-or-nothing behavior, and privacy-safe preview/log responses.

## Endpoint Surface

- `GET /api/rules` materializes onboarding templates idempotently, returns ordered rules plus template summaries, and sets `Cache-Control: no-store`.
- `GET /api/rules/{ruleId}`
- `POST /api/rules/compile`
- `POST /api/rules`
- `PUT /api/rules/{ruleId}`
- `PATCH /api/rules/{ruleId}/enabled`
- `PUT /api/rules/reorder`
- `DELETE /api/rules/{ruleId}`
- `POST /api/rules/{ruleId}/preview`
- `POST /api/rules/preview`
- `GET /api/rules/templates`
- `POST /api/rules/templates/{templateKey}/materialize`

## Task Commits

Each task was committed atomically:

1. **Task 1: DTOs, controller, and error mapping** - `200e65c` (feat)
2. **Task 2: API integration, tenant isolation, privacy, OpenAPI** - `b758fc1` (test)

**Plan metadata:** recorded in final docs commit.

## Files Created/Modified

- `backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java` - Thin HTTP controller for the rules API surface.
- `backend/api/src/main/java/com/zeromail/api/dto/rules/RuleDtos.java` - Frontend-facing request/response records and compile status payloads.
- `backend/api/src/main/java/com/zeromail/api/dto/rules/package-info.java` - Rules DTO package annotations.
- `backend/api/src/main/java/com/zeromail/api/error/RuleApiException.java` - API-level exception for invalid endpoint use such as clarification payloads where compiled payloads are required.
- `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java` - Stable rules error code constants.
- `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` - Privacy-safe rules exception mappings.
- `backend/core/src/main/java/com/zeromail/core/rules/model/ActionIntentJsonValidator.java` - Unsafe/unknown action IDs now fail with the stable unsafe-action validation path.
- `backend/core/src/main/java/com/zeromail/core/rules/model/RuleStatusView.java` - API-safe rule status projection for DTO mapping.
- `backend/core/src/main/java/com/zeromail/core/rules/model/RuleValidationException.java` - Additional rule validation reasons used by API errors.
- `backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java` - Exposes preview-version state needed by API responses.
- `backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewService.java` - Adds API draft-preview entrypoint from compiled JSON strings.
- `backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateMaterializationService.java` - Adds single-template materialization entrypoint for template endpoint use.
- `backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerIntegrationTest.java` - Main rules API happy-path, validation, billing, cache, and OpenAPI contract tests.
- `backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerTenantIsolationTest.java` - Cross-tenant read/mutate/delete/reorder denial and reorder conflict regression tests.
- `backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerPrivacyTest.java` - Sentinel privacy and Gmail preview-unavailable error tests.
- `apps/web/openapi/openapi.json` - Regenerated OpenAPI document with all rules paths.
- `apps/web/lib/api/schema.d.ts` - Regenerated frontend schema types with rules paths and schemas.

## Decisions Made

- The controller maps service/domain results in private `toResponse(...)` helpers and never injects repositories, keeping transaction ownership inside core services.
- `RuleResponse` exposes `entityVersion` and `lastPreviewedEntityVersion`; no ambiguous `version` field is used in frontend JSON.
- Clarification-required compile output remains a normal tagged compile response, while create/update endpoints that require compiled payloads return `error.rules.compile.clarification_required`.
- Gmail preview-unavailable errors expose only the safe reason enum, such as `no_read_grant`, and do not include account, token, subject, or message details.
- OpenAPI/types were regenerated only through `.\gradlew.bat :backend:api:generateOpenApiDocs` and `pnpm --filter web generate:api`.

## Verification

- `.\\gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.rules.RulesControllerIntegrationTest"` - PASS before Task 1 commit.
- `.\\gradlew.bat :backend:api:compileJava :backend:api:compileTestJava --rerun-tasks` - PASS before Task 1 commit.
- `.\\gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.rules.*"` - PASS.
- `.\\gradlew.bat :backend:api:generateOpenApiDocs` - PASS.
- `pnpm --filter web generate:api` - PASS.
- `rg '"/api/rules' apps/web/openapi/openapi.json` - PASS, all generated rules paths present.
- `rg '"/api/rules|/api/rules' apps/web/lib/api/schema.d.ts` - PASS, frontend schema includes rules paths.
- JetBrains file-problem checks for `RulesControllerTenantIsolationTest` and `RulesControllerPrivacyTest` - PASS, no errors; only non-blocking inspections.
- `rg -n 'Repository|repo' backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java` - PASS, no repository injection or repo abbreviation in the controller.
- Privacy keyword scan across rules controller/DTO/error handling found only privacy guard comments and generic HTTP body variables; no raw Gmail content, prompts, completions, or tokens are exposed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added API-safe service entrypoints instead of injecting persistence**
- **Found during:** Task 1 (DTOs, controller, and error mapping)
- **Issue:** The planned controller endpoints needed preview-version state, draft preview from compiled JSON, and single-template materialization, but those were not exposed through core service APIs in a controller-safe shape.
- **Fix:** Added `RuleStatusView`, preview/template service entrypoints, and rule entity accessors so the controller could remain thin and repository-free.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/rules/model/RuleStatusView.java`, `backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java`, `backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewService.java`, `backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateMaterializationService.java`
- **Verification:** Task 1 integration test and compile checks passed; controller repository scan passed.
- **Committed in:** `200e65c`

**2. [Rule 1 - Bug] Routed unknown/unsafe action IDs to the stable rules unsafe-action error**
- **Found during:** Task 1 (DTOs, controller, and error mapping)
- **Issue:** Unknown action IDs could fall through a lower-level action validation path instead of the planned stable API error code.
- **Fix:** `ActionIntentJsonValidator` now maps unsafe action IDs to `RuleValidationException.unsafeAction()`, and the API maps that reason to `error.rules.unsafe_action`.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/rules/model/ActionIntentJsonValidator.java`, `backend/core/src/main/java/com/zeromail/core/rules/model/RuleValidationException.java`, `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java`, `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java`
- **Verification:** Rules controller integration tests cover unsafe-action response mapping.
- **Committed in:** `200e65c`

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 bug)
**Impact on plan:** Both fixes were required to satisfy the planned thin-controller, stable-error, and privacy-safe API requirements. No unplanned product surface was added.

## Issues Encountered

- The first Task 2 commit command exceeded the tool timeout while pre-commit hooks were still running. The commit completed afterward as `28e1365`, then the hook-formatted OpenAPI JSON was amended into the Task 2 commit, producing final commit `b758fc1`.
- `.planning/STATE.md` was already modified in the worktree and stayed unstaged/uncommitted, per the explicit orchestrator constraint that shared tracking writes are owned elsewhere.

## Known Stubs

None. Stub scan found no plan-blocking TODO/FIXME/placeholder UI stubs. Matches for `placeholder` were schema documentation for allowed ICU params, and matches for `null`/empty-list defaults were legitimate validation/defaulting logic rather than unimplemented data.

## Threat Flags

None. This plan introduced the planned `/api/rules` HTTP surface and generated API artifacts only; no unplanned auth path, schema boundary, file access path, Gmail write, LLM logging, or external network surface was added.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 03-08 UI work. The frontend can consume generated `/api/rules` types from `apps/web/lib/api/schema.d.ts`, including compile status variants, reorder entries, rule preview responses, and template materialization feedback.

## Self-Check: PASSED

- Verified summary file exists at `.planning/phases/03-rules-engine/03-07-SUMMARY.md`.
- Verified task commits `200e65c` and `b758fc1` exist in git history.
- Verified key created/generated files exist: `RulesController.java`, `apps/web/openapi/openapi.json`, `apps/web/lib/api/schema.d.ts`, `RulesControllerPrivacyTest.java`, and `RulesControllerTenantIsolationTest.java`.
- Verified only `.planning/STATE.md` remains dirty outside this summary, and it is intentionally unstaged per orchestrator constraint.

---
*Phase: 03-rules-engine*
*Completed: 2026-05-10*
