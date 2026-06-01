---
status: complete
completed: 2026-05-22
---

# Summary

Implemented the accepted beta onboarding prototype in production.

Changes:
- `/onboarding` now sends `GMAIL_CONNECTED` users through `/onboarding/gmail-connect` so the first screen is a short inbox-preview step.
- `/onboarding/gmail-connect` now shows a compact classified inbox preview and a direct continue action.
- `/onboarding/template-select` now combines first-rule selection with a preview table showing which emails would be labeled, archived, pinned, or ignored.
- `/onboarding/complete` now shows a Gmail draft mock, reinforces review mode, and enters the dashboard.
- Onboarding copy and step labels were updated in Vietnamese and English.

Verification:
- `pnpm --filter web run typecheck`
- `pnpm --filter web run i18n:check`
- `pnpm --filter web exec vitest run __tests__/pages/onboarding-complete-redirect.test.tsx __tests__/features/onboarding/onboarding-step-indicator.test.tsx`
- `pnpm --filter web exec playwright test e2e/onboarding-routes.spec.ts`
- Browser MCP checked all three onboarding routes at desktop and mobile widths with API mocks: headings rendered, progress nav visible, no horizontal overflow, and no console warnings/errors.
