---
phase: 09-user-settings-ui-on-curated-catalog
plan: 01
subsystem: database
tags: [postgres, liquibase, jpa, byok, settings, safety-net, testing]

requires: []
provides:
  - Phase 9 database foundation changesets 094 through 097
  - JPA scaffolding for assistant settings, safety-net metadata, triage audit badge, and user BYOK keys
  - Wave 0 validation stubs for backend and Playwright test surfaces
affects: [phase-09-settings-ui, byok, assistant-settings, sender-safety-net, triage-audit]

tech-stack:
  added: []
  patterns:
    - Liquibase forward migration keeps legacy table intact during parallel Wave 1 work
    - BYOK user API key storage uses the existing single-blob RefreshTokenCipher envelope

key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/094-assistant-settings-phase9-columns.yaml
    - backend/core/src/main/resources/db/changelog/changes/095-assistant-knowledge-snippet-unique-title.yaml
    - backend/core/src/main/resources/db/changelog/changes/096-safety-net-pattern-kind-and-audit-badge.yaml
    - backend/core/src/main/resources/db/changelog/changes/097-user-byok-key-table.yaml
    - backend/core/src/main/java/com/zeromail/core/llm/byok/UserByokKeyEntity.java
    - backend/core/src/main/java/com/zeromail/core/llm/byok/UserByokKeyRepository.java
    - apps/web/e2e/ai-settings.spec.ts
  modified:
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/main/java/com/zeromail/core/chat/persistence/AssistantSettingsEntity.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantProtectedSenderObservationEntity.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java
    - .planning/phases/09-user-settings-ui-on-curated-catalog/09-VALIDATION.md

key-decisions:
  - "RefreshTokenCipher stores a single byte[] envelope, so user_byok_key has api_key_ciphertext only and no separate IV column."
  - "tenant_byok_credentials is intentionally left intact in Phase 9; changelog 097 only forward-migrates rows into user_byok_key."
  - "AssistantKnowledgeMemoryEntity already inherits created_at, updated_at, and version from AbstractAuditableEntity, so no duplicate field was added."

patterns-established:
  - "Phase 9 test stubs are discoverable but disabled, with ownership messages pointing to Wave 1 plans."
  - "UserByokKeyEntity stores provider and test result as checked string IDs with fail-loud enum helpers, not ordinal storage."

requirements-completed:
  - SET-VOICE-01
  - SET-VOICE-02
  - SET-VOICE-03
  - SET-VOICE-04
  - SET-VOICE-05
  - SET-VOICE-06
  - SET-VOICE-07
  - SET-BEHV-01
  - SET-BEHV-02
  - SET-BEHV-04
  - SET-SAFE-01
  - SET-SAFE-04
  - SET-AI-01
  - SET-AI-02
  - SET-AI-03
  - SET-AI-04

duration: 34min
completed: 2026-05-26
---

# Phase 09-01: User Settings Foundation Summary

**Liquibase schema, JPA scaffolding, and disabled Wave 0 validation stubs for the Phase 9 settings surface**

## Performance

- **Duration:** 34 min
- **Started:** 2026-05-26T17:05:00Z
- **Completed:** 2026-05-26T17:39:09Z
- **Tasks:** 3
- **Files modified:** 45

## Accomplishments

- Added Liquibase changesets 094..097 and registered them after 093 and before 098 in the master changelog.
- Extended assistant settings, sender safety-net, triage audit, and user BYOK JPA scaffolding for later Wave 1 services/controllers.
- Created all 34 Wave 0 stubs: 33 backend test classes and 1 Playwright spec, then flipped `09-VALIDATION.md` to `nyquist_compliant: true` and `wave_0_complete: true`.

## Task Commits

1. **Task 1: Schema foundation** - `7c819a5a` (`feat(09-01): add phase 9 schema foundation`)
2. **Task 2: Entity scaffolding** - `847e364c` (`feat(09-01): add phase 9 entity scaffolding`)
3. **Task 3: Validation stubs** - `c6276fc9` (`test(09-01): scaffold phase 9 validation stubs`)

## Files Created/Modified

- `094-assistant-settings-phase9-columns.yaml` - adds signature, tone, auto-draft, confidence, and sensitive-data settings columns.
- `095-assistant-knowledge-snippet-unique-title.yaml` - adds duplicate-title HALT precondition and tenant/title unique constraint.
- `096-safety-net-pattern-kind-and-audit-badge.yaml` - adds safety-net pattern metadata and triage audit badge column.
- `097-user-byok-key-table.yaml` - creates `user_byok_key`, includes `last_test_models_json JSONB`, and forward-migrates legacy rows while leaving `tenant_byok_credentials` intact.
- `UserByokKeyEntity.java` / `UserByokKeyRepository.java` - single-row-per-tenant BYOK scaffold for 09-04.
- 33 backend test stubs + `apps/web/e2e/ai-settings.spec.ts` - disabled placeholders owned by Wave 1 plans.

## Decisions Made

- **RefreshTokenCipher envelope:** verified single-blob envelope shape, so no separate IV column or entity field was added.
- **Legacy BYOK table:** retained `tenant_byok_credentials` for boot compatibility while 09-02 / 09-03 / 09-05 can still run before 09-04 removes legacy code paths.
- **095 duplicate handling:** migration halts on duplicate `(tenant_id, title)` rows instead of deduplicating automatically.
- **Assistant knowledge timestamps:** existing inheritance from `AbstractAuditableEntity` already maps `updated_at`, matching changelog 046.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Suppressed stale IDE ORM inspection for new safety-net columns**
- **Found during:** Task 2 verification
- **Issue:** JetBrains inspection still used the pre-096 table shape for `tenant_protected_sender_observation` and reported unresolved `pattern_kind` / `created_by_user`, while Liquibase/Testcontainers and Gradle compile succeeded.
- **Fix:** Applied the same `JpaDataSourceORMInspection` suppression pattern already used by nearby JPA entities.
- **Files modified:** `TenantProtectedSenderObservationEntity.java`
- **Verification:** JetBrains file problems now report warnings only; Gradle core compile succeeds.
- **Committed in:** `847e364c`

---

**Total deviations:** 1 auto-fixed (blocking verification noise).
**Impact on plan:** No runtime scope change; schema and JPA mappings remain as planned.

## Issues Encountered

- The plan's Playwright list command was adapted from `pnpm --filter web playwright ...` to `pnpm --filter web exec playwright ...` because `playwright` is not a package script in `apps/web/package.json`.
- The earlier all-module filtered Gradle test command timed out, so verification was split by module.

## Verification

- `./gradlew.bat :backend:core:test --tests "*LiquibaseStartupTest*" --tests "*ChangelogValidationTest*" --tests "*Settings*" --tests "*Knowledge*" --tests "*VoiceGeneration*" --tests "*Byok*" --tests "*SafetyNet*" --tests "*Draft*" --tests "*Triage*"` - passed.
- `./gradlew.bat :backend:api:test --tests "*Settings*" --tests "*Knowledge*" --tests "*Byok*" --tests "*SafetyNet*" --tests "*Triage*" --tests "*SpringAiObservationDisabledTest*"` - passed.
- `./gradlew.bat :backend:worker:test --tests "*Draft*" --tests "*Triage*" --tests "*SafetyNet*"` - passed.
- `./gradlew.bat :backend:core:compileJava :backend:core:compileTestJava` - passed after final annotation/suppression edit.
- `pnpm --filter web exec playwright test --list ai-settings.spec.ts` - listed 1 skipped Phase 9 golden-path test.
- Acceptance grep for `api_key_iv`, `ai_provider_mode`, `CallSite.CHAT`, `.ordinal()`, and Lombok annotations across touched files returned no matches.
- Stub existence check returned `expected=34 missing=0`.
- `git diff --check` - clean.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Wave 1 plans can build against the settled DB shape and can fill the pre-created test surfaces without adding new class names. BYOK work in 09-04 can rely on `user_byok_key.last_test_models_json`; sender safety-net work can use pattern kind/user-created metadata; triage audit display work can use `blocked_by_safety_net_pattern`.
