---
status: resolved
trigger: "backend and Playwright tests fail after resetting origin/main to streamline beta onboarding"
created: 2026-05-22
updated: 2026-05-22
---

# Debug Session: test-failures-backend-playwright

## Symptoms

- Expected behavior: backend and Playwright tests pass on `origin/main`.
- Actual behavior: user reports backend and Playwright tests are failing.
- Error messages: not provided initially; collected from GitHub CI artifacts and local test runs.
- Timeline: began after `origin/main` was force-reset to `9cb8453a refactor(web): streamline beta onboarding`.
- Reproduction: run backend and Playwright test suites from the `origin/main` state.

## Current Focus

- hypothesis: backend CI failure is caused by test concurrency exhausting the test DB pool; Playwright failure is local environment drift
- test: run targeted Playwright and backend test commands from a branch based on `origin/main`
- expecting: Playwright passes once stale dev server state is removed; backend passes after bounding the integration test fan-out
- next_action: commit and push the bounded concurrency fix

## Evidence

- 2026-05-22: GitHub CI run `26266494645` for `9cb8453a` shows `gates / Playwright` success and `gates / Backend Gradle` failure.
- 2026-05-22: Downloaded backend test report artifact. Only failing suite is `com.zeromail.core.llm.usecases.LlmGatewayByokRoutingTest`, test `multitenant_no_key_leak()`.
- 2026-05-22: Failure is `CannotCreateTransactionException` caused by Hikari timeout: `total=30, active=30, idle=0, waiting=67`. The test starts 100 virtual-thread gateway calls concurrently against a test context configured with `spring.datasource.hikari.maximum-pool-size=30`.
- 2026-05-22: Playwright local failure was environmental: existing Next dev server PID 9152 held the Next dev lock; CI Playwright passed.
- 2026-05-22: Targeted backend test passed locally after the fix: `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.llm.usecases.LlmGatewayByokRoutingTest.multitenant_no_key_leak" --console=plain`.
- 2026-05-22: Full backend check passed locally after the fix: `.\gradlew.bat --no-daemon check --stacktrace --console=plain`.
- 2026-05-22: Targeted Playwright route spec passed locally after stopping the stale dev server: `pnpm --filter web exec playwright test e2e/onboarding-routes.spec.ts`.

## Eliminated

- Playwright product regression on `9cb8453a` — eliminated by CI job success for `gates / Playwright` and local pass after stale server cleanup.

## Fix Notes

- Bound the `multitenant_no_key_leak()` gateway calls to 16 concurrent executions while preserving 100 tenant fixtures and assertions. The invariant is tenant/key isolation, not exhausting the database connection pool.

## Resolution

- root_cause: Unbounded 100-way virtual-thread fan-out in one integration test exhausted the 30-connection Hikari pool in CI.
- fix: Added a `Semaphore` around concurrent gateway calls in `LlmGatewayByokRoutingTest.multitenant_no_key_leak()`.
- verification: targeted backend test passed, full backend `check` passed, targeted Playwright onboarding route spec passed.
- files_changed: `backend/core/src/test/java/com/zeromail/core/llm/usecases/LlmGatewayByokRoutingTest.java`
