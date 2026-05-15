---
phase: 05A-user-surface-web-ui-core
status: recorded
created: 2026-05-12
updated: 2026-05-12
requirements: [WEB-02]
---

# Phase 5A Backend-Surface Gaps

Phase 5A is frontend-only. These gaps were confirmed during execution against `backend/api` controllers and `apps/web/lib/api/schema.d.ts`; they were recorded rather than fixed because `05A-SPEC.md` explicitly keeps backend endpoint additions out of scope. Phase 5A did not regenerate `apps/web/lib/api/schema.d.ts`, did not add backend endpoints, and left the public `(public)/privacy` page untouched.

Research references: `05A-RESEARCH.md` Assumptions Log A4/A6 and Open Questions 1-5.

## Gaps

| Gap | Confirmed absent from | Requirement served | Phase 5A degradation path | Follow-up owner |
|-----|-----------------------|--------------------|----------------------------|-----------------|
| Triage-audit list endpoint | `TriageAuditController`; schema exposes undo only at `.../audit/{auditId}/undo` | WEB-02 | `getAuditLog` returns `{ unavailable: true }`; `AuditLog` renders a distinct "audit history not yet available" panel; undo, empty, and error UI still ship; populated rows are covered by `AuditLog.test.tsx` through injected data; e2e covers the production unavailable state. | Backend API plan before claiming full WEB-02 audit history |
| Billing ledger / transaction-history list endpoint | `BillingController`; schema exposes `/api/billing/balance` and `/api/billing/topup/intent` only | WEB-02 | `useLedgerHistory` returns `{ unavailable: true }`; `LedgerHistory` renders a distinct "transaction history isn't available yet" panel; populated rows are covered by `LedgerTable.test.tsx` through injected data; e2e covers the production unavailable state. | Backend API plan before claiming full WEB-02 billing history |
| Top-up intent-status endpoint and `intentId` field | `BillingController`; `TopupIntentResponse` has no `intentId`; no intent-status GET route exists | WEB-02 | D-15 `?intentId=` rehydration falls back to `?code=` plus `sessionStorage`; credited state is inferred from `/api/billing/balance` rising, with no status poll. | Backend billing API plan if durable top-up resume/status is required |
| Top-up bank-account fields | `TopupIntentResponse`; response exposes only `code`, `amountVnd`, `expiresAt`, and `qrPayload`; schema `accountNumber` belongs to `SepayWebhookPayload`, not the top-up response | WEB-02 | Top-up instructions show the VietQR `qrPayload` as copyable EMV text, transfer `code`, exact `amountVnd`, and `expiresAt` countdown only; separate bank/account-holder/bank-name fields would require static frontend config or a backend response change, both out of Phase 5A scope. | Product/backend decision before separate copyable bank fields |

## Resolved Research Questions

1. **Triage audit list:** absent; Phase 5A ships explicit unavailable state plus injected-data component coverage.
2. **Billing ledger history:** absent; Phase 5A ships explicit unavailable state plus injected-data component coverage.
3. **Top-up intent identity/status:** no `intentId` or intent-status route; Phase 5A uses `?code=` plus `sessionStorage` and balance-rise detection.
4. **Audit entry identifiers:** no backend list shape exists; Phase 5A uses a local `AuditEntry` row model and only links to Gmail when an eventual row provides a Gmail message id.
5. **Onboarding chrome suppression:** resolved by the split `(protected)/(app)` shell route group plus bare `(protected)/onboarding` layout; no fallback segment-branch was needed.

## Related Execution Decisions

- Route grouping: Plan 02 used the preferred split: `(protected)/layout.tsx` owns providers/auth, `(protected)/(app)/layout.tsx` owns the app shell, and `(protected)/onboarding/layout.tsx` remains chrome-suppressed.
- QR dependency: no QR dependency was added. `qrPayload` is rendered as copyable React text only.
- Schema/public/backend drift checks passed during Plan 06: no diff for `apps/web/lib/api/schema.d.ts`, `apps/web/app/(public)/privacy/page.tsx`, or `backend/`.
