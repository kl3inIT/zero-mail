# Phase 5A: User Surface — Web UI Core — Specification

**Created:** 2026-05-12
**Ambiguity score:** 0.14 (gate: ≤ 0.20)
**Requirements:** 8 locked

## Goal

`apps/web` becomes a coherent authenticated product: every already-built backend flow (onboarding, rule CRUD + live preview, triage audit log + undo + shadow mode + sender safety net, billing balance + top-up + ledger history, in-product privacy page) is reachable inside a new authenticated app shell that persistently surfaces the global pause toggle, live credit balance, and Gmail connection health — all consuming the existing typed OpenAPI client, with every authenticated screen rendering on Phase 1.6 design tokens and shared loading/empty/error primitives, responsive from 320px upward.

## Background

The backend surface for everything 5A needs already exists and is shipped:
- `MeController` (`/api/me`, `/api/me/account`, `/api/me/language`), `OnboardingController` (`/api/onboarding/*`, templates), `RulesController` (`/api/rules` CRUD + reorder + `/preview` + `/compile` + templates), `TriageAuditController` (audit list + `/api/triage/audit/{auditId}/undo`), `SenderSafetyNetController` (`/api/triage/sender-safety-net` list + `/{senderEmail}/opt-in`), shadow mode (`/api/tenant/triage/shadow-mode`), triage pause (`/api/tenant/triage-pause`), `BillingController` (`/balance`, `/topup/intent`, + SePay/VietQR webhook crediting the ledger), `ByokController` (`/api/llm/byok`), `TenantStatusController` (`/gmail/connection/status`), `ConnectGmailController` / `DisconnectController`.

`apps/web` (Next.js 16 App Router, route groups `(public)` / `(auth)` / `(protected)`, feature folders with `api/` + `components/` + `hooks/`, typed `openapi-fetch` client, `next-intl` vi/en, Phase 1.6 design tokens + brand identity) already has: landing/`(public)` surfaces, `(auth)/login`, `(protected)/onboarding` (3 routes), `(protected)/rules` (`RulesWorkspace` + composer + list + preview + template gallery), `(protected)/settings` (account delete, language, disconnect Gmail, BYOK form), `PauseBanner`, `ConnectionHealthBadge`, `ReconnectPrompt`, error/not-found boundaries.

What does NOT exist yet:
- A persistent authenticated **app shell** (header/sidebar nav hosting pause toggle + balance + connection health on every protected page) — `(protected)/layout.tsx` is currently a thin layout, not a shell.
- A **billing UI** — no `features/billing/` at all; `BillingController` is unconsumed by the frontend.
- A **triage audit log + undo UI** — no `features/triage` audit-list/undo/shadow-toggle/sender-net components; `TriageAuditController` and `SenderSafetyNetController` are unconsumed.
- An **in-product privacy page** — only a `(public)/privacy` marketing stub exists; no authenticated data-handling page.
- A **credit balance** display anywhere — `BillingController#balance` is unconsumed.

Phases 5B (AI draft replies) and 5C (analytics + daily digest) own the draft-review and analytics screens; their backends do not exist, so those surfaces are out of 5A.

## Requirements

1. **Authenticated app shell**: A persistent shell wraps every `(protected)` route.
   - Current: `(protected)/layout.tsx` is a thin layout; no nav, no persistent chrome region; pause/balance/health are not consistently present
   - Target: `(protected)/layout.tsx` renders an app shell (header — and sidebar if the design calls for it) containing primary navigation to all authenticated surfaces (onboarding completion state, rules, triage, billing, settings, privacy) plus a persistent chrome region with the global pause toggle, live credit balance, and Gmail connection-health indicator; all authenticated pages render their content inside this shell
   - Acceptance: every `app/(protected)/**` page renders inside the shell; the pause toggle, credit balance, and connection-health indicator are visible without scrolling on every authenticated page at desktop and 320px widths (Playwright)

2. **Global pause toggle in chrome**: The chrome pause toggle drives `/api/tenant/triage-pause`.
   - Current: `PauseBanner` + a settings toggle exist but pause is not in a persistent global chrome region
   - Target: the shell chrome exposes the pause toggle; flipping it calls the triage-pause endpoint, reflects the new state optimistically, and the state is consistent everywhere the pause state is shown (banner, settings)
   - Acceptance: toggling pause from the chrome updates the persisted state (verified via the tenant status / pause read endpoint) and the UI reflects it without a full page reload (Playwright)

3. **Live credit balance in chrome**: The chrome shows the tenant credit balance, kept fresh.
   - Current: `/api/billing/balance` is not consumed anywhere in `apps/web`
   - Target: the shell chrome displays the current credit balance via TanStack Query with a periodic refetch (≈30–60 s interval) plus cache invalidation after billable actions and after a top-up is credited; no websockets/SSE
   - Acceptance: balance renders in the chrome on every authenticated page; after a simulated top-up credit, the displayed balance updates without a full page reload (Playwright + mocked/seeded ledger)

4. **Connection-health indicator in chrome**: The chrome surfaces Gmail connection status.
   - Current: `ConnectionHealthBadge` / `useTenantStatus` exist but the badge is not in the persistent chrome region
   - Target: the shell chrome shows a connection-health indicator backed by `/gmail/connection/status`; a `DISCONNECTED` status surfaces a reconnect affordance (reusing `ReconnectPrompt` semantics)
   - Acceptance: with a `CONNECTED` status the chrome shows a healthy indicator; with a `DISCONNECTED` status the chrome shows the degraded state + reconnect affordance on every authenticated page (Playwright with status mocked both ways)

5. **Triage audit log + undo**: An authenticated triage screen lists audit entries and undoes them.
   - Current: no triage audit UI exists; `TriageAuditController` (list) and `/api/triage/audit/{auditId}/undo` are unconsumed
   - Target: a `/triage` (or equivalent) page lists triage audit entries (message reference, rule, action, reason, timestamp) with pagination; each entry within the 30-day undo window offers an Undo action that calls the undo endpoint, shows success/failure, and removes the entry's undoable affordance on success
   - Acceptance: the audit list renders correctly at 0 entries (empty state), 1 entry, and a page-full of entries; clicking Undo on an undoable entry calls the undo endpoint and the UI reflects the undone state; an entry past the 30-day window shows no Undo affordance (Playwright + seeded/mocked audit data)

6. **Shadow mode + sender safety net management**: The triage surface manages shadow mode and sender opt-ins.
   - Current: `/api/tenant/triage/shadow-mode` and `/api/triage/sender-safety-net` (+ `/{senderEmail}/opt-in`) are unconsumed in `apps/web`
   - Target: the triage surface includes (a) a tenant-wide shadow-mode toggle reading/writing `/api/tenant/triage/shadow-mode` and (b) a sender-safety-net list showing senders flagged as frequent/important with a control to opt a sender into automation via `/{senderEmail}/opt-in`
   - Acceptance: toggling shadow mode persists and reflects the new state; the sender list renders (including an empty state); opting a sender in calls the opt-in endpoint and the row reflects the new opted-in state (Playwright)

7. **Billing: balance + top-up + ledger history**: A billing screen shows balance, runs the top-up flow, and lists ledger history.
   - Current: no `features/billing/` exists; `/balance`, `/topup/intent` are unconsumed; no ledger history view
   - Target: a billing page (own route and/or settings section) shows the current balance, a top-up flow (enter amount → `POST /topup/intent` → display the VietQR / bank-transfer instructions → poll balance/intent until credited → success state), and a paginated ledger/transaction history list (top-ups, reserve/settle/release entries) — using whichever read endpoints the existing OpenAPI surface exposes; if a needed list endpoint does not exist, it is logged as a gap rather than built
   - Acceptance: the billing page shows the balance; starting a top-up creates an intent and shows transfer instructions; on a simulated credit the page reaches a success state and the balance increases; the ledger history list renders with at least an empty state and a populated state (Playwright + seeded/mocked billing data)

8. **In-product privacy page**: A distinct authenticated page explains data handling.
   - Current: only the `(public)/privacy` marketing stub exists; no authenticated data-handling page
   - Target: a distinct authenticated page (inside the app shell) explains no long-term storage of email bodies/prompts/completions, no auto-send, and the BYOK option; it is reachable from the shell navigation/chrome; vi + en localized
   - Acceptance: the page exists at an authenticated route, is linked from the shell, renders both vi and en, and explicitly states all three points (no-stored-bodies, no-auto-send, BYOK)

## Boundaries

**In scope:**
- New authenticated app shell for all `(protected)` routes (header / optional sidebar + persistent chrome region)
- Persistent chrome widgets: global pause toggle, live credit balance (polled + invalidated), Gmail connection-health indicator
- New `features/triage` UI: audit log + undo, shadow-mode toggle, sender-safety-net management
- New `features/billing` UI: balance, top-up flow (intent → VietQR/bank-transfer instructions → poll → success), paginated ledger history
- New in-product privacy page (authenticated, distinct from public `/privacy`)
- Convergence pass on existing authenticated screens (rules, onboarding 3 routes, settings): render inside the new shell, use Phase 1.6 design tokens, use shared loading/empty/error primitives, responsive sanity pass to 320px — no flow redesign
- vi/en i18n parity for all new strings; typed OpenAPI client consumption; standard frontend gates (tsc, lint, vitest, i18n:check) green; Playwright e2e for golden paths + key states on desktop + 320px; a frontend-design visual-review note per screen

**Out of scope:**
- Public / marketing surfaces (landing `/`, `/docs`, public `/terms`, public `/privacy`, `(auth)/login`) — owned by Phase 1.6; 5A does not touch them
- AI draft-reply UI and any draft-review screen — Phase 5B; backend does not exist
- Analytics screen and daily-digest UI — Phase 5C; backend does not exist
- Any new or modified backend endpoint — 5A is frontend-only against the existing OpenAPI surface; missing endpoints are logged as gaps, not built
- Onboarding flow redesign — the 3-route structure and behavior are unchanged; only shell/token/state/responsive integration
- Real-time transport (websockets/SSE) for balance or any other data — polling + cache invalidation only

## Constraints

- All backend access goes through the generated typed OpenAPI client in `lib/api` + feature `api/` modules; no ad-hoc `fetch` to backend routes (per project conventions).
- All new visible strings flow through `next-intl` keys with lock-step vi + en entries; `pnpm i18n:check` must pass.
- UI primitives: check shadcn/ui first, install via `pnpm dlx shadcn@latest add <component>`, compose around `@/components/ui/*`; do not wrap primitives without real composition value (rule of three).
- Frontend work uses the Anthropic `frontend-design` skill; visual quality is verified in a real browser (Playwright), not just type-check.
- Phase 1.6 design tokens are the styling source of truth; no hard-coded ad-hoc colors/spacing on authenticated screens after the convergence pass.
- Privacy: no email bodies, addresses, prompts, completions, or token bytes rendered or logged client-side beyond what the backend explicitly returns as owner-visible fields.
- Credit balance refetch interval ≈30–60 s; must also invalidate on billable actions and top-up completion.

## Acceptance Criteria

- [ ] Every `app/(protected)/**` page renders inside a single new app shell with primary nav
- [ ] The global pause toggle, credit balance, and Gmail connection-health indicator are visible (no scroll) on every authenticated page at desktop and 320px widths
- [ ] Toggling pause from the chrome persists and reflects without a full page reload; state is consistent with the settings toggle / pause banner
- [ ] Credit balance renders from `/api/billing/balance`, refetches on a 30–60 s interval, and updates after a simulated top-up credit without a full page reload
- [ ] Connection-health indicator shows healthy on `CONNECTED` and degraded + reconnect affordance on `DISCONNECTED`
- [ ] Triage audit log renders at 0 / 1 / page-full entries; Undo on an in-window entry calls the undo endpoint and updates the UI; an out-of-window entry shows no Undo affordance
- [ ] Shadow-mode toggle reads/writes `/api/tenant/triage/shadow-mode` and persists; sender-safety-net list renders (incl. empty state) and a sender opt-in calls `/{senderEmail}/opt-in` and updates the row
- [ ] Billing page shows balance, runs the top-up flow (intent → VietQR/bank-transfer instructions → poll → success with increased balance), and renders a paginated ledger history (empty + populated states)
- [ ] A distinct authenticated privacy page exists, is linked from the shell, renders vi + en, and states no-stored-bodies, no-auto-send, and BYOK
- [ ] Existing authenticated screens (rules, onboarding ×3, settings) render inside the new shell, on 1.6 tokens, using shared loading/empty/error primitives, with no horizontal scroll at 320px
- [ ] `tsc`, ESLint, Vitest, and `i18n:check` all pass; Playwright e2e covers each surface's golden path + key states on desktop + 320px
- [ ] A frontend-design visual-review note exists for each authenticated screen

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                        |
|--------------------|-------|------|--------|--------------------------------------------------------------|
| Goal Clarity       | 0.90  | 0.75 | ✓      | Delta from current state pinned; convergence bar defined      |
| Boundary Clarity   | 0.85  | 0.70 | ✓      | Explicit out-of-scope incl. 5B/5C, public surfaces, no backend|
| Constraint Clarity | 0.80  | 0.65 | ✓      | Typed client, tokens, i18n, polling cadence, frontend-design  |
| Acceptance Criteria| 0.85  | 0.70 | ✓      | 12 pass/fail checks, Playwright golden-paths+states bar       |
| **Ambiguity**      | 0.14  | ≤0.20| ✓      |                                                              |

## Interview Log

| Round | Perspective     | Question summary                                  | Decision locked                                                                 |
|-------|-----------------|---------------------------------------------------|---------------------------------------------------------------------------------|
| 1     | Researcher      | How treat already-built screens?                  | Full convergence pass — net-new screens + shell + bring all auth screens to a consistent bar |
| 1     | Researcher      | Form of the persistent WEB-04 region?             | New authenticated app shell (header + optional sidebar) hosting pause/balance/health/nav |
| 1     | Researcher      | Billing UI surface in 5A?                          | Balance + top-up flow + paginated ledger history                                |
| 2     | Simplifier      | Triage UI scope?                                  | Audit log + undo + shadow-mode toggle + sender-safety-net management            |
| 2     | Boundary Keeper | What's explicitly OUT?                             | Public/marketing surfaces; 5B/5C draft & analytics; no new backend endpoints    |
| 2     | Boundary Keeper | Convergence bar for existing screens?             | Shell + 1.6 tokens + shared loading/empty/error states + 320px responsive sanity (no flow redesign) |
| 3     | Seed Closer     | In-product privacy page form?                     | Distinct authenticated page inside the shell, separate from public `/privacy`   |
| 3     | Seed Closer     | Acceptance bar?                                    | Playwright golden paths + key states on desktop + 320px; tsc/lint/vitest/i18n green; frontend-design note per screen |
| 3     | Seed Closer     | How real-time is the balance?                      | TanStack Query 30–60 s poll + invalidate on billable actions / top-up; no WS/SSE|

---

*Phase: 05A-user-surface-web-ui-core*
*Spec created: 2026-05-12*
*Next step: /gsd-discuss-phase 5A — implementation decisions (shell layout, nav structure, billing flow UX, audit-list pagination, etc.)*
