# Worker tenant context failure

## Symptom

- Worker logs `Closed by interrupt` while inserting `mail_message_observed`.
- Async processing later fails with `assigned tenant id differs from current tenant id`
  for `ThreadReplyStatusEntity.tenantId`.

## Scope

- Inspect Gmail delivery observation, event publication, async listeners, and tenant context
  propagation in worker/core.
- Fix the production path so tenant-scoped entity writes run with the event tenant.

## Verification

- Focused backend tests around affected listener/use case.
- Compile worker/core as needed.

## Result

- `ClassifyThreadReplyStatusService` now opens classifier writes in a new
  transaction after binding the tenant from the mail event.
- Added an integration test that reproduces the listener shape: an outer
  transaction opens with the default tenant, then classification runs for the
  event tenant.
- Verified:
  - `./gradlew.bat --no-daemon :backend:core:test --tests "com.zeromail.core.thread.ClassifyThreadReplyStatusServiceIntegrationTest" --stacktrace`
  - `./gradlew.bat --no-daemon :backend:core:compileJava :backend:worker:compileJava --stacktrace`
