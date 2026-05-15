---
phase: 06-polish-casa-verified-launch
plan: 04
subsystem: infra
tags: [github-actions, workflow-call, release-gate, ci-refactor, playwright, k6]

requires:
  - phase: 06-polish-casa-verified-launch
    provides: golden-path spec, loadtest harness, and launch-only backend stubs from Plans 06-02 and 06-03
provides:
  - reusable `gates.yml` workflow that single-sources the four daily gate jobs
  - thin `ci.yml` caller that preserves PR/main behavior while delegating to `gates.yml`
  - tag-triggered `release.yml` with golden-path, loadtest, trust-story, and summary checks
affects: [06-05-launch-artifacts, launch-go-no-go, release-gates-summary, ci-workflows]

tech-stack:
  added: []
  patterns:
    - reusable workflow_call gate extraction
    - thin caller CI workflow with inherited secrets
    - rc-tag release workflow with an always-running summary job
    - single-sourced Playwright e2e gate folded into reusable gates

key-files:
  created:
    - .github/workflows/gates.yml
    - .github/workflows/release.yml
  modified:
    - .github/workflows/ci.yml
  deleted:
    - .github/workflows/e2e.yml

key-decisions:
  - "Fold the old standalone e2e workflow into gates.yml to eliminate duplication and keep the release and daily CI gates aligned."
  - "Keep ci.yml as a thin caller that preserves the existing PR + push:main trigger and concurrency contract."
  - "Use a dedicated rc-tag release workflow that adds golden-path, loadtest, trust-story-grep, and a single release-gates-summary check."
  - "Annotate every secrets: inherit site so the secret blast radius stays visible in the workflow files."

patterns-established:
  - "CI and release workflows should call the same reusable gate definitions rather than copy job bodies."
  - "Tag-triggered release workflows should aggregate into one always-running summary check."
  - "Release jobs that touch the workspace should pin working-directory to github.workspace."

requirements-completed: [SPEC-06-R3, SPEC-06-R4]

duration: 9 min
completed: 2026-05-15
---

# Phase 06 Plan 04: Release Gate Workflow Summary

**CI and rc-tag release now share one reusable gate definition, with the legacy standalone e2e workflow folded into the reusable set.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-05-15T06:24:00+07:00
- **Completed:** 2026-05-15T06:33:00+07:00
- **Tasks:** 3 completed
- **Files modified:** 4

## Accomplishments

- Extracted the four existing daily gate jobs into `.github/workflows/gates.yml`.
- Slimmed `.github/workflows/ci.yml` to a thin caller that preserves the existing PR and `main` push behavior.
- Added `.github/workflows/release.yml` with rc-tag-only release gates, golden-path, loadtest, trust-story-grep, and a single summary check.
- Folded `.github/workflows/e2e.yml` into the reusable gates set and removed the duplicate workflow file.

## Task Commits

1. **Task 1: Author reusable Gates workflow** - `0cbe7e2` (feat)
2. **Task 2: Slim CI and delete standalone e2e workflow** - `0cbe7e2` (feat)
3. **Task 3: Author rc-tag release workflow** - `0cbe7e2` (feat)

## Files Created/Modified

- `.github/workflows/gates.yml` - reusable workflow_call containing backend, ai-eval, frontend, and e2e jobs.
- `.github/workflows/ci.yml` - thin caller of `gates.yml` with inherited secrets.
- `.github/workflows/release.yml` - rc-tag release workflow with the release-gates-summary aggregator.
- `.github/workflows/e2e.yml` - removed after folding the job into `gates.yml`.

## Decisions Made

- Kept the existing backend, frontend, and Playwright commands unchanged so the daily gate behavior stayed byte-for-byte consistent.
- Folded `e2e.yml` into `gates.yml` instead of keeping two reusable workflows with duplicated Playwright logic.
- Used `release-gates-summary` as the single required release check target.

## Deviations from Plan

None - plan executed as written.

## Verification Results

- YAML parsed successfully with Python `yaml.safe_load` for `.github/workflows/{gates,ci,release}.yml`.
- `e2e.yml` is deleted and `i18n-check.yml` remains untouched.
- `gates.yml` exposes `workflow_call`, `run-ai-eval`, and all four jobs.
- `ci.yml` is a one-job caller and preserves the `pull_request` + `push:main` trigger and concurrency block.
- `release.yml` has the rc-tag trigger, the loadtest drain/pre-clean steps, and the `release-gates-summary` aggregator.
- Scratch-branch `v0.0.0-rc-test` smoke run was not performed in this environment; static validation and grep checks were used instead.
- GitHub UI check names to target in a follow-up admin task: `Gates / backend`, `Gates / ai-eval`, `Gates / frontend`, `Gates / e2e`, and `Release Gates / Release Gates Summary`.

## Issues Encountered

- `yq` is not installed locally, so Python YAML parsing was used instead.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 06-05. The release gate workflow exists, the reusable gates are single-sourced, and the summary check name is established for the launch decision document.

## Self-Check: PASSED

- Summary exists at `.planning/phases/06-polish-casa-verified-launch/06-04-SUMMARY.md`.
- Key created, modified, and deleted workflow files exist in the expected final state.
- Task commits resolve in git: `0cbe7e2`.

---
*Phase: 06-polish-casa-verified-launch*
*Completed: 2026-05-15*
