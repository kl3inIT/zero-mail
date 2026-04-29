---
phase: 02A
review_cycle: 2
reviewers: [codex]
reviewed_at: 2026-04-29T09:57:31.9552875+07:00
replan_commit: b4af158
plans_reviewed:
  - 02A-00-PLAN.md
  - 02A-01-PLAN.md
  - 02A-02-PLAN.md
  - 02A-03-PLAN.md
  - 02A-04-PLAN.md
  - 02A-05-PLAN.md
current_high: 3
---

# Cross-AI Plan Review - Phase 02A (Cycle 2)

Only the Codex reviewer was requested and invoked for this cycle, so this is a single-reviewer synthesis rather than a multi-reviewer consensus.

## Codex Review

### Summary

Cycle 2 materially improves the Phase 02A plans. The prior HIGH issues around compile-safe scaffolds, Pub/Sub security in tests, tenant lookup, atomic claim semantics, native idempotency inserts, watch baseline initialization, and final verification are mostly resolved.

Remaining risk is lower, but there are still a few plan-level correctness gaps that could break compilation or leave important behaviors unverified.

### Strengths

- Pub/Sub OIDC security is now active under the test profile and explicitly verified.
- Tenant lookup is correctly moved to unscoped `JdbcTemplate`, with tenant-bound writes after `TenantContext` binding.
- `claimPendingBatch` now atomically updates rows to `PROCESSING` with `RETURNING *`.
- Message observation idempotency now uses native `INSERT ... ON CONFLICT DO NOTHING`.
- Final verification now requires all Wave 0 tests enabled and GREEN.
- `recordWatchSuccess` now initializes `last_synced_history_id` from `watch_history_id`.

### Prior Cycle HIGH Resolution

- Mock OIDC fixture module visibility: **FULLY RESOLVED** - `MockGoogleOidcServer` now lives under `backend/api/src/test/java/com/zeromail/api/support`.
- Disabled Java tests referencing missing symbols: **FULLY RESOLVED** - disabled controller scaffolds are specified as raw HTTP/JSON/JdbcTemplate tests without missing DTO/controller imports.
- Test-profile Pub/Sub security conflict: **FULLY RESOLVED** - `PubSubSecurityConfig` explicitly omits `@Profile("!test")` and remains active under `@ActiveProfiles("test")`.
- Same-wave `TenantService.setTriagePaused` dependency: **FULLY RESOLVED** - Plan 03 owns `TenantService.setTriagePaused`, so it no longer depends on Plan 02.
- `SKIP LOCKED` locks released before processing: **FULLY RESOLVED** - Plan 01 replaces claim-by-select with atomic `UPDATE ... RETURNING *`.
- Pub/Sub transaction opened before tenant binding: **FULLY RESOLVED** - Plan 03 performs unscoped lookup first, then wraps insert in `ScopedValue` plus `TransactionTemplate`.
- Tenant-owned Gmail email lookup before binding: **FULLY RESOLVED** - Plan 03 rejects JPA lookup and uses parameterized `JdbcTemplate`.
- Initial watch does not seed `last_synced_history_id`: **FULLY RESOLVED** - `recordWatchSuccess` sets it from `watchHistoryId` when null or lower.
- JPA duplicate-key idempotency via caught exception: **FULLY RESOLVED** - repositories now require native `ON CONFLICT DO NOTHING`.
- Plan 05 misses Wave 0 tests: **FULLY RESOLVED** - Plan 05 lists all backend and frontend Wave 0 tests and requires no `@Disabled` / `it.skip`.
- STATE.md blocker grep contradiction: **FULLY RESOLVED** - Plan 05 removes the blocker bullet and uses a precise grep for that bullet, while the retrospective decision text does not match the blocker pattern.

### Concerns

- **HIGH** Plan 03 references new files `IngestResult.java`, `GmailNotification.java`, and `FlexibleLongDeserializer.java`, and Plan 03 Task 2 references `GmailConnectionProjection.java`, but these files are not listed in `files_modified`. This can cause incomplete execution tracking and missed review/commit scope.

- **HIGH** Plan 02 extends `GmailConnectionService.disconnect()` to call `users.stop()`, then also uses `connectionService.disconnect(tenantId)` on `InvalidGrantException` paths. If refresh is invalid, `disconnect()` may try to refresh/build a Gmail client to call `users.stop()` and fail before marking the connection disconnected unless the DB update is structurally guaranteed outside that best-effort path.

- **HIGH** Plan 03 keeps the normal user `SecurityConfig` under `@Profile("!test")`, but then requires `TriagePauseControllerTest` and `MeControllerTest` under test profile to exercise authenticated, tenant-bound endpoints. The plan does not specify a test security chain or tenant-binding mechanism for those tests, so they may fail with missing tenant context or bypass the real authorization behavior.

- **MEDIUM** `PubSubIngestionService` uses `List.getFirst()`, which is only available on Java 21+ `List`, so Java 25 is fine, but it may not match project style and can surprise if Gradle source compatibility/toolchain is misconfigured.

- **MEDIUM** `MailMessageObservedRepository.insertObservedIfAbsent` passes `String[]` into a native query for `text[]`; depending on Hibernate/PostgreSQL binding, this may need an explicit cast such as `CAST(:labelIds AS text[])` or JDBC `Array` handling.

- **MEDIUM** Plan 02 creates `GmailApiClientFactory` in `backend/core`, but the implementation uses direct `newTrustedTransport()` and direct `HttpClient.newHttpClient()`, making hermetic worker tests harder unless the plan also introduces injectable base URLs/transports/HTTP clients.

- **MEDIUM** Plan 02 says `GmailHistoryProcessorTest` uses `MockGmailHistoryServer`, but `GmailDeliveryProcessingService` calls the real token endpoint unless `GmailApiClientFactory` is injectable/configurable for tests.

- **MEDIUM** `GmailPubSubControllerIntegrationTest.invalidPayload_returns200_dropsSilently` is now aligned with implementation, but this behavior should be explicitly justified as an ack-fast anti-redelivery policy because malformed payloads from non-Google callers are still authenticated by OIDC first.

- **LOW** Plan 00 still says active backend RED tests may fail at `compileTestJava`; that blocks running any test task in the module until production classes exist. This is acceptable if intentional, but it weakens Wave 0 feedback granularity.

### Suggestions

- Add all newly created helper DTO/enum/deserializer/projection files to Plan 03 `files_modified`.
- Split `disconnect()` into "mark disconnected" and "best-effort stop watch" so invalid-grant paths cannot be blocked by watch-stop cleanup.
- Specify the test-profile authentication/tenant-binding setup for `/me` and `/tenant/triage-pause`, or make those tests use the existing project test auth pattern explicitly.
- Make `GmailApiClientFactory` testable via injectable HTTP transport/token endpoint/Gmail base URL, or document the mocking seam used by worker tests.
- Add an acceptance check that native `label_ids` insertion works through `insertObservedIfAbsent`, not only through entity persistence.

### Risk Assessment

Overall risk: **MEDIUM**.

The major architectural hazards from Cycle 1 are fixed. Remaining risk is mostly execution-level: missing file tracking, test-profile security ambiguity, and invalid-grant/disconnect cleanup coupling. These are fixable before implementation and do not undermine the overall design.

### Current HIGH Concerns

- Plan 03 omits several newly created/modified files from `files_modified`, including `IngestResult.java`, `GmailNotification.java`, `FlexibleLongDeserializer.java`, and likely `GmailConnectionProjection.java`.
- `disconnect()` best-effort `users.stop()` may interfere with invalid-grant disconnect paths unless the DB status update is guaranteed independently.
- Test-profile authenticated endpoint behavior for `/me` and `/tenant/triage-pause` is underspecified while the real user security chain remains disabled under `test`.

CURRENT_HIGH_COUNT: 3

---

## Consensus Summary

Only Codex was invoked in Cycle 2, so the consensus summary reflects a single external review.

### Agreed Strengths

- The Cycle 1 architectural hazards were substantially addressed in the replan: tenant lookup, transaction boundaries, OIDC test-profile security, atomic claim semantics, watch baseline initialization, and native idempotency inserts are now called out explicitly.
- The phase still covers the right Phase 2A surface: Gmail watch lifecycle, Pub/Sub push ingress, OIDC verification, durable idempotency, history processing, pause state, reconnect visibility, and final verification.
- The final verification plan now better defends against false GREEN closure by requiring Wave 0 tests to be enabled and passing.

### Agreed Concerns

- HIGH: Plan 03 file tracking is incomplete for several helper DTO/deserializer/projection files, which can cause missed execution scope.
- HIGH: `disconnect()` needs a stronger separation between durable DB state change and best-effort Gmail `users.stop()` cleanup, especially on invalid-grant paths.
- HIGH: Test-profile behavior for authenticated `/me` and `/tenant/triage-pause` tests remains underspecified because the normal user security chain is disabled under `test`.

### Divergent Views

- None observed. Only one reviewer was invoked.

## Cycle Summary

- Prior Cycle HIGH concerns: 11
- Fully resolved prior HIGH concerns: 11
- Partially resolved prior HIGH concerns: 0
- Unresolved prior HIGH concerns: 0
- New Cycle 2 HIGH concerns: 3
- Current unresolved HIGH concerns: 3
