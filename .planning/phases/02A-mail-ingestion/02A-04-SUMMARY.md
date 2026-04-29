---
phase: 02A-mail-ingestion
plan: "04"
subsystem: frontend
tags: [nextjs, react, tanstack-query, openapi, i18n, gmail-ingestion]

requires:
  - phase: 02A-02
    provides: Gmail ingestion health state and backend worker behavior
  - phase: 02A-03
    provides: /tenant/triage-pause endpoint and extended /me response
provides:
  - Triage pause client API, mutation hook, and global PauseBanner
  - Settings pause toggle and reconnect prompt gate for unhealthy ingestion
  - Regenerated OpenAPI and frontend schema for triage pause and /me extensions
affects: [phase-04-triage, frontend-settings, gmail-reconnect, i18n]

tech-stack:
  added: []
  patterns:
    - TanStack Query mutation invalidates accountKeys.me() after triage pause updates
    - Client-side pause state reads from the /me query cache
    - Web API generation defaults to the checked-in OpenAPI artifact when no live API URL is provided

key-files:
  created:
    - apps/web/features/triage/api/keys.ts
    - apps/web/features/triage/api/triagePause.ts
    - apps/web/features/triage/hooks/useToggleTriagePause.ts
    - apps/web/features/triage/components/PauseBanner.tsx
  modified:
    - apps/web/app/(protected)/layout.tsx
    - apps/web/app/(protected)/settings/page.tsx
    - apps/web/features/account/api/me.ts
    - apps/web/features/gmail/components/ReconnectPrompt.test.tsx
    - apps/web/features/triage/components/PauseBanner.test.tsx
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json
    - apps/web/lib/api/schema.d.ts
    - apps/web/openapi/openapi.json
    - apps/web/scripts/check-i18n.ts
    - apps/web/scripts/generate-api.ts

key-decisions:
  - "Use a plain accessible toggle button because apps/web has no shadcn Switch primitive installed."
  - "Default generate-api.ts to openapi/openapi.json so Gradle's hermetic OpenAPI output can drive frontend schema generation without a live backend."

patterns-established:
  - "PauseBanner owns its own /me read and returns null when triagePaused is false; layouts can render it unconditionally."
  - "ReconnectPrompt remains presentational; settings owns the DISCONNECTED or CONNECTED-plus-unhealthy gate."

requirements-completed: [MAIL-05, MAIL-06]

duration: 16min
completed: 2026-04-29
---

# Phase 02A Plan 04: Mail Ingestion Frontend Controls Summary

**Settings now exposes the triage pause control, unhealthy Gmail ingestion now reuses the reconnect prompt, and the frontend schema reflects the new backend mail-ingestion surface.**

## Performance

- **Duration:** 16 min
- **Started:** 2026-04-29T06:26:17Z
- **Completed:** 2026-04-29T06:42:22Z
- **Tasks:** 2/2
- **Files modified:** 15

## Accomplishments

- Added `features/triage` API, key factory, mutation hook, and non-dismissible `PauseBanner`.
- Extended settings with a pause toggle Card and the MAIL-05 reconnect gate for `CONNECTED` plus unhealthy ingestion.
- Regenerated `openapi.json` and `schema.d.ts` for `/tenant/triage-pause`, `triagePaused`, and `gmailConnectionStatus`.
- Enabled the Wave 0 `ReconnectPrompt` tests and kept all four Phase 02A frontend test files green.

## Task Commits

1. **Task 1: triage feature folder, /me typing, and i18n keys** - `3f9a178` (feat)
2. **Task 2: settings toggle, layout banner, OpenAPI regen, and frontend tests** - `a2e1467` (feat)

## Files Created/Modified

- `apps/web/features/triage/api/triagePause.ts` - PUT client for `/tenant/triage-pause` with XSRF header.
- `apps/web/features/triage/hooks/useToggleTriagePause.ts` - TanStack Query mutation invalidating `accountKeys.me()`.
- `apps/web/features/triage/components/PauseBanner.tsx` - Persistent warning banner with inline resume action.
- `apps/web/app/(protected)/layout.tsx` - Renders `PauseBanner` inside the protected query provider.
- `apps/web/app/(protected)/settings/page.tsx` - Adds pause toggle Card and unhealthy-ingestion reconnect gate.
- `apps/web/features/account/api/me.ts` - Adds `triagePaused` and `gmailConnectionStatus` to `CurrentUser`.
- `apps/web/openapi/openapi.json` and `apps/web/lib/api/schema.d.ts` - Generated API artifacts.
- `apps/web/scripts/generate-api.ts` - Defaults codegen to local OpenAPI artifact and still supports `API_SPEC_URL`.
- `apps/web/i18n/messages/en.json`, `apps/web/i18n/messages/vi.json`, `apps/web/scripts/check-i18n.ts` - Pause copy and scanner coverage.

## Verification

- `.\gradlew.bat :backend:api:generateOpenApiDocs` - PASS, `BUILD SUCCESSFUL`.
- `pnpm --filter zeromail-web run generate:api` - PASS, `openapi/openapi.json -> lib/api/schema.d.ts`.
- `pnpm --filter zeromail-web run test` - PASS, 27 files / 150 tests.
- `pnpm --filter zeromail-web exec vitest run features/triage/components/PauseBanner.test.tsx features/triage/hooks/useToggleTriagePause.test.tsx __tests__/architecture/phase-02a-files.test.ts features/gmail/components/ReconnectPrompt.test.tsx` - PASS, 4 files / 13 tests.
- `pnpm --filter zeromail-web exec tsc --noEmit` - PASS.
- `pnpm --filter zeromail-web run lint` - PASS.
- `pnpm --filter zeromail-web run i18n:check` - PASS, 318 leaf keys.
- Acceptance scan - PASS for layout banner render, settings i18n key, settings hook usage, reconnect gate, enabled ReconnectPrompt tests, and schema extensions.

Note: the PLAN examples used `pnpm -F web`, but this workspace package is named `zeromail-web`; verification used the repository's actual package selector.

## Decisions Made

- Used a plain toggle button with `aria-pressed` instead of installing shadcn `Switch`, because `apps/web/components/ui/switch.tsx` is not present and the plan explicitly forbids installing a new primitive here.
- Kept `ReconnectPrompt` presentational and placed the ingestion-health gate in settings, matching D-D3.
- Fixed `generate-api.ts` rather than leaving a manual schema workaround, because the planned regen command was blocked by the script's live-backend-only default.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added XSRF header to triage pause PUT**
- **Found during:** Task 1
- **Issue:** The plan snippet omitted XSRF handling for a state-changing authenticated request.
- **Fix:** Followed the existing `disconnectGmail` mutating-call pattern and sent `X-XSRF-TOKEN` when available.
- **Files modified:** `apps/web/features/triage/api/triagePause.ts`
- **Verification:** Typecheck, i18n check, and frontend tests passed.
- **Committed in:** `3f9a178`

**2. [Rule 1 - Test Bug] Updated PauseBanner test to follow the message bundle**
- **Found during:** Task 1
- **Issue:** The Wave 0 test hard-coded `/unpause/i`, while the plan's English copy for the button is `Resume`.
- **Fix:** Asserted against `enMessages.settings.triage.pause.banner.unpause` instead of a stale literal.
- **Files modified:** `apps/web/features/triage/components/PauseBanner.test.tsx`
- **Verification:** Targeted Phase 02A frontend tests passed.
- **Committed in:** `3f9a178`

**3. [Rule 3 - Blocking] Fixed local OpenAPI schema generation**
- **Found during:** Task 2
- **Issue:** `pnpm --filter zeromail-web run generate:api` tried to fetch `localhost:8080` after `generateOpenApiDocs` stopped its forked server.
- **Fix:** `generate-api.ts` now defaults to `openapi/openapi.json`, supports `API_SPEC_PATH`, and keeps `API_SPEC_URL` for live-server generation.
- **Files modified:** `apps/web/scripts/generate-api.ts`
- **Verification:** `pnpm --filter zeromail-web run generate:api` passed after the fix.
- **Committed in:** `a2e1467`

---

**Total deviations:** 3 auto-fixed (1 missing critical, 1 test bug, 1 blocking tooling issue).
**Impact on plan:** All fixes were necessary for correctness or plan verification. No product scope was added.

## Issues Encountered

- `pnpm -F web` does not match this workspace package; the actual package name is `zeromail-web`. Verification used `pnpm --filter zeromail-web ...`.
- A one-off targeted Vitest rerun with `--reporter=basic` failed because Vitest 4.1.5 does not provide that reporter alias. The same targeted test set was rerun without the unsupported reporter and passed.

## Known Stubs

| File | Lines | Reason |
|------|-------|--------|
| `apps/web/i18n/messages/en.json` | 295-300 | Pre-existing Phase 1.6 legal-page placeholder copy for Terms and Privacy pages; unrelated to this plan and does not affect MAIL-05/MAIL-06. |
| `apps/web/i18n/messages/vi.json` | 295-300 | Pre-existing Phase 1.6 legal-page placeholder copy for Terms and Privacy pages; unrelated to this plan and does not affect MAIL-05/MAIL-06. |

## Threat Flags

None. The new frontend call targets the planned `/tenant/triage-pause` boundary from the threat model, and the settings page does not render raw ingestion-health enum values to users.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 02A-05 can build on the visible pause state, reconnect prompt gate, and generated schema. Phase 4 remains responsible for actually reading `triagePaused` before enqueueing or executing automated triage actions.

## Self-Check: PASSED

- Key files exist on disk.
- Task commits `3f9a178` and `a2e1467` exist in git history.
- Worktree only has the pre-existing `.planning/config.json` change plus this uncommitted summary before metadata updates.

---

*Phase: 02A-mail-ingestion*
*Completed: 2026-04-29*
