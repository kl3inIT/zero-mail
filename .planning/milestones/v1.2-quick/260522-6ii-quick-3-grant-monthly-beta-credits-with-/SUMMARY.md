---
status: complete
completed: 2026-05-21
---

# Summary

Implemented monthly beta credit grants:
- Added beta billing config: enabled, monthly credits, daily hard cap.
- Added `CreditGrantService` for idempotent current-month BETA grants and expired grant cleanup.
- `CreditLedgerService` now lazily ensures beta grants for positive-cost reservations and balance reads.
- Enforced beta daily hard cap before reserving credits.
- Added `BetaCreditGrantJob` in the worker with ShedLock scheduling.
- Added focused tests for beta grant idempotency, expiry, lazy reservation grant, daily cap, and legacy allocation isolation.

Verification:
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.usecases.CreditGrantServiceTest" --tests "com.zeromail.core.billing.usecases.CreditLedgerGrantAllocationTest" --tests "com.zeromail.core.billing.usecases.CreditLedgerConcurrentReserveTest" --tests "com.zeromail.core.billing.usecases.CreditLedgerSettleIdempotentTest" :backend:worker:compileJava :backend:worker:test --tests "com.zeromail.worker.billing.*"` passed.
