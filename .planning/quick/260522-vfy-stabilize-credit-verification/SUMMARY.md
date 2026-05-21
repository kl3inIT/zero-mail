---
status: complete
completed: 2026-05-22
---

# Summary

Stabilized the current-state beta credit verification gate after shared test container pool tuning:
- `origin/main` now caps shared core/worker test pools at 30 connections with Postgres `max_connections=500`.
- Added a smaller per-spec pool cap of 12 only for the two concurrency-heavy core specs that intentionally start 8-10 simultaneous credit reservations/model calls.
- Confirmed the failure was test pool starvation from the earlier shared cap, not a product behavior regression.

Verification:
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.usecases.CreditLedgerConcurrentReserveTest" --tests "com.zeromail.core.llm.usecases.LlmGatewayCreditLifecycleTest"` passed.
- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.usecases.CreditGrantServiceTest" --tests "com.zeromail.core.billing.usecases.CreditLedgerGrantAllocationTest" --tests "com.zeromail.core.billing.usecases.CreditLedgerConcurrentReserveTest" --tests "com.zeromail.core.billing.usecases.CreditLedgerSettleIdempotentTest" --tests "com.zeromail.core.llm.usecases.LlmGatewayCreditLifecycleTest" --tests "com.zeromail.core.llm.usecases.LlmGatewayPlatformPathTest" :backend:api:test --tests "com.zeromail.api.controllers.billing.BillingBalanceControllerTest" --tests "com.zeromail.api.controllers.billing.BillingBalanceMultiTenantLeakTest" --tests "com.zeromail.api.controllers.billing.SepayWebhookIntegrationTest" --tests "com.zeromail.api.controllers.billing.SepayConcurrentDeliveryTest" --tests "com.zeromail.api.controllers.billing.SepayMemoExtractionTest" --tests "com.zeromail.api.controllers.billing.BillingInsufficientCreditsTest" --tests "com.zeromail.api.controllers.llm.ByokControllerIntegrationTest" :backend:worker:compileJava :backend:worker:test --tests "com.zeromail.worker.billing.*"` passed.
