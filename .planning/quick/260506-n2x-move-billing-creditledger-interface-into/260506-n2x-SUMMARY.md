---
quick_id: 260506-n2x
status: complete
completed: 2026-05-06
commit: b2a97d5
---

# Quick Task 260506-n2x Summary

Moved the billing ledger public contract from `core.billing.model` to `core.billing.service`, per user direction.

## Changes

- Moved `CreditLedger` to `backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java`.
- Kept the implementation named `CreditLedgerService` and package-private.
- Updated API, worker, and billing tests to import `com.zeromail.core.billing.service.CreditLedger`.
- Updated billing package docs so `model` is described as records/enums/exceptions and `service` as the service contract plus implementation.
- Kept `model` independent from `service` by removing Javadoc-only imports back to `CreditLedger`.

## Verification

- `.\gradlew.bat :backend:core:compileJava :backend:api:compileJava :backend:worker:compileJava` - PASS.
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.*" :backend:api:test --tests "com.zeromail.api.controllers.billing.*" :backend:worker:test --tests "com.zeromail.worker.billing.*"` - PASS.
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.arch.DomainBoundaryArchTests" --tests "com.zeromail.core.billing.BillingDomainBoundaryArchTest" --tests "com.zeromail.core.billing.CallSiteEnumMembershipArchTest" :backend:api:test --tests "com.zeromail.api.ZeroMailApiApplicationModulesTest"` - PASS.
- JetBrains project build - PASS, no problems.

## Notes

- `SepayApiKeyVerifier` and `TopupCodeGenerator` were not moved in this quick task; this task only applies the user-confirmed `CreditLedger` service-package refactor.
