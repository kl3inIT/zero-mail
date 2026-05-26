---
status: complete
completed: 2026-05-26
---

# Summary

Fixed analytics chart responsiveness on narrow screens.

## Changes

- Allowed the shared Recharts `ChartContainer` and analytics panel cards to shrink inside grid/flex layouts.
- Constrained the analytics triage donut to `max-w-[220px]` and let its legend wrap on small screens.
- Added Playwright checks that the daily-load and triage panels fit the viewport and do not create panel-level horizontal overflow, including Vietnamese mobile rendering.

## Verification

- `pnpm --filter web typecheck`
- `pnpm --filter web test:e2e analytics.spec.ts`
- `pnpm --filter web lint`
