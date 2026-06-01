# PR 72 CI Fix

Date: 2026-05-28

Scope:
- Fix failing i18n key coverage for new billing backend error codes.
- Fix Playwright analytics duplicate fetch caused by client-side refetch after Suspense query resolution.

CI failures observed:
- `i18n-key-coverage`: missing `errors.billing.plan.featureDisabled`, `errors.billing.plan.notFound`, and `errors.billing.checkout.unavailable` in both locale bundles.
- `gates / Playwright`: `analytics canonicalizes empty and invalid window params before fetching` observed duplicate `7d` analytics requests.
