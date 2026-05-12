# Phase 5A: User Surface — Web UI Core - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-12
**Phase:** 5A-user-surface-web-ui-core
**Areas discussed:** App shell + nav structure, Routing & page layout, Chrome data layer & shared state, Billing top-up flow + audit log UX

Mode: advisor (research-backed comparison tables; full_maturity calibration tier — vendor philosophy = thorough-evaluator). NON_TECHNICAL_OWNER resolved to false (developer overwhelmingly technical; `learning_style: guided` signal overridden by direct code-authoring / architecture-decision behavior).

---

## App shell + nav structure

| Option | Description | Selected |
|--------|-------------|----------|
| Icon-rail sidebar + top header | shadcn `sidebar` block, `collapsible="icon"`, cookie-persisted; thin persistent top header owns pause/balance/health chrome; flat single-level nav; 320px = built-in offcanvas `Sheet` | ✓ |
| Persistent (always-expanded) sidebar + top header | Same block always full-width (~16rem); strongest "app" feel; eats horizontal space on the audit-log table | |
| Header-only top nav | No sidebar; horizontal nav + chrome strip in the header; simplest; doesn't scale past ~5 destinations | |

**User's choice:** Icon-rail sidebar + top header (Recommended)
**Notes:** Chrome region anchored in the top header (survives icon-collapse + mobile offcanvas); raw shadcn primitives for the widgets; nav kept flat due to shadcn #5874 (sub-menus don't expand in icon mode); onboarding stays in a minimal nested layout outside the full shell.

---

## Routing & page layout

| Option | Description | Selected |
|--------|-------------|----------|
| Single `/triage` page, Tabs + `?tab=` sync | One route, shadcn Tabs for Audit / Shadow mode / Senders; `?tab=` searchParam for deep-linkability; shadow-mode = page-level chrome | ✓ |
| Multi-route `/triage/audit`, `/triage/shadow`, `/triage/senders` | Shared `triage/layout.tsx` + sub-nav; native deep-links + per-route loading.tsx; heavier surface | |
| Hybrid: `/triage` (audit + inline shadow) + `/triage/senders` | Audit log centerpiece page; senders as a separate management route | |

| Option | Description | Selected |
|--------|-------------|----------|
| `/billing` route + privacy as a `/settings` section | Dedicated `/billing` (balance + top-up + ledger); BYOK stays under `/settings`; authenticated privacy as a `/settings` section; public `/privacy` untouched | ✓ |
| `/billing` route + standalone `/privacy` authenticated page | Same `/billing`, but privacy is its own top-level route — needs route-group disambiguation vs the public `/privacy` | |
| Billing as a `/settings` section + privacy as a `/settings` section | Everything account-ish under `/settings`; fewer routes; settings bloat; payment callback inside a settings tab is awkward | |

**User's choice:** Single `/triage` page with Tabs (Recommended) + `/billing` route with privacy as a `/settings` section (Recommended)
**Notes:** New `(protected)/layout.tsx` shell wraps rules/settings/triage/billing automatically. Privacy implemented as a distinct `/settings/privacy` segment (own route, authenticated) to satisfy SPEC #8's "distinct authenticated page" while avoiding the public `/privacy` name collision.

---

## Chrome data layer & shared state

| Option | Description | Selected |
|--------|-------------|----------|
| Recommended bundle | RSC layout prefetch (`Promise.all`) + `HydrationBoundary` around client `<AppShell>`; balance `refetchInterval ~45s` + `staleTime ~30s` + invalidate-on-event; pause & health invalidate-only; one shared query key + hook pair for pause across chrome/settings/PauseBanner with optimistic update | ✓ |
| Pure client hooks (no RSC prefetch) | Keep layout thin; chrome data loads via client hooks after hydration — accept a brief flicker on pause toggle + balance pill | |
| Recommended, but invalidate-only balance (no polling) | Drop the ~45s balance refetchInterval — balance updates only on focus + after billable actions / top-up; can be stale during heavy worker burn | |

**User's choice:** Recommended bundle
**Notes:** Single source of truth for pause toggle is non-negotiable (trust UI must not drift). Caveat noted for the planner: a layout-prefetched query must be consumed within the layout subtree, not by a deeper page boundary (TanStack #8479). No SSE/WebSocket in v1.

---

## Billing top-up flow + audit log UX

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated `/billing/top-up` route | Amount → VietQR + copyable bank fields → poll-until-credited → success / expiry CTA; pending intent rehydrates from `?intentId=`; refresh-safe | ✓ |
| Hybrid: modal for amount → redirect to `/billing/top-up` for the wait | Quick entry where the user is, then hand off to the durable route | |
| Single Dialog modal (all steps inside) | Amount → QR → polling → success in one dismissible modal; refresh-fragile, cramped at 320px | |

| Option | Description | Selected |
|--------|-------------|----------|
| Responsive hybrid: Table ≥md / cards <md | Dense shadcn Table on desktop; full-width card per entry on mobile; cursor "Load older" (`useInfiniteQuery`); 30-day boundary marker; Undo via `AlertDialog` confirm naming the inverse Gmail change; past-window = muted "Undo window closed" label (not hidden) | ✓ |
| Card list only (one renderer) | One card per entry everywhere; great at 320px, sparser on desktop | |
| shadcn Table only (one renderer) | Dense table everywhere; needs a horizontal-scroll/column-drop escape hatch at 320px; risks truncating the Reason field | |

**User's choice:** Dedicated `/billing/top-up` route (Recommended) + Responsive hybrid audit log (Recommended)
**Notes:** No custom stepper component (no shadcn primitive; pay→confirm is webhook-driven). Reason field is the trust artifact — must never be truncated into invisibility. Undo past the window is visibly finite, not silently absent.

---

## Claude's Discretion

- Exact nav icon choices, header layout/spacing, tab order, settings-section ordering, ledger table columns, loading-skeleton shapes — left to the planner + frontend-design skill within the recorded decisions.
- Whether `?intentId=` resume is also reachable from a "pending top-up" indicator in the chrome — nice-to-have, planner's call.

## Deferred Ideas

None — discussion stayed within phase scope. (Draft-reply UI → Phase 5B; analytics + daily digest → Phase 5C; any backend endpoint a 5A screen needs → logged as a gap during planning, not built in 5A.)
