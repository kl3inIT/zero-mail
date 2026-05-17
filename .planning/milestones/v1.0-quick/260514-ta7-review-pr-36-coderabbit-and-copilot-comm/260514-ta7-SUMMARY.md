---
status: complete
quick_id: 260514-ta7
date: 2026-05-14
commit: 4409e0e
---

# Quick Task 260514-ta7 Summary

Reviewed unresolved CodeRabbit and Copilot comments on PR #36 and applied the warranted fixes.

## Fixed

- Preserved nullable billing credit snapshots in webhook lookup projection.
- Restored legacy amount-based top-up crediting by falling back to VND-to-credit calculation when package snapshots are absent.
- Made package-based top-up responses fail loudly when credit snapshots are missing.
- Renamed the billing package changelog to `038-billing-packages.yaml` and added a package snapshot atomicity check.
- Switched API unauthorized matching to Spring Security's context-path-aware `PathPatternRequestMatcher`.
- Applied low-risk frontend review fixes for devtools loading, package selection defaults, stored intent parsing, legacy amount-form validation, i18n, and billing E2E mocks.
- Added regression coverage for legacy webhook crediting and package-mismatch audit logging.

## Skipped

- Did not encrypt or mask receiving payment-account snapshots because these fields are payment instructions shown to the payer and are already absent from logs.
- Did not churn TanStack versions because the current dependency set is coherent and CI was green.

## Verification

- `mcp__jetbrains__build_project` passed.
- `./gradlew.bat --no-daemon check` passed.
- `./gradlew.bat --no-daemon :backend:core:aiEval -PdeterministicOnly` passed.
- `pnpm --filter web run lint` passed with existing warnings.
- `pnpm --filter web run typecheck` passed.
- `pnpm --filter web run test` passed.
- `pnpm --filter web run build` passed.
- `pnpm --filter web run i18n:check` passed.
- `pnpm --filter web exec playwright test e2e/billing-topup.spec.ts --project=chromium` passed.
