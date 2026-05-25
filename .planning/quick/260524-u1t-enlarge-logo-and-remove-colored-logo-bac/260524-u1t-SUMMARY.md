---
status: complete
quick_id: 260524-u1t
date: 2026-05-24
---

# Quick Task 260524-u1t Summary

## Completed

- Diagnosed the small-logo issue: original `logo.png` had a 1254x1254 canvas while the visible mark occupied only about 45% width and 55% height.
- Cropped `apps/web/public/images/logo.png` to a tighter 1024x1024 transparent asset where the visible mark occupies about 72.6% width and 89.2% height.
- Regenerated `apps/web/app/favicon.ico` from the tighter crop so the browser tab icon fills more of the favicon slot.
- Removed colored logo wrapper backgrounds from `.zm-brand-mark`, `.zm-gm-rail-logo`, and protected `ChromeHeader`.
- Increased logo render sizes across landing, auth, preview, and app chrome surfaces.
- Removed the temporary image scale transform from `ZMLogoMark` because the asset is now properly cropped.

## Verification

- `pnpm --filter web test -- __tests__/landing/zm-logo-mark.test.tsx` passed.
- `pnpm --filter web lint` passed.
- JetBrains diagnostics for `ZMLogoMark.tsx` and `ChromeHeader.tsx` reported no problems.
- Playwright verified the landing header logo renders at 30x30 inside a transparent 34x34 wrapper and `/favicon.ico` serves the regenerated 49,872-byte icon.

## Notes

- The protected app header background removal was verified by code/diagnostics; anonymous Playwright lands on public/auth surfaces without a logged-in session.
- No commit was created because the working tree already contained unrelated modified and untracked files.
