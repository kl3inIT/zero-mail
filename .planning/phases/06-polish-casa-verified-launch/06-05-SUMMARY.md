---
phase: 06-polish-casa-verified-launch
plan: 05
status: complete
subsystem: planning
tags: [launch-decision, casa-seed, housekeeping, release-closure]

requires:
  - phase: 06-polish-casa-verified-launch
    provides: release gate workflow, golden-path spec, and loadtest harness from Plans 06-02 through 06-04
provides:
  - signed launch go/no-go decision artifact with all 8 evidence boxes checked
  - dormant SEED-012 CASA restricted-scope verification track
  - archived resolved worker fail-fast todo
  - committed 50-tenant load-test result evidence
  - green candidate and final Release Gates evidence
  - annotated v1.0.0-rc1 tag cut once on the signed commit
  - Phase 6 ROADMAP/STATE closure
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
  - "Held LAUNCH-GO-NOGO.md unchecked and unsigned until a real candidate Release Gates run produced evidence."
  - "Cut final v1.0.0-rc1 only after committed local load-test evidence, signed go/no-go, and green final Release Gates."
  - "Track CASA production OAuth verification in dormant SEED-012 while launching v1.0.0-rc1 in OAuth Testing mode."

requirements-completed: []

duration: 31 min
completed: 2026-05-15
---

# Phase 06 Plan 05: Launch Decision and CASA Seed Summary

**Phase 06 Plan 05 is complete: the launch decision is signed, `v1.0.0-rc1` is tagged once on the signed commit, and final Release Gates are green.**

## Performance

- **Duration:** 31 min
- **Started:** 2026-05-15T09:35:00+07:00
- **Completed:** 2026-05-15T10:06:00+07:00
- **Tasks:** 3 automated tasks completed; local load-test evidence committed; Task 4 operator checkpoint closed with signed final tag
- **Files modified:** 9 original plan files; post-candidate fixes and closure docs recorded in the commits listed below

## Accomplishments

- Created `.planning/LAUNCH-GO-NOGO.md` at the planning root with exactly 8 evidence checklist items `(a)` through `(h)`, then checked all boxes after candidate gates passed.
- Preserved the three load-bearing trust phrases in item `(g)`: `auto-send forbidden`, `no stored bodies / prompts / completions`, and `every triage action undoable`.
- Created `.planning/seeds/SEED-012-casa-restricted-scope-verification.md` with the required frontmatter, CASA evidence package, candidate lab notes, closure trigger, and breadcrumbs.
- Moved the resolved worker fail-fast todo from `.planning/todos/pending/` to `.planning/todos/done/` and appended the Phase 6 D-15 resolution note.
- Built `zeromail-api:loadtest` and `zeromail-worker:loadtest`, ran the 50-tenant k6 harness, drained the worker queue, captured logs, and committed `06-LOAD-TEST-RESULT.md`.
- Fixed the loadtest gate for local and CI execution: Paketo tiny images do not include shell/curl, so compose healthchecks now use the embedded Java runtime and release.yml waits for HTTP readiness from the host.
- Fixed the Playwright CI split after the first candidate failure: daily `apps/web/playwright.config.ts` now excludes `launch-golden-path.spec.ts`, while `apps/web/playwright.golden.config.ts` keeps the golden-path spec enabled for the release tag workflow.
- Fixed the load-test verifier false positive where `completion_date_idx` matched the broad `completion` log-bleed regex; the verifier now uses token-boundary matching for `email_body`, `prompt`, `completion`, and `raw_html`.
- Included the minor web hardening commit before the final tag: locale/theme server actions now validate the referer origin before redirecting.
- Signed `.planning/LAUNCH-GO-NOGO.md` as `@kl3inIT` on 2026-05-15, checked all 8 launch boxes, and linked the green candidate run used for item `(a)`.
- Cut the final annotated `v1.0.0-rc1` tag on signed commit `2df4e445accb665551775a1c35cc17e2499e1d6f`; final Release Gates passed on run `25903482211`.
- Closed Phase 6 in `.planning/ROADMAP.md` and `.planning/STATE.md`.

## Task Commits

1. **Tasks 1-3: Launch decision, CASA seed, and todo archive** - `06019f2` (docs)
2. **Loadtest gate portability fix** - `5c41351` (fix)
3. **Pre-tag load-test result evidence** - `0573cf7` (docs)
4. **Playwright CI split fix** - `9fb3abb` (fix)
5. **Load-test verifier false-positive fix** - `334c7e6` (fix)
6. **Referer-origin redirect validation** - `30b1841` (fix)
7. **Launch decision sign-off** - `2df4e44` (docs)
8. **Task 4: Operator tag/sign-off checkpoint** - closed by signed final tag `v1.0.0-rc1`

## Files Created/Modified

- `.planning/LAUNCH-GO-NOGO.md` - root-level launch go/no-go artifact with 8 unchecked evidence boxes and a commented sign-off placeholder.
- `.planning/seeds/SEED-012-casa-restricted-scope-verification.md` - dormant CASA restricted-scope verification seed for the post-launch Production OAuth track.
- `.planning/todos/done/2026-04-28-worker-application-yml-fail-fast-parity.md` - archived resolved todo with D-15 resolution context.
- `.planning/phases/06-polish-casa-verified-launch/06-LOAD-TEST-RESULT.md` - committed local load-test evidence.
- `loadtest/compose.loadtest.yml` - healthchecks compatible with Paketo tiny runtime images.
- `.github/workflows/release.yml` - host-side API readiness wait before seeding/running k6.
- `backend/api/build.gradle.kts` - Windows-compatible Git Bash resolution for `loadtestVerify`.
- `apps/web/playwright.config.ts` - daily Playwright suite now excludes the launch golden-path spec.
- `apps/web/playwright.golden.config.ts` - golden-path release config explicitly keeps the launch spec enabled.
- `loadtest/scripts/loadtest-verify.sh` - log-bleed pattern now requires token boundaries to avoid matching index names such as `completion_date_idx`.
- `loadtest/README.md` - documents the host-side API readiness wait and log-bleed verifier behavior.
- `.planning/LAUNCH-GO-NOGO.md` - all 8 boxes checked, candidate run linked, and operator sign-off committed.
- `.planning/ROADMAP.md` - Phase 6 marked complete.
- `.planning/STATE.md` - milestone state closed after final tag gates passed.
- `.planning/phases/06-polish-casa-verified-launch/06-05-SUMMARY.md` - this final closure summary.

## Verification Results

- `LAUNCH-GO-NOGO.md` has at least 40 lines, exactly 8 checked boxes, item `(g)` contains all three required trust phrases, item `(b)` links to `06-LOAD-TEST-RESULT.md`, item `(h)` links to SEED-012, and the active sign-off line names `@kl3inIT` on 2026-05-15.
- `SEED-012-casa-restricted-scope-verification.md` has `id: SEED-012`, `status: dormant`, `scope: large`, 8 required H2 sections, valid frontmatter delimiters, and breadcrumbs for FND-07, PITFALLS.md, and LAUNCH-GO-NOGO.md.
- Todo archive state is correct: the done file exists, the pending counterpart is removed, and the two unrelated pending todos remain untouched.
- Worker fail-fast source verified at `backend/worker/src/main/resources/application.yml:63`: `REFRESH_TOKEN_KEY_BASE64` still uses a `:?` fail-fast placeholder.
- Mojibake scan for the three changed planning artifacts passed after replacing corrupted dash/checkmark text.
- `k6` summary: 5000 iterations, `http_req_failed` rate `0`, checks rate `1`, duration about 600 seconds.
- `C:\Program Files\Git\bin\bash.exe loadtest/scripts/wait-for-worker-drain.sh` passed with `pending=0`.
- `./gradlew.bat --no-daemon :backend:api:loadtestVerify` passed and generated `06-LOAD-TEST-RESULT.md`.
- Python YAML parse passed for `.github/workflows/release.yml`.
- Candidate-c Release Gates run used in `LAUNCH-GO-NOGO.md` item `(a)` passed: https://github.com/kl3inIT/zero-mail/actions/runs/25903268052.
- Final `v1.0.0-rc1` Release Gates run passed on signed SHA `2df4e445accb665551775a1c35cc17e2499e1d6f`: https://github.com/kl3inIT/zero-mail/actions/runs/25903482211.
- Final run jobs all passed: `Playwright Golden Path (e2e-stub)`, `gates / Playwright`, `gates / Backend Gradle`, `gates / Frontend Web`, `gates / Backend AI Eval`, `Trust Story Phrases`, `50-Tenant Load Test`, and `Release Gates Summary`.
- `50-Tenant Load Test` final `Verify invariants` step passed at `2026-05-15T06:28:29Z`.
- `git cat-file -t v1.0.0-rc1` returned `tag`, confirming the final tag is annotated.
- `git rev-list -n 1 v1.0.0-rc1` returned `2df4e445accb665551775a1c35cc17e2499e1d6f`, and `git merge-base --is-ancestor v1.0.0-rc1 main` confirmed the tag is on `main`.
- MED-7: final `v1.0.0-rc1` was pushed exactly once; no tag rewrite/delete was performed.
- LOW-10: Phase 6 closure applied to ROADMAP and STATE; `.planning/STATE.md` now records `status: milestone_complete` and 100% progress.

## Launch Closure

Task 4 is closed.

1. `main` was up to date before final tagging.
2. Candidate-c run `25903268052` went green and is linked in `.planning/LAUNCH-GO-NOGO.md` item `(a)`.
3. All 8 boxes in `.planning/LAUNCH-GO-NOGO.md` are checked.
4. Operator sign-off is committed exactly as `signed-off by @kl3inIT on 2026-05-15`.
5. Final annotated tag `v1.0.0-rc1` points at signed commit `2df4e445accb665551775a1c35cc17e2499e1d6f`.
6. Final Release Gates run `25903482211` is green.
7. Phase 6 closure is recorded in `.planning/ROADMAP.md` and `.planning/STATE.md`.

No operator checkpoint remains open.

## Deviations from Plan

Rule 3 blocker fixed during the load-test run: Paketo tiny runtime images do not include shell/curl, so the original compose healthchecks could never become healthy. The fix keeps compose healthchecks executable inside the image and moves HTTP readiness verification to the host/release runner.

Task 4 started as a documented `checkpoint:human-action` because it required live release evidence, main-branch state, and operator sign-off. It is now closed with the signed launch decision, final annotated tag, and green final Release Gates run.

Two post-candidate fixes were required before the final tag:

- The daily Playwright config was running the launch golden-path spec without the backend e2e stub. The config split now keeps that spec in the release-only golden config.
- The load-test verifier treated `completion_date_idx` as a forbidden `completion` token. Token-boundary matching preserves the privacy invariant check without false positives.

The referer-origin validation fix was included before the final tag as a minor hardening change.

## Issues Encountered

- The first artifact commit attempt only captured the `git mv`; it was immediately amended to include the two new planning files and the todo resolution content in commit `06019f2`.
- The first k6 command was launched with a 600-second tool timeout. The k6 run completed 5000 iterations and wrote `loadtest/run/summary.json`, but the wrapper timed out before returning a clean process exit. The downstream drain and invariant verifier passed; the committed result is based on `loadtest/run/run.log` and database invariants.

## User Setup Required

None for local tooling. `k6`, `psql`, and OpenSSL were verified locally before the load-test run.

## Next Phase Readiness

Plan 06-05 is complete. Phase 6 is marked complete, `.planning/STATE.md` records milestone completion, and the final `v1.0.0-rc1` tag gates are green.

## Self-Check: PASSED

- Automated artifact acceptance checks passed for LAUNCH-GO-NOGO, SEED-012, todo archive state, worker fail-fast source, loadtest verifier, and release workflow YAML parse.
- Task commits resolve in git: `06019f2`, `5c41351`, `0573cf7`.
- Post-candidate fix commits resolve in git: `9fb3abb`, `334c7e6`, `30b1841`.
- Launch sign-off commit resolves in git: `2df4e44`.
- Final Release Gates run `25903482211` passed.

---
*Phase: 06-polish-casa-verified-launch*
*Completed: 2026-05-15*
