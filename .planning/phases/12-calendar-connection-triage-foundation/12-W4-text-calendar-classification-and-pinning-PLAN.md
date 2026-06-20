---
phase: 12-calendar-connection-triage-foundation
plan: 05
type: execute
wave: 4
depends_on:
  - 12-01
files_modified:
  - backend/core/src/main/java/com/zeromail/core/inbox/domain/MessageClass.java
  - backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionEntity.java
  - backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionRepository.java
  - backend/worker/src/main/java/com/zeromail/worker/triage/CalendarPartParser.java
  - backend/worker/src/main/java/com/zeromail/worker/triage/CalendarMessageClassifier.java
  - backend/worker/src/main/java/com/zeromail/worker/triage/package-info.java
  - backend/worker/src/test/resources/ical/invite-request.ics
  - backend/worker/src/test/resources/ical/cancel.ics
  - backend/worker/src/test/resources/ical/reply.ics
  - backend/worker/src/test/resources/ical/folded-lines.ics
  - backend/worker/src/test/resources/ical/billion-laughs.ics
  - backend/worker/src/test/java/com/zeromail/worker/triage/CalendarPartParserTest.java
  - backend/worker/src/test/java/com/zeromail/worker/triage/CalendarMessageClassifierNoOAuthTest.java
  - backend/core/src/test/java/com/zeromail/core/inbox/persistence/InboxProjectionPinningTest.java
  - apps/web/features/inbox/components/MessageRow.tsx
  - apps/web/features/calendar/messages.ts
autonomous: true
requirements:
  - CAL-TRIAGE-01
  - CAL-TRIAGE-02
  - CAL-TRIAGE-04
must_haves:
  truths:
    - "Gmail messages carrying a text/calendar MIME part with METHOD:REQUEST are persisted with message_class=INVITE; METHOD:CANCEL → CANCEL; METHOD:REPLY → RSVP"
    - "Per <open_questions_from_research> Q2, all METHOD:REQUEST messages classify as INVITE in v1; RESCHEDULE detection is deferred to a follow-up phase"
    - "Inbox page query orders calendar-class messages (message_class IS NOT NULL AND now() < event_dt + 24h) ahead of other messages, then by received_at DESC, gmail_message_id DESC"
    - "CalendarMessageClassifier runs only on text/calendar parts already in the Gmail message body fetched by the existing ingestion path; it NEVER calls CalendarApiClientFactory (CAL-TRIAGE-04)"
    - "ical4j parse is XXE-disabled, size-bounded at 1 MB, and silently no-ops on any parse exception (privacy: never logs icsBody)"
    - "Inbox UI renders a Cancellation badge next to message_class=CANCEL rows and a Time changed badge next to message_class=RESCHEDULE rows (CAL-TRIAGE-02)"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/inbox/domain/MessageClass.java"
      provides: "IdentifiedEnum INVITE/CANCEL/RESCHEDULE/RSVP per D-11"
      contains: "INVITE"
    - path: "backend/worker/src/main/java/com/zeromail/worker/triage/CalendarPartParser.java"
      provides: "ical4j wrapper extracting METHOD + first VEVENT DTSTART; XXE-off; 1 MB size bound; silent-fail Optional<ParseResult>"
    - path: "backend/worker/src/main/java/com/zeromail/worker/triage/CalendarMessageClassifier.java"
      provides: "@TransactionalEventListener(AFTER_COMMIT) on MailMessageObserved; classifies + UPDATEs gmail_inbox_projection.message_class + event_dt"
    - path: "backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionRepository.java"
      provides: "Modified findInboxPage native query with pin-aware ORDER BY (D-12)"
  key_links:
    - from: "CalendarMessageClassifier"
      to: "MailMessageObserved AFTER_COMMIT listener"
      via: "Spring @TransactionalEventListener; classifier runs in backend/worker process, NOT in core's @ApplicationModuleListener — per CONVENTIONS §6 the cross-process boundary uses plain @TransactionalEventListener"
      pattern: "@TransactionalEventListener"
    - from: "GmailInboxProjectionRepository.findInboxPage"
      to: "message_class + event_dt columns from W0 changeset 134"
      via: "native SQL ORDER BY (message_class IS NOT NULL AND now() < event_dt + INTERVAL '24 hours') DESC, received_at DESC, gmail_message_id DESC"
      pattern: "INTERVAL '24 hours'"
---

<objective>
Land calendar-aware triage entirely message-side — no Calendar OAuth scope, no CalendarApiClientFactory invocation. Three pieces:

1. **MessageClass IdentifiedEnum** (INVITE / CANCEL / RESCHEDULE / RSVP per D-11). Per `<open_questions_from_research>` Q2, ship INVITE-only for all `METHOD:REQUEST` in v1.4; RESCHEDULE detection is deferred (the column + enum value remain so a follow-up phase can populate without schema migration).

2. **CalendarPartParser** wrapping ical4j 4.2.4 (from W0 catalog). XXE disabled, size-bound 1 MB. Extracts `METHOD` property + first `VEVENT`'s `DTSTART`. Maps METHOD per RFC 5546: `REQUEST → INVITE`, `CANCEL → CANCEL`, `REPLY → RSVP`. Silent-fail on any parse exception (Privacy: NEVER log icsBody bytes).

3. **CalendarMessageClassifier** as a `@TransactionalEventListener(AFTER_COMMIT)` on `MailMessageObserved` inside `backend/worker`. Loads the message body from the projection (already fetched by the v1.3 ingestion path), detects calendar content via the Inbox Zero triple-check (`.ics` attachment OR `mimeType=text/calendar` OR `BEGIN:VCALENDAR` body substring), invokes the parser, and UPDATEs the existing `gmail_inbox_projection` row's `message_class` + `event_dt` columns via `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` (per Pitfall 5).

4. **Inbox projection pinning** — modify `GmailInboxProjectionRepository.findInboxPage` native query ORDER BY per D-12 + RESEARCH.md Pattern 3. Add the derived `(message_class IS NOT NULL AND now() < event_dt + INTERVAL '24 hours') DESC` clause before the existing `received_at DESC, gmail_message_id DESC` keyset cursor. Keyset cursor stays valid because the pin predicate uses a single `now()` invocation per query (monotonic within the page request).

5. **Inbox UI badge** — extend the existing inbox `MessageRow` component to render a raw shadcn `Badge variant="outline"` next to messages with `messageClass='CANCEL'` (label "Cancellation" / VN "Hủy") or `messageClass='RESCHEDULE'` (label "Time changed" / VN "Đổi giờ"). INVITE and RSVP get no badge — they sit pinned but without a callout (D-12 acceptance).

Purpose: Calendar-aware triage works without ANY Calendar OAuth — every Zero Mail user sees pinned invites regardless of whether they connect their Google Calendar.
Output: 5 .ics test fixtures + 2 worker classes + 1 enum + 2 entity/repository edits + 1 frontend component edit + 3 tests + i18n keys.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md
@.planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md
@.planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md
@.planning/phases/12-calendar-connection-triage-foundation/12-01-SUMMARY.md
</context>

<artifacts_this_phase_produces>
- `com.zeromail.core.inbox.domain.MessageClass` IdentifiedEnum (INVITE / CANCEL / RESCHEDULE / RSVP).
- `GmailInboxProjectionEntity` two new fields: `private String messageClass` (column `message_class`), `private Instant eventDt` (column `event_dt`). Getter `MessageClass getMessageClass()` calls `MessageClass.fromId(messageClass)` (returns null when column is null — adjust `fromId` to be nullable-safe at this seam, OR add an Optional-returning getter `Optional<MessageClass> getMessageClassOptional()`).
- `GmailInboxProjectionRepository.findInboxPage` native-query ORDER BY change.
- `CalendarPartParser` (worker utility; pure function shape per RESEARCH.md Pattern 4).
- `CalendarMessageClassifier` (worker `@TransactionalEventListener(AFTER_COMMIT)` listener).
- `apps/web/features/inbox/components/MessageRow.tsx` extension — Cancellation / Time changed `Badge` next to applicable rows.
- 5 `.ics` test fixtures derived from Inbox Zero's test cases per VALIDATION.md Wave 0 row.
- `CalendarPartParserTest` unit test.
- `CalendarMessageClassifierNoOAuthTest` `@SpringBootTest` slice with `@MockitoBean CalendarApiClientFactory` verifying the mock is never invoked (CAL-TRIAGE-04 invariant).
- `InboxProjectionPinningTest` `@DataJpaTest` with keyset cursor sanity (verifies pin predicate doesn't break pagination).

NOT in this plan:
- PRESET_CALENDAR rule matcher + RuleEvaluator branch + template migration — W5.
- Any change to default-rules-seed.yaml — W5.
- Frontend `/inbox` route layout changes beyond MessageRow extension — out of scope.
</artifacts_this_phase_produces>

<tasks>

<task type="auto">
  <name>Task 1: MessageClass enum + GmailInboxProjectionEntity columns + GmailInboxProjectionRepository ORDER BY + InboxProjectionPinningTest</name>
  <files>backend/core/src/main/java/com/zeromail/core/inbox/domain/MessageClass.java, backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionEntity.java, backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionRepository.java, backend/core/src/test/java/com/zeromail/core/inbox/persistence/InboxProjectionPinningTest.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionEntity.java (full file — the existing column shape + the inboxState getter pattern that MessageClass mirrors at lines 70-71, 186-188 per PATTERNS.md line 329-336)
    - backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionRepository.java (full file — the existing findInboxPage native query at lines 102-127 per PATTERNS.md line 345-358)
    - backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java (interface)
    - backend/core/src/main/java/com/zeromail/core/onboarding/domain/OnboardingStep.java (fromId fail-loud convention)
    - backend/core/src/main/resources/db/changelog/changes/134-inbox-projection-calendar-columns.yaml (W0 — the columns the entity must map)
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§Pattern 3 lines 497-543 — pin-aware ORDER BY worked example + keyset cursor reasoning)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-11 column types + D-12 derived pin)
    - CONVENTIONS.md §4 (IdentifiedEnum fromId fail-loud)
    - TESTING.md §3 (@DataJpaTest + Testcontainers Postgres for this test)
    - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java
  </read_first>
  <action>
    Create `MessageClass.java` under `core.inbox.domain` (NEW domain leaf since `core.inbox` doesn't currently have a `domain` package — verify with `glob` and create `package-info.java` if needed; check for `core.inbox.domain` existence; per CONVENTIONS §2 the `domain/` package is allowed and framework-free). Per memory `feedback_config_record_no_new_package.md`, prefer placing under an existing package — if `core.inbox.domain` is being introduced for this single enum it is acceptable because the enum is a core invariant consumed by both the read-side and the write-side. JavaDoc the package as "Core inbox vocabulary".
    - Implement `IdentifiedEnum`. Constants: `INVITE("INVITE")`, `CANCEL("CANCEL")`, `RESCHEDULE("RESCHEDULE")`, `RSVP("RSVP")`.
    - Per `<open_questions_from_research>` Q2, JavaDoc on `RESCHEDULE` notes: "Reserved for follow-up phase v1.5+; Phase 12 worker writes only INVITE / CANCEL / RSVP. Distinguishing initial vs reschedule for METHOD:REQUEST requires UID-based lookup which is not yet implemented."
    - Static `MessageClass fromId(String id)` — throws `NoSuchElementException("Unknown MessageClass id: " + id)` on unknown. Per CONVENTIONS §4 D-B5 — unordered identity.

    Modify `GmailInboxProjectionEntity.java` per PATTERNS.md lines 325-337:
    - Add `@Column(name = "message_class", length = 16) private String messageClass;` (nullable — NULL = not a calendar message).
    - Add `@Column(name = "event_dt") private Instant eventDt;` (nullable).
    - Public `Optional<MessageClass> getMessageClassOptional()` returns `Optional.ofNullable(messageClass).map(MessageClass::fromId)`. NO fail-loud `getMessageClass()` because the column is legitimately null for the vast majority of rows — fail-loud would force every caller to wrap in try/catch.
    - Public `Optional<Instant> getEventDtOptional()` returns `Optional.ofNullable(eventDt)`.
    - Public setter pair `setCalendarClassification(MessageClass messageClass, Instant eventDt)` — both must be non-null; throws `IllegalArgumentException` if one is null and the other is not (consistency invariant: a CANCEL with null event_dt is meaningless — the classifier task validates this upstream).
    - Wire into the protected ctor + getters that the existing repository expects. NO Lombok per CLAUDE.md hard ban.
    - Per privacy: the new fields carry NO content — only an enum + an Instant; safe to log via `event=calendar_classified messageClass={} eventDt={}`.

    Modify `GmailInboxProjectionRepository.java` `findInboxPage` native query per RESEARCH.md Pattern 3 + D-12. Replace the existing ORDER BY:
    ```
    ORDER BY received_at DESC, gmail_message_id DESC
    ```
    with:
    ```
    ORDER BY
        (message_class IS NOT NULL AND event_dt IS NOT NULL AND now() < event_dt + INTERVAL '24 hours') DESC,
        received_at DESC,
        gmail_message_id DESC
    ```
    Per RESEARCH.md line 543: keyset cursor stays valid because the pin predicate uses a single `now()` invocation per query. Update the existing native-query JavaDoc to cite the new pin column behavior. The W0 partial index `idx_inbox_projection_calendar_pin` from changeset 134 accelerates the predicate's filter sub-clause.

    Create `InboxProjectionPinningTest.java` extending `PostgresContainerTest`:
    - Use `@DataJpaTest` per TESTING.md §3 row 3 (repository query test).
    - Seed 10 inbox projection rows for one (tenantId, gmailConnectionId). Mix:
      - 5 rows with `messageClass=null, eventDt=null` (regular messages with descending received_at).
      - 3 rows with `messageClass='INVITE', eventDt=Instant.now()+2h` (pinned — event in the future, within 24h window).
      - 1 row with `messageClass='CANCEL', eventDt=Instant.now()-2h` (still pinned — pin_until = event_dt + 24h, so 22h remaining).
      - 1 row with `messageClass='INVITE', eventDt=Instant.now().minus(48, HOURS)` (NOT pinned — pin_until past).
    - Case `findInboxPage_pinnedRowsAppearFirst` — call repository `findInboxPage(tenantId, gmailConnectionId, beforeReceivedAt=null, beforeGmailMessageId=null, pageLimit=20)`; assert the first 4 rows in the result have `messageClass != null AND event_dt + 24h > now()`; the 5th onwards are unpinned or expired-pin rows ordered by received_at DESC.
    - Case `findInboxPage_keysetCursorSurvivesPin` — page 1 returns the first 5 rows; capture the last row's `(receivedAt, gmailMessageId)`; page 2 with `beforeReceivedAt=lastRow.receivedAt, beforeGmailMessageId=lastRow.gmailMessageId` returns the next 5 rows with no duplicates and no skips — the keyset cursor remains deterministic across the pin predicate.
    - Case `findInboxPage_unpinnedAfter24h` — seed one row with `messageClass='INVITE', eventDt=Instant.now().minus(25, HOURS)`; assert this row is NOT pinned (predicate false).
    - Privacy: no `subjectExcerpt` content asserted on; rows are identified by `gmailMessageId` only.
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.inbox.persistence.InboxProjectionPinningTest"</automated>
  </verify>
  <acceptance_criteria>
    - `MessageClass.java` exists; `grep -c 'RESCHEDULE' backend/core/src/main/java/com/zeromail/core/inbox/domain/MessageClass.java` returns at least 1 (column kept future-proof).
    - `grep -c 'message_class' backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionEntity.java` returns at least 1.
    - `grep -c "INTERVAL '24 hours'" backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionRepository.java` returns at least 1.
    - `grep -c 'pinned_until' backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionEntity.java backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionRepository.java | grep -v ':0$' | head -1` returns empty (D-12 invariant: NO new column, only derived predicate).
    - All 3 test cases in `InboxProjectionPinningTest` green.
    - JetBrains `get_file_problems` returns no errors on modified files.
    - `cd backend && ./gradlew :backend:core:check` green (ArchUnit + DomainPurity intact).
  </acceptance_criteria>
  <done>Inbox read-side pins calendar-class messages for 24h after event_dt; keyset cursor pagination remains correct.</done>
</task>

<task type="auto">
  <name>Task 2: CalendarPartParser + .ics fixtures + CalendarPartParserTest</name>
  <files>backend/worker/src/main/java/com/zeromail/worker/triage/CalendarPartParser.java, backend/worker/src/main/java/com/zeromail/worker/triage/package-info.java, backend/worker/src/test/resources/ical/invite-request.ics, backend/worker/src/test/resources/ical/cancel.ics, backend/worker/src/test/resources/ical/reply.ics, backend/worker/src/test/resources/ical/folded-lines.ics, backend/worker/src/test/resources/ical/billion-laughs.ics, backend/worker/src/test/java/com/zeromail/worker/triage/CalendarPartParserTest.java</files>
  <read_first>
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§Pattern 4 lines 549-616 — CalendarPartParser worked example; §Pitfall 4 charset variability + Pitfall 3 multipart MIME walk + Pitfall 1 XXE)
    - ../inbox-zero/apps/web/utils/parse/calender-event.ts (the IZ detection function; we DO NOT port TS code, but the IZ test fixtures referenced via memory `reference_inbox_zero.md` may live at `../inbox-zero/apps/web/__tests__/parse/calender-event.test.ts` — use those .ics samples translated to standalone files)
    - https://datatracker.ietf.org/doc/html/rfc5546 (METHOD semantics REQUEST / CANCEL / REPLY / REFRESH / COUNTER — only the first 3 matter for Phase 12)
    - https://datatracker.ietf.org/doc/html/rfc5545#section-3.1 (folded line behavior — lines MUST be ≤75 octets, continuation begins with a single SPACE or HTAB)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-10 parser scope deliberately narrow — METHOD + DTSTART only)
    - backend/worker/build.gradle.kts (W0 wires the ical4j dependency for this module)
  </read_first>
  <action>
    Create `package-info.java` under `backend/worker/.../triage/`. Modulith leaf module declaration: `displayName="Triage (Worker)"`, `allowedDependencies={"shared.lang", "shared.persistence"}` PLUS the necessary core access — since the listener UPDATEs `gmail_inbox_projection` it needs `core.inbox.persistence`. Mirror the existing worker module's package-info shape via `glob` — there is already a worker triage package per the existing `triage` glob hits in PATTERNS.md.

    Create `CalendarPartParser.java` per RESEARCH.md §Pattern 4 lines 567-611:
    - `public final class CalendarPartParser` — pure utility (NO `@Component` — instantiated by the classifier).
    - `public record ParseResult(MessageClass messageClass, Optional<Instant> eventDt) {}` — reuse the W4 Task 1 `MessageClass` enum (cross-package import per CONVENTIONS §2: worker → core.inbox.domain is allowed).
    - Static initializer or constructor block: disable XXE via ical4j's `CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, false)` and `CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_VALIDATION, false)` to honor Pitfall 1 — also explicitly disable any system-property-driven XML external entity resolution. Per RESEARCH.md line 1084-1085 verify with `CompatibilityHints`.
    - `public Optional<ParseResult> parse(String icsBody)` — preflight: if `icsBody == null || icsBody.length() > 1_000_000` (1 MB cap per Security Domain T-12-04) return `Optional.empty()`. Otherwise per RESEARCH.md sketch: build `CalendarBuilder`, parse into ical4j `Calendar`, read `METHOD` property, classify via switch on `method.toUpperCase()`: `"REQUEST" → INVITE`, `"CANCEL" → CANCEL`, `"REPLY" → RSVP`, default null (note `<open_questions_from_research>` Q2 — RESCHEDULE intentionally NOT distinguished). Read first `VEVENT.DTSTART` and convert via `toInstant(temporal)` helper covering `Instant`, `ZonedDateTime`, `LocalDateTime` (UTC-asusm), `LocalDate` (day-of). Return `Optional.of(new ParseResult(classification, dtStart))`.
    - Catch `(Exception parseFailure)` — return `Optional.empty()`. Per RESEARCH.md line 588 + CLAUDE.md privacy: NEVER log `icsBody` or `parseFailure.getMessage()` (the message may carry attendee email or subject). Log at most `log.warn("event=calendar_parse_failed_silent")` with no body details.
    - Per CLAUDE.md "Backend Code Style": explicit naming `icsBody`, `parseResult`, `classification`, `temporal` — NO `s`, `body`, `r`, `t`.

    Create the 5 `.ics` fixtures under `backend/worker/src/test/resources/ical/`:
    - `invite-request.ics` — standard `METHOD:REQUEST` calendar with one VEVENT, `DTSTART:20260620T130000Z`, `UID:test-invite-1`, `SUMMARY:Test Meeting`.
    - `cancel.ics` — `METHOD:CANCEL` with one VEVENT carrying the same UID as `invite-request.ics` (RFC 5546 CANCEL refers to a previously-known event).
    - `reply.ics` — `METHOD:REPLY` with PARTSTAT=ACCEPTED on the VEVENT's ATTENDEE property.
    - `folded-lines.ics` — same content as `invite-request.ics` but with a folded SUMMARY property `SUMMARY:Test\r\n  Meeting With Long Subject` exercising RFC 5545 §3.1 (continuation begins with SPACE). Verifies Pitfall 3 + Pitfall 4 (regex would fail; ical4j must not).
    - `billion-laughs.ics` — deliberately a malformed iCal that LOOKS like an XML billion-laughs attack pattern OR an iCal with 100_000 lines (size approaching 1 MB) so the parser hits the size bound. Per Pitfall 1 / T-12-04 the parse MUST return `Optional.empty()` without consuming excessive memory.

    Create `CalendarPartParserTest.java`:
    - Plain JUnit 5 (Layer 1 unit test per TESTING.md §3).
    - Helper `loadFixture(String name)` reads `/ical/{name}.ics` via classloader.
    - Cases:
      - `parse_invite_returnsInvite` — load `invite-request.ics`; assert `parseResult.messageClass() == MessageClass.INVITE`; assert `parseResult.eventDt().isPresent()` and equals the expected `2026-06-20T13:00:00Z`.
      - `parse_cancel_returnsCancel` — assert `messageClass() == CANCEL`.
      - `parse_reply_returnsRSVP` — assert `messageClass() == RSVP`.
      - `parse_methodRequest_doesNotEmitReschedule` — explicit per Q2: assert never `RESCHEDULE`.
      - `parse_foldedLines_doesNotCorruptDtstart` — load `folded-lines.ics`; assert DTSTART parses correctly (Pitfall 3 + Pitfall 4 mitigation).
      - `parse_billionLaughs_returnsEmpty` — load oversized fixture; assert `Optional.empty()` returned; assert no OutOfMemoryError; assert the test completes in <2s (size bound effective).
      - `parse_null_returnsEmpty`.
      - `parse_emptyString_returnsEmpty`.
      - `parse_truncatedIcs_returnsEmpty` — feed `"BEGIN:VCALENDAR\r\nMETHOD:REQUEST"` (no END); assert `Optional.empty()` (parser silent-fails).
      - `parse_methodCounter_returnsEmpty` — feed a valid iCal with `METHOD:COUNTER`; assert `Optional.empty()` (not classified — only REQUEST/CANCEL/REPLY map per D-10).
      - `parse_neverLogsIcsBody` — capture logback; parse a fixture with PII in SUMMARY; assert no log line contains the SUMMARY string.
    - Per TESTING.md §1 must-test "Sanitization + prompt injection corpus" — the billion-laughs and truncated tests are this pipeline's equivalent.
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:worker:test --tests "com.zeromail.worker.triage.CalendarPartParserTest"</automated>
  </verify>
  <acceptance_criteria>
    - 5 `.ics` fixtures exist under `backend/worker/src/test/resources/ical/`.
    - `CalendarPartParser.java` exists; `grep -c 'CompatibilityHints' backend/worker/src/main/java/com/zeromail/worker/triage/CalendarPartParser.java` returns at least 1 (XXE disabled assertion).
    - `grep -c 'log.*icsBody' backend/worker/src/main/java/com/zeromail/worker/triage/CalendarPartParser.java | grep -v '^#'` returns 0 (privacy invariant: never log iCal body).
    - `grep -c '1_000_000\|1000000' backend/worker/src/main/java/com/zeromail/worker/triage/CalendarPartParser.java` returns at least 1 (size bound).
    - All 11 test cases in `CalendarPartParserTest` green; billion-laughs test completes in <2s.
    - JetBrains `get_file_problems` returns no errors on the parser file.
  </acceptance_criteria>
  <done>Parser handles every RFC 5546 method we care about + folded lines + oversized input + null-safe + privacy-clean.</done>
</task>

<task type="auto">
  <name>Task 3: CalendarMessageClassifier worker listener + NoOAuth test + Inbox UI badge</name>
  <files>backend/worker/src/main/java/com/zeromail/worker/triage/CalendarMessageClassifier.java, backend/worker/src/test/java/com/zeromail/worker/triage/CalendarMessageClassifierNoOAuthTest.java, apps/web/features/inbox/components/MessageRow.tsx, apps/web/features/calendar/messages.ts</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java (the event payload — fields `tenantId`, `gmailMessageId`, `gmailConnectionId`, `observedAt` at minimum; verify what's carried so the classifier can re-load the projection row)
    - backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionRepository.java (W4 Task 1 — the new `findByTenantIdAndGmailConnectionIdAndGmailMessageId` finder OR existing `findById` shape; if a finder doesn't exist for this trio, add it in W4 Task 1's repository edit retroactively)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java (or wherever the message body / MIME parts are persisted — the classifier reads the text/calendar part from this row; verify the column name carrying body parts; per ARCH-02 raw body bytes are NOT persisted — the projection carries sanitized subject/sender excerpts + perhaps a flag for `has_calendar_part`; if the body is purged before AFTER_COMMIT, we need to fetch raw body via the existing Gmail message-get pipeline — verify and choose the access path during planning)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-10 + D-11 — worker AFTER_COMMIT; "does NOT block Pub/Sub ingestion latency"; ical4j parsing in worker)
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§Pattern 4 + §Pitfall 3 + §Pitfall 5 — REQUIRES_NEW propagation for the classifier's UPDATE)
    - CONVENTIONS.md §6 — `@ApplicationModuleListener` is INSIDE core only; worker uses plain `@TransactionalEventListener(AFTER_COMMIT)`
    - .planning/milestones/v1.3-phases/11-mailbox-scoped-ingestion-automation-ui-and-verification/11-CONTEXT.md (worker-side listener precedent if one exists from v1.3)
    - apps/web/features/inbox/components/MessageRow.tsx (the existing row component the badge attaches to)
    - apps/web/components/ui/badge.tsx (raw shadcn Badge primitive)
  </read_first>
  <action>
    Establish the body-fetch access path. The Gmail body is NOT persisted long-term per ARCH-02, but the v1.3 Gmail ingestion path (`PubSubIngestionService` + `GmailHistoryProcessor`) fetches the full message via `users.messages.get(id, format=FULL)`. The classifier must either: (a) fetch via the existing Gmail API client at AFTER_COMMIT time (~1 extra Gmail API call per message — expensive); OR (b) receive the text/calendar part as part of the `MailMessageObserved` event payload; OR (c) the existing v1.3 ingestion path already extracts and short-term-caches the body within the transaction context.
    Decide by reading `MailMessageObserved` payload + the v1.3 ingestion path. If (b) or (c) is not in place, choose (a) — but per RESEARCH.md "does NOT block Pub/Sub ingestion latency" comment and Pitfall 5, the classifier should be cheap. Per the spirit of Phase 12 not changing the existing path: option (a) via `GmailApiClientFactory.buildClientForMailbox(MailboxRef)` from Phase 10/11 (NOT `CalendarApiClientFactory` — CAL-TRIAGE-04 invariant — the classifier reads the Gmail message via the Gmail API, NOT the Calendar API).
    Document this decision in the SUMMARY.

    Create `CalendarMessageClassifier.java` in `backend/worker/.../triage/`:
    - `@Component`. Constructor-inject `GmailApiClientFactory gmailApiClientFactory`, `CalendarPartParser calendarPartParser`, `GmailInboxProjectionRepository gmailInboxProjectionRepository`, `PlatformTransactionManager transactionManager`. Build a `TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager); transactionTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);` per Pitfall 5.
    - `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) public void onMailMessageObserved(MailMessageObserved event)`. NOT `@ApplicationModuleListener` per CONVENTIONS §6 (worker process consuming a core event).
    - Steps:
      1. `MailboxRef mailboxRef = new MailboxRef(event.tenantId(), event.gmailConnectionId());`
      2. `Gmail gmailClient = gmailApiClientFactory.buildClientForMailbox(mailboxRef);` — fail-fast on disconnect via the Phase 10 exception family.
      3. `Message message = gmailClient.users().messages().get("me", event.gmailMessageId()).setFormat("FULL").execute();` — Gmail returns `Message` with `payload.parts[]`.
      4. Walk `message.getPayload()` recursively to find a part with `mimeType == "text/calendar"` OR an attachment whose `filename` ends with `.ics`. Implement the helper `Optional<String> findCalendarPartBody(MessagePart payload)` walking children recursively. Return the decoded body text. Per Pitfall 3, this is the IZ triple-check (`.ics` OR `text/calendar` OR `BEGIN:VCALENDAR` substring in `textPlain`/`textHtml` — the third path may also match marketing emails with `BEGIN:VCALENDAR` quoted in the body, so prefer the first two; the third is a fallback only when MIME walk yields nothing).
      5. Decode the body (Pitfall 4 charset variability): if the part has a `charset` MIME param, decode accordingly; default to UTF-8.
      6. If no calendar body found, return (the message is not calendar-related — UPDATE skipped).
      7. `Optional<ParseResult> result = calendarPartParser.parse(icsBody);` — silent-fail on Optional.empty().
      8. Inside `transactionTemplate.execute(...)`: load the projection row via `gmailInboxProjectionRepository.findByTenantIdAndGmailConnectionIdAndGmailMessageId(...)` (or whatever the existing repository shape is — add the finder if not present in W4 Task 1). If found: `projection.setCalendarClassification(result.messageClass(), result.eventDt().orElse(null));` — note `eventDt` may be empty for some weird invites; per W4 Task 1 the entity rejects null pair so handle by skipping the UPDATE if eventDt is empty (we cannot pin without an event_dt). Save via repository.
      9. Per Pitfall 5: on parse failure or save failure, write nothing (no half-update). The TX block silently rolls back; the outer MailMessageObserved event commit is not affected.
    - Privacy logging: `log.info("event=calendar_classified tenantId={} gmailMessageId={} messageClass={}", event.tenantId(), event.gmailMessageId(), result.messageClass());` — NO subject, NO sender email.

    Create `CalendarMessageClassifierNoOAuthTest.java` per VALIDATION.md TBD-w4-03 + CAL-TRIAGE-04 invariant:
    - `@SpringBootTest` on the worker context; `@MockitoBean GmailApiClientFactory` (returns a mock Gmail client whose `users().messages().get(...).setFormat(...).execute()` returns a fixture Message with a text/calendar part), `@MockitoBean CalendarApiClientFactory` (the **invariant under test**: this mock must NEVER be invoked).
    - Publish a `MailMessageObserved` event via `applicationEventPublisher.publishEvent(...)` inside a `@Transactional` block; assert AFTER_COMMIT the projection row gets `messageClass=INVITE` and `eventDt` populated.
    - Critical assertion (CAL-TRIAGE-04): `verify(calendarApiClientFactory, never()).buildClientForCalendarConnection(any(), any());` AND `verify(calendarApiClientFactory, never()).evictAccessToken(any());`. If the classifier ever reaches into Calendar OAuth this fails.
    - Also assert: if the event references a non-calendar message (mock returns a message with only `text/html`), the projection row's `messageClass` remains null (no update).
    - Also assert: if the parser returns `Optional.empty()` (mock the parser via `@MockitoSpyBean` if needed to inject a parse failure), the projection row is NOT updated.

    Extend `apps/web/features/inbox/components/MessageRow.tsx` (per CONVENTIONS §3 + memory `feedback_raw_shadcn_first.md`):
    - Add a prop or read from the existing `message` shape: the regenerated schema from W3 should now carry `messageClass` and `eventDt` on the `InboxProjectionMessage` DTO. If W3 already regenerated the schema, the fields are available; if not, W3's `pnpm generate:api` from Task 2 will catch them once the backend ships W4. For this task assume the backend DTO already exposes them (it should — the inbox controller serializes the projection entity).
    - Conditionally render a raw shadcn `Badge variant="outline"` next to the subject/sender row: if `message.messageClass === 'CANCEL'` → `t('calendar.badge.cancellation')` (VN "Hủy" / EN "Cancellation"); if `message.messageClass === 'RESCHEDULE'` → `t('calendar.badge.timeChanged')` ("Đổi giờ" / "Time changed"). INVITE and RSVP show no badge — pinning alone is the affordance per D-12.
    - Position the badge inline with the subject; use `text-xs` and `Badge` variant outline + token classes (no hex colors per AGENTS.md).

    Edit `apps/web/features/calendar/messages.ts` (extending W3's bundle): add the two new keys `calendar.badge.cancellation` and `calendar.badge.timeChanged` in both `vi` + `en` shapes per CONVENTIONS §10. Run `pnpm --filter web run i18n:build` to regenerate the locale JSON.
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:worker:test --tests "com.zeromail.worker.triage.CalendarMessageClassifierNoOAuthTest" && cd ../apps/web && pnpm --filter web run typecheck && pnpm --filter web run i18n:check</automated>
  </verify>
  <acceptance_criteria>
    - `CalendarMessageClassifier.java` exists; `grep -c '@TransactionalEventListener' backend/worker/src/main/java/com/zeromail/worker/triage/CalendarMessageClassifier.java` returns at least 1; `grep -c '@ApplicationModuleListener' backend/worker/src/main/java/com/zeromail/worker/triage/CalendarMessageClassifier.java | grep -v '^#'` returns 0 (CONVENTIONS §6).
    - `grep -c 'CalendarApiClientFactory' backend/worker/src/main/java/com/zeromail/worker/triage/CalendarMessageClassifier.java | grep -v '^#'` returns 0 (CAL-TRIAGE-04 invariant — classifier uses Gmail API only).
    - `grep -c 'PROPAGATION_REQUIRES_NEW\|REQUIRES_NEW' backend/worker/src/main/java/com/zeromail/worker/triage/CalendarMessageClassifier.java` returns at least 1 (Pitfall 5).
    - All `CalendarMessageClassifierNoOAuthTest` cases green including the `verify(..., never())` assertion on `CalendarApiClientFactory`.
    - `MessageRow.tsx` renders the Badge only for CANCEL / RESCHEDULE — typecheck + lint green.
    - `calendar.badge.cancellation` and `calendar.badge.timeChanged` keys exist in `messages.ts` and pass `i18n:check`.
    - JetBrains `get_file_problems` returns no errors on the classifier file.
  </acceptance_criteria>
  <done>Calendar-aware triage works end-to-end: a Pub/Sub-delivered invite triggers the worker → MailMessageObserved AFTER_COMMIT → CalendarMessageClassifier UPDATEs the projection → inbox page pins it top-of-list → "Cancellation" badge surfaces if METHOD:CANCEL. Zero Calendar OAuth involvement.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Pub/Sub event → MailMessageObserved → CalendarMessageClassifier | Untrusted Gmail message body crosses into ical4j parser. |
| ical4j parser input | RFC 5545 / RFC 5546 spec-conforming text; may contain malicious XXE/billion-laughs payloads. |
| Worker UPDATE on gmail_inbox_projection | Tenant-scoped via repository.findByTenantIdAndGmailConnectionIdAndGmailMessageId; isolation enforced by Hibernate @TenantId. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-12-04 | Denial of Service | iCal injection (XXE / billion-laughs in text/calendar body) | mitigate | ical4j 4.x disables external entity resolution by default; `CompatibilityHints.KEY_RELAXED_PARSING = false`; size-bound 1 MB before parse (`icsBody.length() > 1_000_000 → Optional.empty()`); catch-all silent-fail on parse exceptions. `CalendarPartParserTest.parse_billionLaughs_returnsEmpty` proves bound effective. |
| T-12-10 | Information Disclosure | Worker logs iCal body / SUMMARY / ATTENDEE emails | mitigate | `log.warn("event=calendar_parse_failed_silent")` carries NO body details. `CalendarPartParserTest.parse_neverLogsIcsBody` asserts the discipline. Privacy logging format per CONVENTIONS §5. |
| T-12-04bis | Denial of Service | Classifier triggers OOM via repeated REQUIRES_NEW transactions on heavy ingest | mitigate | Each classifier invocation owns one short TX; UPDATE is a single-row keyed by `(tenantId, gmail_connection_id, gmail_message_id)`. The W0 partial index `idx_inbox_projection_calendar_pin` keeps the table-scan cost low even under sustained load. |
| T-12-11 | Tampering | Stale or malicious DTSTART pinning a message far into the future / past | mitigate | The pinning ORDER BY uses `now() < event_dt + INTERVAL '24 hours'` — past events older than 24h drop out of the pin window naturally. Future events 10y out also pin briefly but the abuse vector requires the user receive a malicious invite, which is no worse than the existing Gmail behavior. No additional gate needed. |
| T-12-V1-1 | Architecture | Worker uses CalendarApiClientFactory (would violate CAL-TRIAGE-04) | mitigate | Test `CalendarMessageClassifierNoOAuthTest` `verify(calendarApiClientFactory, never())` is the executable invariant. Grep-level acceptance criteria also enforces. |
| T-12-V4-1 | Access Control | Worker UPDATE leaks across tenants | mitigate | `findByTenantIdAndGmailConnectionIdAndGmailMessageId(...)` carries tenantId; Hibernate `@TenantId` binding on `GmailInboxProjectionEntity` (existing v1.3 invariant); worker process establishes TenantContext via Phase 11's mailbox resolution. |
</threat_model>

<verification>
- `cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.inbox.persistence.InboxProjectionPinningTest" :backend:worker:test --tests "com.zeromail.worker.triage.*"` — all tests green.
- `cd backend && ./gradlew :backend:core:check :backend:worker:check` — full check green (ApplicationModulesTest, ArchUnit, DomainPurity all intact).
- `cd apps/web && pnpm --filter web run typecheck && pnpm --filter web run lint && pnpm --filter web run i18n:check` — frontend green.
- Manual: send a Google Calendar invite to a test mailbox; observe Pub/Sub → ingestion → classifier → projection row gets `messageClass='INVITE'`; refresh /inbox; the message is at the top with no badge. Send a cancellation; observe `messageClass='CANCEL'`; refresh /inbox; row at top with "Cancellation" badge.
</verification>

<success_criteria>
- `MessageClass` enum + projection columns + repository ORDER BY changes are committed; `InboxProjectionPinningTest` green with keyset cursor sanity.
- `CalendarPartParser` handles REQUEST / CANCEL / REPLY / folded lines / oversized / null / truncated / METHOD:COUNTER; XXE disabled; size-bound 1 MB.
- `CalendarMessageClassifier` runs as a worker AFTER_COMMIT listener on MailMessageObserved; uses `GmailApiClientFactory` (NOT Calendar); UPDATEs projection in REQUIRES_NEW tx.
- `CalendarMessageClassifierNoOAuthTest` verifies `CalendarApiClientFactory` is never invoked — CAL-TRIAGE-04 invariant locked.
- `apps/web/features/inbox/components/MessageRow.tsx` renders Cancellation / Time changed badges with raw shadcn `Badge variant="outline"` + token classes.
- i18n bundles in sync.
</success_criteria>

<output>
Create `.planning/phases/12-calendar-connection-triage-foundation/12-05-SUMMARY.md` listing: (a) the chosen body-fetch path in the classifier (a/b/c from Task 3 deliberation), (b) the parser's billion-laughs test runtime (<2s), (c) confirmation that `grep -c CalendarApiClient backend/worker` is 0, (d) the W0 idx_inbox_projection_calendar_pin index usage proved via `EXPLAIN ANALYZE` on the new ORDER BY query (capture EXPLAIN output proving the partial index is used).
</output>
