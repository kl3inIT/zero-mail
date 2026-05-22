---
quick_id: 260522-iyk
status: complete
date: 2026-05-22
---

# Quick Task 260522-iyk Summary

Simplified `/login` copy and layout density using Inbox Zero's sparse auth screen as the reference direction.

## Changes

- Reduced login form to one short headline, one body line, Google sign-in CTA, mandatory legal copy, and a shortened waitlist link.
- Added `TrustPanel` compact variant for `/login`; onboarding keeps the full trust + permission panel.
- Adjusted auth grid CSS so 1024px desktop keeps two columns instead of pushing the trust panel below the fold.
- Suppressed a hidden theme input hydration warning that surfaced during login browser validation.

## Verification

- `pnpm --filter web i18n:check` passed.
- `pnpm --filter web lint "app/(auth)/login/page.tsx" "features/auth/components/TrustPanel.tsx" "features/landing/components/ThemeToggle.tsx"` passed.
- `pnpm --filter web test:e2e login-shell.spec.ts` passed 7/7.
- Playwright render check passed for `http://localhost:3000/login` at 1024x768 and 375x812 with no console errors after the hydration warning fix.

## Notes

- `pnpm --filter web typecheck` still fails on pre-existing cleanup/unsubscribe OpenAPI schema errors outside this task.
- No commit was made because the worktree already contains unrelated modified and untracked files, including unrelated changes inside generated i18n bundles.
