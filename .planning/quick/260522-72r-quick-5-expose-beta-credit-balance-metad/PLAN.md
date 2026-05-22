---
status: in_progress
created: 2026-05-21
---

# Quick 5: Billing API Metadata And Ledger

Goal: expose beta/free credit metadata and a real ledger history endpoint for the frontend billing surface.

Steps:
- Add core query service/read repository for credit category balances and ledger rows.
- Extend `/api/billing/balance` with beta/paid/monthly/reset/free-beta fields.
- Add `/api/billing/ledger` returning recent ledger history.
- Update backend API tests for beta-aware balances and ledger response shape.
- Verify with focused API tests and OpenAPI generation if available.
