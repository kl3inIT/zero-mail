---
created: 2026-04-28T00:00:00Z
title: Apply :? fail-fast to backend/worker application.yml refresh-token-key (CR-04 parity)
area: api
source:
  phase: 01.5
  finding: SECURITY.md "unregistered flag" (out-of-scope of Plan 08)
  status: follow_up
files:
  - backend/worker/src/main/resources/application.yml
  - backend/api/src/main/resources/application.yml
---

## Problem

Phase 01.5 plan 08 (CR-04 stack-lock cleanup) removed `spring-cloud-gcp-starter-secretmanager`
and replaced the `${REFRESH_TOKEN_KEY_BASE64:${sm://...}}` placeholder with the
`${REFRESH_TOKEN_KEY_BASE64:?...}` fail-fast pattern in
`backend/api/src/main/resources/application.yml`.

The phase-01.5 security audit (`01.5-SECURITY.md`) flagged that
`backend/worker/src/main/resources/application.yml:10` STILL contains the old
`${REFRESH_TOKEN_KEY_BASE64:${sm://oauth-refresh-token-key-v1/versions/1:}}` form.
Plan 08 declared scope as `backend/api/.../application.yml` only, so the worker module
was not touched.

Same threat class as **T-01.5-08-03** (`sm://` URI resolves to empty string on VPS,
silently producing a corrupted encryption key). The api module is now hardened; the
worker is still vulnerable to the same opaque-failure mode if the env var is missing.

## Solution

Apply the same edit to the worker's `application.yml`:

1. Remove any `spring.cloud.gcp.secretmanager.*` block (verify worker module uses the
   same yaml shape as api)
2. Replace `${REFRESH_TOKEN_KEY_BASE64:${sm://oauth-refresh-token-key-v1/versions/1:}}`
   with `${REFRESH_TOKEN_KEY_BASE64:?REFRESH_TOKEN_KEY_BASE64 must be supplied via deployment secret source (Docker secret / systemd credential / locked-down env file) — see CLAUDE.md TL;DR}`
3. Verify worker module test base supplies `REFRESH_TOKEN_KEY_BASE64` (or add a
   `@DynamicPropertySource` if needed — `:?` will fail every `@SpringBootTest` otherwise)
4. `./gradlew :backend:worker:test` exit 0
5. Repo-wide: `grep -rn "sm://" backend/` returns zero hits

**Trivial scope** — likely a `/gsd-quick` or `/gsd-fast` task. Pair-able with the
WR-06 follow-up in a single small phase if both are scheduled together.

## Acceptance

- `grep -c "sm://" backend/worker/src/main/resources/application.yml` returns 0
- `grep -c "REFRESH_TOKEN_KEY_BASE64:?" backend/worker/src/main/resources/application.yml` returns 1
- Worker test suite green
