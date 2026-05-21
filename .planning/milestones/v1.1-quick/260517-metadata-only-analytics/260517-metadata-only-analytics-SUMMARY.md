---
status: completed
quick_id: 260517-metadata-only-analytics
completed: 2026-05-17
---

# Quick Task 260517-metadata-only-analytics Summary

## Scope

Implemented metadata-only Analytics expansion. The feature uses only existing operational metadata from `mail_message_observed`, `triage_audit`, and `thread_reply_status`.

No Gmail scope changes were added. No message body, subject, snippet, raw prompt, completion, or embedding storage was introduced.

## Backend

- Extended analytics summary projections and `/api/analytics/summary` DTOs with daily load, action mix, domain load, Gmail category load, reply buckets, and automation opportunity counts.
- Preserved the old `AnalyticsSummaryProjection` constructor for existing tests and callers.
- Kept analytics queries tenant-scoped and closed-window bounded.

## Frontend

- Added metadata-only load and control panels to the Analytics page.
- Added compact bar visualizations for daily volume, categories, action mix, domains, reply buckets, and rule-tuning opportunities.
- Updated i18n messages, skeleton state, component tests, and Playwright analytics mock data.

## Verification

- `pnpm --filter web i18n:check`
- `pnpm --filter web typecheck`
- Targeted ESLint for changed analytics files.
- `pnpm --filter web exec vitest run features/analytics/__tests__/AnalyticsPanels.test.tsx --pool=forks`
- `pnpm --filter web exec playwright test analytics.spec.ts`
- `./gradlew.bat :backend:core:compileJava :backend:api:compileJava`
- With UTC env because the local Postgres rejects `TimeZone=Asia/Saigon`:
  - `./gradlew.bat :backend:core:test --tests com.zeromail.core.analytics.AnalyticsSummaryQueryServiceTest`
  - `./gradlew.bat :backend:api:test --tests com.zeromail.api.controllers.analytics.AnalyticsControllerContractTest`
- JetBrains error check passed for `AnalyticsSummaryQueryService.java` and `AnalyticsSummaryResponse.java`.
