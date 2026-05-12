---
phase: 05A-user-surface-web-ui-core
plan: 04
subsystem: ui
tags: [nextjs, react, shadcn, tanstack-query, playwright, billing, vietqr]

requires:
  - phase: 05A-user-surface-web-ui-core
    provides: Plans 01-02 billing hooks, app shell, chrome balance, shared states, and shadcn primitives
provides:
  - Dedicated /billing route with focal balance card, top-up CTA, and ledger unavailable degradation
  - Dedicated /billing/top-up route with amount, VietQR EMV payload, code, amount, expiry, success, and expired states
  - SessionStorage-backed ?code= top-up rehydration using the existing response shape
  - LedgerTable populated-render coverage through injected rows
  - Desktop and 320px Playwright coverage for billing and top-up flows
affects: [phase-05A, apps-web, billing, ledger-history, top-up, vietqr]

tech-stack:
  added: []
  patterns:
    - Client-only pending intent rehydration via useSyncExternalStore + sessionStorage
    - Missing backend list endpoint degraded through an unavailable sentinel and component injected rows
    - Real-browser billing credit signal tested by balance-rise reload on the pending code route

key-files:
  created:
    - apps/web/app/(protected)/(app)/billing/page.tsx
    - apps/web/app/(protected)/(app)/billing/top-up/page.tsx
    - apps/web/features/billing/components/BalanceCard.tsx
    - apps/web/features/billing/components/CopyableField.tsx
    - apps/web/features/billing/components/LedgerHistory.tsx
    - apps/web/features/billing/components/LedgerTable.tsx
    - apps/web/features/billing/components/LedgerTable.test.tsx
    - apps/web/features/billing/components/TopupAmountForm.tsx
    - apps/web/features/billing/components/TopupClient.tsx
    - apps/web/features/billing/components/TopupExpired.tsx
    - apps/web/features/billing/components/TopupInstructions.tsx
    - apps/web/features/billing/components/TopupSuccess.tsx
  modified:
    - apps/web/features/billing/messages.ts
    - apps/web/e2e/billing-topup.spec.ts

key-decisions:
  - "Open Question 2 resolved: BillingController has no ledger-history endpoint; LedgerHistory renders a distinct unavailable panel and LedgerTable's populated path is covered with injected rows."
  - "Open Question 3 resolved: TopupIntentResponse has no intentId and there is no intent-status endpoint; /billing/top-up uses ?code= plus sessionStorage and treats a balance rise as the credited signal."
  - "No QR dependency was added. The VietQR qrPayload is rendered as copyable React text only, never as HTML."
  - "No bank account/name/account-holder fields are rendered because TopupIntentResponse exposes only code, amountVnd, expiresAt, and qrPayload."

patterns-established:
  - "For missing payment history endpoints, distinguish unavailable from empty so the UI does not imply a real zero-transaction ledger."
  - "For App Router routes that rehydrate browser storage, use a hydration-stable client snapshot rather than reading sessionStorage during the first render."
  - "For this Base UI Button wrapper, submit-like actions should use explicit type=\"button\" click handlers while retaining form submit for Enter."

requirements-completed: [WEB-01, WEB-02]

duration: 95min
completed: 2026-05-12
---

# Phase 05A Plan 04: Billing Top-Up Surface Summary

**Dedicated billing and VietQR top-up UI with backend-gap-safe ledger degradation**

## Performance

- **Duration:** 95 min
- **Started:** 2026-05-12T11:49:21Z
- **Completed:** 2026-05-12T12:44:27Z
- **Tasks:** 2
- **Files modified:** 14 app/test files

## Accomplishments

- Added `/billing` inside the protected app shell with a focal credit balance figure, held-credit detail, refresh cadence, and top-up CTA.
- Added `/billing/top-up` with amount entry, top-up intent creation, copyable transfer code, exact VND amount, copyable VietQR EMV payload, expiry countdown, success state, and expired reset state.
- Implemented `?code=` + sessionStorage rehydration for pending intents because the backend exposes no `intentId` or intent-status endpoint.
- Rendered ledger history as a distinct "transaction history isn't available yet" panel because no backend ledger endpoint exists.
- Added `LedgerTable.test.tsx` for populated injected ledger rows and `billing-topup.spec.ts` for desktop and 320px browser coverage.

## Frontend Design Notes

- `/billing` page: operational SaaS layout, not a marketing screen. The balance card is the focal Display-scale element, with transaction history beside it on desktop and stacked below at 320px.
- Top-up amount step: compact payment task card with one numeric input and a single accent action. The transfer preview stays low-emphasis so the CTA remains primary.
- Top-up instructions/waiting step: QR instructions, transfer reference, exact VND amount, and EMV payload are grouped as copyable fields. At 320px the copy controls stack full-width with no horizontal overflow.
- Top-up success step: green semantic icon plus Display-scale new balance, followed by one "Back to billing" action. The state reads as confirmation, not a modal.
- Top-up expired panel: warning alert treatment with one reset action; it clears the query string and returns to the amount step.
- Light/dark review: all billing surfaces use existing token classes, neutral card backgrounds, semantic green/warning accents only for state, and mono text for code/amount values.

## Task Commits

1. **Task 1: Billing page and top-up components** - `c84fc47` (feat)
2. **Task 2: Billing top-up Playwright coverage** - `7e44384` (test)

## Files Created/Modified

- `apps/web/app/(protected)/(app)/billing/page.tsx` - protected billing page with balance, ledger degradation, and top-up CTA.
- `apps/web/app/(protected)/(app)/billing/top-up/page.tsx` - dedicated top-up route with `<Suspense>` around `TopupClient`.
- `BalanceCard.tsx` - focal balance figure using `useBillingBalance`, skeleton loading, and retryable error state.
- `TopupClient.tsx` - top-up state machine, `?code=` search param reader, sessionStorage rehydration, success/expired transitions.
- `TopupAmountForm.tsx` - amount validation and `useCreateTopupIntent` mutation.
- `TopupInstructions.tsx` - copyable transfer code, VND amount, EMV payload, countdown, and balance-watch handoff.
- `TopupSuccess.tsx`, `TopupExpired.tsx` - terminal states with semantic headings.
- `CopyableField.tsx` - reusable copy primitive for transfer code, VND amount, and EMV payload.
- `LedgerHistory.tsx`, `LedgerTable.tsx`, `LedgerTable.test.tsx` - unavailable panel and injected-row ledger renderer coverage.
- `apps/web/features/billing/messages.ts` - vi/en source messages for the billing surface.
- `apps/web/e2e/billing-topup.spec.ts` - browser coverage for shell, balance, ledger unavailable, amount -> intent -> instructions -> credited success, rehydration, expiry reset, and 320px overflow.

## Decisions Made

- QR rendering: no runtime dependency added. The plan allowed QR image and/or copyable EMV text; this implementation uses copyable EMV text only to avoid dependency churn and because the backend returns no QR image URL.
- Ledger degradation: `useLedgerHistory` remains the Plan 01 `{ unavailable: true }` sentinel; `LedgerHistory` renders a clearly different unavailable panel, not the empty ledger state.
- Rehydration: `?code=` is the only available handle. The stored intent fields are payment instructions, not secrets, and are cleared on success or reset.
- Credit signal: no intent-status endpoint exists, so the flow uses `/api/billing/balance` rising above the baseline as the success signal.
- Bank fields: account number/name/bank labels are intentionally absent; the payload already encodes transfer destination details and the response has no separate fields.

## Deviations from Plan

### Auto-fixed Issues

**1. [UI behavior] Submit button did not fire the top-up POST**
- **Found during:** Task 2 Playwright execution.
- **Issue:** The local Base UI Button wrapper follows explicit `type="button"` action patterns elsewhere; the initial submit-style button did not submit in the browser test.
- **Fix:** Refactored `TopupAmountForm` to use an explicit click handler while keeping `onSubmit` for Enter key support.
- **Files modified:** `apps/web/features/billing/components/TopupAmountForm.tsx`.
- **Verification:** targeted billing e2e posts to `/api/billing/topup/intent`; lint/type/component tests passed.
- **Committed in:** `c84fc47` (amended Task 1 commit).

**2. [Hydration] sessionStorage rehydration caused a mismatch on `?code=` reload**
- **Found during:** Task 2 Playwright execution.
- **Issue:** Reading `sessionStorage` during the first render made the server render the amount step while the client rendered instructions.
- **Fix:** Switched the stored-intent snapshot to `useSyncExternalStore` with a null server snapshot and memoized parsing.
- **Files modified:** `apps/web/features/billing/components/TopupClient.tsx`.
- **Verification:** targeted billing e2e rehydration test passed; full Playwright suite passed.
- **Committed in:** `c84fc47` (amended Task 1 commit).

**3. [Accessibility/test contract] Success and expired titles were not semantic headings**
- **Found during:** Task 2 Playwright execution.
- **Issue:** `CardTitle`/`AlertTitle` render `div` elements, so the planned "Credits added" and expired headings were not discoverable by role.
- **Fix:** Added nested `h2` elements inside the title primitives for those terminal states.
- **Files modified:** `TopupSuccess.tsx`, `TopupExpired.tsx`.
- **Verification:** targeted billing e2e asserts both headings by role.
- **Committed in:** `c84fc47` (amended Task 1 commit).

**Total deviations:** 3 auto-fixed UI/runtime issues.
**Impact on plan:** All fixes were required for the planned browser flow to work correctly; no backend scope was added.

## Issues Encountered

- Full Playwright passed with a pre-existing duplicate React key warning from another flow (`Actions-archive`). It is not caused by Plan 04.
- Generated `apps/web/i18n/messages/{en,vi}.json` were rebuilt for verification and intentionally left uncommitted for Plan 06.

## Verification

- `pnpm --filter web i18n:build` - passed.
- `pnpm --filter web lint` - passed.
- `pnpm --filter web typecheck` - passed.
- `pnpm --filter web i18n:check` - passed.
- `pnpm --filter web test -- features/billing/components` - passed, 1 file / 3 tests.
- `pnpm --filter web test:e2e -- billing-topup --workers=1 --reporter=dot` - passed, 6 tests.
- `pnpm --filter web test` - passed, 38 files / 228 tests.
- `pnpm --filter web test:e2e -- --workers=1 --reporter=dot` - passed, 57 passed / 2 skipped.
- `apps/web/lib/api/schema.d.ts` - unchanged.
- No QR dependency was added.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 05. Billing now has its dedicated route, top-up route, browser coverage, and documented backend gaps for Plan 06's `05A-GAPS.md` rollup.

## Self-Check: PASSED

- Required `/billing` and `/billing/top-up` routes exist under `(protected)/(app)`.
- Top-up instructions render qrPayload/code/amount/expiry only, with no bank-account fields.
- `?code=` + sessionStorage rehydrates without hydration mismatch.
- Ledger unavailable state is distinct from empty and populated rows are component-tested through injected data.
- Targeted and full web/browser verification gates passed.
- Generated locale bundles are not committed.

---
*Phase: 05A-user-surface-web-ui-core*
*Completed: 2026-05-12*
