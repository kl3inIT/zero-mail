---
status: complete
quick_id: 260523-v3b
date: 2026-05-23
---

# Quick Task 260523-v3b Summary

## Completed

- Replaced stale public header links with current landing sections: `/#features`, `/#pricing`, `/#testimonials`, and `/#faq`.
- Updated the hero secondary CTA from the missing `#how` section to `/#features`.
- Updated footer product links to the same current landing sections and moved the footer Security link to `/privacy`.
- Added English and Vietnamese labels for Pricing, Reviews, and FAQ.

## Verification

- `pnpm --filter web i18n:check` passed.
- `pnpm --filter web typecheck` passed.
- `pnpm --filter web lint` passed.
- Playwright verified `http://localhost:3000/` header link rendering and anchor navigation for all four sections.
- Playwright verified hero secondary CTA navigates to `#features`.
- Playwright verified `/privacy` header link navigation returns to `http://localhost:3000/#pricing`.

## Notes

- Browser validation reported existing Next.js image aspect-ratio warnings for testimonial avatars; no console errors were reported.
- No commit was created because the working tree already contained unrelated modified and untracked files, including files touched by this quick task.
