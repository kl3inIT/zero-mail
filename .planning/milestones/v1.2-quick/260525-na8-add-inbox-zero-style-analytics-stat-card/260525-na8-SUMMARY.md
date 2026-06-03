---
status: complete
date: 2026-05-25
---

# Quick Task 260525-na8 Summary

Implemented the analytics UI update from the supplied references:

- Replaced the analytics window tabs with select controls for range and grouping.
- Kept the existing daily volume chart in place.
- Added a separate archived/deleted bar chart using current analytics data.
- Updated analytics i18n messages and regenerated locale JSON.

Verification:

- `pnpm --filter web run typecheck`
- `pnpm --filter web run lint -- features/analytics/components/AnalyticsPageClient.tsx features/analytics/messages.ts`
- `pnpm --filter web test -- AnalyticsPanels.test.tsx`
- Browser smoke against `http://localhost:3000/analytics?window=7d`, screenshot at `apps/web/test-results/analytics-select-added-chart-desktop.png`
