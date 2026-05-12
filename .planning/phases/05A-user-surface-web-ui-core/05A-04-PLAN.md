---
phase: 05A-user-surface-web-ui-core
plan: 04
type: execute
wave: 3
depends_on: [01, 02]
files_modified:
  - apps/web/app/(protected)/billing/page.tsx
  - apps/web/app/(protected)/billing/top-up/page.tsx
  - apps/web/features/billing/components/BalanceCard.tsx
  - apps/web/features/billing/components/LedgerHistory.tsx
  - apps/web/features/billing/components/LedgerTable.tsx
  - apps/web/features/billing/components/TopupAmountForm.tsx
  - apps/web/features/billing/components/TopupInstructions.tsx
  - apps/web/features/billing/components/CopyableField.tsx
  - apps/web/features/billing/components/TopupSuccess.tsx
  - apps/web/features/billing/components/TopupExpired.tsx
  - apps/web/features/billing/components/TopupClient.tsx
  - apps/web/features/billing/messages.ts
  - apps/web/i18n/messages/vi.json
  - apps/web/i18n/messages/en.json
  - apps/web/scripts/check-i18n.ts
  - apps/web/e2e/billing-topup.spec.ts
  - apps/web/package.json
autonomous: true
requirements: [WEB-01, WEB-02]
user_setup: []

must_haves:
  truths:
    - "A /billing page (inside the app shell) shows the current credit balance as the focal Display-type figure"
    - "A top-up flow at /billing/top-up: amount entry -> POST /api/billing/topup/intent -> displays the VietQR payload + copyable bank-transfer fields (account number, memo/reference code, exact amount) -> polls /api/billing/balance until credited or expired -> success state with the increased balance; an expired intent shows a clear 'intent expired — start a new top-up' panel"
    - "The pending intent rehydrates from a ?code= searchParam (sessionStorage-backed) so a refresh / come-back-later resumes the same intent"
    - "A paginated ledger/transaction-history list renders an empty state and a populated state; because no backend ledger-history endpoint exists it is flagged blocked-on-backend with the documented degradation (empty / 'transaction history coming soon' panel) — no backend endpoint added, schema.d.ts unchanged"
    - "The raw qrPayload EMV string is never rendered as HTML; if a scannable QR image is shown it is rendered client-side from the payload (any new dep noted)"
  artifacts:
    - path: "apps/web/app/(protected)/billing/page.tsx"
      provides: "Billing page: BalanceCard (focal) + LedgerHistory + 'Top up credits' CTA -> /billing/top-up"
    - path: "apps/web/app/(protected)/billing/top-up/page.tsx"
      provides: "<Suspense> -> TopupClient (?code= reader; amount -> instructions -> poll -> success/expired)"
    - path: "apps/web/features/billing/components/TopupInstructions.tsx"
      provides: "VietQR payload + copyable account/memo/amount fields + expiry countdown"
    - path: "apps/web/features/billing/components/LedgerHistory.tsx"
      provides: "Ledger list (gap-degraded to empty/'coming soon' until a backend endpoint exists)"
  key_links:
    - from: "apps/web/features/billing/components/TopupAmountForm.tsx"
      to: "/api/billing/topup/intent"
      via: "useCreateTopupIntent"
      pattern: "topup/intent"
    - from: "apps/web/features/billing/components/TopupClient.tsx"
      to: "/api/billing/balance"
      via: "useTopupCreditWatch (poll until credited/expired)"
      pattern: "useTopupCreditWatch"
    - from: "apps/web/features/billing/components/BalanceCard.tsx"
      to: "/api/billing/balance"
      via: "useBillingBalance"
      pattern: "useBillingBalance"
---

<objective>
Build the `features/billing` UI: a `/billing` page (inside the app shell) whose focal element is the credit-balance figure (Display type), with a "Top up credits" CTA and a paginated ledger/transaction-history list; and a dedicated `/billing/top-up` route (D-15) running the inline top-up sequence — amount entry → `POST /api/billing/topup/intent` → display the VietQR payload + copyable bank-transfer fields → poll `/api/billing/balance` until credited or expired → success state with the increased balance; expiry handled on-route; the pending intent rehydrates from a `?code=` searchParam (sessionStorage-backed, since the backend exposes no `intentId` and no intent-status endpoint — confirmed against `BillingController`). The billing ledger-history endpoint does not exist — flag it as a blocked-on-backend gap and degrade the ledger list to an empty / "transaction history coming soon" panel. Do not add a backend endpoint or regenerate `schema.d.ts`.

Purpose: WEB-02 (the billing portion).
Output: `/billing` + `/billing/top-up` pages, `TopupClient`, the billing components, extended `billing` i18n, the `billing-topup` Playwright spec, an optional QR-rendering dependency (only if chosen — noted).
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
@.planning/phases/05A-user-surface-web-ui-core/05A-02-SUMMARY.md
@CLAUDE.md
@CONVENTIONS.md
@apps/web/AGENTS.md
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Build the /billing page (BalanceCard + LedgerHistory gap-degraded) + the top-up flow at /billing/top-up</name>
  <behavior>
    - TopupClient: reads useSearchParams().get('code'); if present and a matching intent is in sessionStorage -> rehydrates to the instructions step for that intent; otherwise starts at the amount step. On a created intent: persist the intent fields (code, amountVnd, expiresAt, qrPayload) in sessionStorage keyed by code, and router.replace('/billing/top-up?code='+code, { scroll:false }). Steps: amount entry -> instructions (VietQR + copyable fields + expiry countdown) -> on credit detected -> success; on expiry -> expired panel. Leaving and returning with the same ?code= resumes the instructions step.
    - TopupAmountForm: an amount input + a "Continue to payment" submit calling useCreateTopupIntent.mutate(amountVnd); validation errors render inline; on success advances to instructions.
    - TopupInstructions: renders the qrPayload (as text and, if a QR component is chosen, as a scannable image rendered client-side from the payload — never as HTML), plus CopyableField rows for account number, memo/reference code, exact amount (each with a copy button + "Copied" feedback), plus a muted countdown to expiresAt; while waiting it uses useTopupCreditWatch to poll /api/billing/balance.
    - TopupSuccess: heading "Credits added", the UI-SPEC body, the new balance figure (Display type), a "Back to billing" button -> /billing.
    - TopupExpired: an Alert (warning) with the UI-SPEC expired copy + a "Start a new top-up" button resetting to the amount step (and clearing the ?code=).
    - BalanceCard: shows the balance from useBillingBalance as the focal Display-type figure; loading -> a Skeleton; error -> ErrorState onRetry.
    - LedgerHistory: until a backend ledger-history endpoint exists, renders the EmptyState "No transactions yet" (UI-SPEC copy) / a "transaction history coming soon" variant — the useInfiniteQuery is the Plan-01 gap-flagged stub; a comment + the SUMMARY flag the gap. (No populated-state assertion is required against a real endpoint; the e2e mocks a populated ledger to exercise the LedgerTable rendering path.)
  </behavior>
  <read_first>
    - apps/web/app/(protected)/rules/page.tsx (thin page idiom), apps/web/app/(protected)/settings/page.tsx (Card-chain layout idiom for BalanceCard / LedgerHistory)
    - apps/web/features/rules/components/RuleComposer.tsx (form-with-submit idiom for TopupAmountForm)
    - apps/web/features/rules/components/RuleList.tsx (row model for LedgerTable), apps/web/components/ui/{table,alert,alert-dialog,card,input,button,badge,skeleton,sonner}.tsx
    - apps/web/components/states/{LoadingState,EmptyState,ErrorState}.tsx (Plan 01)
    - apps/web/features/billing/api/billing-api.ts, apps/web/features/billing/query-keys.ts, apps/web/features/billing/hooks/{useBillingBalance,useCreateTopupIntent,useTopupCreditWatch,useLedgerHistory}.ts (Plan 01 — note useLedgerHistory and getLedgerHistory are gap-flagged stubs; useTopupCreditWatch takes a baseline balance + expiresAt and stops polling on credit/expiry)
    - apps/web/lib/api/schema.d.ts (the `TopupIntentResponse` shape: `code?`, `amountVnd?`, `expiresAt?`, `qrPayload?` — NO `intentId`, NO image URL; the `BillingBalanceResponse` shape), backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java (confirms only `/balance` + `/topup/intent`)
    - apps/web/features/gmail/components/ReconnectPrompt.tsx (the `Alert variant="warning"` + `AlertAction` idiom for TopupExpired)
    - apps/web/features/billing/messages.ts (the seeded `billing.*` keys from Plan 01 — extend), apps/web/features/billing/hooks/useBillingBalance.test.tsx (the Vitest harness idiom, if a component test is added)
    - 05A-CONTEXT.md D-07 (own /billing route; BYOK stays in /settings), D-15 (dedicated /billing/top-up route, not a modal; `?intentId=` rehydration -> falls back to `?code=` + sessionStorage per RESEARCH A3/A6); 05A-UI-SPEC.md sections Copywriting (top-up amount CTA "Continue to payment", waiting/success/expired copy, ledger empty copy, "Top up credits"), Color (warning for expiry, success/green for credited, accent for primary CTA), Typography (Display type for the balance + success amount; mono for the memo code + account number + ledger amounts), Spacing (card padding, copy fields stack at 320px), Responsive (320px: copyable fields stack vertically full-width), Visual Hierarchy (balance figure focal on /billing; the copyable VietQR block focal on the top-up waiting state; the new balance focal on success)
    - 05A-PATTERNS.md sections "features/billing/components/*", "app/(protected)/triage/page.tsx & billing/top-up/page.tsx (Suspense + search-param reader)", "useTriageAuditLog / useLedgerHistory (BLOCKED)"
    - 05A-RESEARCH.md Pattern 4 (`?code=` under `<Suspense>`), Pitfall 2 (useSearchParams + `<Suspense>` in Next 16 — verify in node_modules/next/dist/docs/), Pitfall 5 (balance staleTime — already handled in useBillingBalance), Pitfall 6 (privacy), Open Questions 2 + 3, Architectural Responsibility Map row "QR rendering = Browser"
    - node_modules/next/dist/docs/ — `useSearchParams` + `<Suspense>` in Next 16
    - if adding a QR component: verify the chosen package's current version on npm before adding (per the global vendor-docs rule); `react-qr-code` (MIT, dependency-light) is the candidate — adding it is a planner decision, note it in the SUMMARY; the SPEC is satisfied by the copyable bank fields alone, so a scannable QR is optional.
  </read_first>
  <action>
    Invoke the `frontend-design` skill BEFORE writing any of these components; record `frontend-design` visual-review notes (desktop + 320px, light + dark) for: the `/billing` page (balance + ledger), the top-up amount step, the top-up instructions/waiting step, the top-up success step, the top-up expired panel — in the SUMMARY.
    Create `app/(protected)/billing/page.tsx` — a thin page (idiom from `rules/page.tsx`): renders `<BalanceCard/>` as the focal element + `<LedgerHistory/>` + a "Top up credits" primary CTA (`Button`, accent) linking to `/billing/top-up`. Create `app/(protected)/billing/top-up/page.tsx` — `export default function TopupPage() { return <Suspense fallback={<LoadingState/>}><TopupClient/></Suspense>; }`.
    Create `features/billing/components/TopupClient.tsx` (`"use client"`): per the behavior block — reads `?code=`, rehydrates from `sessionStorage` (keyed by `code`; the stored fields are bank-transfer instructions, not secrets — acceptable per RESEARCH A3), drives the amount → instructions → success/expired step machine, calls `router.replace('/billing/top-up?code='+code, { scroll:false })` after intent creation. No custom stepper component (D-15 — shadcn has none and the pay→confirm transition is webhook-driven).
    Create `features/billing/components/{TopupAmountForm,TopupInstructions,CopyableField,TopupSuccess,TopupExpired,BalanceCard,LedgerHistory,LedgerTable}.tsx` per the behavior block + the UI-SPEC. `CopyableField` = a small primitive (a labelled value + a copy `Button` + transient "Copied" feedback) — the rule-of-three likely applies across account/memo/amount, so make it a real component. `LedgerHistory` uses `useLedgerHistory` (the Plan-01 gap stub): while loading -> `<LoadingState variant="rows"/>`; on the "not yet available" sentinel/empty page -> `<EmptyState heading="No transactions yet" body=.../>` plus a clearly-worded "transaction history isn't available yet" note — a comment + the SUMMARY flag this as the documented degradation for the missing backend ledger-history endpoint; `LedgerTable` is the renderer (shadcn `Table`, mono for amounts, top-up rows green-soft per UI-SPEC) used once a real endpoint exists or when the e2e mocks a populated ledger. The raw `qrPayload` EMV string is rendered ONLY as React text (and, if a QR component is chosen, as an `<svg>`/canvas the component generates from the string) — never via the dangerously-set-inner-HTML React prop, never as raw HTML.
    Extend `apps/web/features/billing/messages.ts` with all new `billing.*` keys (vi + en lock-step), run `pnpm --filter web i18n:build`, and add the new `features/billing/components/*.tsx`, `features/billing/components/TopupClient.tsx`, `app/(protected)/billing/page.tsx`, `app/(protected)/billing/top-up/page.tsx` paths to `EN_SCAN_FILES` per Plan 01's SUMMARY. If a QR dependency is added, run it through `pnpm --filter web add <pkg>` (verify the current version on npm first) and note it in the SUMMARY; otherwise leave `package.json` unchanged.
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check</automated>
  </verify>
  <acceptance_criteria>
    - `app/(protected)/billing/page.tsx` renders `<BalanceCard/>` (focal) + `<LedgerHistory/>` + a "Top up credits" CTA linking to `/billing/top-up`.
    - `app/(protected)/billing/top-up/page.tsx` renders `<Suspense>` around `TopupClient`; `TopupClient.tsx` reads `useSearchParams().get('code')`, rehydrates from `sessionStorage`, and `router.replace`s `?code=` after intent creation.
    - `features/billing/components/{TopupAmountForm,TopupInstructions,CopyableField,TopupSuccess,TopupExpired,BalanceCard,LedgerHistory,LedgerTable}.tsx` all exist; `TopupAmountForm` calls `useCreateTopupIntent`; `TopupInstructions` uses `useTopupCreditWatch` and renders the `qrPayload` + copyable account/memo/amount fields + an expiry countdown; `TopupSuccess` shows the new balance in Display type; `TopupExpired` uses the warning `Alert` idiom + a "Start a new top-up" reset.
    - `BalanceCard.tsx` shows the balance from `useBillingBalance` in Display type with a Skeleton-loading + `ErrorState` path.
    - `LedgerHistory.tsx` renders `EmptyState`/"coming soon" via the Plan-01 gap-flagged `useLedgerHistory`; a comment references the missing backend ledger-history endpoint.
    - The `qrPayload` is never rendered via the dangerously-set-inner-HTML React prop; if a QR package was added it is recorded in the SUMMARY and `package.json` reflects it.
    - No hardcoded English literals in the new `features/billing/components/*` or the two billing pages (via `pnpm --filter web i18n:check`); all strings resolve from `billing.*`.
    - `apps/web/lib/api/schema.d.ts` is unchanged.
    - `cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check` exit 0.
    - SUMMARY contains the `frontend-design` visual-review notes for the `/billing` page + the four top-up states, the documented ledger-history degradation path, the `?code=`/sessionStorage rehydration approach, and whether a QR dependency was added.
  </acceptance_criteria>
  <done>/billing + /billing/top-up built; top-up flow works against the existing endpoints with `?code=` rehydration; ledger gap degraded; gates green; visual reviews recorded; no backend endpoint added.</done>
</task>

<task type="auto">
  <name>Task 2: Implement the billing-topup Playwright spec</name>
  <read_first>
    - apps/web/e2e/rules.spec.ts (serial mode; `page.route('http://localhost:8080/**', ...)` in-memory mock incl. `/me`; `fulfillJson`/`fulfillProblem`; session+locale cookies)
    - apps/web/e2e/mobile-topbar.spec.ts (320px viewport pattern); apps/web/playwright.config.ts (the 320px approach from 05A-01-SUMMARY)
    - apps/web/e2e/billing-topup.spec.ts (the Plan 01 stub to fill in); apps/web/e2e/billing-balance.spec.ts (Plan 02 — the chrome balance pill is covered there; this spec covers the /billing page + the top-up flow + the ledger)
    - 05A-VALIDATION.md section "Per-Task Verification Map" row "Billing top-up + ledger"
    - 05A-RESEARCH.md section "Validation Architecture" Test Map (the exact behaviors); note the ledger-history response is MOCKED (no real endpoint) and the gap flagged in a spec comment; the credit signal is the balance rising (no intent-status endpoint)
    - apps/web/lib/api/schema.d.ts (the `/api/billing/balance` + `/api/billing/topup/intent` shapes to mock)
  </read_first>
  <action>
    Fill in the Plan-01 stub `e2e/billing-topup.spec.ts` using the `e2e/rules.spec.ts` harness (serial mode, in-memory mock keyed on pathname+method, always mock `/me`, session+locale cookies before `goto`, `waitForLoadState('networkidle')`). A top-of-file comment flags that (a) the billing ledger-history endpoint does not exist and its response is mocked here, and (b) the top-up "credited" signal is inferred from `/api/billing/balance` rising because no intent-status endpoint exists (05A-RESEARCH.md A4/A6). Cases:
      - `/billing` page shows the balance figure (mock `/api/billing/balance`); the ledger renders the empty state when the (mocked) ledger response is empty AND a populated `LedgerTable` when the (mocked) ledger response has rows.
      - Click "Top up credits" -> `/billing/top-up`; enter an amount, submit "Continue to payment" -> a `POST /api/billing/topup/intent` is sent (mock it returning `{ code, amountVnd, expiresAt, qrPayload }`) -> the instructions step shows the `qrPayload` + the copyable account/memo/amount fields + an expiry countdown; a copy button on a field gives "Copied" feedback.
      - Simulate the credit: change the mocked `/api/billing/balance` to a higher value and let `useTopupCreditWatch`'s poll fire (advance time / wait for the interval) -> the success state ("Credits added") appears with the increased balance; "Back to billing" returns to `/billing` with the updated balance.
      - Reload `/billing/top-up?code=<the code>` -> the instructions step rehydrates (from sessionStorage) for the same intent (not a fresh amount step).
      - Mock the intent's `expiresAt` in the past (or let the countdown lapse) -> the "This top-up expired" panel appears with "Start a new top-up", which resets to the amount step and clears `?code=`.
    Run at 1280px and 320px (copyable fields stack vertically at 320px; no horizontal scroll).
  </action>
  <verify>
    <automated>cd apps/web && pnpm test:e2e -- billing-topup</automated>
  </verify>
  <acceptance_criteria>
    - `e2e/billing-topup.spec.ts` contains real (non-skipped) assertions covering: the `/billing` balance figure; the ledger empty + populated states (mocked); the top-up amount → `POST /api/billing/topup/intent` → instructions (qrPayload + copyable fields + countdown) → simulated-credit → success (increased balance) flow; `?code=` rehydration on reload; the expired panel + "Start a new top-up" reset — at 1280px and 320px.
    - A top-of-file comment flags the mocked-because-absent ledger-history endpoint and the balance-rise-as-credit-signal degradation.
    - `pnpm --filter web test:e2e` passes (including this spec).
  </acceptance_criteria>
  <done>Billing top-up + ledger behaviors covered by a passing Playwright spec at desktop + 320px; backend gaps flagged in the spec.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → backend API | Balance read, top-up intent creation, balance-poll-for-credit cross here via the typed `openapi-fetch` client + session cookie + XSRF header. |
| backend response strings → React render | `qrPayload` (a raw VietQR EMV string), the memo/reference code, account number, and amounts are rendered on the top-up screen and the ledger. |
| URL searchParams / sessionStorage → app state | `?code=` is read by `TopupClient`; intent fields are stored in `sessionStorage` keyed by `code`. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05A-12 | Tampering / CSRF | `useCreateTopupIntent` → `POST /api/billing/topup/intent` | mitigate | Goes through `lib/api/client.ts` with `xsrfHeader()`; no raw cross-origin `fetch`; the top-up e2e asserts the request method/path. |
| T-05A-13 | XSS via rendered backend strings | `TopupInstructions` rendering `qrPayload` / memo / account, `LedgerTable` rendering amounts | mitigate | `qrPayload` and all transfer fields rendered as React text children (and, for a QR image, only via a QR component that generates an `<svg>` from the string); never via the dangerously-set-inner-HTML React prop; never as raw HTML. Verified in the acceptance criteria + the threat register. |
| T-05A-14 | Information disclosure | `sessionStorage` of intent fields keyed by `code` | accept | The stored fields (code, amount, expiry, qrPayload) are bank-transfer instructions the backend already returned to this user — not secrets, not credentials, not PII (RESEARCH A3); cleared on a new top-up; tab-scoped. |
| T-05A-15 | Open redirect / injection via `?code=` | `TopupClient` reading `useSearchParams().get('code')` | mitigate | `?code=` is used only as a `sessionStorage` lookup key and is re-rendered as text; it is never used to build a URL, an `href`, or HTML; if no matching intent is in `sessionStorage`, the client falls back to the amount step (it does not re-fetch by code — no GET-intent endpoint). |
| T-05A-16 | Information disclosure | gap-flagged `LedgerHistory` / `useLedgerHistory` stub | accept | Stub returns empty data and calls no endpoint until the backend ships one; the real ledger surface is gap-deferred per the SPEC. |

No high-severity threats — frontend-only; all backend access via the typed client; `qrPayload` and transfer fields React-escaped (and only QR-component-rendered as SVG, never as HTML); `?code=` is a sessionStorage key, not a redirect target; no dangerously-set-inner-HTML React prop.
</threat_model>

<verification>
- `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
- `apps/web/lib/api/schema.d.ts` unchanged; the billing ledger-history endpoint gap and the intent-status/`intentId` gap are documented in `billing-api.ts` (Plan 01), `useLedgerHistory.ts` (Plan 01), `LedgerHistory.tsx`, the e2e spec comment, and the SUMMARY.
- If a QR dependency was added: it is the only `apps/web/package.json` change, the version was verified against npm, and it is recorded in the SUMMARY; otherwise `package.json` is unchanged.
- Manual: walk `/billing` → `/billing/top-up` (amount → instructions → simulated credit → success) and reload `?code=` in a real browser at 1280px and 320px, light + dark — no horizontal scroll; copyable fields stack at 320px; the `qrPayload` shows as text/QR, never as injected markup.
</verification>

<success_criteria>
- `/billing` shows the balance as the focal Display figure with a "Top up credits" CTA and a ledger list (gap-degraded to empty/"coming soon"); `/billing/top-up` runs the amount → intent → VietQR/bank-transfer instructions → poll → success (increased balance) flow with `?code=`/sessionStorage rehydration and an expired panel; the ledger-history and intent-status backend gaps are flagged with documented degradation paths; all gates green; visual reviews recorded; no backend endpoint added or schema regenerated.
</success_criteria>

<output>
After completion, create `.planning/phases/05A-user-surface-web-ui-core/05A-04-SUMMARY.md` (record: the `frontend-design` visual-review notes; the documented ledger-history degradation path; the `?code=`/sessionStorage rehydration approach; whether a QR dependency was added and which version; any `EN_SCAN_FILES` paths added; the resolved value of Open Questions 2 + 3 if anything was learned from the backend).
</output>
