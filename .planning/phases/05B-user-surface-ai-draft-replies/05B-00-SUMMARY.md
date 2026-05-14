---
phase: 05B-user-surface-ai-draft-replies
plan: 00
subsystem: testing
tags: [jakarta-mail, liquibase, postgres, junit, archunit, vitest, playwright, i18n]

requires:
  - phase: 05B-user-surface-ai-draft-replies
    provides: Phase context, AI-SPEC, UI-SPEC, research, and reviewed Plan 00 scope
provides:
  - Jakarta Mail API and Eclipse Angus Mail runtime on backend/core runtime classpath
  - Metadata-only thread_reply_status Liquibase changeset with FK cascade and inbox indexes
  - Backend Wave 0 RED contracts for draft MIME, threading, tone context, classifier, audit API, and thread draft API
  - Frontend Wave 0 skipped contracts for the needs-reply table and Playwright golden path
  - i18n scanner coverage for future needs-reply page and component paths
affects: [phase-05B, backend-core, backend-api, apps-web, gmail-drafts, thread-reply-status]

tech-stack:
  added:
    - jakarta.mail:jakarta.mail-api:2.1.3
    - org.eclipse.angus:angus-mail:2.0.4
  patterns:
    - Wave 0 RED contracts compile via reflection or skipped dynamic imports instead of static references to future code
    - Schema acceptance tests assert PostgreSQL index and FK definitions, not only application startup
    - Future frontend paths are added to EN_SCAN_FILES before implementation so visible copy is scanned as files land

key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml
    - backend/core/src/test/java/com/zeromail/core/draft/ReplyMimeBuildTest.java
    - backend/core/src/test/java/com/zeromail/core/draft/ThreadingHeaderValidatorTest.java
    - backend/core/src/test/java/com/zeromail/core/draft/GenerateThreadDraftServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/draft/ToneContextBuilderTest.java
    - backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacyLogScrubTest.java
    - backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java
    - backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/triage/TriageAuditSagaDraftThreadingTest.java
    - backend/core/src/test/java/com/zeromail/core/triage/AutomaticTriageDraftUsesToneGenerationTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/triage/TriageAuditControllerContractTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/triage/AuditLogPaginationTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/triage/AuditLogMultiTenantLeakTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/thread/ThreadDraftControllerContractTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/thread/DraftLockContentionTest.java
    - apps/web/features/needs-reply/components/NeedsReplyTable.test.tsx
    - apps/web/e2e/needs-reply.spec.ts
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-00-SUMMARY.md
  modified:
    - gradle/libs.versions.toml
    - backend/core/build.gradle.kts
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/test/java/com/zeromail/core/support/LiquibaseMigrationTest.java
    - apps/web/scripts/check-i18n.ts

key-decisions:
  - "Wave 0 backend contracts use reflection/fail scaffolds and ArchUnit package strings so compileTestJava stays green while later plans turn them into executable assertions."
  - "The thread_reply_status account-deletion cleanup mechanism is the tenant FK ON DELETE CASCADE; no separate deleteByTenantId cleanup path was introduced."
  - "The needs-reply Vitest contract uses describe.skip plus a variable dynamic import so TypeScript does not resolve a missing future component."

patterns-established:
  - "Schema migrations that add hot-path indexes should extend LiquibaseMigrationTest with pg_indexes/pg_constraint assertions."
  - "Frontend Wave 0 tests for missing future components use skipped dynamic imports rather than top-level imports."
  - "Phase-local RED contracts may intentionally fail when run, but source compilation and project type checks must remain green."

requirements-completed: [DRFT-01, DRFT-02, DRFT-03, DRFT-04]

duration: 25min
completed: 2026-05-13
---

# Phase 05B Plan 00: Dependency, Schema, and RED Contract Summary

**Jakarta Mail classpath, thread-reply-status schema, and compile-safe backend/frontend RED contracts for AI draft replies**

## Performance

- **Duration:** 25 min
- **Started:** 2026-05-13T03:39:00+07:00
- **Completed:** 2026-05-13T04:04:52+07:00
- **Tasks:** 3
- **Files modified:** 22

## Accomplishments

- Added `jakarta.mail:jakarta.mail-api:2.1.3` and `org.eclipse.angus:angus-mail:2.0.4` to backend/core with verified runtime resolution.
- Added and master-wired `030-thread-reply-status.yaml` with metadata-only columns, tenant FK cascade, unique tenant/thread index, composite inbox keyset index, and partial TO_REPLY count index.
- Added 14 backend Wave 0 contract files for draft threading, tone generation, privacy, classifier, audit list, thread draft API, and lock contention while keeping backend test compilation green.
- Added skipped frontend needs-reply Vitest and Playwright contracts plus i18n scanner coverage for the future needs-reply page and components.

## Verification

- `./gradlew.bat :backend:core:dependencies --configuration runtimeClasspath | Select-String -Pattern 'org\.eclipse\.angus:angus-mail:2\.0\.4|jakarta\.mail:jakarta\.mail-api:2\.1\.3'` - passed.
- `./gradlew.bat :backend:core:dependencyInsight --configuration runtimeClasspath --dependency jakarta.activation` - passed; activation resolves to `jakarta.activation-api:2.1.4` with existing `angus-activation:2.0.3`.
- `./gradlew.bat :backend:core:test --tests com.zeromail.core.support.LiquibaseMigrationTest :backend:core:compileTestJava :backend:api:compileTestJava` - passed.
- `pnpm exec tsc --noEmit` from `apps/web` - passed.
- `pnpm i18n:check` from `apps/web` - passed.
- `pnpm exec vitest run features/needs-reply` from `apps/web` - passed, 1 file / 5 tests skipped as intended.
- `rg -n "import com\.zeromail\.core\.draft" backend/core/src/test backend/api/src/test` - no matches.
- JetBrains inspections on representative new Java files reported no errors.

## Task Commits

1. **Task 1: Add jakarta.mail dependency + thread_reply_status Liquibase changelog** - `eeffd2c` (`feat`)
2. **Task 2: Backend Wave 0 RED test scaffolds + ArchUnit guards** - `f2cc2f6` (`test`)
3. **Task 3: Frontend Wave 0 test scaffolds + EN_SCAN_FILES update** - `f1f0e2c` (`test`)

## Files Created/Modified

- `gradle/libs.versions.toml` - Adds Jakarta Mail API and Angus Mail version-catalog entries.
- `backend/core/build.gradle.kts` - Wires API dependency and runtime Angus implementation into backend/core.
- `backend/core/src/main/resources/db/changelog/changes/030-thread-reply-status.yaml` - Adds metadata-only reply-status projection table and indexes.
- `backend/core/src/test/java/com/zeromail/core/support/LiquibaseMigrationTest.java` - Proves the new table, indexes, and FK cascade exist after migration.
- `backend/core/src/test/java/com/zeromail/core/draft/*` - Draft MIME, validation, generation, privacy, tone, and ArchUnit RED contracts.
- `backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceTest.java` - Reply-status classifier RED contract.
- `backend/core/src/test/java/com/zeromail/core/triage/*Draft*.java` - Triage saga/orchestrator draft-generation RED contracts.
- `backend/api/src/test/java/com/zeromail/api/controllers/triage/*` - Audit list, pagination, and tenant isolation RED contracts.
- `backend/api/src/test/java/com/zeromail/api/controllers/thread/*` - Thread draft endpoint and lock-contention RED contracts.
- `apps/web/features/needs-reply/components/NeedsReplyTable.test.tsx` - Skipped dynamic-import Vitest contract for needs-reply states and row actions.
- `apps/web/e2e/needs-reply.spec.ts` - Playwright `test.fixme` golden path for draft generation.
- `apps/web/scripts/check-i18n.ts` - Adds future needs-reply page and component paths to EN_SCAN_FILES.

## Decisions Made

- Used Context7 Jakarta Mail docs to confirm the `jakarta.mail.internet.MimeMessage` package/API context and the Eclipse Angus implementation split.
- Added explicit LiquibaseMigrationTest assertions for the new indexes and FK instead of treating a booted context as sufficient schema proof.
- Kept requirement-row edits deferred to later closure/validation plans; this plan copies requirement IDs into summary metadata but does not claim product-level completion.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added schema-definition assertions to LiquibaseMigrationTest**
- **Found during:** Task 1
- **Issue:** The plan required proving the `thread_reply_status` indexes and FK cascade, but the existing migration test only asserted table presence and one older triage index behavior.
- **Fix:** Extended `LiquibaseMigrationTest` to assert `thread_reply_status` exists, `ux_thread_reply_status_tenant_thread`, `idx_thread_reply_status_inbox`, `idx_thread_reply_status_to_reply`, and `fk_thread_reply_status_tenant ON DELETE CASCADE`.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/support/LiquibaseMigrationTest.java`
- **Verification:** `./gradlew.bat :backend:core:test --tests com.zeromail.core.support.LiquibaseMigrationTest`
- **Committed in:** `eeffd2c`

**2. [Rule 3 - Blocking] Replaced explicit any in skipped frontend dynamic component type**
- **Found during:** Task 3 commit hook
- **Issue:** ESLint rejected `ComponentType<any>` even though the suite is skipped.
- **Fix:** Changed the dynamic import return type to `ComponentType<Record<string, unknown>>`.
- **Files modified:** `apps/web/features/needs-reply/components/NeedsReplyTable.test.tsx`
- **Verification:** `pnpm exec eslint --fix ...`, `pnpm exec tsc --noEmit`, `pnpm exec vitest run features/needs-reply`
- **Committed in:** `f1f0e2c`

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 blocking).
**Impact on plan:** Both changes strengthened the stated acceptance gates without changing product scope.

## Issues Encountered

- Initial `pnpm -C apps/web vitest run ...` and `pnpm -C apps/web tsc ...` invocations used the wrong binary-execution form for this workspace and failed with `Command "apps/web" not found`. Re-ran the same checks from `apps/web` via `pnpm exec`, and both passed.
- The first `idx_thread_reply_status_to_reply` test assertion was too strict about PostgreSQL's parenthesized index-definition rendering. Relaxed it to assert the important predicate tokens.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 05B-01 can retrofit the existing triage draft write path with Jakarta Mail MIME generation and threading header validation. Plan 05B-02 can create the `core.thread` package on top of the committed schema and classifier RED contracts.

---
*Phase: 05B-user-surface-ai-draft-replies*
*Completed: 2026-05-13*
