---
phase: 12-calendar-connection-triage-foundation
plan: 03
type: execute
wave: 2
depends_on:
  - 12-01
  - 12-02
files_modified:
  - backend/core/src/main/java/com/zeromail/core/calendar/event/CalendarConnectionDisconnected.java
  - backend/core/src/main/java/com/zeromail/core/calendar/event/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionService.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/MailboxCalendarPreferenceService.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarToggleService.java
  - backend/core/src/main/java/com/zeromail/core/calendar/usecases/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/projection/CalendarConnectionView.java
  - backend/core/src/main/java/com/zeromail/core/calendar/projection/MailboxCalendarPreferenceView.java
  - backend/core/src/main/java/com/zeromail/core/calendar/projection/package-info.java
  - backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectionController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/calendar/MailboxCalendarPreferenceController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/calendar/package-info.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarConnectionResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarSubResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/MailboxCalendarPreferenceResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/UpdateMailboxCalendarPreferenceRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/UpdateCalendarEnabledRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/calendar/package-info.java
  - backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java
  - backend/core/src/test/java/com/zeromail/core/calendar/usecases/CalendarConnectionServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceConstraintTest.java
  - backend/core/src/test/java/com/zeromail/core/calendar/event/CalendarConnectionDisconnectedListenerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/calendar/CalendarConnectionControllerTest.java
autonomous: true
requirements:
  - CAL-CONN-01
  - CAL-CONN-04
  - CAL-CONN-05
  - CAL-CONN-06
  - CAL-CONN-07
  - CAL-CONN-08
must_haves:
  truths:
    - "User can call GET /api/calendar/mailboxes/{mailboxId}/connections and receive a JSON array of connections the workspace owns, with per-calendar enable/disable flags + role assignments for the mailbox"
    - "DELETE /api/calendar/connections/{id} transitions status to DISCONNECTED, deletes mailbox_calendar_preference rows referencing the connection, retains the calendar_connections row + any triage_audit rows, and publishes CalendarConnectionDisconnected AFTER_COMMIT"
    - "Right after a successful Calendar OAuth, the connection's primary calendar is enrolled as a mailbox_calendar_preference for FREEBUSY + EVENT_WRITE + BRIEF_SOURCE for the active mailbox only (D-06)"
    - "Calling buildClientForCalendarConnection(...) after disconnect throws CalendarDisconnectedException because the in-memory access-token cache is evicted by the listener"
    - "MailboxCalendarPreferenceEntity rows can only carry role values FREEBUSY/EVENT_WRITE/BRIEF_SOURCE; the partial unique indexes uq_mailbox_event_write + uq_mailbox_brief_source forbid a second EVENT_WRITE or BRIEF_SOURCE row for the same mailbox"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java"
      provides: "list / resolveOwnedConnectionOrThrow / disconnect (cascade-revoke) / synchronous-tx multi-table operation"
    - path: "backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionService.java"
      provides: "Post-OAuth calendarList.list() ingest; primary-calendar default-on-connect role enrollment (D-06)"
    - path: "backend/core/src/main/java/com/zeromail/core/calendar/event/CalendarConnectionDisconnected.java"
      provides: "Modulith event published AFTER_COMMIT by disconnect tx; W4 + W5 + Phase 13 cache listeners subscribe"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectionController.java"
      provides: "REST surface: GET list-by-mailbox, DELETE disconnect, PATCH calendar enable/disable"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/calendar/MailboxCalendarPreferenceController.java"
      provides: "REST surface: GET/PATCH per-mailbox role assignments"
  key_links:
    - from: "CalendarConnectionService.disconnect"
      to: "ApplicationEventPublisher.publishEvent(new CalendarConnectionDisconnected(...))"
      via: "TransactionTemplate commits → event listener runs AFTER_COMMIT → CalendarApiClientFactory.evictAccessToken(...) + free/busy cache eviction (Phase 13 hook)"
      pattern: "CalendarConnectionDisconnected"
    - from: "CalendarSnapshotIngestionService"
      to: "MailboxCalendarPreferenceRepository.save(primaryCalendarRoleRows)"
      via: "D-06 default-on-connect — 3 preference rows for the primary calendar against the active mailbox only"
      pattern: "primary"
    - from: "CalendarConnectionController"
      to: "CalendarConnectionService.resolveOwnedConnectionOrThrow"
      via: "ownership guard before disconnect / patch"
      pattern: "resolveOwnedConnectionOrThrow"
---

<objective>
Land the Calendar connection service + cascade + REST surface that makes Phase 12 user-actionable from the API (W3 owns the UI):

1. `CalendarConnectionService` — list / resolveOwnedConnectionOrThrow / disconnect. The disconnect path uses a `TransactionTemplate` for the multi-table sync write (mark `calendar_connections.status=DISCONNECTED`, DELETE all `mailbox_calendar_preference` rows referencing this connection, retain audit), and AFTER_COMMIT publishes `CalendarConnectionDisconnected` via `ApplicationEventPublisher`.
2. `CalendarSnapshotIngestionService` — invoked from `CalendarOAuthSuccessHandler` AFTER the connection row is saved. Calls `calendarList.list()` via the W1 `CalendarApiClientFactory`, INSERTs one `CalendarEntity` row per Google calendar (primary + secondary), and per D-06 seeds three `MailboxCalendarPreferenceEntity` rows (`FREEBUSY` + `EVENT_WRITE` + `BRIEF_SOURCE`) for the primary calendar against the active mailbox only.
3. `MailboxCalendarPreferenceService` — PATCH per-role per-mailbox assignments (single-select enforcement for EVENT_WRITE/BRIEF_SOURCE at the service layer + DB partial unique index from W0; multi-select for FREEBUSY).
4. `CalendarToggleService` — Per-calendar `is_enabled` toggle; cascade-delete dependent preference rows when toggling off (D-13).
5. `CalendarConnectionDisconnected` Modulith event + listener in `core.calendar` evicting `CalendarApiClientFactory` access-token cache (Phase 13's free/busy cache listener subscribes via the same event).
6. REST controllers + records-only DTOs under `backend/api/controllers/calendar/` + `backend/api/dto/calendar/` with full OpenAPI schema discipline (CONVENTIONS §3) and explicit `from(...)` factories.
7. Wire `CalendarOAuthSuccessHandler` (created in W1) to call `CalendarSnapshotIngestionService.ingestSnapshot(...)` AFTER_COMMIT — completing the OAuth round-trip ↔ sub-calendar ingestion ↔ default role assignment loop.

Purpose: Phase 12 backend becomes complete enough that W3 frontend can render real data and exercise real disconnect flows. Test coverage gates CAL-CONN-04/05/06/07/08 invariants.
Output: 13 new files + 1 modified W1 file (success handler wiring) + 5 tests.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md
@.planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md
@.planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md
@.planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md
@.planning/phases/12-calendar-connection-triage-foundation/12-VALIDATION.md
@.planning/phases/12-calendar-connection-triage-foundation/12-01-SUMMARY.md
@.planning/phases/12-calendar-connection-triage-foundation/12-02-SUMMARY.md
</context>

<artifacts_this_phase_produces>
This plan creates the following Phase 12 symbols:

- `CalendarConnectionService.list(UUID tenantId, UUID mailboxId)` returning `List<CalendarConnectionView>` enriched with sub-calendar enable flags + per-mailbox preference assignments.
- `CalendarConnectionService.resolveOwnedConnectionOrThrow(UUID tenantId, UUID calendarConnectionId)` returning `CalendarConnectionEntity` (throws `CalendarConnectionNotOwnedException`).
- `CalendarConnectionService.disconnect(UUID tenantId, UUID calendarConnectionId)` — sync cascade-tx + AFTER_COMMIT event publication.
- `CalendarSnapshotIngestionService.ingestSnapshot(UUID tenantId, UUID calendarConnectionId, UUID activeMailboxId)` — calls Google calendarList, inserts `calendars` + 3 primary-calendar preference rows for activeMailboxId only.
- `CalendarToggleService.setEnabled(UUID tenantId, UUID calendarId, boolean enabled)` — toggles flag + cascade-deletes preference rows when disabling (D-13).
- `MailboxCalendarPreferenceService.updateForMailbox(UUID tenantId, UUID mailboxId, UpdateMailboxCalendarPreferenceCommand command)` — replaces FREEBUSY/EVENT_WRITE/BRIEF_SOURCE preference rows transactionally.
- `CalendarConnectionDisconnected` Modulith event (record carrying `UUID tenantId`, `UUID calendarConnectionId`, `Instant disconnectedAt`).
- A `@TransactionalEventListener(AFTER_COMMIT)` in `core.calendar.usecases` that evicts the `CalendarApiClientFactory` access-token cache for the disconnected `calendarConnectionId`.
- Read-side records: `CalendarConnectionView`, `MailboxCalendarPreferenceView`.
- REST endpoints under `/api/calendar/...` with springdoc-emitted OpenAPI schema.
- Record DTOs with `@Schema` annotations + `from(...)` factories.

NOT in this plan (deferred):
- Frontend route + hooks + components — W3.
- ical4j classifier and inbox-projection ORDER BY change — W4.
- PRESET_CALENDAR matcher and rule evaluator branch — W5.
- `booking_link.destination_calendar_id` null-out cascade — per `<open_questions_from_research>` Q5 deferred to Phase 14 (booking_link table doesn't exist yet); Phase 12 disconnect is a no-op for that column.
- RESCHEDULE classification — per `<open_questions_from_research>` Q2 deferred; W4 ships INVITE for all `METHOD:REQUEST`.
</artifacts_this_phase_produces>

<tasks>

<task type="auto">
  <name>Task 1: CalendarConnectionService + disconnect cascade + CalendarConnectionDisconnected event + listener</name>
  <files>backend/core/src/main/java/com/zeromail/core/calendar/event/CalendarConnectionDisconnected.java, backend/core/src/main/java/com/zeromail/core/calendar/event/package-info.java, backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java, backend/core/src/main/java/com/zeromail/core/calendar/usecases/package-info.java, backend/core/src/main/java/com/zeromail/core/calendar/projection/CalendarConnectionView.java, backend/core/src/main/java/com/zeromail/core/calendar/projection/MailboxCalendarPreferenceView.java, backend/core/src/main/java/com/zeromail/core/calendar/projection/package-info.java, backend/core/src/test/java/com/zeromail/core/calendar/usecases/CalendarConnectionServiceTest.java, backend/core/src/test/java/com/zeromail/core/calendar/event/CalendarConnectionDisconnectedListenerTest.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java (full file — service-owned @Transactional, `resolveOwnedConnectionOrThrow` precedent, TransactionTemplate for multi-table cascade, AFTER_COMMIT event publication via ApplicationEventPublisher)
    - backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java (Modulith event record precedent)
    - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarConnectionEntity.java (W1)
    - backend/core/src/main/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceRepository.java (W1 — the `deleteByCalendarConnectionId(...)` method this service calls)
    - backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java (W1 — the `evictAccessToken(calendarConnectionId)` method the listener calls)
    - CONVENTIONS.md §6 (Direct calls vs Spring Modulith events — disconnect is direct because the caller needs fail-fast; AFTER_COMMIT publication is for free/busy cache eviction + booking-link null-out in Phase 14)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-14 cascade contract — Tx1 mark status DISCONNECTED, delete preferences, retain audit; Phase 14's booking_link null-out is a no-op now per `<open_questions_from_research>` Q5)
    - .planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md (§ "CalendarConnectionService" lines 192-220 — TransactionTemplate + publish pattern + resolveOwnedConnectionOrThrow shape)
    - CLAUDE.md "Backend Code Style" (no opaque abbreviations; service uses `calendarConnectionRepository`, `mailboxCalendarPreferenceRepository`, `calendarConnectionEntity` etc.)
  </read_first>
  <action>
    Create `CalendarConnectionDisconnected.java` as a Java 25 record under `com.zeromail.core.calendar.event` with fields `(UUID tenantId, UUID calendarConnectionId, Instant disconnectedAt)`. Compact constructor validates all non-null via `Objects.requireNonNull(..., "fieldName")`. JavaDoc cites D-14 + lists known subscribers (this plan's eviction listener; Phase 13 free/busy cache; Phase 14 booking_link null-out cascade).

    Create `CalendarConnectionView.java` as a Java 25 record under `core.calendar.projection`. Fields: `UUID id`, `UUID tenantId`, `String googleEmail`, `CalendarConnectionStatus status`, `Instant connectedAt`, `Instant disconnectedAt`, `String googleProfileName`, `String googleProfilePictureUrl`, `List<CalendarView> calendars`, `List<MailboxCalendarPreferenceView> mailboxPreferences`. The two list fields are the joined read-shape used by `GET /api/calendar/mailboxes/{id}/connections`. Define a nested `CalendarView` record with `(UUID id, String externalCalendarId, String name, boolean isPrimary, boolean isEnabled, String timezone)`.

    Create `MailboxCalendarPreferenceView.java` as a record. Fields: `(UUID id, UUID mailboxId, UUID calendarConnectionId, UUID calendarId, MailboxCalendarRole role)`.

    Create `CalendarConnectionService.java`:
    - `@Service`. Constructor-inject `CalendarConnectionRepository`, `CalendarRepository`, `MailboxCalendarPreferenceRepository`, `ApplicationEventPublisher`, `PlatformTransactionManager` (build a `TransactionTemplate` field in the constructor with default propagation REQUIRED — disconnect runs as one parent transaction).
    - `@Transactional(readOnly = true) public List<CalendarConnectionView> list(UUID tenantId, UUID mailboxId)`. Steps: (a) load all `CalendarConnectionEntity` for the tenant via `calendarConnectionRepository.findAllByTenantId(tenantId)`; (b) for each connection load its `calendars` via `calendarRepository.findAllByCalendarConnectionIdAndTenantId(...)`; (c) load `mailboxCalendarPreferenceRepository.findAllByMailboxIdAndTenantId(mailboxId, tenantId)`; (d) build `CalendarConnectionView` instances joining the three result sets in-memory (CQRS-lite per CONVENTIONS §2 — for Phase 12 the join is small; if profiling later shows N+1, move to a Spring Data JDBC read-side query). Return ordered by `connectedAt DESC`.
    - `@Transactional(readOnly = true) public CalendarConnectionEntity resolveOwnedConnectionOrThrow(UUID tenantId, UUID calendarConnectionId)` — `findByIdAndTenantId(...)` then `orElseThrow(() -> new CalendarConnectionNotOwnedException(tenantId, calendarConnectionId));`. Mirror Phase 10's `GmailConnectionService.resolveOwnedConnectionOrThrow` failure contract: 404 not-owned/missing (controller maps via `@ExceptionHandler`).
    - `public void disconnect(UUID tenantId, UUID calendarConnectionId)` — NOT annotated `@Transactional` (uses `TransactionTemplate.execute(...)` per PATTERNS.md line 218). Inside the TX block: (1) load entity via `resolveOwnedConnectionOrThrow` (re-fetch under tx); (2) early-return if `status != CONNECTED` (idempotent — disconnecting an already-DISCONNECTED row is a no-op, no event); (3) mark `entity.markDisconnected(Instant.now())` (add this method to `CalendarConnectionEntity` if not present — sets `status = DISCONNECTED` and `disconnectedAt`); (4) `mailboxCalendarPreferenceRepository.deleteByCalendarConnectionId(calendarConnectionId, tenantId)` — bulk delete tenant-scoped; (5) per Q5 — NO `booking_link` writes; Phase 14 adds the listener. (6) Save the entity. After TX commit, `applicationEventPublisher.publishEvent(new CalendarConnectionDisconnected(tenantId, calendarConnectionId, Instant.now()))`. Note: publishing inside the TX is the standard pattern; Spring's `@TransactionalEventListener(AFTER_COMMIT)` delivers only when the tx commits, so an exception inside the TX leaves no event in flight.
    - Privacy logging: `log.info("event=calendar_connection_disconnected tenantId={} calendarConnectionId={}", tenantId, calendarConnectionId);` — NEVER log `googleEmail`.
    - Per memory `feedback_config_record_no_new_package.md`, do NOT create a new `CalendarConnectionConfig` properties class for this — there are no configurable knobs yet.

    Add a `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` method `onCalendarConnectionDisconnected(CalendarConnectionDisconnected event)` inside `CalendarConnectionService` (or in a separate `CalendarCacheEvictionListener` class — pick whichever the existing project pattern uses for AFTER_COMMIT eviction; check `core.gmail.usecases.GmailConnectionService` for a precedent). The listener calls `calendarApiClientFactory.evictAccessToken(event.calendarConnectionId())`. NO repository writes happen here. JavaDoc cites CONVENTIONS §6.

    Create `CalendarConnectionServiceTest.java` extending `PostgresContainerTest` (Testcontainers Postgres for the cascade-delete assertion). Cases:
    - `list_returnsCorrectShapeJoiningConnectionsCalendarsPreferences` — seed 2 connections + 3 sub-calendars + 2 preference rows; call `service.list(tenantId, mailboxId)`; assert returned `CalendarConnectionView`s contain the expected child lists.
    - `disconnect_marksDisconnectedAndDeletesPreferenceRows` — seed 1 connection + 2 preferences; call `service.disconnect(...)`; assert connection row remains with `status=DISCONNECTED`, preference rows are gone, `triage_audit` rows (seed one) are still present (audit retention).
    - `disconnect_publishesCalendarConnectionDisconnectedEventAfterCommit` — use Spring Modulith `@ApplicationModuleTest` event-collection support OR a `@RecordApplicationEvents` test extension to capture published events; assert exactly one `CalendarConnectionDisconnected` event with the right tenantId + calendarConnectionId.
    - `disconnect_isIdempotentWhenAlreadyDisconnected` — call disconnect twice; assert: no second event published, status remains DISCONNECTED, no exception.
    - `resolveOwnedConnectionOrThrow_throwsForCrossTenantAccess` — seed connection under tenantA; call with tenantB id; assert `CalendarConnectionNotOwnedException`.

    Create `CalendarConnectionDisconnectedListenerTest.java`:
    - `@SpringBootTest` slice with a `@MockitoBean CalendarApiClientFactory` (per TESTING.md `@MockitoBean` rule — `@MockBean` deprecated).
    - Publish a `CalendarConnectionDisconnected` event inside a programmatic `@Transactional` block via TransactionTemplate; assert after commit that `calendarApiClientFactory.evictAccessToken(eventCalendarConnectionId)` was called exactly once.
    - Also assert the listener does NOT fire if the publishing transaction rolls back (TestTransaction.flagForRollback + assert eviction NOT called).
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.calendar.usecases.CalendarConnectionServiceTest" --tests "com.zeromail.core.calendar.event.CalendarConnectionDisconnectedListenerTest"</automated>
  </verify>
  <acceptance_criteria>
    - All 9 listed files exist.
    - `grep -c '@Transactional' backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java` returns at least 2 (list + resolveOwnedConnectionOrThrow are @Transactional(readOnly=true); disconnect uses TransactionTemplate so it must NOT be @Transactional).
    - `grep -c 'TransactionTemplate' backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java` returns at least 1.
    - `grep -c 'publishEvent' backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java` returns at least 1.
    - `grep -c 'booking_link' backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java | grep -v '^#'` returns 0 (Phase 14 cascade is deferred per Q5).
    - All 4 test cases in `CalendarConnectionServiceTest` green; listener test green.
    - `ApplicationModulesTest` green.
    - JetBrains `get_file_problems` returns no errors on the new production files.
  </acceptance_criteria>
  <done>Disconnect flow works end-to-end with cascade DELETE on preferences, retained audit, and Modulith event published only on TX commit; the listener evicts the factory's cache.</done>
</task>

<task type="auto">
  <name>Task 2: CalendarSnapshotIngestionService + MailboxCalendarPreferenceService + CalendarToggleService + constraint test + wire into CalendarOAuthSuccessHandler</name>
  <files>backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionService.java, backend/core/src/main/java/com/zeromail/core/calendar/usecases/MailboxCalendarPreferenceService.java, backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarToggleService.java, backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java, backend/core/src/test/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionServiceTest.java, backend/core/src/test/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceConstraintTest.java</files>
  <read_first>
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§ "Post-OAuth calendarList.list() snapshot ingest" lines 882-924 — the worked example showing primary-calendar auto-tag for 3 roles)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-06 default-on-connect — primary calendar gets ALL three roles for the ACTIVE mailbox only; other sub-calendars get is_enabled=true but no preference rows)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-08 role-tag is runtime authority; D-13 toggling is_enabled=false cascade-deletes referencing preference rows at the service layer)
    - backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java (W1 — the Calendar client returned here)
    - backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java (W1 — the file modified at the end of this task to invoke CalendarSnapshotIngestionService.ingestSnapshot AFTER_COMMIT)
    - backend/core/src/main/java/com/zeromail/core/mailbox/MailboxRef.java (the active-mailbox identifier the success handler resolves from MailboxContext — the active mailbox at OAuth-time is the one bound when the user clicked "Connect Google Calendar" on its settings page)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (`<integration_points>` block - the active-mailbox is bound by W3's settings page when the user clicks Connect; W3 will pass the mailboxId through OAuth state — for Task 2 of W2, the success handler reads it from the existing MailboxContext binding established by `MailboxBindingFilter` or from an OAuth `OAuth2AuthorizationRequest` attribute mirroring Phase 10 D-01 attributes-based intent pattern. Read `IntentCarryingAuthorizationRequestRepository` from Phase 10 D-01 for the attribute pattern.)
    - backend/api/src/main/java/com/zeromail/api/security/IntentCarryingAuthorizationRequestRepository.java (Phase 10 attribute-based intent pattern; mirror this to stamp `activeMailboxId` on the calendar OAuth authz request)
  </read_first>
  <action>
    Create `CalendarSnapshotIngestionService.java` per RESEARCH.md lines 882-924:
    - `@Service`. Constructor-inject `CalendarApiClientFactory`, `CalendarRepository`, `MailboxCalendarPreferenceRepository`.
    - `@Transactional public void ingestSnapshot(UUID tenantId, UUID calendarConnectionId, UUID activeMailboxId)` throws `IOException`.
    - Steps: (1) `Calendar calendarClient = calendarApiClientFactory.buildClientForCalendarConnection(tenantId, calendarConnectionId);` (2) `com.google.api.services.calendar.model.CalendarList result = calendarClient.calendarList().list().execute();` (3) For each item in `result.getItems()`: build a `CalendarEntity(UUID.randomUUID(), calendarConnectionId, tenantId, item.getId(), item.getSummary(), item.getDescription(), Boolean.TRUE.equals(item.getPrimary()), true /* isEnabled */, item.getTimeZone());` and `calendarRepository.save(...)`. Track the primary calendar in a local `Optional<CalendarEntity>`.
    - (4) Per D-06: if a primary calendar exists, for each `MailboxCalendarRole` value, save a `MailboxCalendarPreferenceEntity` for `(tenantId, activeMailboxId, calendarConnectionId, primaryCalendar.getId(), role)`. Three rows total. If `activeMailboxId` is `null` (no active mailbox context at OAuth time — surface as a checker error during plan-checker so this branch is impossible at runtime), do NOT enroll preferences; log `event=calendar_snapshot_no_active_mailbox_skip_default_roles tenantId={}`.
    - (5) Per the partial unique indexes from W0 changeset 133, if a user re-connects the same Google account against a second mailbox, the `EVENT_WRITE` and `BRIEF_SOURCE` rows for that second mailbox will not collide with the first mailbox's rows (the indexes are partial on `(mailbox_id) WHERE role IN ...`). But if the user re-connects the same Google account against the SAME mailbox (e.g. after a disconnect), the existing preference rows were already deleted by `CalendarConnectionService.disconnect(...)`'s cascade — fresh row inserts proceed without collision.
    - Privacy: never log `item.getSummary()` (calendar name may carry PII like "Personal Doctor Appointments").

    Handle Pitfall 8 (Workspace OU policy blocking): wrap `calendarClient.calendarList().list().execute()` in a try-catch. If `com.google.api.client.googleapis.json.GoogleJsonResponseException` with status 403, do NOT throw — log `event=calendar_snapshot_workspace_policy_blocked tenantId={} calendarConnectionId={}` and proceed without inserting any `calendars` rows. The `calendar_connections` row stays CONNECTED but the connection card will show an empty sub-calendar list, which W3's UX handles. Add a future-todo file `.planning/todos/2026-06-20-pitfall-8-workspace-policy-flag.md` noting that a `last_error` column on `calendar_connections` could surface this clearer in v1.5+.

    Create `MailboxCalendarPreferenceService.java`:
    - `@Service`. Constructor-inject `MailboxCalendarPreferenceRepository`, `CalendarRepository`, `CalendarConnectionRepository`.
    - Define a command record `UpdateMailboxCalendarPreferenceCommand(UUID mailboxId, List<UUID> freebusyCalendarIds, UUID eventWriteCalendarId /* nullable */, UUID briefSourceCalendarId /* nullable */)` in the same package.
    - `@Transactional(readOnly = true) public List<MailboxCalendarPreferenceView> findAllForMailbox(UUID tenantId, UUID mailboxId)` returning preferences enriched with role enum.
    - `@Transactional public void updateForMailbox(UUID tenantId, UpdateMailboxCalendarPreferenceCommand command)`:
      1. Validate each input calendar ID belongs to a calendar owned by the workspace (`calendarRepository.findById(...).getTenantId().equals(tenantId)`); throw `IllegalArgumentException` on any mismatch (controller maps to 404 — we never leak which calendar was rejected for which reason). Validate each calendar is `isEnabled=true` (D-13 picker constraint — the UI only lists enabled, but service-layer validation closes the race).
      2. Replace strategy (simplest correct semantics for a small N): `mailboxCalendarPreferenceRepository.deleteByMailboxIdAndTenantId(command.mailboxId(), tenantId)` (add this bulk method to the repository if not present — explicit tenant scope per project lesson) then INSERT new rows for each role: 0..N FREEBUSY rows, 0..1 EVENT_WRITE row, 0..1 BRIEF_SOURCE row. The partial unique indexes from W0 enforce the 0..1 invariant at the DB layer as a backstop; service-layer validation ensures clean error messages.
    - Per `<open_questions_from_research>` Q3 the locked schema (W0) enforces single-cardinality for EVENT_WRITE/BRIEF_SOURCE. This service trusts the DB constraint; on `DataIntegrityViolationException` it surfaces a friendly error.

    Create `CalendarToggleService.java` per D-13 + Pitfall 6:
    - `@Service`. Constructor-inject `CalendarRepository`, `MailboxCalendarPreferenceRepository`.
    - `@Transactional public void setEnabled(UUID tenantId, UUID calendarId, boolean enabled)`:
      1. Load via `calendarRepository.findById(calendarId)`; verify ownership via tenant match; throw 404 on mismatch.
      2. Update `calendar.setEnabled(enabled)` and save.
      3. If `enabled == false`, `mailboxCalendarPreferenceRepository.deleteByCalendarIdAndTenantId(calendarId, tenantId)` — service-layer cascade (DB doesn't auto-cascade from `is_enabled` toggle).
    - Return value: an `int` count of preference rows that were deleted (caller surfaces this to the UI as "Removed N role assignments").

    Edit `CalendarOAuthSuccessHandler.java` (created in W1): inject `CalendarSnapshotIngestionService calendarSnapshotIngestionService` and `ApplicationEventPublisher`. After the entity save inside the success path, capture `final UUID savedConnectionId = calendarConnection.getId();` and `final UUID activeMailboxId = resolveActiveMailboxIdFromOAuthAttributes(authenticationToken);` (the active mailbox is stamped on the OAuth `OAuth2AuthorizationRequest` attributes at the W3 `/api/calendar/connect-intent` endpoint per Phase 10 D-01 attributes-based intent pattern; for Task 2 implement the read but defer the write side to W3's intent endpoint — if no attribute present, fall back to `null` and the snapshot service skips D-06 default roles).
    Wrap the snapshot call in `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { try { calendarSnapshotIngestionService.ingestSnapshot(tenantId, savedConnectionId, activeMailboxId); } catch (IOException e) { log.error("event=calendar_snapshot_ingest_failed tenantId={} calendarConnectionId={}", tenantId, savedConnectionId, e); } } });` OR (cleaner) publish a `CalendarConnectionEstablished` internal event with the three fields and let an AFTER_COMMIT listener call `ingestSnapshot`. Pick the cleaner approach by reading the existing Gmail post-OAuth side-effect pattern — if Gmail uses listeners, mirror that. Document the choice in the SUMMARY.
    Per RESEARCH.md anti-pattern at line 655: do NOT call `calendarList.list()` synchronously inside the success handler — the AFTER_COMMIT or async invocation is mandatory so the OAuth redirect is fast.

    Create `MailboxCalendarPreferenceConstraintTest.java` extending `PostgresContainerTest` per VALIDATION.md TBD-w2-04:
    - Insert a `MailboxCalendarPreferenceEntity` with `role = MailboxCalendarRole.FREEBUSY` for mailbox A, calendar C1 — succeeds.
    - Insert a second FREEBUSY row for mailbox A, calendar C2 — succeeds (multi-select for FREEBUSY).
    - Insert an EVENT_WRITE row for mailbox A, calendar C1 — succeeds.
    - Insert a second EVENT_WRITE row for mailbox A, calendar C2 — must fail with PG constraint `uq_mailbox_event_write` (the partial unique index from W0 changeset 133). Catch `DataIntegrityViolationException` and assert the underlying SQLException has SQLSTATE 23505.
    - Insert a BRIEF_SOURCE row for mailbox A — succeeds; second BRIEF_SOURCE for same mailbox — fails with `uq_mailbox_brief_source`.
    - Insert a `role = "INVALID_ROLE_NAME"` — must fail to load via JPA's enum binding (the entity uses `@Enumerated(EnumType.STRING)` with `MailboxCalendarRole`; the column itself is `varchar(32)` but the entity rejects unknown roles at hydration via `IdentifiedEnum.fromId` per CONVENTIONS §4 — this assertion proves CAL-CONN-07 invariant).

    Create `CalendarSnapshotIngestionServiceTest.java` extending `PostgresContainerTest`. Use a `@MockitoBean CalendarApiClientFactory` returning a Mockito-built `Calendar` (Google API client) stub whose `calendarList().list().execute()` returns a fixture `CalendarList` with 1 primary + 2 secondary items.
    - Case `ingestSnapshot_insertsCalendarRowsAndDefaultPrimaryRoles` — call `ingestSnapshot(tenantId, calendarConnectionId, activeMailboxId)`; assert 3 `calendars` rows exist; assert exactly 3 `mailbox_calendar_preferences` rows exist (FREEBUSY + EVENT_WRITE + BRIEF_SOURCE) all referencing the primary calendar + activeMailboxId.
    - Case `ingestSnapshot_skipsDefaultRolesIfActiveMailboxIsNull` — call with `activeMailboxId=null`; assert 3 `calendars` rows exist but 0 preference rows.
    - Case `ingestSnapshot_handlesWorkspacePolicy403` — make the mock throw `GoogleJsonResponseException` with status 403; assert no rows inserted, no exception thrown; log line `event=calendar_snapshot_workspace_policy_blocked` captured.
    - Case `ingestSnapshot_doesNotDoubleInsertOnReconnect` — pre-seed an existing `calendars` row for the same `(calendar_connection_id, external_calendar_id)` pair; call `ingestSnapshot`; assert the `uq_calendar_connection_external_id` unique index from W0 catches the duplicate and a `DataIntegrityViolationException` is either bubbled OR caught + the service does an UPSERT (decide implementation: UPSERT is the cleaner UX — Google sends the same calendarList IDs on every connect; treat as `merge` not `INSERT`. Use `JPA repository.findByConnectionIdAndExternalCalendarId` then update-or-insert. Document the choice in the SUMMARY.)
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.calendar.usecases.CalendarSnapshotIngestionServiceTest" --tests "com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceConstraintTest"</automated>
  </verify>
  <acceptance_criteria>
    - All 6 listed files exist.
    - `grep -c 'D-06' backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionService.java` returns at least 1 (decision citation per `<context_fidelity>`).
    - `grep -c 'activeMailboxId' backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionService.java` returns at least 2.
    - `grep -c 'uq_mailbox_event_write' backend/core/src/test/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceConstraintTest.java` returns at least 1.
    - All test cases pass.
    - `CalendarOAuthSuccessHandler.java` now references `calendarSnapshotIngestionService` and registers an AFTER_COMMIT synchronization (or event listener) — verified by grep + manual review.
    - JetBrains `get_file_problems` returns no errors on all new + modified files.
  </acceptance_criteria>
  <done>OAuth round-trip → connection saved → AFTER_COMMIT → calendarList ingested → primary calendar auto-tagged with 3 roles for the active mailbox only. Per-role single-cardinality enforced at DB layer.</done>
</task>

<task type="auto">
  <name>Task 3: REST controllers + record DTOs + @WebMvcTest + OpenAPI regen prep</name>
  <files>backend/api/src/main/java/com/zeromail/api/controllers/calendar/package-info.java, backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectionController.java, backend/api/src/main/java/com/zeromail/api/controllers/calendar/MailboxCalendarPreferenceController.java, backend/api/src/main/java/com/zeromail/api/dto/calendar/package-info.java, backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarConnectionResponse.java, backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarSubResponse.java, backend/api/src/main/java/com/zeromail/api/dto/calendar/MailboxCalendarPreferenceResponse.java, backend/api/src/main/java/com/zeromail/api/dto/calendar/UpdateMailboxCalendarPreferenceRequest.java, backend/api/src/main/java/com/zeromail/api/dto/calendar/UpdateCalendarEnabledRequest.java, backend/api/src/test/java/com/zeromail/api/controllers/calendar/CalendarConnectionControllerTest.java</files>
  <read_first>
    - backend/api/src/main/java/com/zeromail/api/controllers/tenant/TenantStatusController.java (thin-controller + Response.from precedent CONVENTIONS §1)
    - backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java (record DTO with @Schema discipline CONVENTIONS §3)
    - backend/api/src/main/java/com/zeromail/api/security/MailboxBindingFilter.java (the active-mailbox binding filter — controllers under `/api/calendar/mailboxes/{mailboxId}/...` inherit the mailbox binding via ScopedValue per Phase 11 D-06)
    - backend/api/src/test/java/com/zeromail/api/controllers/tenant/TenantStatusControllerTest.java (the @WebMvcTest + mocked service shape — pick whichever existing controller test uses real session cookies via TestSessionSupport — that is the project's authoritative MVC test pattern per TESTING.md §0)
    - .planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md (§§ "CalendarConnectionController" + "CalendarConnectionResponse" lines 291-322)
    - CLAUDE.md §11 OpenAPI codegen MANDATORY — `apps/web/lib/api/schema.d.ts` is generated, never hand-edited. After this task ships, W3 regenerates it.
    - CONVENTIONS.md §3 — @Schema discipline: requiredProperties for response fields always present, allowableValues for closed string sets, NON_NULL include for variant payloads.
  </read_first>
  <action>
    Create `CalendarSubResponse.java` as a record `(String id, String externalCalendarId, String name, boolean isPrimary, boolean isEnabled, String timezone)`. Annotate with `@io.swagger.v3.oas.annotations.media.Schema(requiredProperties = {"id", "externalCalendarId", "name", "isPrimary", "isEnabled"})`. The `timezone` is nullable (IANA tz id is optional in Google response). Provide `public static CalendarSubResponse from(CalendarEntity entity) { return new CalendarSubResponse(entity.getId().toString(), entity.getExternalCalendarId(), entity.getName(), entity.isPrimary(), entity.isEnabled(), entity.getTimezone()); }`.

    Create `MailboxCalendarPreferenceResponse.java` as a record `(String id, String mailboxId, String calendarConnectionId, String calendarId, String role)`. Annotate `@Schema(requiredProperties = {"id", "mailboxId", "calendarConnectionId", "calendarId", "role"}, allowableValues = {"FREEBUSY", "EVENT_WRITE", "BRIEF_SOURCE"})` on the `role` field (use field-level `@Schema(allowableValues = ...)`. Provide `from(MailboxCalendarPreferenceEntity)` factory.

    Create `CalendarConnectionResponse.java` as a record `(String id, String googleEmail, String status, Instant connectedAt, Instant disconnectedAt, String googleProfileName, String googleProfilePictureUrl, List<CalendarSubResponse> calendars, List<MailboxCalendarPreferenceResponse> preferences)`. Annotate with `@Schema(requiredProperties = {"id", "googleEmail", "status", "connectedAt", "calendars", "preferences"})` and field-level `@Schema(allowableValues = {"CONNECTED", "DISCONNECTED", "REVOKED"})` on `status`. Add `@com.fasterxml.jackson.annotation.JsonInclude(JsonInclude.Include.NON_NULL)` at class level so `disconnectedAt` / profile fields serialize only when present. Provide `public static CalendarConnectionResponse from(CalendarConnectionView view) { /* map */ }`.

    Create `UpdateCalendarEnabledRequest.java` as a record `(boolean enabled)`. Bean Validation: no constraints needed.

    Create `UpdateMailboxCalendarPreferenceRequest.java` as a record `(@NotNull List<UUID> freebusyCalendarIds, UUID eventWriteCalendarId, UUID briefSourceCalendarId)`. The two single-select fields are nullable (user may clear them). Bean Validation: `@NotNull` on the list (empty list is valid — clears FREEBUSY).

    Create `CalendarConnectionController.java` per CONVENTIONS §1:
    - `@RestController @RequestMapping("/api/calendar")`. Constructor-inject `CalendarConnectionService calendarConnectionService` + `CalendarToggleService calendarToggleService` (NO repositories — CONVENTIONS §1).
    - `@GetMapping("/mailboxes/{mailboxId}/connections") public List<CalendarConnectionResponse> list(@PathVariable UUID mailboxId) { UUID tenantId = UUID.fromString(TenantContext.currentOrThrow()); return calendarConnectionService.list(tenantId, mailboxId).stream().map(CalendarConnectionResponse::from).toList(); }`. Annotate `@Operation(summary="List calendar connections for a mailbox")` per springdoc convention.
    - `@DeleteMapping("/connections/{calendarConnectionId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void disconnect(@PathVariable UUID calendarConnectionId)`. Resolve tenantId via TenantContext; call `calendarConnectionService.disconnect(tenantId, calendarConnectionId)`. Maps `CalendarConnectionNotOwnedException` → 404 via existing `@ExceptionHandler` chain (`api/error/` package — add a handler entry if not present for this exception type).
    - `@PatchMapping("/calendars/{calendarId}/enabled") public Map<String, Integer> toggle(@PathVariable UUID calendarId, @RequestBody UpdateCalendarEnabledRequest request)` → calls `calendarToggleService.setEnabled(tenantId, calendarId, request.enabled())` and returns `Map.of("preferencesRemoved", deletedCount)` (use the count from the service). Define a dedicated `CalendarToggleResponse` record if the project pattern forbids Map responses — check existing controllers.

    Create `MailboxCalendarPreferenceController.java`:
    - `@RestController @RequestMapping("/api/calendar/mailboxes/{mailboxId}/preferences")`. Inject `MailboxCalendarPreferenceService`.
    - `@GetMapping public List<MailboxCalendarPreferenceResponse> list(@PathVariable UUID mailboxId)`.
    - `@PatchMapping public List<MailboxCalendarPreferenceResponse> update(@PathVariable UUID mailboxId, @Valid @RequestBody UpdateMailboxCalendarPreferenceRequest request)` → builds an `UpdateMailboxCalendarPreferenceCommand`, calls service, returns the updated list.

    Privacy logging: controllers do NOT log anything beyond `event=` opaque names; the service layer already emits the cascade events. NEVER log googleEmail or refresh-token bytes.

    Wire 404/400/409 in `backend/api/src/main/java/com/zeromail/api/error/` — add `@ExceptionHandler(CalendarConnectionNotOwnedException.class)` returning 404 ProblemDetail; `@ExceptionHandler(CalendarDisconnectedException.class)` returning 409 ProblemDetail. Match existing Gmail equivalents' shape — search `backend/api/src/main/java/com/zeromail/api/error/` for `@ExceptionHandler(MailboxNotOwnedException.class)` as the analog (Phase 10).

    Create `CalendarConnectionControllerTest.java` per TESTING.md §3 row 2 (`@WebMvcTest` + `@MockitoBean` services + MockMvc):
    - `@WebMvcTest(controllers = {CalendarConnectionController.class, MailboxCalendarPreferenceController.class})`. `@MockitoBean CalendarConnectionService`; `@MockitoBean CalendarToggleService`; `@MockitoBean MailboxCalendarPreferenceService`.
    - Mint a real session cookie via the project's `TestSessionSupport.TestSessionMinter` per TESTING.md §0 (the project's authoritative MVC pattern).
    - Cases:
      - `GET /api/calendar/mailboxes/{id}/connections` — stub the service to return 1 connection view; assert 200 + JSON body shape (list with `id`, `googleEmail`, `status`, `calendars`, `preferences`).
      - `GET ... ` — stub service throws `MailboxNotOwnedException` (Phase 10 surface — the mailboxId is itself ownership-checked by `MailboxBindingFilter` upstream); assert 404 ProblemDetail.
      - `DELETE /api/calendar/connections/{id}` — assert 204 + service call.
      - `DELETE` — service throws `CalendarConnectionNotOwnedException`; assert 404 ProblemDetail.
      - `PATCH /api/calendar/calendars/{id}/enabled` body `{"enabled": false}` — assert 200 + service call with `enabled=false`.
      - `PATCH /api/calendar/mailboxes/{id}/preferences` body with FREEBUSY ids + null EVENT_WRITE — assert 200 + service called with parsed command.
    - Per TESTING.md §2 do NOT over-test — one happy + one error per endpoint is sufficient.

    After all files compile, prepare for W3 codegen by running `cd backend && ./gradlew :backend:api:generateOpenApiDocs` and committing the regenerated `apps/web/lib/api/openapi.json` (if the project's regen script writes the spec to a checked-in cache). The frontend `schema.d.ts` regen is W3 work; W2 ends with the spec on disk.
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:api:test --tests "com.zeromail.api.controllers.calendar.CalendarConnectionControllerTest"</automated>
  </verify>
  <acceptance_criteria>
    - All 10 listed files exist; controllers inject only services (no repositories — CONVENTIONS §1 enforced).
    - `grep -c 'GmailConnectionRepository\|CalendarConnectionRepository\|MailboxCalendarPreferenceRepository' backend/api/src/main/java/com/zeromail/api/controllers/calendar/*.java | grep -v ':0$' | head -1` returns empty (no controller injects any repository).
    - `grep -c '@Schema' backend/api/src/main/java/com/zeromail/api/dto/calendar/CalendarConnectionResponse.java` returns at least 2 (class + at least one field-level `@Schema`).
    - All controller test cases green.
    - `cd backend && ./gradlew :backend:api:generateOpenApiDocs` writes the OpenAPI spec without errors; the spec includes paths `/api/calendar/mailboxes/{mailboxId}/connections`, `/api/calendar/connections/{calendarConnectionId}`, `/api/calendar/calendars/{calendarId}/enabled`, `/api/calendar/mailboxes/{mailboxId}/preferences`.
    - JetBrains `get_file_problems` returns no errors on the new files.
  </acceptance_criteria>
  <done>REST surface compiles + tests green + OpenAPI spec includes the four new Calendar endpoints. W3 frontend regen is unblocked.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| HTTP request → /api/calendar/* | Session-bound; `MailboxBindingFilter` validates session→mailbox binding for `/mailboxes/{mailboxId}/` routes. |
| CalendarConnectionService → DB cascade | Multi-table sync write inside TransactionTemplate. |
| Modulith event publication → AFTER_COMMIT listeners | In-process; subscribers must not assume tx still open. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-12-01 | Information Disclosure | CalendarConnectionService.list — cross-tenant calendar leak | mitigate | All repository methods carry tenantId as explicit param; `findAllByTenantId(...)` and `findByIdAndTenantId(...)` are the only entry points. Hibernate `@TenantId` binding via `AbstractTenantOwnedEntity` is the secondary defense. Test `CalendarConnectionServiceTest.resolveOwnedConnectionOrThrow_throwsForCrossTenantAccess` proves the seam. |
| T-12-02 | Elevation of Privilege | Stale access token after disconnect | mitigate | `CalendarConnectionDisconnected` event + AFTER_COMMIT listener calls `CalendarApiClientFactory.evictAccessToken(...)`. `CalendarConnectionDisconnectedListenerTest` proves both the firing and the rollback-suppression invariants. |
| T-12-04 | Denial of Service | Workspace OU policy 403 leaving connection in stuck state | mitigate | `CalendarSnapshotIngestionService` catches `GoogleJsonResponseException(403)` and logs `event=calendar_snapshot_workspace_policy_blocked`; connection stays CONNECTED with empty sub-calendar list. W3 UX surfaces the empty-state hint. Test case explicit. |
| T-12-V4-2 | Access Control | MailboxCalendarPreferenceController bypasses MailboxBindingFilter | mitigate | Routes are `/api/calendar/mailboxes/{mailboxId}/preferences` so the existing `MailboxBindingFilter` from Phase 10 binds + validates `MailboxRef` via the path. Controller test `MailboxNotOwnedException → 404` asserts the upstream filter wires correctly. |
| T-12-V5-1 | Input Validation | Cross-tenant calendar IDs in UpdateMailboxCalendarPreferenceRequest | mitigate | `MailboxCalendarPreferenceService.updateForMailbox` validates each input `calendarId` belongs to a `Calendar` owned by the workspace before any write. Single-cardinality enforced at DB layer (partial unique indexes from W0); friendly error surfaced on `DataIntegrityViolationException`. |
| T-12-04bis | Denial of Service | `MailboxCalendarPreferenceService.updateForMailbox` race producing duplicate EVENT_WRITE | mitigate | DB partial unique index `uq_mailbox_event_write` is the race-proof backstop; service-layer pre-check is for UX only. `MailboxCalendarPreferenceConstraintTest` asserts the index fires. |
</threat_model>

<verification>
- `cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.calendar.*"` — 5 new test classes green.
- `cd backend && ./gradlew :backend:api:test --tests "com.zeromail.api.controllers.calendar.*" --tests "com.zeromail.api.security.Calendar*"` — controller + W1 success-handler tests green (verifies the W2 modifications to `CalendarOAuthSuccessHandler` did not regress W1).
- `cd backend && ./gradlew :backend:api:check :backend:core:check` — full backend `check` green (Modulith, ArchUnit, DomainPurity).
- `cd backend && ./gradlew :backend:api:generateOpenApiDocs` — spec emits 4 new paths.
- `grep -c 'GmailConnectionRepository' backend/api/src/main/java/com/zeromail/api/controllers/calendar/*.java | grep -v ':0$'` returns empty (no controller→repo direct injection).
</verification>

<success_criteria>
- `GET /api/calendar/mailboxes/{mailboxId}/connections` returns a typed response with sub-calendar list and per-mailbox preference list.
- `DELETE /api/calendar/connections/{id}` transitions status to DISCONNECTED, cascades preference rows, fires `CalendarConnectionDisconnected` AFTER_COMMIT, evicts factory cache.
- `PATCH /api/calendar/calendars/{id}/enabled false` cascade-deletes referencing preference rows (D-13).
- `PATCH /api/calendar/mailboxes/{id}/preferences` enforces single-cardinality for EVENT_WRITE/BRIEF_SOURCE via DB partial unique index + service-layer pre-check.
- `CalendarSnapshotIngestionService.ingestSnapshot` runs AFTER_COMMIT of the OAuth success path, inserts `calendars` rows, and seeds 3 primary-calendar preference rows for the active mailbox only (D-06).
- `CalendarConnectionDisconnected` event + AFTER_COMMIT listener evicts the access-token cache.
- OpenAPI spec includes the 4 new paths, ready for W3 frontend regen.
- Modulith + ArchUnit + Domain-purity + Schema-isolation tests all green.
</success_criteria>

<output>
Create `.planning/phases/12-calendar-connection-triage-foundation/12-03-SUMMARY.md` listing: (a) the chosen AFTER_COMMIT mechanism in `CalendarOAuthSuccessHandler` (TransactionSynchronization registration vs internal event), (b) the chosen upsert-on-reconnect strategy in `CalendarSnapshotIngestionService`, (c) the 4 new OpenAPI paths, (d) confirmation that `gmail_connections` row remains byte-identical after a Calendar disconnect (recheck the W1 isolation test still passes), (e) the count of preference rows existing immediately after a fresh OAuth round-trip (must be 3 — primary calendar × 3 roles × active mailbox).
</output>
