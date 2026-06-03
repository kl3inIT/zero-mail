---
status: complete
quick_id: 260524-va0
date: 2026-05-24
---

# Quick Task 260524-va0 Summary

## Completed

- Increased public/auth brand logo size from 30px to 40px.
- Increased brand wrapper from 34px to 44px and brand gap from 8px to 10px.
- Increased protected app chrome logo from 32px to 42px and wrapper to 44px.
- Increased inbox preview rail logo from 30px to 38px and wrapper to 42px.

## Verification

- `pnpm --filter web test -- __tests__/landing/zm-logo-mark.test.tsx` passed.
- `pnpm --filter web lint` passed.
- JetBrains diagnostics for `ChromeHeader.tsx` reported no errors before timeout.
- JetBrains diagnostics for `TopBar.tsx` reported only the pre-existing `#waitlist` anchor warning.

## Notes

- No browser render pass was run because the request was specifically to adjust displayed image sizes without another visual render cycle.
- No commit was created because the working tree already contained unrelated modified and untracked files.
