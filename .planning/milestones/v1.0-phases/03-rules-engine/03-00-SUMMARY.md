---
phase: 03-rules-engine
plan: "00"
subsystem: testing
tags: [rules-engine, wave-0, archunit, vitest, playwright, spring-boot]

requires:
  - phase: 02A-mail-ingestion
    provides: Gmail observed-message metadata and Gmail safety boundaries consumed by preview contracts
  - phase: 02B-billing-prepaid-credits
    provides: insufficient-credit error contract and PREVIEW call-site cost behavior
  - phase: 02C-llm-gateway
    provides: LlmGateway boundary, CallSite.PREVIEW, safe action allow-list, and privacy invariants
provides:
  - Phase 03 Wave 0 backend rules contract tests
  - Active ArchUnit guards for core.rules Spring AI/vendor isolation and cross-domain repository isolation
  - API rules endpoint contract scaffolds
  - Frontend rules feature, workspace, and Playwright golden-path contract scaffolds
affects: [03-rules-engine, 04-triage-convergence, apps-web-rules]

tech-stack:
  added: []
  patterns:
    - Disabled future-symbol tests with plan-specific enablement messages
    - Active source and architecture guards that pass before production rules classes exist
    - Conditional frontend source scans that become strict once rules production files land

key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/arch/RulesBoundaryArchTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/model/RuleAstContractTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceWave0Test.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RuleCompilerServiceWave0Test.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RuleEvaluatorWave0Test.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewServiceWave0Test.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationWave0Test.java
    - backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerWave0Test.java
    - apps/web/features/rules/components/RulesWorkspace.test.tsx
    - apps/web/__tests__/rules-feature-contract.test.ts
    - apps/web/e2e/rules.spec.ts
  modified:
    - backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java

key-decisions:
  - "Wave 0 future-symbol backend/API behavior is disabled with exact owner-plan messages instead of adding production scaffolds."
  - "Active architecture guards allow an empty future core.rules package now, then enforce once rules production classes appear."
  - "Frontend Wave 0 source scans are conditional until the protected rules route lands, preventing false failures while still locking API/i18n ownership."

patterns-established:
  - "Wave 0 contract tests may use reflection, non-literal dynamic imports, and skipped bodies to avoid compiling against missing future classes."
  - "ArchUnit empty-package guards use allowEmptyShould(true) only for future domains that do not exist yet."

requirements-completed: [RULE-01, RULE-02, RULE-03, RULE-04, RULE-05, RULE-06, RULE-07]

duration: 15min
completed: 2026-05-10
---

# Phase 03 Plan 00: Rules Engine Wave 0 Summary

**Rules-engine validation spine covering backend, API, web component, source-contract, and browser-flow contracts before production rules code lands.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-05-09T19:01:44Z
- **Completed:** 2026-05-09T19:17:11Z
- **Tasks:** 2 completed
- **Files modified:** 12

## Accomplishments

- Added backend Wave 0 tests for AST vocabulary, persistence, compiler, evaluator, preview, and template materialization.
- Added active boundary guards for `core.rules`: no Spring AI/vendor SDK imports, no Gmail write/execution imports, and no cross-domain repository imports.
- Added API and frontend Wave 0 scaffolds for planned rules endpoints, feature-folder contracts, workspace states, and desktop/mobile Playwright golden path.

## Task Commits

Each task was committed atomically:

1. **Task 1: Backend Wave 0 tests** - `6d88535` (test)
2. **Task 2: API and frontend Wave 0 tests** - `59245ed` (test)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `backend/core/src/test/java/com/zeromail/core/arch/RulesBoundaryArchTest.java` - Active ArchUnit isolation checks for future `core.rules`.
- `backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java` - Adds the `core.rules` cross-domain repository boundary.
- `backend/core/src/test/java/com/zeromail/core/rules/model/RuleAstContractTest.java` - Disabled AST/action/semantic/unknown-node contracts for Plan 03-01.
- `backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceWave0Test.java` - Disabled Liquibase, JSONB, tenant, default-disabled, and provenance contracts for Plan 03-01.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RuleCompilerServiceWave0Test.java` - Disabled compiler/gateway/ambiguity contracts for Plans 03-02 and 03-03.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RuleEvaluatorWave0Test.java` - Disabled deterministic and semantic-deferred evaluator contracts for Plan 03-04.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewServiceWave0Test.java` - Active preview write-dependency source guard plus disabled preview contracts for Plan 03-05.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationWave0Test.java` - Disabled idempotent template materialization contracts for Plan 03-06.
- `backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerWave0Test.java` - Disabled rules controller endpoint, tenant, error, and privacy response contracts for Plan 03-07.
- `apps/web/features/rules/components/RulesWorkspace.test.tsx` - Active copy pin plus skipped future workspace state tests for Plan 03-08.
- `apps/web/__tests__/rules-feature-contract.test.ts` - Active source contract for rules API ownership, i18n copy, and future feature files.
- `apps/web/e2e/rules.spec.ts` - Skipped future desktop/mobile Playwright rules golden path for Plan 03-08.

## Decisions Made

- Used disabled/reflection-based Java tests for future production symbols so `compileTestJava` stays green without adding placeholder production classes.
- Used active ArchUnit and source-scan tests only where the current empty production surface can pass safely.
- Kept STATE.md and ROADMAP.md untouched per orchestrator constraint; the phase orchestrator owns shared tracking writes.

## Verification

- `./gradlew :backend:core:compileTestJava :backend:api:compileTestJava` - PASS
- `./gradlew :backend:core:test --tests "DomainBoundaryArchTests" --tests "RulesBoundaryArchTest"` - PASS
- `pnpm --filter web test -- --run features/rules __tests__/rules-feature-contract.test.ts` - PASS, 2 files passed, 6 tests passed, 5 skipped
- `./gradlew :backend:core:compileTestJava` after Task 1 - PASS
- `./gradlew :backend:api:compileTestJava` after Task 2 - PASS

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Allowed active ArchUnit guards to pass before `core.rules` exists**
- **Found during:** Task 1 (Backend Wave 0 tests)
- **Issue:** New ArchUnit rules failed on empty `core.rules` packages before production classes exist.
- **Fix:** Added `allowEmptyShould(true)` only to future-domain rules so they pass now and enforce once classes land.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/arch/RulesBoundaryArchTest.java`, `backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java`
- **Verification:** `./gradlew :backend:core:test --tests "DomainBoundaryArchTests" --tests "RulesBoundaryArchTest"` passed.
- **Committed in:** `6d88535`

**2. [Rule 1 - Bug] Removed IDE SQL-inspection errors for future `rules` table literals**
- **Found during:** Task 1 (Backend Wave 0 tests)
- **Issue:** JetBrains diagnostics flagged disabled future-table SQL in `RulePersistenceWave0Test` because the table intentionally does not exist yet.
- **Fix:** Built future table SQL through a constant so the disabled contract compiles and IDE diagnostics stay clean without creating schema scaffolds.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceWave0Test.java`
- **Verification:** JetBrains file problems returned no errors and `./gradlew :backend:core:compileTestJava` passed.
- **Committed in:** `6d88535`

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both fixes preserved the Wave 0 requirement that tests compile before production symbols exist. No production scaffolds were added.

## Issues Encountered

- Active ArchUnit guards initially failed on empty future packages. Fixed with scoped `allowEmptyShould(true)`.
- JetBrains SQL inspection flagged disabled future-table SQL. Fixed without changing runtime behavior.

## Known Stubs

Intentional Wave 0 disabled/skipped contracts:

| File | Lines | Reason |
|------|-------|--------|
| `backend/core/src/test/java/com/zeromail/core/rules/model/RuleAstContractTest.java` | 20, 56, 72, 90 | Plan 03-01 lands rules model symbols. |
| `backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceWave0Test.java` | 23, 35, 65, 74 | Plan 03-01 lands rules schema and repositories. |
| `backend/core/src/test/java/com/zeromail/core/rules/service/RuleCompilerServiceWave0Test.java` | 23, 35, 49, 72 | Plans 03-02/03-03 land gateway compile and compiler behavior. |
| `backend/core/src/test/java/com/zeromail/core/rules/service/RuleEvaluatorWave0Test.java` | 17, 32, 46 | Plan 03-04 lands deterministic evaluator symbols. |
| `backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewServiceWave0Test.java` | 57, 71, 85, 99 | Plan 03-05 lands preview service behavior. |
| `backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationWave0Test.java` | 19, 35, 50 | Plan 03-06 lands template materialization behavior. |
| `backend/api/src/test/java/com/zeromail/api/controllers/rules/RulesControllerWave0Test.java` | 47, 71, 86, 106, 127, 148 | Plan 03-07 lands rules API endpoints. |
| `apps/web/features/rules/components/RulesWorkspace.test.tsx` | 34, 44, 55, 64, 79 | Plan 03-08 lands the interactive workspace component. |
| `apps/web/e2e/rules.spec.ts` | 166 | Plan 03-08 lands the protected rules route and UI flow. |

These are intentional Wave 0 contracts and do not block this plan's goal.

## Threat Flags

None - this plan added test-only code and no new production endpoints, auth paths, persistence schema, file access surface, or network surface.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 03-01. The rules schema/model implementation can now enable the Plan 03-01 contracts without inventing test expectations mid-implementation.

## Self-Check: PASSED

- Verified summary file exists.
- Verified all created/modified files listed in the summary exist.
- Verified task commits `6d88535` and `59245ed` exist in git history.

---
*Phase: 03-rules-engine*
*Completed: 2026-05-10*
