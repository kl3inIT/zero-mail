---
phase: 05A-user-surface-web-ui-core
plan: 04
type: execute
wave: 3
depends_on: [01, 02]
files_modified:
  - apps/web/app/(protected)/(app)/billing/page.tsx
  - apps/web/app/(protected)/(app)/billing/top-up/page.tsx
  - apps/web/features/billing/components/BalanceCard.tsx
  - apps/web/features/billing/components/LedgerHistory.tsx
  - apps/web/features/billing/components/LedgerTable.tsx
  - apps/web/features/billing/components/LedgerTable.test.tsx
  - apps/web/features/billing/components/TopupAmountForm.tsx
  - apps/web/features/billing/components/TopupInstructions.tsx
  - apps/web/features/billing/components/CopyableField.tsx
  - apps/web/features/billing/components/TopupSuccess.tsx
  - apps/web/features/billing/components/TopupExpired.tsx
  - apps/web/features/billing/components/TopupClient.tsx
  - apps/web/features/billing/messages.ts
  - apps/web/e2e/billing-topup.spec.ts
  - apps/web/package.json        # CONDITIONAL — only if a QR-rendering dep is added (verify current npm version first)
  - apps/web/pnpm-lock.yaml      # CONDITIONAL — same
autonomous: true
requirements: [WEB-01, WEB-02]
user_setup: []

must_haves:
  truths:
    - "Billing is its own /billing route (at app/(protected)/(app)/billing/page.tsx, not a /settings section) — a transactional surface (top-up intent, payment-callback target, credit ledger); BYOK stays under /settings (a credential preference, not a transaction) — D-07"
    - "A /billing page (inside the app shell) shows the current credit balance as the focal Display-type figure"
    - "A top-up flow at a dedicated /billing/top-up route (not a dismissible modal): amount entry -> POST /api/billing/topup/intent -> displays the VietQR `qrPayload` (rendered client-side as a scannable QR image and/or as a copyable EMV text string — never as HTML) + the transfer reference `code` + the exact `amountVnd` + an `expiresAt` countdown -> polls /api/billing/balance via refetchInterval (stopped once credited or expired) -> success state with the increased balance; expiry handled on-route with a clear 'intent expired — start a new top-up' panel; no custom stepper component (D-15). NOTE: `TopupIntentResponse` carries ONLY `code`/`amountVnd`/`expiresAt`/`qrPayload` — there is no `accountNumber`/`accountName`/`bankName`/`transferContent` field, so the top-up screen does NOT show separate bank-account / bank-name / account-holder fields; the VietQR payload already encodes the destination account + amount + content, so 'scan this QR with your banking app' is a complete flow."
    - "The pending intent rehydrates from a ?code= searchParam (sessionStorage-backed, standing in for the spec's ?intentId= since the backend exposes no intentId / intent-status endpoint) so a refresh / come-back-later resumes the same intent (D-15)"
    - "A paginated ledger/transaction-history list renders an empty state and (via the e2e mock / via LedgerTable.test.tsx injected data) a populated state; because no backend ledger-history endpoint exists, useLedgerHistory returns the Plan-01 `{unavailable:true}` sentinel and LedgerHistory renders a distinct 'transaction history isn't available yet' panel (NOT the 'no transactions yet' empty panel); the populated LedgerTable rendering path is covered by LedgerTable.test.tsx with injected fixture data, and the billing e2e covers the real production 'not yet available' state — no backend endpoint added, schema.d.ts unchanged"
    - "The raw qrPayload EMV string is never rendered as HTML; if a scannable QR image is shown it is rendered client-side from the payload (any new dep noted + version-verified)"
  artifacts:
    - path: "apps/web/app/(protected)/(app)/billing/page.tsx"
      provides: "Billing page on its own /billing route: BalanceCard (focal) + LedgerHistory + 'Top up credits' CTA -> /billing/top-up (D-07)"
    - path: "apps/web/app/(protected)/(app)/billing/top-up/page.tsx"
      provides: "<Suspense> -> TopupClient on a dedicated /billing/top-up route (not a modal); ?code= reader; amount -> instructions -> poll -> success/expired (D-15)"
    - path: "apps/web/features/billing/components/TopupInstructions.tsx"
      provides: "VietQR qrPayload (QR image + copyable EMV text) + copyable transfer code + amount + expiry countdown + balance poll until credited or expired (D-15) — NO separate bank-account/bank-name fields (the response carries none)"
    - path: "apps/web/features/billing/components/TopupClient.tsx"
      provides: "Top-up step machine with ?code=/sessionStorage rehydration so a refresh resumes the same pending intent (D-15)"
    - path: "apps/web/features/billing/components/LedgerHistory.tsx"
      provides: "Ledger list — renders a distinct 'transaction history isn't available yet' panel for the Plan-01 `{unavailable:true}` sentinel; LedgerTable is the renderer for populated data (e2e mock / component test)"
  key_links:
    - from: "apps/web/features/billing/components/TopupAmountForm.tsx"
      to: "/api/billing/topup/intent"
      via: "useCreateTopupIntent (dedicated /billing/top-up route, D-15)"
      pattern: "topup/intent"
    - from: "apps/web/features/billing/components/TopupClient.tsx"
      to: "/api/billing/balance"
      via: "useTopupCreditWatch (poll until credited/expired) + ?code= rehydration (D-15)"
      pattern: "useTopupCreditWatch"
    - from: "apps/web/features/billing/components/BalanceCard.tsx"
      to: "/api/billing/balance"
      via: "useBillingBalance (focal balance figure on the dedicated /billing route, D-07)"
      pattern: "useBillingBalance"
---

<objective>
Build the `features/billing` UI: a `/billing` page (at `app/(protected)/(app)/billing/page.tsx`, inside the app shell) whose focal element is the credit-balance figure (Display type), with a "Top up credits" CTA and a paginated ledger/transaction-history list; and a dedicated `/billing/top-up` route (D-15) running the inline top-up sequence — amount entry → `POST /api/billing/topup/intent` → display the VietQR `qrPayload` (QR image rendered client-side and/or copyable EMV text) + the transfer reference `code` + the exact `amountVnd` + an `expiresAt` countdown → poll `/api/billing/balance` until credited or expired → success state with the increased balance; expiry handled on-route; the pending intent rehydrates from a `?code=` searchParam (sessionStorage-backed, since the backend exposes no `intentId` and no intent-status endpoint — confirmed against `BillingController`). `TopupIntentResponse` carries NO bank-account-number / bank-name / account-holder-name fields, so the top-up screen does not show those as separate copyable fields — the VietQR payload already encodes the destination account, amount, and reference; "scan this QR with your banking app" is a complete flow. The billing ledger-history endpoint does not exist — `useLedgerHistory` returns the Plan-01 `{unavailable:true}` sentinel and `LedgerHistory` renders a distinct "transaction history isn't available yet" panel (not the empty panel); the populated `LedgerTable` rendering path is covered by `LedgerTable.test.tsx` with injected data, and the billing e2e covers the real production "not yet available" state. Do not add a backend endpoint or regenerate `schema.d.ts`.

Purpose: WEB-02 (the billing portion — note WEB-02 stays partial after 5A; the bank-fields gap and the ledger-history endpoint are recorded in 05A-GAPS.md).
Output: `/billing` + `/billing/top-up` pages, `TopupClient`, the billing components, `LedgerTable` Vitest spec, extended `billing` i18n, the `billing-topup` Playwright spec, an optional QR-rendering dependency (only if chosen — noted + version-verified).
</objective>

<reviewer_response>
Cross-AI review:
- #5 (Codex HIGH — `TopupIntentResponse` lacks bank-transfer fields; CONFIRMED against `apps/web/lib/api/schema.d.ts`): the top-up instructions screen renders QR + code + amount + expiry ONLY — no separate `accountNumber`/`accountName`/`bankName`/`transferContent` copyable fields (the schema has none; `accountNumber` there belongs to `SepayWebhookPayload`). The VietQR EMV payload already encodes the destination account + amount + content. Showing the raw bank account/name as separate fields would require a static frontend config constant/env (SePay merchant account is fixed config) OR a backend change — both are out of 5A's frontend-only scope, so it is logged as a 4th flagged gap in `05A-GAPS.md` and called out below; it is NOT added here. 05A-VALIDATION.md / 05A-UI-SPEC references that assumed bank fields are updated.
- #1 (Codex HIGH — gap-stub vs. mocked-e2e mismatch, ledger): `useLedgerHistory` returns the Plan-01 `{unavailable:true}` sentinel; `LedgerHistory` renders a distinct "transaction history isn't available yet" panel; the populated `LedgerTable` path is covered by `LedgerTable.test.tsx` with INJECTED fixture data; the billing e2e covers the real production "not yet available" state (it MAY still mock a populated ledger to exercise the `LedgerTable` render path, but the primary assertion is the degraded panel). `LedgerTable` accepts an optional injected-rows prop so the component test needs no endpoint.
- #7 (MEDIUM): `package.json`/`pnpm-lock.yaml` listed as CONDITIONALLY-owned (only if a QR dep is added; verify the current npm version first). The generated i18n bundles stay Plan-06-owned; this plan runs `i18n:build` locally for its gate only.
- Note: the `/billing` shell-presence smoke check that Plan 02 used to own now lives in this plan's `billing-topup.spec.ts`.
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
@.planning/phases/05A-user-surface-web-ui-core/05A-02-SUMMARY.md
@CLAUDE.md
@CONVENTIONS.md
@apps/web/AGENTS.md
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Build the /billing page (BalanceCard + LedgerHistory gap-degraded) + the top-up flow at /billing/top-up (QR + code + amount + expiry only)</name>
  <behavior>
    - TopupClient: reads useSearchParams().get('code'); if present and a matching intent is in sessionStorage -> rehydrates to the instructions step for that intent; otherwise starts at the amount step. On a created intent: persist the intent fields (code, amountVnd, expiresAt, qrPayload) in sessionStorage keyed by code, and router.replace('/billing/top-up?code='+code, { scroll:false }). Steps: amount entry -> instructions (VietQR + transfer code + amount + expiry countdown) -> on credit detected -> success; on expiry -> expired panel. Leaving and returning with the same ?code= resumes the instructions step.
    - TopupAmountForm: an amount input + a "Continue to payment" submit calling useCreateTopupIntent.mutate(amountVnd); validation errors render inline; on success advances to instructions.
    - TopupInstructions: renders the qrPayload (client-side as a scannable QR image AND/OR as a copyable EMV text string via CopyableField — never as HTML), plus a CopyableField for the transfer reference `code` and one for the exact `amountVnd` (each with a copy button + "Copied" feedback), plus a muted countdown to expiresAt; while waiting it uses useTopupCreditWatch to poll /api/billing/balance. It does NOT render a bank-account-number / bank-name / account-holder field — the response carries none; instead it shows guidance copy "Scan this QR with your banking app — it already includes the recipient, amount, and reference" (UI-SPEC copy).
    - TopupSuccess: heading "Credits added", the UI-SPEC body, the new balance figure (Display type), a "Back to billing" button -> /billing.
    - TopupExpired: an Alert (warning) with the UI-SPEC expired copy + a "Start a new top-up" button resetting to the amount step (and clearing the ?code=).
    - BalanceCard: shows the balance from useBillingBalance as the focal Display-type figure; loading -> a Skeleton; error -> ErrorState onRetry.
    - LedgerHistory: useLedgerHistory returns the Plan-01 `{unavailable:true}` sentinel -> LedgerHistory renders a distinct "transaction history isn't available yet" panel (NOT the EmptyState "No transactions yet" panel — that one is reserved for "endpoint exists but returned no rows"); a comment + the SUMMARY flag the gap. LedgerTable (the renderer) accepts an optional injected-rows prop so LedgerTable.test.tsx can exercise the populated render path without an endpoint; the e2e may also mock a populated ledger to exercise LedgerTable.
  </behavior>
  <read_first>
    - apps/web/app/(protected)/(app)/rules/page.tsx (thin page idiom — rules is under (app)/ per Plan 02), apps/web/app/(protected)/(app)/settings/page.tsx (Card-chain layout idiom for BalanceCard / LedgerHistory)
    - apps/web/features/rules/components/RuleComposer.tsx (form-with-submit idiom for TopupAmountForm)
    - apps/web/features/rules/components/RuleList.tsx (row model for LedgerTable), apps/web/components/ui/{table,alert,alert-dialog,card,input,button,badge,skeleton,sonner}.tsx
    - apps/web/components/states/{LoadingState,EmptyState,ErrorState}.tsx (Plan 01 — the "not yet available" panel is a distinct composition, not the EmptyState)
    - apps/web/features/billing/api/billing-api.ts, apps/web/features/billing/query-keys.ts, apps/web/features/billing/hooks/{useBillingBalance,useCreateTopupIntent,useTopupCreditWatch,useLedgerHistory}.ts (Plan 01 — note useLedgerHistory and getLedgerHistory return a `{unavailable:true}` sentinel; useTopupCreditWatch takes a baseline balance + expiresAt and stops polling on credit/expiry)
    - apps/web/lib/api/schema.d.ts (the `TopupIntentResponse` shape: `code?`, `amountVnd?`, `expiresAt?`, `qrPayload?` — NO `intentId`, NO image URL, NO `accountNumber`/`accountName`/`bankName`/`transferContent`; the `BillingBalanceResponse` shape), backend/api/src/main/java/com/zeromail/api/controllers/billing/BillingController.java (confirms only `/balance` + `/topup/intent`)
    - apps/web/features/gmail/components/ReconnectPrompt.tsx (the `Alert variant="warning"` + `AlertAction` idiom for TopupExpired)
    - apps/web/features/billing/messages.ts (the seeded `billing.*` keys from Plan 01 — extend; note Plan 01 already removed bank-account-number/bank-name labels and added a ledger-empty AND a ledger-unavailable copy pair), apps/web/features/billing/hooks/useBillingBalance.test.tsx (the Vitest harness idiom)
    - 05A-CONTEXT.md D-07 (own /billing route; BYOK stays in /settings), D-15 (dedicated /billing/top-up route, not a modal; `?intentId=` rehydration -> falls back to `?code=` + sessionStorage per RESEARCH A3/A6); 05A-UI-SPEC.md sections Copywriting (top-up amount CTA "Continue to payment", waiting/success/expired copy, the "scan this QR" guidance copy, ledger empty copy AND ledger-unavailable copy, "Top up credits"), Color (warning for expiry, success/green for credited, accent for primary CTA), Typography (Display type for the balance + success amount; mono for the transfer code + ledger amounts), Spacing (card padding, copy fields stack at 320px), Responsive (320px: copyable fields stack vertically full-width), Visual Hierarchy (balance figure focal on /billing; the QR + transfer code focal on the top-up waiting state; the new balance focal on success)
    - 05A-PATTERNS.md sections "features/billing/components/*", "app/(protected)/triage/page.tsx & billing/top-up/page.tsx (Suspense + search-param reader)" (note the pages now live under `(app)/`), "useTriageAuditLog / useLedgerHistory (BLOCKED)"
    - 05A-RESEARCH.md Pattern 4 (`?code=` under `<Suspense>`), Pitfall 2 (useSearchParams + `<Suspense>` in Next 16 — verify in node_modules/next/dist/docs/), Pitfall 5 (balance staleTime — already handled in useBillingBalance), Pitfall 6 (privacy), Open Questions 2 + 3, Architectural Responsibility Map row "QR rendering = Browser"
    - node_modules/next/dist/docs/ — `useSearchParams` + `<Suspense>` in Next 16
    - if adding a QR component: verify the chosen package's current version on npm before adding (per the global vendor-docs rule); `react-qr-code` (MIT, dependency-light) is the candidate — adding it is a planner-permitted decision, note it (with the version) in the SUMMARY; the flow is satisfied by the copyable EMV text alone, so a scannable QR image is optional.
  </read_first>
  <action>
    Invoke the `frontend-design` skill BEFORE writing any of these components; record `frontend-design` visual-review notes (desktop + 320px, light + dark) for: the `/billing` page (balance + ledger), the top-up amount step, the top-up instructions/waiting step (QR + code + amount + expiry), the top-up success step, the top-up expired panel — in the SUMMARY.
    Create `app/(protected)/(app)/billing/page.tsx` — a thin page (idiom from `(app)/rules/page.tsx`): renders `<BalanceCard/>` as the focal element + `<LedgerHistory/>` + a "Top up credits" primary CTA (`Button`, accent) linking to `/billing/top-up`. Create `app/(protected)/(app)/billing/top-up/page.tsx` — `export default function TopupPage() { return <Suspense fallback={<LoadingState/>}><TopupClient/></Suspense>; }`.
    Create `features/billing/components/TopupClient.tsx` (`"use client"`): per the behavior block — reads `?code=`, rehydrates from `sessionStorage` (keyed by `code`; the stored fields are bank-transfer instructions, not secrets — acceptable per RESEARCH A3), drives the amount → instructions → success/expired step machine, calls `router.replace('/billing/top-up?code='+code, { scroll:false })` after intent creation. No custom stepper component (D-15 — shadcn has none and the pay→confirm transition is webhook-driven).
    Create `features/billing/components/{TopupAmountForm,TopupInstructions,CopyableField,TopupSuccess,TopupExpired,BalanceCard,LedgerHistory,LedgerTable}.tsx` per the behavior block + the UI-SPEC. `CopyableField` = a small primitive (a labelled value + a copy `Button` + transient "Copied" feedback) — the rule-of-three applies (used for the transfer code, the amount, and the EMV text), so make it a real component. `TopupInstructions` renders the `qrPayload` as a client-side QR image (if a QR dep is added) and/or as a `CopyableField` EMV text string, plus a `CopyableField` for the transfer `code` and one for the `amountVnd`, plus the "scan this QR with your banking app — it already includes recipient, amount, and reference" guidance copy, plus the expiry countdown — and NO bank-account-number / bank-name / account-holder field. `LedgerHistory` uses `useLedgerHistory` (the Plan-01 `{unavailable:true}` stub): while loading -> `<LoadingState variant="rows"/>`; on the `{unavailable:true}` sentinel -> a CLEARLY-WORDED "transaction history isn't available yet" panel (a distinct composition — NOT the `EmptyState "No transactions yet"` panel, which is reserved for an existing-endpoint-zero-rows case) — a comment + the SUMMARY flag this as the documented degradation for the missing backend ledger-history endpoint; `LedgerTable` is the renderer (shadcn `Table`, mono for amounts, top-up rows green-soft per UI-SPEC) and accepts an optional injected-rows prop for tests / the e2e mock. The raw `qrPayload` EMV string is rendered ONLY as React text (and, for a QR image, only via a QR component that generates an `<svg>`/canvas from the string) — never via the dangerously-set-inner-HTML React prop, never as raw HTML.
    Create the Vitest spec `apps/web/features/billing/components/LedgerTable.test.tsx` — render `LedgerTable` with an injected rows fixture (no endpoint), assert it renders a row per ledger entry, mono amounts, and the top-up-row styling; also render `LedgerHistory` (mocking `useLedgerHistory` to return the `{unavailable:true}` sentinel) and assert it shows the "transaction history isn't available yet" panel, distinct from the empty panel.
    Extend `apps/web/features/billing/messages.ts` with all new `billing.*` keys (vi + en lock-step) — NO bank-account-number/bank-name labels — run `pnpm --filter web i18n:build` locally (do NOT edit `apps/web/scripts/check-i18n.ts` — Plan 01 owns the list; do NOT commit the generated bundles — Plan 06 owns them). If a QR dependency is added, verify its current version on npm first, run `pnpm --filter web add <pkg>`, and note the package + version in the SUMMARY; otherwise leave `package.json`/`pnpm-lock.yaml` unchanged.
  </action>
  <verify>
    <automated>cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm test -- features/billing/components</automated>
  </verify>
  <acceptance_criteria>
    - `app/(protected)/(app)/billing/page.tsx` renders `<BalanceCard/>` (focal) + `<LedgerHistory/>` + a "Top up credits" CTA linking to `/billing/top-up`.
    - `app/(protected)/(app)/billing/top-up/page.tsx` renders `<Suspense>` around `TopupClient`; `TopupClient.tsx` reads `useSearchParams().get('code')`, rehydrates from `sessionStorage`, and `router.replace`s `?code=` after intent creation.
    - `features/billing/components/{TopupAmountForm,TopupInstructions,CopyableField,TopupSuccess,TopupExpired,BalanceCard,LedgerHistory,LedgerTable}.tsx` all exist; `TopupAmountForm` calls `useCreateTopupIntent`; `TopupInstructions` uses `useTopupCreditWatch` and renders the `qrPayload` (QR image and/or EMV text) + a `CopyableField` for the transfer `code` + a `CopyableField` for the `amountVnd` + the "scan this QR" guidance copy + an expiry countdown, and renders NO bank-account-number / bank-name / account-holder field; `TopupSuccess` shows the new balance in Display type; `TopupExpired` uses the warning `Alert` idiom + a "Start a new top-up" reset.
    - `BalanceCard.tsx` shows the balance from `useBillingBalance` in Display type with a Skeleton-loading + `ErrorState` path.
    - `LedgerHistory.tsx` renders a distinct "transaction history isn't available yet" panel for the Plan-01 `{unavailable:true}` sentinel (NOT the `EmptyState`); a comment references the missing backend ledger-history endpoint. `LedgerTable.tsx` accepts an optional injected-rows prop.
    - `apps/web/features/billing/components/LedgerTable.test.tsx` exists and passes — covers the populated `LedgerTable` render via injected data and the `LedgerHistory` "not yet available" panel.
    - The `qrPayload` is never rendered via the dangerously-set-inner-HTML React prop; if a QR package was added it is recorded in the SUMMARY with its version and `package.json`/`pnpm-lock.yaml` reflect it; otherwise both are unchanged.
    - No hardcoded English literals in the new `features/billing/components/*` or the two billing pages (via `pnpm --filter web i18n:check`); all strings resolve from `billing.*`; no bank-account-number/bank-name label keys exist.
    - `apps/web/lib/api/schema.d.ts` is unchanged.
    - `cd apps/web && pnpm i18n:build && pnpm typecheck && pnpm lint && pnpm i18n:check` exit 0.
    - SUMMARY contains the `frontend-design` visual-review notes for the `/billing` page + the four top-up states, the documented ledger-history degradation path, the bank-fields gap (logged for 05A-GAPS.md), the `?code=`/sessionStorage rehydration approach, and whether a QR dependency (+ version) was added.
  </acceptance_criteria>
  <done>/billing + /billing/top-up built (QR + code + amount + expiry only, no bank-account fields); top-up flow works against the existing endpoints with `?code=` rehydration; ledger gap degraded to a distinct "not yet available" panel with the populated path tested via injected data; gates green; visual reviews recorded; no backend endpoint added.</done>
</task>

<task type="auto">
  <name>Task 2: Implement the billing-topup Playwright spec</name>
  <read_first>
    - apps/web/e2e/rules.spec.ts (serial mode; `page.route('http://localhost:8080/**', ...)` in-memory mock incl. `/me`; `fulfillJson`/`fulfillProblem`; session+locale cookies)
    - apps/web/e2e/mobile-topbar.spec.ts (320px viewport pattern); apps/web/playwright.config.ts (the 320px approach from 05A-01-SUMMARY)
    - apps/web/e2e/billing-topup.spec.ts (the Plan 01 stub to fill in); apps/web/e2e/billing-balance.spec.ts (Plan 02 — the chrome balance pill is covered there; this spec covers the /billing page + the top-up flow + the ledger + the /billing shell-presence smoke check)
    - 05A-VALIDATION.md section "Per-Task Verification Map" row "Billing top-up + ledger"
    - 05A-RESEARCH.md section "Validation Architecture" Test Map (the exact behaviors); note the ledger e2e primarily covers the real production "not yet available" panel (it may ALSO mock a populated ledger to exercise LedgerTable); the credit signal is the balance rising (no intent-status endpoint)
    - apps/web/lib/api/schema.d.ts (the `/api/billing/balance` + `/api/billing/topup/intent` shapes to mock — `TopupIntentResponse` has only code/amountVnd/expiresAt/qrPayload)
  </read_first>
  <action>
    Fill in the Plan-01 stub `e2e/billing-topup.spec.ts` using the `e2e/rules.spec.ts` harness (serial mode, in-memory mock keyed on pathname+method, always mock `/me`, session+locale cookies before `goto`, `waitForLoadState('networkidle')`). A top-of-file comment flags that (a) the billing ledger-history endpoint does not exist — the e2e covers the real production "transaction history isn't available yet" panel (a mocked populated ledger may also be used to exercise `LedgerTable`), (b) the top-up "credited" signal is inferred from `/api/billing/balance` rising because no intent-status endpoint exists, and (c) `TopupIntentResponse` carries no bank-account fields so the instructions screen shows QR + code + amount + expiry only (05A-RESEARCH.md A4/A6 + the bank-fields gap). Cases:
      - navigate to `/billing` — assert the app shell (sidebar + chrome region) renders on this page (the shell-presence smoke check that used to live in Plan 02); assert the balance figure renders (mock `/api/billing/balance`); assert the ledger renders the "transaction history isn't available yet" panel (the real production state); optionally, with a populated mocked ledger, assert `LedgerTable` rows render.
      - Click "Top up credits" -> `/billing/top-up`; enter an amount, submit "Continue to payment" -> a `POST /api/billing/topup/intent` is sent (mock it returning `{ code, amountVnd, expiresAt, qrPayload }`) -> the instructions step shows the `qrPayload` (text and/or QR image) + the copyable transfer `code` + the `amountVnd` + the "scan this QR" guidance + an expiry countdown; a copy button on a field gives "Copied" feedback; assert there is NO bank-account-number / bank-name field.
      - Simulate the credit: change the mocked `/api/billing/balance` to a higher value and let `useTopupCreditWatch`'s poll fire (advance time / wait for the interval) -> the success state ("Credits added") appears with the increased balance; "Back to billing" returns to `/billing` with the updated balance.
      - Reload `/billing/top-up?code=<the code>` -> the instructions step rehydrates (from sessionStorage) for the same intent (not a fresh amount step).
      - Mock the intent's `expiresAt` in the past (or let the countdown lapse) -> the "This top-up expired" panel appears with "Start a new top-up", which resets to the amount step and clears `?code=`.
    Run at 1280px and 320px (copyable fields stack vertically at 320px; no horizontal scroll).
  </action>
  <verify>
    <automated>cd apps/web && pnpm test:e2e -- billing-topup</automated>
  </verify>
  <acceptance_criteria>
    - `e2e/billing-topup.spec.ts` contains real (non-skipped) assertions covering: the app shell rendering on `/billing`; the `/billing` balance figure; the ledger "not yet available" panel (and optionally a mocked populated `LedgerTable`); the top-up amount → `POST /api/billing/topup/intent` → instructions (qrPayload + transfer code + amount + expiry + "scan this QR" guidance, NO bank-account fields) → simulated-credit → success (increased balance) flow; `?code=` rehydration on reload; the expired panel + "Start a new top-up" reset — at 1280px and 320px.
    - A top-of-file comment flags the absent ledger-history endpoint, the balance-rise-as-credit-signal degradation, and the no-bank-fields constraint.
    - `pnpm --filter web test:e2e` passes (including this spec).
  </acceptance_criteria>
  <done>Billing top-up + ledger behaviors covered by a passing Playwright spec at desktop + 320px; backend gaps flagged in the spec; the `/billing` shell-presence check lives here.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser → backend API | Balance read, top-up intent creation, balance-poll-for-credit cross here via the typed `openapi-fetch` client + session cookie + XSRF header. |
| backend response strings → React render | `qrPayload` (a raw VietQR EMV string), the transfer reference `code`, and amounts are rendered on the top-up screen and the ledger. |
| URL searchParams / sessionStorage → app state | `?code=` is read by `TopupClient`; intent fields are stored in `sessionStorage` keyed by `code`. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05A-12 | Tampering / CSRF | `useCreateTopupIntent` → `POST /api/billing/topup/intent` | mitigate | Goes through `lib/api/client.ts` with `xsrfHeader()`; no raw cross-origin `fetch`; the top-up e2e asserts the request method/path. |
| T-05A-13 | XSS via rendered backend strings | `TopupInstructions` rendering `qrPayload` / transfer code, `LedgerTable` rendering amounts | mitigate | `qrPayload` and all transfer fields rendered as React text children (and, for a QR image, only via a QR component that generates an `<svg>` from the string); never via the dangerously-set-inner-HTML React prop; never as raw HTML. |
| T-05A-14 | Information disclosure | `sessionStorage` of intent fields keyed by `code` | accept | The stored fields (code, amount, expiry, qrPayload) are bank-transfer instructions the backend already returned to this user — not secrets, not credentials, not PII (RESEARCH A3); cleared on a new top-up; tab-scoped. |
| T-05A-15 | Open redirect / injection via `?code=` | `TopupClient` reading `useSearchParams().get('code')` | mitigate | `?code=` is used only as a `sessionStorage` lookup key and is re-rendered as text; it is never used to build a URL, an `href`, or HTML; if no matching intent is in `sessionStorage`, the client falls back to the amount step (it does not re-fetch by code — no GET-intent endpoint). |
| T-05A-16 | Information disclosure | gap-flagged `LedgerHistory` / `useLedgerHistory` stub | accept | Stub returns the `{unavailable:true}` sentinel and calls no endpoint until the backend ships one; the real ledger surface is gap-deferred per the SPEC. |

No high-severity threats — frontend-only; all backend access via the typed client; `qrPayload` and transfer fields React-escaped (and only QR-component-rendered as SVG, never as HTML); `?code=` is a sessionStorage key, not a redirect target; no dangerously-set-inner-HTML React prop.
</threat_model>

<verification>
- `pnpm --filter web i18n:build` is run as part of the gate but the generated `i18n/messages/{vi,en}.json` are NOT in this plan's `files_modified` and must not be committed here — Plan 06 regenerates and commits the canonical bundles. The per-feature `messages.ts` files (which ARE owned here) are the source of truth.
- `cd apps/web && pnpm typecheck && pnpm lint && pnpm test && pnpm i18n:check && pnpm test:e2e` all exit 0.
- `apps/web/lib/api/schema.d.ts` unchanged; the billing ledger-history endpoint gap, the intent-status/`intentId` gap, AND the bank-account-fields gap are documented in `billing-api.ts` (Plan 01), `useLedgerHistory.ts` (Plan 01), `LedgerHistory.tsx`, `TopupInstructions.tsx`, the e2e spec comment, and the SUMMARY (for roll-up into 05A-GAPS.md by Plan 06).
- If a QR dependency was added: it is the only `apps/web/package.json`/`pnpm-lock.yaml` change, the version was verified against npm, and it is recorded in the SUMMARY; otherwise both are unchanged.
- Manual: walk `/billing` → `/billing/top-up` (amount → instructions → simulated credit → success) and reload `?code=` in a real browser at 1280px and 320px, light + dark — no horizontal scroll; copyable fields stack at 320px; the `qrPayload` shows as text/QR, never as injected markup; no bank-account-number field anywhere.
</verification>

<success_criteria>
- `/billing` (under `(app)/`) shows the balance as the focal Display figure with a "Top up credits" CTA and a ledger list (gap-degraded to a distinct "not yet available" panel); `/billing/top-up` runs the amount → intent → VietQR/code/amount/expiry instructions → poll → success (increased balance) flow with `?code=`/sessionStorage rehydration and an expired panel; no separate bank-account/bank-name fields (the response carries none); the ledger-history, intent-status, and bank-fields backend gaps are flagged with documented degradation paths; all gates green; visual reviews recorded; no backend endpoint added or schema regenerated.
</success_criteria>

<output>
After completion, create `.planning/phases/05A-user-surface-web-ui-core/05A-04-SUMMARY.md` (record: the `frontend-design` visual-review notes; the documented ledger-history degradation path; the bank-account-fields gap (for 05A-GAPS.md); the `?code=`/sessionStorage rehydration approach; the `LedgerTable` injected-rows prop shape; whether a QR dependency was added and which version; the resolved value of Open Questions 2 + 3 if anything was learned from the backend).
</output>
