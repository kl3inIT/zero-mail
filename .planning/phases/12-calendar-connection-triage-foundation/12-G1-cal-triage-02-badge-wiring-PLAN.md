---
phase: 12-calendar-connection-triage-foundation
plan: G1
type: execute
wave: 6
depends_on: [12-04, 12-05]
gap_closure: true
autonomous: true
requirements: [CAL-TRIAGE-02]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionMessage.java
  - backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionReadService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java
  - backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java
  - apps/web/lib/api/schema.d.ts
  - apps/web/openapi/zero-mail-spec.json
  - apps/web/features/inbox/api/inbox-api.ts
  - apps/web/features/inbox/components/InboxPageClient.tsx
  - apps/web/__tests__/inbox/inbox-page-client-cancel-badge.test.tsx
  - apps/web/e2e/inbox-calendar-badge.spec.ts
  # Optional (Task 4 — recommended bundling once tunnel + regen are up):
  - apps/web/features/calendar/types.ts
  - apps/web/features/calendar/api/calendar-api.ts

must_haves:
  truths:
    - "GmailInboxMessageResponse JSON payload carries messageClass (allowableValues INVITE|CANCEL|RESCHEDULE|RSVP, nullable) and eventDt (ISO8601 instant, nullable) for every inbox row served by the projection path."
    - "apps/web/lib/api/schema.d.ts is regenerated from /v3/api-docs via pnpm --filter web run generate:api; the generated GmailInboxMessageResponse schema carries the two new properties; the hand-typed InboxMessageClass + messageClass?/eventDt? declarations at inbox-api.ts lines 4-6, 22-26, and 64-66 are deleted in favour of the regenerated type."
    - "Playwright e2e exercises a row with messageClass='CANCEL' and asserts the [data-testid='inbox-message-cancellation-badge'] outline Badge renders — NOT the dead-branch undefined state."
    - "Server-side pin behavior (W4) is unchanged — InboxProjectionPinningTest stays green; the new wiring does NOT alter the ORDER BY pin predicate or the partial index."
    - "Backend enum exposure honours CONVENTIONS §3 + §4 — @Schema(allowableValues = {\"INVITE\",\"CANCEL\",\"RESCHEDULE\",\"RSVP\"}) mirrors MessageClass.values().id() exactly; MessageClass.fromId remains the only deserialization seam and stays fail-loud."
    - "Privacy invariant (CLAUDE.md ARCH-02) — eventDt is the calendar event datetime already extracted and stored by W4's CalendarPartParser from VEVENT DTSTART, NOT raw body content; this gap plan adds zero new long-term body persistence and zero new logging surfaces."
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionMessage.java"
      provides: "Projection read-shape record carrying messageClass + eventDt"
      contains: "MessageClass messageClass"
      contains_2: "Instant eventDt"
    - path: "backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionReadService.java"
      provides: "toInboxProjectionMessage(row, tenantId) populates the two new fields from GmailInboxProjectionEntity.getMessageClassOptional()/getEventDtOptional()"
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java"
      provides: "RecentInboxMessage nested record extended; toRecentInboxMessages (projection path) populates the two new fields; toRecentInboxMessage (live-Gmail path) returns null for both (live Gmail never carries projection columns)"
    - path: "backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java"
      provides: "DTO carries the two fields; from(...) mapper threads them; @Schema(allowableValues = {...}) constrains the enum on the OpenAPI spec; @JsonInclude(Include.NON_NULL) for nullable variants"
    - path: "apps/web/lib/api/schema.d.ts"
      provides: "Regenerated GmailInboxMessageResponse schema carries messageClass + eventDt (verifiable via grep -c messageClass returning >0)"
    - path: "apps/web/features/inbox/api/inbox-api.ts"
      provides: "Hand-typed InboxMessageClass + messageClass?/eventDt? fields REMOVED; type re-exports/aliases pull from components['schemas']['GmailInboxMessageResponse'] instead"
    - path: "apps/web/__tests__/inbox/inbox-page-client-cancel-badge.test.tsx"
      provides: "Vitest: render InboxMessageRow against a CANCEL row; assert [data-testid='inbox-message-cancellation-badge'] visible"
    - path: "apps/web/e2e/inbox-calendar-badge.spec.ts"
      provides: "Playwright: mock /api/gmail/inbox to return a row with messageClass='CANCEL'; assert badge visible + screenshot"
  key_links:
    - from: "GmailInboxProjectionRepository.findInboxPage (native SQL — already SELECTs message_class, event_dt)"
      to: "InboxProjectionMessage"
      via: "InboxProjectionReadService.toInboxProjectionMessage reads row.getMessageClassOptional() + row.getEventDtOptional()"
      pattern: "row\\.getMessageClassOptional"
    - from: "InboxProjectionMessage"
      to: "RecentInboxReadService.RecentInboxMessage"
      via: "toRecentInboxMessages copies projectionItem.messageClass() + projectionItem.eventDt() into the new record fields"
      pattern: "projectionItem\\.messageClass\\(\\)"
    - from: "RecentInboxReadService.RecentInboxMessage"
      to: "GmailInboxMessageResponse"
      via: "from(message) reads message.messageClass() (MessageClass | null) → .id() and message.eventDt() (Instant | null)"
      pattern: "message\\.messageClass\\(\\)"
    - from: "GmailInboxMessageResponse"
      to: "apps/web/lib/api/schema.d.ts components['schemas']['GmailInboxMessageResponse']"
      via: "pnpm --filter web run generate:api fetches /v3/api-docs against the running backend"
      pattern: "messageClass"
    - from: "apps/web/features/inbox/api/inbox-api.ts InboxMessage"
      to: "apps/web/features/inbox/components/InboxPageClient.tsx InboxMessageRow"
      via: "normalizeMessage propagates messageClass from the response (line 130 stays); InboxMessageRow lines 587-604 read message.messageClass and pick the Badge variant"
      pattern: "message\\.messageClass === 'CANCEL'"
---

<objective>
Close CAL-TRIAGE-02 by threading the existing W4-populated `gmail_inbox_projection.message_class` + `event_dt` columns through the read-side chain so the already-shipped Badge UI (`InboxPageClient.tsx:587-604`) — currently a dead branch because `message.messageClass` is always `undefined` at runtime — actually lights up against a CANCEL or RESCHEDULE row.

This is a pure data-wiring gap closure. Source: `12-VERIFICATION.md` gaps[0] (CAL-TRIAGE-02). The pin half of CAL-TRIAGE-02 (server-side ORDER BY in `GmailInboxProjectionRepository.findInboxPage`) was verified green by `InboxProjectionPinningTest` in W4. The badge half is broken end-to-end because the projection→DTO chain was never threaded through — W4 SUMMARY admitted this as "Known Stub #1" and "Deviation #3"; REQUIREMENTS.md already flipped CAL-TRIAGE-02 back to `[ ]` after the verification run.

Purpose: Make REQUIREMENTS.md `CAL-TRIAGE-02` truthful by closing the read-side gap. No new behavior, no new persistence, no new logging, no UI redesign — just connect the wires that W4 left dangling.

Output:
- `messageClass` + `eventDt` carried end-to-end: `GmailInboxProjectionEntity` (already has the columns + Optional getters from W4) → `InboxProjectionMessage` (extend) → `RecentInboxReadService.RecentInboxMessage` (extend) → `GmailInboxMessageResponse` (extend + `@Schema(allowableValues)`) → regenerated `schema.d.ts` → typed `InboxMessage` via re-export → the existing `InboxPageClient` Badge branch finally renders.
- One Vitest unit test pinning the Badge branch against a CANCEL row.
- One Playwright e2e spec proving the badge renders end-to-end with the mocked inbox endpoint.
- (OPTIONAL TASK 4 — recommended bundling) Once the dev SSH tunnel is up and the backend booted for the schema regen, also collapse the W3 deferral: swap `apps/web/features/calendar/types.ts` to use `components['schemas'][...]` and promote `calendar-api.ts` from raw `fetch` to typed `api.GET/POST/PATCH/DELETE`. Same regen cycle, ~15min extra, removes the `TODO(12-W3)` markers cleanly.

Pre-existing follow-up NOT in scope (separate one-line PR): `apps/web/lib/content/frontmatter.ts(1,36): TS2307: Cannot find module 'yaml'` — fix is `pnpm --filter web add yaml`. Pre-existing from commit `ab77689e`, not introduced by Phase 12.
</objective>

<execution_context>
@$HOME/.claude/gsd-core/workflows/execute-plan.md
@$HOME/.claude/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/phases/12-calendar-connection-triage-foundation/12-VERIFICATION.md
@.planning/phases/12-calendar-connection-triage-foundation/12-W4-text-calendar-classification-and-pinning-SUMMARY.md
@.planning/phases/12-calendar-connection-triage-foundation/12-W3-calendar-settings-frontend-SUMMARY.md
@CLAUDE.md
@apps/web/AGENTS.md
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Extend backend projection + read-service records with messageClass + eventDt; preserve invariants</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionMessage.java,
    backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionReadService.java,
    backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java,
    backend/core/src/test/java/com/zeromail/core/inbox/persistence/InboxProjectionPinningTest.java
  </files>
  <read_first>
    - .planning/phases/12-calendar-connection-triage-foundation/12-VERIFICATION.md (gaps[0] — fix list items 1 + 2)
    - backend/core/src/main/java/com/zeromail/core/inbox/domain/MessageClass.java — IdentifiedEnum carrying INVITE/CANCEL/RESCHEDULE/RSVP; `fromId` is fail-loud (CONVENTIONS §4)
    - backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionEntity.java lines 88-265 — `getMessageClassOptional()` (Optional&lt;MessageClass&gt;) + `getEventDtOptional()` (Optional&lt;Instant&gt;) already shipped by W4; pair-set invariant in `setCalendarClassification` is the source of truth for "both or neither"
    - backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionRepository.java — `findInboxPage` already SELECTs `message_class` + `event_dt` and the ORDER BY pin predicate (line 130-132) reads them; `updateCalendarClassification` is W4's writer
    - backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionMessage.java — current 12-field record shape, validate-in-canonical-ctor pattern (lines 38-49)
    - backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionReadService.java lines 122-160 — `toInboxProjectionMessage(row, tenantId)`; this is where new fields are populated for the projection path
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java lines 398-418 (`toRecentInboxMessages` projection path), 807-829 (`toRecentInboxMessage` live-Gmail path), 1121-1141 (`RecentInboxMessage` nested record — extend HERE, not in a new file)
    - backend/core/src/test/java/com/zeromail/core/inbox/persistence/InboxProjectionPinningTest.java — existing W4 test; must remain green
  </read_first>
  <action>
    Extend the two read-shape records and their mappers to carry calendar classification.

    1. `InboxProjectionMessage` (record at lines 24-49): add `Optional&lt;MessageClass&gt; messageClass` and `Optional&lt;Instant&gt; eventDt` as the LAST two fields (after `hasAttachment`). Use `Optional` not nullable reference — this is a use-case-facing record, not a wire DTO, and the pair-set invariant from `GmailInboxProjectionEntity.setCalendarClassification` (both NULL or both present) is naturally expressed as `Optional`. Update the canonical constructor: `messageClass = messageClass == null ? Optional.empty() : messageClass;` and same for `eventDt`. Add a small invariant check at the end of the canonical ctor: `if (messageClass.isPresent() != eventDt.isPresent()) throw new IllegalArgumentException("messageClass and eventDt must be set together");`. Update the Javadoc to mention the calendar columns + W4 source.

    2. `InboxProjectionReadService.toInboxProjectionMessage` (line 122): pass `row.getMessageClassOptional()` + `row.getEventDtOptional()` as the two new constructor args. NO new field on the row needed — W4 already added the optional getters.

    3. `RecentInboxReadService.RecentInboxMessage` (NESTED record at lines 1121-1141 inside `RecentInboxReadService.java` — NOT a separate file; the verifier's `path:` was a logical reference): add `MessageClass messageClass` (nullable reference) and `Instant eventDt` (nullable reference) as the LAST two fields. The `RecentInboxMessage` is closer to the wire shape than `InboxProjectionMessage` so nullable references match the JSON-null wire semantics naturally. Update the canonical ctor's defensive copies (no copy needed for either new field — both are immutable reference types).

    4. `RecentInboxReadService.toRecentInboxMessages` (line 398): pull `projectionItem.messageClass().orElse(null)` and `projectionItem.eventDt().orElse(null)` and pass them into the `new RecentInboxMessage(...)` constructor. Do NOT collapse to a single `Optional` — the wire DTO maps `Optional.empty()` to JSON `null` anyway, and a nullable reference is the conventional Java DTO shape.

    5. `RecentInboxReadService.toRecentInboxMessage` (LIVE-GMAIL path, line 807): pass `null, null` for the two new fields. The live-Gmail fallback path does NOT read from the projection table — it has no access to `message_class`/`event_dt`. This is correct: calendar pin + badge only work once the projection is populated; for first-connect tenants serving live Gmail (line 200), badges silently degrade until backfill catches up. Document this in a one-line code comment: `// live-Gmail path has no projection columns — calendar badges activate once backfill populates the projection`.

    6. Extend the existing `InboxProjectionPinningTest` (do NOT create a new file): add ONE new test method `findInboxPage_carriesMessageClassAndEventDt` that inserts a CANCEL row via the test fixture, calls `findInboxPage`, maps the result through `InboxProjectionReadService.toInboxProjectionMessage` (or call the service if the test already wires it), and asserts `result.messageClass()` is `Optional.of(MessageClass.CANCEL)` and `result.eventDt()` is present. This guards the projection→record map at the boundary that VERIFICATION.md flagged as missing.

    Constraints:
    - NO new long-term storage. No DB schema change. No new logging. No new prompts/completions surface.
    - CONVENTIONS §4: MessageClass.fromId stays fail-loud — do NOT add `try/catch` around the getter chain to hide deserialization errors. If the DB ever holds an unknown `message_class` value (impossible because the column has no W4 writer outside the IdentifiedEnum and the W0 changeset 134 has no CHECK constraint relaxation), the read should crash loudly.
    - Enterprise readability: name the new locals `messageClassification` / `eventTimestamp` when they need to alias the field name (e.g. inside the pair-set validation). Single-letter `e` / `m` is forbidden.
    - The pair-set invariant on `InboxProjectionMessage` is defensive — it cannot be violated by the projection path (entity's `setCalendarClassification` already enforces pair-set) but it pins the contract for future writers.
  </action>
  <verify>
    <automated>cd backend &amp;&amp; ./gradlew :backend:core:test --tests "com.zeromail.core.inbox.persistence.InboxProjectionPinningTest" --tests "com.zeromail.core.inbox.usecases.InboxProjectionReadServiceTest" --tests "com.zeromail.core.gmail.usecases.RecentInboxReadServiceTest"</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "messageClass" backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionMessage.java` returns ≥2 (field + ctor copy).
    - `grep -c "eventDt" backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionMessage.java` returns ≥2.
    - `grep -c "messageClass" backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java` returns ≥3 (record field + projection-path copy + live-Gmail-path null).
    - `grep -c "row.getMessageClassOptional" backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionReadService.java` returns 1.
    - `InboxProjectionPinningTest` is green (W4 invariant unchanged) AND the new `findInboxPage_carriesMessageClassAndEventDt` test method passes.
    - `mcp__jetbrains__get_file_problems` on the three modified Java files reports zero errors and zero warnings introduced by this task.
    - `grep -c "import lombok" backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionMessage.java backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java` returns 0 (Lombok ban preserved).
  </acceptance_criteria>
  <done>
    `InboxProjectionMessage` and `RecentInboxMessage` both carry calendar classification; the projection-path mappers populate them from W4's getters; the live-Gmail-path mapper sets them to null with a documented one-line rationale; the pair-set invariant is asserted at the use-case record boundary; the new `InboxProjectionPinningTest` case proves the projection→record map; all existing W4 tests stay green.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Extend GmailInboxMessageResponse DTO with messageClass + eventDt; add @Schema allowableValues; update from(...) mapper</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java,
    backend/api/src/test/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponseTest.java
  </files>
  <read_first>
    - backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java — current shape; existing `@Schema(requiredProperties = {...})` pattern at line 8; canonical-ctor defensive copies at lines 39-44; `from(RecentInboxMessage)` static factory at line 46
    - backend/core/src/main/java/com/zeromail/core/inbox/domain/MessageClass.java — IdentifiedEnum; `id()` returns the wire string (INVITE/CANCEL/RESCHEDULE/RSVP)
    - CLAUDE.md CONVENTIONS §3 — DTO records must publish accurate OpenAPI: `@Schema(allowableValues)` for closed string sets; `@JsonInclude(NON_NULL)` for nullable optional fields; do NOT mark a nullable field in `requiredProperties`
    - apps/web/AGENTS.md — `schema.d.ts` regen contract; the wire schema is the source of truth for the frontend
    - Check existing test pattern: backend/api/src/test/java/com/zeromail/api/dto/gmail/ (look for an existing `GmailInboxMessageResponseTest` or sibling test as a shape precedent; if none exists, create the file fresh)
  </read_first>
  <action>
    Extend the wire DTO so the OpenAPI document carries the calendar fields with proper schema constraints.

    1. Add two new fields to the record header, AFTER `openInGmailUrl`:
       ```
       @Schema(
               description = "Calendar classification when this message is a text/calendar invite/cancel/reschedule/RSVP — populated by the worker AFTER_COMMIT classifier (W4). NULL for non-calendar messages and for rows served from the live-Gmail fallback before backfill completes.",
               allowableValues = {"INVITE", "CANCEL", "RESCHEDULE", "RSVP"},
               nullable = true)
       String messageClass,
       @Schema(
               description = "Calendar event datetime extracted from VEVENT DTSTART when messageClass is non-null. NULL when messageClass is NULL. Drives the 24-hour top-of-inbox pin window (W4).",
               nullable = true,
               format = "date-time")
       Instant eventDt
       ```
       Place both fields LAST in the record header, after the existing `String openInGmailUrl` (preserve existing field order for JSON wire stability).

    2. Do NOT add `messageClass` or `eventDt` to the existing `@Schema(requiredProperties = {...})` array on the record — they are nullable, not required. Leave the existing 13 required properties untouched.

    3. Add `@JsonInclude(JsonInclude.Include.NON_NULL)` at the record type level. Per CONVENTIONS §3, nullable optional fields should be omitted from the JSON payload when null, keeping the wire shape stable for non-calendar messages. Verify the import is `com.fasterxml.jackson.annotation.JsonInclude` (per memory `feedback_spring_boot_4_breaking_changes.md` — Boot 4 / Jackson 3 left annotations on the legacy package; do NOT switch to `tools.jackson.annotation.*`).

    4. Update the canonical constructor (lines 39-44) — neither new field needs a defensive copy (`String` is immutable; `Instant` is immutable). Just leave them through.

    5. Update the `from(RecentInboxMessage message)` static factory at line 46: pass `message.messageClass() == null ? null : message.messageClass().id()` for the String field, and `message.eventDt()` for the Instant field. The `.id()` call hands MessageClass.fromId's inverse — the canonical string the API publishes. Do NOT use `.name()` — convention §4 says `id()` is the storage seam.

    6. Create `GmailInboxMessageResponseTest` (or extend if it exists) with three new test methods:
       - `from_propagatesCancelMessageClass`: build a `RecentInboxMessage` with `messageClass = MessageClass.CANCEL` and a fixed `eventDt` Instant; call `from(...)`; assert `response.messageClass()` equals `"CANCEL"` and `response.eventDt()` equals the fixture instant.
       - `from_propagatesNullMessageClassAsNull`: build a `RecentInboxMessage` with both new fields null; assert both response fields are null.
       - `from_serializesAllowableValuesPerEnum`: use a parameterized test or a four-case AssertJ assertion that all four `MessageClass.values()` map to their `.id()` string verbatim when passed through `from(...)` — proves Schema allowableValues stays in sync with the enum if a future contributor adds a fifth value.

    Constraints:
    - Thin controller boundary (CONVENTIONS §1): no DB access in this DTO; the controller stays thin; `from(...)` does the mapping.
    - `@Schema(allowableValues = ...)` must MIRROR `MessageClass.values()` exactly. If a future planner adds a fifth value (e.g. COUNTER) the parameterized test `from_serializesAllowableValuesPerEnum` will trip and force the DTO update — that is intentional.
    - Do NOT widen the DTO into a generic `String` without `allowableValues`. The OpenAPI consumer (frontend) relies on the closed enum for exhaustive switch coverage.
  </action>
  <verify>
    <automated>cd backend &amp;&amp; ./gradlew :backend:api:test --tests "com.zeromail.api.dto.gmail.GmailInboxMessageResponseTest"</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "messageClass" backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java` returns ≥3 (field + Schema + from(...) mapping).
    - `grep -c "allowableValues" backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java` returns ≥1.
    - `grep -c "INVITE.*CANCEL.*RESCHEDULE.*RSVP" backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java` returns ≥1 (the enum-string list inside `allowableValues`).
    - `grep -c "@JsonInclude" backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java` returns ≥1.
    - `grep -c "com.fasterxml.jackson.annotation.JsonInclude" backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailInboxMessageResponse.java` returns 1 (Jackson 3 annotation package check — NOT `tools.jackson.annotation`).
    - `GmailInboxMessageResponseTest` has the three new methods all green.
    - `mcp__jetbrains__get_file_problems` on the modified DTO + test report zero errors.
  </acceptance_criteria>
  <done>
    DTO carries `messageClass` (with closed `allowableValues`) + `eventDt` (with `format=date-time`); both are nullable + `@JsonInclude(NON_NULL)`; `from(...)` threads them; three unit tests pin the mapping + the enum-exhaustiveness contract.
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 3: Regenerate apps/web/lib/api/schema.d.ts; delete hand-typed fields from inbox-api.ts; add Vitest + Playwright tests for the CANCEL badge</name>
  <files>
    apps/web/lib/api/schema.d.ts,
    apps/web/openapi/zero-mail-spec.json,
    apps/web/features/inbox/api/inbox-api.ts,
    apps/web/__tests__/inbox/inbox-page-client-cancel-badge.test.tsx,
    apps/web/e2e/inbox-calendar-badge.spec.ts
  </files>
  <read_first>
    - apps/web/AGENTS.md — `schema.d.ts` is generated NEVER hand-edited (MANDATORY); regen procedure
    - apps/web/scripts/generate-api.ts — the regen pipeline (`pnpm --filter web run generate:api`)
    - apps/web/features/inbox/api/inbox-api.ts lines 4-6 (`InboxMessageClass` type alias), 22-26 (`GmailInboxMessageResponse.messageClass?/eventDt?`), 64-66 (`InboxMessage.messageClass?/eventDt?`) — the three hand-typed sites that must be replaced by re-exports from the generated schema
    - apps/web/features/inbox/components/InboxPageClient.tsx lines 587-604 — the existing Badge branches for CANCEL/RESCHEDULE; UNCHANGED in this task (already correct, just dead because the wire data was missing)
    - apps/web/__tests__/calendar/use-calendar-connections.test.tsx — Vitest shape precedent for feature tests
    - apps/web/e2e/calendar-settings.spec.ts — Playwright shape precedent for mocked inbox responses
    - apps/web/e2e/chrome-test-utils.ts — shared mock stack for inbox routes
    - memory `reference_dev_db_ssh_tunnel.md`: dev DB requires `ssh -L 5555:localhost:5555 dat@72.62.193.33` to be running BEFORE the backend boots
  </read_first>
  <action>
    Threading the backend wire changes into the frontend, then proving the dead Badge branch is alive with both unit and e2e tests.

    PRE-FLIGHT (manual gate — surface BEFORE running pnpm):
    1. Ensure the dev SSH tunnel is up: `ssh -L 5555:localhost:5555 dat@72.62.193.33` (one-time, runs in a background terminal). The tunnel is REQUIRED — Liquibase fails at boot without it (memory `reference_dev_dev_inbox_projection_keys.md` + `reference_dev_db_ssh_tunnel.md`). If the tunnel is not available in this executor session, surface as an Authentication Gate: report "SSH tunnel to dev Postgres required for OpenAPI regen — start tunnel in a separate terminal and retry" with the exact command and the resume signal "tunnel up".
    2. Ensure `apps/web/.env.local` carries `INBOX_PROJECTION_KEY_BASE64` + `INBOX_PROJECTION_SENDER_HASH_KEY_BASE64` (memory `reference_dev_inbox_projection_keys.md` — Spring `${VAR:?msg}` is NOT bash fail-fast and a bad base64 default crashes boot).

    REGEN (commands; no interpretation needed):
    3. Boot the backend in a separate terminal (or via IntelliJ run config "ZeroMailApi"): `./gradlew :backend:api:bootRun`. Wait for `Started ZeroMailApiApplication`.
    4. Run the regen: `pnpm --filter web run generate:api`. The script fetches `/v3/api-docs` from `http://localhost:8080` (default backend port) and pipes through `openapi-typescript` → `apps/web/lib/api/schema.d.ts` + `apps/web/openapi/zero-mail-spec.json`.
    5. Verify the regen carries the new fields: `grep -c "messageClass" apps/web/lib/api/schema.d.ts` must return ≥1 (it was 0 before). Also `grep -c "eventDt" apps/web/lib/api/schema.d.ts` ≥1. If either is 0, the backend boot ran a stale build — clean + rebuild + retry.

    FRONTEND-TYPE DEDUP:
    6. Edit `apps/web/features/inbox/api/inbox-api.ts`:
       - DELETE lines 4-6: the `// TODO: ...` comment + `export type InboxMessageClass = 'INVITE' | 'CANCEL' | 'RESCHEDULE' | 'RSVP';`.
       - REPLACE with a re-export: `import type { components } from '@/lib/api/schema'; export type InboxMessageClass = NonNullable&lt;components['schemas']['GmailInboxMessageResponse']['messageClass']&gt;;` (or the equivalent path the regen emits — check `schema.d.ts` for the exact `messageClass` property type; with `allowableValues` it should emit a string-literal union which `NonNullable&lt;...&gt;` reduces to the four literals).
       - DELETE the hand-typed `messageClass?: InboxMessageClass | null` and `eventDt?: string | null` from the `GmailInboxMessageResponse` type at lines 22-26. Either remove the entire hand-written `type GmailInboxMessageResponse` (preferred — replace ALL uses with `components['schemas']['GmailInboxMessageResponse']`) OR keep the local alias but remove the redundant calendar fields. Pick whichever requires fewer downstream consumer edits; if the hand-typed alias is used many places, keep it but reduce to `export type GmailInboxMessageResponse = components['schemas']['GmailInboxMessageResponse'];`.
       - LEAVE the `InboxMessage` view-model (lines 50-67) untouched EXCEPT delete the comment "Phase 12 W4: optional because the projection→DTO wiring ships separately" — the wiring ships HERE and the field is no longer optional-because-deferred. Keep `messageClass?` as nullable on the view-model (the field is genuinely nullable for non-calendar rows).
       - `normalizeMessage` lines 130-131 (`messageClass: message.messageClass ?? null,` + `eventDt: message.eventDt ?? null,`) STAY — `??` already handles the wire `null`/`undefined` correctly.

    VITEST:
    7. Create `apps/web/__tests__/inbox/inbox-page-client-cancel-badge.test.tsx`:
       - Render `InboxPageClient` (or the smaller `InboxMessageRow` if it is exportable; check the file — if not exportable, render the full page client with a single-row mocked `useInboxPage` return).
       - Mock `useInboxPage` (TanStack Query hook) to return one row with `messageClass: 'CANCEL'`, `eventDt: '2026-06-25T15:00:00Z'`, `subject: 'Test cancel'`, `from: 'tester@example.com'`, all other required fields set.
       - Use `next-intl` test wrapper from existing precedent `__tests__/calendar/use-calendar-connections.test.tsx` (look for `IntlProvider` or similar test util pattern).
       - Assert `screen.getByTestId('inbox-message-cancellation-badge')` is in the document and `toBeVisible()`.
       - Add a second test case for `messageClass: 'RESCHEDULE'` → `inbox-message-time-changed-badge` visible.
       - Add a third negative case: a row with `messageClass: null` → neither badge testid is in the document.

    PLAYWRIGHT:
    8. Create `apps/web/e2e/inbox-calendar-badge.spec.ts`:
       - Follow the structure of `apps/web/e2e/calendar-settings.spec.ts`: import chrome-test-utils, set up auth + tenant mocks, then mock `**/api/gmail/inbox*` to return a `GmailInboxPageResponse` with one CANCEL row + one normal row.
       - Navigate to `/inbox` (or whatever the inbox route is — check `apps/web/app/(protected)/(app)/inbox/` directory for the route).
       - Wait for the inbox list to render.
       - Assert `page.getByTestId('inbox-message-cancellation-badge')` is visible.
       - Take a screenshot named `inbox-calendar-cancel-badge.png` for human visual verification.
       - Add a second scenario for the RESCHEDULE row asserting `inbox-message-time-changed-badge`.

    Constraints:
    - apps/web/AGENTS.md MANDATORY: `schema.d.ts` regenerated by the script ONLY. Zero hand-edits permitted to that file or `zero-mail-spec.json`.
    - The Playwright spec runs against the local dev server. If the dev server is not reachable in the executor session, ship the spec as a durable gate (precedent: W3 SUMMARY did this for `calendar-settings.spec.ts`) and document the deferred auto-run in the SUMMARY.
    - Do NOT remove the `// Phase 12 W4` comment in `normalizeMessage` (lines 130-131) — those lines stay; just delete the W4-defer comment above the field declarations.
    - Do NOT touch `InboxPageClient.tsx` lines 587-604 — those branches are already correct. The whole point of this task is to make the wire data show up so they fire.
  </action>
  <verify>
    <automated>pnpm --filter web run typecheck &amp;&amp; pnpm --filter web exec eslint features/inbox __tests__/inbox e2e/inbox-calendar-badge.spec.ts &amp;&amp; pnpm --filter web exec vitest run __tests__/inbox/inbox-page-client-cancel-badge.test.tsx</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "messageClass" apps/web/lib/api/schema.d.ts` returns ≥1 (was 0 — regen happened).
    - `grep -c "eventDt" apps/web/lib/api/schema.d.ts` returns ≥1.
    - `grep -cE "^export type InboxMessageClass = '(INVITE|CANCEL|RESCHEDULE|RSVP)'" apps/web/features/inbox/api/inbox-api.ts` returns 0 (hand-typed literal union deleted).
    - `grep -c "components\\['schemas'\\]\\['GmailInboxMessageResponse'\\]" apps/web/features/inbox/api/inbox-api.ts` returns ≥1 (re-export from generated schema present).
    - `apps/web/__tests__/inbox/inbox-page-client-cancel-badge.test.tsx` exists; Vitest passes 3 cases (CANCEL visible, RESCHEDULE visible, null absent).
    - `apps/web/e2e/inbox-calendar-badge.spec.ts` exists; spec is syntactically valid (parses without error in Playwright); auto-run optional in executor session if dev server unreachable (document defer in SUMMARY).
    - `pnpm --filter web run typecheck` is green for the inbox surface (a pre-existing `lib/content/frontmatter.ts` yaml error is allowed and NOT regressed — note it but do not fix in this plan).
    - `pnpm --filter web exec eslint features/inbox __tests__/inbox e2e/inbox-calendar-badge.spec.ts` returns 0 errors.
    - `apps/web/features/inbox/components/InboxPageClient.tsx` UNCHANGED by this task (verify with `git diff --stat apps/web/features/inbox/components/InboxPageClient.tsx` showing no edits).
  </acceptance_criteria>
  <done>
    Regenerated `schema.d.ts` carries the two new fields; the hand-typed `InboxMessageClass` literal union and `messageClass?`/`eventDt?` field declarations in `inbox-api.ts` are deleted and replaced by re-exports from the generated schema; three Vitest cases prove the CANCEL/RESCHEDULE/null branches; one Playwright spec exists as a durable gate (auto-run if dev server reachable, otherwise documented defer); typecheck + lint + Vitest all green for the inbox surface.
  </done>
</task>

<task type="auto" tdd="false">
  <name>Task 4 (OPTIONAL, recommended): Collapse W3 deferral — swap apps/web/features/calendar/types.ts to generated schema; promote calendar-api.ts to typed api.* client</name>
  <files>
    apps/web/features/calendar/types.ts,
    apps/web/features/calendar/api/calendar-api.ts,
    apps/web/features/calendar/hooks/use-calendar-connections.ts,
    apps/web/features/calendar/hooks/use-disconnect-calendar-connection.ts,
    apps/web/features/calendar/hooks/use-toggle-calendar.ts,
    apps/web/features/calendar/hooks/use-update-calendar-preference.ts,
    apps/web/features/calendar/hooks/use-connect-calendar-intent.ts
  </files>
  <read_first>
    - .planning/phases/12-calendar-connection-triage-foundation/12-W3-calendar-settings-frontend-SUMMARY.md "Output Contract (a)" — the 7-step swap procedure documented by the W3 executor
    - apps/web/features/calendar/types.ts — the transitional shim with `TODO(12-W3)` markers
    - apps/web/features/calendar/api/calendar-api.ts — raw-fetch wrappers using `xsrfHeader()` + `getApiUrl()`
    - apps/web/lib/api/client.ts — typed `api` openapi-fetch client + `xsrfHeader` middleware contract (CSRF echo handled by the middleware on the typed path)
    - apps/web/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api.ts — precedent the W3 executor cited for raw-fetch fallback shape (compare to the typed shape used elsewhere)
  </read_first>
  <action>
    This task is OPTIONAL but recommended. It bundles cleanly with Task 3 because the schema regen has already happened and the dev tunnel is already up — running this task here costs ~15 minutes of pure mechanical work and removes the eight `TODO(12-W3)` markers across the calendar feature. The user may drop this task if scope is tight (the calendar settings UI already works end-to-end with the shim per W3 verification).

    Decision rule: include this task in the PR if Task 3 ran the regen successfully. Skip if Task 3 deferred the regen (no point swapping if the regen did not land).

    1. Verify Task 3's regen wrote the eight calendar schemas into `schema.d.ts`: `grep -E "CalendarConnectionResponse|CalendarSubResponse|MailboxCalendarPreferenceResponse|UpdateCalendarEnabledRequest|UpdateMailboxCalendarPreferenceRequest|CalendarToggleResponse|CalendarConnectIntentRequest|CalendarConnectIntentResponse" apps/web/lib/api/schema.d.ts` should return ≥8. Also verify the five paths: `grep -E "/api/calendar/(mailboxes|connect-intent|connections)" apps/web/lib/api/schema.d.ts` should return ≥5. If any are missing, the W3 backend DTOs are not on the booted instance — STOP and report.

    2. Rewrite `apps/web/features/calendar/types.ts` to re-export from the generated schema. Replace the hand-derived bodies with:
       ```
       import type { components } from '@/lib/api/schema';
       export type CalendarConnection = components['schemas']['CalendarConnectionResponse'];
       export type CalendarSub = components['schemas']['CalendarSubResponse'];
       export type MailboxCalendarPreference = components['schemas']['MailboxCalendarPreferenceResponse'];
       export type UpdateCalendarEnabledRequest = components['schemas']['UpdateCalendarEnabledRequest'];
       export type UpdateMailboxCalendarPreferenceRequest = components['schemas']['UpdateMailboxCalendarPreferenceRequest'];
       export type CalendarToggleResponse = components['schemas']['CalendarToggleResponse'];
       export type CalendarConnectIntentRequest = components['schemas']['CalendarConnectIntentRequest'];
       export type CalendarConnectIntentResponse = components['schemas']['CalendarConnectIntentResponse'];
       ```
       Delete every `TODO(12-W3)` marker and the leading explanatory comment. The W3 SUMMARY's seventh step says "delete the file entirely" if all consumers shift to direct `components['schemas'][...]` imports — pick whichever is less churn for the 6 component files and 5 hook files; preserving the named aliases is the less-churn option.

    3. Rewrite `apps/web/features/calendar/api/calendar-api.ts` from raw `fetch` + `xsrfHeader()` to typed `api.GET / api.POST / api.PATCH / api.DELETE` from `@/lib/api/client`. The `onRequest` middleware in `client.ts` echoes the XSRF cookie automatically for mutating methods, so the explicit `xsrfHeader()` becomes dead code. Drop the import + every call site. Keep the function names + signatures (callers in hooks already depend on them).

    4. For each of the five hooks (`use-calendar-connections`, `use-disconnect-calendar-connection`, `use-toggle-calendar`, `use-update-calendar-preference`, `use-connect-calendar-intent`), verify their TanStack Query usage still typechecks against the new typed responses. The TanStack `meta.successMessage` / `meta.errorMessage` keys stay unchanged. If a hook references a type re-exported through `types.ts`, no import change needed — the alias is still there.

    5. Re-run the feature's existing Vitest spec: `pnpm --filter web exec vitest run __tests__/calendar/`. Both existing cases must stay green (the only change is the underlying fetch transport; the assertions are about hook behavior).

    Constraints:
    - This task is optional. If the executor reaches Task 4 and the regen from Task 3 did NOT happen (deferred), SKIP Task 4 and document the skip in the SUMMARY with a note that the calendar feature swap remains a `TODO(12-W3)` follow-up — closing it without the regen is impossible.
    - Do NOT change the URL paths or HTTP methods in `calendar-api.ts`. The path + method must match the regenerated `paths` exactly or `api.GET / POST` typecheck will fail.
    - Do NOT touch `apps/web/features/calendar/components/*.tsx` or the route file. The components consume the named type aliases from `types.ts`, which are unchanged in NAME (only their definition source changes from hand-derived to generated).
    - Do NOT touch the existing Playwright `apps/web/e2e/calendar-settings.spec.ts` — it tests the user flow, not the type layer.
  </action>
  <verify>
    <automated>pnpm --filter web run typecheck &amp;&amp; pnpm --filter web exec eslint features/calendar &amp;&amp; pnpm --filter web exec vitest run __tests__/calendar/</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c "TODO(12-W3)" apps/web/features/calendar/types.ts apps/web/features/calendar/api/calendar-api.ts apps/web/features/calendar/hooks/*.ts` returns 0 (all W3 deferral markers cleared).
    - `grep -c "import type { components }" apps/web/features/calendar/types.ts` returns 1.
    - `grep -c "from '@/lib/api/client'" apps/web/features/calendar/api/calendar-api.ts` returns ≥1 (typed client imported).
    - `grep -c "xsrfHeader\\(\\)" apps/web/features/calendar/api/calendar-api.ts` returns 0 (raw-fetch CSRF echo dropped; middleware handles it on the typed path).
    - `grep -c "^\\s*const response = await fetch\\(getApiUrl" apps/web/features/calendar/api/calendar-api.ts` returns 0 (raw-fetch call sites removed).
    - `__tests__/calendar/use-calendar-connections.test.tsx` still passes (2/2 cases).
    - `pnpm --filter web run typecheck` green for the calendar surface (pre-existing `frontmatter.ts` yaml error tolerated and unchanged).
    - `pnpm --filter web exec eslint features/calendar` returns 0 errors.
  </acceptance_criteria>
  <done>
    `apps/web/features/calendar/types.ts` re-exports from the generated schema with zero hand-derived type bodies; `calendar-api.ts` uses the typed `api.GET/POST/PATCH/DELETE` client with no raw `fetch` or `xsrfHeader()` call sites; the five hooks still typecheck unchanged; existing Vitest + lint + typecheck stay green; eight `TODO(12-W3)` markers are gone; OR (if Task 3 deferred the regen) the task is skipped and the SUMMARY documents the dependency.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| projection table → API response | Calendar classification crosses the read-side boundary; must NOT leak raw body content (only the enum + timestamp) |
| backend wire → frontend type | OpenAPI regen is the only sanctioned channel; hand-edits to `schema.d.ts` would silently drift |
| live-Gmail path → API response | Live-Gmail fallback path has NO projection columns; must NOT fabricate or infer classification from message content (would re-introduce a parsing surface outside the W4 worker) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-12-G1-01 | Information Disclosure | GmailInboxMessageResponse | mitigate | Only `messageClass` (closed enum) + `eventDt` (calendar event timestamp from VEVENT DTSTART, already persisted by W4) cross the wire. No new body content, no attendee email, no SUMMARY string. `@Schema(allowableValues = {...})` constrains the enum; the existing Privacy logging convention (CLAUDE.md §5) already bans logging the response payload. No new log line added by this plan. |
| T-12-G1-02 | Tampering | apps/web/lib/api/schema.d.ts | mitigate | apps/web/AGENTS.md MANDATORY rule: `schema.d.ts` is generated, never hand-edited. The Task 3 acceptance criteria verify the file came from the regen pipeline (presence of new fields) and reject hand-edited drift. The `inbox-api.ts` swap re-exports from `components['schemas']` so any future backend DTO change auto-propagates after the next regen. |
| T-12-G1-03 | Tampering | Live-Gmail fallback path returning fabricated `messageClass` | mitigate | `RecentInboxReadService.toRecentInboxMessage` (line 807, live-Gmail path) explicitly passes `null, null` for the two new fields with a one-line documenting comment. ArchUnit / unit test cannot easily pin this seam (it is a single static method), but the verifier's `grep -c "messageClass" RecentInboxReadService.java ≥3` acceptance criterion (projection-path + live-path + record-field) gates against accidental fabrication via copy-paste. |
| T-12-G1-04 | Repudiation | None new | accept | No new audit surface introduced. Calendar classification was already auditable via the projection write path (W4 classifier logs `event=calendar_classified`); read-side does not need its own audit row. |
| T-12-G1-SC | Tampering (supply chain) | npm / Maven installs | accept | This plan adds ZERO new packages on either backend (`libs.versions.toml` untouched) or frontend (`package.json` untouched). No package-legitimacy gate needed. The pre-existing `yaml` module error is explicitly deferred to a separate PR. |
</threat_model>

<verification>
End-of-plan verification (run after all 4 tasks complete; defer Task 4's verification if Task 4 was skipped):

1. **Backend chain**: `cd backend && ./gradlew :backend:core:test :backend:api:test --tests "*InboxProjection*" --tests "*RecentInboxRead*" --tests "*GmailInboxMessageResponse*"` — all green.
2. **OpenAPI regen happened**: `grep -c "messageClass" apps/web/lib/api/schema.d.ts` returns ≥1 (was 0 before this plan).
3. **Hand-typed dedup**: `grep -cE "^export type InboxMessageClass = '" apps/web/features/inbox/api/inbox-api.ts` returns 0.
4. **Vitest green**: `pnpm --filter web exec vitest run __tests__/inbox/inbox-page-client-cancel-badge.test.tsx` — 3 cases pass.
5. **TypeScript + lint green**: `pnpm --filter web run typecheck && pnpm --filter web exec eslint features/inbox features/calendar __tests__/inbox e2e/inbox-calendar-badge.spec.ts` — 0 errors (pre-existing `lib/content/frontmatter.ts` yaml error tolerated, not regressed).
6. **W4 regression test stays green**: `./gradlew :backend:core:test --tests "com.zeromail.core.inbox.persistence.InboxProjectionPinningTest"` — all 3 (now 4) cases pass; the pin ORDER BY predicate is unchanged.
7. **Playwright spec is syntactically valid** (auto-run if dev server reachable): `pnpm --filter web exec playwright test e2e/inbox-calendar-badge.spec.ts --reporter=line` — passes if dev server is up; document defer in SUMMARY if not.
8. **JetBrains problem check**: `mcp__jetbrains__get_file_problems` on the four modified Java files reports 0 errors / 0 new warnings.

Defer signals that ARE acceptable:
- Playwright auto-run deferred (precedent: W3 SUMMARY) when dev server is not reachable in the executor session.
- Task 4 skipped if Task 3 deferred the OpenAPI regen.

Defer signals that ARE NOT acceptable (these BLOCK plan completion):
- OpenAPI regen deferred — without `schema.d.ts` carrying the new fields the frontend type-dedup cannot happen, and CAL-TRIAGE-02 stays open. If the SSH tunnel is unreachable, this becomes an Authentication Gate (block-and-resume), not a silent defer.
</verification>

<post_execution>
After Task 3 ships its commits (and Task 4 if it ran) and BEFORE writing SUMMARY.md, perform the REQUIREMENTS.md re-marking step. This is mandatory — silent overclaim of CAL-TRIAGE-02 is exactly the failure mode that caused this gap plan to exist, so the closure must own the edit explicitly.

- [ ] Edit `.planning/REQUIREMENTS.md` line 53: flip `[ ]` → `[x]` and replace the trailing deferred-closure note with: `_(Closed in Phase 12 G1: backend RecentInboxMessage + GmailInboxMessageResponse extended, schema.d.ts regenerated, Vitest + Playwright proof committed YYYY-MM-DD.)_` — substitute the actual closure date.
- [ ] Verify with: `grep -n "CAL-TRIAGE-02" .planning/REQUIREMENTS.md` — the first match must be the `[x]` line and the trailing note must reflect closure (NOT "Closure tracked in Phase 12.1 gaps").
- [ ] Commit this edit as part of the final docs commit alongside SUMMARY.md (do not slip it into a Task 1/2/3/4 commit; this is a docs-only post-execution step).
</post_execution>

<success_criteria>
- REQUIREMENTS.md `CAL-TRIAGE-02` flipped back to `[x]` with closure note rewritten per `<post_execution>` — the badge half is wired end-to-end.
- `apps/web/features/inbox/components/InboxPageClient.tsx:587-604` is no longer a dead branch — Playwright + Vitest both prove the Badge renders.
- `grep -c "messageClass" apps/web/lib/api/schema.d.ts` returns ≥1 (was 0 — the verification-time gap was the regenerated schema not carrying the field).
- `grep -c "TODO(12-W4)" apps/web/features/inbox/api/inbox-api.ts` returns 0 (the W4 deferral comment is gone because the wiring shipped).
- Optional bonus: 8 `TODO(12-W3)` markers cleared from `apps/web/features/calendar/**` (Task 4) if regen succeeded.
- W4 + W5 invariants preserved: `InboxProjectionPinningTest` green; `RuleEvaluatorCalendarPresetTest` green (the PRESET match path is unaffected by this read-side wiring); `CalendarMessageClassifierNoOAuthTest` green (worker classifier is unchanged).
- Privacy invariant respected: zero new long-term body persistence, zero new logging of email content or LLM I/O.
</success_criteria>

<output>
Create `.planning/phases/12-calendar-connection-triage-foundation/12-G1-cal-triage-02-badge-wiring-SUMMARY.md` when done. SUMMARY must document:

1. **Closure proof** for CAL-TRIAGE-02: backend chain wired, OpenAPI regen happened (or deferred-with-reason), Vitest + Playwright cases added, AND `.planning/REQUIREMENTS.md` line 53 flipped `[ ]` → `[x]` with the closure note rewritten per `<post_execution>` (cite the new `grep -n "CAL-TRIAGE-02" .planning/REQUIREMENTS.md` output).
2. **Whether Task 4 ran** (regen succeeded → ran; regen deferred → skipped with note).
3. **Authentication gate hits** if any (SSH tunnel availability for the regen step is the main risk).
4. **Any pre-existing issues touched in passing** (none expected; the `frontmatter.ts` yaml error is explicitly out of scope).
5. **Regenerated `schema.d.ts` symbol delta**: list of new properties on `GmailInboxMessageResponse` (should be exactly `messageClass: string | null` with the four-value enum + `eventDt: string | null` with `format=date-time`).
6. **Confirmation the W4 pin invariant is unchanged**: `InboxProjectionPinningTest` still 3 (or 4 with the new case) green, ORDER BY predicate untouched.
7. **Files modified** (split by backend / frontend / test) for the executor's commit grouping.
</output>

<deferred>
**NOT in scope for this gap plan (documented for handoff):**

1. **Pre-existing `apps/web/lib/content/frontmatter.ts` yaml-module error** (TS2307: Cannot find module 'yaml'). Introduced by commit `ab77689e`. Closure: `pnpm --filter web add yaml`. Should be a separate one-line PR. Mentioned in W3 + W4 SUMMARYs as a known pre-existing defect.

2. **`auto_explain` capture against `idx_inbox_projection_calendar_pin`** (W4 Known Stub #2). The partial index was created in W0 changeset 134; W4 functional test (`InboxProjectionPinningTest`) proves the pin behavior is correct but does not capture an EXPLAIN ANALYZE. Closure: a `postgres-mcp__analyze_query_indexes` run against the dev DB once Phase 12 verification re-runs. NOT a blocker for CAL-TRIAGE-02 closure.

3. **Live human verification items** from `12-VERIFICATION.md` `human_verification[]`: real Google Calendar OAuth consent screen showing the three calendar-only scopes; real Pub/Sub invite ingestion → pin behavior; disconnect cascade visible end-to-end; PRESET match in `/audit` with zero `llm_call_audit` rows. These require a live system + real Google account; they are tracked in VERIFICATION.md and stay tracked there.

4. **The latent `RecentInboxReadService` keyset-cursor invariant** (W4 Deviation #2): pinned rows can RE-APPEAR on page 2. The W4 executor documented this as a future read-service-level dedup (a small `Set&lt;gmailMessageId&gt;` skip-list across page fetches). NOT a CAL-TRIAGE-02 blocker — the pin still works correctly, just with possible duplicates after page 1. Should be closed in a future read-service refactor, not this gap plan.
</deferred>
