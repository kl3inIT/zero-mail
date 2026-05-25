---
status: planned
created: 2026-05-24
task: fix-admin-llm-provider-cards-and-9router
---

# Quick Task: Fix Admin LLM Provider Cards And 9Router Selection

## Scope

- Remove technical `dependents` wording from provider cards.
- Show provider active-key counts from the multi-key credential table, not the legacy `maskedKey` field.
- Ensure 9Router has selectable catalog models for admin routing slots when a valid provider key exists.
- Replace native browser confirms with in-app admin dialogs.
- Make key deletion match the UI wording: remove the key row from the provider list instead of leaving a confusing `REVOKED` row behind.
- Expose/filter model verification status so the picker does not offer models the backend will reject, and backfill currently-routed seed models out of `UNTESTED`.
- Keep the fix boundary-safe and verify backend/API/admin UI contracts.

## Plan

1. Extend the master-key list projection/API with `activeKeyCount`.
2. Update the admin provider cards to render `activeKeyCount` and drop the dependents badge.
3. Inspect catalog seed/runtime data for `ROUTER_9R` and add a migration or logic fix if selectable models are missing.
4. Fix follow-up UAT issues from the admin LLM screen: key delete UX, routing save eligibility, and native confirm dialogs.
5. Regenerate admin API schema after backend DTO changes.
6. Verify backend tests, admin typecheck/lint, and Playwright on the master keys page.

## Verification

- Backend compile or focused tests for master key/catalog changes.
- `pnpm --filter @zeromail/admin run generate-api`
- `pnpm --filter @zeromail/admin run typecheck`
- `pnpm --filter @zeromail/admin run lint`
- Playwright browser check for provider cards and 9Router model selection.
