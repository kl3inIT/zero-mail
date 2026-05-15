---
phase: 06-polish-casa-verified-launch
plan: 05
status: code-complete-awaiting-operator-checkpoint
subsystem: planning
tags: [launch-decision, casa-seed, housekeeping, operator-checkpoint]

requires:
  - phase: 06-polish-casa-verified-launch
    provides: release gate workflow, golden-path spec, and loadtest harness from Plans 06-02 through 06-04
provides:
  - launch go/no-go decision artifact with 8 unchecked evidence boxes
  - dormant SEED-012 CASA restricted-scope verification track
  - archived resolved worker fail-fast todo
  - operator runbook for candidate tag, sign-off, final tag, and STATE closure
affects: [launch-go-no-go, casa-verification, rc-tag-runbook, phase-06-closure]

tech-stack:
  added: []
  patterns:
    - root-level launch decision artifact
    - dormant post-launch seed for CASA restricted-scope verification
    - two-tag RC launch pattern with signed decision before final tag

key-files:
  created:
    - .planning/LAUNCH-GO-NOGO.md
    - .planning/seeds/SEED-012-casa-restricted-scope-verification.md
    - .planning/phases/06-polish-casa-verified-launch/06-05-SUMMARY.md
  modified:
    - .planning/todos/done/2026-04-28-worker-application-yml-fail-fast-parity.md
  moved:
    - .planning/todos/pending/2026-04-28-worker-application-yml-fail-fast-parity.md -> .planning/todos/done/2026-04-28-worker-application-yml-fail-fast-parity.md

key-decisions:
  - "Keep LAUNCH-GO-NOGO.md unchecked and unsigned until a real candidate Release Gates run produces evidence."
  - "Do not cut v1.0.0-rc1 from this branch or without committed local load-test evidence."
  - "Track CASA production OAuth verification in dormant SEED-012 while launching v1.0.0-rc1 in OAuth Testing mode."

requirements-completed: []

duration: 31 min
completed: 2026-05-15
---

# Phase 06 Plan 05: Launch Decision and CASA Seed Summary

**Launch decision artifacts are ready for operator review, while final RC tagging remains blocked on live evidence and main-branch sign-off.**

## Performance

- **Duration:** 31 min
- **Started:** 2026-05-15T09:35:00+07:00
- **Completed:** 2026-05-15T10:06:00+07:00
- **Tasks:** 3 automated tasks completed; 1 operator checkpoint pending
- **Files modified:** 4

## Accomplishments

- Created `.planning/LAUNCH-GO-NOGO.md` at the planning root with exactly 8 unchecked checklist items `(a)` through `(h)`.
- Preserved the three load-bearing trust phrases in item `(g)`: `auto-send forbidden`, `no stored bodies / prompts / completions`, and `every triage action undoable`.
- Created `.planning/seeds/SEED-012-casa-restricted-scope-verification.md` with the required frontmatter, CASA evidence package, candidate lab notes, closure trigger, and breadcrumbs.
- Moved the resolved worker fail-fast todo from `.planning/todos/pending/` to `.planning/todos/done/` and appended the Phase 6 D-15 resolution note.

## Task Commits

1. **Tasks 1-3: Launch decision, CASA seed, and todo archive** - `06019f2` (docs)
2. **Task 4: Operator tag/sign-off checkpoint** - pending; no tag was cut

## Files Created/Modified

- `.planning/LAUNCH-GO-NOGO.md` - root-level launch go/no-go artifact with 8 unchecked evidence boxes and a commented sign-off placeholder.
- `.planning/seeds/SEED-012-casa-restricted-scope-verification.md` - dormant CASA restricted-scope verification seed for the post-launch Production OAuth track.
- `.planning/todos/done/2026-04-28-worker-application-yml-fail-fast-parity.md` - archived resolved todo with D-15 resolution context.
- `.planning/phases/06-polish-casa-verified-launch/06-05-SUMMARY.md` - this pending-checkpoint summary.

## Verification Results

- `LAUNCH-GO-NOGO.md` has at least 40 lines, exactly 8 unchecked boxes, item `(g)` contains all three required trust phrases, item `(b)` links to `06-LOAD-TEST-RESULT.md`, item `(h)` links to SEED-012, and no active sign-off line exists.
- `SEED-012-casa-restricted-scope-verification.md` has `id: SEED-012`, `status: dormant`, `scope: large`, 8 required H2 sections, valid frontmatter delimiters, and breadcrumbs for FND-07, PITFALLS.md, and LAUNCH-GO-NOGO.md.
- Todo archive state is correct: the done file exists, the pending counterpart is removed, and the two unrelated pending todos remain untouched.
- Worker fail-fast source verified at `backend/worker/src/main/resources/application.yml:63`: `REFRESH_TOKEN_KEY_BASE64` still uses a `:?` fail-fast placeholder.
- Mojibake scan for the three changed planning artifacts passed after replacing corrupted dash/checkmark text.

## Open Checkpoint - Operator Action Required

Task 4 is intentionally not performed here. The final launch still requires:

1. Switch to `main` and ensure it is up to date.
2. Run the full local load test and commit `.planning/phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md` before any tag.
3. Cut `v1.0.0-rc1-candidate`, wait for `release-gates-summary`, and copy the green run URL into item `(a)`.
4. Check all 8 boxes in `.planning/LAUNCH-GO-NOGO.md`, promote the sign-off placeholder into a real signed line, and commit that change.
5. Cut the final annotated `v1.0.0-rc1` tag exactly once on the signed commit.
6. Close Phase 6 in `.planning/STATE.md` with the GSD state command and commit the closure.

Current blockers to doing that safely in this environment:

- Current branch is `gsd/phase-06-polish-casa-verified-launch`, not `main`.
- `k6`, `psql`, and `openssl` are not available on `PATH`; only `docker` and `gh` were found.
- No committed `06-LOAD-TEST-RESULT.md` exists yet for the final tag SHA.
- `.planning/LAUNCH-GO-NOGO.md` is intentionally unchecked and unsigned.
- No `v1.0.0-rc1-candidate` or final `v1.0.0-rc1` tag was cut.

## Deviations from Plan

None for Tasks 1-3. Task 4 remains a documented `checkpoint:human-action` because it requires live release evidence, main-branch state, and operator sign-off.

## Issues Encountered

- The first artifact commit attempt only captured the `git mv`; it was immediately amended to include the two new planning files and the todo resolution content in commit `06019f2`.
- Local load-test tooling is incomplete: `k6`, `psql`, and `openssl` are missing from `PATH`.

## User Setup Required

Install or expose the missing release tools before the final operator runbook:

- `k6`
- `psql` client
- `openssl`, or an equivalent PowerShell command that generates a 32-byte base64 key for `REFRESH_TOKEN_KEY_BASE64`

## Next Phase Readiness

Plan 06-05 is code/docs complete through Tasks 1-3 and paused at the operator checkpoint. Phase 6 should not be marked complete until the load-test result is committed, the launch decision is signed, the final tag gates are green, and `.planning/STATE.md` is updated.

## Self-Check: PASSED

- Automated artifact acceptance checks passed for LAUNCH-GO-NOGO, SEED-012, todo archive state, and worker fail-fast source.
- Working tree was clean before this summary was written.
- Task commit resolves in git: `06019f2`.

---
*Phase: 06-polish-casa-verified-launch*
*Completed: 2026-05-15*
