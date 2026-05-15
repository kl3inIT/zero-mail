---
phase: 6
slug: polish-casa-verified-launch
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-14
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Phase 6 itself IS launch-validation infrastructure — the dimensions below map 1:1 to the 9 acceptance criteria in `06-SPEC.md`.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (backend)** | JUnit 5 + AssertJ + Mockito; ArchUnit 1.x; Spring Modulith `ApplicationModulesTest` |
| **Framework (frontend)** | Vitest (unit), Playwright (Chromium, e2e) |
| **Framework (load)** | k6 v1.7.x (Grafana) via `grafana/setup-k6-action@v1` |
| **Config files** | `backend/api/build.gradle.kts`, `apps/web/playwright.config.ts`, `loadtest/compose.loadtest.yml` (new), `loadtest/scripts/golden-path.js` (new) |
| **Quick run command** | `./gradlew :backend:api:check` |
| **Full suite command** | `./gradlew check && pnpm -r test && pnpm -r test:e2e` |
| **Load suite command** | `docker compose -f loadtest/compose.loadtest.yml up -d && k6 run loadtest/scripts/golden-path.js && ./gradlew :backend:api:loadtestVerify` |
| **Estimated runtime** | quick ~120s · full ~10 min · load ~15 min |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :backend:api:check` for touched backend modules, or `pnpm -r --filter '...{apps/web}' test` for frontend changes.
- **After every plan wave:** Run `./gradlew check && pnpm -r test`.
- **Before `/gsd-verify-work`:** Full suite green + load-test invariant report committed to `06-LOAD-TEST-RESULT.md`.
- **Phase gate:** `release.yml` on the `v1.0.0-rc1` tag — `release-gates-summary` job aggregates all 6 gates and posts a single GitHub check.
- **Max feedback latency:** ~120s (per-commit), ~10 min (per-wave), ~15 min (RC tag gate).

---

## Validation Dimensions

| Dimension | Source | What proves it | Where measured |
|---|---|---|---|
| Privacy (LLM-09, FND-03, FND-04) | SPEC AC #1, #9 | Load-test invariant (c) finds 0 lines matching `email_body\|prompt\|completion\|raw_html` in prod-config logs over 10-min sustained traffic | `loadtest/run/run.log` scanned by `:backend:api:loadtestVerify` |
| Multi-tenant isolation (FND-01, FND-05) | SPEC AC #2 | Invariant (a) finds 0 audit rows whose `tenant_id` is not `loadtest-tenant-%` | `triage_audit` table query in `loadtestVerify` |
| Ledger consistency (BILL-02..BILL-04) | SPEC AC #2 | Invariant (b) finds net=0 across all 50 loadtest tenants | `credit_ledger_entry` table query |
| Regression-suite green on RC commit | SPEC AC #3, #4, #5, #6 | All 4 gate jobs in `gates.yml` (backend Gradle check incl. ArchUnit + ApplicationModulesTest + prompt-injection regression; AI eval `-PdeterministicOnly` drift; frontend; Playwright e2e) pass on SHA tagged `v1.0.0-rc1` | `release-gates-summary` job aggregation in `release.yml` |
| End-to-end golden path | SPEC AC #1 | `apps/web/e2e/launch-golden-path.spec.ts` green under `e2e-stub` Spring profile (real backend, stubbed Gmail + PubsubVerifier) | `release.yml` golden-path job |
| Trust-story restatement | SPEC AC #7, D-14 item (g) | Three verbatim phrases in `.planning/LAUNCH-GO-NOGO.md`: `auto-send forbidden`, `no stored bodies / prompts / completions`, `every triage action undoable` | Manual verification at sign-off + `release.yml` literal-grep step |
| OAuth Testing-mode launch | SPEC AC #7, D-14 item (h) | Item (h) checkbox checked + `SEED-012-casa-restricted-scope-verification.md` exists | Manual verification at sign-off |
| Tag annotated + on main | SPEC AC #7 | `git cat-file -t v1.0.0-rc1` returns `tag`; `git merge-base --is-ancestor v1.0.0-rc1 main` succeeds | `release.yml` early-step assertion |
| No auto-send code-path added | SPEC AC #8 | `DraftPathArchUnitTest::draft_and_triage_paths_never_send_or_update_gmail_drafts` passes | `gates.yml` backend job |

---

## Per-Task Verification Map

> Filled by the planner. One row per task ID; each acceptance criterion must have ≥1 automated verify row.

| Task ID | Plan | Wave | Acceptance Criterion | Test Type | Automated Command | Status |
|---------|------|------|----------------------|-----------|-------------------|--------|
| 06-XX-YY | TBD  | TBD  | AC-{1..9}            | TBD       | TBD               | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] No new test framework installs — JUnit 5, Vitest, Playwright, ArchUnit, Spring Modulith all already present in the repo.
- [ ] k6 binary installed in CI via `grafana/setup-k6-action@v1`; locally optional (developers can install via `winget install k6` / `brew install k6`).
- [ ] `loadtest/scripts/golden-path.js` and `loadtest/compose.loadtest.yml` are net-new but are themselves the validation tooling — not Wave 0 stubs.

*Phase 6 adds infrastructure, not new test files for existing requirements — no Wave 0 stubs needed.*

---

## Manual-Only Verifications

| Behavior | Acceptance Criterion | Why Manual | Test Instructions |
|----------|----------------------|------------|-------------------|
| Trust-story restatement uses verbatim phrasing | AC #7 (D-14 item g) | Operator sign-off line is human-attested; `release.yml` adds a literal-grep step as a safety net | Reviewer reads `.planning/LAUNCH-GO-NOGO.md` and confirms each of the 3 phrases appears as exact strings |
| Launch sign-off line committed | AC #7 (D-14) | Sign-off is a deliberate human act; cannot be automated | Operator commits `✓ signed-off by @<user> on <ISO date>` as the last edit on `LAUNCH-GO-NOGO.md` |
| OAuth consent screen launch mode | AC #7 (D-14 item h) | Google Cloud Console state lives outside the repo | Operator verifies consent screen status = "Testing", screenshot attached in PR |
| Annotated tag message embeds gate URL | D-12 | The URL is observed after the gate run; can't be embedded by automation | Operator runs `gh run view <run-id> --json url -q .url` and amends the tag message before pushing |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (N/A — no Wave 0 stubs)
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s for per-commit, < 15 min for RC gate
- [ ] `nyquist_compliant: true` set in frontmatter (after planner fills task table)

**Approval:** pending
