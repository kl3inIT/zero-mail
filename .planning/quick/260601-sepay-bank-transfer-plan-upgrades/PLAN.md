# SePay Bank Transfer Plan Upgrades

## Goal
Add QR bank-transfer payment via SePay as a second payment method for plan upgrades, while keeping Lemon Squeezy as its own readable path.

## Constraints
- Keep existing monthly plan period + credit allowance lifecycle.
- SePay webhook must be secured by a dedicated security filter path before controller execution.
- New persistence table: billing_bank_transfer_intent.
- DTO from frontend should carry plan code and payment method.
- Service code should split Lemon Squeezy and SePay flows into separate methods.
- Liquibase must be append-only; do not edit applied changelogs.

## Notes
- Old branch `billing` implemented SePay top-up intents. Reused matching/idempotency/security ideas only, not old top-up domain.

## Result
- Added plan-upgrade checkout DTO with `planCode` + `paymentMethod`.
- Split checkout creation into Lemon Squeezy and SE Pay bank-transfer paths.
- Added `billing_bank_transfer_intent` and SE Pay webhook ingestion.
- Added a dedicated SE Pay API-key security filter for `/api/plan-upgrades/webhooks/sepay`.
- Cleaned Lemon Squeezy product snapshots from plan periods and removed unused plan catalog product id.
- Updated frontend plan actions to support card checkout and QR bank transfer.

## Follow-up 2026-06-01
- Address PR 80 review comments from CodeRabbit/Copilot/CodeQL.
- Keep mobile QR popup compact, but expand desktop popup into a larger horizontal receipt-style layout with plan details.
- Preserve Liquibase as-is.
