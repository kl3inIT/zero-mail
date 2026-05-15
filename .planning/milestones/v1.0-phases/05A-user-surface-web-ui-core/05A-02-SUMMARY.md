---
phase: 05A-user-surface-web-ui-core
plan: 02
subsystem: ui
tags: [nextjs, react, shadcn, tanstack-query, playwright, app-shell, i18n]

requires:
  - phase: 05A-user-surface-web-ui-core
    provides: Plan 01 shadcn primitives, triage/billing query keys, shell messages, and Phase 5A validation scaffolding
provides:
  - Persistent protected app shell with collapsible sidebar and top chrome
  - Route-group split for app chrome vs onboarding chrome suppression
  - Single triage pause read/write cache across chrome, settings, and PauseBanner
  - E2E coverage for shell, pause, connection health, and billing balance chrome
affects: [phase-05A, apps-web, shell, triage, billing, gmail, onboarding]

tech-stack:
  added: []
  patterns:
    - Next route-group layout split for protected app chrome
    - Server chrome-query prefetch with TanStack Query dehydration
    - Single-source optimistic mutation keyed on triageKeys.pauseState()
    - Playwright chrome coverage driven from Wave-2 routes only

key-files:
  created:
    - apps/web/app/(protected)/(app)/layout.tsx
    - apps/web/app/(protected)/onboarding/layout.tsx
    - apps/web/components/shell/AppShell.tsx
    - apps/web/components/shell/AppSidebar.tsx
    - apps/web/components/shell/ChromeHeader.tsx
    - apps/web/features/triage/hooks/useTriagePauseState.ts
    - apps/web/e2e/chrome-test-utils.ts
  modified:
    - apps/web/app/(protected)/layout.tsx
    - apps/web/app/(protected)/(app)/rules/page.tsx
    - apps/web/app/(protected)/(app)/settings/page.tsx
    - apps/web/features/triage/hooks/useToggleTriagePause.ts
    - apps/web/features/triage/components/PauseBanner.tsx
    - apps/web/features/shell/messages.ts
    - apps/web/e2e/app-shell.spec.ts
    - apps/web/e2e/pause-toggle.spec.ts
    - apps/web/e2e/connection-health.spec.ts
    - apps/web/e2e/billing-balance.spec.ts

key-decisions:
  - "Chose the preferred route-group split: `(protected)/layout.tsx` keeps only providers/auth, `(protected)/(app)/layout.tsx` owns the shell, and `(protected)/onboarding/layout.tsx` stays bare; no fallback segment branching was needed."
  - "Next 16 docs findings used during implementation: route groups nest layouts normally, `cookies()` is async in server layouts, and `usePathname()` stays client-only."
  - "No Plan 06 `EN_SCAN_FILES` reconciliation is needed for the route move because Plan 01 already registered the `(protected)/(app)` paths."
  - "The settings pause control now reads and writes the same `triageKeys.pauseState()` cache entry as the chrome and `PauseBanner`."

patterns-established:
  - "Protected app chrome data is prefetched in the `(app)` server layout, dehydrated, and consumed inside the shell subtree."
  - "Pause writes use optimistic `setQueryData(triageKeys.pauseState(), ...)`, rollback on error, and invalidate pause, billing balance, and `/me` on settle."
  - "Wave-2 shell E2E specs use existing `/rules` and `/settings` routes; `/triage` and `/billing` shell checks remain owned by Plans 03 and 04."

requirements-completed: [WEB-01, WEB-04]

duration: 93min
completed: 2026-05-12
---

# Phase 05A Plan 02: Protected App Shell Summary

**Protected SaaS chrome with route-group onboarding suppression, hydrated trust widgets, and single-source triage pause state**

## Performance

- **Duration:** 93 min
- **Started:** 2026-05-12T09:38:17Z
- **Completed:** 2026-05-12T11:11:43Z
- **Tasks:** 3
- **Files modified:** 25 app/test files

## Accomplishments

- Split protected routes so `(protected)/layout.tsx` is provider/auth-only, `(protected)/(app)/layout.tsx` hosts the persistent shell, and onboarding keeps a bare layout.
- Built `AppShell`, `AppSidebar`, and `ChromeHeader` with flat navigation, sidebar cookie state, pause, billing balance, Gmail health, language/settings/sign-out menu, and compact reconnect affordance.
- Prefetched pause, billing balance, and Gmail status in the shell server layout and dehydrated them into the client shell.
- Rebased chrome, `/settings`, and `PauseBanner` onto `useTriagePauseState()` plus the optimistic `useToggleTriagePause()` mutation.
- Added Playwright coverage for protected shell chrome, pause consistency, connection health, and balance polling at desktop and 320px.

## Frontend Design Note

Shell visual review: the shell stays compact and operational, with a flat icon rail, restrained status color for health/pause, and no marketing-style framing. Desktop and 320px layouts keep chrome controls reachable without horizontal scroll, and the surfaces remain compatible with light and dark themes.

## Task Commits

1. **Tasks 1-2: Route-group split, app shell, and pause single source** - `3107119` (feat)
2. **Task 3: App-shell chrome Playwright coverage** - `aa8ca2a` (test)

## Files Created/Modified

- `apps/web/app/(protected)/layout.tsx` - provider/auth host only; no shell imports or sidebar cookie read.
- `apps/web/app/(protected)/(app)/layout.tsx` - shell host with async cookies, three chrome prefetches, `dehydrate`, and `HydrationBoundary`.
- `apps/web/app/(protected)/onboarding/layout.tsx` - chrome-suppressed onboarding wrapper.
- `apps/web/components/shell/AppShell.tsx` - client shell composition with sidebar, header, pause banner, content, and single toaster.
- `apps/web/components/shell/AppSidebar.tsx` - flat icon-rail sidebar using `usePathname()` for active state.
- `apps/web/components/shell/ChromeHeader.tsx` - pause switch, balance pill, Gmail health dot/reconnect affordance, and user menu.
- `apps/web/features/triage/hooks/useTriagePauseState.ts` - invalidate-only pause read hook keyed on `triageKeys.pauseState()`.
- `apps/web/features/triage/hooks/useToggleTriagePause.ts` - optimistic pause mutation with rollback and pause/balance/account invalidations.
- `apps/web/app/(protected)/(app)/settings/page.tsx` - settings pause control rebased onto the same pause hooks.
- `apps/web/features/triage/components/PauseBanner.tsx` - banner now reads pause state from the shared pause query.
- `apps/web/features/shell/messages.ts` - added shell/nav chrome strings used by the new components.
- `apps/web/e2e/chrome-test-utils.ts` and shell-related specs - shared mock harness and chrome behavior coverage.

## Decisions Made

- Used the route-group split rather than layout segment branching because Next layout nesting supports the protected provider parent plus nested app/onboarding layouts cleanly.
- Left `SIDEBAR_COOKIE_NAME` as the literal `sidebar_state`, matching the private const from the copied shadcn primitive.
- Kept the chrome title out of an `h1` so existing page-level heading expectations remain valid.
- Used onboarding step `GMAIL_CONNECTED` for chrome-suppression coverage because `COMPLETE` correctly redirects to `/settings`, which should show the shell.
- Kept generated `apps/web/i18n/messages/{en,vi}.json` uncommitted; Plan 06 still owns canonical generated bundle commits.

## Deviations from Plan

None - plan executed within the route-group approach specified by the reviewed plan.

## Issues Encountered

- Full Playwright E2E passed with an existing browser-console warning about duplicate React keys in the rules action UI (`Actions-archive`). The warning is pre-existing and did not fail the Plan 02 gates.
- `apps/web/scripts/check-i18n.ts` remains locally dirty from line-ending/stat churn and was not edited for this plan.

## Verification

- `pnpm --filter web i18n:build` - passed.
- `pnpm --filter web typecheck` - passed.
- `pnpm --filter web lint` - passed.
- `pnpm --filter web i18n:check` - passed.
- `pnpm --filter web test` - passed, 35 files / 216 tests.
- `pnpm --filter web test:e2e -- app-shell pause-toggle connection-health billing-balance --workers=1 --reporter=dot` - passed, 13 passed.
- `pnpm --filter web test:e2e -- --workers=1 --reporter=dot` - passed, 43 passed / 5 skipped.
- `apps/web/lib/api/schema.d.ts` - unchanged.
- No new runtime dependency was added.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 03. The protected app shell now wraps `/rules` and `/settings`; Plans 03 and 04 should place `/triage` and `/billing` under `(protected)/(app)/` and add their page-specific shell-presence checks.

## Self-Check: PASSED

- Required shell, route-group, pause, and E2E artifacts exist.
- Task commits are present.
- Verification gates listed above passed.
- Generated locale bundles are not committed.

---
*Phase: 05A-user-surface-web-ui-core*
*Completed: 2026-05-12*
