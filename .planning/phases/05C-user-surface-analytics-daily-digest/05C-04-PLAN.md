---
phase: 05C
plan: 04
type: execute
wave: 4
depends_on:
  - 05C-02
  - 05C-03
files_modified:
  - apps/web/lib/api/schema.d.ts
  - apps/web/scripts/check-i18n.ts
  - apps/web/app/(protected)/(app)/analytics/page.tsx
  - apps/web/app/(protected)/(app)/analytics/loading.tsx
  - apps/web/app/(protected)/(app)/settings/page.tsx
  - apps/web/features/analytics/api/analytics-api.ts
  - apps/web/features/analytics/query-keys.ts
  - apps/web/features/analytics/hooks/useAnalyticsSummary.ts
  - apps/web/features/analytics/messages.ts
  - apps/web/features/analytics/components/AnalyticsPageClient.tsx
  - apps/web/features/analytics/components/VolumePanel.tsx
  - apps/web/features/analytics/components/TimeSavedPanel.tsx
  - apps/web/features/analytics/components/TopSendersPanel.tsx
  - apps/web/features/analytics/components/RuleHitsPanel.tsx
  - apps/web/features/analytics/components/WindowChips.tsx
  - apps/web/features/analytics/components/AnalyticsSkeleton.tsx
  - apps/web/features/analytics/__tests__/AnalyticsPanels.test.tsx
  - apps/web/features/notifications/api/notifications-api.ts
  - apps/web/features/notifications/query-keys.ts
  - apps/web/features/notifications/hooks/useNotificationPreferences.ts
  - apps/web/features/notifications/hooks/useUpdateNotificationPreferences.ts
  - apps/web/features/notifications/messages.ts
  - apps/web/features/notifications/components/NotificationsSection.tsx
  - apps/web/features/notifications/__tests__/NotificationsSection.test.tsx
  - apps/web/components/shell/ProtectedSidebar.tsx
  - apps/web/e2e/analytics.spec.ts
  - apps/web/e2e/settings-notifications.spec.ts
autonomous: true
requirements:
  - ANL-01
  - WEB-02
threat_refs:
  - T-05C-13
  - T-05C-14
must_haves:
  truths:
    - "Visiting /analytics while authenticated renders volume + time-saved + top-3 senders + rule-hits panels for the default 7d window"
    - "Switching the window chip to 30d or 90d re-fetches via TanStack Query keyed by window and re-renders all 4 panels"
    - "Zero-data response renders explicit empty-state copy in every panel — no NaN, no infinite spinner"
    - "/settings shows a Notifications section with a Switch (digest_enabled) + Select (send hour 0-23) + read-only time-zone label"
    - "Toggling the Switch optimistically updates UI and persists via PATCH /api/me/notifications; error rolls back + toasts a retry"
    - "Sidebar gains an Analytics nav item that highlights when /analytics is active"
    - "All new user-facing prose has lock-step vi + en parity (pnpm i18n:check passes)"
    - "Playwright e2e covers window switch + opt-out + send-hour persistence at desktop AND 320px"
  artifacts:
    - path: "apps/web/app/(protected)/(app)/analytics/page.tsx"
      provides: "Authenticated /analytics route inside (protected)/(app) shell"
    - path: "apps/web/features/analytics/hooks/useAnalyticsSummary.ts"
      provides: "TanStack Query hook keyed by window"
    - path: "apps/web/features/notifications/components/NotificationsSection.tsx"
      provides: "Settings section with Switch + Select + time-zone label"
    - path: "apps/web/features/analytics/messages.ts"
      provides: "Per-feature i18n source for analytics namespace (vi + en)"
      contains: "analytics"
    - path: "apps/web/features/notifications/messages.ts"
      provides: "Per-feature i18n source for settings.notifications namespace (vi + en)"
  key_links:
    - from: "apps/web/features/analytics/hooks/useAnalyticsSummary.ts"
      to: "GET /api/analytics/summary?window=..."
      via: "openapi-fetch typed client"
      pattern: "openapi-fetch|/api/analytics/summary"
    - from: "apps/web/features/notifications/hooks/useUpdateNotificationPreferences.ts"
      to: "PATCH /api/me/notifications"
      via: "optimistic onMutate + onError rollback"
      pattern: "onMutate|onError"
---

<objective>
Ship the user surface (UI-SPEC §A + §B): authenticated `/analytics` route inside `(protected)/(app)` shell with 4 panels (volume, time-saved, top-3 senders, rule-hits) driven by a single TanStack Query hook keyed by URL search-param window chip (7d/30d/90d, default 7d, per D-21); `/settings` Notifications subsection with Switch (digest_enabled) + Select (send-hour 0–23) + read-only time-zone label, optimistic save with toast on error; sidebar nav item for Analytics; sources-of-truth in per-feature `messages.ts` for vi + en (Convention 10); `EN_SCAN_FILES` updated; Playwright e2e for both surfaces at desktop + 320px.

Purpose: closes ANL-01 + the frontend half of WEB-02 (Notifications subsection). After this plan ships, Phase 5C is feature-complete; only the deploy-runbook (Resend domain verification + RESEND_API_KEY in prod) remains as a manual `user_setup` activity carried over from Plan 03.

Output: 2 new app routes (analytics page + loading), 2 modifications to existing routes (settings page + protected sidebar), 2 feature folders (`features/analytics/` and `features/notifications/`) each with `api/`, `query-keys.ts`, `hooks/`, `components/`, `messages.ts`, `__tests__/`, 2 Playwright e2e specs, 1 OpenAPI typed-client regeneration (`schema.d.ts` update via `pnpm generate:api`), 1 `EN_SCAN_FILES` update.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-SPEC.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-UI-SPEC.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-PROTOTYPE.html
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-VALIDATION.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-02-PLAN.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-03-PLAN.md
@CLAUDE.md
@CONVENTIONS.md

<!-- Templates the executor MUST read before editing -->
@apps/web/features/triage/query-keys.ts
@apps/web/features/triage/messages.ts
@apps/web/features/triage/api/triage-api.ts
@apps/web/features/triage/hooks
@apps/web/features/triage/components
@apps/web/scripts/check-i18n.ts
@apps/web/app/(protected)/(app)/layout.tsx
@apps/web/app/(protected)/(app)/triage/page.tsx
@apps/web/app/(protected)/(app)/settings/page.tsx
@apps/web/components/shell/ProtectedSidebar.tsx
@apps/web/lib/api/schema.d.ts
@apps/web/package.json
@apps/web/vitest.config.ts
@apps/web/playwright.config.ts
</context>

<interfaces>
<!-- Critical contracts the executor needs without re-exploring -->

**MANDATORY: invoke the `frontend-design` skill BEFORE writing any UI code** (project memory rule + UI-SPEC frontmatter + user's `Developer Profile: Frontend uses Anthropic frontend-design skill`). Pass this rule forward to any executor subagent that touches UI.

**Convention 10 (i18n source-of-truth):** per-feature `messages.ts` is the source — DO NOT edit `apps/web/i18n/messages/vi.json` or `en.json` directly (those are generated by `pnpm i18n:build` from `messages.ts`). After editing `messages.ts`, run `pnpm i18n:build` then `pnpm i18n:check` to verify parity.

**`check-i18n.ts` EN_SCAN_FILES (hand-maintained allowlist):** add every new file in `apps/web/app/(protected)/(app)/analytics/`, every `apps/web/features/analytics/components/*.tsx`, every `apps/web/features/notifications/components/*.tsx` to the `EN_SCAN_FILES` constant in `apps/web/scripts/check-i18n.ts`. Convention 10's parity gate depends on it.

**OpenAPI typed client regeneration:** after Plan 02 + Plan 03 ship the new endpoints, `pnpm --filter apps/web generate:api` regenerates `apps/web/lib/api/schema.d.ts`. Backend must be running for the script's default mode, OR — per the Phase 02A P04 STATE.md decision — the script defaults to `openapi/openapi.json` (a Gradle-emitted local artifact); confirm which mode the current `generate-api.ts` uses and run accordingly. Once regenerated, the new endpoints have typed paths under `paths["/api/analytics/summary"]` and `paths["/api/me/notifications"]`.

**TanStack Query key contract (Phase 5A precedent):** per-feature key factories in `query-keys.ts`. NO barrel files (Convention 8). Deep imports only.

Analytics query key:
```
export const analyticsKeys = {
  all: ['analytics'] as const,
  summary: (window: '7d' | '30d' | '90d') => [...analyticsKeys.all, 'summary', window] as const,
};
```

Notifications query key (mutation-only — but list/read endpoint exists too):
```
export const notificationsKeys = {
  all: ['notifications'] as const,
  preferences: () => [...notificationsKeys.all, 'preferences'] as const,
};
```

**Window chip URL-param contract (D-21 + UI-SPEC §A):** `Tabs.value` binds to `useSearchParams().get('window') ?? '7d'`; on change, `router.replace('?window=...', { scroll: false })`. The window param is the source of truth — `/analytics?window=30d` is shareable; back/forward navigation works.

**Optimistic mutation recipe (Phase 5A pause-toggle precedent — D-13 from 5A):** `onMutate` → `setQueryData`; `onError` → rollback; `onSettled` → `invalidateQueries`. Reuse for both `Switch` toggle and `Select` change.

**shadcn primitives (already installed in Phase 5A, ZERO new installs):** `tabs`, `card`, `skeleton`, `table`, `switch`, `select`, `separator`, `badge`, `button`, `sonner` (toast), `tooltip`. UI-SPEC locks the design contract — do NOT wrap primitives unless the rule-of-three triggers (memory rule "Use raw shadcn primitives first").

**Authenticated screens stay on `.zm-proto`/`.zm-auth`-free base teal contract.** UI-SPEC explicitly bans those classes on `/analytics` and `/settings/notifications`.

**Responsive contract (UI-SPEC §"Responsive Contract"):** 4-panel grid stacks to single column below `md`; rule-hits switches from `Table` to card-list renderer below `md`; touch targets 44px at 320px; window chips may wrap or scroll horizontally below `md`.

**Accessibility (UI-SPEC §"Accessibility Contract"):** `Tabs` role tablist + arrow keys + aria-selected; panel `<h3>`s; mono `Xh YYm` accompanied by sr-only full form; `aria-live="polite"` loading announcer; `aria-describedby` helper text on `Switch` and `Select`; disabled state on `Select` when digest is OFF stays mounted (preserves cognitive map, signals reversibility).

Vietnamese-first copy from UI-SPEC §"Copywriting Contract" — vi authored alongside en lock-step. Source-of-truth in `features/analytics/messages.ts` and `features/notifications/messages.ts`.
</interfaces>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Regenerate OpenAPI typed client + features/analytics scaffolding (api + query-keys + hook + i18n + EN_SCAN_FILES) + analytics page + panels + window chips + Vitest Wave 0</name>
  <files>
    apps/web/lib/api/schema.d.ts,
    apps/web/scripts/check-i18n.ts,
    apps/web/features/analytics/api/analytics-api.ts,
    apps/web/features/analytics/query-keys.ts,
    apps/web/features/analytics/hooks/useAnalyticsSummary.ts,
    apps/web/features/analytics/messages.ts,
    apps/web/features/analytics/components/AnalyticsPageClient.tsx,
    apps/web/features/analytics/components/VolumePanel.tsx,
    apps/web/features/analytics/components/TimeSavedPanel.tsx,
    apps/web/features/analytics/components/TopSendersPanel.tsx,
    apps/web/features/analytics/components/RuleHitsPanel.tsx,
    apps/web/features/analytics/components/WindowChips.tsx,
    apps/web/features/analytics/components/AnalyticsSkeleton.tsx,
    apps/web/features/analytics/__tests__/AnalyticsPanels.test.tsx,
    apps/web/app/(protected)/(app)/analytics/page.tsx,
    apps/web/app/(protected)/(app)/analytics/loading.tsx,
    apps/web/components/shell/ProtectedSidebar.tsx
  </files>
  <read_first>
    apps/web/features/triage/query-keys.ts,
    apps/web/features/triage/messages.ts,
    apps/web/features/triage/api/triage-api.ts,
    apps/web/features/triage/components/AuditTable.tsx,
    apps/web/app/(protected)/(app)/triage/page.tsx,
    apps/web/scripts/check-i18n.ts,
    apps/web/components/shell/ProtectedSidebar.tsx,
    apps/web/lib/api/schema.d.ts,
    apps/web/package.json,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-UI-SPEC.md (§"Surfaces in scope" §A, §Spacing, §Typography, §Color, §"Component Inventory" A, §"Copywriting Contract" /analytics, §"Accessibility Contract", §"Responsive Contract", §"Notes for the Planner"),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-PROTOTYPE.html (visual ground truth)
  </read_first>
  <behavior>
    - **FIRST:** invoke the `frontend-design` skill (memory rule + UI-SPEC frontmatter) — pass UI-SPEC §A summary in the invocation
    - Run `pnpm --filter apps/web generate:api` to regenerate `apps/web/lib/api/schema.d.ts` from the backend's OpenAPI; verify `schema.d.ts` now exposes `paths["/api/analytics/summary"]` and the typed `AnalyticsSummaryResponse` shape
    - `analytics-api.ts` exports `fetchAnalyticsSummary(window: '7d' | '30d' | '90d'): Promise<AnalyticsSummaryResponse>` using the `openapi-fetch` client at `apps/web/lib/api/client.ts` (verify the existing client name from triage-api.ts)
    - `query-keys.ts`: `analyticsKeys` factory as documented in `<interfaces>`
    - `useAnalyticsSummary(window)` hook: `useQuery({ queryKey: analyticsKeys.summary(window), queryFn: () => fetchAnalyticsSummary(window), staleTime: 60_000, refetchOnWindowFocus: false })`
    - `messages.ts`: every key from UI-SPEC §"Copywriting Contract" /analytics page table in both `en` and `vi`; export shape matches the existing `features/triage/messages.ts` precedent
    - `AnalyticsPageClient` is the client component owning the URL-search-param window chip state (`'use client'`, reads `useSearchParams().get('window') ?? '7d'`, `useRouter().replace('?window=...', { scroll: false })` on change); renders the 4 panels in a Tailwind grid (`grid grid-cols-1 md:grid-cols-2 gap-6`) above the rule-hits panel which is full-width
    - `WindowChips` renders shadcn `Tabs` with 3 triggers `7d/30d/90d`; `Tabs.value` binds to the URL param
    - Each panel component (`VolumePanel`, `TimeSavedPanel`, `TopSendersPanel`, `RuleHitsPanel`) takes typed props from `AnalyticsSummaryResponse` and renders per UI-SPEC §"Component Inventory" A — Volume + Time Saved show Display-size headline figure (28px, 600), Top Senders shows up to 3 rows with rank + sender_email + count badge, Rule Hits uses `Table` at `≥md` and card-list below
    - Empty states: `0` (not `—`, not NaN) for headline figures; explicit copy from `analytics.{volume,topSenders,ruleHits}.empty` i18n keys; spinner never flashes after data lands
    - `RuleHitsPanel` `Table` at `≥md` uses dense `py-2` rows + columns `Rule, Decisions, Applied, Reverted` with mono `--font-mono` for numbers; below `md` renders a card-list (one card per rule, all 4 metrics vertical)
    - `AnalyticsSkeleton` renders skeleton rectangles MATCHING the final layout (UI-SPEC §"Loading state" A) — not a generic spinner
    - `analytics/page.tsx` is an RSC page that renders `<AnalyticsPageClient />` (passes no server data; the client hook fetches via the typed openapi-fetch client). Page title `<h1>` reads from server-rendered translations (`getTranslations` from `next-intl/server`)
    - `analytics/loading.tsx` renders `<AnalyticsSkeleton />` for the route-level Suspense boundary
    - `ProtectedSidebar` gains a new nav item `Analytics` linking to `/analytics`, with `isActive` highlight when the current path matches; sidebar order: keep existing order, add Analytics between `Triage` and `Rules` (or wherever UI-SPEC implies — read the sidebar file to determine the right insertion)
    - `EN_SCAN_FILES` in `check-i18n.ts` gains entries for `app/(protected)/(app)/analytics/page.tsx`, `app/(protected)/(app)/analytics/loading.tsx`, and every `features/analytics/components/*.tsx`
    - `AnalyticsPanels.test.tsx` (Vitest + jsdom): mounts each panel with (a) zero-data fixture — assert empty-state copy renders, no NaN; (b) seeded fixture — assert Volume figure equals `1247`, Time Saved equals `4h 12m` (formatted), Top Senders shows 3 rows with ranks 1/2/3, Rule Hits table shows N rows with applied/reverted counts; (c) window-switch — re-render with different prop, panels re-render with new values
  </behavior>
  <action>**Before writing any UI code, invoke the `frontend-design` skill.** Pass UI-SPEC §A summary + UI-SPEC §"Component Inventory" A and let the skill decide micro-layout (icon choice for the tooltips, exact skeleton shapes, optional rank-1 accent stripe on Top Senders). Read `features/triage/` carefully — it is the canonical "feature folder under apps/web/features/" precedent (api + query-keys + hooks + components + messages.ts pattern). Reuse the `openapi-fetch` client identifier from `features/triage/api/triage-api.ts` (do not invent a new client). Per memory rule "Use raw shadcn primitives first" — `Card`, `Tabs`, `Switch`, `Select`, `Table`, `Skeleton`, `Badge` are used DIRECTLY, NOT wrapped (rule-of-three has not triggered for any of these compositions; UI-SPEC compositions are first-time uses). All headline `Card`s use the shadcn `Card` + `CardHeader` + `CardContent` primitives unchanged. The `Tabs` value MUST drive the URL via `router.replace` with `{ scroll: false }` — UI-SPEC explicitly bans full page reload on window change. Empty-state numbers MUST be `0` and `0m` — never `—` or `NaN` (UI-SPEC §"Notes for the Planner"). For test mounting (`AnalyticsPanels.test.tsx`), use vitest + jsdom; if `next-intl` proves hostile to test rendering (precedent: Phase 01.5 Plan 02 loose translator cast), use the same `as unknown as` translator cast pattern. After UI edits, run `pnpm --filter apps/web tsc --noEmit && pnpm --filter apps/web lint && pnpm --filter apps/web i18n:check && pnpm --filter apps/web test features/analytics`. Verify visually via Playwright MCP after dev server is up (`mcp__playwright__browser_navigate http://localhost:3000/analytics` + `browser_snapshot` for the 4 panels at default 7d). Implements ANL-01 + the analytics half of D-21.</action>
  <verify>
    <automated>pnpm --filter apps/web tsc --noEmit && pnpm --filter apps/web test features/analytics --run && pnpm --filter apps/web i18n:check</automated>
  </verify>
  <done>
    `/analytics` route exists; visiting it (Playwright MCP) renders 4 panels + window chips + `<h1>Analytics</h1>`. Window chip switches re-fetch via TanStack Query (verified via Playwright MCP `browser_network_requests` showing a new GET on `?window=30d` click). Empty-state copy renders cleanly with zero-data fixture. `tsc --noEmit` 0 errors. `lint` 0 errors. `i18n:check` passes (vi/en parity). `AnalyticsPanels.test.tsx` Vitest run green. `EN_SCAN_FILES` updated for every new file.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: features/notifications scaffolding + /settings Notifications subsection + optimistic save + Vitest + Playwright e2e for both surfaces (analytics + settings notifications)</name>
  <files>
    apps/web/features/notifications/api/notifications-api.ts,
    apps/web/features/notifications/query-keys.ts,
    apps/web/features/notifications/hooks/useNotificationPreferences.ts,
    apps/web/features/notifications/hooks/useUpdateNotificationPreferences.ts,
    apps/web/features/notifications/messages.ts,
    apps/web/features/notifications/components/NotificationsSection.tsx,
    apps/web/features/notifications/__tests__/NotificationsSection.test.tsx,
    apps/web/app/(protected)/(app)/settings/page.tsx,
    apps/web/scripts/check-i18n.ts,
    apps/web/e2e/analytics.spec.ts,
    apps/web/e2e/settings-notifications.spec.ts
  </files>
  <read_first>
    apps/web/features/triage/hooks (existing optimistic-mutation precedent — Phase 5A pause-toggle recipe),
    apps/web/features/notifications/api/notifications-api.ts (will be created in this task),
    apps/web/app/(protected)/(app)/settings/page.tsx,
    apps/web/e2e/triage.spec.ts (Playwright auth-helper precedent),
    apps/web/playwright.config.ts,
    apps/web/scripts/check-i18n.ts,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-UI-SPEC.md (§"Component Inventory" B, §"Copywriting Contract" /settings notifications, §"Notes for the Planner" send-hour Select + optimistic save),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-VALIDATION.md (§"Wave 0 Requirements" frontend section, §"Manual-Only Verifications")
  </read_first>
  <behavior>
    - **FIRST:** invoke the `frontend-design` skill (memory rule) — pass UI-SPEC §B summary
    - `notifications-api.ts`: `fetchNotificationPreferences(): Promise<NotificationPreferencesResponse>` + `updateNotificationPreferences(body: NotificationPreferencesUpdateRequest): Promise<NotificationPreferencesResponse>` both via openapi-fetch typed client
    - `query-keys.ts`: `notificationsKeys.preferences()` returns `['notifications', 'preferences']`
    - `useNotificationPreferences()`: `useQuery({ queryKey: notificationsKeys.preferences(), queryFn: fetchNotificationPreferences, staleTime: 5 * 60_000 })`
    - `useUpdateNotificationPreferences()`: `useMutation` with `onMutate` (snapshot + setQueryData optimistic), `onError` (rollback + sonner toast `settings.notifications.toast.errorTitle` with retry action), `onSettled` (invalidateQueries on notificationsKeys.preferences) — recipe mirrors Phase 5A pause-toggle exactly
    - `messages.ts`: every key from UI-SPEC §"Copywriting Contract" /settings table — `settings.notifications.title`, `description`, `toggle.{label,helperOn,helperOff}`, `sendHour.{label,helper}`, `timeZone.{label,tooltip}`, `toast.{savedTitle,errorTitle,retry}` — both vi + en
    - `NotificationsSection` `'use client'`: renders the section heading + description + Switch (digest_enabled) + Select (0–23 send hour, displayed as `00:00`..`23:00`) + read-only time-zone label with tooltip icon; Switch and Select both fire the mutation `onChange`; Select stays mounted but `disabled` when Switch is OFF (UI-SPEC §"Component Inventory" B); sonner toast on success and error; aria-describedby helper text on Switch + Select
    - `settings/page.tsx`: existing settings page gains `<NotificationsSection />` block; verify no other settings block collides
    - `EN_SCAN_FILES` in `check-i18n.ts` gains `features/notifications/components/NotificationsSection.tsx`
    - `NotificationsSection.test.tsx` (Vitest + jsdom): (a) optimistic toggle — click Switch, UI updates BEFORE mutation resolves, on error UI rolls back, toast renders with retry action; (b) Select change — change to `08:00`, mutation fires with `digestSendHourLocal=8`, optimistic state shows immediately; (c) Switch OFF — Select becomes disabled but stays mounted (UI-SPEC explicit requirement)
    - `e2e/analytics.spec.ts` (Playwright): auth helper from existing e2e setup; visit `/analytics`; assert 4 panels present + window chips visible; click `30d` chip; assert URL is `/analytics?window=30d` and panels re-render; resize viewport to 320px; assert no horizontal scroll + window chips wrap or scroll-x; verify vi locale rendering
    - `e2e/settings-notifications.spec.ts` (Playwright): visit `/settings`; assert Notifications section visible; click Switch → assert toast appears + state persists across reload; change Select to `08:00` → assert persist; verify Switch OFF disables but does not unmount Select; verify vi + en
    - Both e2e specs run at desktop AND 320px (per SPEC acceptance + 5A precedent)
  </behavior>
  <action>**Invoke `frontend-design` skill before writing UI code.** Mirror `features/triage/` for the feature folder shape. The optimistic-mutation recipe is documented in STATE.md `[Phase ?]: 02A-04 Use a plain accessible toggle button because apps/web has no shadcn Switch primitive installed` — verify whether the `Switch` shadcn primitive is now installed (UI-SPEC says yes, "all already installed from 5A"); if absent, install via `pnpm dlx shadcn@latest add switch` BEFORE writing the section. The `Select` displays 24 string options `00:00`..`23:00`; the underlying value is the int `0..23` passed to the mutation. Per memory rule "Use raw shadcn primitives first" — `Switch`, `Select`, `Label`, `Card` are used directly. Disabled state when Switch is OFF is the EXACT UI-SPEC requirement: the Select stays mounted (not unmounted) per "preserves cognitive map" — implement via `disabled={!preferences?.digestEnabled}` on the trigger. The optimistic recipe is from Phase 5A; read an existing optimistic hook in `features/triage/hooks/` (likely `useTogglePause` or similar) for the exact `onMutate`/`onError`/`onSettled` shape and copy it verbatim with notifications-specific keys. After UI edits, run the full frontend gate. Run Playwright e2e with `pnpm --filter apps/web playwright test e2e/analytics.spec.ts e2e/settings-notifications.spec.ts` — if env-blocked (Phase 5A precedent: stale `next dev` on port 3000), commit the specs as durable gates and document the env-block in `05C-04-SUMMARY.md`. Implements WEB-02 (frontend notifications portion).</action>
  <verify>
    <automated>pnpm --filter apps/web tsc --noEmit && pnpm --filter apps/web test features/notifications --run && pnpm --filter apps/web lint && pnpm --filter apps/web i18n:check</automated>
  </verify>
  <done>
    `/settings` Notifications subsection renders + persists changes optimistically with toast on error. Vitest `NotificationsSection.test.tsx` green covering the 3 cases. `tsc --noEmit`, `lint`, `i18n:check` all pass. Playwright e2e specs committed; if dev-server is up they run green (verify locally via `mcp__playwright__browser_navigate http://localhost:3000/settings` and inspect the Notifications section). `EN_SCAN_FILES` updated. All UI uses raw shadcn primitives (no new wrapper components added).
  </done>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → `/api/analytics/summary` | Window param crosses here — server-side already validates via `AnalyticsWindow.fromId` (Plan 02 enforces); frontend MUST NOT pass arbitrary strings — only `'7d'` `'30d'` `'90d'` |
| browser → `/api/me/notifications` | Untrusted preference body crosses here — Plan 03 backend already validates via `@Min(0) @Max(23)`; frontend's optimistic state assumes valid input only |
| client component → openapi-fetch typed client → Next.js proxy → Spring API | Session cookie + tenant scoping inherits the existing Phase 5A `(protected)` chrome; no new session/auth surface |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05C-13 | Tampering (client-side window manipulation) | Window URL param `?window=` | mitigate | Frontend `WindowChips` is a closed-enum `Tabs` with 3 triggers; if user manually edits URL to `?window=bogus`, the client hook's typed call sends `'bogus'` to the server which returns 400 — handled by route-level error.tsx (Phase 5A precedent). Frontend MUST NOT crash on 400 — display the inherited error UI |
| T-05C-14 | Information disclosure (auth boundary) | `/analytics` + `/settings` both under `(protected)/(app)` | accept | Inherits Phase 5A authentication — no new auth surface; `(protected)/layout.tsx` already enforces session redirect to `/login` for unauthenticated visitors |

</threat_model>

<verification>
- `pnpm --filter apps/web tsc --noEmit` exits 0
- `pnpm --filter apps/web lint` exits 0
- `pnpm --filter apps/web i18n:check` passes (strict mode — vi/en parity)
- `pnpm --filter apps/web test features/analytics --run` and `pnpm --filter apps/web test features/notifications --run` both green
- `pnpm --filter apps/web playwright test e2e/analytics.spec.ts e2e/settings-notifications.spec.ts` green when dev-server is up (or committed as durable gates if env-blocked, per Phase 5A precedent)
- Manual via Playwright MCP: `mcp__playwright__browser_navigate http://localhost:3000/analytics` shows 4 panels at default 7d; clicking `30d` chip updates URL + re-fetches (verified via `browser_network_requests`); `mcp__playwright__browser_navigate http://localhost:3000/settings` shows Notifications section; toggling Switch triggers PATCH (verified via `browser_network_requests`)
- Resize to 320px via `browser_resize` and verify no horizontal scroll + window chips usable
</verification>

<success_criteria>
- `/analytics` renders 4 panels with default 7d window, switches to 30d / 90d via chips, URL search param drives state, back/forward navigation works
- Zero-data fixture renders explicit `0` / `0m` / empty list copy — NO NaN, NO infinite spinner
- `/settings → Notifications` Switch + Select + read-only TZ label render per UI-SPEC; optimistic save with toast on success, rollback + toast with retry on error
- Sidebar Analytics nav item highlights when on /analytics
- vi + en parity (i18n:check strict gate passes)
- `EN_SCAN_FILES` updated to scan every new component for English-prose leakage
- Vitest + Playwright cover the Wave 0 frontend tests from VALIDATION.md
- 320px responsive (Playwright + manual MCP verification)
- Standard frontend gates (`tsc`, ESLint, Vitest, `i18n:check`) all green
- `frontend-design` skill invoked before any UI code was written (documented in summary)
</success_criteria>

<output>
After completion, create `.planning/phases/05C-user-surface-analytics-daily-digest/05C-04-SUMMARY.md` capturing:
- Whether `pnpm generate:api` ran against a live backend or a Gradle-emitted artifact
- The exact sidebar insertion point for the Analytics nav item (between which two existing items)
- Whether the `Switch` primitive was already installed or required `pnpm dlx shadcn@latest add switch`
- Any deviations from UI-SPEC §"Component Inventory" — particularly the rule-hits below-md card-list renderer choice + the optional rank-1 accent stripe choice
- Playwright e2e status: green vs env-blocked (with port-state evidence if blocked)
- `frontend-design` skill invocation evidence (was it invoked before UI code? was the prototype HTML used as visual ground truth?)
- Phase 5C closure note: what manual deploy-runbook tasks remain (Resend domain verification, `RESEND_API_KEY` env-var setup in prod)
</output>
