---
status: complete
task: analytics-explanation-layout
date: 2026-05-17
---

# Summary

Completed Analytics UI layout refinement:

- split crowded metadata-load and top-senders sections into separate chart cards
- removed the redundant inbox-flow card from the rendered analytics page
- removed user-facing metadata-only badges/labels
- added clickable info popovers to major analytics chart cards
- clarified the recovered-time estimate formula in localized copy
- regenerated i18n bundles and updated the analytics e2e assertion

# Verification

- `pnpm --filter web i18n:build`
- `pnpm --filter web i18n:check`
- `pnpm --filter web typecheck`
- `pnpm --filter web exec eslint features/analytics/components/AnalyticsPageClient.tsx features/analytics/components/AnalyticsSkeleton.tsx features/analytics/components/ChartInfoAction.tsx features/analytics/components/MetadataLoadPanel.tsx features/analytics/components/MetadataControlPanel.tsx features/analytics/components/TopSendersPanel.tsx features/analytics/components/RuleHitsPanel.tsx features/analytics/components/TimeSavedPanel.tsx features/analytics/components/VolumePanel.tsx features/analytics/messages.ts e2e/analytics.spec.ts`
- `pnpm --filter web exec vitest run features/analytics/__tests__/AnalyticsPanels.test.tsx --pool=forks`
- `pnpm --filter web exec playwright test analytics.spec.ts`
- `git diff --check`
