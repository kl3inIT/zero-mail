---
phase: 05B-user-surface-ai-draft-replies
plan: 07
subsystem: testing
tags: [ai-eval, junit, privacy, ci, validation, uat]

requires:
  - phase: 05B-01..06
    provides: threaded draft writes, draft generation services, reply-status read side, REST endpoints, and needs-reply web surface
provides:
  - Deterministic `:backend:core:aiEval` source set with CI job coverage
  - Synthetic draft and classifier eval fixtures with deterministic dims 4/6/7/8 green
  - `DraftPrivacySweepTest` proving draft/classify/list paths leak no content through logs, exceptions, or persistence
  - Completed Phase 5B validation and UAT artifacts
  - DRFT-01..DRFT-04 requirement closure and Phase 5B roadmap completion
affects: [phase-05B, phase-5C, phase-6-launch, ai-eval, privacy]

tech-stack:
  added: []
  patterns:
    - Separate `aiEval` JUnit source set excluded from `check` but invoked by CI
    - Synthetic fixture privacy linting for eval corpora
    - FND-03-style draft privacy sweep over logs, exception chains, projection reads, and persistence

key-files:
  created:
    - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftThreadingEvalTest.java
    - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftSafetyEvalTest.java
    - backend/core/src/aiEval/java/com/zeromail/core/aiEval/ClassifierAccuracyEvalTest.java
    - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftTokenBudgetEvalTest.java
    - backend/core/src/aiEval/resources/fixtures/draft/
    - backend/core/src/aiEval/resources/fixtures/classifier/
    - backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacySweepTest.java
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-UAT.md
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-07-SUMMARY.md
  modified:
    - backend/core/build.gradle.kts
    - .github/workflows/ci.yml
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md
    - .planning/phases/05B-user-surface-ai-draft-replies/05B-VALIDATION.md
    - .planning/REQUIREMENTS.md
    - .planning/ROADMAP.md

key-decisions:
  - "DRFT-04 closes as Complete because deterministic dim 7 passed at 22/22 classifier fixtures with 7 edge cases."
  - "LLM-judge dims 1/2/3/5 remain report-only until human/judge calibration reaches the AI-SPEC threshold."
  - "Draft privacy sweep lives in backend/core and exercises core projection services directly; backend/api controller behavior is already covered by Plan 05 API tests."

patterns-established:
  - "Closure plans should record exact eval fixture counts and classifier accuracy in VALIDATION.md so requirement status is traceable."
  - "Privacy sweep tests assert exception cause chains as well as logs and persistence, because wrapper exceptions can otherwise leak cause messages."
  - "Phase UAT files distinguish automated closure gates from optional live Gmail/human tone review."

requirements-completed: [DRFT-01, DRFT-02, DRFT-03, DRFT-04]

duration: 51min
completed: 2026-05-13
---

# Phase 05B Plan 07: Closure and Eval Gate Summary

**Deterministic draft eval gates, privacy sweep, and phase sign-off for AI draft replies**

## Performance

- **Duration:** 51 min
- **Started:** 2026-05-13T07:15:00+07:00
- **Completed:** 2026-05-13T08:06:00+07:00
- **Tasks:** 2
- **Files modified:** 40+

## Accomplishments

- Added the `backend/core/src/aiEval` source set, Gradle `aiEval` task, and CI job running `./gradlew --no-daemon :backend:core:aiEval -PdeterministicOnly`.
- Added deterministic eval dimensions for safety/no-auto-send, threading headers, classifier accuracy, and token budget, plus 15 draft fixtures and 22 classifier fixtures.
- Updated `05B-AI-SPEC.md` to use `CallSite.DRAFT`; `DRAFT_REPLY` now has no matches.
- Added `DraftPrivacySweepTest`, covering success, `SafetyViolationException`, and Gmail-fetch failure paths across logs, exception chains, projection reads, `triage_audit`, and `thread_reply_status`.
- Filled `05B-VALIDATION.md`, wrote `05B-UAT.md`, closed DRFT-01..DRFT-04, updated WEB-02 progress, and marked Phase 5B complete in the roadmap.

## Task Commits

1. **Task 1: aiEval tagged source set + deterministic eval dims 4/6/7/8 + fixture seeds** - `ae8d477` (`test`)
2. **Task 2: DraftPrivacySweepTest + full gates + VALIDATION/UAT + REQUIREMENTS/ROADMAP flip** - `798e020` (`test`)

**Plan metadata:** this summary commit.

## Files Created/Modified

- `backend/core/build.gradle.kts` - Adds `aiEval` source set and `aiEval` task with `-PdeterministicOnly` / `-PjudgeOnly`.
- `.github/workflows/ci.yml` - Adds the separate Backend AI Eval job.
- `backend/core/src/aiEval/java/com/zeromail/core/aiEval/*` - Deterministic eval tests for dimensions 4, 6, 7, and 8.
- `backend/core/src/aiEval/resources/fixtures/draft/*` - 15 synthetic draft fixtures plus README.
- `backend/core/src/aiEval/resources/fixtures/classifier/*` - 22 synthetic classifier fixtures, 7 edge cases, plus README.
- `backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacySweepTest.java` - FND-03-style draft privacy sweep.
- `.planning/phases/05B-user-surface-ai-draft-replies/05B-VALIDATION.md` - Approved validation sign-off with eval results.
- `.planning/phases/05B-user-surface-ai-draft-replies/05B-UAT.md` - Phase UAT checklist and replay commands.
- `.planning/REQUIREMENTS.md` - DRFT-01..04 Complete; WEB-02 reflects 5B draft-review completion and Phase 5C analytics remainder.
- `.planning/ROADMAP.md` - Phase 5B marked complete with 8/8 plans.

## Decisions Made

- Marked DRFT-04 Complete because the classifier accuracy gate passed, not because the gap was documented away.
- Kept LLM judge dimensions report-only in docs and CI semantics until the AI-SPEC calibration gate is met.
- Used the existing core query services in `DraftPrivacySweepTest` for audit/needs-reply list coverage because the core module cannot depend on backend/api controllers.

## Deviations from Plan

None - plan executed within the planned closure scope.

## Issues Encountered

- First `./gradlew.bat clean check --console=plain` failed only on Spotless formatting for the new test. Ran `./gradlew.bat :backend:core:spotlessApply --console=plain`; the rerun passed.
- JetBrains MCP `get_file_problems` timed out twice during this plan. Verification fell back to focused Gradle test, full `clean check`, and deterministic eval, all green.

## Verification

- `./gradlew.bat :backend:core:test --tests com.zeromail.core.draft.DraftPrivacySweepTest --console=plain` - PASS
- `./gradlew.bat clean check --console=plain` - PASS
- `./gradlew.bat :backend:core:aiEval -PdeterministicOnly --console=plain` - PASS
- `pnpm -C apps/web typecheck` - PASS
- `pnpm -C apps/web lint` - PASS
- `pnpm -C apps/web test` - PASS (39 files, 236 tests)
- `pnpm -C apps/web i18n:check` - PASS
- `rg -n "DRAFT_REPLY" .planning/phases/05B-user-surface-ai-draft-replies/05B-AI-SPEC.md` - no matches
- Draft fixture count: 15 JSON files; classifier fixture count: 22 JSON files; classifier edge cases: 7.
- Classifier dim 7 result: 22/22 correct (100%), no one-direction skew.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 5B is complete. Phase 5C can build analytics/digest on the completed web shell, audit endpoint, and metadata-only privacy posture. Launch hardening should still include human tone review, Vietnamese copy review, and judge-dim calibration before promoting LLM-judge dims 1/2/3/5 to required CI.

---
*Phase: 05B-user-surface-ai-draft-replies*
*Completed: 2026-05-13*

## Self-Check: PASSED
