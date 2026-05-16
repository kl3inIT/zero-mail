---
phase: 03-rules-engine
plan: "05"
subsystem: rules-engine
tags: [rules-preview, gmail-api, privacy, side-effect-free, deterministic-evaluation]

# Dependency graph
requires:
  - phase: 03-rules-engine/03
    provides: Rule compiler and persisted rule management used by saved-rule preview
  - phase: 03-rules-engine/04
    provides: Deterministic rule evaluator and action proposal merger used by preview rows
provides:
  - Side-effect-free Gmail rule preview orchestration
  - Transient bounded Gmail preview read boundary
  - Preview sample-size normalization for 10, 25, and 50 messages
  - Mechanical no-write and privacy regression tests for preview
affects: [rules-api, onboarding, rule-enable-gate, gmail-integration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Read-only Gmail preview fetch with request-scoped sanitized DTOs
    - Header-first matching with body-derived evidence fetched only when matcher AST requires it
    - ArchUnit/source boundary checks for no-write preview services

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/GmailPreviewUnavailableException.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/PreviewSampleSize.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RulePreviewCommand.java
    - backend/core/src/main/java/com/zeromail/core/rules/model/RulePreviewResult.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewDataService.java
    - backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewService.java
    - backend/core/src/test/java/com/zeromail/core/rules/privacy/RulePreviewPrivacyTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewDataServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewWriteBoundaryTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewDataService.java

key-decisions:
  - "Preview uses Gmail messages.get via Google API Client batch requests, with bounded sequential fallback, rather than a nonexistent Gmail batchGet endpoint."
  - "Preview fetches Gmail metadata by default and switches to full format only when MatcherNode.requiresBodyEvidence() is true."
  - "Successful saved-rule preview marks only the current entity version as previewed after the preview result is built."

patterns-established:
  - "Safe preview input: Gmail headers, subject excerpts, labels, and body-derived flags are sanitized and remain request-scoped."
  - "No-write preview proof: ArchUnit/source tests guard preview services against Gmail write clients, action executors, and future write packages."

requirements-completed: [RULE-03, RULE-04, RULE-05]

# Metrics
duration: 25min
completed: 2026-05-09
---

# Phase 03 Plan 05: Rule Preview Summary

**Side-effect-free Gmail rule preview with bounded transient reads, deterministic evaluation, safe evidence chips, and no-write regression tests**

## Performance

- **Duration:** 25 min
- **Started:** 2026-05-09T20:35:08Z
- **Completed:** 2026-05-09T20:51:25Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- Added a read-only Gmail preview boundary that selects recent observed messages, verifies a connected grant, fetches transient Gmail data, and returns request-scoped safe preview inputs.
- Added preview orchestration for saved rules and draft payloads using `RuleEvaluator` and `ActionProposalMerger`, with sample sizes restricted to 10, 25, or 50 and defaulting to 25.
- Added privacy and no-write tests proving preview does not invoke Gmail write behavior, LLM calls, action executors, or durable raw mail/prompt/completion storage.

## Preview Behavior

- `PreviewSampleSize` accepts only `10`, `25`, or `50`; `null` normalizes to `25`; any other value fails loudly.
- `RulePreviewDataService` maps disconnected, revoked, missing-grant, timeout, and Gmail-unavailable cases to `GmailPreviewUnavailableException` reason metadata for safe API error handling.
- `GmailPreviewReadService` reads recent observed Gmail message IDs ordered by `internal_date desc nulls last, observed_at desc`, uses `messages().get("me", id)`, and prefers Google API Client batch requests with a bounded sequential fallback.
- Preview uses Gmail `metadata` format and selected headers by default. It requests `full` format only when the matcher tree reports `requiresBodyEvidence()`.
- Preview has a default 5-second remote fetch budget through `RulePreviewDataService.DEFAULT_FETCH_BUDGET`.
- Preview output includes sanitized sender/domain, subject excerpt, message date, existing Gmail labels, proposed action chips, matched evidence chips, deferred semantic chips, conflict chips, and an explicit no-write notice key.

## Privacy And Safety

- Preview is side-effect-free by construction: it does not create labels, archive messages, save drafts, mutate enablement state, call an LLM, or persist raw Gmail headers/snippets/bodies/prompts/completions.
- Saved-rule preview evaluates the current disabled saved rule plus enabled sibling rules ordered by `order_index`; unrelated disabled rules are ignored.
- Successful saved-rule preview marks `lastPreviewedEntityVersion` only after result construction, enabling a later enable gate for that exact version.
- `RulePreviewPrivacyTest` uses sentinel body/header/prompt/completion strings and verifies they do not appear in durable preview paths.
- `RulePreviewWriteBoundaryTest` mechanically fails if preview services depend on Gmail write clients, action executors, or Phase 4 write packages.

## Task Commits

Each task was committed atomically:

1. **Task 1: Preview data fetch boundary** - `61e098e` (`feat`)
2. **Task 2: Preview orchestration and privacy tests** - `c8af53d` (`feat`)

**Plan metadata:** committed separately after summary creation.

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java` - Read-only transient Gmail fetch service for preview samples.
- `backend/core/src/main/java/com/zeromail/core/rules/model/GmailPreviewUnavailableException.java` - Safe preview-unavailable reason wrapper for API mapping.
- `backend/core/src/main/java/com/zeromail/core/rules/model/PreviewSampleSize.java` - Allowed preview sample size value object.
- `backend/core/src/main/java/com/zeromail/core/rules/model/RulePreviewCommand.java` - Saved-rule and draft preview command record.
- `backend/core/src/main/java/com/zeromail/core/rules/model/RulePreviewResult.java` - Impact summary, row summaries, and chip DTOs for preview results.
- `backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewDataService.java` - Gmail status checks, body-evidence policy, and safe preview input mapping.
- `backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewService.java` - Deterministic preview orchestration and saved-rule preview marker.
- `backend/core/src/test/java/com/zeromail/core/rules/privacy/RulePreviewPrivacyTest.java` - Sentinel privacy regression coverage.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewDataServiceTest.java` - Data boundary, Gmail unavailable, sample size, body policy, and timeout coverage.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewServiceTest.java` - Saved and draft preview behavior coverage.
- `backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewWriteBoundaryTest.java` - Mechanical write boundary coverage.

## Decisions Made

- Used Google API Client batch support around `messages().get` rather than inventing a Gmail batch endpoint.
- Kept Gmail data request-scoped and sanitized before it crosses from Gmail integration into rules preview evaluation.
- Made the preview marker update a post-result saved-rule concern so failed previews cannot satisfy later enablement checks.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added explicit Spring constructor selection for Gmail preview reads**
- **Found during:** Task 1 (Preview data fetch boundary)
- **Issue:** The production and test constructors could be ambiguous to Spring/IDE diagnostics without an explicit autowired constructor.
- **Fix:** Added `@Autowired` to the production `GmailPreviewReadService` constructor and kept the clock-aware constructor package-private for tests.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java`
- **Verification:** `RulePreviewDataServiceTest` and IDE build diagnostics passed for the preview files.
- **Committed in:** `61e098e`

**2. [Rule 3 - Blocking] Exposed the body-evidence fetch policy overload for orchestration tests**
- **Found during:** Task 2 (Preview orchestration and privacy tests)
- **Issue:** `RulePreviewService` and privacy tests needed to assert the computed body-evidence policy without re-parsing Gmail data.
- **Fix:** Made the boolean overload of `RulePreviewDataService.fetchPreviewInputs(...)` public.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/rules/service/RulePreviewDataService.java`
- **Verification:** `RulePreviewServiceTest`, `RulePreviewPrivacyTest`, and `RulePreviewWriteBoundaryTest` passed.
- **Committed in:** `c8af53d`

**3. [Rule 3 - Blocking] Matched boundary tests to the installed ArchUnit API**
- **Found during:** Task 2 (Preview orchestration and privacy tests)
- **Issue:** The initial boundary matcher used unsupported ArchUnit helpers for the installed version.
- **Fix:** Implemented the check using supported package/name predicates and source scanning.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/rules/service/RulePreviewWriteBoundaryTest.java`
- **Verification:** `RulePreviewWriteBoundaryTest` passed.
- **Committed in:** `c8af53d`

**4. [Rule 3 - Blocking] Suppressed known SQL resolution inspection in privacy test**
- **Found during:** Task 2 (Preview orchestration and privacy tests)
- **Issue:** IDE inspection flagged test-local SQL strings similarly to existing rules persistence tests.
- **Fix:** Added the same targeted `@SuppressWarnings("SqlResolve")` pattern used by existing rules tests.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/rules/privacy/RulePreviewPrivacyTest.java`
- **Verification:** JetBrains diagnostics and targeted tests passed.
- **Committed in:** `c8af53d`

---

**Total deviations:** 4 auto-fixed (1 missing critical, 3 blocking)
**Impact on plan:** All fixes were directly required to make the planned preview boundary testable and buildable. No Gmail writes, API endpoints, schema changes, LLM calls, or 03-06-owned template/onboarding files were added by this plan.

## Issues Encountered

- JetBrains build diagnostics surfaced unrelated concurrent 03-06 work in onboarding/template materialization files. Those files were left untouched per the wave constraint.

## Known Stubs

None.

## Verification

- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RulePreviewDataServiceTest"` - passed.
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RulePreviewServiceTest" --tests "com.zeromail.core.rules.privacy.RulePreviewPrivacyTest"` - passed.
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RulePreviewWriteBoundaryTest"` - passed.
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.rules.service.RulePreviewServiceTest" --tests "com.zeromail.core.rules.service.RulePreviewWriteBoundaryTest" --tests "com.zeromail.core.rules.privacy.RulePreviewPrivacyTest"` - passed.
- `.\gradlew.bat :backend:core:test --tests "RulesBoundaryArchTest"` - passed.
- JetBrains build diagnostics for the new Task 2 files - passed; unrelated concurrent 03-06 files reported separate compile issues and were not modified.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Rule API/UI work can call `RulePreviewService` to show safe preview impact and evidence before enabling a rule.
- Later enablement work can rely on `lastPreviewedEntityVersion` being set only after successful saved-rule preview.
- Phase 4 write-action work should keep action executors separate from preview services; the boundary test now guards that dependency.

---
*Phase: 03-rules-engine*
*Completed: 2026-05-09*

## Self-Check: PASSED

- Found summary file: `.planning/phases/03-rules-engine/03-05-SUMMARY.md`
- Found task commit: `61e098e`
- Found task commit: `c8af53d`
- Stub scan found only ordinary null guards and test path scanning helpers; no TODO/FIXME/placeholders or unwired mock data in 03-05 files.
- Threat scan found no unplanned endpoints, auth paths, schema changes, Gmail write paths, or durable raw-mail storage surfaces.
