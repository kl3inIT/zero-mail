---
phase: 05A-user-surface-web-ui-core
plan: 02
type: execute
wave: 2
depends_on: [01]
files_modified:
  - apps/web/app/(protected)/layout.tsx
  - apps/web/app/(protected)/(app)/layout.tsx
  - apps/web/app/(protected)/onboarding/layout.tsx
  - apps/web/app/(protected)/(app)/rules/page.tsx          # MOVED from app/(protected)/rules/page.tsx (route-group split); Plan 05 then converges it
  - apps/web/app/(protected)/(app)/settings/page.tsx       # MOVED from app/(protected)/settings/page.tsx + pause control rebased here; Plan 05 then converges it
  - apps/web/components/shell/AppShell.tsx
  - apps/web/components/shell/AppSidebar.tsx
  - apps/web/components/shell/ChromeHeader.tsx
  - apps/web/features/triage/hooks/useTriagePauseState.ts
  - apps/web/features/triage/hooks/useToggleTriagePause.ts
  - apps/web/features/triage/hooks/useToggleTriagePause.test.tsx
  - apps/web/features/triage/components/PauseBanner.tsx
  - apps/web/features/triage/messages.ts
  - apps/web/features/shell/messages.ts
  - apps/web/e2e/app-shell.spec.ts
  - apps/web/e2e/pause-toggle.spec.ts
  - apps/web/e2e/connection-health.spec.ts
  - apps/web/e2e/billing-balance.spec.ts
autonomous: true
requirements: [WEB-01, WEB-04]
user_setup: []

# NOTE: this plan also moves the `app/(protected)/rules/**` and `app/(protected)/settings/**`
# directories under `app/(protected)/(app)/` (the route-group split per review #3). The two
# `page.tsx` files moved are listed above; if those directories contain additional files
# (e.g. `loading.tsx`, nested folders), they move too — the move is mechanical and URL-transparent.
# It does NOT create `(app)/triage/page.tsx` or `(app)/billing/page.tsx` — those are created by
# Plans 03/04 (Wave 3), which is why this plan's e2e specs do not navigate to /triage or /billing.

must_haves:
  truths:
    - "Every app/(protected)/** page except onboarding/* renders inside a single persistent app shell — a collapsible icon-rail sidebar (shadcn sidebar block, collapsible=icon, expanded/collapsed state in an SSR-readable cookie) + a thin persistent top header that owns the chrome region, hosted in (protected)/(app)/layout.tsx so it never unmounts on navigation between app routes (D-01)"
    - "Onboarding chrome-suppression is done via a route-group split, NOT server-layout segment branching: (protected)/(app)/layout.tsx renders the shell and wraps the app routes (rules, settings, plus the new triage, billing created in Plans 03/04); (protected)/onboarding/layout.tsx is a bare minimal layout with no shell; if node_modules/next/dist/docs/ shows the route-group move breaks the (protected)/layout.tsx-level cache()'d /me fetch or middleware matching, the constraint is documented in the SUMMARY and the fallback is a clean tested segment check in (protected)/layout.tsx — note which was chosen and why (D-01, D-05)"
    - "Primary nav is single-level / flat — one SidebarGroup separator at most, no nested SidebarMenuSub (D-02); destinations are Triage, Rules, Billing, Settings plus an onboarding-state entry where appropriate"
    - "The top-header chrome shows the pause toggle, credit-balance pill, and Gmail connection-health indicator, built from raw shadcn primitives (badge for balance, tooltip-wrapped colored dot for health, switch + confirm dialog for pause), without scrolling at desktop and 320px (D-03)"
    - "320px / mobile uses the shadcn sidebar's built-in offcanvas Sheet mode opened by SidebarTrigger in the header — no separate responsive nav implementation (D-04)"
    - "Chrome data (pause, balance, health) is prefetched in (protected)/(app)/layout.tsx via Promise.all and dehydrated into a HydrationBoundary wrapping the client AppShell, and is consumed only within the shell subtree, never relied on by a deeper page boundary (D-10)"
    - "The credit-balance query polls at refetchInterval ~45s with refetchIntervalInBackground:false and staleTime ~30s, plus invalidateQueries after billable actions / top-up settle / pause toggle (D-11)"
    - "Pause state and Gmail connection health stay invalidate-only — no polling (D-12); no SSE/WebSocket is used for any chrome data (D-14)"
    - "Toggling pause from the chrome persists via /tenant/triage-pause, updates optimistically (onMutate cancel+snapshot+setQueryData), rolls back on error, and is reconciled by invalidating triageKeys.pauseState() and the balance key on onSettled; onSettled also invalidates accountQueryKeys.me() so other /me consumers don't go stale, while triageKeys.pauseState() stays the only pause READ key (D-13)"
    - "The chrome pause toggle, the /settings pause toggle, and PauseBanner all read the single triageKeys.pauseState() cache entry via one read hook (useTriagePauseState) + one write hook (useToggleTriagePause) — no local useState, no ad-hoc query keys; the /settings page's pause control is rebased onto these hooks IN THIS PLAN (D-13)"
    - "A DISCONNECTED Gmail status surfaces a reconnect affordance reusing ReconnectPrompt semantics (D-03)"
    - "onboarding/* keeps a minimal chrome-suppressed layout and does not render inside the sidebar shell (D-05)"
  artifacts:
    - path: "apps/web/app/(protected)/layout.tsx"
      provides: "Provider host: NextIntlClientProvider + QueryProvider + cache()'d /me + middleware-friendly; no shell here — shell moved to (app)/layout.tsx (D-01)"
    - path: "apps/web/app/(protected)/(app)/layout.tsx"
      provides: "Shell host: sidebar_state cookie, Promise.all chrome prefetch, dehydrate + HydrationBoundary wrapping <AppShell> (D-01, D-10)"
      contains: "HydrationBoundary"
    - path: "apps/web/app/(protected)/onboarding/layout.tsx"
      provides: "Bare minimal chrome-suppressed layout (no shell, no sidebar) — D-05"
    - path: "apps/web/components/shell/AppShell.tsx"
      provides: "Client shell: SidebarProvider + AppSidebar + SidebarInset + ChromeHeader + main + Toaster (D-01)"
    - path: "apps/web/components/shell/AppSidebar.tsx"
      provides: "Flat icon-rail sidebar (collapsible=icon, no SidebarMenuSub, offcanvas Sheet at 320px) — D-02, D-04"
    - path: "apps/web/components/shell/ChromeHeader.tsx"
      provides: "Thin header with BalancePill (badge), HealthDot (tooltip dot + reconnect on DISCONNECTED), PauseSwitch (switch + confirm dialog on pause-OFF), UserMenu — D-03"
    - path: "apps/web/features/triage/hooks/useTriagePauseState.ts"
      provides: "Single read hook for pause state keyed on triageKeys.pauseState(), invalidate-only — no refetchInterval (D-12, D-13)"
    - path: "apps/web/features/triage/hooks/useToggleTriagePause.ts"
      provides: "Optimistic pause mutation (onMutate/onError/onSettled) keyed on triageKeys.pauseState(); onSettled invalidates triageKeys.pauseState() + billingKeys.balance() + accountQueryKeys.me() (D-11, D-13)"
      contains: "triageKeys.pauseState"
    - path: "apps/web/app/(protected)/(app)/settings/page.tsx"
      provides: "Settings page moved under (app)/ and its pause control rebased onto useTriagePauseState()/useToggleTriagePause() — D-09/D-13 pause single-source done HERE (Plan 05 only touches tokens/states/responsive on it, not the pause control)"
  key_links:
    - from: "apps/web/components/shell/ChromeHeader.tsx"
      to: "triageKeys.pauseState()"
      via: "useTriagePauseState + useToggleTriagePause (single source of truth, D-13)"
      pattern: "useTriagePauseState"
    - from: "apps/web/components/shell/ChromeHeader.tsx"
      to: "/api/billing/balance"
      via: "useBillingBalance (polled ~45s + invalidate-after-action, D-11)"
      pattern: "useBillingBalance"
    - from: "apps/web/app/(protected)/(app)/layout.tsx"
      to: "triageKeys.pauseState() / billingKeys.balance() / gmailQueryKeys.status()"
      via: "queryClient.prefetchQuery x3 + dehydrate inside HydrationBoundary (D-10)"
      pattern: "prefetchQuery"
    - from: "apps/web/app/(protected)/(app)/settings/page.tsx"
      to: "triageKeys.pauseState()"
      via: "useTriagePauseState + useToggleTriagePause (same cache entry as chrome — D-13)"
      pattern: "useTriagePauseState"
---

<objective>
Restructure `app/(protected)` into route groups: `(protected)/layout.tsx` stays the provider host (intl + query + cache()'d `/me`), `(protected)/(app)/layout.tsx` becomes the persistent authenticated app shell (a collapsible icon-rail sidebar — shadcn `sidebar` block — + a 56px top header that owns the chrome region: pause toggle, credit-balance pill, Gmail connection-health indicator, user menu) wrapping the app routes (the moved `rules` + `settings`, plus the `triage` + `billing` pages created by Plans 03/04), and `(protected)/onboarding/layout.tsx` stays a bare minimal chrome-suppressed layout (D-05 — replacing the fragile server-layout segment-branch with a route-group split). Prefetch the three chrome queries in `(app)/layout.tsx` and hydrate them into a client `<AppShell>`. Refactor the pause toggle onto a single source of truth (D-13): one query key, one read hook, one optimistic write hook — consumed by the chrome toggle, the `/settings` toggle (rebased HERE), and `PauseBanner`.

Purpose: WEB-04 — the trust UI (pause / balance / health) must be on every authenticated screen, flicker-free, and physically incapable of state drift.
Output: `(protected)/layout.tsx` reduced to provider host, new `(protected)/(app)/layout.tsx` + `(protected)/onboarding/layout.tsx`, the `rules` + `settings` route directories moved under `(app)/`, new `components/shell/{AppShell,AppSidebar,ChromeHeader}.tsx`, new `useTriagePauseState`, rewritten `useToggleTriagePause` + its test, rebased `PauseBanner`, rebased `(app)/settings/page.tsx` pause control, implemented `app-shell` / `pause-toggle` / `connection-health` / `billing-balance` Playwright specs (chrome portions, on Wave-2-existing routes only).
</objective>

<reviewer_response>
Cross-AI review:
- #2 (Codex HIGH — settings/page.tsx ownership conflict): the `/settings` pause-toggle rebase onto `useTriagePauseState()`/`useToggleTriagePause()` is moved INTO this plan; `app/(protected)/(app)/settings/page.tsx` is in this plan's `files_modified`. Plan 05's convergence pass treats `settings/page.tsx` as already-on-the-shell with the pause control already single-sourced — it only touches tokens / shared-states / responsive there. Plan 02 is Wave 2 and Plan 05 is Wave 3, so the `settings/page.tsx` (and `rules/page.tsx`, and `AppSidebar.tsx`) handoff is sequential — no same-wave overlap.
- #3 (Codex HIGH — fragile server-layout segment branching): replaced with a route-group split (`(protected)/(app)/layout.tsx` = shell; `(protected)/onboarding/layout.tsx` = bare). The `rules` + `settings` route directories move under `(app)/`; `triage` + `billing` (Plans 03/04) live under `(app)/` too. Verified against `node_modules/next/dist/docs/`; if the move breaks the `(protected)/layout.tsx`-level `cache()`'d `/me` fetch or middleware matching, the fallback is a clean tested segment check in `(protected)/layout.tsx` — the executor documents which was chosen and why in the SUMMARY (and flags a Plan-06 `EN_SCAN_FILES` reconciliation if paths changed).
- #4 (Codex HIGH — premature /triage /billing e2e): the `/triage` and `/billing` chrome-presence smoke assertions are MOVED OUT of this plan's e2e specs into Plans 03/04 (their triage/billing specs assert the shell renders on those pages). This plan does NOT create placeholder `/triage` or `/billing` pages. This plan's `e2e/app-shell.spec.ts` asserts the shell on routes that exist at Wave 2 — `/rules` and `/settings` — and the chrome-widget behavior specs (`pause-toggle`, `billing-balance`, `connection-health`) drive from those existing routes.
- #11 (Codex MEDIUM — useTriagePauseState reading /me): the pause mutation's `onSettled` also `invalidateQueries({ queryKey: accountQueryKeys.me() })`; `triageKeys.pauseState()` stays the only pause READ key.
- #8 (OpenCode MEDIUM): explicit acceptance criteria added for pause-single-source-in-sync (a Vitest assertion that mutating via one hook updates all readers off the one cache entry, plus a Playwright cross-route consistency check) and for the balance `refetchInterval ≈ 45s` actually firing (Plan 01 owns the fake-timer Vitest; this plan's e2e additionally observes a balance refetch on a Wave-2 route).
</reviewer_response>

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
  <name>Task 1: Route-group split — (protected)/layout.tsx = provider host; (app)/layout.tsx = shell host; onboarding/layout.tsx = bare; move rules + settings under (app)/</name>
  <read_first>
    - apps/web/app/(protected)/layout.tsx (the current thin version — keep the NextIntlClientProvider + QueryProvider nesting + the cache()'d /me / middleware behavior here)
    - apps/web/features/account/api/account-api.ts (`getCurrentUserCached` — the `cache()` keyed-by-cookie-string idiom; RSC callers pass `(await cookies()).toString()`)
    - apps/web/app/(public)/layout.tsx, apps/web/app/(auth)/layout.tsx (minimal-layout idiom for the new onboarding layout)
    - apps/web/middleware.ts (confirm the protected-route matcher still covers `(protected)/(app)/...` and `(protected)/onboarding/...` after the route-group move — route groups are URL-transparent, so `/rules`, `/settings`, `/triage`, `/billing`, `/onboarding/*` URLs are unchanged; verify the matcher pattern)
    - apps/web/components/ui/sidebar.tsx (the generated primitive — read `SIDEBAR_COOKIE_NAME`, recorded in 05A-01-SUMMARY)
    - apps/web/features/triage/query-keys.ts, apps/web/features/billing/query-keys.ts, apps/web/features/billing/hooks/useBillingBalance.ts, apps/web/features/gmail/query-keys.ts, apps/web/features/gmail/api/gmail-api.ts
    - apps/web/lib/query-client.tsx (global staleTime/gcTime), node_modules/@tanstack/react-query (HydrationBoundary/dehydrate/QueryClient API)
    - 05A-CONTEXT.md D-01, D-05, D-10; 05A-RESEARCH.md Pattern 1, Pattern 2, Pitfall 3 (hydration mismatch), Pitfall 4 (#8479), Pitfall 8 (onboarding inside shell)
    - 05A-PATTERNS.md section "app/(protected)/layout.tsx (route layout — rewrite to shell host)" and section "app/(protected)/onboarding/layout.tsx" (NOTE these analogs predate the route-group decision — apply the route-group split: the shell-host content moves into `(app)/layout.tsx`; the onboarding-bare content moves into `(protected)/onboarding/layout.tsx`; `(protected)/layout.tsx` keeps only the providers + cache()'d /me)
    - node_modules/next/dist/docs/ — async `cookies()` in Next 16, route-group layout nesting, whether a nested route group can have its own layout while the parent route-group layout still runs (read BEFORE writing any Next code per apps/web/AGENTS.md; this is the decisive check for the route-group split)
  </read_first>
  <action>
    First, study `node_modules/next/dist/docs/` on route-group layout nesting. If a nested route group (`(protected)/(app)/`) can carry its own layout while `(protected)/layout.tsx` still runs (it can — route groups nest layouts normally), proceed with the split below. If the docs reveal a constraint that breaks the `cache()`'d `/me` or the middleware matcher, fall back to keeping the app routes directly under `(protected)/` and a clean, tested `headers()`-based segment check in `(protected)/layout.tsx` to choose shell-vs-bare — document the chosen approach and the constraint in the SUMMARY, and tell Plan 06 to do an `EN_SCAN_FILES` reconciliation if paths changed.
    Split approach (preferred):
      - `app/(protected)/layout.tsx` — reduce to a Server Component that keeps ONLY: `NextIntlClientProvider` (from `getLocale`/`getMessages`) + `QueryProvider` + whatever `/me`/auth-redirect logic currently lives here (the `cache()`'d `/me` stays here so both `(app)/` and `onboarding/` subtrees share it). It renders `{children}` directly — no `<AppShell>`, no sidebar cookie read.
      - `app/(protected)/(app)/layout.tsx` — NEW Server Component: `await cookies()` → read the sidebar cookie (`SIDEBAR_COOKIE_NAME`; default open when value !== 'false'); `const queryClient = new QueryClient()`; `await Promise.all([queryClient.prefetchQuery({ queryKey: triageKeys.pauseState(), queryFn: () => /* paused boolean from /me via getCurrentUserCached */ }), queryClient.prefetchQuery({ queryKey: billingKeys.balance(), queryFn: getBillingBalance }), queryClient.prefetchQuery({ queryKey: gmailQueryKeys.status(), queryFn: getTenantStatus })])`; then `<HydrationBoundary state={dehydrate(queryClient)}><AppShell defaultSidebarOpen={...}>{children}</AppShell></HydrationBoundary>`. Note D-10/#8479: these prefetched queries are consumed only inside `<AppShell>` (the header), never relied on by a deeper page boundary. `PauseBanner` is mounted inside `<AppShell>`, not at any layout root.
      - `app/(protected)/onboarding/layout.tsx` — NEW minimal nested layout mirroring `app/(auth)/layout.tsx`: a focused-funnel wrapper (centered, no sidebar, no chrome) passing children through; it inherits the parent's intl + query providers; no `Sidebar`/`AppShell` import.
      - MOVE the existing app route directories into `(app)/`: `app/(protected)/rules/**` → `app/(protected)/(app)/rules/**` (this is `apps/web/app/(protected)/(app)/rules/page.tsx` + any sibling files); `app/(protected)/settings/**` → `app/(protected)/(app)/settings/**` (this is `apps/web/app/(protected)/(app)/settings/page.tsx` + any sibling files, incl. the `settings/privacy/` segment Plan 05 will add). The new `triage` and `billing` pages (created in Plans 03/04) will live under `(app)/` too (Plan 01's `EN_SCAN_FILES` already uses the `(app)/` paths). Onboarding routes do NOT move — they stay at `app/(protected)/onboarding/{gmail-connect,template-select,complete}/page.tsx`. Update any intra-repo file imports that referenced the old paths (route-group folders are URL-transparent so `<Link href="/rules">` etc. are unchanged; only relative file imports change). For `(app)/rules/page.tsx`, just move it (Plan 05 converges it). For `(app)/settings/page.tsx`, the pause-control rebase happens in Task 2 — in THIS task just move the file and confirm it still typechecks.
    Verify manually that the sidebar is absent on `/onboarding/*` and present on `/rules` and `/settings`. Do not write UI styling here beyond layout wiring; the visual `frontend-design` review for the shell happens in Task 2.
  </action>
  <verify>
    <automated>cd apps/web && pnpm typecheck && pnpm lint</automated>
  </verify>
  <acceptance_criteria>
    - `app/(protected)/layout.tsx` is a Server Component containing ONLY the providers + the cache()'d `/me`/auth logic; it imports no `Sidebar`/`AppShell` and reads no sidebar cookie.
    - `app/(protected)/(app)/layout.tsx` exists, `await cookies()`, reads the sidebar cookie via `SIDEBAR_COOKIE_NAME`, creates a `QueryClient`, calls `prefetchQuery` three times inside a `Promise.all`, `dehydrate`s, and wraps `<AppShell>` in `<HydrationBoundary>`.
    - `app/(protected)/onboarding/layout.tsx` exists and is a chrome-suppressed minimal wrapper (no `Sidebar`/`AppShell` import).
    - `app/(protected)/(app)/rules/page.tsx` and `app/(protected)/(app)/settings/page.tsx` exist (the old `app/(protected)/rules/page.tsx` / `app/(protected)/settings/page.tsx` are gone); URL paths `/rules` and `/settings` are unchanged; `middleware.ts`'s matcher still covers them; this plan did NOT create `(app)/triage/page.tsx` or `(app)/billing/page.tsx`.
    - `PauseBanner` is mounted inside `<AppShell>`, not at any layout root.
    - `cd apps/web && pnpm typecheck && pnpm lint` exit 0.
    - SUMMARY documents the chosen route-group approach (split vs. fallback segment-check) and any Next 16 constraint found.
  </acceptance_criteria>
  <done>Route-group split done (or the documented fallback); `(app)/layout.tsx` is the shell host with prefetched chrome data; onboarding stays chrome-suppressed; `rules` + `settings` moved under `(app)/`; typecheck + lint green.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Build AppShell / AppSidebar / ChromeHeader + refactor pause to single source of truth (D-13) + rebase the /settings pause control</name>
  <behavior>
    - useToggleTriagePause: onMutate cancels queries on triageKeys.pauseState(), snapshots the previous boolean, setQueryData to the next value; onError restores the snapshot; onSettled invalidates triageKeys.pauseState() AND billingKeys.balance() AND accountQueryKeys.me(). (test: useToggleTriagePause.test.tsx rewritten — assert classic useQueryClient() form, the right key, optimistic set, rollback, and all three invalidations; ALSO assert that setQueryData(triageKeys.pauseState(), ...) is the single write target so any reader using triageKeys.pauseState() sees the new value — the "single source of truth in sync" check.)
    - useTriagePauseState: useQuery keyed on triageKeys.pauseState(), no refetchInterval (invalidate-only), queryFn derives the paused boolean from /me.
    - PauseBanner: returns null unless useTriagePauseState() data is true; renders the warning Alert; resume button calls useToggleTriagePause().mutate(false). No useCurrentUser dependency for the paused flag.
    - ChromeHeader: renders the balance pill (from useBillingBalance), the health dot (from useTenantStatus — green CONNECTED, amber action-needed, red DISCONNECTED + ReconnectPrompt affordance), the pause Switch (off when paused, amber surface; turning OFF i.e. pausing opens an AlertDialog confirm; turning ON does not confirm), and a dropdown-menu UserMenu (language, settings link, sign out). All visible strings via next-intl nav.*/shell.* keys. Toast invocation for any chrome action stays in ChromeHeader (where useTranslations is available); hooks only mutate the cache.
    - (app)/settings/page.tsx pause control: reads useTriagePauseState() (no local useState for the paused flag), writes via useToggleTriagePause() — the SAME cache entry as the chrome toggle; ReconnectPromptGate / ConnectionHealthBadge stay as-is.
  </behavior>
  <read_first>
    - apps/web/app/(protected)/(app)/settings/page.tsx (the moved settings page — it already composes useCurrentUser + useTenantStatus + useDisconnectGmail + useToggleTriagePause + ConnectionHealthBadge + ReconnectPromptGate; also the hand-rolled pause switch markup to REPLACE with shadcn `switch` driven by useTriagePauseState/useToggleTriagePause)
    - apps/web/features/rules/hooks/use-rules.ts -> `useReorderRules` (the only existing full optimistic mutation: cancelQueries -> snapshot -> setQueryData -> onError restore -> onSettled invalidate; classic `useQueryClient()` + 3-arg-callback form)
    - apps/web/features/triage/hooks/useToggleTriagePause.ts + useToggleTriagePause.test.tsx (current `onSuccess -> invalidate accountQueryKeys.me()` body to replace; the Vitest harness)
    - apps/web/features/triage/components/PauseBanner.tsx (current `useCurrentUser().triagePaused` read to rebase onto `useTriagePauseState()`)
    - apps/web/features/triage/api/triage-api.ts (`setTriagePaused` -> PUT `/tenant/triage-pause` — bare-prefixed path)
    - apps/web/features/gmail/components/{ReconnectPrompt.tsx,ConnectionHealthBadge.tsx}, apps/web/features/gmail/hooks/useTenantStatus.ts (reuse as-is in the chrome — note ReconnectPrompt may render cramped in a 56px header; use a compact affordance / icon-button that opens ReconnectPrompt rather than inlining its full body)
    - apps/web/features/account/hooks/useUpdateLanguage.ts; apps/web/lib/api/base-url.ts -> `getApiUrl` (reconnect/sign-out hrefs)
    - apps/web/components/ui/{sidebar,sheet,switch,alert-dialog,sonner,dropdown-menu,badge,tooltip}.tsx, apps/web/features/landing/components/TopBar.tsx (active-link nav idiom), apps/web/features/triage/query-keys.ts, apps/web/features/billing/query-keys.ts, apps/web/features/account/query-keys.ts, apps/web/features/billing/hooks/useBillingBalance.ts
    - apps/web/features/shell/messages.ts (the seeded nav.*/shell.* keys from Plan 01 — extend if needed)
    - 05A-CONTEXT.md D-01, D-02, D-03, D-04, D-12, D-13; 05A-UI-SPEC.md sections Color (health 3-state, pause amber), Copywriting (pause label/states/confirm, "Reconnect Gmail", balance pill), Spacing (56px header, 40/44px touch targets), Responsive (320px chrome wraps/compacts), Typography
    - 05A-PATTERNS.md sections "components/shell/ChromeHeader.tsx", "components/shell/AppSidebar.tsx", "components/shell/AppShell.tsx", "useToggleTriagePause.ts (rewrite — optimistic, D-13)", "useTriagePauseState.ts", "PauseBanner.tsx (rebase)"
    - 05A-RESEARCH.md Pitfall 1 (v5.90 mutation-callback form — use the classic 3-arg form to match the repo), Pattern 3
    - node_modules/next/dist/docs/ — `usePathname` usage in Next 16
  </read_first>
  <action>
    Invoke the `frontend-design` skill BEFORE writing any of these components; record a `frontend-design` visual-review note for the app shell + the chrome header (desktop + 320px, light + dark) in the SUMMARY.
    Create `apps/web/features/triage/hooks/useTriagePauseState.ts` — `"use client"` `useQuery({ queryKey: triageKeys.pauseState(), queryFn: ... derive paused from /me ... })`, invalidate-only (no `refetchInterval`), mirroring `useTenantStatus.ts`.
    Rewrite `apps/web/features/triage/hooks/useToggleTriagePause.ts` per the behavior block using the `useReorderRules` skeleton (classic `useQueryClient()` + 3-arg callbacks; do NOT adopt the v5.90 4-arg `context.client` form). Drop the current `onSuccess -> invalidate accountQueryKeys.me()` body; replace with `onMutate`/`onError` optimistic handling on `triageKeys.pauseState()` and `onSettled` invalidating `triageKeys.pauseState()`, `billingKeys.balance()`, AND `accountQueryKeys.me()`. Rewrite `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` in lock-step (incl. the "single write target" assertion).
    Rebase `apps/web/features/triage/components/PauseBanner.tsx` onto `useTriagePauseState()` (keep the `Alert`/`AlertTitle`/`AlertDescription` markup and the `useToggleTriagePause()` write hook).
    Rebase the pause control in `apps/web/app/(protected)/(app)/settings/page.tsx`: replace the hand-rolled pause switch + any local `useState` for the paused flag with the shadcn `switch` (or keep the existing control's shape if cleaner) driven by `useTriagePauseState()` (read) + `useToggleTriagePause()` (write) — the SAME `triageKeys.pauseState()` cache entry the chrome uses. Do not otherwise restyle the settings page here (tokens/states/responsive convergence is Plan 05's job).
    Create `apps/web/components/shell/AppShell.tsx` (`"use client"`): `<SidebarProvider defaultOpen={defaultSidebarOpen}>` -> `<AppSidebar/>` + `<SidebarInset>` -> `<ChromeHeader/>` + `<PauseBanner/>` + `<main>{children}</main>` (8-pt gutters per UI-SPEC) + a single `<Toaster/>` (shadcn `sonner`).
    Create `apps/web/components/shell/AppSidebar.tsx` (`"use client"`): shadcn `<Sidebar collapsible="icon">` with `SidebarHeader` = brand/logo, a flat `SidebarMenu` (NO `SidebarMenuSub` — D-02/#5874) with items Triage (`/triage`), Rules (`/rules`), Billing (`/billing`), Settings (`/settings`) plus an onboarding-state entry if `/me` indicates onboarding incomplete; active item via `usePathname()`; lucide icons (planner's choice). 320px = the built-in offcanvas `Sheet` (D-04) — no custom drawer; `SidebarTrigger` lives in the header.
    Create `apps/web/components/shell/ChromeHeader.tsx` (`"use client"`): a 56px-high header strip (UI-SPEC Spacing) containing `SidebarTrigger`, a page-title slot, the `BalancePill` (raw `badge` from `useBillingBalance` — neutral pill chrome, figure may be accent-tinted; show a `Skeleton` pill while loading), the `HealthDot` (a `tooltip`-wrapped colored dot from `useTenantStatus`: green CONNECTED / amber action-needed / red DISCONNECTED — on DISCONNECTED also surface a COMPACT "Reconnect Gmail" affordance (an icon-button or small button that opens `ReconnectPrompt`/`ReconnectPromptGate`, not the full inlined body — keeps 320px uncramped) via `getApiUrl('/tenant/connect-gmail')`), the `PauseSwitch` (shadcn `switch`; off = paused, amber surface per UI-SPEC; turning the switch OFF i.e. pausing opens an `alert-dialog` confirm with the UI-SPEC copy "Pause automatic triage?" / "Pause triage" / "Keep it running"; turning it back ON does NOT confirm; displayed state from `useTriagePauseState()`, write from `useToggleTriagePause()`; on success a `sonner` toast is fired FROM ChromeHeader, not from the hook), and a `UserMenu` (`dropdown-menu`: language switch via `useUpdateLanguage`, link to `/settings`, sign out). Minimum 40px hit areas (44px at 320px) on all chrome controls — pad the hit area, not the glyph. At 320px the strip wraps/compacts (labels collapse to icons + accessible names). All visible strings via `next-intl` `nav.*`/`shell.*` keys. Render only React-escaped values — no use of the dangerously-set-inner-HTML React prop anywhere in the shell.
    Update `apps/web/features/shell/messages.ts` (and re-run `pnpm --filter web i18n:build` locally — do NOT commit the generated bundles) if you add keys beyond Plan 01's seed. Do NOT edit `apps/web/scripts/check-i18n.ts` — Plan 01 already registered every Phase 5A path (incl. `components/shell/*.tsx`, `features/triage/components/PauseBanner.tsx`, `app/(protected)/(app)/settings/page.tsx`) in `EN_SCAN_FILES`.
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm test -- features/triage/hooks/useToggleTriagePause</automated>
  </verify>
  <acceptance_criteria>
    - `apps/web/components/shell/{AppShell,AppSidebar,ChromeHeader}.tsx` exist and are `"use client"`; `AppShell` mounts `SidebarProvider` + `SidebarInset` + a single `<Toaster/>`; `AppSidebar` uses a flat `SidebarMenu` with no `SidebarMenuSub` and `usePathname()` for active state.
    - `apps/web/features/triage/hooks/useTriagePauseState.ts` exists, keyed on `triageKeys.pauseState()`, with no `refetchInterval`.
    - `apps/web/features/triage/hooks/useToggleTriagePause.ts` uses `triageKeys.pauseState()`, the classic `useQueryClient()` 3-arg form, optimistic `onMutate`/`onError`, and `onSettled` invalidates `triageKeys.pauseState()`, `billingKeys.balance()`, AND `accountQueryKeys.me()`; the single optimistic write target is `setQueryData(triageKeys.pauseState(), ...)`.
    - `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` is updated, asserts the optimistic write/rollback/all-three-invalidations AND that `triageKeys.pauseState()` is the single write target, and passes under `pnpm --filter web test`.
    - `apps/web/features/triage/components/PauseBanner.tsx` reads `useTriagePauseState()` and no longer reads `useCurrentUser().triagePaused`.
    - `apps/web/app/(protected)/(app)/settings/page.tsx` reads the paused flag from `useTriagePauseState()` (no local `useState` for it) and writes via `useToggleTriagePause()` — the same `triageKeys.pauseState()` cache entry as the chrome.
    - `ChromeHeader.tsx` consumes `useBillingBalance`, `useTenantStatus`, `useTriagePauseState`, `useToggleTriagePause`; renders an `alert-dialog` confirm only on pause-OFF; surfaces a COMPACT reconnect affordance on `DISCONNECTED` (not the full inlined `ReconnectPrompt` body); fires the pause-success toast from the component (not the hook); contains no use of the dangerously-set-inner-HTML React prop.
    - No hardcoded English literals in the new `components/shell/*` files (via `pnpm --filter web i18n:check`); all chrome strings resolve from `nav.*`/`shell.*`.
    - `cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check` exit 0.
    - SUMMARY contains the `frontend-design` visual-review note for the shell + chrome (desktop + 320px, light + dark).
  </acceptance_criteria>
  <done>App shell + chrome built; pause is a single source of truth (D-13) consumed by chrome + PauseBanner + /settings; toast lives in the component; gates green; visual review recorded.</done>
</task>

<task type="auto">
  <name>Task 3: Implement the app-shell / pause-toggle / connection-health / billing-balance Playwright specs (chrome portions, on Wave-2-existing routes only)</name>
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
    Fill in the four Plan-01 stubs using the `e2e/rules.spec.ts` harness (serial mode, `page.route('http://localhost:8080/**')` in-memory mock keyed on pathname+method, always mock `/me`, session+locale cookies before `goto`, `waitForLoadState('networkidle')`). IMPORTANT: drive everything from routes that EXIST at Wave 2 — `/rules` and `/settings` — NOT `/triage` or `/billing` (those pages don't exist until Wave 3; their shell-presence smoke checks live in Plans 03/04's specs):
      - `e2e/app-shell.spec.ts`: navigate to `/rules` and `/settings`; assert the sidebar, the balance pill, the health dot, and the pause toggle are visible (no horizontal scroll) at 1280px AND 320px; assert client-side nav between `/rules` and `/settings` does NOT remount the shell (pick a robust signal — a stable element handle, or that an in-shell counter/state survives nav). Assert `/onboarding/gmail-connect` does NOT show the sidebar (chrome-suppressed). Add a `// Plan 03/04 own the /triage and /billing shell-presence checks` comment.
      - `e2e/pause-toggle.spec.ts`: on `/settings` (which exists at Wave 2 and now has the rebased pause control), with `/me` mocked `triagePaused:false`, toggle the chrome pause Switch OFF -> assert the `alert-dialog` confirm appears; confirm -> assert a `PUT /tenant/triage-pause` was sent with `{paused:true}` and the UI reflects paused without a full reload; assert `PauseBanner` is now visible AND the `/settings` page's own pause toggle reflects the same state (the single-source-in-sync cross-reader check); toggle ON -> assert no confirm dialog. Run at 1280px and 320px.
      - `e2e/connection-health.spec.ts`: on `/rules` and `/settings`, mock `/gmail/connection/status` (and/or `/me` `gmailConnectionStatus`) as `CONNECTED` -> assert the healthy dot; re-mock as `DISCONNECTED` -> assert the degraded dot + the "Reconnect Gmail" affordance. Run at 1280px and 320px.
      - `e2e/billing-balance.spec.ts`: on `/rules` and `/settings`, mock `/api/billing/balance` -> assert the chrome balance pill renders the value; then change the mocked balance to a higher value and trigger/await a refetch (advance time past ~45s or trigger an invalidating action) -> assert the pill updates without a full reload (the "45s refetch actually fires / isn't swallowed by the global staleTime" e2e-level check; Plan 01 owns the fake-timer Vitest version). Add a `// the /billing page balance card + ledger + top-up assertions belong to Plan 04's e2e/billing-topup.spec.ts` comment.
    Cover golden path + key states at desktop AND 320px in each spec.
  </action>
  <verify>
    <automated>cd apps/web && pnpm test:e2e -- app-shell pause-toggle connection-health billing-balance</automated>
  </verify>
  <acceptance_criteria>
    - `e2e/{app-shell,pause-toggle,connection-health,billing-balance}.spec.ts` contain real (non-skipped) assertions covering the behaviors above at 1280px and 320px, driving from `/rules` and `/settings` only (no `/triage` or `/billing` navigation).
    - `pnpm --filter web test:e2e` passes (including these four specs).
    - The pause-toggle spec asserts a `PUT /tenant/triage-pause` with `{paused:true}` and consistency between chrome / `PauseBanner` / `/settings` toggle (single-source-in-sync).
    - The connection-health spec covers both `CONNECTED` and `DISCONNECTED` (mocked both ways).
    - The billing-balance spec asserts the pill updates after a ~45s-window refetch / invalidating action.
  </acceptance_criteria>
  <done>Chrome behaviors are covered by passing Playwright specs at desktop + 320px, on Wave-2-existing routes; /triage and /billing shell checks deferred to Plans 03/04.</done>
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
- `pnpm --filter web i18n:build` is run as part of the gate but the generated `i18n/messages/{vi,en}.json` are NOT in this plan's `files_modified` and must not be committed here — Plan 06 regenerates and commits the canonical bundles. The per-feature `messages.ts` files (which ARE owned here) are the source of truth.
- `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
- `apps/web/lib/api/schema.d.ts` unchanged.
- No new runtime dependency in `apps/web/package.json`.
- Manual: load `/rules`, `/settings`, and `/onboarding/gmail-connect` in a real browser at 1280px and 320px, light and dark — shell present on `/rules` + `/settings`, absent on onboarding; chrome controls visible without scrolling; no `.zm-proto`/`.zm-auth` classes used.
</verification>

<success_criteria>
- The route-group split (or the documented fallback) is in place; the persistent shell wraps every `(protected)/(app)` route; onboarding stays chrome-suppressed; the chrome (pause / balance / health / user menu) is on every authenticated page at desktop and 320px; pause is a single source of truth across chrome / `PauseBanner` / `/settings` (rebased here); `DISCONNECTED` surfaces a compact reconnect affordance; the pause mutation also invalidates `/me`; all gates green; visual review recorded.
</success_criteria>

<output>
After completion, create `.planning/phases/05A-user-surface-web-ui-core/05A-02-SUMMARY.md` (record: the chosen route-group approach (split vs. fallback) and any Next 16 constraint found; whether `EN_SCAN_FILES` paths need a Plan-06 reconciliation; the `frontend-design` visual-review note for shell + chrome; any nav/shell i18n keys added beyond Plan 01's seed; confirmation the `/settings` pause control is now on the single source).
</output>
