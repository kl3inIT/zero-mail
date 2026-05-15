---
phase: 05B-user-surface-ai-draft-replies
plan: 06
subsystem: ui
tags: [nextjs, react, tanstack-query, openapi, shadcn, playwright, i18n]

requires:
  - phase: 05B-05
    provides: "GET /api/threads, POST /api/threads/{gmailThreadId}/draft, POST /api/threads/{gmailThreadId}/resolve, and GET /api/triage/audit OpenAPI contract"
provides:
  - "Authenticated /needs-reply route with two-bucket inbox tabs, responsive rows, empty/loading/error states, and Gmail deep links"
  - "Needs reply sidebar nav item with a cached TO_REPLY badge using the lightweight count hook"
  - "Draft reply / Regenerate draft action shared by needs-reply rows and save_draft audit rows"
  - "Live /triage audit list wired to GET /api/triage/audit with cursor load-more and end-of-list copy"
affects: [apps-web, web-02, triage-audit, needs-reply, draft-review]

tech-stack:
  added: []
  patterns:
    - "Feature-local OpenAPI helpers + TanStack Query hooks for needs-reply"
    - "Hydration-safe dynamic count badges via useSyncExternalStore"
    - "Shared GenerateDraftButton for needs-reply and triage audit save_draft rows"

key-files:
  created:
    - apps/web/features/needs-reply/api/needs-reply-api.ts
    - apps/web/features/needs-reply/query-keys.ts
    - apps/web/features/needs-reply/hooks/useNeedsReplyInbox.ts
    - apps/web/features/needs-reply/hooks/useToReplyCount.ts
    - apps/web/features/needs-reply/hooks/useGenerateDraft.ts
    - apps/web/features/needs-reply/hooks/useMarkResolved.ts
    - apps/web/features/needs-reply/components/NeedsReplyPageClient.tsx
    - apps/web/features/needs-reply/components/NeedsReplyTabs.tsx
    - apps/web/features/needs-reply/components/NeedsReplyTable.tsx
    - apps/web/features/needs-reply/components/NeedsReplyRow.tsx
    - apps/web/features/needs-reply/components/GenerateDraftButton.tsx
    - apps/web/app/(protected)/(app)/needs-reply/page.tsx
    - apps/web/lib/use-hydrated.ts
  modified:
    - apps/web/components/shell/AppSidebar.tsx
    - apps/web/features/triage/api/triage-api.ts
    - apps/web/features/triage/hooks/useTriageAuditLog.ts
    - apps/web/features/triage/components/AuditLog.tsx
    - apps/web/features/triage/components/AuditRow.tsx
    - apps/web/features/triage/components/AuditTable.tsx
    - apps/web/features/triage/components/AuditCardList.tsx
    - apps/web/features/triage/messages.ts
    - apps/web/features/needs-reply/messages.ts
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json

key-decisions:
  - "The sidebar badge uses useToReplyCount() with limit=1 fallback because Plan 05 did not add a dedicated count endpoint."
  - "Dynamic needs-reply counts are hidden until hydration via useHydrated() to prevent SSR/client count mismatches."
  - "Vietnamese 409/failure draft copy is present but should be reviewed by a Vietnamese speaker before release."

patterns-established:
  - "Needs-reply rows never fetch or render draft bodies; the only review/edit/send path is the Gmail deep link."
  - "409 draft-lock responses are handled inline on the row and do not fire destructive toasts."

requirements-completed: [DRFT-04]

duration: 1h 36m
completed: 2026-05-13
---

# Phase 05B Plan 06: Needs-Reply Web Surface Summary

**Next.js needs-reply inbox with live audit pagination, shared draft-generation controls, and a cached sidebar reply count**

## Performance

- **Duration:** 1h 36m
- **Started:** 2026-05-12T22:35:00Z
- **Completed:** 2026-05-13T00:11:32Z
- **Tasks:** 2/2
- **Files modified:** 29

## Accomplishments

- Built the needs-reply feature data layer, query keys, mutations, route, tabs, responsive table/card rows, and browser-tested E2E path.
- Replaced the 5A triage audit sentinel with live `GET /api/triage/audit` cursor pagination and added `Load more` / end-of-list UI.
- Added the shared draft-generation action to needs-reply rows and `save_draft` audit rows, including regenerate confirmation, 409 inline cooldown, and no draft body rendering.
- Added the app-sidebar `Needs reply` nav item with a hydration-safe cached count badge.

## Task Commits

1. **Task 1: API + hooks + i18n + live audit rewire** - `6f47b14` (`feat`)
2. **Task 2: UI components + route + sidebar + audit-row draft action** - `7c8dc5d` (`feat`)

## Files Created/Modified

- `apps/web/features/needs-reply/api/needs-reply-api.ts` - typed API calls for inbox, count fallback, draft generation, and resolve.
- `apps/web/features/needs-reply/hooks/*` - TanStack Query hooks for infinite inbox pages, count, draft generation, and resolve.
- `apps/web/features/needs-reply/components/*` - page client, tabs, table, rows, and shared draft-generation button.
- `apps/web/app/(protected)/(app)/needs-reply/page.tsx` - protected app route mounting the needs-reply page.
- `apps/web/components/shell/AppSidebar.tsx` - new nav item and TO_REPLY badge.
- `apps/web/features/triage/*` - live audit hook/API mapping, load-more/end-of-list UI, and save_draft row action.
- `apps/web/e2e/*` - needs-reply golden path unblocked and triage audit E2E updated for the live endpoint.
- `apps/web/i18n/messages/{en,vi}.json` - regenerated lock-step locale bundles.

## Decisions Made

- `useToReplyCount()` uses `GET /api/threads?bucket=to-reply&limit=1&resolved=false` and reads `toReplyCount`; this is the lightest available contract until a dedicated count endpoint exists.
- Count badges wait for client hydration through `useHydrated()` so SSR starts from a stable zero/hidden badge and updates after mount.
- Regenerate draft is confirmation-gated; first draft generation is not.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed hydration mismatch for dynamic count badges**
- **Found during:** Playwright MCP browser verification
- **Issue:** The server rendered a zero count while the client had the mocked count before hydration completed, causing a React hydration error.
- **Fix:** Added `apps/web/lib/use-hydrated.ts` using `useSyncExternalStore`, then gated needs-reply tab counts and the sidebar badge until hydration.
- **Files modified:** `apps/web/lib/use-hydrated.ts`, `apps/web/features/needs-reply/components/NeedsReplyPageClient.tsx`, `apps/web/components/shell/AppSidebar.tsx`
- **Verification:** Re-ran Playwright MCP dark-mode pass; console reported 0 errors, and automated lint/typecheck/E2E stayed green.
- **Committed in:** `7c8dc5d`

**2. [Rule 1 - Bug] Updated stale triage-audit E2E expectations**
- **Found during:** Task 2 E2E verification
- **Issue:** The old triage-audit spec still expected the 5A unavailable panel and had a mobile strict-locator collision after the live empty state landed.
- **Fix:** Mocked `GET /api/triage/audit`, asserted the live empty state, and made the page-heading locator exact.
- **Files modified:** `apps/web/e2e/triage-audit.spec.ts`
- **Verification:** `pnpm -C apps/web e2e -- triage-audit.spec.ts --workers=1`
- **Committed in:** `7c8dc5d`

---

**Total deviations:** 2 auto-fixed (2 Rule 1 bugs)
**Impact on plan:** Both fixes were required to make the planned UI and verification contract correct. No scope expansion beyond the 05B-06 surface.

## Verification

- `pnpm -C apps/web typecheck`
- `pnpm -C apps/web lint`
- `pnpm -C apps/web test`
- `pnpm -C apps/web i18n:check`
- `pnpm -C apps/web test -- features/needs-reply features/triage`
- `pnpm -C apps/web e2e -- needs-reply.spec.ts`
- `pnpm -C apps/web e2e -- triage-audit.spec.ts --workers=1`
- `rg -n "unavailable|AuditLogUnavailablePage|isAuditLogUnavailable" apps/web/features/triage` returned no matches.
- `rg -n "drafts\\(\\)\\.(send|update)|draft body|\\bSend\\b|\\bEdit\\b" apps/web/features/needs-reply backend/api/src/main/java/com/zeromail/api/controllers/thread backend/api/src/main/java/com/zeromail/api/dto/thread` returned no matches.
- Playwright MCP visual review: desktop populated needs-reply, mobile awaiting-empty at 320px, destructive error state, and dark theme. All checked with no horizontal overflow; final dark/mobile passes had 0 console errors.

## Known Stubs

None.

## Threat Flags

None.

## Issues Encountered

- The first Playwright MCP route mock used `URL`, which was not available in the MCP VM context. Replaced it with simple path parsing for the browser verification script. This did not affect production code.
- The `useToReplyCount()` fallback still calls `GET /api/threads` with `limit=1`; it avoids a full inbox load, but a dedicated count endpoint would be cleaner if this becomes hot.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 07 can validate the full Phase 5B surface with the needs-reply UI, live audit list, and draft-review path in place. The remaining release note is that the new Vietnamese safety/error copy should be reviewed by a Vietnamese speaker before launch.

## Self-Check: PASSED

- Created files listed in `key-files.created` exist.
- Task commits exist: `6f47b14`, `7c8dc5d`.
- Plan acceptance checks listed above pass.

---
*Phase: 05B-user-surface-ai-draft-replies*
*Completed: 2026-05-13*
