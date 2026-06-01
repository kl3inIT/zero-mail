# Production Credit Reset Refactor

## Intent

Zero Mail is no longer in beta. Credit allowance must come from the active billing plan:

- `FREE` resets to 300 credits each month.
- Paid plans reset to their configured `monthly_credit_allowance`.
- Resets replace the remaining monthly allowance balance; they do not add on top.
- Worker and webhook processing must use the same idempotent reset path.

## Approach

- Stop creating beta grants and move automatic allowance grants to `MONTHLY_ALLOWANCE`.
- Resolve the tenant's current effective plan from subscription state, falling back to `FREE`.
- Use a stable plan-period reference for idempotency across balance reads, reservations, worker runs, and Lemon Squeezy webhook events.
- Keep historical `BETA` category readable for old ledger data, but remove beta-specific runtime behavior and UI/API wording where this task touches billing balance.

## Verification

- `./gradlew.bat --no-daemon :backend:api:compileTestJava :backend:core:compileTestJava :backend:worker:compileTestJava --stacktrace` passed.
- `cmd /c pnpm -C apps/web typecheck` passed.
- `cmd /c pnpm -C apps/web test -- features/billing/hooks/useBillingBalance.test.tsx` passed; Vitest ran the current web test suite and reported 51 files / 282 tests passed.
- `cmd /c pnpm -C apps/web i18n:check` passed.
- Full backend integration tests were not run because local Docker service `com.docker.service` is stopped.
