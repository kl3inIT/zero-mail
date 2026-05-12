---
phase: 5A
slug: user-surface-web-ui-core
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-12
---

# Phase 5A — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Frontend-only UI phase: weight = standard frontend gates (`tsc`, ESLint, Vitest, `i18n:check`) + Playwright e2e for golden paths + key states (desktop + 320px) + a `frontend-design` visual-review note per authenticated screen. No backend test scaffolding — 5A adds/modifies no backend endpoint.
> Derived from `05A-RESEARCH.md` § "Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Unit/component framework** | Vitest 4.x + `@testing-library/react` + `jsdom` + `@testing-library/jest-dom` |
| **Vitest config** | `apps/web/vitest.config.ts` (setup `apps/web/__tests__/setup.ts`) |
| **E2E framework** | Playwright (`@playwright/test`) — config `apps/web/playwright.config.ts`, specs in `apps/web/e2e/**` |
| **Lint / typecheck / i18n** | `eslint-config-next` (ESLint 9) · `tsc --noEmit` · STRICT `scripts/check-i18n.ts` |
| **Quick run command** | `pnpm --filter web typecheck && pnpm --filter web lint` (+ touched feature's Vitest file) |
| **Full suite command** | `pnpm --filter web typecheck && pnpm --filter web lint && pnpm --filter web test && pnpm --filter web i18n:check && pnpm --filter web test:e2e` |
| **Estimated runtime** | ~30–90s unit/lint/typecheck; Playwright suite a few minutes |

---

## Sampling Rate

- **After every task commit:** `pnpm --filter web typecheck && pnpm --filter web lint` + the touched feature's Vitest file(s).
- **After every plan wave:** `pnpm --filter web test && pnpm --filter web i18n:check` + the relevant Playwright spec(s).
- **Before `/gsd-verify-work`:** Full suite green (`typecheck` + `lint` + `test` + `i18n:check` + `test:e2e`) AND a `frontend-design` visual-review note recorded for each authenticated screen (shell, `/triage` all tabs, `/billing`, `/billing` top-up all states, `/settings/privacy`, converged rules / onboarding ×3 / settings).
- **Max feedback latency:** < 120 seconds for the quick loop.

---

## Per-Task Verification Map

> Task IDs are assigned by the planner; this is the requirement → test-artifact map the plans must wire to. Every new visible behavior gets a Vitest contract and/or a Playwright spec.

| Plan area | Wave | Requirement | Secure / expected behavior | Test type | Automated command / artifact | File Exists | Status |
|-----------|------|-------------|-----------------------------|-----------|------------------------------|-------------|--------|
| App shell | 1 | WEB-04 | Shell renders on every `(protected)` route; pause/balance/health visible (no horizontal scroll) at 1280px and 320px; shell never unmounts on client nav | Playwright | `e2e/app-shell.spec.ts` | ❌ W0 | ⬜ pending |
| Pause toggle (D-13) | 2 | WEB-04 | One query key (`triageKeys.pauseState()`); chrome toggle + settings toggle + `PauseBanner` all read it; optimistic update + rollback | Playwright + Vitest | `e2e/pause-toggle.spec.ts`; `features/triage/hooks/useToggleTriagePause.test.tsx` (rewrite) | ⚠️ partial | ⬜ pending |
| Credit balance | 2 | WEB-04 | Renders from `/api/billing/balance`; `refetchInterval ≈ 45s` actually fires (not swallowed by global `staleTime`); updates after simulated top-up credit | Playwright (route-fulfill + poll) + Vitest | `e2e/billing-balance.spec.ts`; `features/billing/hooks/useBillingBalance.test.tsx` | ❌ W0 | ⬜ pending |
| Connection health | 2 | WEB-04 | Healthy dot on `CONNECTED`; degraded + reconnect affordance on `DISCONNECTED` | Playwright (route-fulfill both states) | `e2e/connection-health.spec.ts` | ❌ W0 | ⬜ pending |
| Triage audit + undo | 3 | WEB-02 | List at 0 / 1 / page-full; Undo on in-window entry calls undo + updates; out-of-window entry shows no Undo | Playwright (route-fulfill audit list — **mocked; flag backend gap**) + Vitest | `e2e/triage-audit.spec.ts`; `features/triage/components/AuditLog.test.tsx` | ❌ W0 | ⬜ pending |
| Shadow mode + sender net | 3 | WEB-02 | Shadow toggle reads/writes `/api/tenant/triage/shadow-mode`; sender list renders incl. empty; opt-in calls endpoint + updates row | Playwright + Vitest | `e2e/triage-shadow-senders.spec.ts`; `features/triage/components/SenderSafetyNetList.test.tsx` | ❌ W0 | ⬜ pending |
| Billing top-up + ledger | 3 | WEB-02 | Balance shown; top-up → intent → VietQR/bank instructions → simulated credit → success + balance up; ledger renders empty + populated (ledger mocked if no endpoint — **flag gap**) | Playwright (route-fulfill `topup/intent` + `balance`) | `e2e/billing-topup.spec.ts` | ❌ W0 | ⬜ pending |
| In-product privacy | 3 | WEB-03 | Authenticated route (`/settings/privacy`), linked from shell; renders vi + en; states no-stored-bodies / no-auto-send / BYOK | Playwright + Vitest (i18n parity) | `e2e/privacy-page.spec.ts`; `__tests__/i18n/messages.contract.test.ts` (extend) | ❌ W0 | ⬜ pending |
| Convergence pass | 4 | WEB-02 (existing screens) | rules / onboarding ×3 / settings render inside the shell, on 1.6 tokens, with shared loading/empty/error primitives; no horizontal scroll at 320px; onboarding stays chrome-suppressed | Playwright (extend `e2e/rules.spec.ts`, `e2e/onboarding-routes.spec.ts`, `e2e/byok.spec.ts`) | extend existing specs | ⚠️ extend | ⬜ pending |
| All | every | WEB-01 | Every new screen consumes the typed `openapi-fetch` client (no ad-hoc backend `fetch`); `tsc` / ESLint / Vitest / `i18n:check` green | CI gates | full-suite command | ✓ infra | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `apps/web/components/states/{LoadingState,EmptyState,ErrorState}.tsx` — shared loading/empty/error trio (consumed by every new list + the convergence pass) — **build first**; does not exist yet.
- [ ] `apps/web/features/triage/query-keys.ts` — `triageKeys` factory (incl. `pauseState()`, `auditLog(...)`, `senderSafetyNet()`, `shadowMode()`) — does not exist yet.
- [ ] `apps/web/features/billing/` — entire feature folder is new (`api/billing-api.ts`, `query-keys.ts`, `hooks/`, `components/`, `messages.ts`).
- [ ] Playwright spec stubs: `e2e/app-shell.spec.ts`, `e2e/pause-toggle.spec.ts`, `e2e/billing-balance.spec.ts`, `e2e/connection-health.spec.ts`, `e2e/triage-audit.spec.ts`, `e2e/triage-shadow-senders.spec.ts`, `e2e/billing-topup.spec.ts`, `e2e/privacy-page.spec.ts`.
- [ ] Vitest spec stubs: `useToggleTriagePause.test.tsx` (rewrite for D-13), `useBillingBalance.test.tsx`, `AuditLog.test.tsx`, `SenderSafetyNetList.test.tsx`, `useTopupCreditWatch.test.tsx`.
- [ ] Confirm/add a 320px viewport project (or per-spec viewport override) in `apps/web/playwright.config.ts` for the responsive-floor assertions.
- [ ] Extend `apps/web/scripts/check-i18n.ts` `EN_SCAN_FILES` with every new English-literal-bearing component.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual quality / brand-token correctness per screen (light + dark) | WEB-02/03/04, convergence | Pixel/brand judgment is not assertable in Playwright beyond "no horizontal scroll / element visible" | Run `frontend-design` skill review per screen; record a visual-review note (shell, `/triage` all tabs, `/billing`, top-up all states, `/settings/privacy`, converged rules/onboarding/settings); both color schemes read correctly; Phase 1.6 teal token contract honored; no `.zm-proto`/`.zm-auth` clay skin, no ad-hoc colors |
| No client-side data exposure | privacy constraint | Static rule check, not a behavioral test | Reviewer greps new components: no email bodies/addresses/prompts/completions/token bytes rendered or logged beyond owner-visible fields the backend explicitly returns |

---

## Backend-Surface Gaps (resolve before finalizing wave sequencing)

Per `05A-SPEC.md` / `05A-CONTEXT.md`: 5A adds/modifies no backend endpoint; missing endpoints are **logged as gaps**, not built. The planner MUST confirm these against `backend/api` controllers (or ask the user) before locking waves:

1. **Triage-audit *list* endpoint** — only `/api/triage/audit/{auditId}/undo` is in the committed schema. If a list endpoint exists → request `pnpm generate:api` refresh + build the real feature; if not → `AuditLog` consumes a flagged blocked-on-backend stub and the e2e mocks the list response.
2. **Billing *ledger / transaction-history* list endpoint** — not in the committed schema. Same fork as (1).
3. **Top-up *intent-status* endpoint / `intentId` field** — `TopupIntentResponse` carries only `code`/`amountVnd`/`expiresAt`/`qrPayload` (raw EMV string, not an image URL). If no intent-status endpoint/`intentId` → `?intentId=` rehydration (D-15) falls back to `?code=` + `sessionStorage`, and the success transition relies on the balance refetch/invalidate rather than a status poll.

Each has a documented degradation path; none blocks the phase.

---

## Validation Sign-Off

- [ ] All tasks have an `<automated>` verify or a Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (shared states trio, query-key factories, billing feature folder, Playwright/Vitest stubs, 320px viewport, i18n scan list)
- [ ] No watch-mode flags in any automated command
- [ ] Feedback latency < 120s (quick loop)
- [ ] Backend-surface gaps (1)–(3) resolved or explicitly flagged in the affected plans
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
