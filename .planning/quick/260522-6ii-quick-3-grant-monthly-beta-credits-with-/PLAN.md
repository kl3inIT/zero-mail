---
status: in_progress
created: 2026-05-21
---

# Quick 3: Monthly Beta Credits

Goal: issue beta credits idempotently per tenant/month, expire old promotional balances, and make reservations lazily ensure the current beta grant before charging.

Steps:
- Add beta billing config: enabled flag, monthly credits, daily hard cap.
- Add `CreditGrantService` for current-month beta grants and expired grant cleanup.
- Enforce the daily hard cap in `CreditLedgerService.reserve(...)`.
- Add a worker scheduler that iterates tenants and grants current beta credits.
- Add focused integration tests for idempotency, expiry, lazy reservation grant, and daily cap behavior.
