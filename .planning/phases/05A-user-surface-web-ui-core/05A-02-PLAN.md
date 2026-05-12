---
phase: 05A-user-surface-web-ui-core
plan: 02
type: execute
wave: 2
depends_on: [01]
files_modified:
  - apps/web/app/(protected)/layout.tsx
  - apps/web/app/(protected)/onboarding/layout.tsx
  - apps/web/components/shell/AppShell.tsx
  - apps/web/components/shell/AppSidebar.tsx
  - apps/web/components/shell/ChromeHeader.tsx
  - apps/web/features/triage/hooks/useTriagePauseState.ts
  - apps/web/features/triage/hooks/useToggleTriagePause.ts
  - apps/web/features/triage/hooks/useToggleTriagePause.test.tsx
  - apps/web/features/triage/components/PauseBanner.tsx
  - apps/web/features/triage/messages.ts
  - apps/web/features/shell/messages.ts
  - apps/web/i18n/messages/vi.json
  - apps/web/i18n/messages/en.json
  - apps/web/scripts/check-i18n.ts
  - apps/web/e2e/app-shell.spec.ts
  - apps/web/e2e/pause-toggle.spec.ts
  - apps/web/e2e/connection-health.spec.ts
  - apps/web/e2e/billing-balance.spec.ts
autonomous: true
requirements: [WEB-01, WEB-04]
user_setup: []

must_haves:
  truths:
    - "Every app/(protected)/** page except onboarding/* renders inside a single persistent app shell (collapsible icon sidebar + 56px top header)"
    - "The top-header chrome shows the pause toggle, credit-balance pill, and Gmail connection-health indicator without scrolling at desktop and 320px"
    - "Toggling pause from the chrome persists via /tenant/triage-pause, updates optimistically, rolls back on error, and is reconciled by invalidating triageKeys.pauseState()"
    - "The chrome pause toggle, the /settings pause toggle, and PauseBanner all read the single triageKeys.pauseState() cache entry — no local useState, no ad-hoc query keys"
    - "A DISCONNECTED Gmail status surfaces a reconnect affordance reusing ReconnectPrompt semantics"
    - "onboarding/* keeps a minimal chrome-suppressed layout and does not render inside the sidebar shell"
  artifacts:
    - path: "apps/web/app/(protected)/layout.tsx"
      provides: "Shell host: cache()'d /me, sidebar_state cookie, Promise.all chrome prefetch, dehydrate + HydrationBoundary wrapping <AppShell>"
      contains: "HydrationBoundary"
    - path: "apps/web/components/shell/AppShell.tsx"
      provides: "Client shell: SidebarProvider + AppSidebar + SidebarInset + ChromeHeader + main + Toaster"
    - path: "apps/web/components/shell/ChromeHeader.tsx"
      provides: "56px header with BalancePill, HealthDot, PauseSwitch (+ confirm dialog on pause-OFF), UserMenu"
    - path: "apps/web/features/triage/hooks/useTriagePauseState.ts"
      provides: "Single read hook for pause state keyed on triageKeys.pauseState()"
    - path: "apps/web/features/triage/hooks/useToggleTriagePause.ts"
      provides: "Optimistic pause mutation (onMutate/onError/onSettled) keyed on triageKeys.pauseState()"
      contains: "triageKeys.pauseState"
  key_links:
    - from: "apps/web/components/shell/ChromeHeader.tsx"
      to: "triageKeys.pauseState()"
      via: "useTriagePauseState + useToggleTriagePause"
      pattern: "useTriagePauseState"
    - from: "apps/web/components/shell/ChromeHeader.tsx"
      to: "/api/billing/balance"
      via: "useBillingBalance"
      pattern: "useBillingBalance"
    - from: "apps/web/app/(protected)/layout.tsx"
      to: "triageKeys.pauseState() / billingKeys.balance() / gmailQueryKeys.status()"
      via: "qc.prefetchQuery x3 + dehydrate"
      pattern: "prefetchQuery"
---

<objective>
Replace the thin `app/(protected)/layout.tsx` with the persistent authenticated app shell: a collapsible icon-rail sidebar (shadcn `sidebar` block) + a 56px top header that owns the chrome region (pause toggle, credit-balance pill, Gmail connection-health indicator, user menu). Prefetch the three chrome queries in the layout and hydrate them into a client `<AppShell>`. Refactor the pause toggle onto a single source of truth (D-13): one query key, one read hook, one optimistic write hook — consumed by the chrome toggle, the `/settings` toggle, and `PauseBanner`. Keep `onboarding/*` chrome-suppressed (D-05).

Purpose: WEB-04 — the trust UI (pause / balance / health) must be on every authenticated screen, flicker-free, and physically incapable of state drift.
Output: rewritten `(protected)/layout.tsx`, new `components/shell/{AppShell,AppSidebar,ChromeHeader}.tsx`, new `onboarding/layout.tsx`, new `useTriagePauseState`, rewritten `useToggleTriagePause` + its test, rebased `PauseBanner`, implemented `app-shell` / `pause-toggle` / `connection-health` / `billing-balance` Playwright specs (chrome portions).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/05A-user-surface-web-ui-core/05A-SPEC.md
@.planning/phases/05A-user-surface-web-ui-core/05A-CONTEXT.md
@.planning/phases/05A-user-surface-web-ui-core/05A-PATTERNS.md
@.planning/phases/05A-user-surface-web-ui-core/05A-UI-SPEC.md
@.planning/phases/05A-user-surface-web-ui-core/05A-VALIDATION.md
@.planning/phases/05A-user-surface-web-ui-core/05A-01-SUMMARY.md
@CLAUDE.md
@CONVENTIONS.md
@apps/web/AGENTS.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Rewrite (protected)/layout.tsx as the shell host + add onboarding chrome-suppressed layout</name>
  <read_first>
    - apps/web/app/(protected)/layout.tsx (the current thin version being replaced — keep the NextIntlClientProvider + QueryProvider nesting)
    - apps/web/features/account/api/account-api.ts (`getCurrentUserCached` — the `cache()` keyed-by-cookie-string idiom; RSC callers pass `(await cookies()).toString()`)
    - apps/web/app/(public)/layout.tsx, apps/web/app/(auth)/layout.tsx (minimal-layout idiom for the new onboarding layout)
    - apps/web/components/ui/sidebar.tsx (the generated primitive — read `SIDEBAR_COOKIE_NAME`, recorded in 05A-01-SUMMARY)
    - apps/web/features/triage/query-keys.ts, apps/web/features/billing/query-keys.ts, apps/web/features/billing/hooks/useBillingBalance.ts, apps/web/features/gmail/query-keys.ts, apps/web/features/gmail/api/gmail-api.ts
    - apps/web/lib/query-client.tsx (global staleTime/gcTime), node_modules/@tanstack/react-query (HydrationBoundary/dehydrate/QueryClient API)
    - 05A-CONTEXT.md D-01, D-05, D-10; 05A-RESEARCH.md Pattern 1, Pattern 2, Pitfall 3 (hydration mismatch), Pitfall 4 (#8479), Pitfall 8 (onboarding inside shell)
    - 05A-PATTERNS.md section "app/(protected)/layout.tsx (route layout — rewrite to shell host)" and section "app/(protected)/onboarding/layout.tsx"
    - node_modules/next/dist/docs/ — async `cookies()` in Next 16, route-group layout nesting (read before writing any Next code per apps/web/AGENTS.md)
  </read_first>
  <action>
    Rewrite `app/(protected)/layout.tsx` as a Server Component: keep `NextIntlClientProvider` (from `getLocale`/`getMessages`) + `QueryProvider`; add `await cookies()` → read the sidebar cookie (use the exported `SIDEBAR_COOKIE_NAME`; default open when value !== 'false'); detect the active route segment — if it is `onboarding`, render a bare minimal wrapper (no `<AppShell>`, per D-05); otherwise create `const queryClient = new QueryClient()`, `await Promise.all([queryClient.prefetchQuery({ queryKey: triageKeys.pauseState(), queryFn: () => /* paused boolean from /me via getCurrentUserCached */ }), queryClient.prefetchQuery({ queryKey: billingKeys.balance(), queryFn: getBillingBalance }), queryClient.prefetchQuery({ queryKey: gmailQueryKeys.status(), queryFn: getTenantStatus })])`, then `<HydrationBoundary state={dehydrate(queryClient)}><AppShell defaultSidebarOpen={...}>{children}</AppShell></HydrationBoundary>`. Note D-10/#8479: these prefetched queries are consumed only inside `<AppShell>` (the header), never relied on by a deeper page boundary. Move the existing `PauseBanner` mount out of the layout root and into the shell subtree.
    Create `app/(protected)/onboarding/layout.tsx` — a minimal nested layout mirroring `app/(auth)/layout.tsx`: a focused-funnel wrapper (centered, no sidebar, no chrome), passing children through; it inherits the parent's intl + query providers. Verify manually that the sidebar is absent on `/onboarding/*`. Document the chosen suppression mechanism (parent segment-branch vs. nested override) in the SUMMARY.
    Do not write UI styling here beyond layout wiring; the visual `frontend-design` review for the shell happens in Task 2.
  </action>
  <verify>
    <automated>cd apps/web && pnpm typecheck && pnpm lint</automated>
  </verify>
  <acceptance_criteria>
    - `app/(protected)/layout.tsx` is a Server Component that `await cookies()`, reads the sidebar cookie via `SIDEBAR_COOKIE_NAME`, branches on the `onboarding` segment, and (for non-onboarding) creates a `QueryClient`, calls `prefetchQuery` three times inside a `Promise.all`, `dehydrate`s, and wraps `<AppShell>` in `<HydrationBoundary>`.
    - `app/(protected)/onboarding/layout.tsx` exists and renders a chrome-suppressed minimal wrapper (no `Sidebar`/`AppShell` import).
    - `PauseBanner` is no longer mounted at the layout root; it is mounted inside the shell subtree.
    - `cd apps/web && pnpm typecheck && pnpm lint` exit 0.
    - SUMMARY documents the onboarding-suppression mechanism.
  </acceptance_criteria>
  <done>Layout is the shell host with prefetched chrome data; onboarding stays chrome-suppressed; typecheck + lint green.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Build AppShell / AppSidebar / ChromeHeader + refactor pause to single source of truth (D-13)</name>
  <behavior>
    - useToggleTriagePause: onMutate cancels queries on triageKeys.pauseState(), snapshots the previous boolean, setQueryData to the next value; onError restores the snapshot; onSettled invalidates triageKeys.pauseState() AND billingKeys.balance(). (test: useToggleTriagePause.test.tsx rewritten — assert classic useQueryClient() form, the right key, optimistic set, rollback, and both invalidations)
    - useTriagePauseState: useQuery keyed on triageKeys.pauseState(), no refetchInterval (invalidate-only), queryFn derives the paused boolean from /me.
    - PauseBanner: returns null unless useTriagePauseState() data is true; renders the warning Alert; resume button calls useToggleTriagePause().mutate(false). No useCurrentUser dependency for the paused flag.
    - ChromeHeader: renders the balance pill (from useBillingBalance), the health dot (from useTenantStatus — green CONNECTED, amber action-needed, red DISCONNECTED + ReconnectPrompt affordance), the pause Switch (off when paused, amber surface; turning OFF i.e. pausing opens an AlertDialog confirm; turning ON does not confirm), and a dropdown-menu UserMenu (language, settings link, sign out). All visible strings via next-intl nav.*/shell.* keys.
  </behavior>
  <read_first>
    - apps/web/app/(protected)/settings/page.tsx (already composes useCurrentUser + useTenantStatus + useDisconnectGmail + useToggleTriagePause + ConnectionHealthBadge + ReconnectPromptGate — the exact chrome hook set; also the hand-rolled pause switch markup to REPLACE with shadcn `switch`)
    - apps/web/features/rules/hooks/use-rules.ts -> `useReorderRules` (the only existing full optimistic mutation: cancelQueries -> snapshot -> setQueryData -> onError restore -> onSettled invalidate; classic `useQueryClient()` + 3-arg-callback form)
    - apps/web/features/triage/hooks/useToggleTriagePause.ts + useToggleTriagePause.test.tsx (current `onSuccess -> invalidate accountQueryKeys.me()` body to replace; the Vitest harness)
    - apps/web/features/triage/components/PauseBanner.tsx (current `useCurrentUser().triagePaused` read to rebase onto `useTriagePauseState()`)
    - apps/web/features/triage/api/triage-api.ts (`setTriagePaused` -> PUT `/tenant/triage-pause` — bare-prefixed path)
    - apps/web/features/gmail/components/{ReconnectPrompt.tsx,ConnectionHealthBadge.tsx}, apps/web/features/gmail/hooks/useTenantStatus.ts (reuse as-is in the chrome)
    - apps/web/features/account/hooks/useUpdateLanguage.ts; apps/web/lib/api/base-url.ts -> `getApiUrl` (reconnect/sign-out hrefs)
    - apps/web/components/ui/{sidebar,sheet,switch,alert-dialog,sonner,dropdown-menu,badge,tooltip}.tsx, apps/web/features/landing/components/TopBar.tsx (active-link nav idiom), apps/web/features/triage/query-keys.ts, apps/web/features/billing/query-keys.ts, apps/web/features/billing/hooks/useBillingBalance.ts
    - apps/web/features/shell/messages.ts (the seeded nav.*/shell.* keys from Plan 01 — extend if needed)
    - 05A-CONTEXT.md D-01, D-02, D-03, D-04, D-12, D-13; 05A-UI-SPEC.md sections Color (health 3-state, pause amber), Copywriting (pause label/states/confirm, "Reconnect Gmail", balance pill), Spacing (56px header, 40/44px touch targets), Responsive (320px chrome wraps/compacts), Typography
    - 05A-PATTERNS.md sections "components/shell/ChromeHeader.tsx", "components/shell/AppSidebar.tsx", "components/shell/AppShell.tsx", "useToggleTriagePause.ts (rewrite — optimistic, D-13)", "useTriagePauseState.ts", "PauseBanner.tsx (rebase)"
    - 05A-RESEARCH.md Pitfall 1 (v5.90 mutation-callback form — use the classic 3-arg form to match the repo), Pattern 3
    - node_modules/next/dist/docs/ — `usePathname` usage in Next 16
  </read_first>
  <action>
    Invoke the `frontend-design` skill BEFORE writing any of these components; record a `frontend-design` visual-review note for the app shell + the chrome header (desktop + 320px, light + dark) in the SUMMARY.
    Create `apps/web/features/triage/hooks/useTriagePauseState.ts` — `"use client"` `useQuery({ queryKey: triageKeys.pauseState(), queryFn: ... derive paused from /me ... })`, invalidate-only (no `refetchInterval`), mirroring `useTenantStatus.ts`.
    Rewrite `apps/web/features/triage/hooks/useToggleTriagePause.ts` per the behavior block using the `useReorderRules` skeleton (classic `useQueryClient()` + 3-arg callbacks; do NOT adopt the v5.90 4-arg `context.client` form). Drop the current `onSuccess -> invalidate accountQueryKeys.me()` body. Rewrite `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` in lock-step.
    Rebase `apps/web/features/triage/components/PauseBanner.tsx` onto `useTriagePauseState()` (keep the `Alert`/`AlertTitle`/`AlertDescription` markup and the `useToggleTriagePause()` write hook).
    Create `apps/web/components/shell/AppShell.tsx` (`"use client"`): `<SidebarProvider defaultOpen={defaultSidebarOpen}>` -> `<AppSidebar/>` + `<SidebarInset>` -> `<ChromeHeader/>` + `<PauseBanner/>` + `<main>{children}</main>` (8-pt gutters per UI-SPEC) + a single `<Toaster/>` (shadcn `sonner`).
    Create `apps/web/components/shell/AppSidebar.tsx` (`"use client"`): shadcn `<Sidebar collapsible="icon">` with `SidebarHeader` = brand/logo, a flat `SidebarMenu` (NO `SidebarMenuSub` — D-02/#5874) with items Triage (`/triage`), Rules (`/rules`), Billing (`/billing`), Settings (`/settings`) plus an onboarding-state entry if `/me` indicates onboarding incomplete; active item via `usePathname()`; lucide icons (planner's choice). 320px = the built-in offcanvas `Sheet` (D-04) — no custom drawer; `SidebarTrigger` lives in the header.
    Create `apps/web/components/shell/ChromeHeader.tsx` (`"use client"`): a 56px-high header strip (UI-SPEC Spacing) containing `SidebarTrigger`, a page-title slot, the `BalancePill` (raw `badge` from `useBillingBalance` — neutral pill chrome, figure may be accent-tinted; show a `Skeleton` pill while loading), the `HealthDot` (a `tooltip`-wrapped colored dot from `useTenantStatus`: green CONNECTED / amber action-needed / red DISCONNECTED — on DISCONNECTED also surface `ReconnectPrompt`/`ReconnectPromptGate` semantics with a "Reconnect Gmail" affordance via `getApiUrl('/tenant/connect-gmail')`), the `PauseSwitch` (shadcn `switch`; off = paused, amber surface per UI-SPEC; turning the switch OFF i.e. pausing opens an `alert-dialog` confirm with the UI-SPEC copy "Pause automatic triage?" / "Pause triage" / "Keep it running"; turning it back ON does NOT confirm; displayed state from `useTriagePauseState()`, write from `useToggleTriagePause()`), and a `UserMenu` (`dropdown-menu`: language switch via `useUpdateLanguage`, link to `/settings`, sign out). Minimum 40px hit areas (44px at 320px) on all chrome controls — pad the hit area, not the glyph. At 320px the strip wraps/compacts (labels collapse to icons + accessible names). All visible strings via `next-intl` `nav.*`/`shell.*` keys. Render only React-escaped values — no use of the dangerously-set-inner-HTML prop anywhere in the shell.
    Update `apps/web/features/shell/messages.ts` (and re-run `pnpm --filter web i18n:build`) if you add keys beyond Plan 01's seed; add the three `components/shell/*.tsx` paths and `features/triage/components/PauseBanner.tsx` (if not already listed) to `EN_SCAN_FILES` in `apps/web/scripts/check-i18n.ts` per Plan 01's SUMMARY guidance.
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm test -- features/triage/hooks/useToggleTriagePause</automated>
  </verify>
  <acceptance_criteria>
    - `apps/web/components/shell/{AppShell,AppSidebar,ChromeHeader}.tsx` exist and are `"use client"`; `AppShell` mounts `SidebarProvider` + `SidebarInset` + a single `<Toaster/>`; `AppSidebar` uses a flat `SidebarMenu` with no `SidebarMenuSub` and `usePathname()` for active state.
    - `apps/web/features/triage/hooks/useTriagePauseState.ts` exists, keyed on `triageKeys.pauseState()`, with no `refetchInterval`.
    - `apps/web/features/triage/hooks/useToggleTriagePause.ts` uses `triageKeys.pauseState()`, the classic `useQueryClient()` 3-arg form, optimistic `onMutate`/`onError`, and `onSettled` invalidates both `triageKeys.pauseState()` and `billingKeys.balance()`; no reference to `accountQueryKeys.me()` remains in this hook.
    - `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` is updated and passes under `pnpm --filter web test`.
    - `apps/web/features/triage/components/PauseBanner.tsx` reads `useTriagePauseState()` and no longer reads `useCurrentUser().triagePaused`.
    - `ChromeHeader.tsx` consumes `useBillingBalance`, `useTenantStatus`, `useTriagePauseState`, `useToggleTriagePause`; renders an `alert-dialog` confirm only on pause-OFF; reuses `ReconnectPrompt`/`ConnectionHealthBadge` (no re-authored health/reconnect UI); contains no use of the dangerously-set-inner-HTML React prop.
    - No hardcoded English literals in the new `components/shell/*` files (via `pnpm --filter web i18n:check`); all chrome strings resolve from `nav.*`/`shell.*`.
    - `cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check` exit 0.
    - SUMMARY contains the `frontend-design` visual-review note for the shell + chrome (desktop + 320px, light + dark).
  </acceptance_criteria>
  <done>App shell + chrome built; pause is a single source of truth (D-13); gates green; visual review recorded.</done>
</task>

<task type="auto">
  <name>Task 3: Implement the app-shell / pause-toggle / connection-health / billing-balance Playwright specs (chrome portions)</name>
  <read_first>
    - apps/web/e2e/rules.spec.ts (serial mode; `page.route('http://localhost:8080/**', ...)` in-memory mock incl. `/me` returning `triagePaused`/`gmailConnectionStatus`; `fulfillJson`/`fulfillProblem`; session+locale cookies; horizontal-overflow check via `document.documentElement.scrollWidth > window.innerWidth`)
    - apps/web/e2e/mobile-topbar.spec.ts (320px viewport pattern)
    - apps/web/e2e/{app-shell,pause-toggle,connection-health,billing-balance}.spec.ts (the Plan 01 stubs to fill in)
    - apps/web/playwright.config.ts (the 320px viewport approach chosen in Plan 01 — see 05A-01-SUMMARY)
    - 05A-VALIDATION.md section "Per-Task Verification Map" rows for App shell / Pause toggle / Credit balance / Connection health
    - 05A-RESEARCH.md section "Validation Architecture" Test Map and "Playwright e2e harness" pattern
    - apps/web/features/billing/api/billing-api.ts, apps/web/lib/api/schema.d.ts (the `/api/billing/balance` path + response shape to mock)
  </read_first>
  <action>
    Fill in the four Plan-01 stubs using the `e2e/rules.spec.ts` harness (serial mode, `page.route('http://localhost:8080/**')` in-memory mock keyed on pathname+method, always mock `/me`, session+locale cookies before `goto`, `waitForLoadState('networkidle')`):
      - `e2e/app-shell.spec.ts`: navigate to `/triage`, `/billing`, `/rules`, `/settings`; assert the sidebar, the balance pill, the health dot, and the pause toggle are visible (no horizontal scroll) at 1280px AND 320px; assert client-side nav between two protected routes does NOT remount the shell (pick a robust signal — e.g. a stable element handle, or that an in-shell counter/state survives nav). Assert `/onboarding/*` does NOT show the sidebar (chrome-suppressed).
      - `e2e/pause-toggle.spec.ts`: with `/me` mocked `triagePaused:false`, toggle the chrome pause Switch OFF -> assert the `alert-dialog` confirm appears; confirm -> assert a `PUT /tenant/triage-pause` was sent with `{paused:true}` and the UI reflects paused without a full reload; assert `PauseBanner` is now visible and the `/settings` pause toggle reflects the same state. Toggle ON -> assert no confirm dialog.
      - `e2e/connection-health.spec.ts`: mock `/gmail/connection/status` (and/or `/me` `gmailConnectionStatus`) as `CONNECTED` -> assert the healthy dot; re-mock as `DISCONNECTED` -> assert the degraded dot + the reconnect affordance ("Reconnect Gmail") on `/triage` and `/billing`.
      - `e2e/billing-balance.spec.ts`: mock `/api/billing/balance` -> assert the chrome balance pill renders the value on `/triage` and `/billing`; then change the mocked balance to a higher value and trigger/await a refetch -> assert the pill updates without a full reload. (The deeper `/billing` page balance card + ledger + top-up assertions belong to Plan 04's `e2e/billing-topup.spec.ts`.)
    Cover golden path + key states at desktop AND 320px in each spec.
  </action>
  <verify>
    <automated>cd apps/web && pnpm test:e2e -- app-shell pause-toggle connection-health billing-balance</automated>
  </verify>
  <acceptance_criteria>
    - `e2e/{app-shell,pause-toggle,connection-health,billing-balance}.spec.ts` contain real (non-skipped) assertions covering the behaviors above at 1280px and 320px.
    - `pnpm --filter web test:e2e` passes (including these four specs).
    - The pause-toggle spec asserts a `PUT /tenant/triage-pause` with `{paused:true}` and consistency between chrome / `PauseBanner` / `/settings` toggle.
    - The connection-health spec covers both `CONNECTED` and `DISCONNECTED` (mocked both ways).
  </acceptance_criteria>
  <done>Chrome behaviors are covered by passing Playwright specs at desktop + 320px.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → backend API | Chrome reads (`/me`, `/api/billing/balance`, `/gmail/connection/status`) and the pause write (`PUT /tenant/triage-pause`) cross here via the typed `openapi-fetch` client + session cookie + XSRF header. |
| backend response strings → React render | Gmail connection status enum and the `/me` payload are rendered in the chrome. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05A-04 | Tampering / CSRF | `useToggleTriagePause` → `PUT /tenant/triage-pause` | mitigate | Mutation goes through `lib/api/client.ts` (`xsrfHeader()` attached); no raw cross-origin `fetch`; verified by review + the pause-toggle e2e asserting the request shape. |
| T-05A-05 | Information disclosure | `ChromeHeader` rendering `/me` fields | mitigate | Render only the balance figure, the health enum, and the locale/name the backend explicitly returns for the owner; no email body/address/token bytes; no `console.log` of `/me`. |
| T-05A-06 | Information disclosure / XSS | balance pill, health dot, user-menu labels | mitigate | All values are numbers / known enums / next-intl strings, React-escaped; no dangerously-set-inner-HTML React prop anywhere in the shell. |
| T-05A-07 | Open redirect | reconnect / sign-out hrefs via `getApiUrl(...)` | mitigate | Hrefs built from the fixed `getApiUrl` base + a constant path string; no user-controlled `?redirect=` input consumed in the shell. |

No high-severity threats — frontend-only; all backend access via the typed client; all rendered strings React-escaped; no dangerously-set-inner-HTML React prop; no unvalidated redirect params.
</threat_model>

<verification>
- `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
- `apps/web/lib/api/schema.d.ts` unchanged.
- No new runtime dependency in `apps/web/package.json`.
- Manual: load `/triage`, `/billing`, `/rules`, `/settings` and `/onboarding/gmail-connect` in a real browser at 1280px and 320px, light and dark — shell present everywhere except onboarding; chrome controls visible without scrolling; no `.zm-proto`/`.zm-auth` classes used.
</verification>

<success_criteria>
- The persistent shell wraps every `(protected)` route except onboarding; the chrome (pause / balance / health / user menu) is on every authenticated page at desktop and 320px; pause is a single source of truth across chrome / `PauseBanner` / `/settings`; `DISCONNECTED` surfaces a reconnect affordance; all gates green; visual review recorded.
</success_criteria>

<output>
After completion, create `.planning/phases/05A-user-surface-web-ui-core/05A-02-SUMMARY.md` (record: onboarding-suppression mechanism; the `frontend-design` visual-review note for shell + chrome; any nav/shell i18n keys added beyond Plan 01's seed; any `EN_SCAN_FILES` paths added).
</output>
