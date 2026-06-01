# One-Time Monthly Plan Billing

## Goal

Replace subscription-based billing credit allocation with one-time monthly plan purchases:

- A successful payment creates a tenant plan period.
- The plan period grants monthly allowance credits immediately.
- Paid plan access lasts for exactly the purchased period.
- Credit reserve/settle/release lifecycle remains unchanged.

## Scope

- Remove subscription as the source of current plan access.
- Remove scheduled worker credit grants.
- Add `billing_plan_period` as the current plan entitlement source.
- Refactor Lemon Squeezy webhook handling to create plan periods from paid one-time order events.
- Keep `credit_grant`, `credit_ledger_entry`, and `credit_reservation` lifecycle intact.
- Align plan allowances to FREE 300, PLUS 2000, PRO 8000.

## Non-goals

- Implement recurring subscriptions.
- Implement top-up credit products.
- Change spend allocation/reservation semantics.
