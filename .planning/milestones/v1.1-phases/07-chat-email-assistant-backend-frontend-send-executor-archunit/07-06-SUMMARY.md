---
phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit
plan: 06
subsystem: frontend
tags: [chat, nextjs, react, ai-sdk, ai-elements, playwright, i18n]

requires:
  - phase: 07-05
    provides: Confirm/cancel endpoints, pending actions, confirmed-send executor, VIP enforcement, replayable chat history
provides:
  - Protected `/chat` route with conversation pane, history sidebar, and Vietnamese-default chrome
  - AI SDK 6 `useChat` transport wired to `/api/chat` with cookie auth and XSRF header parity
  - Vendored AI Elements primitives under `apps/web/components/ai`
  - Generic preview-card shell with exactly 9 body slots for the user-confirmable tools
  - Frontend tool catalog contract test for the 24-tool authoritative list
  - Eight focused Playwright chat specs covering streaming, replay, race, history, locale, VIP, outside-thread badge, and CSRF parity
affects: [phase-07, phase-08, chat-frontend, assistant-settings, e2e]

tech-stack:
  added:
    - ai
    - "@ai-sdk/react"
    - streamdown
    - ai-elements vendored source
    - "@radix-ui/react-use-controllable-state"
    - "@streamdown/cjk"
    - "@streamdown/code"
    - "@streamdown/math"
    - "@streamdown/mermaid"
    - motion
    - nanoid
    - shiki
    - use-stick-to-bottom
  patterns:
    - Feature-owned chat API/hooks/query-keys/messages per CONVENTIONS #8
    - Single preview-card shell dispatching to body-slot components by tool name
    - Playwright chat harness with backend route interception and explicit XSRF assertions

key-files:
  created:
    - apps/web/app/(protected)/(app)/chat/page.tsx
    - apps/web/app/(protected)/(app)/chat/layout.tsx
    - apps/web/features/chat/api/chat-api.ts
    - apps/web/features/chat/hooks/use-chat.ts
    - apps/web/features/chat/hooks/use-chat-history.ts
    - apps/web/features/chat/hooks/use-confirm-action.ts
    - apps/web/features/chat/components/chat-workspace.tsx
    - apps/web/features/chat/components/conversation-pane.tsx
    - apps/web/features/chat/components/history-sidebar.tsx
    - apps/web/features/chat/components/preview-card/preview-card.tsx
    - apps/web/features/chat/components/preview-card/preview-card-state.ts
    - apps/web/features/chat/components/preview-card/body/*.tsx
    - apps/web/features/chat/messages.ts
    - apps/web/e2e/chat/*.spec.ts
    - apps/web/e2e/chat/chat-test-utils.ts
    - apps/web/__tests__/chat/tool-catalog-contract.test.ts
  modified:
    - apps/web/package.json
    - pnpm-lock.yaml
    - apps/web/eslint.config.mjs
    - apps/web/.prettierignore
    - apps/web/i18n/messages/en.json
    - apps/web/i18n/messages/vi.json

key-decisions:
  - "User explicitly opted out of frontend-design skill for this plan; UI implementation followed existing UI-SPEC/prototype and local shadcn/Base UI primitives without invoking that skill."
  - "Frontend package filter is `web`, not `@zero-mail/web`; all pnpm commands used `--filter web`."
  - "AI Elements current CLI writes `components/ai-elements` and installs selected component dependencies; sources were normalized to `components/ai` and unused CLI deps were trimmed."
  - "Vendored response/loader compatibility wrappers were added because current AI Elements registry has no separate `response` or `loader` entries."
  - "PreviewCard adds a local in-flight guard so double-clicking Send cannot enqueue two frontend confirm mutations."
  - "Chat Playwright folder is verified with `--workers=1` because Next dev route compilation was unstable under first-run 8-worker contention."

patterns-established:
  - "Transport CSRF parity: `DefaultChatTransport` headers and `prepareSendMessagesRequest` both use a concrete XSRF header record."
  - "Replay path maps `chat_message.parts` to AI SDK UI messages and renders terminal preview cards without firing confirm."
  - "Body-slot map is the frontend enforcement point for the 9 user-confirmable tools."
  - "Chat e2e mocks backend API boundaries at `API_ROUTE_PATTERN` and asserts HTTP request headers/bodies where safety matters."

requirements-completed:
  - CHAT-01
  - CHAT-04
  - CHAT-06
  - CHAT-07
  - CHAT-08
  - SET-SAFE-05

duration: 7h
completed: 2026-05-18
---

# Phase 07 Plan 06: Frontend Chat Surface Summary

**Next.js `/chat` workspace with AI SDK streaming, durable confirmation preview cards, Vietnamese chrome, and focused browser coverage**

## Performance

- **Duration:** ~7h across resumed execution
- **Started:** 2026-05-18T16:00:00+07:00
- **Completed:** 2026-05-18T23:02:53+07:00
- **Tasks:** 8 planned tasks covered
- **Files modified:** frontend package/config/i18n plus chat route, chat feature, AI primitives, and e2e tests

## Accomplishments

- Added `/chat` inside the protected app shell with a history sidebar and conversation pane.
- Wired AI SDK 6 `useChat` through `DefaultChatTransport`, `credentials: include`, `experimental_throttle: 100`, and XSRF headers for `/api/chat`.
- Added history, detail, confirm, cancel, and soft-delete frontend API functions and TanStack Query hooks.
- Built one generic `PreviewCard` shell and exactly 9 body slots: `createRule`, `deleteRule`, `removeSenderFromSafetyNet`, `bulkArchive`, `saveMemory`, `updatePersonalInstructions`, `sendEmail`, `replyEmail`, `forwardEmail`.
- Added VIP acknowledgement gating and outside-source-thread recipient badges.
- Added frontend contract coverage for the 24-tool authoritative list and 8 browser specs for the chat surface.
- Kept generated `apps/web/i18n/messages/{vi,en}.json` in sync via `pnpm --filter web i18n:build`.

## Task Commits

1. **Tasks 6.1-6.7: Frontend chat surface, AI Elements primitives, preview cards, i18n, and e2e coverage** - `85f3bc1e`

**Plan metadata:** this summary plus GSD state/roadmap updates.

## Verification

- `pnpm --filter web typecheck` - PASS
- `pnpm --filter web lint` - PASS
- `pnpm --filter web i18n:check` - PASS
- `pnpm --filter web test -- __tests__/chat/tool-catalog-contract.test.ts` - PASS, 2 tests
- `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm --filter web test:e2e -- e2e/chat --workers=1 --reporter=list` - PASS, 8 tests
- `pnpm --filter web build` - PASS; `/chat` appears in the production route table
- Browser smoke URL: `http://localhost:3000/chat` returned HTTP 200 after the dev server was restarted with logs outside the repo.

Playwright trace/snapshot evidence was generated under `apps/web/test-results/` during failed and passing runs; no standalone screenshot file was saved because the final verification used automated assertions rather than manual snapshot capture.

## Files Created/Modified

- `apps/web/app/(protected)/(app)/chat/page.tsx` - protected chat route entry with suspense fallback.
- `apps/web/app/(protected)/(app)/chat/layout.tsx` - chat route layout.
- `apps/web/features/chat/api/chat-api.ts` - chat history/detail/confirm/cancel/soft-delete API helpers and persisted-message conversion.
- `apps/web/features/chat/hooks/use-chat.ts` - AI SDK 6 chat transport with XSRF header record conversion.
- `apps/web/features/chat/hooks/use-chat-history.ts` - history/detail/soft-delete query hooks.
- `apps/web/features/chat/hooks/use-confirm-action.ts` - confirm/cancel mutations and cache invalidation.
- `apps/web/features/chat/components/chat-workspace.tsx` - route-level workspace composition and URL chat selection.
- `apps/web/features/chat/components/conversation-pane.tsx` - AI Elements conversation rendering, streaming input, tool envelope dispatch.
- `apps/web/features/chat/components/history-sidebar.tsx` - list/open/soft-delete sidebar; no rename or search controls.
- `apps/web/features/chat/components/preview-card/*` - preview card shell, state hook, VIP banner, outside-thread badge, and 9 body slots.
- `apps/web/components/ai/*` - vendored AI Elements primitives normalized to `components/ai`.
- `apps/web/components/ui/{accordion,button-group,collapsible,hover-card,spinner}.tsx` - supporting shadcn/Base UI primitives added by AI Elements.
- `apps/web/e2e/chat/*.spec.ts` - 8 chat Playwright specs plus shared route mock harness.
- `apps/web/__tests__/chat/tool-catalog-contract.test.ts` - 24-tool/9-body-slot frontend contract test.

## Decisions Made

- Did not invoke `frontend-design` because the user explicitly said it was not needed. Existing `07-UI-SPEC.md`, `07-PROTOTYPE.html`, and local app conventions were sufficient.
- Kept AI Elements vendored files ignored by ESLint/Prettier like `components/ui/**`, while patching the route-facing vendored components that blocked compile/runtime.
- Removed heavyweight runtime use of Streamdown syntax/diagram plugins and Shiki from chat route-facing vendored primitives. The route still uses `streamdown`, but avoids slow dev compilation from optional code/highlight paths.
- Used `onClick` for the local Base UI dropdown item in `HistorySidebar`; this repo's dropdown primitive is not Radix and does not use the Radix-style `onSelect` behavior expected by the plan text.
- Added a `PreviewCard` in-flight ref guard to prevent double-click confirmation at the frontend, complementing the backend race protection from Plan 05.

## Deviations from Plan

### Auto-fixed Issues

**1. [Tooling/API Drift] Package filter in plan did not match workspace package name**
- **Found during:** Task 6.2 dependency installation
- **Issue:** Plan command used `--filter @zero-mail/web`; actual package name is `web`.
- **Fix:** Used `pnpm --filter web add ai @ai-sdk/react streamdown`.
- **Verification:** `apps/web/package.json` and `pnpm-lock.yaml` updated; typecheck/lint/build pass.

**2. [Tooling/API Drift] AI Elements CLI behavior differed from plan**
- **Found during:** Task 6.3 primitive vendoring
- **Issue:** Current CLI writes to `components/ai-elements`, installs selected direct dependencies, and has no separate `response` or `loader` registry entries.
- **Fix:** Normalized copied sources to `components/ai`, added compatibility `response.tsx` and `loader.tsx`, and removed unused CLI-added dependencies.
- **Verification:** `pnpm --filter web typecheck`, `lint`, and `build` pass.

**3. [Performance] Route-facing vendored primitives pulled slow optional highlighting paths into `/chat`**
- **Found during:** Playwright navigation to `/chat`
- **Issue:** Next dev route compilation stalled while bundling Shiki/Streamdown optional plugin paths.
- **Fix:** Kept `streamdown` for assistant markdown but removed optional CJK/code/math/mermaid plugin wiring and replaced AI Elements `Tool` code rendering with a plain pre/code block.
- **Verification:** `/chat` compiles; `pnpm --filter web build` pass; 8 chat Playwright specs pass.

**4. [Correctness] Double-clicking Send produced two frontend confirm requests**
- **Found during:** `confirmation-race.spec.ts`
- **Issue:** React mutation pending state did not flip quickly enough to suppress a same-tick double-click.
- **Fix:** Added local in-flight refs in `PreviewCard` confirm/cancel handlers.
- **Verification:** `confirmation-race.spec.ts` pass and backend Plan 05 still owns server-side race protection.

**Total deviations:** 4 auto-fixed issues.
**Impact on plan:** All fixes were necessary to make the planned frontend surface compile, run, and preserve safety invariants. Product scope did not expand.

## Issues Encountered

- The first background server attempt used `Start-Process -FilePath pnpm`, which fails on Windows because `pnpm` is a command shim. Restarted with `cmd.exe /c pnpm --filter web dev` and kept logs outside the repo.
- Full chat Playwright at default 8 workers was unstable on first-run Next dev compilation. The accepted verification command uses `--workers=1`; individual specs also pass once the route is warm.

## User Setup Required

None for source changes. For local manual review, the web dev server is running at `http://localhost:3000/chat`.

## Next Phase Readiness

Phase 8 can build assistant settings and personalization UI on top of the chat route, hooks, tool catalog, and confirmation card patterns. Backend Phase 7 invariants remain owned by Plans 01-05; this plan verified the frontend slice and production Next build.

---
*Phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit*
*Completed: 2026-05-18*
