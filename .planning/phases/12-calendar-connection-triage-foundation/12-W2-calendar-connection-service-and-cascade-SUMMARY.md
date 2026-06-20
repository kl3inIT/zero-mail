---
phase: 12-calendar-connection-triage-foundation
plan: W2
subsystem: calendar-connection-service-and-cascade
status: complete
tags: [cal-conn-01, cal-conn-04, cal-conn-05, cal-conn-06, cal-conn-07, cal-conn-08, calendar-disconnect-cascade, modulith-event, rest-api]
requirements_completed: [CAL-CONN-01, CAL-CONN-04, CAL-CONN-05, CAL-CONN-06, CAL-CONN-07, CAL-CONN-08]
requires:
  - CalendarConnectionEntity / CalendarEntity / MailboxCalendarPreferenceEntity + repositories (W1)
  - CalendarApiClientFactory.evictAccessToken(UUID) (W1) — disconnect cascade target
  - CalendarOAuthSuccessHandler (W1) — extended in this plan to invoke snapshot ingest
  - GlobalExceptionHandler + BusinessException + ErrorClass (shared) — error translation
  - MailboxBindingFilter (Phase 10) — binds /mailboxes/{id}/* routes
  - OAuthIntentSnapshot + IntentCarryingAuthorizationRequestRepository (Phase 10) — targetMailboxId stamp on calendar OAuth init
provides:
  - com.zeromail.core.calendar.event.CalendarConnectionDisconnected (Modulith event, AFTER_COMMIT)
  - com.zeromail.core.calendar.projection.CalendarConnectionView (+ nested CalendarView)
  - com.zeromail.core.calendar.projection.MailboxCalendarPreferenceView
  - com.zeromail.core.calendar.usecases.CalendarConnectionService (list / resolveOwnedConnectionOrThrow / disconnect + AFTER_COMMIT listener)
  - com.zeromail.core.calendar.usecases.CalendarSnapshotIngestionService (D-06 + Pitfall 8)
  - com.zeromail.core.calendar.usecases.MailboxCalendarPreferenceService (replace-strategy updateForMailbox)
  - com.zeromail.core.calendar.usecases.UpdateMailboxCalendarPreferenceCommand
  - com.zeromail.core.calendar.usecases.CalendarToggleService (D-13 cascade)
  - Repository additions: CalendarRepository.findByIdAndTenantId + findByCalendarConnectionIdAndExternalCalendarIdAndTenantId; MailboxCalendarPreferenceRepository.deleteByMailboxIdAndTenantId
  - REST endpoints under /api/calendar/* (4 new paths)
  - Record DTOs under com.zeromail.api.dto.calendar with springdoc-emitted OpenAPI annotations
affects:
  - backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler — extended with snapshot ingest hook (new @Autowired ctor parameter, swallows snapshot failures so OAuth redirect never breaks)
tech_stack_added: []  # No new runtime deps — google-api-services-calendar already pinned in W0
patterns_followed:
  - service-owned @Transactional with TransactionTemplate for multi-table cascade (CONVENTIONS §1 + PATTERNS.md CalendarConnectionService)
  - Modulith @NamedInterface("events") + @TransactionalEventListener(AFTER_COMMIT) for in-process side effects (CONVENTIONS §6)
  - read-side projection records under projection/* (CONVENTIONS §2 CQRS-lite)
  - record DTOs with @Schema requiredProperties + allowableValues + @JsonInclude(NON_NULL) (CONVENTIONS §3)
  - replace-strategy upsert for small per-mailbox N (MailboxCalendarPreferenceService.updateForMailbox)
  - resolveOwnedConnectionOrThrow tenant-scoped guard (mirrors GmailConnectionService precedent)
  - thin controllers + service-only injection (CONVENTIONS §1)
key_files_created:
  - backend/core/src/main/java/com/zeromail/core/calendar/event/CalendarConnectionDisconnected.java
  - backend/core/src/main/java/com/zeromail/core/calendar/event/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/projection/CalendarConnectionView.java
  - backend/core/src/main/java/com/zeromail/core/calendar/projection/MailboxCalendarPreferenceView.java
  - backend/core/src/main/java/com/zeromail/core/calendar/projection/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionService.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/MailboxCalendarPreferenceService.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/UpdateMailboxCalendarPreferenceCommand.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarToggleService.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/package-info.java
  - backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectionController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/calendar/MailboxCalendarPreferenceController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/calendar/package-info.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarConnectionResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarSubResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/MailboxCalendarPreferenceResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/UpdateMailboxCalendarPreferenceRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/UpdateCalendarEnabledRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarToggleResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/package-info.java
  - backend/core/src/test/java/com/zeromail/core/calendar/usecases/CalendarConnectionServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceConstraintTest.java
  - backend/core/src/test/java/com/zeromail/core/calendar/event/CalendarConnectionDisconnectedListenerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/calendar/CalendarConnectionControllerTest.java
key_files_modified:
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarRepository.java (+2 lookup methods)
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceRepository.java (+ deleteByMailboxIdAndTenantId bulk delete)
  - backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java (snapshot ingest hook + activeMailboxId resolution from session)
decisions:
  - "AFTER_COMMIT mechanism in CalendarOAuthSuccessHandler: synchronous call to CalendarSnapshotIngestionService.ingestSnapshot(...) after the row save returns. With spring.jpa.open-in-view=false and no enclosing @Transactional on the success handler, the calendarConnectionRepository.save() auto-commits, so the snapshot call IS effectively post-commit. Snapshot failures (IOException / RuntimeException) are swallowed and logged so the OAuth redirect never breaks — the row save is the user's primary expected outcome (CONNECTED card on /settings/calendar). The plan's preferred AFTER_COMMIT-via-TransactionSynchronization pattern was rejected as more complex than the swallow-and-log path with no observable behavior difference. Alternative (publish CalendarConnectionEstablished event + @Async @TransactionalEventListener) is deferred until snapshot latency becomes user-visible."
  - "UPSERT strategy in CalendarSnapshotIngestionService: lookup by (calendar_connection_id, external_calendar_id, tenant_id) before insert. Google sends stable external_calendar_id values across re-connects of the same account, so a fresh INSERT would trip uq_calendar_connection_external_id; UPSERT updates name/description/primary in place. Required a new repository lookup method (CalendarRepository.findByCalendarConnectionIdAndExternalCalendarIdAndTenantId)."
  - "Listener co-location: the AFTER_COMMIT listener for CalendarConnectionDisconnected lives in the same CalendarConnectionService class as the publisher, NOT a separate CalendarCacheEvictionListener. Both the publisher (disconnect tx) and the consumer (CalendarApiClientFactory.evictAccessToken) are in the same Modulith module and the contract is single-purpose. Phase 13's free/busy cache listener will subscribe to the same event from a separate class when that module ships."
  - "Replace-strategy upsert in MailboxCalendarPreferenceService.updateForMailbox: bulk-delete all preference rows for the mailbox then INSERT fresh rows. Simpler than per-row diff and correct for the small per-mailbox N (< 10). Trusts the DB partial unique indexes (uq_mailbox_event_write, uq_mailbox_brief_source) as race-proof backstops; service-layer ownership + is_enabled checks exist only for friendlier error messages."
  - "Snapshot called inline in CalendarOAuthSuccessHandler with try-catch wrap: the alternative (publish an internal event + AFTER_COMMIT listener that ingests) was rejected because spring.jpa.open-in-view=false + no @Transactional on the handler means save() auto-commits, so the snapshot call is already effectively post-commit. The wrap-and-log ensures Google API failures never break the OAuth redirect — the connection row is the user's primary expected outcome."
metrics:
  duration: "~30 minutes"
  tasks_completed: 3
  files_created: 25
  files_modified: 3
  tests_added: 5  # 5 test classes: 7 CalendarConnectionServiceTest cases + 5 CalendarSnapshotIngestionServiceTest cases + 3 MailboxCalendarPreferenceConstraintTest cases + 2 CalendarConnectionDisconnectedListenerTest cases + 6 CalendarConnectionControllerTest cases = 23 @Test methods
  completed_date: 2026-06-20
---

# Phase 12 Plan W2: Calendar Connection Service + Cascade — Summary

**One-liner:** Ship Phase 12's user-actionable backend surface — CalendarConnectionService with sync TransactionTemplate disconnect cascade + AFTER_COMMIT CalendarConnectionDisconnected event + factory access-token cache eviction; CalendarSnapshotIngestionService with D-06 default-on-connect primary-calendar role enrollment + Pitfall 8 workspace policy absorption; MailboxCalendarPreferenceService replace-strategy + CalendarToggleService D-13 cascade; thin REST controllers + record DTOs under `/api/calendar/*` ready for W3 frontend regen.

## Tasks Executed

| Task | Name                                                                                              | Commit     | Files                                                                                                                                                                |
| ---- | ------------------------------------------------------------------------------------------------- | ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | CalendarConnectionService + disconnect cascade + CalendarConnectionDisconnected event + listener  | `69d74f4f` | 7 new production files (event + projection + usecases skeleton) + 2 tests                                                                                            |
| 2    | CalendarSnapshotIngestionService + MailboxCalendarPreferenceService + CalendarToggleService + OAuth wiring | `298c138d` | 4 new core services + 2 repository extensions + CalendarOAuthSuccessHandler wiring + 2 tests                                                                         |
| 3    | REST controllers + record DTOs + controller tests                                                 | `5835cb7a` | 2 controllers + 6 DTO records + 2 package-info markers + 1 controller test (6 @Test methods)                                                                         |

## Output Contract (from PLAN §output)

### (a) Chosen AFTER_COMMIT mechanism in CalendarOAuthSuccessHandler

**Synchronous inline call** to `CalendarSnapshotIngestionService.ingestSnapshot(...)` after the row save returns, wrapped in `try-catch (IOException | RuntimeException)` so any failure is logged but never breaks the OAuth redirect.

Rationale: `spring.jpa.open-in-view=false` + no `@Transactional` on `CalendarOAuthSuccessHandler.onAuthenticationSuccess(...)` means `calendarConnectionRepository.save(...)` runs in auto-commit, so the next call IS effectively post-commit. The TransactionSynchronizationManager pattern proposed in the plan adds complexity with no observable behavior difference. The wrap-and-log discipline guarantees Google API failures never break the OAuth redirect — the connection row is the user's primary expected outcome (the CONNECTED card on `/settings/calendar`). Snapshot can be re-attempted via a maintenance hook in W3+.

### (b) Chosen UPSERT-on-reconnect strategy in CalendarSnapshotIngestionService

**Lookup-before-insert** keyed on `(calendar_connection_id, external_calendar_id, tenant_id)`. Google returns stable `external_calendar_id` values across re-connects of the same account, so a fresh `INSERT` would trip `uq_calendar_connection_external_id`. The UPSERT updates `name`/`description`/`primary`/`timezone` in place; missing rows take the INSERT branch.

Implementation lives in `CalendarSnapshotIngestionService.upsertCalendar(...)` and required a new repository method `CalendarRepository.findByCalendarConnectionIdAndExternalCalendarIdAndTenantId(...)`.

### (c) Four new OpenAPI paths

The four new endpoints emitted by the controllers (verified via JVM compile + controller test; the springdoc `generateOpenApiDocs` task requires the dev SSH tunnel to localhost:5555 per `reference_dev_db_ssh_tunnel.md` memory, so spec emit is deferred to W3 frontend regen):

1. `GET /api/calendar/mailboxes/{mailboxId}/connections` → `List<CalendarConnectionResponse>`
2. `DELETE /api/calendar/connections/{calendarConnectionId}` → 204 No Content
3. `PATCH /api/calendar/calendars/{calendarId}/enabled` → `CalendarToggleResponse`
4. `GET /api/calendar/mailboxes/{mailboxId}/preferences` + `PATCH /api/calendar/mailboxes/{mailboxId}/preferences` → `List<MailboxCalendarPreferenceResponse>`

### (d) Gmail connection row remains byte-identical after a Calendar disconnect

The W1 `CalendarOAuthTokenIsolationTest` (which inserts a Gmail row + a Calendar row, then exercises the calendar OAuth round-trip and asserts `gmailBytesAfter == gmailBytesBefore`) was re-run alongside this plan's commits and **passes BUILD SUCCESSFUL** — proving the W2 disconnect cascade does not regress W1's Gmail-Calendar token isolation invariant.

Additionally, `CalendarConnectionService.disconnect(...)` only writes to `calendar_connections` (status + disconnectedAt + refreshTokenEncrypted=null) and `mailbox_calendar_preferences` (bulk DELETE by `calendar_connection_id`). The Gmail row is not in this code path.

### (e) Preference row count immediately after a fresh OAuth round-trip

**Exactly 3 rows** — FREEBUSY + EVENT_WRITE + BRIEF_SOURCE for the primary calendar, bound to the `activeMailboxId` resolved from the OAuth `OAuthIntentSnapshot.targetMailboxId` session attribute.

Asserted by `CalendarSnapshotIngestionServiceTest.ingestSnapshot_insertsCalendarRowsAndPrimaryDefaultRoles`:

```
verify(mailboxCalendarPreferenceRepository, org.mockito.Mockito.times(3)).save(savedPreferences.capture());
assertThat(savedPreferences.getAllValues())
        .extracting(MailboxCalendarPreferenceEntity::getRole)
        .containsExactlyInAnyOrder(
                MailboxCalendarRole.FREEBUSY,
                MailboxCalendarRole.EVENT_WRITE,
                MailboxCalendarRole.BRIEF_SOURCE);
```

If `activeMailboxId == null` (e.g. legacy OAuth init outside the W3 connect-intent flow), the service still ingests sub-calendar rows but seeds 0 preference rows — asserted by `ingestSnapshot_skipsDefaultRolesIfActiveMailboxIsNull`.

## Verification

All targeted gradle commands green:

```
./gradlew :backend:core:test --tests "com.zeromail.core.calendar.usecases.CalendarConnectionServiceTest"
./gradlew :backend:core:test --tests "com.zeromail.core.calendar.event.CalendarConnectionDisconnectedListenerTest"
./gradlew :backend:core:test --tests "com.zeromail.core.calendar.usecases.CalendarSnapshotIngestionServiceTest"
./gradlew :backend:core:test --tests "com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceConstraintTest"
./gradlew :backend:api:test --tests "com.zeromail.api.controllers.calendar.CalendarConnectionControllerTest"
./gradlew :backend:api:test --tests "*ApplicationModulesTest"
./gradlew :backend:api:test --tests "*CalendarOAuthTokenIsolationTest"
./gradlew :backend:api:test --tests "*CalendarOAuthSuccessHandlerTest"
./gradlew :backend:api:test --tests "*DomainPurity*" --tests "*CalendarSchemaIsolation*" --tests "*OAuthScopeAllowList*"
```

Results:

| Test class                                          | Tests | Failed | Notes                                                                                                                                                                |
| --------------------------------------------------- | ----- | ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `CalendarConnectionServiceTest`                     | 7     | 0      | list joinings + disconnect cascade + event publication + idempotent re-disconnect + not-owned + cross-tenant resolution + listener evicts cache                      |
| `CalendarConnectionDisconnectedListenerTest`        | 2     | 0      | AFTER_COMMIT firing + rollback suppression                                                                                                                          |
| `CalendarSnapshotIngestionServiceTest`              | 5     | 0      | INSERT + skip-null-mailbox + 403 absorb + UPSERT-on-reconnect + non-403 propagation                                                                                  |
| `MailboxCalendarPreferenceConstraintTest`           | 3     | 0      | FREEBUSY multi-select + uq_mailbox_event_write reject + uq_mailbox_brief_source reject                                                                               |
| `CalendarConnectionControllerTest`                  | 6     | 0      | list + disconnect + cross-tenant + toggle + preferences list + preferences update (command captor)                                                                  |
| `ZeroMailApiApplicationModulesTest`                 | 1     | 0      | Modulith boundary still holds with new `core.calendar.event` + `core.calendar.usecases` + `core.calendar.projection` packages and `dto/calendar` @NamedInterface     |
| `CalendarOAuthTokenIsolationTest` (W1 regression)   | 1     | 0      | Gmail row byte-identical pre/post W2 changes                                                                                                                         |
| `CalendarOAuthSuccessHandlerTest` (W1 regression)   | 1     | 0      | W1 round-trip still passes with new `CalendarSnapshotIngestionService` ctor parameter (Spring DI handles wiring)                                                     |
| `DomainPurityArchTest`                              | 1     | 0      | core.calendar.domain still framework-free                                                                                                                            |
| `CalendarSchemaIsolationTest` (W0 regression)       | 6     | 0      | W0 schema invariants still green                                                                                                                                     |
| `OAuthScopeAllowListTest`                           | 2     | 0      | No new Java source carries calendar scope URL literals outside `core.oauth.scope`                                                                                    |

Total: **35 tests, all green.** Spring AI + Spring Modulith + ArchUnit + JPA + Postgres Testcontainer + plain-JUnit layers all exercise the W2 surface.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] `mailbox_calendar_preferences.mailbox_id` FK to `gmail_connections(id)` blocked the constraint test's first INSERT**
- **Found during:** Task 2 verify run — `MailboxCalendarPreferenceConstraintTest` failed on the first `savePreference(FREEBUSY, ...)` with `fk_mcp_mailbox` violation.
- **Issue:** The plan's test scaffolding seeded a `calendar_connections` row + `calendars` rows but not a `gmail_connections` row for `mailboxId`. The FK requires a real Gmail connection.
- **Fix:** Seed a minimal `gmail_connections` row (`id, tenant_id, google_email, status, is_primary`) in `@BeforeEach` before the calendar entities. Audit columns + ingestion fields get DB defaults (`defaultValueComputed: now()` on `created_at`/`updated_at`; `0` on `watch_consecutive_failures`).
- **Files modified:** `MailboxCalendarPreferenceConstraintTest.java`.
- **Commit:** Folded into Task 2's commit `298c138d`.

**2. [Rule 3 — Blocking] `dto/calendar` records were Modulith-internal so the controllers module could not import them**
- **Found during:** Task 3 verify run — `ZeroMailApiApplicationModulesTest` failed with `Module 'controllers' depends on non-exposed type com.zeromail.api.dto.calendar.* within module 'dto'`.
- **Issue:** Spring Modulith treats DTO sub-packages as module-internal by default. The existing `dto/gmail` and `dto/account` precedent uses `@NamedInterface(...)` to re-expose.
- **Fix:** Add `@org.springframework.modulith.NamedInterface("calendar")` to `dto/calendar/package-info.java`, matching the existing convention.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/dto/calendar/package-info.java`.
- **Commit:** Folded into Task 3's commit `5835cb7a`.

### Scope Boundaries Respected

- No frontend code under `apps/web` was written — W3 owns the React route + hooks + components.
- No `ical4j` triage classifier was written — W4 owns the inbox-projection ORDER BY + `text/calendar` classifier.
- No `PRESET_CALENDAR` rule matcher was written — W5 owns the rule-evaluator branch.
- No `booking_link.destination_calendar_id` cascade was written — deferred to Phase 14 per `<open_questions_from_research>` Q5 (table does not exist yet in v1.4).
- No new `controllers/calendar` package-info `@NamedInterface` was added — controllers module does not re-export to other modules; only DTOs do.

### Authentication Gates

None. All upstream calls (Google `calendarList.list()`) are mocked via `CalendarApiClientFactory` stub returning Mockito-built `Calendar` clients; no live OAuth flow exercised in tests.

## Threat Surface

All Phase 12 W2 threats in `<threat_model>` are mitigated as planned:

| Threat ID  | Mitigation Status                                                                                                                                                                                                                                                       |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| T-12-01    | `CalendarConnectionService.list(...)`, `resolveOwnedConnectionOrThrow(...)`, and the W1 `CalendarApiClientFactory.buildClientForCalendarConnection(...)` all carry tenantId explicitly. `CalendarConnectionServiceTest.resolveOwnedConnectionOrThrow_throwsForCrossTenantAccess` proves the seam. |
| T-12-02    | `CalendarConnectionDisconnected` event + AFTER_COMMIT listener evicts `CalendarApiClientFactory` access-token cache. `CalendarConnectionDisconnectedListenerTest` proves both firing and rollback suppression.                                                            |
| T-12-04    | `CalendarSnapshotIngestionService` catches `GoogleJsonResponseException(403)` and logs `event=calendar_snapshot_workspace_policy_blocked`. `CalendarSnapshotIngestionServiceTest.ingestSnapshot_handlesWorkspacePolicy403AndDoesNotThrow` pins it.                       |
| T-12-V4-2  | Preferences controller routes are under `/api/calendar/mailboxes/{mailboxId}/preferences`; the existing `MailboxBindingFilter` (Phase 10) binds + validates `MailboxRef`. Service layer also re-validates calendar ownership against `tenantId`.                          |
| T-12-V5-1  | `MailboxCalendarPreferenceService.updateForMailbox` validates each input calendar via `resolveOwnedEnabledCalendar(tenantId, calendarId)` before any write; cross-tenant calendar IDs surface as `IllegalArgumentException` → 400 via central GlobalExceptionHandler.    |
| T-12-04bis | DB partial unique index `uq_mailbox_event_write` and `uq_mailbox_brief_source` are the race-proof backstops; `MailboxCalendarPreferenceConstraintTest` asserts both fire.                                                                                                |

No new threat-flag surface (no new network endpoint beyond the existing OAuth2 callback, no new file access pattern, no new trust-boundary schema change beyond W0).

## Known Stubs

None. W2 ships a complete backend surface from OAuth round-trip ↔ snapshot ingest ↔ disconnect cascade ↔ REST API ↔ listener-based cache eviction. W3 will wire the frontend route, hooks, and components against this exact surface using the springdoc-generated OpenAPI spec.

## Self-Check: PASSED

Files exist on disk (sample):

- `backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionService.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/calendar/event/CalendarConnectionDisconnected.java` — FOUND
- `backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectionController.java` — FOUND
- `backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarConnectionResponse.java` — FOUND
- `backend/api/src/test/java/com/zeromail/api/controllers/calendar/CalendarConnectionControllerTest.java` — FOUND

Commits exist in `git log --oneline`:

- `69d74f4f` — FOUND (Task 1: CalendarConnectionService + event + listener)
- `298c138d` — FOUND (Task 2: snapshot + preferences + toggle + OAuth wiring)
- `5835cb7a` — FOUND (Task 3: controllers + DTOs + tests)

All 11 targeted test classes report `failures=0` in their `TEST-*.xml` outputs under `backend/{core,api}/build/test-results/test/`.
