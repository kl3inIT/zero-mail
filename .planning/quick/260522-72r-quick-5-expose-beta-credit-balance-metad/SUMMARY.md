---
status: complete
completed: 2026-05-22
---

# Summary

Exposed beta-aware billing account metadata and recent credit activity:
- Added a core billing query service and JDBC read repository for credit summaries and ledger history.
- Extended `/api/billing/balance` with beta credits, paid credits, monthly grant, reset time, and free-beta status.
- Added `/api/billing/ledger?limit=` with recent activity rows.
- Counted paid credits from both PAID grants and legacy unscoped top-ups.
- Attached new SePay top-ups to PAID credit grants while preserving TOPUP ledger rows for existing webhook contracts.
- Avoided nested balance transactions so beta grant creation does not exhaust small API test pools.

Verification:
- `.\gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.billing.BillingBalanceControllerTest" --tests "com.zeromail.api.controllers.billing.BillingBalanceMultiTenantLeakTest"` passed.
- `.\gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.billing.BillingBalanceControllerTest" --tests "com.zeromail.api.controllers.billing.BillingBalanceMultiTenantLeakTest" --tests "com.zeromail.api.controllers.billing.SepayWebhookIntegrationTest" --tests "com.zeromail.api.controllers.billing.SepayConcurrentDeliveryTest" --tests "com.zeromail.api.controllers.billing.SepayMemoExtractionTest"` passed.
