# Lemon Squeezy Checkout API

Date: 2026-05-29

Root cause:
- Checkout URL generation depended on `LEMON_SQUEEZY_STORE_SLUG` and local URL construction.
- Local env has Lemon Squeezy API credentials but no store slug, so checkout creation returned unavailable.
- Seeded billing plans do not include `lemon_squeezy_variant_id`; plan rows must have variant IDs before checkout can be created.

Change:
- Create checkout sessions through Lemon Squeezy `POST /v1/checkouts`.
- Read URL from `data.attributes.url`.
- Require store ID + API key + plan variant ID instead of store slug.
- Send `product_options.enabled_variants` with only the selected plan variant so the hosted
  checkout cannot show the rest of the store's plans.
- Persist each checkout attempt in `billing_checkout_session` with request/response JSON, checkout
  URL, provider checkout ID, `CREATED`/`FAILED` status, and a local `reuse_expires_at` cache
  window.
- Reuse a successful checkout session for the same tenant + plan + user email inside
  `LEMON_SQUEEZY_CHECKOUT_REUSE_WINDOW` (default `PT15M`) so repeated clicks do not create
  repeated Lemon Squeezy checkouts.
- Add a public Lemon Squeezy webhook endpoint that stores redacted `subscription_webhook_event`
  rows only for events that can affect local subscription state or credit grants.
- Webhook receipt now follows Lemon Squeezy's `X-Signature` HMAC-SHA256 check over the raw body
  inside the Spring Security filter chain. Invalid or missing signatures return `401` before the
  controller runs; valid signatures are stored and return `200`. The controller prints the redacted
  payload to stdout for initial delivery verification.
- Valid subscription lifecycle webhook bodies are processed synchronously after persistence:
  `subscription_created`, `subscription_updated`, `subscription_cancelled`,
  `subscription_resumed`, `subscription_expired`, `subscription_paused`,
  `subscription_unpaused`, and `subscription_plan_changed` upsert the local `subscription` row from
  Lemon Squeezy `data.attributes`.
- Valid `subscription_payment_success` invoice webhook bodies reset the subscribed plan's monthly
  credit allowance through `CreditGrantService`: any remaining active `MONTHLY_ALLOWANCE` balance is
  expired, then a new grant is created for exactly the current plan allowance. Replayed webhooks and
  worker runs are idempotent on `(tenant_id, MONTHLY_ALLOWANCE, PLAN_PERIOD, plan_period_key)`.
- Other Lemon Squeezy events such as `order_created` return `200` after signature verification but
  are not stored, because they do not currently drive subscription state or credit grants.

Verification:
- `./gradlew.bat --no-daemon :backend:api:test --tests "com.zeromail.api.controllers.billing.BillingBalanceControllerTest" --stacktrace`
  passed before the local checkout cache change.
- After the cache change, `./gradlew.bat --no-daemon :backend:api:compileTestJava --stacktrace`
  passed. Re-running the same integration test was blocked because the local Docker service was
  stopped, so Testcontainers could not start Postgres.
- After the production credit-reset refactor, `./gradlew.bat --no-daemon :backend:api:compileTestJava :backend:core:compileTestJava :backend:worker:compileTestJava --stacktrace`
  passed.
