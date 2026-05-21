---
status: complete
task: analytics-legibility-polish
date: 2026-05-17
---

# Summary

Completed analytics legibility polish:

- replaced Gmail category `count · percent` text with separate labelled badges
- added a progress bar per category row so the share reads visually
- strengthened card and table headers in the affected analytics sections
- improved follow-up opportunity stat hierarchy
- regenerated i18n bundles

# Verification

- `pnpm --filter web i18n:build`
- `pnpm --filter web i18n:check`
- `pnpm --filter web typecheck`
- `pnpm --filter web exec eslint features/analytics/components/MetadataLoadPanel.tsx features/analytics/components/MetadataControlPanel.tsx features/analytics/components/RuleHitsPanel.tsx features/analytics/messages.ts`
- `pnpm --filter web exec vitest run features/analytics/__tests__/AnalyticsPanels.test.tsx --pool=forks`
- `pnpm --filter web exec playwright test analytics.spec.ts`
- `git diff --check`
