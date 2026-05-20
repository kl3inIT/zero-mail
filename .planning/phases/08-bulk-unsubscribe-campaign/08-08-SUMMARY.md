---
phase: 08-bulk-unsubscribe-campaign
plan: 08
subsystem: api
tags: [spring-mvc, springdoc-openapi, openapi-typescript, jakarta-validation, problemdetail, rest, cleanup, unsubscribe]

# Dependency graph
requires:
  - phase: 08-bulk-unsubscribe-campaign
    provides:
      - "CampaignPreviewService + CampaignExecuteService (UNS-03 + UNS-04)"
      - "CampaignStatusQueryService + CampaignRetryService (UNS-05 + UNS-06)"
      - "CampaignUndoService (UNS-07)"
      - "CandidateQueryService + SuppressionCrudService (UNS-01 + UNS-02)"
      - "All five cleanup business exceptions extend BusinessException"
provides:
  - "4 thin REST controllers under /api/unsubscribe/* and /api/cleanup/suppression"
  - "13 record DTOs in api.dto.cleanup with static from(...) factories"
  - "Controller-local @ExceptionHandler overrides: CampaignCapExceededException → 400, UndoWindowExpiredException → 410"
  - "Wave 0 controller tests UnsubscribeCampaignControllerTest + CampaignStatusControllerTest flip GREEN"
  - "Regenerated typed client apps/web/lib/api/schema.d.ts with 12 new schemas + 8 new path entries"
affects: [Phase 8 Wave 5b frontend feature wave, future cleanup observability, Phase 9 hardening pass]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Thin controllers (CONVENTIONS §1) + service-owned @Transactional"
    - "Controller-local @ExceptionHandler used only to override GlobalExceptionHandler's ErrorClass defaults when SPEC mandates a non-default HTTP code"
    - "Single-input wire DTO (senderEmailOrDomain) disambiguated at the controller boundary into the XOR-validated AddSuppressionCommand"
    - "Computed presentation fields (progressPct, undoAvailable) live on the controller, not the projection"

key-files:
  created:
    - "backend/api/src/main/java/com/zeromail/api/controllers/cleanup/UnsubscribeCandidateController.java"
    - "backend/api/src/main/java/com/zeromail/api/controllers/cleanup/UnsubscribeCampaignController.java"
    - "backend/api/src/main/java/com/zeromail/api/controllers/cleanup/CampaignStatusController.java"
    - "backend/api/src/main/java/com/zeromail/api/controllers/cleanup/SuppressionController.java"
    - "backend/api/src/main/java/com/zeromail/api/controllers/cleanup/package-info.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/package-info.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/UnsubscribeCandidateResponse.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/UnsubscribeCandidateListResponse.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignPreviewRequest.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignPreviewResponse.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/PerSenderPreviewResponse.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignExecuteRequest.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignExecuteResponse.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignStatusResponse.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/PerSenderStateResponse.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/SuppressionEntryResponse.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/SuppressionListResponse.java"
    - "backend/api/src/main/java/com/zeromail/api/dto/cleanup/SuppressionAddRequest.java"
  modified:
    - "apps/web/lib/api/schema.d.ts"
    - "apps/web/openapi/openapi.json"
    - ".planning/phases/08-bulk-unsubscribe-campaign/deferred-items.md"

key-decisions:
  - "Controller-local @ExceptionHandler overrides used only for the two SPEC-mandated non-default mappings (400 for cap exceeded, 410 for undo window expired); the other three cleanup exceptions keep the GlobalExceptionHandler default mapping"
  - "Undo endpoint resolves jobId → campaignId via CampaignStatusQueryService.findByJobId(...) so the controller never injects UnsubscribeCampaignRepository (ArchUnit WR-01 compliance)"
  - "SuppressionAddRequest carries a single user-friendly senderEmailOrDomain field; the controller disambiguates email vs domain by '@' presence before constructing AddSuppressionCommand"
  - "OpenAPI regen path: docker-compose lifecycle disabled, ddl-auto=none, liquibase enabled, point at existing PG/Redis containers — the springdoc plugin's forkedSpringBootRun task does not respect the project's JDK 25 toolchain, so the regen runs through bootRun against a manually-started Postgres + Redis"

patterns-established:
  - "Cleanup controller convention: @Tag(name = \"cleanup\") + thin body of (TenantContext.currentTenantUuid() → service.method(...) → DTO.from(result)) + log line carrying tenantId + UUIDs + counts only"
  - "Controller-local @ExceptionHandler returns a hand-rolled ProblemDetail that matches GlobalExceptionHandler's wire shape (type/title/detail/code/params/message) so the frontend can branch on code uniformly across all error paths"

requirements-completed: [UNS-01, UNS-02, UNS-03, UNS-05, UNS-06, UNS-07]

# Metrics
duration: 45min
completed: 2026-05-21
---

# Phase 8 Plan 08: Wave 7 Cleanup HTTP Surface Summary

**4 thin REST controllers + 13 record DTOs + regenerated OpenAPI typed client — full cleanup HTTP surface for UNS-01..UNS-07 lands; Wave 0 controller tests flip GREEN.**

## Performance

- **Duration:** 45 minutes
- **Started:** 2026-05-20T16:57:55Z
- **Completed:** 2026-05-20T17:43:00Z (approx., based on commit timestamps)
- **Tasks:** 3 of 3 complete
- **Files created:** 18 (4 controllers + 13 DTOs + 1 controller package-info)
- **Files modified:** 3 (schema.d.ts, openapi.json, deferred-items.md)

## Accomplishments

- **Full cleanup HTTP surface lands**: 9 new endpoints under `/api/unsubscribe/*` and `/api/cleanup/suppression` cover UNS-01 (candidate list), UNS-02 (suppression CRUD), UNS-03 (preview), UNS-04 (execute), UNS-05 (status polling), UNS-06 (per-sender retry), and UNS-07 (undo).
- **Thin controllers verified**: zero repository injection in `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/` — every controller delegates to a service from `core.cleanup.usecases` and the ArchUnit `controllers_do_not_touch_repositories` rule passes.
- **Wave 0 controller tests flip GREEN**: `UnsubscribeCampaignControllerTest` and `CampaignStatusControllerTest` (committed in Wave 0 commit `65bbf9d7` as RED stubs) now pass because their `Class.forName(...)` future-type assertions resolve.
- **Typed client regenerated**: `apps/web/lib/api/schema.d.ts` grew from 4085 to 5033 lines with 12 new component schemas + 8 new path entries. Wave 5b frontend can now consume the typed client without any hand-rolling.

## Task Commits

1. **Task 1: 13 DTO records** — `f96f3f18` (feat)
2. **Task 2: 4 thin controllers + 2 @ExceptionHandler overrides** — `767f8355` (feat)
3. **Task 3: OpenAPI regen + deferred-items update** — `5f3bd51f` (chore)

## Files Created/Modified

### Created

- `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/UnsubscribeCandidateController.java` — `GET /api/unsubscribe/candidates` (UNS-01); window literal resolver (7d/30d/90d).
- `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/UnsubscribeCampaignController.java` — 4 write-path endpoints + 2 controller-local `@ExceptionHandler` overrides.
- `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/CampaignStatusController.java` — `GET /api/unsubscribe/campaigns/{jobId}` (UNS-05) with computed `progressPct` + `undoAvailable`.
- `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/SuppressionController.java` — CRUD under `/api/cleanup/suppression` (UNS-02); single-input disambiguation + `NoSuchElementException` → 404 handler.
- `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/package-info.java` — controller package marker.
- 12 DTO records under `backend/api/src/main/java/com/zeromail/api/dto/cleanup/` + a `package-info.java` carrying `@NamedInterface("cleanup")` for Spring Modulith.

### Modified

- `apps/web/lib/api/schema.d.ts` — regenerated from live backend `/v3/api-docs`; 12 new schemas, 8 new paths.
- `apps/web/openapi/openapi.json` — raw OpenAPI spec input for codegen reproducibility.
- `.planning/phases/08-bulk-unsubscribe-campaign/deferred-items.md` — documented the two pre-existing Wave 0 RED frontend hook tests that block `pnpm tsc --noEmit` (out of scope; owned by Wave 5b).

## Decisions Made

- **Controller-local override, not GlobalExceptionHandler edit** — `CampaignCapExceededException` carries `ErrorClass.UNPROCESSABLE` (default → HTTP 422) and `UndoWindowExpiredException` carries `ErrorClass.CONFLICT` (default → HTTP 409), but the SPEC must_haves and Wave 0 test contract require 400 and 410 respectively. Instead of editing `GlobalExceptionHandler` (which would change semantics for any future caller of these exceptions), the override lives in `UnsubscribeCampaignController` as a controller-local `@ExceptionHandler`. The wire-shape ProblemDetail still matches the global handler's output exactly (type/title/detail/code/params/message).
- **Undo resolves jobId → campaignId via CampaignStatusQueryService** — `CampaignUndoService.undo(...)` takes a `campaignId`, but the wire-level endpoint is `/api/unsubscribe/campaigns/{jobId}/undo`. The controller could have injected `UnsubscribeCampaignRepository` directly, but that would violate ArchUnit `controllers_do_not_touch_repositories`. Instead the controller resolves the projection through `CampaignStatusQueryService.findByJobId(...)`, then passes the resolved campaign id to `CampaignUndoService.undo(...)`. One extra read; ArchUnit-compliant.
- **OpenAPI regen via bootRun, not the springdoc plugin** — The `org.springdoc.openapi-gradle-plugin`'s `forkedSpringBootRun` task does not honor the project's JDK 25 toolchain; it spawns the JVM using whatever `JAVA_HOME` happens to point at. With `JAVA_HOME=jdk-21` (the user's default) the forked Boot process errors with `UnsupportedClassVersionError: class file version 69.0`. Workaround: regen runs through standard `bootRun` against the locally-running PG + Redis containers (docker-compose lifecycle disabled, `ddl-auto=none`, `liquibase.enabled=true`), then `curl http://localhost:8080/v3/api-docs > apps/web/openapi/openapi.json`, then `pnpm exec openapi-typescript ...`. The plugin's hermetic boot path remains broken in this environment but is unblocking for the regen step.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking Issue] springdoc-openapi-gradle-plugin forkedSpringBootRun ignores JDK toolchain**

- **Found during:** Task 3 (OpenAPI codegen)
- **Issue:** Plan task 3 calls for `./gradlew :backend:api:generateOpenApiDocs` which delegates to the springdoc plugin's `forkedSpringBootRun` task. That task spawns Boot using `JAVA_HOME` directly and does not honor the Gradle toolchain. With `JAVA_HOME=jdk-21` on this machine the forked Boot dies with `UnsupportedClassVersionError: class file version 69.0`. Setting `JAVA_HOME=jdk-25` then surfaces a secondary blocker: the plugin's args include `--spring.docker.compose.file=...` which makes Boot's docker-compose lifecycle try to invoke `docker` from the OS PATH; docker is at `C:/Program Files/Docker/Docker/resources/bin/docker.exe` but not on Git Bash's PATH, so Boot fails with `Cannot run program "docker": CreateProcess error=2`. Even with that worked around, Hibernate `validate` against the existing Postgres rejects the boot because the schema is behind (no `processing_job` table from Phase 8).
- **Fix:** Use Plan B from the plan task — start Boot via plain `bootRun` against the locally-running containers with `--spring.docker.compose.enabled=false --spring.jpa.hibernate.ddl-auto=none --spring.liquibase.enabled=true` so Liquibase brings the schema up to date in-process, then `curl http://localhost:8080/v3/api-docs > apps/web/openapi/openapi.json` and run `pnpm exec openapi-typescript ...` exactly as the project's `generate-api.ts` script would have.
- **Files modified:** `apps/web/openapi/openapi.json`, `apps/web/lib/api/schema.d.ts`.
- **Verification:** spec contains all 12 expected cleanup schemas + all 8 expected cleanup paths; `schema.d.ts` grew by 948 lines with 25 cleanup type references.
- **Committed in:** `5f3bd51f` (Task 3 commit).

**2. [Rule 2 - Missing Critical Functionality] SuppressionController missing NoSuchElementException → 404 mapping**

- **Found during:** Task 2 (controllers)
- **Issue:** `SuppressionCrudService.remove(...)` throws `NoSuchElementException` on a missing row, but `GlobalExceptionHandler` has no explicit `@ExceptionHandler(NoSuchElementException.class)` and falls back to a generic 500. SPEC requires DELETE on a non-existent suppression id to return 404.
- **Fix:** Added a controller-local `@ExceptionHandler(NoSuchElementException.class)` to `SuppressionController` returning HTTP 404 with `code = "error.cleanup.suppression.not_found"` and a ProblemDetail that matches the global handler's wire shape.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/SuppressionController.java`.
- **Verification:** controller compiles; the existing ArchUnit + Modulith verification still passes; tenant-scoping in the service guarantees no cross-tenant info leak.
- **Committed in:** `767f8355` (Task 2 commit).

**Total deviations:** 2 auto-fixed (1 Rule 3 blocking, 1 Rule 2 missing critical functionality).
**Impact on plan:** Both fixes preserve plan intent. The OpenAPI regen path is now slightly more manual than the plan's preferred `./gradlew generateOpenApiDocs` invocation; the plugin bug is environment-specific and the workaround is documented in the deviation. The 404 mapping is required for SPEC compliance.

## Issues Encountered

None during planned work. The OpenAPI regen blocker was a deviation handled by Plan B (already authorized in the plan task action text).

## TDD Gate Compliance

Not applicable — this plan does not use `tdd="true"` on its tasks. Wave 0 RED contract tests (committed in `65bbf9d7`) are the GREEN flip target for this plan, and the GREEN flip is verified in the Self-Check below.

## Self-Check

### Created files exist

| File                                                                                  | Status |
| ------------------------------------------------------------------------------------- | ------ |
| `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/UnsubscribeCandidateController.java`  | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/UnsubscribeCampaignController.java`   | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/CampaignStatusController.java`        | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/SuppressionController.java`           | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/controllers/cleanup/package-info.java`                    | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/package-info.java`                             | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/UnsubscribeCandidateResponse.java`             | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/UnsubscribeCandidateListResponse.java`         | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignPreviewRequest.java`                   | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignPreviewResponse.java`                  | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/PerSenderPreviewResponse.java`                 | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignExecuteRequest.java`                   | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignExecuteResponse.java`                  | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/CampaignStatusResponse.java`                   | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/PerSenderStateResponse.java`                   | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/SuppressionEntryResponse.java`                 | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/SuppressionListResponse.java`                  | FOUND  |
| `backend/api/src/main/java/com/zeromail/api/dto/cleanup/SuppressionAddRequest.java`                    | FOUND  |
| `apps/web/lib/api/schema.d.ts`                                                                        | FOUND  |

### Commits exist

| Hash       | Found |
| ---------- | ----- |
| `f96f3f18` | yes   |
| `767f8355` | yes   |
| `5f3bd51f` | yes   |

### Verification commands

| Check                                                                          | Result   |
| ------------------------------------------------------------------------------ | -------- |
| `./gradlew :backend:api:compileJava :backend:api:compileTestJava`              | PASS     |
| `./gradlew :backend:api:test --tests "*UnsubscribeCampaignControllerTest*"`    | PASS     |
| `./gradlew :backend:api:test --tests "*CampaignStatusControllerTest*"`         | PASS     |
| `./gradlew :backend:api:test --tests "*ControllerBoundary*"`                   | PASS     |
| `./gradlew :backend:core:test --tests "*Triage*Test*"`                         | PASS     |
| `find backend/api/src/main/java/com/zeromail/api/controllers/cleanup -name "*Repository*"` | 0 matches |
| `grep -rE "core\.cleanup\.application" backend`                                | 0 matches |
| `grep -c "UnsubscribeCandidate\|CampaignPreview\|CampaignStatus\|SuppressionEntry" apps/web/lib/api/schema.d.ts` | 25       |
| Cleanup path entries in `schema.d.ts`                                           | 8 paths  |

## Self-Check: PASSED

## Known Stubs

None — every Java file ships its production implementation. The two pre-existing Wave 0 RED frontend hook test files (`useSuppressionList.test.ts`, `useCampaignStatus.test.ts`) reference TypeScript modules that are Wave 5b deliverables; this is tracked in `deferred-items.md`. No Wave 7 backend file is a stub.

## Next Phase Readiness

- **Wave 5b frontend (next)**: typed client at `apps/web/lib/api/schema.d.ts` is ready; Wave 5b can now create `features/cleanup/suppression/` + `features/cleanup/unsubscribe-campaign/` query-keys, hooks, and components consuming `components["schemas"]["CampaignStatusResponse"]` etc. The Wave 0 RED frontend stubs in `deferred-items.md` will flip GREEN automatically once Wave 5b ships the hook files they reference.
- **Phase 9 hardening (later)**: candidate areas — ProblemDetail wire-shape contract tests across the 5 cleanup-exception → HTTP-code paths; @WebMvcTest slices on the 4 controllers; OpenAPI regen automation that does not depend on a JDK 25 in `JAVA_HOME`.

---
*Phase: 08-bulk-unsubscribe-campaign*
*Completed: 2026-05-20*
