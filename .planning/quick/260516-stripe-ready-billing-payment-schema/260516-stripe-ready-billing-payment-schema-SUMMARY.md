---
quick_id: 260516-stripe-ready-billing-payment-schema
status: completed
completed: 2026-05-16
---

# Summary

Added the generic billing payment schema needed for Stripe without adding provider-specific columns
to `billing_topup_intent`.

## Changes

- Created `billing_payment_attempt` for provider-specific payment sessions/orders.
- Created `billing_payment_event` for webhook/event receipt and processing status.
- Backfilled existing SE Pay top-up intents into `billing_payment_attempt`.
- Backfilled processed SE Pay events for historical paid intents with `sepay_transaction_id`.
- Added payment provider/status enums, JPA entities, and repositories.
- Routed current SE Pay intent creation and webhook fulfillment through payment attempts/events.
- Kept the existing atomic `billing_topup_intent` `PENDING -> PAID` transition as the money-path
  race-condition guard.
- Updated the billing expiry sweeper to expire stale payment attempts together with stale intents.

## Verification

- Focused SE Pay API tests passed.
- Focused core billing/credit tests passed.
- Focused worker billing expiry test passed.

Note: local Gradle test commands need `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` in this environment
because PostgreSQL Testcontainers rejects the JVM timezone name `Asia/Saigon`.
