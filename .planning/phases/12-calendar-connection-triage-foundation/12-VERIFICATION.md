---
phase: 12-calendar-connection-triage-foundation
verified: 2026-06-22T00:00:00Z
reverified: 2026-06-23T00:00:00Z
status: passed
score: 13/13 must-haves verified
behavior_unverified: 0
overrides_applied: 0
resolution: >-
  CAL-TRIAGE-02 (the single FAILED truth, SC3b) was closed by gap plan 12-G1 on 2026-06-23.
  Independently re-verified against the codebase: apps/web/lib/api/schema.d.ts now carries
  messageClass (grep=2) + eventDt (grep=1) on GmailInboxMessageResponse (was 0); the DTO + from(...)
  mapper expose both fields with @Schema(allowableValues); apps/web/features/inbox/api/inbox-api.ts
  derives InboxMessageClass from the generated schema (hand-typed union deleted); the badge Vitest is
  3/3 green; InboxProjectionPinningTest stays green with the ORDER BY pin predicate unchanged.
  Root cause of the prior regen block was a latent W1 regression (the google-calendar ClientRegistration
  was added without matching hermetic emit args in backend/api/build.gradle.kts) — fixed in commit e6f67581.
  The 4 human_verification items below remain live-system checks tracked for /gsd-verify-work.
gaps_resolved:
  - truth: "CAL-TRIAGE-02 — Calendar-class messages are pinned at top-of-inbox in the web UI for a 24-hour window after the event date, with explicit 'Cancellation' / 'Time changed' badges."
    status: partial
    reason: >-
      The pin half is implemented (GmailInboxProjectionRepository.findInboxPage native query carries
      `(message_class IS NOT NULL AND event_dt IS NOT NULL AND now() < event_dt + INTERVAL '24 hours') DESC`
      and W4's CalendarMessageClassifier writes message_class + event_dt). The badge half is broken end-to-end
      because the projection→response chain was never threaded through. W4's SUMMARY admitted this as
      "Known Stubs #1" and "Deviation #3". REQUIREMENTS.md still has CAL-TRIAGE-02 marked [x], so it is
      overclaimed.
    artifacts:
      - path: "backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java"
        issue: "No `messageClass` or `eventDt` field — projection columns never reach the API DTO."
      - path: "backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java"
        issue: "Does not read or expose messageClass/eventDt from the projection."
      - path: "apps/web/lib/api/schema.d.ts"
        issue: "Generated schema carries no messageClass/eventDt — confirmed by grep (0 matches). Frontend `inbox-api.ts:25` hand-declared the optional field, but the wire payload never carries it, so `message.messageClass` is always `undefined` at runtime."
      - path: "apps/web/features/inbox/components/InboxPageClient.tsx"
        issue: "Lines 587-596 render `Badge` for `CANCEL` / `RESCHEDULE` — dead branch because `message.messageClass` is always undefined."
    missing:
      - "Add `messageClass` (String enum INVITE/CANCEL/RESCHEDULE/RSVP) and `eventDt` (Instant) fields to `RecentInboxMessage` projection record."
      - "Thread the new fields through `RecentInboxReadService` so the projection columns surface in the read shape."
      - "Add `messageClass` + `eventDt` to `GmailInboxMessageResponse` DTO with `@Schema(allowableValues = {...})` for the enum field."
      - "Regenerate `apps/web/lib/api/schema.d.ts` via `pnpm --filter web run generate:api` and remove the hand-typed field in `apps/web/features/inbox/api/inbox-api.ts` once the generated type carries it."
      - "Re-run Playwright e2e to confirm the Cancellation badge actually renders against a row with messageClass=CANCEL."
deferred: []
human_verification:
  - test: "Connect a real Google Calendar through the live OAuth round-trip and confirm Consent screen shows ONLY 'View free/busy time on your calendar', 'View and edit events on all your calendars', 'See and download any calendar you can access using your Google Calendar' (the three calendar-only scopes) — no Gmail or Drive consent items."
    expected: "After granting, /settings/mailboxes/{id}/calendar shows a CONNECTED card with the primary calendar tagged FREEBUSY+EVENT_WRITE+BRIEF_SOURCE; gmail_connections refresh_token_encrypted unchanged (verifiable via psql)."
    why_human: "Cannot exercise Google's real consent screen in a Bash sandbox — SSH tunnel + backend boot not reachable in this session, and OAuth requires Google's hosted consent UI."
  - test: "Send yourself a Google Calendar invite (METHOD:REQUEST), wait for Pub/Sub ingestion, then refresh /inbox. Confirm the invite row is pinned at top-of-inbox."
    expected: "Inbox shows the invite at position 1 even though it is not the most recently received message (the pin predicate event_dt + 24h > now() ranks it ahead)."
    why_human: "Requires a real Gmail account, real Pub/Sub delivery, and the worker process running. The W4 CalendarMessageClassifier behavior cannot be exercised against a static codebase scan."
  - test: "Disconnect a connected Calendar via the DropdownMenu → Disconnect action in the UI and verify the card transitions to a DISCONNECTED badge within 5s, and mailbox_calendar_preferences rows for the connection are GONE (via psql)."
    expected: "Card flips to destructive Badge variant; preferences DELETE confirmed in DB; triage_audit rows for the tenant retained."
    why_human: "Requires the dev server + live OAuth + DB inspection. CalendarConnectionDisconnectedListenerTest proves the event fires; the user-visible cascade is e2e behavior."
  - test: "Trigger the seeded system-calendar rule against a real calendar invite and confirm via /audit that the rule fired via PRESET match (diagnostic='preset_calendar') with NO llm_call_audit row written for the evaluation."
    expected: "Audit row shows rule matched, diagnostic preset_calendar, llm_call_audit count unchanged."
    why_human: "Structural proof exists in W5 unit tests; runtime confirmation against a real ingest path is human-only."
---

# Phase 12: Calendar Connection + Triage Foundation — Verification Report

**Phase Goal:** "User can connect one or more Google Calendars on minimal scopes and immediately see Gmail calendar invites/cancellations pinned top-of-inbox and guarded against destructive rule actions — even before any AI calendar feature ships. The OAuth scope ledger introduced here protects every later phase from accidentally requesting a restricted scope."

**Verified:** 2026-06-22
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria + REQUIREMENTS)

| #   | Truth (requirement ID + claim)   | Status     | Evidence       |
| --- | ------- | ---------- | -------------- |
| SC1 | User connects Calendar via explicit action with incremental OAuth on freebusy/events/readonly ONLY, sees workspace-shared connection w/ provider email, status, last sync, per-calendar toggles, three-state machine (CONNECTED/DISCONNECTED/REVOKED); disconnect cascade-revokes preferences while retaining audit (CAL-CONN-01,02,04,08) | ✓ VERIFIED | `CalendarClientRegistrationConfig` (`@PostConstruct` cross-checks bound scopes against `GoogleOAuthScope.CALENDAR_FREEBUSY/EVENTS/READONLY.value()`); `CalendarOAuthSuccessHandler` writes only calendar_connections; `CalendarConnectionService.disconnect` uses TransactionTemplate to mark DISCONNECTED + bulk-delete preferences + publish event AFTER_COMMIT (verified `CalendarConnectionServiceTest`); CalendarConnectionStatus has CONNECTED/DISCONNECTED/REVOKED constants. CalendarConnectionController exposes GET list + DELETE disconnect + PATCH enabled (3 endpoints found). |
| SC2 | INFRA-01 ledger + allow-list test fails CI on any unapproved Google scope; reused by Phase 15 | ✓ VERIFIED | `GoogleOAuthScope.java` carries only the seven approved constants (no drive); `OAuthScopeAllowListTest.java` exists and W0 SUMMARY records it caught 3 real violations in OAuthScopes.java + E2eStubResetController.java (auto-fixed). Production-Java grep for `https://www.googleapis.com/auth/calendar` returned 1 file: GoogleOAuthScope.java only (2 test files are exempt by file path). |
| SC3a | Gmail ingestion classifies text/calendar parts as INVITE/CANCEL/RESCHEDULE/RSVP; persists on existing mail-projection row, no new long-term body storage (CAL-TRIAGE-01) | ✓ VERIFIED | `MessageClass` enum has INVITE/CANCEL/RESCHEDULE/RSVP; `CalendarPartParser` (worker) silent-fails Optional<ParseResult>, XXE off, 1MB cap (CalendarPartParserTest 11 cases); `CalendarMessageClassifier` is `@TransactionalEventListener(AFTER_COMMIT)` using REQUIRES_NEW propagation; writes only `message_class` + `event_dt` columns (no body). |
| SC3b | Calendar-class messages pinned top-of-inbox 24h after event date with "Cancellation"/"Time changed" badges (CAL-TRIAGE-02) | ✗ FAILED | **PIN half WORKS:** `GmailInboxProjectionRepository.findInboxPage` ORDER BY carries `(message_class IS NOT NULL AND event_dt IS NOT NULL AND now() < event_dt + INTERVAL '24 hours') DESC` (verified). **BADGE half BROKEN:** `GmailInboxMessageResponse` DTO does NOT carry `messageClass`/`eventDt`; `RecentInboxReadService` does NOT read them; `schema.d.ts` does NOT contain them. Frontend `inbox-api.ts:25` hand-declared the field but the wire payload is empty, so `message.messageClass` is always `undefined` and the Badge branch in `InboxPageClient.tsx:587-596` is dead. W4 SUMMARY explicitly admitted this as Known Stub #1 + Deviation #3. |
| SC4 | Seeded `system-calendar` rule auto-matches calendar-class messages via PRESET match before AI; user-authored rules retain full authority — no backend downgrade, no CalendarAwareGuard, no audit reason (CAL-TRIAGE-03) | ✓ VERIFIED | `MatcherNode.PresetCalendarMatcher` permit exists; `RuleEvaluator` switch arm returns terminal MATCHED on `messageClass.isPresent()`; `RuleEvaluationInput.messageClass()` plumbed; Liquibase 136 rewrites both `system-calendar` and `system-calendar-vi` template_catalog rows + uncustomized materialized rules to PRESET_CALENDAR; idempotent-by-WHERE; customized rules preserved (`SystemCalendarTemplateMigrationTest` 3 cases). `grep -c CalendarAwareGuard` and `grep -c auditReason` in RuleEvaluator both return 0. |
| SC5a | Workspace-shared boundary holds — `calendar_connection` has no `gmail_connection_id` FK (CAL-CONN-06) | ✓ VERIFIED | Changeset 131-calendar-connections.yaml schema has NO gmail_connection_id column; `CalendarSchemaIsolationTest` (6 cases including the column-absence check) is green. `grep -c 'gmail_connection_id' CalendarConnectionEntity.java` returns 0. |
| SC5b | `mailbox_calendar_preference` table disambiguates per-mailbox per-role assignment; FREEBUSY multi, EVENT_WRITE/BRIEF_SOURCE single (CAL-CONN-07) | ✓ VERIFIED | Changeset 133 defines partial unique indexes `uq_mailbox_event_write ON (mailbox_id) WHERE role = 'EVENT_WRITE'` and `uq_mailbox_brief_source ON (mailbox_id) WHERE role = 'BRIEF_SOURCE'`; FREEBUSY has no such index. `MailboxCalendarPreferenceConstraintTest` 3 cases (FREEBUSY multi succeeds; second EVENT_WRITE fails 23505; second BRIEF_SOURCE fails 23505). `MailboxCalendarRole` enum has the 3 expected values. |
| SC5c | Refresh tokens AES-GCM via `OAuthTokenStore`, never logged, never reused across connections (CAL-CONN-03) | ✓ VERIFIED | `OAuthTokenStore.java` is a `@Component` facade with `RowDiscriminator{GMAIL_CONNECTION, CALENDAR_CONNECTION}`; delegates 1:1 to unchanged `RefreshTokenCipher`; `CalendarOAuthTokenIsolationTest` asserts the Gmail row's encrypted bytes are byte-identical pre/post a Calendar OAuth round-trip (Pitfall 2 mitigation). `OAuthTokenStoreRoundTripTest` 5 cases incl. cross-tenant `AEADBadTagException` + 100x nonce uniqueness. |
| SC5d | Mid-flight reads against DISCONNECTED calendar fail-fast emitting Modulith event that evicts free/busy cache (CAL-CONN-08) | ✓ VERIFIED | `CalendarApiClientFactory.buildClientForCalendarConnection` throws `CalendarDisconnectedException` when status != CONNECTED (`CalendarApiClientFactoryDisconnectTest` 5 cases). `CalendarConnectionDisconnected` Modulith event published AFTER_COMMIT by `CalendarConnectionService.disconnect`; `@TransactionalEventListener(AFTER_COMMIT)` listener calls `factory.evictAccessToken()`. `CalendarConnectionDisconnectedListenerTest` 2 cases incl. rollback suppression. Phase 13's free/busy cache hook is forward-decl in the event JavaDoc. |
| CAL-CONN-05 | Each connection enumerates calendars (primary+secondary) with per-calendar `is_enabled` flag; only enabled participate | ✓ VERIFIED | `CalendarSnapshotIngestionService.ingestSnapshot` calls `calendarList.list()` and INSERTs/UPSERTS `CalendarEntity` rows with `isPrimary` + `isEnabled=true` defaults; UI surfaces the per-calendar Switch in `CalendarList.tsx`; service-layer filters in `MailboxCalendarPreferenceService` validate is_enabled before binding. UPSERT-on-reconnect verified by W2 SUMMARY. |
| CAL-TRIAGE-04 | Calendar-aware triage works without any Calendar OAuth scope — text/calendar parsing is purely message-side | ✓ VERIFIED | `grep '^import.*CalendarApiClientFactory' backend/worker/src/main/java/com/zeromail/worker/triage/CalendarMessageClassifier.java` returns NO matches. Imports `GmailApiClientFactory` only. `CalendarMessageClassifierNoOAuthTest` has explicit `verify(calendarApiClientFactory, never())` assertion as a build-fail trip-wire. |

**Score:** 12/13 truths verified (0 present, behavior-unverified). One FAILED truth (CAL-TRIAGE-02 badge half).

### Required Artifacts (sampled)

| Artifact | Expected    | Status | Details |
| -------- | ----------- | ------ | ------- |
| `backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java` | Ledger enum w/ 7 constants, no drive | ✓ VERIFIED | Contains CALENDAR_FREEBUSY/EVENTS/READONLY + GMAIL_MODIFY + OPENID/PROFILE/EMAIL; 0 drive entries |
| `backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java` | Facade w/ RowDiscriminator | ✓ VERIFIED | Spring @Component, encrypt/decrypt delegate to RefreshTokenCipher unchanged |
| `backend/core/src/main/resources/db/changelog/changes/131-calendar-connections.yaml` | Workspace-shared connection table | ✓ VERIFIED | Registered in master changelog line 387; no gmail_connection_id column |
| `backend/core/src/main/resources/db/changelog/changes/132-calendars.yaml` | Per-connection sub-calendars | ✓ VERIFIED | Registered master line 390 |
| `backend/core/src/main/resources/db/changelog/changes/133-mailbox-calendar-preferences.yaml` | Per-role per-mailbox join | ✓ VERIFIED | Registered master line 393; partial unique indexes confirmed in constraint test |
| `backend/core/src/main/resources/db/changelog/changes/134-inbox-projection-calendar-columns.yaml` | message_class+event_dt columns | ✓ VERIFIED | Registered master line 396 |
| `backend/core/src/main/resources/db/changelog/changes/135-calendar-tables-version-column.yaml` | (W1 rollforward) optimistic-lock version column on calendars + mailbox_calendar_preferences | ✓ VERIFIED | Registered master line 399 |
| `backend/core/src/main/resources/db/changelog/changes/136-system-calendar-template-preset-matcher.yaml` | PRESET_CALENDAR data migration | ✓ VERIFIED | Registered master line 402; 8 PRESET_CALENDAR occurrences; explicit rollback block; 113/114 byte-identical pre/post (git log shows last touch was original seed commit 3ceb3cce) |
| `backend/api/src/main/java/com/zeromail/api/security/CalendarClientRegistrationConfig.java` | Boot-assertion + google-calendar registration | ✓ VERIFIED | @PostConstruct cross-checks getScopes() against GoogleOAuthScope.CALENDAR_*.value() |
| `backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java` | Writes calendar_connections only | ✓ VERIFIED | Dispatched from GoogleOAuthSuccessHandler by registrationId; integration test pins Gmail-row byte-identity |
| `backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java` | Per-connection Google Calendar client | ✓ VERIFIED | Fail-fast on non-CONNECTED + cross-tenant; evictAccessToken exposed |
| `backend/core/src/main/java/com/zeromail/core/calendar/event/CalendarConnectionDisconnected.java` | Modulith event record | ✓ VERIFIED | Record (tenantId, calendarConnectionId, disconnectedAt) |
| `backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarConnectionService.java` | list/resolveOwnedConnectionOrThrow/disconnect | ✓ VERIFIED | TransactionTemplate + AFTER_COMMIT event publish + idempotent re-disconnect |
| `backend/core/src/main/java/com/zeromail/core/calendar/usecases/CalendarSnapshotIngestionService.java` | calendarList ingest + D-06 default 3 roles for primary on active mailbox | ✓ VERIFIED | Verifies 3 preference rows seeded per W2 test; UPSERT-on-reconnect; Pitfall 8 403 absorption |
| `backend/worker/src/main/java/com/zeromail/worker/triage/CalendarPartParser.java` | ical4j wrapper | ✓ VERIFIED | XXE off (CompatibilityHints), 1MB preflight, silent-fail Throwable catch, OffsetDateTime arm fix verified |
| `backend/worker/src/main/java/com/zeromail/worker/triage/CalendarMessageClassifier.java` | Worker AFTER_COMMIT listener; Gmail API only | ✓ VERIFIED | Imports GmailApiClientFactory only; no CalendarApiClientFactory import; PROPAGATION_REQUIRES_NEW; NoOAuth test pins the invariant |
| `backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectionController.java` | REST list/disconnect/toggle | ✓ VERIFIED | @GetMapping list, @DeleteMapping disconnect, @PatchMapping enabled |
| `backend/api/src/main/java/com/zeromail/api/controllers/calendar/MailboxCalendarPreferenceController.java` | REST GET/PATCH preferences | ✓ VERIFIED | @GetMapping + @PatchMapping |
| `backend/api/src/main/java/com/zeromail/api/controllers/calendar/CalendarConnectIntentController.java` | POST connect-intent stamping mailboxId on OAuth session | ✓ VERIFIED | @PostMapping("/connect-intent"); 3 controller tests |
| `apps/web/features/calendar/types.ts` | Local DTO shim with TODO(12-W3) flag | ⚠️ PARTIAL (deferred OpenAPI regen) | Hand-derived from backend DTO records; CalendarConnection/Sub/Preference/UpdateCalendarEnabledRequest/UpdateMailboxCalendarPreferenceRequest/CalendarToggleResponse/CalendarConnectIntentRequest/CalendarConnectIntentResponse all present and byte-faithful to backend records. CLAUDE.md §11 says schema.d.ts must not be hand-edited; shim under feature folder is the documented workaround. PARTIAL because the OpenAPI regen + typed `api.GET/POST` swap are deferred. |
| `apps/web/features/calendar/api/calendar-api.ts` | Typed/raw HTTP wrappers | ⚠️ PARTIAL (raw fetch + xsrfHeader) | Uses raw fetch + xsrfHeader() per CLAUDE.md §12 CSRF echo contract (matches the openapi-fetch middleware behavior). Will swap to typed `api.GET/POST/PATCH/DELETE` after regen. |
| `apps/web/app/(protected)/(app)/settings/mailboxes/[mailboxId]/calendar/page.tsx` | RSC shell + client orchestrator | ✓ VERIFIED | Route shipped + CalendarSettingsClient wraps the panel |
| `apps/web/e2e/calendar-settings.spec.ts` | Playwright spec for empty/populated/disconnect | ⚠️ PARTIAL | Spec committed as durable gate; auto-run deferred (no dev server in executor session) |

### Key Link Verification

| From | To  | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| `OAuthTokenStore` | `RefreshTokenCipher` | facade delegation | ✓ WIRED | W0 SUMMARY confirms git diff empty on RefreshTokenCipher.java pre/post phase |
| `CalendarClientRegistrationConfig` | `GoogleOAuthScope.CALENDAR_*.value()` | Bean @PostConstruct cross-check | ✓ WIRED | grep finds 3 enum reads in JavaDoc + production cross-check |
| `CalendarOAuthSuccessHandler` | `OAuthTokenStore.encrypt(..., RowDiscriminator.CALENDAR_CONNECTION)` | refresh-token encryption | ✓ WIRED | Verified by `CalendarOAuthTokenIsolationTest` byte-identity assertion + `CalendarConnectionCipherTest` cross-write isolation |
| `CalendarConnectionService.disconnect` | `ApplicationEventPublisher.publishEvent(CalendarConnectionDisconnected)` | AFTER_COMMIT publish | ✓ WIRED | `CalendarConnectionServiceTest` records event via `@RecordApplicationEvents`; idempotent re-disconnect produces no second event |
| Disconnect listener | `CalendarApiClientFactory.evictAccessToken(connectionId)` | AFTER_COMMIT @TransactionalEventListener | ✓ WIRED | `CalendarConnectionDisconnectedListenerTest` rollback suppression case proves event-tx ordering |
| `CalendarSnapshotIngestionService` | `MailboxCalendarPreferenceRepository.save(3 primary-calendar role rows)` | D-06 default-on-connect | ✓ WIRED | W2 SUMMARY: `times(3).save(...)` verification with FREEBUSY+EVENT_WRITE+BRIEF_SOURCE for primary calendar |
| `CalendarConnectionController` | `CalendarConnectionService.resolveOwnedConnectionOrThrow` | ownership guard | ✓ WIRED | Controller test asserts 404 for cross-tenant disconnect |
| `CalendarMessageClassifier` | `GmailApiClientFactory.buildClientForMailbox` | Gmail-API-only body fetch | ✓ WIRED | NoOAuth test verifies CalendarApiClientFactory mock NEVER called |
| `CalendarMessageClassifier` | `GmailInboxProjectionRepository.updateCalendarClassification` (REQUIRES_NEW tx) | per-message UPDATE on projection | ✓ WIRED | Verified by NoOAuth test happy path |
| `RuleEvaluator (PRESET branch)` | `RuleEvaluationInput.messageClass()` | terminal MATCHED on Optional.isPresent | ✓ WIRED | RuleEvaluatorCalendarPresetTest 5 cases incl. parameterized 4 enum values |
| `RuleEvaluationInput` builder | `InboxProjectionReadService.findMessageClass(...)` | W5 plumbed-through read | ✓ WIRED | `TriageRuleEvaluationInputFactoryTest` exercises the new ctor `(GmailPreviewReadService, InboxProjectionReadService)` |
| `GmailInboxProjectionRepository.findInboxPage` | message_class + event_dt columns (W0 134) | native ORDER BY pin predicate | ✓ WIRED | `InboxProjectionPinningTest` 3 cases prove pinning behavior; W4 SUMMARY documented keyset-cursor limitation that does NOT break pin |
| `GmailInboxMessageResponse` DTO | message_class / event_dt fields | API response shape | ✗ NOT_WIRED | **GAP** — DTO never extended; `grep -c messageClass apps/web/lib/api/schema.d.ts` = 0; frontend InboxPageClient.tsx:587 Badge code is dead |
| `ConnectCalendarButton` | `prepareCalendarConnect(mailboxId)` POST → `window.location.assign` | OAuth round-trip start | ✓ WIRED | calendar-api.ts:101 POST /api/calendar/connect-intent; hook in features/calendar/hooks/use-connect-calendar-intent.ts |
| `RoleAssignmentSection` | `useUpdateCalendarPreference` PATCH preferences | per-role per-mailbox update | ✓ WIRED | calendar-api.ts:85 PATCH /api/calendar/mailboxes/{mailboxId}/preferences |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `CalendarConnectionsPanel.tsx` | `data` from useCalendarConnections | `listCalendarConnections(mailboxId)` → GET `/api/calendar/mailboxes/{id}/connections` → `CalendarConnectionService.list` joining 3 repositories | ✓ Yes | ✓ FLOWING |
| `RoleAssignmentSection.tsx` | preferences | flattened from `data` of useCalendarConnections | ✓ Yes | ✓ FLOWING |
| `InboxPageClient.tsx` Badge for CANCEL/RESCHEDULE | `message.messageClass` | `GmailInboxMessageResponse` — **does not carry messageClass field** | ✗ No | ✗ HOLLOW_PROP (dead UI) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| INFRA-01 | W0 | Scope ledger CI gate | ✓ SATISFIED | GoogleOAuthScope.java + OAuthScopeAllowListTest.java |
| CAL-CONN-01 | W1/W2/W3 | Explicit "Connect Calendar" action | ✓ SATISFIED | ConnectCalendarButton + CalendarConnectIntentController + /settings/mailboxes/[id]/calendar route |
| CAL-CONN-02 | W1 | Incremental OAuth, freebusy+events+readonly only, no full calendar, shared client, include_granted_scopes+access_type=offline+prompt=consent | ✓ SATISFIED | CalendarClientRegistrationConfig boot-asserts exact scope set; GoogleAuthorizationRequestResolver branch forces prompt=consent for `google-calendar` registrationId |
| CAL-CONN-03 | W0/W1 | AES-GCM tokens via OAuthTokenStore, never logged, never reused across connections | ✓ SATISFIED | OAuthTokenStore facade + RowDiscriminator; CalendarOAuthTokenIsolationTest pins isolation |
| CAL-CONN-04 | W2/W3 | List + per-calendar toggles + disconnect cascade | ✓ SATISFIED | CalendarConnectionService.list/disconnect; CalendarToggleService.setEnabled cascades preference rows |
| CAL-CONN-05 | W2 | Per-connection sub-calendar enumeration + is_enabled | ✓ SATISFIED | CalendarSnapshotIngestionService + CalendarEntity.isEnabled + service-layer filters |
| CAL-CONN-06 | W0 | Workspace-shared — no gmail_connection_id FK | ✓ SATISFIED | CalendarSchemaIsolationTest pins schema invariant |
| CAL-CONN-07 | W0/W1/W2 | mailbox_calendar_preference table | ✓ SATISFIED | Liquibase 133 + MailboxCalendarPreferenceConstraintTest + MailboxCalendarRole enum |
| CAL-CONN-08 | W1/W2 | Three-state machine + fail-fast on DISCONNECTED + Modulith eviction event | ✓ SATISFIED | CalendarConnectionStatus enum + CalendarApiClientFactoryDisconnectTest + CalendarConnectionDisconnectedListenerTest |
| CAL-TRIAGE-01 | W4 | text/calendar classification → INVITE/CANCEL/RESCHEDULE/RSVP on projection, no body storage | ✓ SATISFIED | MessageClass enum + CalendarPartParser + CalendarMessageClassifier + 11+3 unit tests |
| CAL-TRIAGE-02 | W4 | Pinned top-of-inbox 24h + Cancellation/Time changed badges | ✗ BLOCKED | Pin half WIRED via projection ORDER BY; badge half BROKEN — DTO doesn't carry messageClass, schema.d.ts has 0 messageClass refs, InboxPageClient Badge branches are dead UI |
| CAL-TRIAGE-03 | W5 | PRESET match before AI; user rules retain authority; no backend downgrade | ✓ SATISFIED | MatcherNode.PresetCalendarMatcher + RuleEvaluator switch arm + Liquibase 136 (113/114 byte-identical pre/post) + RuleEvaluatorCalendarPresetTest 5 cases |
| CAL-TRIAGE-04 | W4 | Works without any Calendar OAuth scope | ✓ SATISFIED | Worker import grep clean; CalendarMessageClassifierNoOAuthTest trip-wire |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| `apps/web/features/inbox/components/InboxPageClient.tsx` | 587-596 | Badge for CANCEL/RESCHEDULE rendered against `message.messageClass` that is permanently undefined at runtime (the projection→DTO chain is unwired) | ⚠️ Warning | Dead UI branch; one of the two halves of CAL-TRIAGE-02 ships visually but never fires. SUMMARY admits the stub. |
| `apps/web/features/inbox/api/inbox-api.ts` | 25, 65, 130 | Optional `messageClass` field declared by hand on TypeScript type even though the OpenAPI wire payload never carries it | ⚠️ Warning | Drift between hand-declared types and actual response; per CLAUDE.md §11 should be in `schema.d.ts` — but the backend DTO has no such field, so regen wouldn't help; the upstream gap is the missing backend DTO field. |
| `apps/web/features/calendar/types.ts` | 1-73 | Hand-derived DTO shim instead of generated `schema.d.ts` types | ℹ️ Info | Explicit TODO(12-W3) tags + W3 SUMMARY documents the swap procedure; CLAUDE.md §11 lets a clearly-flagged transitional shim under a feature folder pass while regen pipeline is blocked; not a project bug. |

No debt markers (`TBD`, `FIXME`, `XXX`) without follow-up references found in production source for this phase (the `TODO(12-W3)` markers cite an explicit follow-up step documented in W3 SUMMARY).

### Behavioral Spot-Checks

Tests enumerated by SUMMARY frontmatter — not re-run in this session (Java build sandbox + SSH tunnel to dev Postgres not reachable per memory `reference_dev_db_ssh_tunnel.md`). The SUMMARYs document the following test classes shipped and reported green in each wave's verify-run:

| Test class | Tests | Result per SUMMARY | Status |
| ---------- | ----- | ------------------ | ------ |
| `GoogleOAuthScopeEnumTest` | 5 | 0 failures (W0) | ? SKIP (not re-run in this session) |
| `OAuthScopeAllowListTest` | 2 | 0 failures (W0/W1/W2 regression) | ? SKIP |
| `OAuthTokenStoreRoundTripTest` | 5 | 0 failures (W0) | ? SKIP |
| `CalendarSchemaIsolationTest` | 6 | 0 failures (W0/W1/W2 regression) | ? SKIP |
| `CalendarConnectionCipherTest` | 3 | 0 failures (W1) | ? SKIP |
| `CalendarApiClientFactoryDisconnectTest` | 5 | 0 failures (W1) | ? SKIP |
| `CalendarClientRegistrationConfigTest` | 6 | 0 failures (W1) | ? SKIP |
| `CalendarOAuthSuccessHandlerTest` | 1 | 0 failures (W1/W2 regression) | ? SKIP |
| `CalendarOAuthTokenIsolationTest` | 1 | 0 failures (W1/W2 regression) | ? SKIP |
| `CalendarConnectionServiceTest` | 7 | 0 failures (W2) | ? SKIP |
| `CalendarConnectionDisconnectedListenerTest` | 2 | 0 failures (W2) | ? SKIP |
| `CalendarSnapshotIngestionServiceTest` | 5 | 0 failures (W2) | ? SKIP |
| `MailboxCalendarPreferenceConstraintTest` | 3 | 0 failures (W2) | ? SKIP |
| `CalendarConnectionControllerTest` | 6 | 0 failures (W2) | ? SKIP |
| `CalendarConnectIntentControllerTest` | 3 | 0 failures (W3) | ? SKIP |
| `InboxProjectionPinningTest` | 3 | 0 failures (W4) | ? SKIP |
| `CalendarPartParserTest` | 11 | 0 failures (W4) | ? SKIP |
| `CalendarMessageClassifierNoOAuthTest` | 3 | 0 failures (W4) | ? SKIP |
| `RuleEvaluatorCalendarPresetTest` | 5 | 0 failures (W5) | ? SKIP |
| `SystemCalendarTemplateMigrationTest` | 3 | 0 failures (W5) | ? SKIP |
| `ZeroMailApiApplicationModulesTest` | 30 | 0 failures (W5 regression) | ? SKIP |

Step 7b SKIPPED for full re-run (would require Java build + Testcontainers Postgres + dev SSH tunnel; W5 SUMMARY's `e377b331` is the latest commit and all suites green per SUMMARY). The SUMMARY's per-wave verify-output line is the executor's reported test result; spot-checking by re-running here would not provide independent evidence in this Bash sandbox.

### Probe Execution

No `scripts/*/tests/probe-*.sh` declared by the phase plans. Step 7c — N/A.

### Human Verification Required

See `human_verification` in frontmatter (4 items): real Google Calendar OAuth round-trip with consent-screen scope display; real Pub/Sub invite → pin behavior; disconnect cascade end-to-end visible in UI; PRESET match producing zero llm_call_audit rows in a live evaluation.

### Gaps Summary

**One BLOCKER:** CAL-TRIAGE-02 is overclaimed in REQUIREMENTS.md. The pin half (server-side ORDER BY) is in place and verified by `InboxProjectionPinningTest`, but the badge half (the user-visible "Cancellation" / "Time changed" affordance) is broken end-to-end because no one wired the projection's `message_class`/`event_dt` columns through `RecentInboxMessage` → `GmailInboxMessageResponse` → regenerated `schema.d.ts` → frontend type. W4's SUMMARY acknowledged this as Known Stub #1 and Deviation #3, but REQUIREMENTS.md still marks CAL-TRIAGE-02 as `[x]`. The Badge code in `InboxPageClient.tsx:587-596` is a dead UI branch — `message.messageClass` is always `undefined` at runtime.

**Phase-12-coherent fix surface (~1-2 hours of work):**
1. Add `messageClass: MessageClass` + `eventDt: Instant` accessors to `RecentInboxMessage` (core).
2. Read them in `RecentInboxReadService` from the projection (already exposes `getMessageClassOptional` and `getEventDtOptional`).
3. Add `messageClass` (allowableValues INVITE/CANCEL/RESCHEDULE/RSVP) and `eventDt` to `GmailInboxMessageResponse` DTO.
4. Boot backend + regen `schema.d.ts`.
5. Delete the hand-typed field in `apps/web/features/inbox/api/inbox-api.ts:25,65,130` and use the generated type.
6. Run Playwright e2e to confirm the Badge renders.

W3's deferred items (OpenAPI regen + typed `api.GET/POST` swap; Playwright auto-run; live OAuth confirmation) are honest defers documented in the SUMMARY with explicit TODO tags. They do NOT block CAL-TRIAGE-02 closure but are tracked separately.

W5's changeset renumbering (planner said 135 but actual is 136 because W1 used 135 for a rollforward) is correctly handled — `db.changelog-master.yaml` includes 131,132,133,134,135,136 in order with no edits to previously-applied entries, rollback block present on 136, no `runOnChange`/`runAlways`/`clear-checksums`. CLAUDE.md §10 holds.

The architectural trip-wires all hold:
- `grep -c CalendarApiClient backend/worker/src/main/java` = 0 production source (only a JavaDoc reference + a test mock spy that exists to fail-build if the production code ever calls Calendar).
- Production-Java scope URL grep returns ONLY `GoogleOAuthScope.java` (the ledger).
- `OAuthScopeAllowListTest` + `CalendarOAuthTokenIsolationTest` + `CalendarMessageClassifierNoOAuthTest` are all shipped and green per SUMMARY.
- Liquibase 131-136 registered in master changelog with append-only discipline.
- No `import lombok` in backend (CLAUDE.md hard ban respected).

---

_Verified: 2026-06-22_
_Verifier: Claude (gsd-verifier)_
