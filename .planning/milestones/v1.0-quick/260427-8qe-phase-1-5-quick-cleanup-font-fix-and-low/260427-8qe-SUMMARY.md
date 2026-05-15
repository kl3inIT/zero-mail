---
status: complete
quick_id: 260427-8qe
date: 2026-04-27
commit: 91117fd
---

# Quick Task 260427-8qe Summary

## Completed

- Fixed the Tailwind font token self-reference in `apps/web/app/globals.css`.
- Added TanStack Query defaults: `staleTime` 5 minutes and `gcTime` 30 minutes.
- Replaced `DeleteAccountDialog` local busy state with the owning delete mutation's `isPending` state.
- Made Gmail OAuth additional parameters immutable with `Map.copyOf(...)`.
- Changed unknown OAuth success registration handling from silent default redirect to fail-loud `IllegalStateException`.

## Verification

- `pnpm --dir apps/web exec prettier --write app/globals.css lib/query-client.tsx features/account/components/DeleteAccountDialog.tsx "app/(protected)/settings/page.tsx"`
- `pnpm --dir apps/web exec tsc --noEmit`
- `./gradlew :backend:api:compileJava`
- JetBrains file problems:
  - `GmailScopeRequestResolver.java`: no problems.
  - `OAuth2LoginDispatchingSuccessHandler.java`: existing Spring nullness override warnings only; no errors.

## Deferred

- Root layout `/me` fetch cache strategy remains in Phase 1.5 proper.
- OAuth bundling and primitive deflation remain in Phase 1.5 proper.
