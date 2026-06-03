---
status: complete
completed: 2026-05-22
---

# Summary

Simplified the login and current onboarding shell for the beta UX:
- Removed the login trust panel and deleted the shared `TrustPanel` component.
- Removed the extra login eyebrow, OAuth explanatory note, divider, and active company-email secondary path.
- Kept a disabled "company Gmail" secondary button so the future option is visible but unavailable.
- Shortened login copy to concrete OAuth and beta-payment information.
- Tightened mobile topbar and login spacing; theme toggle now stays compact on mobile.
- Removed the trust panel from the existing onboarding pages so the upcoming onboarding redesign starts from a clean one-column shell.

Onboarding beta research recommendation:
- Keep beta onboarding focused on activation, not education.
- Recommended steps: connect Gmail, choose one inbox goal, create or pick the first rule, preview the rule on recent messages, then start in monitor/manual-review mode.
- Put trust/security detail in settings/docs/legal or contextual help after the user reaches value; do not put long trust copy on login.

Verification:
- `pnpm --filter web run typecheck` passed.
- `pnpm --filter web run i18n:check` passed.
- `pnpm --filter web exec vitest run __tests__/pages/login-error-rendering.test.tsx` passed.
- `pnpm --filter web exec playwright test e2e/login-shell.spec.ts` passed.
- Playwright MCP verified `/login` desktop and mobile rendering with no console warnings/errors.
