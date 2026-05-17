---
phase: 01-foundation-safety-infrastructure
plan: 09
status: code-complete-awaiting-human-checkpoint
---

# Plan 01-09 Summary — CASA package draft + FND-03 runtime log-scrub + actuator probes

## What was built

### Task 1 — Log-scrub real-request integration test + actuator wiring

- `backend/api/src/main/resources/application.yml` — extended `management:` block:
  - `endpoints.web.exposure.include: health, info, prometheus`
  - `endpoint.health.probes.enabled: true` (Cloud Run `/actuator/health/{liveness,readiness}` available)
  - `metrics.enable.all: true` (Micrometer registry on)
- `backend/api/src/test/java/com/zeromail/api/LogScrubSyntheticTrafficTest.java` — extends `ApiPostgresTestBase`, mints a real Spring Session via `TestSessionMinter`, drives `/me`, `/tenant/status`, and `/onboarding/select-template` against seeded sentinel rows (`leak-probe-12345`, `leak-probe@example.test`, `LEAK-REFRESH-TOKEN-ABC` planted into `users.google_subject` / `users.email` / `gmail_connections.refresh_token_encrypted`). A ROOT-logger `ListAppender` captures every emitted event for the lifetime of the requests. Asserts:
  1. The captured stream does NOT contain `LEAK_PROBE_SUBJECT` or `LEAK_PROBE_REFRESH_TOKEN`.
  2. The captured stream does NOT contain raw field-name leaks (`refresh_token=`, `"body":`, `"prompt":`, `"completion":`).
  3. At least one captured event carries `MDC[scrubbed]=true` — proves the plan-03 `SensitiveMarkerScrubFilter` (a Logback `TurboFilter`) actually fires end-to-end. To trigger it deterministically, the test emits a synthetic line whose rendered message contains the literal `Sensitive(...)` token (the substring the filter scans for); the rendered probe never contains real sentinel content.

### Task 2 — CASA submission package (4 markdown docs)

- `docs/casa/submission-log.md` — submission tracking with placeholders for the human-action checkpoint (project, submission ID, lab, date) and a filing checklist.
- `docs/casa/privacy-policy-draft.md` — public-facing privacy stance: no sending, no body storage, AES-GCM refresh-token encryption with tenant-AAD, account delete cascade, AUTH-05 reconnect UX, log redaction posture.
- `docs/casa/scopes-justification.md` — explicit narrative on why `gmail.modify` (not `gmail.readonly`, not `gmail.send`); explicit "No auto-send", AES-GCM, `@Sensitive`/ArchUnit, and `AUTH-05` `invalid_grant` → DISCONNECTED narrative; cross-references Phase 1 plans 02/03/05/06/07/08.
- `docs/casa/data-handling-attestation.md` — ASVS-aligned control matrix referencing every Phase 1 evidence test by file path (`MultiTenantLeakIntegrationTest`, `LogScrubSyntheticTrafficTest`, `DisconnectOnInvalidGrantTest`, `AccountDeletionE2ETest`, `OnboardingStateMachineTest`, `RefreshTokenCipherTest`, `NonceUniquenessTest`, `OpenApiSchemaTest`, `scripts/verify-codegen.sh`).

## Verification

```
$ ./gradlew :backend:api:test --tests "com.zeromail.api.LogScrubSyntheticTrafficTest"
LogScrubSyntheticTrafficTest:  tests=1, failures=0, errors=0

$ grep -c "AUTH-05\|invalid_grant\|gmail.modify\|AES-GCM-256\|ScopedValue\|No auto-send" \
        docs/casa/scopes-justification.md
10

$ grep -c "AUTH-05\|gmail.modify\|AES-GCM-256\|ScopedValue" \
        docs/casa/data-handling-attestation.md
6
```

## Open checkpoint — Human action required (cannot be automated)

This plan is `autonomous: false`. The remaining filing steps are external:

1. In **Google Cloud Console → APIs & Services → OAuth consent screen**: configure app name (Zero Mail), support email, developer contact, app logo, and a privacy-policy URL hosting the content of `docs/casa/privacy-policy-draft.md`.
2. Add `https://www.googleapis.com/auth/gmail.modify` to requested scopes.
3. Click **Submit for verification**. Google routes the app to a CASA-certified lab based on tier assignment.
4. Forward the packet (`docs/casa/*.md`) to the assigned lab contact.
5. Capture the **submission ID**, **assigned lab**, and **submission date**; fill them into `docs/casa/submission-log.md`.
6. Commit the updated submission log.

Resume signal in the gsd-checkpoint protocol: `casa-filed`.

## Threat mitigations from PLAN.md

| ID | Mitigation status |
|----|-------------------|
| T-02-runtime | ✅ `LogScrubSyntheticTrafficTest` drives real authenticated request traffic with sentinel-laden seed data; asserts zero sentinel occurrences + at least one `scrubbed=true` MDC event. |
| T-casa | ⏳ CASA package drafted; actual external filing pending the human checkpoint above. |

## Acceptance criteria

| Criterion | Status |
|-----------|--------|
| `:backend:api:test --tests "*LogScrubSyntheticTrafficTest"` exits 0 | ✅ |
| `grep "TestRestTemplate\|RestClient" LogScrubSyntheticTrafficTest.java` | ✅ (uses `RestClient`; Boot 4 removed `TestRestTemplate`) |
| `grep "leak-probe-12345" LogScrubSyntheticTrafficTest.java` | ✅ |
| `grep "LEAK-REFRESH-TOKEN-ABC" LogScrubSyntheticTrafficTest.java` | ✅ |
| `grep "scrubbed.*true" LogScrubSyntheticTrafficTest.java` | ✅ |
| `grep "probes:" application.yml` | ✅ |
| `grep "prometheus" application.yml` | ✅ |
| `test -f docs/casa/submission-log.md` | ✅ |
| `grep "gmail.modify" docs/casa/scopes-justification.md` | ✅ |
| `grep "AES-GCM-256" docs/casa/data-handling-attestation.md` | ✅ |
| `grep "No auto-send" docs/casa/scopes-justification.md -i` | ✅ |
| `grep "ScopedValue" docs/casa/data-handling-attestation.md -i` | ✅ |
| `grep "AUTH-05" docs/casa/scopes-justification.md` | ✅ |
| `grep "invalid_grant" docs/casa/scopes-justification.md` | ✅ |
| External CASA submission filed (human checkpoint) | ⏳ pending user action |

## Notes for follow-ups

- The privacy-policy draft contains _TBD_ contact placeholders that must be filled before the public hosted version goes live.
- The submission-log filing checklist is the running todo list for the human checkpoint.
