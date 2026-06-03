# Deferred Items

## Pre-existing public API route test drift

- **Found during:** Phase 08 Plan 8A final verification
- **Command:** `./gradlew :backend:core:test :backend:api:test :backend:worker:test`
- **Observed:** `:backend:api:test` fails legacy public API tests that still call `/me`,
  `/me/language`, `/tenant/triage-pause`, and expect unprefixed OpenAPI paths like `/me`.
- **Evidence:** `git log -S'@GetMapping("/api/me")'` shows the public API route prefix
  was introduced by pre-8A commit `db38a7be deploy: production Docker setup and API routing fixes`.
- **Disposition:** Out of scope for 8A. Admin-focused backend gates pass after the 8A
  Modulith named-interface fix. A future cleanup should align these legacy tests and OpenAPI
  assertions with the `/api/**` production routing decision, or intentionally reintroduce
  unprefixed compatibility routes if that is still a product contract.

