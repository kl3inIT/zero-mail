# PR 59 Review Fixes

## Goal

Review GitHub PR #59 and fix concrete correctness/security issues found by local review and PR comments.

## Scope

- Keep changes on the current `nhat` branch.
- Fix waitlist rate limiting and invite dispatch correctness issues.
- Fix admin waitlist pagination consistency.
- Diagnose and fix PR #59 CI failures.
- Keep generated OpenAPI files generated, not hand-edited.

## Checks

- Core waitlist tests.
- Worker waitlist tests.
- Admin/web TypeScript checks.
- Full backend CI check and web coverage gate.
- Targeted backend API compilation/tests if affected.

## Result

- Removed committed default Postgres password from `docker-compose.yml`.
- Stopped trusting client-controlled `X-Forwarded-For` for waitlist rate limiting.
- Mapped Redis `DataAccessException` failures to the documented waitlist 503 business exception.
- Replaced stringly waitlist status comparison in invite dispatch with enum comparison.
- Added transaction boundary to the waitlist due-invite `SKIP LOCKED` repository query.
- Exposed worker notification config as a Spring Modulith named interface for the waitlist worker dependency.
- Added admin waitlist page auto-correction when URL page is beyond the filtered result range.
- Fixed `gates / Backend Gradle` by reducing `LlmGatewayByokRoutingTest.multitenant_no_key_leak`
  concurrency so the test still exercises tenant isolation without exhausting Hikari's test pool.
- Fixed `gates / Frontend Web` by adding focused coverage for billing formatter/query-key utilities,
  raising web statement coverage above the configured 30% threshold.

## Verification

- `./gradlew.bat :backend:core:test --tests "com.zeromail.core.waitlist.*"`
- `./gradlew.bat :backend:worker:test --tests "com.zeromail.worker.waitlist.*"`
- `./gradlew.bat :backend:api:test`
- `pnpm --filter @zeromail/admin run typecheck`
- `pnpm --filter web run typecheck`
- `./gradlew.bat --no-daemon check --stacktrace`
- `pnpm --filter web run test:coverage`
- `pnpm --filter web run lint`
- JetBrains rebuild of touched Java files
