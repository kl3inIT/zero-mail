---
quick_id: 260516-stripe-ready-billing-payment-schema
status: completed
created: 2026-05-16
---

# Quick Task 260516: Stripe-Ready Billing Payment Schema

## Objective

Split payment-provider state out of `billing_topup_intent` into generic payment attempt and
payment event tables so SE Pay continues to work and Stripe can be added without adding
provider-specific columns to the top-up intent table.

## Tasks

1. Add Liquibase migration creating `billing_payment_attempt` and `billing_payment_event`.
2. Backfill existing SE Pay top-up intents into payment attempts and processed events where
   historical SePay transaction ids exist.
3. Add Java domain enums, JPA entities, and repositories for the new tables.
4. Route current SE Pay intent creation and webhook fulfillment through the new tables while
   preserving the existing atomic `PENDING -> PAID` guard.
5. Run focused backend validation.

## Validation

- `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC ./gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.billing.Sepay*"`
- `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC ./gradlew.bat :backend:core:test --tests "com.zeromail.core.billing.*" --tests "com.zeromail.core.llm.usecases.LlmGatewayCreditLifecycleTest"`
- `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC ./gradlew.bat :backend:worker:test --tests "com.zeromail.worker.billing.BillingIntentExpirySweeperTest"`

## Expected Files

- `backend/core/src/main/resources/db/changelog/changes/039-billing-payment-attempt-event.yaml`
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml`
- `backend/core/src/main/java/com/zeromail/core/billing/domain/*`
- `backend/core/src/main/java/com/zeromail/core/billing/persistence/*`
- `backend/core/src/main/java/com/zeromail/core/billing/usecases/BillingTopupService.java`
