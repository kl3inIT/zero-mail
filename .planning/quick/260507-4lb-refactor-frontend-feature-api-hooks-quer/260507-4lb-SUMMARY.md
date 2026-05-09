---
quick_id: 260507-4lb
status: complete
completed: 2026-05-07
commit: a3c2966
---

# Quick Task 260507-4lb Summary

Refactored frontend feature API ownership, TanStack Query keys, hooks, and browser test layout.

## Changes

- Moved account and Gmail query key factories from `features/*/api/keys.ts` to `features/*/query-keys.ts`.
- Consolidated small feature API files into `account-api.ts`, `gmail-api.ts`, `onboarding-api.ts`, and `triage-api.ts`.
- Kept hooks one file per use case and updated imports/invalidation to the new API and query-key layout.
- Moved Playwright specs to `apps/web/e2e/**` and simplified Playwright/Vitest collection boundaries.
- Added `.github/workflows/e2e.yml` so browser tests run separately from unit/build CI.
- Updated frontend architecture tests, README, and project conventions for the new ownership rules.
- Removed stale RED-by-design labels from green frontend tests.

## Verification

- `pnpm --filter web run lint` - PASS.
- `pnpm --filter web run typecheck` - PASS.
- `pnpm --filter web run test:e2e -- --list` - PASS, 25 tests discovered under `apps/web/e2e`.
- `pnpm --filter web run test` - PASS, 26 files / 153 tests.
- `pnpm --filter web run i18n:check` - PASS, 322 leaf keys.
- `pnpm --filter web run build` - PASS.
- `pnpm --filter web run test:e2e` - PASS, 24 passed / 1 skipped.
- `git diff --check` - PASS.

## Notes

- `apps/web/test-results/` is generated Playwright output and remains untracked.
- The skipped E2E is the authenticated `/me/language` smoke, skipped by its own guard.
- Removed stale `.planning/HANDOFF.json` during resume closeout because it pointed at Phase 01.3 and no longer matched the active state.
