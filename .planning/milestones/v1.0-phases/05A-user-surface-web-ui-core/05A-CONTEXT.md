# Phase 5A: User Surface — Web UI Core - Context

**Gathered:** 2026-05-12
**Status:** Ready for planning

<domain>
## Phase Boundary

`apps/web` (Next.js 16 / React 19) becomes a coherent authenticated product: a new authenticated app shell ties together every already-built backend flow — onboarding, rule CRUD + live preview, triage audit log + undo + shadow mode + sender safety net, billing balance + top-up + ledger history, an in-product privacy page — all consuming the existing typed OpenAPI client. The shell persistently surfaces the global pause toggle, live credit balance, and Gmail connection health on every authenticated screen. A convergence pass brings the existing authenticated screens (rules, onboarding, settings) onto the new shell + Phase 1.6 design tokens + shared loading/empty/error primitives, responsive to 320px (no flow redesign). Draft-reply and analytics screens are explicitly NOT in this phase (backends don't exist — Phases 5B/5C).

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**8 requirements are locked.** See `05A-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `05A-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- New authenticated app shell for all `(protected)` routes (header / sidebar + persistent chrome region)
- Persistent chrome widgets: global pause toggle, live credit balance (polled + invalidated), Gmail connection-health indicator
- New `features/triage` UI: audit log + undo, shadow-mode toggle, sender-safety-net management
- New `features/billing` UI: balance, top-up flow (intent → VietQR/bank-transfer instructions → poll → success), paginated ledger history
- New in-product privacy page (authenticated, distinct from public `/privacy`)
- Convergence pass on existing authenticated screens (rules, onboarding ×3, settings): render inside the new shell, use Phase 1.6 design tokens, use shared loading/empty/error primitives, responsive sanity pass to 320px — no flow redesign
- vi/en i18n parity for all new strings; typed OpenAPI client consumption; standard frontend gates green; Playwright e2e for golden paths + key states on desktop + 320px; a frontend-design visual-review note per screen

**Out of scope (from SPEC.md):**
- Public / marketing surfaces (landing `/`, `/docs`, public `/terms`, public `/privacy`, `(auth)/login`) — owned by Phase 1.6
- AI draft-reply UI / draft-review screen — Phase 5B; backend does not exist
- Analytics screen and daily-digest UI — Phase 5C; backend does not exist
- Any new or modified backend endpoint — 5A is frontend-only against the existing OpenAPI surface; missing endpoints are logged as gaps, not built
- Onboarding flow redesign — the 3-route structure and behavior are unchanged; only shell/token/state/responsive integration
- Real-time transport (websockets/SSE) for balance or any other data — polling + cache invalidation only

</spec_lock>

<decisions>
## Implementation Decisions

### App shell + navigation
- **D-01:** Authenticated shell = a **collapsible icon-rail sidebar** built on the shadcn `sidebar` block (`collapsible="icon"`, expanded/collapsed state persisted in an SSR-readable cookie) + a **thin persistent top header** that owns the chrome region. Lives in `app/(protected)/layout.tsx` via `SidebarProvider` + `SidebarInset` so it never unmounts on navigation (keeps live query subscriptions alive).
- **D-02:** Primary nav stays **single-level / flat** (one `SidebarGroup` separator at most) — shadcn's icon-collapse mode does not expand nested `SidebarMenuSub` items (issue #5874). Destinations: Triage, Rules, Billing, Settings (privacy lives inside Settings — see D-08), plus whatever onboarding-state entry is appropriate.
- **D-03:** The chrome region (global **pause toggle**, **credit balance** pill, **Gmail connection-health** indicator) is anchored in the **top header**, not the sidebar footer (must survive icon-collapse and the mobile offcanvas state). Built from **raw shadcn primitives** — `badge` for balance, a `tooltip`-wrapped colored dot for health, a `switch` (or `button` + confirm `dialog`) for pause. No wrapper component until the rule-of-three applies. A `DISCONNECTED` health state surfaces a reconnect affordance reusing `ReconnectPrompt` semantics.
- **D-04:** 320px / mobile = the shadcn sidebar's built-in **offcanvas `Sheet`** mode — no separate responsive nav implementation. `SidebarTrigger` in the header opens it.
- **D-05:** `onboarding/*` keeps a **minimal nested layout** that suppresses the full app chrome (focused funnel) — it does not render inside the full sidebar shell. The 3-route structure (`gmail-connect` / `template-select` / `complete`) is unchanged; only tokens/shared-states/responsive convergence applies.

### Routing & page layout
- **D-06:** Triage = a **single `/triage` page** with shadcn **`Tabs`** for *Audit log* / *Shadow mode* / *Sender safety net*. The active tab is synced to a **`?tab=` searchParam** (`useSearchParams`) so each tab is deep-linkable (support links, email links). Shadow-mode is treated as page-level state, not a peer "section."
- **D-07:** Billing = its **own `/billing` route** (not a `/settings` section) — it's a transactional surface (SePay/VietQR top-up intent, payment callback target, credit ledger) with independently streamed ledger; mirrors inbox-zero / standard SaaS. **BYOK stays under `/settings`** (an API key is a credential preference, not a transaction).
- **D-08:** In-product privacy = a **prominent `/settings` section** (or a `/settings/privacy` segment) explaining no-stored-bodies / no-auto-send / BYOK. The existing public legal page at `(public)/privacy` is **left untouched** (a top-level `(protected)/privacy` route would collide with that path). Note: SPEC.md requirement #8 says "distinct authenticated page" — satisfied by a dedicated `/settings/privacy` segment with its own route, which is both "distinct/authenticated" and avoids the public-route name collision.
- **D-09:** Existing `rules` + `settings` pages slot under `(protected)/layout.tsx` automatically once the shell layout exists; the convergence pass (tokens + shared loading/empty/error primitives + 320px sanity) applies to each. No flow redesign.

### Chrome data layer & shared state
- **D-10:** Chrome data (pause state, credit balance, Gmail health) is **prefetched in `(protected)/layout.tsx`** via `Promise.all` and dehydrated into a `HydrationBoundary` wrapping a `"use client"` `<AppShell>` — flicker-free, SSR-consistent first paint of the trust UI. (The layout is already dynamically rendered because of the existing `cache()`'d `/me` fetch, so the "prefetch-in-layout forces dynamic" cost is already paid.) Caveat for the planner: a query prefetched in the layout must be **consumed within the layout subtree** (the shell), not relied on by a deeper page boundary.
- **D-11:** **Credit balance** query gets `refetchInterval ≈ 45s` with `refetchIntervalInBackground: false` and `staleTime ≈ 30s` (avoids double-fetch with focus refetch), **plus** `invalidateQueries` after billable actions / top-up settle / pause toggle. Rationale: background worker jobs burn credits without user action, so pure invalidate-only would leave the displayed balance stale during heavy triage.
- **D-12:** **Pause state** and **Gmail connection health** stay **invalidate-only** (no polling) — they only change via user action or webhook.
- **D-13:** **Single source of truth for the pause toggle:** one query key (`triageKeys.pauseState()` in `features/triage/query-keys.ts`) and one hook pair — a read hook (`useTriagePauseState()`) and the existing write hook (`useToggleTriagePause()`) — are the **only** accessors. The chrome toggle, the `/settings` toggle, and `PauseBanner` all render off this one cache entry; no local `useState`, no ad-hoc `useQuery` keys. Optimistic update recipe in the mutation hook: `onMutate` → `await cancelQueries({ queryKey: triageKeys.pauseState() })` + snapshot + `setQueryData(target)`; `onError` → restore snapshot; `onSettled` → `invalidateQueries` to reconcile (and invalidate the balance key too if pricing cares about pause state).
- **D-14:** No SSE/WebSocket for any chrome data in v1 — polling + invalidation only (consistent with the no-new-infra posture).

### Billing top-up flow
- **D-15:** Top-up = a **dedicated `/billing/top-up` route** (not a dismissible modal). Inline step sequence: amount entry → display VietQR image + **copyable** bank-transfer fields (account number, memo/reference code, exact amount) → poll `GET /api/billing/balance` and the intent-status endpoint via `refetchInterval` (stopped once `credited` or `expired`) → success state with updated balance. The **pending intent rehydrates from `?intentId=`** so a refresh / coming-back-later resumes the same intent. Expiry handled on-route with a clear "intent expired — start a new top-up" panel. No custom stepper component (shadcn has no stepper primitive, and the pay→confirm transition is webhook-driven, not user-driven).

### Triage audit log presentation
- **D-16:** Audit log = a **responsive hybrid renderer**: shadcn `Table` at `≥ md` (columns: Date/time, Message ref [subject + sender, truncated, links to Gmail if backend gives enough], Rule, Action [`Badge`], Reason [truncated, expandable], Undo [`Button`/disabled]); a **card list** below `md` (one card per entry — header = Action badge + timestamp, body = message ref + rule + **full Reason**, footer = Undo button or muted "Undo window closed" note). Shared row model; truncation/Undo logic lives in one place. The Reason field must never be truncated into invisibility — it's the trust evidence.
- **D-17:** Pagination = **cursor "Load older entries"** via `useInfiniteQuery` (the backend list is cursor-paginated; the log is append-only/time-ordered) — **not** numbered pages. Render a subtle divider/marker where entries cross the **30-day undo boundary**.
- **D-18:** Undo UX = within the window, a small outline `Button` "Undo" per entry → an `AlertDialog` confirm that **names the exact inverse Gmail change** ("This will move the message back to your inbox" / "remove the Finance label") before `POST /api/triage/audit/{auditId}/undo`; on success, invalidate the audit + balance queries and toast. Past the 30-day window — **do not hide** the affordance; render a muted, non-interactive "Undo window closed" label with a tooltip ("Triage actions can be undone for 30 days") so the capability reads as finite, not mysteriously absent.

### Cross-cutting (carried forward — not re-decided here)
- Phase 1.3 frontend architecture: route groups `(public)`/`(auth)`/`(protected)`, feature folders `api/` + `components/` + `hooks/`, query-key factories in `features/<feature>/query-keys.ts`, one hook file per use case, shared primitives in `components/ui`, shared infra in `lib/`.
- Phase 1.6 brand identity & design tokens (Teal accent + Paper-warm neutrals + Geist/Be Vietnam Pro/Instrument Serif type stack) are the styling source of truth; theme via `zm-theme` cookie + Server Action, no `localStorage`, no flash.
- Conventions: **raw shadcn primitives first** (install via `pnpm dlx shadcn@latest add <component>`; don't wrap without rule-of-three composition value), **flat folder structure** (co-locate, hoist single-child folders), **frontend-design skill** invoked before writing any UI (and passed into executor subagents touching frontend), **Vietnamese-first** i18n via `next-intl` with lock-step vi/en bundles (`pnpm i18n:check` must pass), **Playwright-verified** in a real browser before declaring done, privacy logging rules client-side (no bodies/addresses/prompts/completions/token bytes beyond owner-visible backend fields).

### Claude's Discretion
- Exact nav icon choices, header layout/spacing details, tab order, settings-page section ordering, ledger table columns, loading-skeleton shapes — left to the planner + frontend-design skill within the decisions above.
- Whether the `?intentId=` resume is also reachable from a "pending top-up" indicator in the chrome — nice-to-have, planner's call.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase requirements (read first)
- `.planning/phases/05A-user-surface-web-ui-core/05A-SPEC.md` — Locked requirements, boundaries, acceptance criteria for Phase 5A. MUST read before planning.

### Project-level
- `CLAUDE.md` — project constraints, backend code style, conventions 1–9, "do not use" list, tooling (JetBrains/Postgres/Playwright MCP).
- `CONVENTIONS.md` — detailed examples/anti-patterns for the conventions referenced in CLAUDE.md (thin controllers, domain layout, records-for-DTOs, enum state machines, privacy logging, Modulith events, UI primitive selection, frontend feature/hooks/query-keys/tests, subproject-owned config).
- `.planning/REQUIREMENTS.md` §WEB-01..WEB-04 — the requirement IDs this phase satisfies (WEB-02's draft-review + analytics portions are explicitly deferred to 5B/5C).
- `.planning/ROADMAP.md` §"Phase 5A: User Surface — Web UI Core" — phase goal, depends-on (Phase 4 audit/undo REST, 2B billing API, 2A pause/health, 1.6 brand tokens).

### Frontend architecture & design system (precedent to follow)
- `.planning/phases/01.3-frontend-architecture-refactor-and-public-content-foundation/` (CONTEXT/PLANs) — route-group + feature-folder + typed-OpenAPI-client structure 5A builds on.
- `.planning/phases/01.6-brand-identity-design-tokens-and-landing-page/01.6-SPEC.md` — design tokens, type stack, public layout shell, theme cookie pattern, shadcn primitive token-rebind (the styling contract authenticated screens must match).
- `.planning/research/ARCHITECTURE.md` — frontend/backend architecture research (if it contains apps/web guidance).

### Backend contracts this UI consumes (no new endpoints — frontend-only against these)
- `backend/api` springdoc OpenAPI output → `apps/web` generated typed client (`schema.d.ts` via `openapi-typescript`). Existing controllers/paths: `MeController` (`/api/me`, `/api/me/account`, `/api/me/language`), `OnboardingController` (`/api/onboarding/*`, templates), `RulesController` (`/api/rules` CRUD + `/reorder` + `/{ruleId}/preview` + `/compile` + `/templates`), `TriageAuditController` (audit list + `/api/triage/audit/{auditId}/undo`), `SenderSafetyNetController` (`/api/triage/sender-safety-net` + `/{senderEmail}/opt-in`), shadow mode `/api/tenant/triage/shadow-mode`, pause `/api/tenant/triage-pause`, `BillingController` (`/api/billing/balance`, `/api/billing/topup/intent`), `ByokController` (`/api/llm/byok`), `TenantStatusController` (`/api/gmail/connection/status`), `ConnectGmailController` / `DisconnectController`.
- `.planning/phases/04-triage-convergence-hero/04-SPEC.md` + `04-UAT.md` — triage audit/undo/shadow/sender-net REST semantics (30-day undo window, inverse-action computation, shadow-mode = tenant-wide opt-in toggle).
- `.planning/phases/02B-billing-prepaid-credits/` (SPEC/PLANs) — credit ledger, reserve/settle/release, SePay/VietQR top-up intent + webhook semantics; check whether a ledger-history list endpoint exists (if not, log it as a gap per SPEC out-of-scope rule).
- `.planning/phases/02A-mail-ingestion/` (SPEC/PLANs) — triage-pause toggle + connection-health/reconnect semantics.

### External library docs (use Context7 per global rule before using these APIs)
- shadcn/ui `sidebar` block + component docs (icon-collapse mode, `SidebarProvider`/`SidebarInset`/`SidebarTrigger`, cookie state) — https://ui.shadcn.com/blocks/sidebar , https://ui.shadcn.com/docs/components/radix/sidebar (known limitation: shadcn-ui/ui #5874 — collapsible sub-menus don't expand in icon mode).
- TanStack Query v5 — Advanced Server Rendering (App Router) / prefetching / `HydrationBoundary` (gotcha: TanStack/query #8479 — a query prefetched only in a layout isn't seen by a deeper page `HydrationBoundary`).
- Next.js 16 App Router — nested layouts, route groups, `useSearchParams`, route handlers for payment callbacks.
- SePay webhooks / VietQR API (https://developer.sepay.vn , https://vietqr.io) — for the top-up instruction display contract (the backend owns verification; the UI only renders intent fields).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `apps/web/components/ui/*` — already installed: `alert`, `avatar`, `badge`, `button`, `card`, `checkbox`, `dialog`, `input`, `label`, `radio-group`, `separator`, `skeleton`, `tabs`, `textarea`, `toggle-group`, `toggle`, `tooltip`. Still need to install: `sidebar` (pulls `sheet`), `table`, `alert-dialog`, `switch`, possibly `dropdown-menu`/`navigation-menu`, `sonner` (toast).
- `apps/web/features/triage/` — `PauseBanner.tsx`, `useToggleTriagePause.ts` (+ tests), `triage-api.ts`, `messages.ts` already exist. The pause toggle refactor (D-13) rebases these onto the shared query key/hooks; add audit/undo/shadow/sender-net under this same feature.
- `apps/web/features/gmail/` — `ConnectionHealthBadge.tsx`, `useTenantStatus.ts`, `query-keys.ts`, `ReconnectPrompt.tsx`, `useDisconnectGmail.ts` — the chrome health indicator + reconnect affordance reuse these.
- `apps/web/features/account/` — `query-keys.ts`, `useCurrentUser.ts`, `account-api.ts`; `app/(protected)/layout.tsx` already does a `cache()`'d `getCurrentUser` server fetch — extend it for the shell-data prefetch (D-10).
- `apps/web/features/auth/components/` — `AuthTopBar.tsx`, `LegalFooter.tsx`, `StepIndicator.tsx`, `TrustPanel.tsx` — patterns for the onboarding minimal layout (D-05) and for trust copy.
- `apps/web/features/rules/` — full rules workspace (`RulesWorkspace`, `RuleComposer`, `RuleList`, `RulePreviewPanel`, `RuleTemplateGallery`, `use-rules.ts`, `query-keys.ts`) — only needs the convergence pass (slot under shell, tokens, shared states, 320px).
- `apps/web/features/llm/` — `ByokForm.tsx` + `use-byok.ts` already mounted on `/settings` — stays under `/settings` per D-07.
- `apps/web/lib/api/*` — base `openapi-fetch` client + per-feature `api/` modules; new `features/billing/api/billing-api.ts` follows this.
- i18n: `apps/web/i18n/messages/{vi,en}.json` + `scripts/check-i18n.ts` (`EN_SCAN_FILES`, STRICT lint-staged gate) — new namespaces (`nav.*`, `triage.*`, `billing.*`, `privacy.*`, `shell.*`) must be added to both bundles and the scanner.

### Established Patterns
- Route groups `app/(public)` / `app/(auth)` / `app/(protected)` with per-group `layout.tsx` + `error.tsx`; `global-error.tsx` + `not-found.tsx` at root. New shell = `app/(protected)/layout.tsx`; new routes `app/(protected)/triage/page.tsx`, `app/(protected)/billing/page.tsx`, `app/(protected)/billing/top-up/page.tsx`, `app/(protected)/settings/privacy/page.tsx` (or a section in `app/(protected)/settings/page.tsx`).
- Feature folders own `api/<feature>-api.ts` (typed `openapi-fetch` calls), `query-keys.ts` (key factory; only for features owning cached data), `hooks/` (one file per use case), `components/`, optional `messages.ts`.
- Server-component layout reads session via `cache()`'d `/me`; TanStack Query client lives in a `"use client"` provider; Playwright e2e under `apps/web/e2e/**`, Vitest feature tests beside feature code or `apps/web/__tests__/**`.
- shadcn primitives are copied-in source, excluded from ESLint/Prettier; install via `pnpm dlx shadcn@latest add <component>`.

### Integration Points
- `app/(protected)/layout.tsx` — becomes the shell host (SidebarProvider + SidebarInset + top header + HydrationBoundary with prefetched pause/balance/health).
- Generated OpenAPI client — every new screen calls the existing typed client; regenerate `schema.d.ts` only if the backend OpenAPI output changed (it shouldn't — 5A is frontend-only).
- `next-intl` request/middleware config + `i18n/messages/*` + `scripts/check-i18n.ts` — extend for all new strings.
- `lint-staged` / Husky / Prettier gates + `tsc` + Vitest + `i18n:check` — must stay green; Playwright e2e is the visual/behavior verification bar (golden paths + key states, desktop + 320px).

</code_context>

<specifics>
## Specific Ideas

- Audit log is "the evidence locker" — the **Reason** field is the trust artifact; never let it get truncated into invisibility (drove the responsive-hybrid renderer choice D-16).
- Undo affordance past the 30-day window must be **visibly finite** ("Undo window closed" muted label), not silently absent (D-18).
- Pause toggle is a high-stakes trust kill-switch — it lives in the most stable always-rendered chrome (top header), and its displayed state must be physically incapable of drifting between the chrome / settings / banner (single shared query key, D-03 + D-13).
- Top-up is a real wait (async bank transfer, webhook-credited, Vietnam beta) — the surface must survive a refresh and a "come back later" (`?intentId=` rehydration, D-15).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. (Draft-reply UI → Phase 5B; analytics screen + daily digest → Phase 5C; any backend endpoint a 5A screen turns out to need → logged as a gap during planning/execution per SPEC out-of-scope rule, not built in 5A.)

</deferred>

---

*Phase: 5A-user-surface-web-ui-core*
*Context gathered: 2026-05-12*
