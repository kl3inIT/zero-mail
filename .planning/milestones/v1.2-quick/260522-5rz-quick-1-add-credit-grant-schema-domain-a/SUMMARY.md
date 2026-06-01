---
status: complete
completed: 2026-05-21
---

# Summary

Added the credit grant persistence foundation:
- `credit_grant` table with category/status/bounds constraints and tenant/ref idempotency.
- Nullable `credit_ledger_entry.grant_id`, expanded ledger kind constraint, and sign invariant.
- Java grant category/status enums, JPA entity, repository, and ledger factories for future grant/expire/adjustment entries.
- Tenant deletion order updated so ledger rows are removed before grants.

Verification:
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.usecases.CreditLedgerConcurrentReserveTest" --tests "com.zeromail.core.billing.usecases.CreditLedgerSettleIdempotentTest"` passed.
