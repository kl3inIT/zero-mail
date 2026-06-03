---
status: complete
completed: 2026-05-21
---

# Summary

Implemented grant-aware reservation allocation:
- Added nullable `credit_reservation.grant_id` and widened `call_site` to support `TRIAGE_DETERMINISTIC`.
- `CreditLedgerService.reserve(...)` now spends from active grants ordered by priority and expiry before falling back to legacy unscoped top-up balance.
- `release(...)` returns credits to the original grant.
- Zero-cost call sites now create reservations without ledger charges.
- Added focused grant allocation tests.

Verification:
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.usecases.CreditLedgerGrantAllocationTest" --tests "com.zeromail.core.billing.usecases.CreditLedgerConcurrentReserveTest" --tests "com.zeromail.core.billing.usecases.CreditLedgerSettleIdempotentTest"` passed.
