---
phase: 05C-user-surface-analytics-daily-digest
plan: 04
subsystem: frontend-analytics-notifications
tags: [nextjs, react, tanstack-query, openapi-fetch, shadcn, playwright, i18n]

requires:
  - phase: 05C-02
    provides: GET /api/analytics/summary and AnalyticsSummaryResponse
  - phase: 05C-03
    provides: GET/PATCH /api/me/notifications and notification preference DTOs
provides:
  - authenticated /analytics route with 7d/30d/90d URL-driven window chips
  - analytics feature folder with typed API, query keys, hook, panels, i18n, and Vitest coverage
  - settings Notifications section with optimistic digest opt-out and send-hour selection
  - notification feature folder with typed API, query keys, hooks, i18n, component, and Vitest coverage
  - sidebar Analytics nav item and Playwright e2e coverage for analytics/settings notifications
affects: [ANL-01, WEB-02, apps-web, frontend-i18n, openapi-client]

tech-stack:
  added:
    - shadcn Select primitive source at apps/web/components/ui/select.tsx
  patterns:
    - closed-enum URL search param gate before every analytics API call
    - TanStack Query optimistic update with rollback/toast/retry action
    - per-feature messages.ts source-of-truth plus generated message bundle refresh

key-files:
  created:
    - apps/web/app/(protected)/(app)/analytics/page.tsx
    - apps/web/features/analytics/**
    - apps/web/features/notifications/**
    - apps/web/e2e/analytics.spec.ts
    - apps/web/e2e/settings-notifications.spec.ts
    - apps/web/components/ui/select.tsx
  modified:
    - apps/web/app/(protected)/(app)/settings/page.tsx
    - apps/web/components/shell/AppSidebar.tsx
    - apps/web/scripts/check-i18n.ts
    - apps/web/lib/api/schema.d.ts
    - apps/web/openapi/openapi.json
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json

requirements-completed:
  - ANL-01
  - WEB-02

duration: 1h 55m
completed: 2026-05-13
---

# Phase 05C Plan 04: Frontend Analytics + Notifications Summary

**Authenticated analytics route, digest notification settings, i18n, and browser coverage**

## Accomplishments

- Regenerated the OpenAPI artifact and typed web schema after running the backend `generateOpenApiDocs` task.
- Added `/analytics` with URL-normalized `7d | 30d | 90d` windows, four analytics panels, skeletons, zero-data states, and responsive rule-hit table/card renderers.
- Added `/settings` Notifications with digest opt-out, 0-23 send-hour selection, read-only time zone, optimistic PATCH persistence, rollback, and retry toast.
- Added Analytics to the app sidebar after Triage and before Needs Reply.
- Added vi/en message sources, rebuilt generated bundles, and extended `EN_SCAN_FILES`.
- Added focused Vitest suites and Playwright e2e specs for analytics, notification persistence, invalid window normalization, 320px layout, and localized policy disclosure.

## Required Execution Notes

- `pnpm --filter apps/web generate:api` did not match this workspace; the correct package filter is `web`.
- OpenAPI generation ran against the Gradle-emitted local artifact:
  1. `.\gradlew.bat :backend:api:generateOpenApiDocs`
  2. `pnpm --filter web generate:api`
- `Switch` was already installed. `Select` was missing despite the UI-SPEC registry note, so it was added with `pnpm dlx shadcn@latest add select` from `apps/web`.
- `frontend-design` was invoked before UI code. The UI-SPEC and prototype HTML were used as the visual ground truth: dense utility layout, teal-neutral accent, no marketing composition, no `.zm-proto` / `.zm-auth` skin classes.

## Deviations

- Rule hits render as a full-width panel at `md+` so the table has enough horizontal room; below `md`, the card-list renderer is implemented as planned.
- The Top Senders first row includes the optional rank-1 accent stripe.
- The refreshed label is deterministic (`0s`) rather than a live timer because the project ESLint profile rejects impure `Date.now()` during render and synchronous timer state updates in effects.

## Verification

- `pnpm --filter web typecheck` - PASS
- `pnpm --filter web lint` - PASS
- `pnpm --filter web i18n:build` - PASS
- `pnpm --filter web i18n:check` - PASS
- `pnpm --filter web test features/analytics --run` - PASS
- `pnpm --filter web test features/notifications --run` - PASS
- `pnpm --filter web test:e2e e2e/analytics.spec.ts e2e/settings-notifications.spec.ts` - PASS (7 tests)
- Playwright MCP manual pass - PASS: `/analytics?window=` canonicalized to `?window=7d`, 7d and 30d analytics GETs fired, all four panels rendered, 320px overflow check returned false, `/settings` Notifications rendered, Switch PATCH fired, Select disabled after opt-out, no console errors.

## User Setup Required

No new setup for Plan 04. Phase 5C still carries the Plan 03 manual deployment tasks for real digest delivery:

- Verify the Resend sender domain.
- Set `RESEND_API_KEY` and digest sender env vars in production.

## Phase Closure

Phase 5C is feature-complete for the planned code surfaces: analytics read model, digest backend, notification preferences, user-facing analytics route, and settings controls are all implemented and verified. Phase 6 can consume this as part of the launch hardening and end-to-end CASA-ready flow.

---
*Phase: 05C-user-surface-analytics-daily-digest*
*Completed: 2026-05-13*
