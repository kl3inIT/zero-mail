---
phase: 05A-user-surface-web-ui-core
plan: 03
subsystem: ui
tags: [nextjs, react, shadcn, tanstack-query, playwright, triage, audit-log]

requires:
  - phase: 05A-user-surface-web-ui-core
    provides: Plans 01-02 shell, shadcn primitives, triage query keys, and protected app chrome
provides:
  - Shell-hosted /triage page with deep-linkable audit, shadow-mode, and sender tabs
  - Audit-log unavailable degradation path plus injected-data populated row tests
  - Shadow-mode write UI against the real PATCH endpoint
  - Sender safety-net list and opt-in UI
  - Desktop and 320px Playwright coverage for triage flows
affects: [phase-05A, apps-web, triage, audit, sender-safety-net, shadow-mode]

tech-stack:
  added: []
  patterns:
    - Next App Router client tabs synced through an allow-listed ?tab= search param
    - TanStack useInfiniteQuery unavailable sentinel for a missing backend list endpoint
    - Component-level injected fixture seam for unavailable backend read paths
    - PATCH response snapshot for write-only shadow-mode state

key-files:
  created:
    - apps/web/app/(protected)/(app)/triage/page.tsx
    - apps/web/features/triage/hooks/useTriageAuditLog.ts
    - apps/web/features/triage/hooks/useUndoAuditEntry.ts
    - apps/web/features/triage/hooks/useShadowMode.ts
    - apps/web/features/triage/hooks/useProtectedSenders.ts
    - apps/web/features/triage/hooks/useOptInSender.ts
    - apps/web/features/triage/components/TriagePageClient.tsx
    - apps/web/features/triage/components/AuditLog.tsx
    - apps/web/features/triage/components/AuditTable.tsx
    - apps/web/features/triage/components/AuditCardList.tsx
    - apps/web/features/triage/components/UndoButton.tsx
    - apps/web/features/triage/components/ShadowModeCard.tsx
    - apps/web/features/triage/components/SenderSafetyNetList.tsx
    - apps/web/features/triage/components/AuditLog.test.tsx
    - apps/web/features/triage/components/SenderSafetyNetList.test.tsx
  modified:
    - apps/web/features/triage/api/triage-api.ts
    - apps/web/features/triage/messages.ts
    - apps/web/e2e/triage-audit.spec.ts
    - apps/web/e2e/triage-shadow-senders.spec.ts

key-decisions:
  - "AuditEntry row model is local to the frontend until the backend list endpoint exists: id, timestamp, action, actionLabel, ruleName, reason, inverseAction, optional messageRef, undoableUntil, and undone."
  - "AuditLog exposes an `injectedData` seam with `{ unavailable?, entries?, hasNextPage?, isFetchingNextPage?, onLoadMore? }`, so populated audit rows are tested without inventing a backend list endpoint."
  - "The real shadow-mode contract is PATCH-only; no GET exists in backend/api or schema.d.ts. The UI starts from a local false default, writes via PATCH, and preserves the latest PATCH response in a client-session snapshot."
  - "The audit-list endpoint remains a documented backend gap. The production UI renders a distinct not-yet-available panel, not an empty state."

patterns-established:
  - "When a backend read surface is absent, render an explicit unavailable state and test populated UI paths through injected component data."
  - "For trust evidence, render the audit reason as full text on the card renderer and wrapped text in the table renderer."
  - "For tab URLs, validate `?tab=` against a fixed allow-list before calling router.replace."

requirements-completed: [WEB-01, WEB-02]

duration: 32min
completed: 2026-05-12
---

# Phase 05A Plan 03: Triage Workspace Summary

**Deep-linkable triage workspace with audit degradation, undo UI, shadow-mode writes, and sender opt-in controls**

## Performance

- **Duration:** 32 min
- **Started:** 2026-05-12T11:16:59Z
- **Completed:** 2026-05-12T11:49:21Z
- **Tasks:** 3
- **Files modified:** 21 app/test files

## Accomplishments

- Added `/triage` under `(protected)/(app)` with shadcn tabs synced to `?tab=audit|shadow|senders`.
- Extended triage API/helpers and hooks for audit sentinel pages, undo, shadow mode, protected senders, and sender opt-in.
- Built the audit table/card renderers with 30-day undo boundary, undo confirm, closed-window label, and full reason text on cards.
- Built the shadow-mode card and sender safety-net list with empty, loading, error, and populated states.
- Added component tests for unavailable audit, empty audit, populated audit rows, boundary placement, full reason text, sender empty/populated states, and opt-in.
- Added Playwright coverage for `/triage` shell presence, audit unavailable panel, tab deep links, shadow-mode writes, sender empty/populated states, and opt-in at desktop and 320px.

## Frontend Design Notes

- `/triage` page: compact operational layout, no hero treatment, with the audit evidence area as the focal element and tab navigation that remains usable at 320px.
- Audit table: dense rows, mono timestamps, wrapped reason text, and restrained action badges so trust evidence stays scannable.
- Audit card renderer: mobile-first evidence cards show the full reason, message reference, rule, action, and undo state without horizontal scroll.
- Shadow-mode card: quiet settings-card treatment with a small info-blue badge only when active; turn-off confirmation uses the existing alert-dialog language.
- Sender safety net: simple divided rows with sender email, opt-in state, and one clear action; empty state uses the shared component and avoids implying missing data is success.
- Light/dark compatibility: all surfaces use existing token classes, neutral cards, and limited semantic color for status only.

## Task Commits

1. **Task 1: Triage API and hooks** - `017f3b6` (feat)
2. **Deviation fix: Preserve PATCH-only shadow state after invalidation** - `7a21069` (fix)
3. **Task 2: Triage page, components, messages, and component tests** - `fcac573` (feat)
4. **Task 3: Triage Playwright flows** - `d964039` (test)

## Files Created/Modified

- `apps/web/features/triage/api/triage-api.ts` - typed API helpers, local `AuditEntry`, audit unavailable page, and shadow read-gap snapshot.
- `apps/web/features/triage/hooks/useTriageAuditLog.ts` - `useInfiniteQuery` wrapper over the unavailable audit sentinel.
- `apps/web/features/triage/hooks/useUndoAuditEntry.ts` - undo mutation invalidating audit and billing balance.
- `apps/web/features/triage/hooks/useShadowMode.ts` - shadow-mode read/write pair around the write-only PATCH endpoint.
- `apps/web/features/triage/hooks/useProtectedSenders.ts`, `useOptInSender.ts` - sender list and opt-in hooks.
- `apps/web/app/(protected)/(app)/triage/page.tsx` and `TriagePageClient.tsx` - shell-hosted page and deep-linkable tabs.
- `AuditLog.tsx`, `AuditTable.tsx`, `AuditCardList.tsx`, `AuditRow.tsx`, `UndoButton.tsx` - audit renderers and undo UI.
- `ShadowModeCard.tsx`, `SenderSafetyNetList.tsx`, `SenderRow.tsx` - shadow and sender tabs.
- `AuditLog.test.tsx`, `SenderSafetyNetList.test.tsx` - fixture-backed component coverage.
- `triage-audit.spec.ts`, `triage-shadow-senders.spec.ts` - real-browser triage flow coverage.
- `apps/web/features/triage/messages.ts` - vi/en source messages for the new triage surface.

## Decisions Made

- Used Context7-confirmed TanStack Query v5 shape: `useInfiniteQuery` with explicit `initialPageParam` and `getNextPageParam`; mutations invalidate via `queryClient.invalidateQueries`.
- Used Context7-confirmed Next App Router behavior: `useSearchParams()` stays in a client component wrapped by the server page's `Suspense`, and tab changes call `router.replace`.
- Kept `apps/web/lib/api/schema.d.ts` unchanged. No backend endpoint was added or regenerated.
- Open Question 1 resolved: no audit-list endpoint exists, only undo. The audit tab degrades to "audit history not available yet"; populated rows are component-tested through `injectedData`.
- Open Question 4 resolved: the eventual backend audit-entry shape is unknown, so the frontend row model is intentionally local and limited to fields the UI needs.
- Additional backend finding: shadow mode is write-only in the current API surface (`PATCH /api/tenant/triage/shadow-mode`), despite the plan assuming GET/PUT.

## Deviations from Plan

### Auto-fixed Issues

**1. [Contract drift] Shadow mode endpoint is PATCH-only and has no read**
- **Found during:** Task 1 API verification against backend controllers and `schema.d.ts`.
- **Issue:** Plan text expected GET/PUT, but the committed backend exposes only `PATCH /api/tenant/triage/shadow-mode`; there is no read endpoint or `/me` field.
- **Fix:** Implemented the real PATCH write and a documented local read fallback that starts false and preserves the latest PATCH response for the browser session.
- **Files modified:** `apps/web/features/triage/api/triage-api.ts`, `apps/web/features/triage/hooks/useShadowMode.ts`, `ShadowModeCard.tsx`, `triage-shadow-senders.spec.ts`.
- **Verification:** typecheck/lint passed; Playwright asserts PATCH writes and coherent UI state.
- **Committed in:** `017f3b6`, `7a21069`, `d964039`.

**Total deviations:** 1 backend contract drift handled in frontend without backend changes.
**Impact on plan:** Shadow mode writes work against the real endpoint; initial persisted read remains a backend gap for a later API plan.

## Issues Encountered

- Full Playwright passed with the pre-existing duplicate React key warning from the rules action UI (`Actions-archive`). It is not caused by Plan 03.
- Generated `apps/web/i18n/messages/{en,vi}.json` were rebuilt for verification and intentionally left uncommitted.

## Verification

- `pnpm --filter web i18n:build` - passed.
- `pnpm --filter web typecheck` - passed.
- `pnpm --filter web lint` - passed.
- `pnpm --filter web i18n:check` - passed.
- `pnpm --filter web test -- features/triage/components` - passed, 3 files / 12 tests.
- `pnpm --filter web test:e2e -- triage-audit triage-shadow-senders --workers=1 --reporter=dot` - passed, 8 tests.
- `pnpm --filter web test` - passed, 37 files / 225 tests.
- `pnpm --filter web test:e2e -- --workers=1 --reporter=dot` - passed, 51 passed / 3 skipped.
- `apps/web/lib/api/schema.d.ts` - unchanged.
- No new runtime dependency was added.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 04. `/triage` now exists inside the protected app shell, and `/billing` can follow the same `(protected)/(app)` route pattern.

## Self-Check: PASSED

- Required triage page, hooks, components, tests, and e2e specs exist.
- Audit-list gap is documented in code, e2e, and this summary.
- Shadow-mode read contract drift is documented.
- Verification gates listed above passed.
- Generated locale bundles are not committed.

---
*Phase: 05A-user-surface-web-ui-core*
*Completed: 2026-05-12*
