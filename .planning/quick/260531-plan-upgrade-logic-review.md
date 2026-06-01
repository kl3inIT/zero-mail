# Plan upgrade logic review

## Scope

- Review checkout creation, downgrade guard, Lemon Squeezy webhook processing,
  plan period creation, and credit reset behavior.
- Fix only defects that can corrupt paid plan state.

## Findings

- Checkout creation blocks lower-tier checkout while a higher plan is active.
- Webhook processing still needs the same stale-checkout guard because an old
  lower-tier checkout link can be paid after a higher plan is active.

## Verification

- Add/adjust focused billing controller tests.
- Run focused backend API tests and compile affected modules.

## Result

- Added webhook-time downgrade guard so a stale lower-tier checkout cannot
  overwrite an active higher-tier plan.
- Duplicate webhook deliveries for an existing provider order are treated as
  no-ops and no longer re-expire overlapping plan periods.
- Checkout provider failures now keep a `FAILED` checkout session audit row
  instead of rolling it back with the API error.
- Verified:
  - `./gradlew.bat --no-daemon :backend:api:test --tests "com.zeromail.api.controllers.billing.BillingBalanceControllerTest" --stacktrace`
  - `./gradlew.bat --no-daemon :backend:core:compileJava :backend:api:compileJava --stacktrace`
