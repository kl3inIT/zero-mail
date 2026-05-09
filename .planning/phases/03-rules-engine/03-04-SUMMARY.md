---
phase: 03-rules-engine
plan: "04"
subsystem: rules-engine
tags: [rules-engine, deterministic-evaluator, re2j, tri-state, action-dedupe]

requires:
  - phase: 03-rules-engine
    provides: Rules.v1 matcher and action model from Plan 03-01
  - phase: 03-rules-engine
    provides: Rule compile gateway contract from Plan 03-02
provides:
  - Deterministic tri-state matcher evaluator for the Phase 3 matcher vocabulary
  - Preview-safe evaluation input/result records with ordered evidence
  - Action proposal model with provenance-preserving dedupe
  - Rule conflict warning taxonomy and merger behavior
affects: [03-rules-engine, 04-triage-convergence, rules-preview]

tech-stack:
  added: []
  patterns:
    - Tri-state rule evaluation using MATCHED, NOT_MATCHED, and DEFERRED states
    - RE2J-only subject regex evaluation in rules services
    - Warning-only rule action conflict reporting with safe count metadata

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/rules/model/MatcherEvaluationState.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleEvaluationInput.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleEvaluationResult.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/ActionProposal.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RuleConflictType.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RuleEvaluator.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/ActionProposalMerger.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RuleEvaluatorTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/ActionProposalMergerTest.java
  modified: []

key-decisions:
  - "RuleEvaluator has no LlmGateway dependency; SEMANTIC_INTENT always returns DEFERRED evidence."
  - "RuleEvaluationInput carries only sanitized preview-safe metadata and derived flags, never raw body/header content."
  - "ActionProposalMerger returns conflict warnings only; warnings do not block proposal creation."
  - "STATE.md and ROADMAP.md were left untouched per user/orchestrator single-writer constraint."

patterns-established:
  - "Boolean matcher tri-state semantics: ALL short-circuits to NOT_MATCHED on any miss, otherwise DEFERRED if any child is deferred; ANY returns MATCHED on any match, otherwise DEFERRED if any child is deferred."
  - "Exact duplicate action intents merge by action record equality while preserving contributing rule ids, rule names, and evidence ids in rule order."

requirements-completed: [RULE-03, RULE-04]

duration: 13min
completed: 2026-05-10
---

# Phase 03 Plan 04: Deterministic Evaluator and Action Proposal Merging Summary

**Tri-state rules.v1 matcher evaluation with RE2J regex safety, deferred semantic evidence, and provenance-preserving safe-action proposal merging.**

## Performance

- **Duration:** 13 min
- **Started:** 2026-05-09T20:07:12Z
- **Completed:** 2026-05-09T20:20:02Z
- **Tasks:** 2 completed
- **Files modified:** 9

## Accomplishments

- Added `RuleEvaluator` for every Phase 3 matcher family: sender, recipients, subject contains/equals/regex, Gmail labels/categories, attachments, newsletter/list-unsubscribe signals, message age/date, boolean groups, and semantic deferral.
- Added preview-safe evaluation records that carry sanitized metadata, derived body evidence flags, ordered matched/deferred evidence ids, and no raw Gmail content.
- Added `ActionProposalMerger` to merge exact duplicate `label`, `archive`, and `save_draft` intents while preserving ordered rule/evidence provenance.
- Added `RuleConflictType` warnings for multiple labels, archive+draft combinations, duplicate draft intents, and category-label mismatch.

## Task Commits

Each task was committed atomically:

1. **Task 1: Deterministic evaluator** - `c7fab37` (feat)
2. **Task 2: Action dedupe and conflict warnings** - `a7c9fd2` (feat)

**Plan metadata:** recorded in final docs commit.

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/rules/model/MatcherEvaluationState.java` - Adds stable tri-state matcher evaluation ids.
- `backend/core/src/main/java/com/zeromail/core/rules/model/RuleEvaluationInput.java` - Adds sanitized preview-safe input for deterministic evaluation.
- `backend/core/src/main/java/com/zeromail/core/rules/model/RuleEvaluationResult.java` - Adds immutable ordered evidence output for matched/deferred results.
- `backend/core/src/main/java/com/zeromail/core/rules/model/ActionProposal.java` - Adds safe action proposal provenance and exact duplicate merge behavior.
- `backend/core/src/main/java/com/zeromail/core/rules/model/RuleConflictType.java` - Adds warning taxonomy with stable ids.
- `backend/core/src/main/java/com/zeromail/core/rules/service/RuleEvaluator.java` - Evaluates typed `MatcherNode` trees deterministically and uses RE2J for subject regex matching.
- `backend/core/src/main/java/com/zeromail/core/rules/service/ActionProposalMerger.java` - Merges proposals, evaluates ordered rule candidates, and emits non-blocking warnings.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RuleEvaluatorTest.java` - Covers matcher vocabulary, tri-state semantics, semantic deferral, RE2J source guard, body-evidence flags, and stable ordering.
- `backend/core/src/test/java/com/zeromail/core/rules/service/ActionProposalMergerTest.java` - Covers dedupe provenance, warnings, safe metadata, rule ordering, and disabled-rule preview inclusion.

## Decisions Made

- Used `MatcherEvaluationState` as a fail-loud identified enum so results are not bare booleans.
- Kept `RuleEvaluator` constructor dependency-free; tests verify no `LlmGateway` field is present.
- Kept conflict metadata count/type based. Rule names are preserved in proposal provenance as required, but warning metadata does not include subject/body/header content.
- Left shared orchestration files untouched; `.planning/STATE.md` was dirty before and after this plan.

## Verification

- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RuleEvaluatorTest"` - PASS
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.ActionProposalMergerTest"` - PASS
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RuleEvaluatorTest" --tests "com.zeromail.core.rules.service.ActionProposalMergerTest"` - PASS
- JetBrains file problem checks for all new production/test files - PASS, no errors after cleanup.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed evaluator source-scan test path for Gradle module cwd**
- **Found during:** Task 1 (Deterministic evaluator)
- **Issue:** `RuleEvaluatorTest` initially scanned `backend/core/src/...`, but Gradle runs the core test task with `backend/core` as the working directory.
- **Fix:** Updated the source-scan path to `src/main/java/...` so the RE2J/java-regex guard runs reliably in the module test task.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/rules/service/RuleEvaluatorTest.java`
- **Verification:** Re-ran `RuleEvaluatorTest`; PASS.
- **Committed in:** `c7fab37`

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** The fix only corrected the test harness path; production behavior and plan scope were unchanged.

## Issues Encountered

- Plan 03-03 landed a concurrent commit while this plan was running. An attempted Task 1 formatter amend briefly targeted the newer 03-03 HEAD; this was recovered by resetting back to the original 03-03 commit `b2a1064` without a hard reset, restoring only the Task 1 formatter diffs, and then committing Task 2 separately. Final 03-04 commits contain only 03-04-owned files.
- A pre-existing `.planning/STATE.md` modification remained dirty and was intentionally left unstaged per the user constraint.

## Known Stubs

None. Stub scan found no TODO/FIXME/placeholder text or unwired mock data in the 03-04-created files.

## Threat Flags

None. This plan added pure deterministic evaluation and merge services only; it introduced no endpoints, auth paths, persistence schema, file access, Gmail writes, or LLM calls.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 03-05 preview orchestration. The preview layer can now use `MatcherNode.requiresBodyEvidence()` for fetch decisions, `RuleEvaluator` for side-effect-free match results, and `ActionProposalMerger` for explainable action chips plus warning summaries.

## Self-Check: PASSED

- Verified summary file exists.
- Verified all created files listed above exist.
- Verified task commits `c7fab37` and `a7c9fd2` exist in git history.
- Verified final plan test command passed.

---
*Phase: 03-rules-engine*
*Completed: 2026-05-10*
