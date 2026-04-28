---
status: resolved
trigger: "User reports clicking settings after onboarding gets stuck on `Đang tải thiết lập...`."
created: 2026-04-28
updated: 2026-04-28
---

# Debug Session: Settings Stuck Loading

## Symptoms

- Expected behavior: Clicking the onboarding completion CTA should open `/settings`.
- Actual behavior: The app stays on the onboarding loading copy, `Đang tải thiết lập...`.
- Error messages: No explicit error reported.
- Timeline: Reported after Phase 01.5 UAT gap closure.
- Reproduction: Complete template selection, then click the completion CTA labeled as opening settings.

## Current Focus

- hypothesis: The frontend advances onboarding to `COMPLETE` but never navigates away from `/onboarding`.
- test: Inspect `/onboarding` step handling and the complete-onboarding mutation.
- expecting: `COMPLETE` step has no redirect and renders the loading copy indefinitely.
- next_action: complete; fix verified.

## Evidence

- timestamp: 2026-04-28
  observation: `apps/web/app/(protected)/onboarding/page.tsx` renders `t('onboarding.loading')` when `step === 'COMPLETE'`.
- timestamp: 2026-04-28
  observation: The `TEMPLATE_SELECTED` CTA calls `completeMut.mutate()` without a router navigation callback.
- timestamp: 2026-04-28
  observation: `useCompleteOnboarding` invalidates queries only; it does not navigate to `/settings`.

## Eliminated

- hypothesis: The reported string comes from `/settings`.
  reason: `/settings` uses `common.loading` (`Đang tải...`), while the exact reported copy is `onboarding.loading`.

## Resolution

- root_cause: The frontend state machine had a terminal `COMPLETE` branch that displayed loading copy but relied on a non-existent auth guard redirect.
- fix: Redirect to `/settings` after the completion mutation succeeds, and redirect any already-`COMPLETE` `/onboarding` render to `/settings`.
- verification: Added `onboarding-complete-redirect.test.tsx` covering both direct `COMPLETE` render and completion CTA success navigation.
- files_changed: apps/web/app/(protected)/onboarding/page.tsx, apps/web/__tests__/pages/onboarding-complete-redirect.test.tsx
