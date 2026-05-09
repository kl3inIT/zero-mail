---
phase: 03-rules-engine
plan: "06"
subsystem: rules-engine
tags: [rules-engine, onboarding, templates, idempotency, spring-modulith, postgresql]

requires:
  - phase: 03-rules-engine
    provides: Rules persistence, template catalog seed rows, and partial unique template materialization index
  - phase: 03-rules-engine
    provides: Rule management semantics for template customization preservation
provides:
  - DB-backed rule template catalog service with safe tenant-aware views
  - Onboarding selected-template read facade owned by core.onboarding
  - Idempotent template materialization service creating disabled rule rows from selected templates
  - Safe skipped-template metadata for unknown, deprecated, already materialized, and customized rules
affects: [03-rules-engine, 03-07-rules-api, 03-08-rules-ui, 04-triage-convergence]

tech-stack:
  added: []
  patterns:
    - Cross-domain onboarding reads go through OnboardingService, never onboarding persistence imports
    - Template materialization binds TenantContext before opening the write transaction
    - Partial unique-index conflicts are handled by retry/reload instead of application locking

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleTemplateView.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleTemplateMaterializationResult.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateCatalogService.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateMaterializationService.java
    - backend/core/src/test/java/com/zeromail/core/onboarding/service/OnboardingServiceSelectedTemplatesTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/persistence/RuleTemplateCatalogTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationServiceTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/onboarding/service/OnboardingService.java
    - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleTemplateRepository.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationWave0Test.java

key-decisions:
  - "Template catalog reads expose safe view metadata only; matcher/action JSON stays behind services."
  - "Materialized template rules pin template_key and template_version at creation time; Phase 3 does not migrate or overwrite later."
  - "Onboarding selected template reads bind tenant scope inside OnboardingService so callers do not import onboarding persistence."
  - "Template materialization uses the existing partial unique index as the concurrency boundary and reloads race winners safely."
  - "STATE.md and ROADMAP.md were left untouched per user/orchestrator single-writer constraint."

patterns-established:
  - "RuleTemplateCatalogService.listActiveTemplates returns materializable templates before gallery-only templates and marks gallery-only rows as not onboarding-sourced."
  - "RuleTemplateMaterializationResult carries counts plus safe skippedTemplates entries without source text, matcher JSON, action JSON, prompts, or mail content."
  - "Gradle verification for this concurrent wave used --no-daemon --max-workers=1 to avoid shared test-result writer contention."

requirements-completed: [RULE-07]

duration: 38min
completed: 2026-05-10
---

# Phase 03 Plan 06: Template Catalog and Materialization Summary

**DB-backed starter templates now materialize selected onboarding rules exactly once per tenant as disabled, version-pinned rule rows.**

## Performance

- **Duration:** 38 min
- **Started:** 2026-05-09T20:24:17Z
- **Completed:** 2026-05-09T21:01:33Z
- **Tasks:** 2 completed
- **Files modified:** 10

## Accomplishments

- Added `RuleTemplateCatalogService` and `RuleTemplateView` so active template rows are read from `rule_template_catalog` and exposed without raw matcher/action JSON.
- Added `OnboardingService.selectedEnabledTemplateKeys(UUID tenantId)` as the onboarding-owned facade for stable, tenant-scoped selected template keys.
- Added `RuleTemplateMaterializationService` and `RuleTemplateMaterializationResult` to create disabled rules from latest materializable template versions idempotently.
- Added integration coverage for seeded catalog rows, gallery-only metadata, tenant materialized/customized state, idempotency, concurrency, unknown/deprecated skips, and the rules/onboarding boundary.

## Task Commits

Each task was committed atomically:

1. **Task 1: Template catalog service** - `4edf1b3` (feat)
2. **Task 2: Onboarding materialization** - `7fb7dbd` (feat)

**Plan metadata:** recorded in final docs commit.

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/rules/model/RuleTemplateView.java` - Safe catalog projection with key, version, copy metadata, action summary, onboarding source flag, materialized flag, and customized flag.
- `backend/core/src/main/java/com/zeromail/core/rules/model/RuleTemplateMaterializationResult.java` - Materialization counts and safe skipped-template reason records.
- `backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateCatalogService.java` - DB-backed active catalog listing and template resolution.
- `backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateMaterializationService.java` - Idempotent selected-template materialization with tenant scope, transaction isolation, and unique-index retry/reload.
- `backend/core/src/main/java/com/zeromail/core/onboarding/service/OnboardingService.java` - Adds selected enabled template key facade and renames the repository field to a domain-revealing name.
- `backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleTemplateRepository.java` - Adds active/latest template lookup queries.
- `backend/core/src/test/java/com/zeromail/core/rules/persistence/RuleTemplateCatalogTest.java` - Verifies catalog seeds, safe views, gallery-only metadata, and materialized/customized view state.
- `backend/core/src/test/java/com/zeromail/core/onboarding/service/OnboardingServiceSelectedTemplatesTest.java` - Verifies selected-template facade stability and tenant scoping.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationServiceTest.java` - Verifies first-run creation, second-run idempotency, customized preservation, unknown/deprecated skip metadata, concurrency, and boundary ownership.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationWave0Test.java` - Retires the stale disabled Plan 03-06 Wave 0 stub with active contract checks.

## Decisions Made

- Materialization reads only `RuleTemplateStatus.MATERIALIZABLE` rows; `gallery_only` rows are visible in the catalog but explicitly not sourced from onboarding.
- Existing template-derived rules are never overwritten. Customized existing rules increment `customizedPreservedCount` and return `CUSTOMIZED_PRESERVED` skip metadata.
- Unknown or deprecated onboarding keys return `UNKNOWN_OR_DEPRECATED` in `skippedTemplates`; the result carries no source text, matcher JSON, action JSON, prompt, completion, or mail content.
- Tenant context is bound before opening the materialization transaction so Hibernate tenant ownership is populated correctly on new `RuleEntity` rows.

## Verification

- `.\\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.persistence.RuleTemplateCatalogTest"` - PASS.
- `.\\gradlew.bat --no-daemon --max-workers=1 :backend:core:test --tests "com.zeromail.core.onboarding.service.OnboardingServiceSelectedTemplatesTest" --tests "com.zeromail.core.rules.service.RuleTemplateMaterializationServiceTest" --tests "DomainBoundaryArchTests"` - PASS.
- `.\\gradlew.bat --no-daemon --max-workers=1 :backend:core:test --tests "com.zeromail.core.onboarding.service.OnboardingServiceSelectedTemplatesTest" --tests "com.zeromail.core.rules.service.RuleTemplateMaterializationServiceTest" --tests "com.zeromail.core.rules.persistence.RuleTemplateCatalogTest" --tests "com.zeromail.core.rules.service.RuleTemplateMaterializationWave0Test" --tests "DomainBoundaryArchTests"` - PASS.
- JetBrains file-problem checks for all 03-06 production/test files - PASS, no errors.
- JetBrains rebuild for 03-06 touched files - PASS after concurrent 03-05 preview compile fixes landed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Bound onboarding selected-template reads to tenant scope**
- **Found during:** Task 2 (Onboarding materialization)
- **Issue:** The first materialization test returned zero selected keys because the service method opened/read through JPA without an active `TenantContext` scope.
- **Fix:** `OnboardingService.selectedEnabledTemplateKeys(...)` now binds `TenantContext` around the repository read and returns stable enabled keys.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/onboarding/service/OnboardingService.java`, `backend/core/src/test/java/com/zeromail/core/onboarding/service/OnboardingServiceSelectedTemplatesTest.java`
- **Verification:** Task 2 and plan-level Gradle slices passed.
- **Committed in:** `7fb7dbd`

**2. [Rule 1 - Bug] Bound materialization tenant scope before transaction creation**
- **Found during:** Task 2 (Onboarding materialization)
- **Issue:** New `RuleEntity` inserts failed Hibernate tenant-owned persistence because the transaction opened before tenant scope was active.
- **Fix:** `RuleTemplateMaterializationService` now enters `TenantContext` first, then opens the `REQUIRES_NEW` transaction.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/rules/service/RuleTemplateMaterializationService.java`
- **Verification:** `RuleTemplateMaterializationServiceTest` passed under isolated Gradle.
- **Committed in:** `7fb7dbd`

**3. [Rule 2 - Missing Critical] Retired stale disabled Wave 0 materialization stub**
- **Found during:** Post-task stub scan
- **Issue:** `RuleTemplateMaterializationWave0Test` still contained disabled Plan 03-06 placeholder tests after the actual materialization service landed.
- **Fix:** Replaced the disabled placeholders with active reflection checks for the real service entrypoint, result contract, and onboarding-service boundary.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/rules/service/RuleTemplateMaterializationWave0Test.java`
- **Verification:** Plan-level Gradle slice passed with `RuleTemplateMaterializationWave0Test` included.
- **Committed in:** `7fb7dbd`

---

**Total deviations:** 3 auto-fixed (2 bugs, 1 missing critical test closure)
**Impact on plan:** All fixes were required for tenant-correct materialization and to close the Plan 03-06 Wave 0 contract. No API, schema, or frontend scope was added.

## Issues Encountered

- Concurrent Plan 03-05 preview files appeared during this run and initially caused unrelated compile errors. Those files were completed in 03-05 commits (`61e098e`, `c8af53d`, `2f5363c`); 03-06 staged only 03-06-owned files.
- The shared Gradle daemon intermittently failed with `NoSuchFileException` under concurrent test execution. Re-running verification with `--no-daemon --max-workers=1` produced clean passes.

## Known Stubs

None. Stub scan found no TODO/FIXME/placeholder markers in 03-06 changed files, and the stale disabled Wave 0 materialization stub was retired.

## Threat Flags

None. This plan added planned backend services and tests only. It introduced no endpoints, auth paths, schema changes, file access paths, Gmail writes, LLM calls, or new external network surface.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 03-07. The first rules API read can call `RuleTemplateMaterializationService.materializeSelectedTemplates(tenantId)` before listing rules, and template gallery endpoints can read safe catalog views from `RuleTemplateCatalogService`.

## Self-Check: PASSED

- Verified summary file is being created at `.planning/phases/03-rules-engine/03-06-SUMMARY.md`.
- Verified task commits `4edf1b3` and `7fb7dbd` exist in git history.
- Verified all key 03-06 created/modified files exist.
- Verified plan-level Gradle test command passed.

---
*Phase: 03-rules-engine*
*Completed: 2026-05-10*
