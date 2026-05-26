---
status: in-progress
created: 2026-05-26
---

# Move Billing Top-Up CTA Into Balance Card

## Scope

- Remove the standalone billing top-up CTA row from `/billing`.
- Add the same top-up link inside the existing balance card header.
- Keep the billing page spacing and responsive behavior stable.

## Verification

- `pnpm --filter web typecheck`
- `pnpm --filter web lint`
- `pnpm --filter web test:e2e billing-topup.spec.ts`
- `git diff --check`
