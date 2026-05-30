# Shell settings billing navigation

## Goal

Adjust the app shell to match the provided reference layout:

- Keep the outer sidebar focused on the primary app surfaces only.
- Move secondary surfaces into settings/account navigation.
- Split credit history from plan upgrade.
- Add pagination controls for credit history.
- Keep AI configuration directly under the assistant surface in the outer sidebar.
- Make the sidebar header an account switcher only, leaving settings actions in the footer account menu.

## Scope

- Frontend app shell/sidebar and settings/billing routes.
- Billing ledger pagination request wiring.
- Keep backend changes minimal unless the current ledger API cannot paginate by cursor.

## Verification

- Typecheck/lint focused frontend changes.
- Run billing/sidebar Playwright coverage where practical.
