---
status: resolved
trigger: "Merge main into current branch failed; prefer main while resolving conflicts."
created: "2026-05-22"
updated: "2026-05-22"
---

# Debug Session: merge-main-conflicts

## Symptoms

- Expected behavior: merge `main` into the current branch completes cleanly.
- Actual behavior: repository is mid-merge with unresolved conflicts.
- Error messages: git reports 9 conflicted files.
- Timeline: after merging `main` into the current branch.
- Reproduction: current working tree state.

## Current Focus

- hypothesis: conflict files can be resolved by taking `main` (`theirs`) unless local-only branch work must be preserved.
- test: list unmerged files, resolve with `theirs`, then verify no conflict markers remain.
- expecting: git reports no unmerged paths.
- next_action: gather exact conflicted file list.

## Evidence

- 2026-05-22: `git diff --name-only --diff-filter=U` listed 9 conflicted files.
- 2026-05-22: `MERGE_HEAD` resolved to `main` at `66f84b9c`.
- 2026-05-22: conflict files resolved by taking `main` (`--theirs`), with `TrustPanel.tsx` removed because `main` deleted it.
- 2026-05-22: generated i18n/OpenAPI artifacts needed regeneration after conflict resolution so branch cleanup UI code stayed type-safe.

## Eliminated

## Resolution

- root_cause: Merge conflicts between the current feature branch and `main`, plus stale generated web artifacts after taking `main` for conflicted files.
- fix: Resolved conflicted files in favor of `main`; regenerated `apps/web/i18n/messages/*.json`, `apps/web/openapi/openapi.json`, and `apps/web/lib/api/schema.d.ts`.
- verification: No unmerged paths, no conflict markers, `git diff --cached --check` clean, JetBrains Java problem check clean, targeted Gradle test passed, `pnpm --dir apps/web i18n:check` passed, and `pnpm --dir apps/web typecheck` passed.
- files_changed: conflicted files from merge plus regenerated web artifacts.
