---
status: complete
completed: 2026-05-22
---

# Summary

Updated the billing UI for beta-aware credits:
- Regenerated frontend OpenAPI artifacts for the beta balance and ledger endpoints.
- Wired billing ledger history to `/api/billing/ledger` instead of the previous unavailable placeholder.
- Expanded the balance card with available credits, held credits, beta credits, paid credits, monthly grant, reset time, and free-beta notice.
- Updated recent activity rendering for grant/expire/adjustment ledger rows and kept the table mobile overflow-safe.
- Refreshed billing mocks, feature tests, and Playwright specs for beta credit metadata.
- Added the backend OpenAPI generation task dependency needed for reliable schema regeneration.

Verification:
- `pnpm --filter web run typecheck` passed.
- `pnpm --filter web run test -- features/billing/components/LedgerTable.test.tsx features/billing/hooks/useBillingBalance.test.tsx features/billing/hooks/useTopupCreditWatch.test.tsx` passed.
- `pnpm --filter web run i18n:check` passed.
- `pnpm --filter web run test:e2e -- billing-topup.spec.ts billing-balance.spec.ts` passed.
- Playwright MCP browser verification passed on `/billing` at 1280x820 and 320x740 with mocked billing APIs: beta notice and ledger visible, no console warnings/errors, no horizontal overflow.
