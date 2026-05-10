---
phase: 03-rules-engine
plan: "01"
subsystem: database
tags: [rules-engine, spring-modulith, liquibase, postgresql-jsonb, re2j]

requires:
  - phase: 03-rules-engine
    provides: Wave 0 rules model, persistence, and boundary contracts
  - phase: 02C-llm-gateway
    provides: Safe action allow-list used by rules action intents
provides:
  - core.rules Spring Modulith package with model, persistence, and service sub-package boundaries
  - rules.v1 matcher AST and safe action intent model with fail-loud schema/action validation
  - Rules and rule_template_catalog PostgreSQL schema with JSONB columns, btree indexes, GIN jsonb_path_ops indexes, and template idempotency constraint
  - Starter template catalog rows for archive-receipts, label-newsletters, pin-calendar, and gallery-only calendar-invites
affects: [03-rules-engine, 04-triage-convergence, apps-web-rules]

tech-stack:
  added:
    - com.google.re2j:re2j:1.8
  patterns:
    - Sealed matcher/action model with explicit rules.v1 schema validation before service use
    - JSONB persistence through Hibernate @JdbcTypeCode(SqlTypes.JSON) with real Postgres round-trip tests
    - Liquibase DDL and seed data split into separate changelogs

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/rules/package-info.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/MatcherNode.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/ActionIntent.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleAstJsonValidator.java
    - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java
    - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleTemplateEntity.java
    - backend/core/src/main/resources/db/changelog/changes/021-rules-engine-schema.yaml
    - backend/core/src/main/resources/db/changelog/changes/022-rule-template-catalog-seed.yaml
    - backend/core/src/test/java/com/zeromail/core/rules/model/RuleModelTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceTest.java
  modified:
    - gradle/libs.versions.toml
    - backend/core/build.gradle.kts
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/test/java/com/zeromail/core/rules/model/RuleAstContractTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceWave0Test.java

key-decisions:
  - "Unknown matcher, action, language, template status, and schema-version ids fail loud via NoSuchElementException."
  - "RuleEntity exposes Hibernate optimistic-lock version as entityVersion, keeping it distinct from schema_version and last_previewed_entity_version."
  - "Starter template seed data is DB-backed and split from DDL; only materializable catalog rows align with onboarding selection keys."
  - "STATE.md and ROADMAP.md were left untouched per phase-orchestrator single-writer constraint."

patterns-established:
  - "MatcherNode.requiresBodyEvidence() is part of every matcher contract; current v1 metadata/header matchers return false, including deferred SEMANTIC_INTENT."
  - "Rule template catalog statuses are materializable, gallery_only, and deprecated; gallery_only rows are not onboarding materialization inputs."

requirements-completed: [RULE-02, RULE-04, RULE-06, RULE-07]

duration: 13min
completed: 2026-05-10
---

# Phase 03 Plan 01: Rules Domain Persistence Summary

**Rules.v1 model and PostgreSQL JSONB persistence foundation for tenant-owned rule definitions and starter template catalog rows.**

## Performance

- **Duration:** 13 min
- **Started:** 2026-05-09T19:20:47Z
- **Completed:** 2026-05-09T19:34:10Z
- **Tasks:** 2 completed
- **Files modified:** 29

## Accomplishments

- Added `core.rules` as a Spring Modulith domain with model, persistence, and service package boundaries.
- Added the rules.v1 matcher vocabulary, deferred-only `SEMANTIC_INTENT`, safe action intents aligned to `Action`, and RE2J-backed subject regex validation.
- Added `rules` and `rule_template_catalog` tables with JSONB AST/action columns, schema-version checks, btree indexes, explicit `jsonb_path_ops` GIN indexes, and partial unique template materialization protection.
- Seeded starter templates for `archive-receipts`, `label-newsletters`, `pin-calendar`, and gallery-only `calendar-invites`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Modulith package and model vocabulary** - `d0d3c58` (feat)
2. **Task 2: Liquibase schema and JPA persistence** - `2ad0e17` (feat)

**Plan metadata:** recorded in final docs commit

## Files Created/Modified

- `gradle/libs.versions.toml` - Added RE2J version/catalog entry.
- `backend/core/build.gradle.kts` - Added `libs.google.re2j` dependency.
- `backend/core/src/main/java/com/zeromail/core/rules/package-info.java` - Declares the `core.rules` Modulith boundary.
- `backend/core/src/main/java/com/zeromail/core/rules/model/*` - Adds rule id, language, schema version, matcher/action types, matcher nodes, action intents, validators, template status, and status view.
- `backend/core/src/main/java/com/zeromail/core/rules/persistence/*` - Adds JSONB-backed rule/template entities and repositories.
- `backend/core/src/main/resources/db/changelog/changes/021-rules-engine-schema.yaml` - Creates rule persistence tables, constraints, and indexes.
- `backend/core/src/main/resources/db/changelog/changes/022-rule-template-catalog-seed.yaml` - Seeds starter template catalog rows.
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` - Includes changelogs 021 and 022 in order.
- `backend/core/src/test/java/com/zeromail/core/rules/model/RuleAstContractTest.java` - Enables relevant Wave 0 model contracts.
- `backend/core/src/test/java/com/zeromail/core/rules/model/RuleModelTest.java` - Verifies fail-loud ids, semantic deferral, schema-version rejection, RE2J validation, and action alignment.
- `backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceWave0Test.java` - Enables and updates relevant Wave 0 persistence contracts to final table/column names.
- `backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceTest.java` - Verifies real Postgres JSONB round trips, indexes, partial uniqueness, template seeds, and onboarding key alignment.

## Decisions Made

- Used `rules.v1` as both the database `schema_version` check value and the matcher JSON root version. Unknown versions fail before model/service use.
- Stored matcher AST and action intents as JSONB strings validated through rules-owned validators. This keeps the persistence foundation simple while preserving typed model validation and future service control.
- Named the Hibernate optimistic-lock value `entityVersion` in `RuleEntity`, separate from AST `schema_version` and `last_previewed_entity_version`.
- Kept template catalog DDL and seed rows in separate changelogs so future seed updates do not churn table DDL.
- Skipped `STATE.md` and `ROADMAP.md` updates because this executor is running under a phase orchestrator that owns shared planning writes.

## Verification

- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.model.*"` - PASS
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.persistence.*"` - PASS
- `.\gradlew.bat :backend:core:test --tests "RulesBoundaryArchTest" --tests "DomainBoundaryArchTests"` - PASS
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.model.*" --tests "com.zeromail.core.rules.persistence.*"` - PASS

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Aligned Wave 0 unknown-matcher expectation with fail-loud enum policy**
- **Found during:** Task 1 (Modulith package and model vocabulary)
- **Issue:** The disabled Wave 0 unknown matcher test expected an `IllegalArgumentException`, while the project enum convention and this plan's acceptance criteria require unknown ids to throw `NoSuchElementException`.
- **Fix:** Enabled the contract and updated it to assert the `NoSuchElementException` root cause.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/rules/model/RuleAstContractTest.java`
- **Verification:** Model test slice passed.
- **Committed in:** `d0d3c58`

**2. [Rule 1 - Bug] Made JSONB round-trip assertion format-insensitive**
- **Found during:** Task 2 (Liquibase schema and JPA persistence)
- **Issue:** PostgreSQL canonicalized JSONB with spaces, causing a string-format-specific assertion to fail despite correct data.
- **Fix:** Updated the assertion to check stable JSON field/value tokens rather than exact spacing.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/rules/persistence/RulePersistenceTest.java`
- **Verification:** Persistence test slice passed.
- **Committed in:** `2ad0e17`

**3. [Rule 3 - Blocking] Suppressed IDE false positives for newly migrated tables**
- **Found during:** Task 2 (Liquibase schema and JPA persistence)
- **Issue:** JetBrains inspections did not resolve new Liquibase-managed tables before IDE datasource metadata refreshed, reporting false table/column errors.
- **Fix:** Added scoped inspection suppressions on the new entities and SQL-heavy persistence test.
- **Files modified:** `RuleEntity.java`, `RuleTemplateEntity.java`, `RulePersistenceTest.java`
- **Verification:** JetBrains file-problem checks returned no errors; Gradle persistence tests passed.
- **Committed in:** `2ad0e17`

---

**Total deviations:** 3 auto-fixed (2 bugs, 1 blocking tooling diagnostic)
**Impact on plan:** All fixes preserved the planned behavior and tightened the Wave 0 contracts around final implementation names and project enum policy.

## Issues Encountered

- Postgres JSONB canonical formatting changed whitespace on readback; fixed in tests without changing production behavior.
- Existing `.planning/STATE.md` was dirty before this executor started and was intentionally left unstaged per orchestration constraint.

## Known Stubs

None. Stub scan only found intentional null checks, not placeholder data or unwired UI.

## Threat Flags

None. This plan added the planned persistence schema and no unplanned endpoints, auth paths, file access paths, or network surfaces. Persistent entities contain matcher/action metadata only, with no raw Gmail headers, snippets, bodies, prompts, completions, or model raw output fields.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 03-02. Downstream compiler work can depend on `RuleSchemaVersion.RULES_V1`, the locked matcher/action vocabulary, RE2J regex validation, and durable JSONB persistence without schema churn.

## Self-Check: PASSED

- Verified summary file exists.
- Verified key created files exist: `MatcherNode.java`, `RuleEntity.java`, changelogs 021/022, and `RulePersistenceTest.java`.
- Verified task commits `d0d3c58` and `2ad0e17` exist in git history.

---
*Phase: 03-rules-engine*
*Completed: 2026-05-10*
