---
quick_id: 260512-dx4
status: complete
completed: 2026-05-12
commit: 4ecd071
---

# Quick Task 260512-dx4 Summary

## Task

Fix Frontend Web CI workspace cleanup failure on PR #29.

## Changes

- Updated `apps/web/__tests__/workspace/workspace-cleanup.test.ts` so it no longer expects inline `lint-staged` config in root `package.json`.
- The test now asserts the current root `lint-staged.config.mjs` file exists and covers backend Java Spotless, web ESLint/Prettier, and i18n checks.
- Audited package ownership: root `package.json` owns `prepare: "husky"` and the `lint-staged` devDependency; neither root nor `apps/web/package.json` contains an inline `lint-staged` config block; `apps/web/package.json` does not duplicate Husky/lint-staged setup.

## Verification

- `pnpm --filter web exec vitest run __tests__/workspace/workspace-cleanup.test.ts` - passed, 7/7 tests.
- `pnpm --filter web test` - passed, 33 files and 206 tests.
- Node package ownership check - passed: no duplicated root/apps lint-staged package config.

## Commit

- `4ecd071` - `test(web): align workspace cleanup with lint-staged config file`
