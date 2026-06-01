---
status: complete
quick_id: 260524-sej
date: 2026-05-24
---

# Quick Task 260524-sej Summary

## Completed

- Updated `ZMLogoMark` to render the shared `/images/logo.png` asset instead of the old inline SVG.
- Updated the logo component test contract to assert the image asset, sizing, and decorative accessibility behavior.
- Replaced `apps/web/app/favicon.ico` with a favicon generated from `apps/web/public/images/logo.png`.

## Verification

- `pnpm --filter web test -- __tests__/landing/zm-logo-mark.test.tsx` passed.
- `pnpm --filter web lint` passed.
- `pnpm --filter web i18n:check` passed.
- JetBrains file diagnostics for `ZMLogoMark.tsx` reported no problems.
- Playwright verified the landing header renders `/images/logo.png` and Next serves the new favicon at `/favicon.ico?...` with HTTP 200.

## Notes

- `pnpm --filter web typecheck` currently fails in unrelated cleanup unsubscribe translation key references, not in the logo files changed by this quick task.
- No commit was created because the working tree already contained unrelated modified and untracked files.
