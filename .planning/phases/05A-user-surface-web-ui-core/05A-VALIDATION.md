---
phase: 5A
slug: user-surface-web-ui-core
status: signed-off
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-12
updated: 2026-05-12
---

# Phase 5A — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Frontend-only UI phase: weight = standard frontend gates (`tsc`, ESLint, Vitest, `i18n:check`) + Playwright e2e for golden paths + key states (desktop + 320px) + a `frontend-design` visual-review note per authenticated screen. No backend test scaffolding — 5A adds/modifies no backend endpoint.
> Derived from `05A-RESEARCH.md` § "Validation Architecture". Updated by the `--reviews` replan pass: audit/ledger populated state moves to Vitest component tests (injected data); the e2e for those features covers the production "not yet available" state; top-up bank-account fields removed (the response carries none); route paths use the `(protected)/(app)/...` route-group layout.

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

> Task IDs are assigned by the planner; this is the requirement → test-artifact map the plans must wire to. Every new visible behavior gets a Vitest contract and/or a Playwright spec. Routes use the `(protected)/(app)/...` route-group layout (Plan 02's split); onboarding stays under `(protected)/onboarding/...`.

| Plan area | Wave | Requirement | Secure / expected behavior | Test type | Automated command / artifact | File Exists | Status |
|-----------|------|-------------|-----------------------------|-----------|------------------------------|-------------|--------|
| App shell | 2 | WEB-04 | Shell renders on every `(protected)/(app)` route; pause/balance/health visible (no horizontal scroll) at 1280px and 320px; shell never unmounts on client nav; onboarding chrome-suppressed (route-group split, not server-layout segment branching) | Playwright (driving from `/rules`, `/settings` at Wave 2; `/triage`/`/billing` shell checks live in the triage/billing specs) | `e2e/app-shell.spec.ts` | ✅ | ✅ green |
| Pause toggle (D-13) | 2 | WEB-04 | One query key (`triageKeys.pauseState()`); chrome toggle + `/settings` toggle + `PauseBanner` all read it; optimistic update + rollback; mutation `onSettled` also invalidates `accountQueryKeys.me()`; mutating via one hook updates all readers off the one cache entry | Playwright + Vitest | `e2e/pause-toggle.spec.ts` (cross-reader consistency on `/settings`); `features/triage/hooks/useToggleTriagePause.test.tsx` (rewrite — incl. "single write target" assertion) | ✅ | ✅ green |
| Credit balance | 2 | WEB-04 | Renders from `/api/billing/balance`; `refetchInterval ≈ 45s` actually fires and is NOT swallowed by the global 5-min `staleTime` (fake-timer Vitest asserts the queryFn re-invokes after ~45s); pill updates after a simulated top-up credit / invalidating action | Vitest (fake timers) + Playwright (route-fulfill + poll on a Wave-2 route) | `features/billing/hooks/useBillingBalance.test.tsx` (Plan 01, fake timers); `e2e/billing-balance.spec.ts` (Plan 02, on `/rules`/`/settings`) | ✅ | ✅ green |
| Connection health | 2 | WEB-04 | Healthy dot on `CONNECTED`; degraded + compact reconnect affordance on `DISCONNECTED` | Playwright (route-fulfill both states, on `/rules`/`/settings`) | `e2e/connection-health.spec.ts` | ✅ | ✅ green |
| Triage audit + undo | 3 | WEB-02 (partial) | **No backend audit-list endpoint** → `getAuditLog` returns a `{unavailable:true}` sentinel; `AuditLog` renders an "audit history not yet available" panel (distinct from the empty panel). Populated rows (0/1/N + 30-day boundary divider position + full-Reason-on-card) covered by a Vitest component test with **injected fixture data** (no endpoint). Undo on an in-window entry → `alert-dialog` confirm naming the inverse change → `POST …/audit/{auditId}/undo` → component fires a translated toast. Out-of-window entry → "Undo window closed" label (never hidden). E2e covers the production "not yet available" panel + `/triage` shell-presence + `?tab=` deep-linking. | Playwright (production state) + Vitest (populated, injected) | `e2e/triage-audit.spec.ts`; `features/triage/components/AuditLog.test.tsx` (injected data) | ✅ | ✅ green |
| Shadow mode + sender net | 3 | WEB-02 (partial) | Shadow toggle reads/writes `/api/tenant/triage/shadow-mode` with a turn-off confirm; sender list renders incl. empty; opt-in calls `…/sender-safety-net/{senderEmail}/opt-in` + updates row | Playwright + Vitest | `e2e/triage-shadow-senders.spec.ts`; `features/triage/components/SenderSafetyNetList.test.tsx` | ✅ | ✅ green |
| Billing top-up + ledger | 3 | WEB-02 (partial) | Balance shown (focal Display figure on `/billing`); top-up → `POST /api/billing/topup/intent` → VietQR `qrPayload` (copyable EMV text) + transfer `code` + `amountVnd` + `expiresAt` countdown (**NO QR dependency and NO bank-account/bank-name fields — the response carries none**) → simulated credit (balance rises; no intent-status endpoint) → success + balance up; `?code=`/sessionStorage rehydration; expired panel. **No backend ledger-history endpoint** → `useLedgerHistory` returns the `{unavailable:true}` sentinel; `LedgerHistory` renders a "transaction history isn't available yet" panel (distinct from the empty panel). Populated `LedgerTable` covered by a Vitest component test with injected data; the e2e covers the production "not yet available" state (may also mock a populated ledger to exercise `LedgerTable`) + `/billing` shell-presence. | Playwright (route-fulfill `topup/intent` + `balance`; production ledger state) + Vitest (populated `LedgerTable`, injected) | `e2e/billing-topup.spec.ts`; `features/billing/components/LedgerTable.test.tsx` (injected data) | ✅ | ✅ green |
| In-product privacy | 3 | WEB-03 | Authenticated route (`/settings/privacy`, under `(app)/`), reachable from a real shell nav element/link (the Settings-page "Privacy & data handling" link); renders vi + en; states no-stored-bodies / no-auto-send / BYOK; link to public `/privacy`; no `.zm-proto`/`.zm-auth` class | Playwright + Vitest (i18n parity) | `e2e/privacy-page.spec.ts`; `__tests__/i18n/messages.contract.test.ts` (extend) | ✅ | ✅ green |
| Convergence pass | 3 | WEB-02 (existing screens, partial) | rules / onboarding ×3 / settings render inside the shell (rules + settings under `(app)/`), on 1.6 base teal tokens, with shared `@/components/states` loading/empty/error primitives; no horizontal scroll at 320px; onboarding stays chrome-suppressed; no `.zm-proto`/`.zm-auth` on any authenticated screen; `/settings` pause control already single-sourced by Plan 02 (untouched in convergence) | Playwright (extend `e2e/rules.spec.ts`, `e2e/onboarding-routes.spec.ts`, `e2e/byok.spec.ts` — in-shell + 320px + no-clay-skin-class) | extend existing specs | ✅ | ✅ green |
| All | every | WEB-01 | Every new screen consumes the typed `openapi-fetch` client (no ad-hoc backend `fetch`; path params via typed `params.path`, not interpolation); `tsc` / ESLint / Vitest / `i18n:check` green | CI gates | full-suite command | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky/extend*

---

## Wave 0 Requirements (Plan 01)

- [x] `apps/web/components/states/{LoadingState,EmptyState,ErrorState}.tsx` — shared loading/empty/error trio (consumed by every new list + the convergence pass).
- [x] `apps/web/features/triage/query-keys.ts` — `triageKeys` factory (incl. `pauseState()`, `auditLog(...)`, `senderSafetyNet()`, `shadowMode()`).
- [x] `apps/web/features/billing/` — feature folder (`api/billing-api.ts`, `query-keys.ts`, `hooks/`, `messages.ts`) with explicit `{unavailable:true}` gap stubs and no bank-account-number/bank-name labels.
- [x] Playwright specs: `e2e/app-shell.spec.ts`, `e2e/pause-toggle.spec.ts`, `e2e/billing-balance.spec.ts`, `e2e/connection-health.spec.ts`, `e2e/triage-audit.spec.ts`, `e2e/triage-shadow-senders.spec.ts`, `e2e/billing-topup.spec.ts`, `e2e/privacy-page.spec.ts`.
- [x] Vitest specs: `useBillingBalance.test.tsx`, `useTopupCreditWatch.test.tsx`, `useToggleTriagePause.test.tsx`, `AuditLog.test.tsx`, `SenderSafetyNetList.test.tsx`, `LedgerTable.test.tsx`, and `messages.contract.test.ts`.
- [x] 320px responsive-floor assertions are covered per spec with `page.setViewportSize(...)`.
- [x] `apps/web/scripts/check-i18n.ts` `EN_SCAN_FILES` includes every Phase 5A English-literal-bearing component and route-group path.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual quality / brand-token correctness per screen (light + dark) | WEB-02/03/04, convergence | Pixel/brand judgment is not assertable in Playwright beyond "no horizontal scroll / element visible / no clay-skin class" | Run `frontend-design` skill review per screen; record a visual-review note (shell, `/triage` all tabs, `/billing`, top-up all states, `/settings/privacy`, converged rules/onboarding/settings); both color schemes read correctly; Phase 1.6 teal token contract honored; no `.zm-proto`/`.zm-auth` clay skin, no ad-hoc colors |
| No client-side data exposure | privacy constraint | Static rule check, not a behavioral test | Reviewer (PowerShell `Select-String`) over new components: no email bodies/addresses/prompts/completions/token bytes rendered or logged beyond owner-visible fields the backend explicitly returns |

---

## Backend-Surface Gaps (resolved-as-flagged; recorded in 05A-GAPS.md by Plan 06)

Per `05A-SPEC.md` / `05A-CONTEXT.md`: 5A adds/modifies no backend endpoint; missing endpoints/fields are **logged as gaps**, not built. Confirmed against `backend/api` controllers and `apps/web/lib/api/schema.d.ts`:

1. **Triage-audit list endpoint** — absent (`TriageAuditController` only has `…/audit/{auditId}/undo`). Degradation: `getAuditLog` → `{unavailable:true}` sentinel; `AuditLog` → "audit history not yet available" panel; undo flow + empty/error states ship; populated rows covered by `AuditLog.test.tsx` with injected data; e2e covers the production state.
2. **Billing ledger / transaction-history list endpoint** — absent (`BillingController` only has `/balance` + `/topup/intent`). Degradation: `useLedgerHistory` → `{unavailable:true}` sentinel; `LedgerHistory` → "transaction history isn't available yet" panel; populated `LedgerTable` covered by `LedgerTable.test.tsx` with injected data; e2e covers the production state.
3. **Top-up intent-status endpoint / `intentId` field** — absent. Degradation: `?intentId=` rehydration (D-15) → `?code=` + `sessionStorage`; "credited" inferred from `/api/billing/balance` rising (no status poll).
4. **Top-up bank-account fields not in `TopupIntentResponse`** — the response carries only `code`/`amountVnd`/`expiresAt`/`qrPayload`; no `accountNumber`/`accountName`/`bankName`/`transferContent` (`accountNumber` in the schema is on `SepayWebhookPayload`). Degradation: the top-up screen shows the VietQR `qrPayload` as copyable EMV text + transfer `code` + `amountVnd` + `expiresAt` countdown only; separate bank-account/bank-name copyable fields would need a static frontend config constant/env OR a backend change — both out of 5A's frontend-only scope; logged, not added.

Each has a documented degradation path; none blocks the phase.

---

## Validation Sign-Off

- [x] All tasks have an `<automated>` verify or a Wave 0 dependency
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (shared states trio, query-key factories, billing feature folder with `{unavailable:true}` gap stubs, Playwright/Vitest stubs, 320px viewport, i18n scan list with route-group paths)
- [x] No watch-mode flags in any automated command
- [x] Feedback latency < 120s (quick loop)
- [x] Backend-surface gaps (1)–(4) resolved-as-flagged in the affected plans + recorded in `05A-GAPS.md`
- [x] Route-group split verified against Next 16 docs; `EN_SCAN_FILES` required no fallback reconciliation
- [x] `git diff --exit-code` clean for `apps/web/lib/api/schema.d.ts`, public `/privacy`, and `backend/`
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** signed off (gsd-plan-execution, 2026-05-12)
