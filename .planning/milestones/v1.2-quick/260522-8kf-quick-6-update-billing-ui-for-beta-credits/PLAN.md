---
status: in_progress
created: 2026-05-22
---

# Quick 6: Billing UI For Beta Credits

Goal: update the frontend billing surface to consume the beta-aware billing API and show SaaS-style credit metadata/history.

Steps:
- Inspect existing billing feature API, hooks, components, and generated OpenAPI schema workflow.
- Regenerate or update frontend API types for `/api/billing/balance` and `/api/billing/ledger`.
- Add frontend API/query hook support for recent ledger activity.
- Update billing UI to show available credits, held credits, beta grant, reset time, paid credits, recent activity, and beta-free notice.
- Verify with frontend tests/typecheck and Playwright browser verification against the running app.
