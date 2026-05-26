---
status: complete
completed: 2026-05-26
---

# Summary

Polished the unsubscribe campaign list UI to match the newer Analytics visual style.

Changes:
- Reworked the page header into the compact app-section header style and added a top-right help popover.
- Replaced small metric pills with Analytics-style metric cards.
- Wrapped search/filter/sort controls in a cleaner toolbar card.
- Polished the selection toolbar and candidate table with lighter hierarchy, softer progress bars, and safer horizontal table sizing on mobile.
- Added i18n keys and regenerated `en.json` / `vi.json`.

Verification:
- `pnpm --filter web i18n:build`
- `pnpm --filter web i18n:check`
- `pnpm --filter web lint`
- `pnpm --filter web typecheck`
- `pnpm --filter web test:e2e -- cleanup-unsubscribe-campaign.spec.ts`
- `pnpm --filter web test:e2e -- cleanup-suppression.spec.ts`
