# Phase 5A: User Surface — Web UI Core - Research

**Researched:** 2026-05-12
**Domain:** Next.js 16 App Router frontend — authenticated app shell (shadcn `sidebar`), TanStack Query v5 SSR/prefetch + optimistic chrome state, typed `openapi-fetch` consumption, `next-intl` vi/en, Playwright e2e
**Confidence:** HIGH (decisions are locked; main risk is OpenAPI surface gaps — see below)

## Summary

Phase 5A is a frontend-only build against the **already-shipped** `apps/web` (Next 16.2.6 / React 19.2.6, route groups `(public)`/`(auth)`/`(protected)`, feature folders, typed `openapi-fetch` client at `lib/api`, `next-intl` vi/en, Phase 1.6 design tokens, TanStack Query 5.100.9). Nearly every architectural decision is already locked in `05A-CONTEXT.md` (D-01..D-18) and the visual contract in `05A-UI-SPEC.md`. The planner's job is sequencing + wiring, not exploration.

The single largest planning risk is **OpenAPI-surface gaps**: the generated `lib/api/schema.d.ts` (and `openapi/openapi.json`) do **not** expose (1) a triage-audit **list** endpoint, (2) a top-up **intent-status** poll endpoint or an `intentId` field, (3) a billing **ledger/transaction-history** list endpoint, or (4) a QR **image URL** (only a raw `qrPayload` string). SPEC.md and CONTEXT.md both say missing endpoints are **logged as gaps, not built** — so the planner must (a) carve these into clearly-flagged "blocked-on-backend" sub-tasks, and (b) design the screens to degrade gracefully (the audit screen renders the undo flow + empty/error states against the data it *can* get; billing renders balance + top-up-intent + a "ledger history coming soon / not yet available" panel; top-up polls `/api/billing/balance` for the credit signal, since there's no intent-status endpoint and the `code` is the only intent handle).

**Primary recommendation:** Plan in four tracks — (1) **App shell + chrome** (`(protected)/layout.tsx` → SidebarProvider/SidebarInset + 56px top header + HydrationBoundary; `<AppSidebar>`, `<AppShell>` client components; refactor pause state onto one query key per D-13); (2) **`features/triage`** (audit list — *gap-flagged*, undo `AlertDialog`, shadow-mode toggle, sender-safety-net list; single `/triage` page + `Tabs` + `?tab=`); (3) **`features/billing`** (`/billing` balance + ledger — *ledger gap-flagged*; `/billing/top-up` amount → QR/bank fields → poll balance → success; `?intentId=` rehydration *blocked unless backend returns an intent id* — fall back to `?code=`); (4) **convergence + privacy page** (`/settings/privacy` segment; bring rules/onboarding×3/settings onto shell + 1.6 tokens + shared loading/empty/error primitives + 320px). Add a shared `components/states/` (or `features/.../components/`) loading/empty/error trio first — it does **not** exist yet. Install `sidebar` (pulls `sheet`), `table`, `alert-dialog`, `switch`, `sonner`, `dropdown-menu` via `pnpm dlx shadcn@latest add`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**App shell + navigation**
- **D-01:** Authenticated shell = a collapsible icon-rail sidebar built on the shadcn `sidebar` block (`collapsible="icon"`, expanded/collapsed state persisted in an SSR-readable cookie) + a thin persistent top header that owns the chrome region. Lives in `app/(protected)/layout.tsx` via `SidebarProvider` + `SidebarInset` so it never unmounts on navigation (keeps live query subscriptions alive).
- **D-02:** Primary nav stays single-level / flat (one `SidebarGroup` separator at most) — shadcn's icon-collapse mode does not expand nested `SidebarMenuSub` items (issue #5874). Destinations: Triage, Rules, Billing, Settings (privacy lives inside Settings — see D-08), plus whatever onboarding-state entry is appropriate.
- **D-03:** The chrome region (global pause toggle, credit balance pill, Gmail connection-health indicator) is anchored in the top header, not the sidebar footer (must survive icon-collapse and the mobile offcanvas state). Built from raw shadcn primitives — `badge` for balance, a `tooltip`-wrapped colored dot for health, a `switch` (or `button` + confirm `dialog`) for pause. No wrapper component until the rule-of-three applies. A `DISCONNECTED` health state surfaces a reconnect affordance reusing `ReconnectPrompt` semantics.
- **D-04:** 320px / mobile = the shadcn sidebar's built-in offcanvas `Sheet` mode — no separate responsive nav implementation. `SidebarTrigger` in the header opens it.
- **D-05:** `onboarding/*` keeps a minimal nested layout that suppresses the full app chrome (focused funnel) — it does not render inside the full sidebar shell. The 3-route structure (`gmail-connect` / `template-select` / `complete`) is unchanged; only tokens/shared-states/responsive convergence applies.

**Routing & page layout**
- **D-06:** Triage = a single `/triage` page with shadcn `Tabs` for *Audit log* / *Shadow mode* / *Sender safety net*. The active tab is synced to a `?tab=` searchParam (`useSearchParams`) so each tab is deep-linkable. Shadow-mode is treated as page-level state, not a peer "section."
- **D-07:** Billing = its own `/billing` route (not a `/settings` section). BYOK stays under `/settings`.
- **D-08:** In-product privacy = a `/settings/privacy` segment (its own route) explaining no-stored-bodies / no-auto-send / BYOK. The public legal page at `(public)/privacy` is left untouched (a top-level `(protected)/privacy` route would collide).
- **D-09:** Existing `rules` + `settings` pages slot under `(protected)/layout.tsx` automatically once the shell exists; the convergence pass (tokens + shared loading/empty/error primitives + 320px sanity) applies to each. No flow redesign.

**Chrome data layer & shared state**
- **D-10:** Chrome data (pause state, credit balance, Gmail health) is prefetched in `(protected)/layout.tsx` via `Promise.all` and dehydrated into a `HydrationBoundary` wrapping a `"use client"` `<AppShell>`. Caveat: a query prefetched in the layout must be consumed within the layout subtree (the shell), not relied on by a deeper page boundary.
- **D-11:** Credit balance query gets `refetchInterval ≈ 45s` with `refetchIntervalInBackground: false` and `staleTime ≈ 30s`, plus `invalidateQueries` after billable actions / top-up settle / pause toggle.
- **D-12:** Pause state and Gmail connection health stay invalidate-only (no polling).
- **D-13:** Single source of truth for the pause toggle: one query key (`triageKeys.pauseState()` in `features/triage/query-keys.ts`) and one hook pair — a read hook (`useTriagePauseState()`) and the existing write hook (`useToggleTriagePause()`) — are the only accessors. Chrome toggle, `/settings` toggle, and `PauseBanner` all render off this one cache entry. Optimistic recipe in the mutation hook: `onMutate` → `cancelQueries({ queryKey: triageKeys.pauseState() })` + snapshot + `setQueryData(target)`; `onError` → restore snapshot; `onSettled` → `invalidateQueries` to reconcile (and invalidate the balance key too if pricing cares about pause state).
- **D-14:** No SSE/WebSocket for any chrome data in v1 — polling + invalidation only.

**Billing top-up flow**
- **D-15:** Top-up = a dedicated `/billing/top-up` route (not a modal). Inline step sequence: amount entry → display VietQR + copyable bank-transfer fields (account number, memo/reference code, exact amount) → poll `GET /api/billing/balance` and the intent-status endpoint via `refetchInterval` (stopped once credited/expired) → success state with updated balance. The pending intent rehydrates from `?intentId=` so a refresh resumes the same intent. Expiry handled on-route. No custom stepper component.

**Triage audit log presentation**
- **D-16:** Audit log = a responsive hybrid renderer: shadcn `Table` at `≥ md` (columns: Date/time, Message ref [subject + sender, truncated, links to Gmail if backend gives enough], Rule, Action [`Badge`], Reason [truncated, expandable], Undo [`Button`/disabled]); a card list below `md` (one card per entry — header = Action badge + timestamp, body = message ref + rule + full Reason, footer = Undo button or muted "Undo window closed" note). Shared row model; truncation/Undo logic in one place. The Reason field must never be truncated into invisibility — it's the trust evidence.
- **D-17:** Pagination = cursor "Load older entries" via `useInfiniteQuery` (backend list is cursor-paginated; log is append-only/time-ordered) — not numbered pages. Render a subtle divider/marker where entries cross the 30-day undo boundary.
- **D-18:** Undo UX = within the window, a small outline `Button` "Undo" per entry → an `AlertDialog` confirm that names the exact inverse Gmail change before `POST /api/triage/audit/{auditId}/undo`; on success, invalidate the audit + balance queries and toast. Past the 30-day window — do not hide the affordance; render a muted, non-interactive "Undo window closed" label with a tooltip.

**Cross-cutting (carried forward — not re-decided)**
- Phase 1.3 frontend architecture: route groups `(public)`/`(auth)`/`(protected)`, feature folders `api/` + `components/` + `hooks/`, query-key factories in `features/<feature>/query-keys.ts`, one hook file per use case, shared primitives in `components/ui`, shared infra in `lib/`.
- Phase 1.6 brand identity & design tokens (Teal accent + Paper-warm neutrals + Geist/Be Vietnam Pro/Instrument Serif type stack) are the styling source of truth; theme via `zm-theme` cookie + Server Action, no `localStorage`, no flash.
- Conventions: raw shadcn primitives first (install via `pnpm dlx shadcn@latest add <component>`; don't wrap without rule-of-three); flat folder structure; frontend-design skill invoked before writing any UI (and passed into executor subagents); Vietnamese-first i18n via `next-intl` lock-step vi/en bundles (`pnpm i18n:check` must pass); Playwright-verified in a real browser before declaring done; privacy logging rules client-side.

### Claude's Discretion
- Exact nav icon choices, header layout/spacing details, tab order, settings-page section ordering, ledger table columns, loading-skeleton shapes — left to the planner + frontend-design skill within the locked decisions.
- Whether the `?intentId=` resume is also reachable from a "pending top-up" indicator in the chrome — nice-to-have, planner's call.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope. (Draft-reply UI → Phase 5B; analytics screen + daily digest → Phase 5C; any backend endpoint a 5A screen turns out to need → logged as a gap during planning/execution, not built in 5A.)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WEB-01 | (project requirement — see `.planning/REQUIREMENTS.md`) authenticated web product surface | App shell architecture (shadcn `sidebar` + `SidebarProvider`/`SidebarInset` in `(protected)/layout.tsx`); existing route-group + feature-folder + typed-client structure already in place — see "Architecture Patterns". |
| WEB-02 (5A portion: onboarding / rule CRUD + live preview / triage audit log + undo / billing) | Each via the typed OpenAPI client | Onboarding (3 routes) + rules workspace already exist → convergence pass only. Triage audit+undo: undo endpoint exists; **list endpoint does NOT** → gap (see "Open Questions" + "Environment Availability"). Billing balance + top-up-intent endpoints exist; **ledger-history endpoint does NOT** → gap. |
| WEB-03 | In-product privacy page (no-stored-bodies / no-auto-send / BYOK), vi+en | New `/settings/privacy` route (D-08); copy contract in `05A-UI-SPEC.md` §Copywriting; `next-intl` namespace `privacy.*` added to both bundles + `scripts/check-i18n.ts` `EN_SCAN_FILES`. |
| WEB-04 | Persistent UI region: global pause toggle + real-time credit balance + tenant connection health on every authenticated screen | Top-header chrome (D-03), prefetched in layout (D-10), pause single-source-of-truth (D-13), balance `refetchInterval≈45s`+invalidate (D-11), health/pause invalidate-only (D-12). `MeResponse` already carries `triagePaused` + `gmailConnectionStatus`; `/api/billing/balance` returns `availableCredits`/`heldCredits`/`currency`. |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| App shell render (sidebar + chrome) | Frontend Server (Next RSC layout) | Browser (client `<AppShell>`/`<AppSidebar>`/`useSidebar`) | `(protected)/layout.tsx` is a Server Component (reads `cache()`'d `/me`, the sidebar cookie, prefetches chrome queries); the interactive shell + chrome controls are `"use client"`. |
| Chrome data (pause / balance / health) read | API (Spring `MeController`/`BillingController`/`TenantStatusController`) | Frontend Server (prefetch + dehydrate) → Browser (TanStack Query consumer) | Backend owns the data; layout prefetches for flicker-free first paint; client hooks own polling + invalidation. |
| Pause / shadow-mode / undo / sender-opt-in writes | API (Spring controllers) | Browser (TanStack mutation + optimistic cache) | All state changes are server-authoritative; client does optimistic UI then reconciles via `invalidateQueries`. |
| Top-up intent creation + crediting | API (Spring `BillingController` + SePay webhook) | Browser (poll `/api/billing/balance`) | Backend owns the SePay/VietQR verification and credits the ledger on webhook; UI only displays the intent fields and polls the balance for the credit signal. |
| QR rendering | Browser | — | Backend returns only `qrPayload` (a raw VietQR EMV string), **not** an image URL — the client renders the QR (e.g. a lightweight QR-code component) from that string, or displays the bank-transfer fields and the payload. |
| Routing / deep-linkable tab state | Browser (`useSearchParams` in a client component under `<Suspense>`) | Frontend Server (route segment) | `?tab=` / `?intentId=`/`?code=` are client-read; the page segment is otherwise static-ish but already dynamic because the parent layout reads cookies. |
| i18n message resolution | Frontend Server (`next-intl/server` `getMessages`/`getLocale`) | Browser (`NextIntlClientProvider` → `useTranslations`) | Existing pattern in `(protected)/layout.tsx`; new namespaces added to `i18n/messages/{vi,en}.json` + merged via `merge-feature-i18n.ts`. |

## Standard Stack

### Core (already installed — verified from `apps/web/package.json`, 2026-05-12)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `next` | 16.2.6 | App Router framework | Project-locked; `apps/web` already on it. **Read `node_modules/next/dist/docs/` before writing code — Next 16 has breaking changes vs. training data (per `apps/web/AGENTS.md`).** |
| `react` / `react-dom` | 19.2.6 | UI runtime | Project-locked. |
| `@tanstack/react-query` | 5.100.9 | Server-state, prefetch, optimistic updates | Already the data layer; query-key factories per feature. **v5.90+ changed mutation-callback signatures** — see Pitfalls. |
| `openapi-fetch` | 0.17.0 | Typed backend client | `lib/api/client.ts` wraps it with `createClient<paths>`; `xsrfHeader()` helper for mutating calls. |
| `openapi-typescript` | 7.13.0 | Generates `lib/api/schema.d.ts` | `pnpm generate:api` regenerates from `openapi/openapi.json`; **5A should NOT need to regenerate** (frontend-only) — if it does, that's a backend-gap signal. |
| `next-intl` | ^4.11.1 | vi/en i18n | `i18n/messages/{vi,en}.json` + `merge-feature-i18n.ts` (builds from per-feature `messages.ts`) + `check-i18n.ts` (STRICT gate). |
| `shadcn` (CLI) | ^4.7.0 | Primitive installer | `pnpm dlx shadcn@latest add <component>`; `components.json` preset `base-nova`, `baseColor: neutral`, `cssVariables: true`, `rsc: true`. `components/ui/**` is copied source, ESLint/Prettier-excluded. |
| `lucide-react` | ^1.14.0 | Icons | Already used; nav icons drawn from here. |
| `@base-ui/react` | ^1.4.1 | Headless primitives under shadcn `base-nova` | Pulled in by `base-nova` shadcn components — don't import directly; compose via `@/components/ui/*`. |
| `tailwindcss` | ^4 (v4) | Styling | Token-driven; design tokens in `app/globals.css` `:root`/`.dark`. No arbitrary px for layout gaps (UI-SPEC §Spacing). |

### Primitives to install this phase (shadcn `base-nova`)
| Component | Pulls | Used for |
|-----------|-------|----------|
| `sidebar` | `sheet` (mobile offcanvas), `skeleton`/`separator`/`button`/`input`/`tooltip` (already present) | The whole app shell (D-01/D-04). Provides `SidebarProvider`, `Sidebar`, `SidebarInset`, `SidebarTrigger`, `SidebarHeader/Content/Footer/Group/Menu/MenuItem/MenuButton`, `useSidebar`. |
| `table` | — | Audit log + ledger history at `≥ md` (D-16). |
| `alert-dialog` | — | Undo confirm, remove-protected-sender, pause-confirm, turn-off-shadow-mode confirms (UI-SPEC §Copywriting "Destructive confirmations summary"). |
| `switch` | — | Pause toggle + shadow-mode toggle. |
| `sonner` | — | Toast (undo success, top-up credited, etc.). Mount `<Toaster />` once in the shell. |
| `dropdown-menu` | — | User/account menu in the header (sign out, language, link to settings). |

Already installed (verified `apps/web/components/ui/`): `alert`, `avatar`, `badge`, `button`, `card`, `checkbox`, `dialog`, `input`, `label`, `radio-group`, `separator`, `skeleton`, `tabs`, `textarea`, `toggle-group`, `toggle`, `tooltip`.

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| (QR rendering) | — | Render `qrPayload` (raw VietQR EMV string) as a scannable image | If the planner wants a scannable QR on `/billing/top-up`. Options: a tiny zero-dep QR component, or `qrcode.react` / `react-qr-code` (~`react-qr-code` is dependency-light, MIT). **Flag for the planner:** adding a new runtime dep needs a note; alternatively show only the copyable bank fields + the payload string. Verify the chosen package's version against npm before adding. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| shadcn `sidebar` block | Hand-rolled flex layout + custom mobile drawer | Rejected by D-01/D-04 — reinvents cookie state, offcanvas, accessibility; violates "shadcn-first" convention. |
| Nested `SidebarMenuSub` nav | Flat single-level `SidebarMenu` | Forced by D-02 + shadcn-ui/ui #5874 (sub-menus don't expand in `collapsible="icon"`). |
| `?tab=` searchParam tab state | Sub-routes `/triage/audit`, `/triage/shadow`, `/triage/senders` | Rejected by D-06 — single page + `Tabs` keeps it lighter and the shadow-mode toggle is page-level state. |
| Modal top-up | Dedicated `/billing/top-up` route | Rejected by D-15 — async bank transfer must survive refresh / "come back later". |
| SSE/WebSocket balance | `refetchInterval` + `invalidateQueries` | Rejected by D-14/D-11 — no new infra. |

**Installation:**
```bash
cd apps/web
pnpm dlx shadcn@latest add sidebar table alert-dialog switch sonner dropdown-menu
# (optional, only if a scannable QR is wanted — verify version first)
# pnpm add react-qr-code
```

## Architecture Patterns

### System Architecture Diagram

```
                         Browser (authenticated session cookie + XSRF-TOKEN)
                                          │
                                          ▼
   ┌─────────────────────────────────────────────────────────────────────────┐
   │  Next 16 RSC:  app/(protected)/layout.tsx  (Server Component)            │
   │   • cache()'d getCurrentUser()  ──────────────► /me  (triagePaused,      │
   │   • cookies().get("sidebar_state")                gmailConnectionStatus) │
   │   • const qc = new QueryClient()                                         │
   │   • await Promise.all([                                                  │
   │        qc.prefetchQuery(triageKeys.pauseState() → /me or pause read),    │
   │        qc.prefetchQuery(billingKeys.balance()   → /api/billing/balance), │
   │        qc.prefetchQuery(gmailQueryKeys.status() → /gmail/connection/status)│
   │     ])                                                                   │
   │   • <NextIntlClientProvider> <QueryProvider>                             │
   │       <HydrationBoundary state={dehydrate(qc)}>                          │
   │         <AppShell defaultSidebarOpen={cookieValue}>{children}</AppShell> │
   └───────────────────────────────────────┬─────────────────────────────────┘
                                            ▼
   ┌─────────────────────────────────────────────────────────────────────────┐
   │  "use client"  <AppShell>                                               │
   │   <SidebarProvider defaultOpen={...}>                                    │
   │     <AppSidebar/>           ← flat SidebarMenu: Triage, Rules, Billing,  │
   │                                Settings; active item = usePathname()     │
   │     <SidebarInset>                                                       │
   │        <header h-56px>  [SidebarTrigger] ... [BalancePill][HealthDot]    │
   │                          [PauseSwitch][UserMenu]                         │
   │           ▲ all read TanStack Query cache (hydrated) — never unmounts    │
   │        <main>{children}</main>   ← /triage /billing /rules /settings ... │
   │     </SidebarInset>                                                       │
   │   </SidebarProvider>                                                      │
   └───────────────────────────────────────┬─────────────────────────────────┘
                                            ▼ (page-level client hooks)
   features/triage : useTriagePauseState (read, invalidate-only),
                     useToggleTriagePause (write, optimistic D-13),
                     useTriageAuditLog (useInfiniteQuery — BLOCKED: no list endpoint),
                     useUndoAuditEntry (POST /api/triage/audit/{auditId}/undo),
                     useShadowMode (GET/PUT /api/tenant/triage/shadow-mode),
                     useProtectedSenders (GET /api/triage/sender-safety-net),
                     useOptInSender (POST .../{senderEmail}/opt-in)
   features/billing: useBillingBalance (refetchInterval≈45s, staleTime≈30s, D-11),
                     useCreateTopupIntent (POST /api/billing/topup/intent),
                     useTopupCreditWatch (poll /api/billing/balance until availableCredits rises),
                     useLedgerHistory (useInfiniteQuery — BLOCKED: no ledger endpoint)
   features/gmail  : useTenantStatus (exists; invalidate-only), ReconnectPrompt (reuse)
   features/account: useCurrentUser (exists), useUpdateLanguage (exists)
```
*File-to-implementation mapping is in "Component Responsibilities" below, not the diagram.*

### Recommended Project Structure (additions only — everything else exists)
```
apps/web/
├── app/(protected)/
│   ├── layout.tsx                    # REWRITE: shell host (SidebarProvider+SidebarInset+header+HydrationBoundary)
│   ├── onboarding/layout.tsx         # NEW: minimal chrome-suppressed nested layout (D-05)
│   ├── triage/page.tsx               # NEW: <Suspense><TriagePageClient/></Suspense> (?tab= reader)
│   ├── billing/page.tsx              # NEW: balance + ledger (ledger gap-flagged)
│   ├── billing/top-up/page.tsx       # NEW: <Suspense><TopupClient/></Suspense> (?intentId= / ?code= reader)
│   └── settings/privacy/page.tsx     # NEW: in-product privacy (D-08)
├── components/
│   ├── shell/AppShell.tsx            # NEW "use client"
│   ├── shell/AppSidebar.tsx          # NEW "use client" (flat nav, active = usePathname)
│   ├── shell/ChromeHeader.tsx        # NEW "use client" (or inline in AppShell) — balance pill, health dot, pause switch, user menu
│   └── states/{LoadingState,EmptyState,ErrorState}.tsx  # NEW shared loading/empty/error trio (does NOT exist yet)
├── features/triage/
│   ├── query-keys.ts                 # NEW: triageKeys = { pauseState, auditLog, shadowMode, protectedSenders }
│   ├── api/triage-api.ts             # EXTEND: addAuditLogFns(BLOCKED), undoAuditEntry, get/setShadowMode, getProtectedSenders, optInSender
│   ├── hooks/useTriagePauseState.ts  # NEW (read)
│   ├── hooks/useToggleTriagePause.ts # REWRITE per D-13 (optimistic onMutate/onError/onSettled, key = triageKeys.pauseState())
│   ├── hooks/useTriageAuditLog.ts    # NEW (useInfiniteQuery) — BLOCKED on backend list endpoint
│   ├── hooks/useUndoAuditEntry.ts    # NEW
│   ├── hooks/useShadowMode.ts        # NEW
│   ├── hooks/useProtectedSenders.ts  # NEW
│   ├── hooks/useOptInSender.ts       # NEW
│   ├── components/{AuditLog,AuditTable,AuditCardList,AuditRow,UndoButton,ShadowModeCard,SenderSafetyNetList,SenderRow}.tsx  # NEW
│   ├── components/PauseBanner.tsx     # REBASE onto useTriagePauseState (drop dependency on useCurrentUser for paused state)
│   └── messages.ts                    # EXTEND: triage.* keys
├── features/billing/                  # NEW feature folder
│   ├── api/billing-api.ts             # getBalance, createTopupIntent, (getLedgerHistory — BLOCKED)
│   ├── query-keys.ts                  # billingKeys = { balance, ledger, topupIntent(code) }
│   ├── hooks/useBillingBalance.ts
│   ├── hooks/useCreateTopupIntent.ts
│   ├── hooks/useTopupCreditWatch.ts   # poll balance; stop on credit or expiry
│   ├── hooks/useLedgerHistory.ts      # BLOCKED on backend ledger endpoint
│   ├── components/{BalanceCard,LedgerHistory,LedgerTable,TopupAmountForm,TopupInstructions,CopyableField,TopupSuccess,TopupExpired}.tsx
│   └── messages.ts                    # billing.* keys
├── features/privacy/                  # NEW (or fold copy into a settings/privacy page module)
│   └── messages.ts                    # privacy.* keys
└── scripts/check-i18n.ts              # EXTEND: add new component file paths to EN_SCAN_FILES
```

### Pattern 1: SSR-readable sidebar cookie → `defaultOpen` (D-01)
**What:** The shadcn `sidebar` block persists expand/collapse client-side as a cookie named `sidebar_state` (`"true"`/`"false"`, ~7-day max-age, set by `useSidebar`/`SidebarProvider` on toggle). The Server Component layout reads it so the first paint matches.
**When to use:** In `(protected)/layout.tsx`.
**Example:**
```tsx
// app/(protected)/layout.tsx  — Server Component
import { cookies } from 'next/headers'
// ...
const cookieStore = await cookies()                 // Next 16: cookies() is async
const sidebarOpen = cookieStore.get('sidebar_state')?.value !== 'false'  // default open
// ...
<AppShell defaultSidebarOpen={sidebarOpen}>{children}</AppShell>
// inside AppShell ("use client"):
//   <SidebarProvider defaultOpen={defaultSidebarOpen}> ... </SidebarProvider>
```
[CITED: ui.shadcn.com/docs/components/sidebar — "Persisted State" section] [ASSUMED: exact cookie name `sidebar_state` and the `!== 'false'` default-open convention — verify in the installed `components/ui/sidebar.tsx` after `shadcn add`, the constant is exported as `SIDEBAR_COOKIE_NAME`].

### Pattern 2: Prefetch-in-layout + `HydrationBoundary` + client consumer (D-10)
**What:** Layout creates a throwaway `QueryClient`, prefetches the chrome queries in parallel, dehydrates, wraps the `"use client"` shell in `<HydrationBoundary state={dehydrate(qc)}>`. The shell's client hooks then read from the hydrated cache — no loading flicker for the trust UI.
**Caveat (D-10 + TanStack/query #8479):** a query prefetched **only in the layout** is **not** visible to a `HydrationBoundary` rendered by a deeper page. Anything that needs the prefetched chrome data must live in the **shell subtree** (header/sidebar). Page-level queries (audit log, ledger, rules) prefetch in their own `page.tsx` if SSR-first paint matters, or just fetch client-side.
**Example:**
```tsx
// app/(protected)/layout.tsx
import { QueryClient, dehydrate, HydrationBoundary } from '@tanstack/react-query'
// ...
const qc = new QueryClient()
await Promise.all([
  qc.prefetchQuery({ queryKey: triageKeys.pauseState(), queryFn: () => getMe() /* triagePaused */ }),
  qc.prefetchQuery({ queryKey: billingKeys.balance(),   queryFn: getBillingBalance }),
  qc.prefetchQuery({ queryKey: gmailQueryKeys.status(), queryFn: () => getTenantStatus() }),
])
return (
  <NextIntlClientProvider locale={locale} messages={messages}>
    <QueryProvider>
      <HydrationBoundary state={dehydrate(qc)}>
        <AppShell defaultSidebarOpen={sidebarOpen}>{children}</AppShell>
      </HydrationBoundary>
    </QueryProvider>
  </NextIntlClientProvider>
)
```
Note: `QueryProvider` currently sets a global `staleTime` of 5 min and `gcTime` 30 min. The balance hook overrides `staleTime≈30s` + `refetchInterval≈45s` locally (D-11). Server-prefetched queries should set a small `staleTime` (or rely on the per-query override) so they refetch client-side as intended.
[CITED: tanstack.com/query/latest — Advanced Server Rendering / App Router] [VERIFIED: ctx7 docs /tanstack/query — `prefetchQuery` + `HydrationBoundary` + `dehydrate` example].

### Pattern 3: Single-source-of-truth pause toggle with optimistic update (D-13)
**What:** One query key `triageKeys.pauseState()`; a read hook `useTriagePauseState()` (invalidate-only, no polling — D-12); the write hook `useToggleTriagePause()` does `onMutate` (`cancelQueries` + snapshot + `setQueryData(next)`), `onError` (restore snapshot), `onSettled` (`invalidateQueries` to reconcile; also invalidate `billingKeys.balance()` per D-13). Chrome `<PauseSwitch>`, the `/settings` toggle, and `PauseBanner` all subscribe to `useTriagePauseState()` — **no local `useState`, no ad-hoc `useQuery`**.
**Current state:** `useToggleTriagePause` exists but only does `onSuccess → invalidate accountQueryKeys.me()`; `PauseBanner` reads `useCurrentUser().triagePaused`. Both need rebasing onto `triageKeys.pauseState()`.
**Example:**
```ts
// features/triage/hooks/useToggleTriagePause.ts  (rewrite)
export function useToggleTriagePause() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (paused: boolean) => setTriagePaused(paused),
    onMutate: async (paused) => {
      await qc.cancelQueries({ queryKey: triageKeys.pauseState() })
      const prev = qc.getQueryData<boolean>(triageKeys.pauseState())
      qc.setQueryData(triageKeys.pauseState(), paused)
      return { prev }
    },
    onError: (_e, _v, ctx) => { if (ctx?.prev !== undefined) qc.setQueryData(triageKeys.pauseState(), ctx.prev) },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: triageKeys.pauseState() })
      qc.invalidateQueries({ queryKey: billingKeys.balance() })   // D-13
    },
  })
}
```
[VERIFIED: ctx7 docs /tanstack/query — optimistic-updates guide] [ASSUMED: TanStack 5.100.9 still accepts the `useQueryClient()` + 3-arg-callback form shown above; v5.90 *added* a 4-arg form with `context.client` but did **not** remove the classic form — confirm against the installed version's types, the existing `useToggleTriagePause` already uses the classic form].

### Pattern 4: Deep-linkable tab state via `?tab=` under `<Suspense>` (D-06)
**What:** `/triage/page.tsx` renders `<Suspense fallback={...}><TriagePageClient/></Suspense>`; `TriagePageClient` is `"use client"`, reads `useSearchParams().get('tab')`, drives shadcn `<Tabs value={tab} onValueChange={...}>`, and on change calls `router.replace(\`/triage?tab=${value}\`, { scroll: false })` (or `useRouter` from `next/navigation`).
**Why the `<Suspense>` boundary:** in Next 16, a component calling `useSearchParams()` opts the route into client-side rendering for that subtree and **must** be wrapped in `<Suspense>` or the build errors / the whole page goes dynamic. (Confirm exact behavior in `node_modules/next/dist/docs/` — Next 16 may have relaxed or changed this; the safe pattern is the `<Suspense>` wrapper.)
**Same pattern** for `/billing/top-up/page.tsx` reading `?intentId=` (or `?code=` — see Open Questions) to rehydrate a pending intent.
[ASSUMED: `useSearchParams()` still requires a `<Suspense>` boundary in Next 16 — verify in the bundled Next docs].

### Pattern 5: Typed `openapi-fetch` consumption (existing convention)
**What:** Per-feature `api/<feature>-api.ts` modules call `api.GET/POST/PUT('<path>', {...})` from `lib/api/client.ts`; mutating calls include `...xsrfHeader()` and `'Content-Type': 'application/json'`. Errors: throw `error ?? new Error(...)` on `error || !response.ok`. `lib/api/errors.ts` (client-only, uses next-intl) maps `ApiError.code` to messages — import it only in client components, never RSC/proxy.
**Note on path prefixes:** the generated schema has a **mixed prefix surface** — most paths are `/api/...` but a few are bare (`/me`, `/me/account`, `/me/language`, `/gmail/connection/status`, `/tenant/triage-pause`, `/tenant/disconnect`, `/tenant/connect-gmail`, `/onboarding/select-template`, `/onboarding/complete`). Use the exact string from `schema.d.ts`; the typed client enforces it.
**Example (from existing `gmail-api.ts`):**
```ts
const { data, error, response } = await api.GET('/gmail/connection/status', { signal })
if (error || !response.ok) throw error ?? new Error(`/gmail/connection/status failed: ${response.status}`)
return data as TenantStatus
```
[VERIFIED: codebase — `apps/web/features/gmail/api/gmail-api.ts`, `apps/web/lib/api/client.ts`].

### Pattern 6: `next-intl` new namespace (existing convention)
**What:** Add a `messages.ts` exporting an `en` object to the feature folder; `merge-feature-i18n.ts` merges it into `i18n/messages/en.json`; author the Vietnamese in `i18n/messages/vi.json` (or the feature's vi map) in lock-step; add the component file path to `EN_SCAN_FILES` in `scripts/check-i18n.ts` so the STRICT no-English-literal lint passes; run `pnpm i18n:check`. Namespaces to add: `nav.*`, `shell.*`, `triage.*` (extend), `billing.*`, `privacy.*`.
[VERIFIED: codebase — `apps/web/features/rules/messages.ts`, `apps/web/scripts/check-i18n.ts`, `apps/web/scripts/merge-feature-i18n.ts`].

### Anti-Patterns to Avoid
- **Re-mounting the shell on navigation** — the shell must live in `(protected)/layout.tsx` (not in each page) so `SidebarProvider` state and live query subscriptions survive route changes (D-01).
- **Local `useState` for pause / shadow-mode state shared across components** — use the single query key (D-13). Drift between chrome / settings / banner is a *correctness* bug, not cosmetic.
- **Relying on layout-prefetched queries in a deep page boundary** — TanStack/query #8479; only the shell subtree sees them (D-10).
- **Truncating the audit "Reason" into invisibility** — it's the trust evidence; full text on card renderer and at 320px (D-16, UI-SPEC).
- **Hiding the Undo affordance past 30 days** — render a muted "Undo window closed" label + tooltip (D-18).
- **Importing `lib/api/errors.ts` from a Server Component or `proxy.ts`** — it's `"use client"` + uses next-intl hooks; only client components import it.
- **Adding/modifying any backend endpoint, or regenerating `schema.d.ts` to "make a screen work"** — 5A is frontend-only; missing endpoints are gaps (SPEC out-of-scope rule).
- **Applying the `.zm-proto` / `.zm-auth` clay skin to authenticated screens** — base teal token contract only (UI-SPEC §scope note).
- **Wrapping shadcn primitives without rule-of-three** — chrome widgets are raw `badge` / `switch` / `tooltip`-wrapped dot until composition value is real (D-03, conventions).
- **Hand-rolling a mobile nav drawer** — use the sidebar's built-in offcanvas `Sheet` (D-04).

## Component Responsibilities

| File / module | Responsibility |
|---------------|----------------|
| `app/(protected)/layout.tsx` (rewrite) | Server Component: read `/me` (`cache()`'d), read `sidebar_state` cookie, prefetch `pauseState`/`balance`/`gmailStatus` in parallel, dehydrate, wrap `<AppShell>` in `HydrationBoundary` + `QueryProvider` + `NextIntlClientProvider`. |
| `app/(protected)/onboarding/layout.tsx` (new) | Minimal nested layout — suppresses the full shell chrome (focused funnel, D-05). Inherits `QueryProvider`/`NextIntlClientProvider` from parent? No — parent now renders `<AppShell>{children}`, so the onboarding layout needs to *not* be wrapped by `<AppShell>`'s sidebar. Planner: decide whether `(protected)/layout.tsx` conditionally skips the shell for the onboarding segment, or whether the shell renders children but onboarding's own layout overrides `<main>`/CSS to hide the sidebar (cleanest: detect segment in the layout and render a bare wrapper for `onboarding/*`). |
| `components/shell/AppShell.tsx` (new, "use client") | `SidebarProvider defaultOpen={...}` → `<AppSidebar/>` + `<SidebarInset>` → `<ChromeHeader/>` + `<main>{children}</main>` + `<Toaster/>`. |
| `components/shell/AppSidebar.tsx` (new, "use client") | `collapsible="icon"` `Sidebar`; flat `SidebarMenu`: Triage / Rules / Billing / Settings (+ onboarding-state entry); active item via `usePathname()`; `SidebarHeader` = logo/brand; no `SidebarMenuSub`. |
| `components/shell/ChromeHeader.tsx` (new, "use client") | 56px header: `SidebarTrigger`, page-title slot, `BalancePill` (`useBillingBalance`), `HealthDot` (`useTenantStatus` → `tooltip`-wrapped colored dot; `DISCONNECTED` → `ReconnectPrompt`), `PauseSwitch` (`useTriagePauseState` + `useToggleTriagePause` + confirm `Dialog` on pause-OFF), `UserMenu` (`dropdown-menu`: language, settings, sign out). |
| `components/states/{LoadingState,EmptyState,ErrorState}.tsx` (new) | Shared loading (`Skeleton` rows/cards), empty (heading+body+optional CTA), error (heading+body+"Try again" re-runs query) — used by all new lists and the convergence pass. |
| `features/triage/query-keys.ts` (new) | `triageKeys = { all, pauseState(), auditLog(), shadowMode(), protectedSenders() }`. |
| `features/triage/hooks/*` | `useTriagePauseState` (read), `useToggleTriagePause` (rewrite, optimistic), `useTriageAuditLog` (`useInfiniteQuery` — **BLOCKED**), `useUndoAuditEntry`, `useShadowMode`, `useProtectedSenders`, `useOptInSender`. |
| `features/triage/components/*` | `AuditLog` (responsive switch), `AuditTable`/`AuditCardList` sharing `AuditRow` model + truncation/Undo logic, `UndoButton` + `AlertDialog`, 30-day boundary divider, `ShadowModeCard` (`switch` + confirm-off `AlertDialog`, info badge when on), `SenderSafetyNetList` + `SenderRow` (opt-in control, remove-sender `AlertDialog`). |
| `features/billing/*` (new feature) | `billing-api.ts` (`getBalance` → `/api/billing/balance`, `createTopupIntent` → `/api/billing/topup/intent`, `getLedgerHistory` — **BLOCKED**); `billingKeys`; hooks (`useBillingBalance` w/ `refetchInterval≈45s`+`staleTime≈30s`, `useCreateTopupIntent`, `useTopupCreditWatch`, `useLedgerHistory` — **BLOCKED**); components (`BalanceCard` w/ Display-type figure, `LedgerHistory`/`LedgerTable` — gap-degraded, `TopupAmountForm`, `TopupInstructions` w/ `CopyableField` for account/code/amount + QR render of `qrPayload`, `TopupSuccess`, `TopupExpired`). |
| `app/(protected)/triage/page.tsx` (new) | `<Suspense>` → `TriagePageClient` (`?tab=` reader → `Tabs`). |
| `app/(protected)/billing/page.tsx` (new) | `BalanceCard` + `LedgerHistory` + "Top up credits" CTA → `/billing/top-up`. |
| `app/(protected)/billing/top-up/page.tsx` (new) | `<Suspense>` → `TopupClient` (`?intentId=`/`?code=` reader; amount → instructions → poll → success/expired). |
| `app/(protected)/settings/privacy/page.tsx` (new) | In-product privacy copy (UI-SPEC §Copywriting), link to public `/privacy`, vi+en. |
| Existing pages (`rules`, `settings`, `onboarding/*`) | Convergence pass only: render inside shell (automatic via layout), swap ad-hoc colors/spacing for 1.6 tokens, adopt shared loading/empty/error primitives, 320px no-horizontal-scroll sanity. No flow redesign (D-09, D-05). |

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Collapsible sidebar + icon rail + mobile drawer + persisted state | Custom layout + drawer + cookie code | shadcn `sidebar` block (`SidebarProvider`/`SidebarInset`/`SidebarTrigger`/`useSidebar`, `collapsible="icon"`, built-in `Sheet` + `sidebar_state` cookie) | Accessibility, keyboard, focus trap, offcanvas, cookie state all solved; D-01/D-04 mandate it. |
| Toasts | Custom toast queue/portal | shadcn `sonner` (`<Toaster/>` + `toast()`) | Standard; one mount in the shell. |
| Confirm dialogs | Custom modal + focus management | shadcn `alert-dialog` | Used for all destructive confirms (undo, remove sender, pause, turn-off-shadow). |
| Data table | Custom `<table>` + responsive logic from scratch | shadcn `table` + a hand-written responsive switch to a card list (D-16) | The *primitive* is shadcn; only the table↔card switch and shared row model are project code. |
| Server-state cache, polling, optimistic updates, SSR hydration | Custom fetch + `useState` + `useEffect` polling | TanStack Query v5 (`useQuery`/`useInfiniteQuery`/`useMutation`, `refetchInterval`, `HydrationBoundary`, `invalidateQueries`) | Already the project's data layer; D-10/D-11/D-13 are expressed in its primitives. |
| Typed API calls | Hand-written `fetch` to backend routes | `lib/api/client.ts` (`openapi-fetch` `createClient<paths>`) + per-feature `api/` modules | Project convention; gives path + body type-checking; ad-hoc `fetch` is forbidden. |
| i18n plumbing | Custom string-table | `next-intl` + the existing `merge-feature-i18n.ts` / `check-i18n.ts` pipeline | Lock-step vi/en + STRICT lint already wired. |
| QR rendering (if a scannable image is wanted) | Hand-rolled EMV-string→QR matrix | A small MIT QR component (`react-qr-code` or equivalent) — *flag the new dep for the planner* | QR encoding is fiddly; backend gives only the raw `qrPayload` string. |

**Key insight:** Almost nothing in 5A is genuinely novel — it's assembly of shadcn primitives + TanStack Query + the existing typed client into the locked shell/route structure. The only "real" engineering is (a) the responsive audit-table↔card renderer with a shared row model, (b) the single-source-of-truth pause-state refactor, (c) the top-up poll-until-credited flow against a backend that exposes only `balance` (no intent-status endpoint), and (d) graceful degradation around the three missing endpoints.

## Common Pitfalls

### Pitfall 1: TanStack Query v5.90+ mutation-callback signature change
**What goes wrong:** Newer TanStack docs/examples show `onMutate: async (variables, context) => { await context.client.cancelQueries(...) }` and `onError: (err, vars, onMutateResult, context) => ...` (4 args, `context.client`). The existing codebase (`useToggleTriagePause`) uses the classic `useQueryClient()` + 3-arg form. Mixing them, or copying a v5.90 example wholesale into a 5.100.9 codebase that elsewhere uses the classic form, produces inconsistent code and possible type errors.
**Why it happens:** v5.90 *added* the new form; both still work, but docs lean toward the new one.
**How to avoid:** Pick the classic form (`useQueryClient()` + `qc.xxx`, 3-arg callbacks) to match the existing `useToggleTriagePause` — or, if migrating, migrate consistently. Verify against `node_modules/@tanstack/react-query`'s `.d.ts`.
**Warning signs:** `Property 'client' does not exist on type 'MutationContext'`, or two hooks in `features/triage` using different callback shapes.

### Pitfall 2: `useSearchParams()` without `<Suspense>` → build error / forced dynamic
**What goes wrong:** A client component reading `useSearchParams()` (the `?tab=` / `?intentId=` readers) that isn't wrapped in `<Suspense>` either fails the build or de-opts the whole route to dynamic.
**How to avoid:** Always render the search-param-reading client component inside `<Suspense fallback={...}>` in the `page.tsx`. (Re-check Next 16's exact rule in `node_modules/next/dist/docs/` — it may have changed; the `<Suspense>` wrapper is the safe default.)
**Warning signs:** `useSearchParams() should be wrapped in a suspense boundary` build error.

### Pitfall 3: Hydration mismatch on cookie-driven sidebar state
**What goes wrong:** Server renders the sidebar from the `sidebar_state` cookie; client's `SidebarProvider` has its own default → mismatch → flash/warning.
**How to avoid:** Pass the cookie-derived value as `defaultOpen` to `SidebarProvider` (Pattern 1). Don't let the client compute its own initial state from `document.cookie` in an effect.
**Warning signs:** `Hydration failed because the server rendered HTML didn't match` around the sidebar; sidebar flips open→closed on first paint.

### Pitfall 4: Layout-prefetched query not seen by a deeper page boundary (TanStack/query #8479)
**What goes wrong:** Planner prefetches `billingKeys.balance()` in `(protected)/layout.tsx`, then a `/billing/page.tsx` `HydrationBoundary` doesn't see it → balance refetches client-side / flickers there.
**How to avoid:** Consume layout-prefetched chrome queries **only in the shell subtree** (header). Page-level data prefetches in its own `page.tsx`. (D-10 already calls this out.)
**Warning signs:** Balance pill is instant in the header but the `/billing` balance card spins.

### Pitfall 5: `QueryProvider`'s 5-min global `staleTime` swallows the 45s balance refetch
**What goes wrong:** `lib/query-client.tsx` sets `staleTime: 5 min` globally; a balance hook that only sets `refetchInterval: 45s` but not `staleTime` may still serve stale cache and skip the interval refetch in some cases (interval refetch fires regardless of staleTime, but focus refetch / mount refetch won't).
**How to avoid:** In `useBillingBalance`, explicitly set `staleTime ≈ 30s` (D-11) so the interplay is as designed. Also set a small `staleTime` on the *server-prefetched* balance query (or accept that it's immediately stale on the client, which is fine — it'll refetch).
**Warning signs:** Balance doesn't update for 5 minutes after a top-up despite the interval.

### Pitfall 6: Privacy-logging / data-exposure rules client-side
**What goes wrong:** Audit-log rows want to show subject/sender and link to Gmail; ledger rows want detail — easy to log these to the console or render fields the backend didn't intend as owner-visible.
**How to avoid:** Render only fields the backend explicitly returns in the response DTOs; no `console.log` of audit/ledger/message data; the audit "Message ref" links to Gmail only if the backend supplies enough (a Gmail message id / thread id) — otherwise show subject+sender text only. (CLAUDE.md privacy logging format + UI-SPEC.)
**Warning signs:** `console.log(auditEntry)` in a component; rendering a field not present in `schema.d.ts`.

### Pitfall 7: i18n parity break / STRICT lint failure
**What goes wrong:** New strings added to `en` but not `vi` (or vice-versa), or a new component with English literals not added to `EN_SCAN_FILES` → `pnpm i18n:check` fails / lint-staged blocks the commit.
**How to avoid:** For every new UI component: add its strings to a feature `messages.ts` (en) + `i18n/messages/vi.json` (vi) in lock-step, add the component path to `EN_SCAN_FILES`, run `pnpm i18n:build && pnpm i18n:check` before committing.
**Warning signs:** `i18n:check` reports missing keys or unexpected English literals.

### Pitfall 8: Onboarding accidentally rendered inside the full shell
**What goes wrong:** Putting the shell in `(protected)/layout.tsx` makes *every* protected page (including `onboarding/*`) render inside the sidebar — but D-05 wants onboarding chrome-suppressed.
**How to avoid:** In `(protected)/layout.tsx`, branch on the route segment (or use a nested `onboarding/layout.tsx` that the parent respects) so `onboarding/*` gets a bare focused wrapper, not `<AppShell>`. The simplest reliable approach: detect the segment in the parent layout and conditionally render `<AppShell>{children}</AppShell>` vs. a minimal wrapper.
**Warning signs:** Sidebar visible during the onboarding funnel; onboarding e2e specs break.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `cookies()` / `headers()` sync in Server Components | `await cookies()` / `await headers()` (async) | Next 15 → 16 | Layout must `await cookies()` to read `sidebar_state`. |
| TanStack Query mutation callbacks `(vars)` / `(err, vars, ctx)` only | Also accept `(vars, context)` / `(err, vars, onMutateResult, context)` with `context.client` | TanStack Query v5.90 | Both forms valid; pick one consistently — see Pitfall 1. |
| shadcn sidebar = ad-hoc per-project | First-class `sidebar` block with `SidebarProvider`/`SidebarInset`/`useSidebar` + cookie state | shadcn (current) | Use it as-is; don't reinvent. |
| Pages own their own chrome (`PauseBanner` mounted in `(protected)/layout.tsx` passthrough) | Persistent shell in `(protected)/layout.tsx` owning chrome | This phase (5A) | Existing `(protected)/layout.tsx` passthrough + `PauseBanner` mount gets replaced; `PauseBanner` rebased onto the shared pause query key. |

**Deprecated/outdated:**
- The current `(protected)/layout.tsx` "bare passthrough + `PauseBanner`" — explicitly a placeholder ("ProtectedHeader will land lazily once Phase 5 has actual chrome"); 5A is that phase. Replace it.
- `useToggleTriagePause`'s `onSuccess → invalidate accountQueryKeys.me()` — replace with the D-13 optimistic recipe keyed on `triageKeys.pauseState()`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | shadcn `sidebar` persists state in a cookie literally named `sidebar_state` with a `"true"`/`"false"` value and ~7-day max-age; layout reads it for `defaultOpen` | Pattern 1 | LOW — verify in the installed `components/ui/sidebar.tsx` (`SIDEBAR_COOKIE_NAME`); only affects the cookie-read line. |
| A2 | `useSearchParams()` in Next 16 still requires (or strongly benefits from) a `<Suspense>` boundary | Pattern 4, Pitfall 2 | LOW — worst case the `<Suspense>` wrapper is harmless even if not strictly required; verify in `node_modules/next/dist/docs/`. |
| A3 | TanStack Query 5.100.9 still accepts the classic `useQueryClient()` + 3-arg-callback mutation form | Pattern 3, Pitfall 1 | LOW — the existing codebase already uses it; v5.90 added, didn't remove. |
| A4 | The triage-audit **list** endpoint, the top-up **intent-status** endpoint, an `intentId` field, the billing **ledger-history** list endpoint, and a QR **image URL** are genuinely absent from the current backend OpenAPI surface (not just missing from the committed `apps/web/openapi/openapi.json` / `lib/api/schema.d.ts`) | Open Questions, Environment Availability | **HIGH** — if these actually exist on the backend, the planner should request a `pnpm generate:api` refresh and the screens are buildable in full. If they truly don't exist, those sub-requirements are blocked-on-backend gaps. **The planner MUST confirm with the user / by inspecting `backend/api` controllers before sequencing.** |
| A5 | `MeResponse.triagePaused` + `MeResponse.gmailConnectionStatus` are authoritative enough to back the chrome pause toggle and health dot (so the chrome can prefetch `/me` rather than separate pause/health endpoints) | Pattern 2, Component Responsibilities | LOW-MEDIUM — there's also a dedicated `/gmail/connection/status` (`GmailConnectionStatusResponse`) and a `/tenant/triage-pause` PUT (write only, no GET). Planner: prefer `/me` for the read since it carries both; reconcile after mutations by invalidating. Confirm `/me` is the intended read source. |
| A6 | Top-up "credited" is detectable only by polling `/api/billing/balance` and watching `availableCredits` rise (no per-intent status endpoint) | Pattern 4 (billing), D-15 note | MEDIUM — if a status endpoint exists, use it (cleaner: stop on `credited`/`expired`). With only `balance`, the watch must also stop on the intent's `expiresAt` and handle the ambiguous "balance rose for an unrelated reason" case (rare in beta). |

**If A4/A6 resolve "the endpoints exist":** 5A is fully buildable; just refresh the generated client. **If they resolve "they don't":** the audit-list, ledger-history, and intent-id-rehydration pieces become explicitly-flagged blocked-on-backend gaps per the SPEC out-of-scope rule, and the screens ship the parts that work (undo flow, empty/error states, balance, top-up-by-code).

## Open Questions

1. **Does a triage-audit *list* endpoint exist on the backend?**
   - What we know: `apps/web/openapi/openapi.json` and `lib/api/schema.d.ts` expose **only** `POST /api/triage/audit/{auditId}/undo`. `04-SPEC.md` (Phase 4) describes audit/undo semantics — check whether it shipped a `GET /api/triage/audit` list.
   - What's unclear: whether the list endpoint exists but wasn't regenerated into the committed schema, or genuinely doesn't exist.
   - Recommendation: Planner inspects `backend/api` (`TriageAuditController`) and/or asks the user. If it exists → `pnpm generate:api`, build the full audit list (D-16/D-17). If not → build the `/triage` page with the Audit-log tab showing an "audit history not yet available" state + the shadow-mode and sender-safety-net tabs (which *do* have endpoints) fully working, and log the list endpoint as a gap. The undo endpoint alone can't drive a useful list, so the list itself is the blocked piece.

2. **Does a billing *ledger/transaction-history* list endpoint exist?**
   - What we know: schema exposes `GET /api/billing/balance` (`availableCredits`/`heldCredits`/`currency`) and `POST /api/billing/topup/intent` (`code`/`amountVnd`/`expiresAt`/`qrPayload`) — no ledger/history list.
   - Recommendation: Planner inspects `backend/api` (`BillingController`) / `02B` phase output. If it exists → build `LedgerHistory` (`useInfiniteQuery`) per UI-SPEC. If not → `/billing` ships balance + top-up + an empty/"transaction history coming soon" panel; log as a gap (SPEC explicitly allows this: "if a needed list endpoint does not exist, it is logged as a gap rather than built").

3. **Does the top-up intent response carry an `intentId` (for `?intentId=` rehydration per D-15), or only `code`?**
   - What we know: `TopupIntentResponse = { code?, amountVnd?, expiresAt?, qrPayload? }` — no `intentId`. There's no intent-status GET endpoint either.
   - Recommendation: Use `?code=` as the rehydration handle (the `code` is the bank-transfer memo, unique per intent, and the only stable identifier the response gives). On rehydration with `?code=`, the client can't re-fetch the intent (no GET) — so it must either (a) keep the intent fields in `sessionStorage` keyed by `code` (acceptable — these aren't secrets, just bank-transfer instructions), or (b) re-display only the `code` + amount and tell the user to check `/billing` for the credited balance. Polling for the credit signal = `/api/billing/balance`. Flag this as a UX-degradation gap if a GET-intent endpoint would be cleaner; do **not** build the endpoint.

4. **What identifiers does an audit entry expose for the Gmail deep-link and the inverse-action description?**
   - What we know: the audit response shape isn't in the committed schema (no list endpoint). UI-SPEC D-16/D-18 assume subject + sender (truncated) and a backend-computed inverse-action string.
   - Recommendation: resolves with Q1 — once the list endpoint/shape is known, the audit row model and the `AlertDialog` copy bind to whatever fields exist; if a Gmail message id is present, link out; if not, text only (privacy default).

5. **Onboarding shell-suppression mechanism — parent-layout branch vs. nested layout override?**
   - Recommendation: Planner's call; the parent-layout segment-branch is simplest and most robust (Pitfall 8). Document the choice in the plan.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| `node_modules/next/dist/docs/` (bundled Next 16 docs) | Verifying Next 16 APIs before coding (per `apps/web/AGENTS.md`) | ✓ (installed with `next`) | 16.2.6 | — |
| shadcn CLI (`pnpm dlx shadcn@latest`) | Installing `sidebar`/`table`/`alert-dialog`/`switch`/`sonner`/`dropdown-menu` | ✓ | `shadcn` ^4.7.0 in devDeps; `components.json` configured | — |
| Backend running (`http://localhost:8080`, `NEXT_PUBLIC_API_BASE`) | Playwright e2e against real endpoints; manual verification | ✓ assumed (dev VPS / local) | — | Playwright can mock/route-fulfill responses for endpoints (and is *required* to for the missing ones) — see Validation Architecture. |
| Triage-audit **list** endpoint | REQ #5 audit list | ✗ (not in committed OpenAPI schema) | — | Build the rest of `/triage`; log the list endpoint as a backend gap; audit tab shows an "unavailable" state. |
| Billing **ledger-history** endpoint | REQ #7 ledger history | ✗ (not in committed OpenAPI schema) | — | `/billing` ships balance + top-up; ledger panel shows empty/"coming soon"; log as a gap (SPEC allows). |
| Top-up **intent-status** endpoint / `intentId` field | D-15 `?intentId=` rehydration + clean credit detection | ✗ | — | Use `?code=` + `sessionStorage` for rehydration; poll `/api/billing/balance` for the credit signal; flag the degradation. |
| QR **image URL** in the top-up response | A scannable QR on `/billing/top-up` | ✗ (only `qrPayload` raw string) | — | Render the QR client-side from `qrPayload` with a small MIT QR component (new dep — flag for the planner), or show only the copyable bank fields + the payload string. |
| `react-qr-code` (or equivalent) | Optional scannable QR rendering | ✗ (not installed) | — | Don't add unless the planner decides a scannable QR is in scope; bank-transfer fields alone satisfy the SPEC ("display the VietQR / bank-transfer instructions"). Verify version against npm before adding. |

**Missing dependencies with no fallback:** none — every gap has a documented degradation path; nothing *blocks* the phase, it just narrows the scope of three sub-requirements (all explicitly allowed to be logged as gaps by the SPEC).

**Missing dependencies with fallback:** the three missing backend endpoints + the QR image URL + the optional QR library — all handled above.

## Validation Architecture

> `workflow.nyquist_validation` — `.planning/config.json` not checked exhaustively; treat as enabled. This is a **frontend-only UI phase**, so the validation weight is: standard frontend gates (`tsc`, ESLint, Vitest, `i18n:check`) + Playwright e2e for golden paths + key states on desktop + 320px + a `frontend-design` visual-review note per screen. No heavy backend test scaffolding is warranted; Vitest covers hook/component contracts, Playwright covers behavior/visuals (the project's stated bar — "type-check passing is not enough", `apps/web/CLAUDE.md` UX rule).

### Test Framework
| Property | Value |
|----------|-------|
| Unit/component framework | Vitest 4.1.5 + `@testing-library/react` 16.3.2 + `jsdom` 29.1.1 + `@testing-library/jest-dom` 6.9.1 |
| Vitest config | `apps/web/vitest.config.ts` (setup `apps/web/__tests__/setup.ts`) |
| Vitest quick run | `pnpm --filter web test` (= `vitest run`) |
| E2E framework | Playwright (`@playwright/test`) — config `apps/web/playwright.config.ts`, specs in `apps/web/e2e/**` |
| E2E run | `pnpm --filter web test:e2e` (= `node ../../node_modules/@playwright/test/cli.js test`) |
| Lint | `pnpm --filter web lint` (ESLint 9, `eslint-config-next`) |
| Typecheck | `pnpm --filter web typecheck` (`tsc --noEmit`) |
| i18n parity | `pnpm --filter web i18n:check` (STRICT — `scripts/check-i18n.ts`) |
| Full suite (phase gate) | `pnpm --filter web typecheck && pnpm --filter web lint && pnpm --filter web test && pnpm --filter web i18n:check && pnpm --filter web test:e2e` |

### Phase Requirements → Test Map
| Req | Behavior | Test type | Automated command | Exists? |
|-----|----------|-----------|-------------------|---------|
| WEB-04 | Shell renders on every `(protected)` route; pause/balance/health visible (no scroll) at desktop + 320px | Playwright | `e2e/app-shell.spec.ts` (navigate to /triage, /billing, /rules, /settings; assert chrome widgets visible at 1280px and 320px) | ❌ Wave 0 |
| WEB-04 | Pause toggle from chrome persists + reflects without reload; consistent with settings toggle + `PauseBanner` | Playwright + Vitest | `e2e/pause-toggle.spec.ts`; `features/triage/hooks/useToggleTriagePause.test.tsx` (extend — optimistic + rollback + single-key) | ⚠️ partial (`useToggleTriagePause.test.tsx` exists, needs rewrite for D-13) |
| WEB-04 | Balance renders from `/api/billing/balance`, refetches ~45s, updates after simulated top-up credit | Playwright (route-fulfill balance, advance/poll) + Vitest | `e2e/billing-balance.spec.ts`; `features/billing/hooks/useBillingBalance.test.tsx` | ❌ Wave 0 |
| WEB-04 | Health: healthy on `CONNECTED`, degraded + reconnect on `DISCONNECTED` | Playwright (route-fulfill `/gmail/connection/status` both ways) | `e2e/connection-health.spec.ts` | ❌ Wave 0 |
| WEB-02 | Audit list at 0 / 1 / page-full; Undo on in-window entry calls undo + updates; out-of-window shows no Undo | Playwright (route-fulfill audit list — **mocked, since no real endpoint**) + Vitest (`AuditRow`/`AuditLog`) | `e2e/triage-audit.spec.ts`; `features/triage/components/AuditLog.test.tsx` | ❌ Wave 0 — **note: e2e mocks the list endpoint; flag the dependency** |
| WEB-02 | Shadow-mode toggle reads/writes `/api/tenant/triage/shadow-mode`; sender list renders (incl. empty); opt-in calls endpoint + updates row | Playwright + Vitest | `e2e/triage-shadow-senders.spec.ts`; `features/triage/components/SenderSafetyNetList.test.tsx` | ❌ Wave 0 |
| WEB-02 | Billing: balance shown; top-up → intent → instructions → simulated credit → success + balance up; ledger renders empty + populated | Playwright (route-fulfill `topup/intent` + `balance`; ledger mocked if no endpoint) | `e2e/billing-topup.spec.ts` | ❌ Wave 0 |
| WEB-03 | Privacy page exists at an authenticated route, linked from shell, renders vi + en, states no-stored-bodies / no-auto-send / BYOK | Playwright (visit `/settings/privacy`, assert the three points; switch locale) + Vitest (i18n parity) | `e2e/privacy-page.spec.ts`; covered partly by `__tests__/i18n/messages.contract.test.ts` | ❌ Wave 0 (e2e); contract test extends |
| convergence | Existing screens (rules, onboarding ×3, settings) render inside shell, on 1.6 tokens, shared loading/empty/error, no horizontal scroll at 320px | Playwright (existing `e2e/rules.spec.ts`, `e2e/onboarding-routes.spec.ts`, `e2e/byok.spec.ts` — extend with 320px + in-shell assertions) | extend existing specs | ⚠️ extend |
| all | `tsc` / ESLint / Vitest / `i18n:check` green | CI gates | full-suite command above | ✓ infra exists |

### Sampling Rate
- **Per task commit:** `pnpm --filter web typecheck && pnpm --filter web lint` + the touched feature's Vitest file(s).
- **Per wave merge:** `pnpm --filter web test && pnpm --filter web i18n:check` + the relevant Playwright spec(s).
- **Phase gate:** full suite green (`typecheck` + `lint` + `test` + `i18n:check` + `test:e2e`) + a `frontend-design` visual-review note recorded for each authenticated screen (shell, /triage all tabs, /billing, /billing/top-up all states, /settings/privacy, and the converged rules/onboarding/settings).

### Wave 0 Gaps
- [ ] `components/states/{LoadingState,EmptyState,ErrorState}.tsx` — shared loading/empty/error trio (consumed by every new list + the convergence pass) — **build first**.
- [ ] `features/triage/query-keys.ts` — `triageKeys` factory (does not exist; `features/triage` currently has no `query-keys.ts`).
- [ ] `features/billing/` — entire feature folder is new (`api/`, `query-keys.ts`, `hooks/`, `components/`, `messages.ts`).
- [ ] Playwright specs: `e2e/app-shell.spec.ts`, `e2e/pause-toggle.spec.ts`, `e2e/billing-balance.spec.ts`, `e2e/connection-health.spec.ts`, `e2e/triage-audit.spec.ts`, `e2e/triage-shadow-senders.spec.ts`, `e2e/billing-topup.spec.ts`, `e2e/privacy-page.spec.ts` (+ 320px viewport project or per-spec viewport overrides — check `playwright.config.ts` for existing mobile projects).
- [ ] Vitest specs: `useToggleTriagePause.test.tsx` (rewrite for D-13), `useBillingBalance.test.tsx`, `AuditLog.test.tsx`, `SenderSafetyNetList.test.tsx`, `useTopupCreditWatch.test.tsx`.
- [ ] Extend `scripts/check-i18n.ts` `EN_SCAN_FILES` with every new English-literal-bearing component.
- [ ] Confirm `playwright.config.ts` has (or add) a 320px viewport for the responsive-floor assertions.

## Project Constraints (from CLAUDE.md / `apps/web/AGENTS.md` / CONVENTIONS.md)

- **Read `node_modules/next/dist/docs/` before writing any Next 16 code** — Next 16 has breaking changes vs. training data (`apps/web/AGENTS.md`). Heed deprecation notices.
- **shadcn-first** — check shadcn for any primitive; install via `pnpm dlx shadcn@latest add <component>` from `apps/web`; compose around `@/components/ui/*`; `components/ui/**` is copied source, ESLint/Prettier-excluded; don't hand-roll primitives shadcn provides; don't wrap primitives without rule-of-three (CLAUDE.md convention 7, `apps/web/AGENTS.md`).
- **Feature-folder layout** — `features/<feature>/{api/<feature>-api.ts, query-keys.ts (only if the feature owns cached data), hooks/ (one file per use case), components/, messages.ts}`; Playwright specs only in `apps/web/e2e/**`; Vitest feature tests beside feature code or in `apps/web/__tests__/**` (convention 8).
- **Subproject-owned config** — web config lives under `apps/web` (convention 9).
- **i18n** — all new visible strings via `next-intl` keys with lock-step vi + en; `pnpm i18n:check` must pass; add component paths to `EN_SCAN_FILES`.
- **Privacy logging / data exposure** — no email bodies, addresses, prompts, completions, or token bytes rendered or logged client-side beyond what the backend explicitly returns as owner-visible fields (CLAUDE.md privacy constraint + privacy logging format).
- **Typed client only** — all backend access through `lib/api` + feature `api/` modules; no ad-hoc `fetch` to backend routes.
- **Design tokens** — Phase 1.6 base teal token contract is the styling source of truth on authenticated screens; no `.zm-proto`/`.zm-auth` clay skin; no hard-coded ad-hoc colors/spacing after the convergence pass; theme via `zm-theme` cookie + Server Action, no `localStorage`, no flash; both light + dark must read correctly.
- **`frontend-design` skill** — invoke before writing any UI; pass the rule into any executor subagent that touches frontend (MEMORY rule + CLAUDE.md UX directive).
- **Verify in a real browser** — type-check passing is not enough; Playwright golden path + key states, desktop + 320px, before declaring done.
- **GSD workflow** — file edits go through a GSD command (`/gsd-execute-phase` etc.), not direct repo edits.
- **No new/modified backend endpoint** — 5A is frontend-only; missing endpoints are logged as gaps, not built.
- **Vietnamese-first communication** in prose (MEMORY rule) — but code/class-names/technical terms stay English.

## Sources

### Primary (HIGH confidence)
- Codebase — `apps/web/` (`package.json`, `lib/api/{client.ts,schema.d.ts,base-url.ts}`, `openapi/openapi.json`, `lib/query-client.tsx`, `app/(protected)/layout.tsx`, `features/{triage,gmail,account,rules,llm}/...`, `scripts/check-i18n.ts`, `components/ui/*`, `playwright.config.ts`, `e2e/*`, `apps/web/AGENTS.md`/`CLAUDE.md`) — verified 2026-05-12.
- `.planning/phases/05A-user-surface-web-ui-core/{05A-SPEC.md, 05A-CONTEXT.md, 05A-UI-SPEC.md}` — locked requirements/decisions/visual contract.
- `CLAUDE.md`, `apps/web/AGENTS.md` (CLAUDE.md proxies it) — project constraints, conventions, do-not-use list, tooling.
- ctx7 docs `/tanstack/query` — Advanced Server Rendering (App Router) `prefetchQuery`+`HydrationBoundary`+`dehydrate`; optimistic-updates / mutations guides — fetched 2026-05-12.
- ctx7 docs `/shadcn-ui/ui` — `sidebar` component (`SidebarProvider`/`SidebarInset`/`SidebarTrigger`/`useSidebar`, `collapsible="icon"|"offcanvas"|"none"`, `variant="inset"`, `group-data-[collapsible=icon]:hidden`) — fetched 2026-05-12.

### Secondary (MEDIUM confidence)
- shadcn-ui/ui issue #5874 (referenced in CONTEXT D-02) — `SidebarMenuSub` doesn't expand in `collapsible="icon"` mode. Not independently re-verified this session; treated as authoritative because the decision is already locked around it.
- TanStack/query issue #8479 (referenced in CONTEXT D-10) — layout-prefetched query not seen by a deeper page `HydrationBoundary`. Not independently re-verified; locked-decision basis.

### Tertiary (LOW confidence — verify before relying)
- Exact shadcn sidebar cookie name/format (`sidebar_state`, `"true"`/`"false"`, ~7-day max-age) — training knowledge; verify in the installed `components/ui/sidebar.tsx` (`SIDEBAR_COOKIE_NAME` / `SIDEBAR_COOKIE_MAX_AGE`).
- `useSearchParams()` `<Suspense>` requirement specifics in Next 16 — verify in `node_modules/next/dist/docs/`.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions read directly from `apps/web/package.json` (2026-05-12); shadcn/TanStack/Next behaviors cross-checked against ctx7 docs.
- Architecture: HIGH — every architectural decision is pre-locked in `05A-CONTEXT.md`/`05A-UI-SPEC.md`; this research mostly maps decisions onto the existing code and flags execution risks.
- Pitfalls: MEDIUM-HIGH — drawn from the locked decisions' own caveats (D-10/#8479, D-02/#5874), TanStack v5.90 API change (verified via ctx7), Next 16 async-cookies (well-established), and codebase realities (global staleTime, i18n STRICT gate).
- **Backend-surface gaps: the one real unknown** — A4/A6 (audit-list / ledger-history / intent-status endpoints) MUST be confirmed against `backend/api` before sequencing; if they exist, scope widens; if not, three sub-requirements become flagged gaps per the SPEC's own rule.

**Research date:** 2026-05-12
**Valid until:** ~2026-06-11 (30 days — stable stack; re-check only if `apps/web` deps bump or the backend OpenAPI surface changes).
