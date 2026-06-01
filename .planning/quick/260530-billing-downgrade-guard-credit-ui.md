# Billing Downgrade Guard + Credit UI

## Goal

Prevent users with an active higher plan from starting checkout for a lower plan, and move the
frontend away from the separate subscription surface into a credit/payment page.

## Scope

- Backend checkout guard compares selected plan tier against the tenant's current active plan tier.
- Frontend disables lower-tier plan cards and shows the current plan in the sidebar user block.
- Remove subscription navigation surface; keep billing/credits with balance, ledger, and checkout.
- Verify with focused backend tests and frontend type/lint/build checks where practical.
