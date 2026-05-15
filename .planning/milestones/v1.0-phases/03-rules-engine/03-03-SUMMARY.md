---
phase: 03-rules-engine
plan: "03"
subsystem: rules-engine
tags: [rules-engine, llm-gateway, crud, optimistic-locking, tenant-isolation]

requires:
  - phase: 03-rules-engine
    provides: Rules domain model, JSONB persistence, and rule_compile gateway result
  - phase: 02C-llm-gateway
    provides: LlmGateway.compileRule(CallSite.PREVIEW, payload) and rule_compile tool boundary
provides:
  - Rules compiler service with fail-closed rule_compile validation
  - Distinct compiled, clarificationRequired, and invalid compile result states
  - Tenant-qualified transactional rule CRUD, enable, preview-marker, delete, and reorder service
  - Preview-before-enable and full-list optimistic reorder invariants
affects: [03-rules-engine, 04-triage-convergence, backend-api-rules]

tech-stack:
  added: []
  patterns:
    - Rules-owned validation of untrusted LLM tool arguments before persistence
    - Tenant-qualified native state flips for preview/enable/disable that preserve definition entityVersion
    - Full-list reorder commands carrying RuleOrderEntry(ruleId, entityVersion)

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleCompileCommand.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleCompileResult.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleClarificationQuestion.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleCreateCommand.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleUpdateCommand.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleReorderCommand.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleOrderEntry.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleValidationException.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RuleCompilerService.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RuleCompileResultValidator.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RuleManagementService.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RuleCompilerServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RuleManagementServiceTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java

key-decisions:
  - "Compile validation returns invalid for malformed or unsafe model output instead of rendering validation errors as clarification prompts."
  - "RuleManagementService keeps preview/enable/disable from advancing entityVersion so lastPreviewedEntityVersion can represent the exact definition version previewed."
  - "STATE.md and ROADMAP.md were left untouched per phase-orchestrator single-writer constraint."

patterns-established:
  - "RuleCompilerService binds TenantContext around LlmGateway.compileRule while accepting tenantId explicitly in RuleCompileCommand."
  - "Rule updates clear preview state and disable the rule; template-derived rules become customized only when source text, matcher AST, or action intents change."
  - "Rule reorders validate the submitted set equals the tenant's current rule set and every submitted entityVersion matches before writing any order_index values."

requirements-completed: [RULE-01, RULE-02, RULE-06]

duration: 15min
completed: 2026-05-10
---

# Phase 03 Plan 03: Rule Compiler and Management Summary

**Rules compiler validation and tenant-safe rule CRUD/reorder services with preview-gated enablement.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-05-09T20:07:00Z
- **Completed:** 2026-05-09T20:21:34Z
- **Tasks:** 2 completed
- **Files modified:** 14

## Accomplishments

- Added `RuleCompilerService` and `RuleCompileResultValidator` to call `LlmGateway.compileRule(CallSite.PREVIEW, payload)` and validate untrusted `rule_compile` output into bounded `rules.v1` matcher/action JSON.
- Added compile result states for `COMPILED`, `CLARIFICATION_REQUIRED`, and `INVALID`, including single-question clarification validation with English/Vietnamese language handling.
- Added `RuleManagementService` with transactional create, update, preview marker, enable/disable, delete, and full-list optimistic reorder behavior.
- Locked edited-rule semantics: updates disable the rule and clear preview eligibility, while template customization only flips on source/matcher/action definition changes.

## Task Commits

Each task was committed atomically:

1. **Task 1: Rule compiler and result validator** - `b2a1064` (feat)
2. **Task 2: Rule management service** - `caea219` (feat)

**Plan metadata:** pending final docs commit

## Files Created/Modified

- `RuleCompileCommand.java`, `RuleCompileResult.java`, `RuleClarificationQuestion.java` - Compiler input/output records and clarification payload.
- `RuleCompileResultValidator.java` - Fail-closed validation for schema version, matcher nodes, action intents, clarification payloads, string bounds, RE2J regex, and deferred semantic nodes.
- `RuleCompilerService.java` - Builds compiler payload and calls `LlmGateway.compileRule` with `CallSite.PREVIEW` and privacy-safe logs.
- `RuleCreateCommand.java`, `RuleUpdateCommand.java`, `RuleReorderCommand.java`, `RuleOrderEntry.java`, `RuleValidationException.java` - Management command and error contract records.
- `RuleManagementService.java` - Tenant-qualified CRUD, preview marker, enable/disable, delete, and optimistic reorder state transitions.
- `RuleEntity.java` - Adds preview clearing and excludes preview/enabled state from definition optimistic locking.
- `RuleCompilerServiceTest.java` - Covers happy path, unsafe actions, clarification safety, EN/VI language handling, unknown tool, and unknown matcher rejection.
- `RuleManagementServiceTest.java` - Covers cross-tenant denial, preview-before-enable, update reset, customization semantics, reorder all-or-nothing, and delete normalization.

## Decisions Made

- The compiler returns an `INVALID` result for unsafe or malformed model output; only ambiguity or missing resolvable slots can return `CLARIFICATION_REQUIRED`.
- Source-language detection is deterministic first; model-provided language is used only when the deterministic heuristic is unknown and the model value is one of `en`, `vi`, or `unknown`.
- Preview, enable, and disable use tenant-qualified native updates that do not advance entityVersion; definition edits and reorders still advance Hibernate optimistic version.

## Verification

- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RuleCompilerServiceTest"` - PASS
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RuleManagementServiceTest"` - PASS
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RuleCompilerServiceTest" --tests "com.zeromail.core.rules.service.RuleManagementServiceTest"` - PASS
- `.\gradlew.bat :backend:core:test --tests "RulesBoundaryArchTest"` - PASS
- `rg -n "org\.springframework\.ai" backend/core/src/main/java/com/zeromail/core/rules` - PASS, no matches.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Preserved previewed entity-version semantics for preview/enable state flips**
- **Found during:** Task 2 (Rule management service)
- **Issue:** Entity-managed preview metadata updates advanced Hibernate `version`, so `lastPreviewedEntityVersion == entityVersion` was false immediately after preview.
- **Fix:** Implemented tenant-qualified native updates for preview marker, enable, and disable state changes so definition `entityVersion` remains stable across non-definition transitions.
- **Files modified:** `RuleEntity.java`, `RuleManagementService.java`, `RuleManagementServiceTest.java`
- **Verification:** `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RuleManagementServiceTest"` passed.
- **Committed in:** `caea219`

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** The fix preserves the planned preview-before-enable invariant and does not expand the rules service surface beyond the requested state transitions.

## Issues Encountered

- A plan-level verification attempt ran two Gradle `:backend:core:test` invocations concurrently and one failed with a Gradle test-results `NoSuchFileException`. The same compiler/management test command was rerun alone and passed; the ArchUnit command also passed.
- Plan 03-04 committed work between the two 03-03 task commits. No 03-04-owned files were staged or modified by this plan.

## Known Stubs

None. Stub scan found only intentional null-state fields for inactive compile result variants and normal validation null checks.

## Threat Flags

None. This plan added planned backend service and persistence-state-transition logic only; no new endpoints, auth paths, external network calls, file access patterns, or unplanned schema changes were introduced.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 03-05/03-07 consumers. API and preview layers can call the compiler and management services without importing Spring AI, parsing model prose, or enabling edited rules before the current entity version has been previewed.

## Self-Check: PASSED

- Verified summary target path is being created at `.planning/phases/03-rules-engine/03-03-SUMMARY.md`.
- Verified key created files exist: `RuleCompilerService.java`, `RuleManagementService.java`, `RuleCompilerServiceTest.java`, and `RuleManagementServiceTest.java`.
- Verified task commits `b2a1064` and `caea219` exist in git history.

---
*Phase: 03-rules-engine*
*Completed: 2026-05-10*
