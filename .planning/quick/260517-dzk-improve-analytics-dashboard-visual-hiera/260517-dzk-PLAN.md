---
status: executing
quick_id: 260517-dzk
created: 2026-05-17
---

# Quick Task 260517-dzk: Improve Analytics Dashboard Visual Hierarchy

## Goal

Make `/analytics` easier to scan and more visual, closer to an Inbox Zero-style operational dashboard: clear hierarchy, explicit ratios, and multiple truthful chart types using only currently available metadata.

## Tasks

1. Refactor analytics visualization helpers and panels.
   - Files: `apps/web/features/analytics/components/**`
   - Action: add shared chart/data helpers, improve Volume, Time Saved, Top Senders, and Rule Hits with rings, bars, stacked bars, precision/trust indicators, and domain grouping.
   - Verify: panel tests still pass and no NaN/overflow states appear.

2. Improve Analytics page layout and loading skeleton.
   - Files: `apps/web/features/analytics/components/AnalyticsPageClient.tsx`, `AnalyticsSkeleton.tsx`, `apps/web/app/(protected)/(app)/analytics/page.tsx`
   - Action: add an Inbox Flow panel and rebuild the page as a dense but readable dashboard.
   - Verify: desktop and mobile layout can be exercised through Playwright.

3. Fix Analytics i18n copy.
   - Files: `apps/web/features/analytics/messages.ts`, generated locale bundles.
   - Action: replace mojibake Vietnamese analytics copy and add labels for the new visualizations.
   - Verify: `pnpm --filter web i18n:check` passes.
