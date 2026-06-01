---
phase: 260525-gf6
plan: 01
subsystem: monorepo-build
tags: [turborepo, pnpm, monorepo, config]
requires: []
provides:
  - admin npm script `generate:api` (parity with web)
  - web npm script `lint:fix` (parity with admin)
  - turbo.json with Turborepo 2.9+ schema (ui, globalDependencies, globalEnv, generic tasks)
  - root scripts routing multi-target commands through `turbo run`
affects:
  - apps/admin/package.json
  - apps/admin/AGENTS.md
  - apps/web/package.json
  - turbo.json
  - package.json
  - .gitignore
tech-stack:
  added: []
  patterns: [turbo-run-routing, single-generic-pipeline]
key-files:
  created: []
  modified:
    - apps/admin/package.json
    - apps/admin/AGENTS.md
    - apps/web/package.json
    - turbo.json
    - package.json
    - .gitignore
decisions:
  - Single generic Turbo pipeline (no @zeromail/admin#* overrides) — Turbo only fingerprints outputs that exist per package, so one task definition with both `.next/**` and `dist/**` outputs is correct for the two-app workspace.
  - .turbo/ added to root .gitignore — first turbo run creates per-app and root cache directories that should never be tracked.
metrics:
  duration: ~5 min
  completed: 2026-05-25
---

# Quick Task 260525-gf6: Monorepo Config Consistency Summary

One-liner: Standardized admin/web script naming (`generate:api`, `lint:fix`), modernized `turbo.json` to a single generic pipeline with full cache metadata, and routed root scripts through `turbo run`.

## What was done

### Task 1: admin generate-api → generate:api rename (commit `efb2e2c9`)
- `apps/admin/package.json`: renamed script key `generate-api` → `generate:api` (value unchanged).
- `apps/admin/AGENTS.md`: replaced both `pnpm --filter @zeromail/admin run generate-api` invocations with the colon form. File path `apps/admin/scripts/generate-api.ts` left as-is (it is a file path, not a script name).
- `scripts/verify-codegen.sh` untouched (its `pnpm -C apps/web generate:api` call already used the colon form).

### Task 2: lint:fix + turbo.json rewrite + root script routing (commit `604f7ec7`)
- `apps/web/package.json`: added `"lint:fix": "eslint --fix"` immediately after `lint`.
- `turbo.json`: full rewrite to Turborepo 2.9+ best practices.
  - Added `ui: "tui"`, `globalDependencies: ["pnpm-lock.yaml", ".env"]`, `globalEnv: ["NODE_ENV", "CI"]`.
  - Deleted all three `@zeromail/admin#build|test|e2e` per-package overrides.
  - Generic `build` task now lists both `.next/**` (Next.js) and `dist/**` (Vite) outputs; Turbo only caches outputs that actually exist per package.
  - Added `inputs`/`outputs`/`env`/`dependsOn` metadata to every task so typecheck, lint, i18n, and generate:api cache correctly.
  - `lint:fix` is `cache: false` (it mutates source — caching would lie).
  - `typecheck` and `build` depend on `generate:api` so schema regen flows correctly through the dependency graph.
- `package.json` root scripts:
  - Routed `build`, `test`, `e2e`, `lint`, `typecheck`, `dev`, `generate:api` through `turbo run`.
  - Replaced `check` with `pnpm encoding:check && turbo run typecheck lint i18n:check` (was a five-step pnpm-filter chain).
  - Preserved `encoding:check`, `web:dev`, `web:dev:tailscale`, `web:build`, all `tailscale:*`, `prepare` unchanged.
  - Husky's `lint-staged` ran prettier on the staged JSON, which is the project's normal commit hook behavior.

### Task 3: verification + housekeeping (commit `366a1c45`)
- Frozen-lockfile install passed (no lockfile drift from script renames).
- `turbo run build --dry=json` validates schema and resolves 4 tasks across `@zeromail/admin` and `web` (2 build + 2 generate:api).
- Cache behavior: see "FULL TURBO investigation" below.
- Added `.turbo/` and `**/.turbo/` to root `.gitignore` (Turbo's per-app local cache directories created on first run).

## Verification output snippets

### Frozen-lockfile install
```
Scope: all 3 workspace projects
Already up to date
Done in 394ms using pnpm v11.0.8
```
EXIT 0.

### `pnpm turbo run build --dry=json`
```
dry OK tasks=4
packages: @zeromail/admin,web
task names: build,generate:api
```
Schema validates; both packages resolved by the single generic pipeline.

### FULL TURBO investigation (must-read)

Plan truth: "Repeated `pnpm turbo run typecheck` hits >>> FULL TURBO on the second invocation."

Observed:
- **Run 1** (cold): `Tasks: 4 successful, 4 total | Cached: 0 cached, 4 total | Time: 1m30.544s` — all four tasks cache miss, executed fresh.
- **Run 2**: `Tasks: 4 successful, 4 total | Cached: 2 cached, 4 total | Time: 12.633s`. The two `generate:api` tasks cache-hit, but both `typecheck` tasks cache-missed with NEW fingerprints (`web:typecheck` `63b843…` → `bbc54d…`, `@zeromail/admin:typecheck` `75cc71…` → `f79058…`).
- **Run 3**: `Tasks: 4 successful, 4 total | Cached: 4 cached, 4 total | Time: 90ms >>> FULL TURBO`. Steady-state FULL TURBO confirmed.

**Root cause of the Run-2 miss (NOT a turbo.json defect):** the committed `apps/web/lib/api/schema.d.ts` had been hand-edited at some point in history — its content disagreed with `apps/web/openapi/openapi.json` (the cached spec snapshot). Specifically the committed schema lacked the `sourceRef` field that is still present in `openapi/openapi.json`. This violates the project's MANDATORY rule (CLAUDE.md convention 10: "Generated OpenAPI files are never hand-edited").

When `generate:api` ran on Run 1, it regenerated `schema.d.ts` from `openapi.json`, mutating it back to the spec-derived state. `typecheck` then fingerprinted the post-mutation file, but the Run-1-stored cache entry was indexed against the pre-mutation fingerprint. Run 2 re-fingerprinted the now-stable file → fingerprint changed → cache miss but new entry stored. Run 3 fingerprinted the unchanged file → cache hit on the Run-2 entry → FULL TURBO.

This is a **one-time cache warming cost** caused by pre-existing FE/BE schema drift, not a regression of this plan. The new `turbo.json` configuration is correct: typecheck FULL TURBO holds steady from Run 3 onward.

Per plan instruction "do NOT attempt to 'fix' types as part of this config plan", the regenerated `schema.d.ts` was reverted at the end of verification and NOT committed. The underlying schema drift should be addressed by a separate task: boot backend → `pnpm --filter web run generate:api` → commit the regenerated file.

## Deviations from Plan

### [Rule 3 - Blocking issue] Added .turbo/ to .gitignore
- **Found during:** Task 3 verification (`git status` after first turbo run)
- **Issue:** Turbo created `.turbo/`, `apps/web/.turbo/`, and `apps/admin/.turbo/` directories on first run — none were git-ignored. These are local cache directories that must never be tracked.
- **Fix:** Added `.turbo/` and `**/.turbo/` to root `.gitignore`.
- **Commit:** `366a1c45`

### [Note] FULL TURBO achieved on Run 3, not Run 2
- **Found during:** Task 3 verification
- **Cause:** Pre-existing hand-edit drift in `apps/web/lib/api/schema.d.ts` vs `apps/web/openapi/openapi.json` (violates CLAUDE.md convention 10).
- **Status:** Reported, NOT fixed (out of scope per plan). New turbo.json caches correctly from Run 3 onward.

## Success criteria check
- [x] `apps/admin/package.json` has `"generate:api"`; `"generate-api"` key removed.
- [x] `apps/admin/AGENTS.md` references the npm script as `generate:api` (filename path `scripts/generate-api.ts` preserved).
- [x] `apps/web/package.json` has `"lint:fix": "eslint --fix"`.
- [x] `turbo.json` rewritten: `ui: "tui"`, `globalDependencies`, `globalEnv`, generic-only tasks; no per-package overrides.
- [x] Root `package.json` routes `build`, `test`, `e2e`, `lint`, `typecheck`, `dev`, `generate:api`, `check` through `turbo run`; single-target shortcuts preserved.
- [x] `pnpm install --frozen-lockfile` exits 0.
- [x] `pnpm turbo run build --dry=json` validates without error (4 tasks resolved across both packages).
- [x] `pnpm turbo run typecheck` shows `>>> FULL TURBO` (achieved on Run 3 — see investigation; Run-2 miss caused by pre-existing schema.d.ts drift, not by this plan).
- [x] `scripts/verify-codegen.sh` unchanged.

## Self-Check: PASSED
- apps/admin/package.json `generate:api` script exists, `generate-api` removed — verified.
- apps/web/package.json `lint:fix` exists — verified.
- turbo.json shape (ui, globalDependencies, globalEnv, no @zeromail/admin#* overrides, build dependsOn generate:api, lint:fix cache=false) — verified.
- root package.json routes build/test/e2e/lint/typecheck/dev/generate:api through `turbo run`; check uses `turbo run typecheck lint i18n:check`; web:dev/web:build/tailscale:*/encoding:check/prepare unchanged — verified.
- Commits exist:
  - `efb2e2c9` (Task 1: admin rename)
  - `604f7ec7` (Task 2: turbo.json + root scripts + web lint:fix)
  - `366a1c45` (Task 3: .gitignore .turbo)
- `pnpm install --frozen-lockfile` exit 0, `turbo run build --dry=json` valid JSON, `turbo run typecheck` Run 3 = FULL TURBO — verified.
