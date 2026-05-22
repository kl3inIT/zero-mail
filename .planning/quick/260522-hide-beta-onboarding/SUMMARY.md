---
status: complete
completed: 2026-05-22
---

# Summary

Temporarily hid the beta onboarding flow without deleting the implementation.

Changes:
- Added `BETA_ONBOARDING_ENABLED = false` in `apps/web/features/onboarding/config.ts`.
- Redirected `/onboarding` and all direct onboarding subroutes to `/rules` while the flag is disabled.
- Hid "continue setup" entry points by routing logged-in landing CTAs to the app and suppressing the sidebar onboarding item.
- Kept the extracted onboarding client flow in `OnboardingIndexClient` so the flow can be restored by flipping the flag.
- Updated onboarding route tests to lock the hidden beta redirect behavior.

Verification:
- `pnpm --filter web run typecheck`
- `pnpm --filter web run i18n:check`
- `pnpm --filter web exec vitest run __tests__/pages/onboarding-complete-redirect.test.tsx __tests__/features/onboarding/onboarding-step-indicator.test.tsx`
- `pnpm --filter web exec playwright test e2e/onboarding-routes.spec.ts`
- Browser MCP confirmed `/onboarding/gmail-connect` redirects to `/rules` with no onboarding progress nav and no console warnings/errors.
