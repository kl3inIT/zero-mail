---
status: completed
quick_id: 260517-analytics-chart-layout
completed: 2026-05-17
---

# Quick Task 260517-analytics-chart-layout Summary

## Changes

- Reworked metadata-only Analytics layout so related sections are grouped and unrelated sections are separated.
- Replaced repeated progress-bar visuals with mixed chart types:
  - grouped daily bar chart for observed/handled/reverted mail,
  - donut chart for Gmail category share,
  - stacked horizontal action chart for automation outcomes,
  - compact reply queue and opportunity cards.
- Moved domain load back into the sender panel, where it belongs contextually.
- Added `recharts` to support the shadcn-style chart patterns without changing the metadata-only backend contract.

## Verification

- `pnpm --filter web i18n:build`
- `pnpm --filter web i18n:check`
- `pnpm --filter web typecheck`
- Targeted ESLint for changed Analytics files.
- `pnpm --filter web exec vitest run features/analytics/__tests__/AnalyticsPanels.test.tsx --pool=forks`
- `pnpm --filter web exec playwright test analytics.spec.ts`
