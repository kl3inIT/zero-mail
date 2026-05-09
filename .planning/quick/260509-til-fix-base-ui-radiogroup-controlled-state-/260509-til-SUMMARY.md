---
quick_id: 260509-til
status: complete
date: 2026-05-09
commit: 9540921
---

# Quick Task 260509-til: Fix Base UI RadioGroup controlled state warning on onboarding template select

## Summary

Fixed the onboarding template selector so Base UI `RadioGroup` is controlled from its first render. `selected` now starts as an empty string sentinel and `RadioGroup` always receives a defined `value`, avoiding the uncontrolled-to-controlled transition warning.

## Files Changed

- `apps/web/app/(protected)/onboarding/template-select/TemplateSelectClient.tsx`

## Verification

- PASS: `pnpm --filter web typecheck`
- PASS: `pnpm --filter web exec eslint "app/(protected)/onboarding/template-select/TemplateSelectClient.tsx"`
- PASS: Playwright browser check on `http://localhost:3000/onboarding/template-select` with mocked authenticated `/me`: rendered 3 radio items, selected a template, and observed no Base UI warnings, no console warnings, and no console errors.
- KNOWN UNRELATED FAILURE: `pnpm --filter web test` ran 165/166 tests passing and failed `__tests__/workspace/workspace-cleanup.test.ts` because `pnpm-workspace.yaml` currently contains `allowBuilds` instead of the test's expected `ignoredBuiltDependencies`.

## Commits

- `9540921` `fix(web): keep onboarding template radio controlled`
