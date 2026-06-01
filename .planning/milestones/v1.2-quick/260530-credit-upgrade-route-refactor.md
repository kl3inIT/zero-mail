# Credit and upgrade route refactor

## Goal

Rename route surfaces so they match the production billing model:

- Credits: balance and ledger history.
- Plan upgrades: monthly one-time plan checkout.

## Scope

- Backend HTTP route paths.
- Frontend page routes, sidebar/account links, API client wrappers, mocks, and tests.
- Preserve existing domain classes unless renaming them would materially improve route clarity.

## Verification

- Backend compile and focused billing controller tests.
- Frontend i18n, typecheck, lint, unit tests, and billing Playwright coverage.

## Result

- Replaced user-facing billing/subscription routes with `/credits` and `/upgrade-plan`.
- Replaced public API routes with `/api/credits/**` and `/api/plan-upgrades/**`.
- Regenerated OpenAPI and TypeScript API schema from the JDK 25 backend.
- Verified backend focused tests, frontend i18n/typecheck/lint/unit tests, and Playwright billing spec.
