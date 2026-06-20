# Roadmap: Zero Mail

## Milestones

- ✅ **v1.0 MVP** — Phases 1, 1.1-1.6, 2A-2C, 3, 4, 5A-5C, 6 (shipped 2026-05-15) — see [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Email assistant chat** — Phase 7 only (shipped 2026-05-19) — see [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)
- ✅ **v1.2 Admin Console + User Settings UI** — Phases 8, 08.1, 9 (+ 08-bulk-unsubscribe) (shipped 2026-06-01) — see [milestones/v1.2-ROADMAP.md](milestones/v1.2-ROADMAP.md)
- ✅ **v1.3 Gmail Workspace Foundation** — Phases 10-11 (shipped 2026-06-16) — see [milestones/v1.3-ROADMAP.md](milestones/v1.3-ROADMAP.md)
- 🚧 **v1.4 Calendar Co-Pilot + Drive Filing** — Phases 12-16 (in progress, started 2026-06-17)

## Phases

<details>
<summary>✅ v1.0 MVP (shipped 2026-05-15) — 17 phases, 123 plans</summary>

Full details: [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md)

</details>

<details>
<summary>✅ v1.1 Email assistant chat (shipped 2026-05-19) — Phase 7 only</summary>

- [x] Phase 7: Chat Email Assistant — 6/6 plans, completed 2026-05-18

Full details: [milestones/v1.1-ROADMAP.md](milestones/v1.1-ROADMAP.md)

</details>

<details>
<summary>✅ v1.2 Admin Console + User Settings UI (shipped 2026-06-01) — 4 phases, 28 plans</summary>

- [x] Phase 8: Admin Console & Operator Tooling — 6/6 plans, completed 2026-05-20
- [x] Phase 08.1: Inbox Zero-style Rule Actions & Admin-managed Examples Catalog — 6/6 plans, completed 2026-05-25
- [x] Phase 08-bulk-unsubscribe: Bulk Unsubscribe Campaign (UNS-01..07) — shipped alongside v1.2
- [x] Phase 9: User Settings UI on Curated Catalog — 7/7 plans, completed 2026-05-29

70/73 v1.2 requirements complete; 3 deferred to v1.3 (SET-BEHV-05, SET-SAFE-02, SET-SAFE-03).

Full details: [milestones/v1.2-ROADMAP.md](milestones/v1.2-ROADMAP.md)

</details>

<details>
<summary>✅ v1.3 Gmail Workspace Foundation (shipped 2026-06-16) — 2 phases, 12 plans</summary>

- [x] Phase 10: Gmail Mailbox Foundation and Account Management — 6/6 plans, completed 2026-06-09
- [x] Phase 11: Mailbox-Scoped Ingestion, Automation, UI, and Verification — 6/6 plans, automated 2026-06-10, live-verified 2026-06-15

43/43 v1.3 requirements complete; live-verified via 11-UAT (10/10, two real Gmail mailboxes). Gmail-only workspace-shared, mailbox-isolated foundation. Microsoft, Zalo OA, CRM, full team collaboration, and the formal GA tag remain deferred.

Full details: [milestones/v1.3-ROADMAP.md](milestones/v1.3-ROADMAP.md)

</details>

### 🚧 v1.4 Calendar Co-Pilot + Drive Filing (In Progress)

**Milestone Goal:** Make Zero Mail handle the schedule + attachment workflow that pairs with email. v1.4 ships as **5 feature-pack phases** — each phase is one top-level user-visible Co-Pilot capability delivered end-to-end on the v1.3 multi-Gmail workspace baseline, not horizontally-sliced technical layers. Privacy posture preserved: attachment bytes never persisted, meeting-brief summary is the third explicit ARCH-02 carve-out, and the new OAuth scope ledger fails CI on any unapproved Google scope.

**Note on Spring AI 2.0.0 GA:** The version pin already moved from M6 → GA in commit `eb19ecbc` and a read-only audit (`research/SPRING-AI-2.0-MIGRATION.md`) found zero migration debt. `BeanOutputConverter<MeetingBriefSchema>` and `DefaultToolCallingManager` adoption are folded into Phase 16 (CAL-BRIEF-02), not split into a standalone migration phase.

**Note on CASA / formal GA tag:** Already deferred to v1.5+ via PROJECT.md "Explicitly deferred to v1.5+" and OPS-FUT-04. No v1.4 requirement re-states this. v1.4 does NOT close the GA gate.

- [ ] **Phase 12: Calendar Connection + Triage Foundation** — Multi-Google-Calendar incremental OAuth (`calendar.freebusy` + `calendar.events` + `calendar.readonly`) with shared Google client + dedicated `ClientRegistration`, the OAuth scope ledger + `OAuthScopeAllowListTest` ArchUnit allow-list (reused by every later phase), workspace-shared `calendar_connection` model with `mailbox_calendar_preference (mailbox_id, calendar_connection_id, role)`, AES-GCM token reuse via `OAuthTokenStore`, three-state connection state machine + cascade-revoke, plus `text/calendar` MIME parsing in Gmail ingestion that pins invites/cancellations/reschedules top-of-inbox and downgrades destructive rule actions to label-only via `CalendarAwareGuard` — all triage features ship without requiring any Calendar OAuth scope.
- [ ] **Phase 13: Calendar Intelligence — AI Availability + propose_meeting Rule Action** — Single-call-site `CalendarReadGateway` (ArchUnit-locked against direct `Calendar.Builder` use) with Redis 60s cache + `SETNX` single-flight + `quotaUser` sha256 + `calendarExpansionMax=50` + per-tenant 60/min rate cap; `UnifiedAvailabilityService` that unions free/busy across all `freebusy`-role calendars and returns 3–5 LLM-duration-inferred slots respecting business hours and timezone; draft-reply integration and chat-assistant `getCalendarAvailability` tool with booking-link fallback when the message asks only for a scheduling method; and the **autonomous `propose_meeting` rule action** wired as a two-stage compound (`CalendarReadGateway` then existing v1.2 `OutboundSendGateway.sendReply(...)`) that inherits every existing outbound gate unchanged (Auto-send setting, safety net, rate cap, idempotency, audit, "blocked = failed audit, NO draft fallback").
- [ ] **Phase 14: Booking Links + Public Booking Page** — One personal Calendly-style booking link with slug (≥12 chars + random suffix), duration (15/30/45/60), slot interval, weekly availability windows, minimum notice, max days ahead, and location type (Google Meet / phone / in-person / custom); public `/book/{slug}` served by a sessionless `@Order(40)` Spring Security filter chain isolated from the v1.3 user chain `@Order(50)` and v1.2 admin chain `@Order(1)`; separate `CalendarOutboundGateway` for `events.insert` with `conferenceDataVersion=1` Google Meet creation (NOT the Gmail `OutboundSendGateway`); hCaptcha + `Idempotency-Key` + multi-bucket Redis token-buckets (per-IP 3/h, per-slug 10/h, per-attendee 5/day, platform-wide 1000/h); Postgres `UNIQUE (destination_calendar_id, starts_at)` as the source of truth for double-booking prevention with Redis `booking:slot-lock` as a 30s UX cushion only; `robots.txt` Disallow + `X-Robots-Tag: noindex`; idempotent Gmail confirmation + Google Calendar invite delivery; and an operator dashboard for booking-abuse counters.
- [ ] **Phase 15: Google Drive Integration — Connection + Filing Engine + Attachment-Source Rules** *(largest feature pack — Drive ships end-to-end in one milestone phase by design; do not split)* — Drive OAuth on `drive.file` ONLY (enforced by the Phase 12 scope ledger), workspace-shared `drive_connection` (no `gmail_connection_id` FK), AES-GCM token reuse, Google Picker-driven `filing_folders` selection accepting that `drive.file` makes Picker the only legal folder browser, and disconnect cascade-revoke (folders + sources + engine stop) retaining `document_filing` audit; **AI document auto-filing engine** with in-memory streaming (`Gmail.attachments.get().executeMediaAsInputStream()` → `InputStreamContent` → `Drive.files.create()` in one try-with-resources block — bytes never hit disk, `byte[]`, or DB), metadata-only AI analysis (NO content extraction, NO OCR, NO Tika/PDFBox/poi-ooxml dependency added), confidence + ASK-USER queue with `filing_auto_threshold=0.85` default + 7-day auto-discard, dedicated bounded `FilingExecutor` (`maxConcurrent=4`), size guards (>10MB skip AI, >25MB skip entirely), `AttachmentBytesCarrier` package-private `AutoCloseable` wrapper, `AttachmentBytesNotPersistedRule` ArchUnit composite (fails CI on `attachment_bytes|body_bytes|*Plaintext` outside the carrier package), metadata-only `document_filing` schema with status enum, filing activity UI with correct/reject + sender-level learning metadata-only, and in-app + email notifications that NEVER contain attachment body text; plus **Mode A static-pin attachment-source rules** — `attachment_source` schema referencing one or more Drive `fileId`s pinned via Picker at rule create/edit time, new `attach_from_source` rule action that streams files fresh from Drive at send time and assembles raw RFC 2822 routed through the existing Gmail `OutboundSendGateway` (one-Gmail-send-call-site invariant preserved), and audit recording attached fileIds + `SOURCE_FILE_UNAVAILABLE` failure mode with NO partial send.
- [ ] **Phase 16: AI Meeting Briefs — Cron + Agentic Loop** — `MeetingBriefScheduler` ShedLock cron firing every 5 minutes within the user's configured lead-time window (default 24h, tenant-configurable 1–72) finding events with at least one external attendee and enqueuing `processing_job` rows; agentic Spring AI loop in `backend/worker` driven by an explicit `DefaultToolCallingManager` (NOT the auto-loop `ToolCallingAdvisor`) so per-iteration budget checks (token cap, wall-clock cap, BYOK USD cap, brief-credit cap) land between iterations with `maxIterations=8`; only `past_emails(guestEmail)` and `past_meetings(guestEmail)` tools (no web search); structured brief output parsed via `BeanOutputConverter<MeetingBriefSchema>`; brief content generated at delivery time (NOT schedule time) with source email bodies in zero-on-close request-scoped buffers; `meeting_brief.summary_text` persisted as the third explicit ARCH-02 carve-out documented side-by-side in PROJECT.md with the v1.1 chat draft body carve-out and the v1.0 in-memory triage carve-out (NO `body_text`, NO `prompt`, NO `completion`, NO raw email column); email-only delivery via Resend + idempotency (in-app digest / Slack / Teams / Telegram / web push explicitly deferred to v1.5); premium per-tenant per-day brief credit cap (default 50/day, SEPARATE from LLM spend cap) + BYOK daily USD cap (default $5/day) both checked before the loop starts + configurable in tenant settings; tenant controls (global enable/disable, per-calendar `brief_source` role opt-in/out, brief queue preview, manual test brief); and failed-audit-with-reason on budget exhaustion (NO fallback brief, NO partial summary persisted).

## Phase Details

### Phase 12: Calendar Connection + Triage Foundation

**Goal**: User can connect one or more Google Calendars on minimal scopes and immediately see Gmail calendar invites/cancellations pinned top-of-inbox and guarded against destructive rule actions — even before any AI calendar feature ships. The OAuth scope ledger introduced here protects every later phase from accidentally requesting a restricted scope.
**Depends on**: v1.3 multi-Gmail mailbox foundation (Phases 10-11) + Spring AI 2.0.0 GA pin (pre-v1.4, already shipped via commit `eb19ecbc`).
**Requirements**: INFRA-01, CAL-CONN-01, CAL-CONN-02, CAL-CONN-03, CAL-CONN-04, CAL-CONN-05, CAL-CONN-06, CAL-CONN-07, CAL-CONN-08, CAL-TRIAGE-01, CAL-TRIAGE-02, CAL-TRIAGE-03, CAL-TRIAGE-04
**Success Criteria** (what must be TRUE):

  1. User can click "Connect Google Calendar" in settings (consent never requested implicitly during signup), complete an incremental OAuth grant on `calendar.freebusy` + `calendar.events` + `calendar.readonly` only — never a full `calendar` scope — and see the resulting workspace-shared connection with provider email, status, last sync, per-calendar enable/disable toggles, and a three-state state machine (`CONNECTED` / `DISCONNECTED` / `REVOKED`); disconnect cascade-revokes derived state (preferences, brief subscriptions, booking-link destination if applicable) while retaining audit.
  2. The `docs/oauth-scopes.md` ledger + `OAuthScopeAllowListTest` ArchUnit rule fail CI on any production code requesting a Google OAuth scope outside the approved set (catches a typo'd `drive`, `drive.readonly`, or full `calendar` before it reaches Google's consent screen) — and this ledger is the single source of truth re-used by Phase 15 to enforce `drive.file`-only.
  3. Gmail ingestion classifies messages with `text/calendar` MIME parts as `INVITE` / `CANCEL` / `RESCHEDULE` / `RSVP` and pins them top-of-inbox for 24h after the event date with "Cancellation" / "Time changed" badges — entirely message-side, working for every user regardless of whether they connect Google Calendar.
  4. Any rule whose evaluation would `archive`, `mark_spam`, or `delete` a calendar-class message is downgraded to `label` only, leaving a `CalendarAwareGuard` audit row with the original action + reason — protecting Gmail invites from silent loss by aggressive existing user rules.
  5. The workspace-shared / mailbox-isolated boundary holds: `calendar_connection` has no `gmail_connection_id` FK, the new `mailbox_calendar_preference (mailbox_id, calendar_connection_id, role ∈ {freebusy, event_write, brief_source})` table disambiguates which workspace-shared calendar each mailbox uses per role, refresh tokens are AES-GCM-encrypted via the existing `OAuthTokenStore` (never logged, never reused across connections), and mid-flight reads against a `DISCONNECTED` calendar fail fast emitting a Modulith event that evicts the free/busy cache.

**Plans**: 2/6 plans executed

- [x] 12-W0-oauth-scope-ledger-and-token-store-PLAN.md — `GoogleOAuthScope` enum + source-text scan + `OAuthTokenStore` facade + Liquibase 131-134 schema + ical4j/calendar-API deps.
- [x] 12-W1-calendar-oauth-and-connection-bootstrap-PLAN.md — `google-calendar` `ClientRegistration` + AuthZ resolver branch + `CalendarOAuthSuccessHandler` + `core.calendar.{domain,exception,persistence,gateway}` entities + `CalendarApiClientFactory`.
- [ ] 12-W2-calendar-connection-service-and-cascade-PLAN.md — `CalendarConnectionService` (list / disconnect cascade) + `CalendarSnapshotIngestionService` (D-06 default primary-calendar role enrollment) + `MailboxCalendarPreferenceService` + `CalendarToggleService` + `CalendarConnectionDisconnected` Modulith event + REST controllers + record DTOs.
- [ ] 12-W3-calendar-settings-frontend-PLAN.md — `/settings/mailboxes/[mailboxId]/calendar` route + IZ-style `CalendarConnectionsPanel/CalendarConnectionCard/CalendarList` + Calendly-style `RoleAssignmentSection` + `POST /api/calendar/connect-intent` (Phase 10 attributes-based intent mailbox stamping) + Playwright e2e.
- [ ] 12-W4-text-calendar-classification-and-pinning-PLAN.md — `MessageClass` enum + `gmail_inbox_projection.message_class/event_dt` columns + ical4j `CalendarPartParser` + worker `CalendarMessageClassifier` (`@TransactionalEventListener(AFTER_COMMIT)`) + pin-aware ORDER BY (derived `pin_until = event_dt + 24h`) + "Cancellation"/"Time changed" inbox badges.
- [ ] 12-W5-preset-calendar-rule-wiring-PLAN.md — `MatcherNode.PresetCalendarMatcher` sealed-interface permit + `RuleEvaluator` PRESET branch + `RuleEvaluationInput.messageClass()` plumb + Liquibase 135 data migration of seeded `system-calendar(-vi)` template + uncustomized materialized rules from `SEMANTIC_INTENT` → `PRESET_CALENDAR` (D-09 IZ pattern).

**UI hint**: yes

### Phase 13: Calendar Intelligence — AI Availability + propose_meeting Rule Action

**Goal**: When an inbound message asks for scheduling, AI draft reply and chat assistant propose 3–5 candidate slots drawn from the user's actual free/busy across all enabled calendars — and a new `propose_meeting` rule action can do the same autonomously, executed through the same outbound gateway and gates as every other v1.2 send/reply/forward rule.
**Depends on**: Phase 12 (calendar connections + per-mailbox preferences). Phase 14 may execute in parallel once `CalendarReadGateway` lands.
**Requirements**: CAL-AVAIL-01, CAL-AVAIL-02, CAL-AVAIL-03, CAL-AVAIL-04, CAL-AVAIL-05, CAL-AVAIL-06, CAL-RULE-01, CAL-RULE-02, CAL-RULE-03, CAL-RULE-04
**Success Criteria** (what must be TRUE):

  1. `CalendarReadGateway` is the single legal call site for `freebusy.query` and event reads — ArchUnit fails any direct `Calendar.Builder` / `freebusy.query` / event-read call site outside the gateway package — and free/busy responses are cached per `(connectionId, dayBucket)` for 60 seconds in Redis with `SETNX` single-flight deduplication, `quotaUser=sha256(tenantId:calendarId)` always set, `calendarExpansionMax=50` enforced, and a per-tenant 60/minute outbound free/busy rate cap; no `calendar_event_cache` table exists.
  2. When a user clicks "AI draft reply" on a message requesting a meeting, the draft contains 3–5 candidate slots produced by `UnifiedAvailabilityService` that union free/busy across all calendars enabled for the mailbox's `freebusy`-role preferences with LLM-inferred meeting duration, business-hours respect, and the user's timezone; when the user has a booking link configured AND the inbound message asks only for a scheduling method (not specific times), the draft routes recipients to the booking link instead of proposing specific slots.
  3. Chat assistant exposes a tenant-safe `getCalendarAvailability(durationMinutes, dayWindow)` tool that wraps `UnifiedAvailabilityService` and returns the same slot shape; the assistant uses it only when the conversation explicitly asks about scheduling.
  4. The rule action catalog gains `propose_meeting` with structured arguments (`durationMinutes`, `windowDays`, `tone`) extracted by the LLM rule compiler via Spring AI structured output ONLY — never via regex, substring matching, or accent-insensitive keyword inference; the action is a two-stage compound where stage 1 calls `CalendarReadGateway` and stage 2 calls the existing v1.2 `OutboundSendGateway.sendReply(...)` with an assistant-authored draft body containing the suggested slots.
  5. Stage 2 inherits — without modification — every v1.2 outbound gate (global `Auto-send rules` setting, sender safety net, per-tenant outbound rate cap, idempotency, ArchUnit-locked outbound gateway boundary, append-only audit); when blocked (safety net, rate cap, idempotency collision) or failed (Gmail 5xx) the outcome is recorded as a failed audit with the reason and NO Gmail draft fallback is created — consistent with the v1.3 product decision for send/forward.

**Plans**: TBD

### Phase 14: Booking Links + Public Booking Page

**Goal**: User has one personal Calendly-style booking link that external attendees can visit to book a meeting; the public page is sessionless, abuse-resistant, and writes events into the user's chosen calendar with a Google Meet link — through a separate calendar outbound gateway that does NOT compromise the v1.2 one-Gmail-send-call-site invariant.
**Depends on**: Phase 12 (calendar connections). Parallel-able with the back half of Phase 13 once `CalendarReadGateway` lands (booking availability filter depends on free/busy).
**Requirements**: CAL-BOOK-01, CAL-BOOK-02, CAL-BOOK-03, CAL-BOOK-04, CAL-BOOK-05, CAL-BOOK-06, CAL-BOOK-07, CAL-BOOK-08, CAL-BOOK-09
**Success Criteria** (what must be TRUE):

  1. User can create one booking link with a slug (≥12 characters with a random suffix), duration (15/30/45/60 minutes), slot interval, weekly availability windows, minimum notice, max days ahead, and location type (Google Meet / phone / in-person / custom).
  2. Public `/book/{slug}` is served by a sessionless `@Order(40)` Spring Security filter chain isolated from the v1.3 user chain `@Order(50)` and v1.2 admin chain `@Order(1)`, with its own controller package + `PublicBookingChainIsolationTest` ArchUnit rule; `robots.txt` Disallow + `X-Robots-Tag: noindex` are emitted so slug discovery requires the URL.
  3. Every booking submission requires a valid hCaptcha (or Turnstile) token AND an `Idempotency-Key` header; per-IP (3/hour), per-slug (10/hour), per-attendee-email (5/day), and platform-wide (1000/hour) Redis token-buckets enforce abuse caps; an operator dashboard surfaces CAPTCHA failures, throttled requests, and attempted slot-collision counters.
  4. Booking confirmation writes the event via a separate `CalendarOutboundGateway` (NOT the Gmail `OutboundSendGateway` — preserving the v1.2 one-Gmail-send-call-site invariant) calling `events.insert` with `conferenceDataVersion=1` for Google Meet creation; Postgres `UNIQUE (destination_calendar_id, starts_at)` is the source of truth for double-booking prevention while Redis `booking:slot-lock` is a 30-second UX cushion only.
  5. Confirmation sends both a Gmail confirmation email (through the existing `OutboundSendGateway`, audited) AND a Google Calendar invite to the attendee; both actions are idempotent, and suggested slots respect free/busy across all `freebusy`-role calendars plus the destination calendar, minimum notice, and max days ahead.

**Plans**: TBD
**UI hint**: yes

### Phase 15: Google Drive Integration — Connection + Filing Engine + Attachment-Source Rules

**Goal**: User connects one workspace-shared Google Drive account on the minimal `drive.file` scope and gets a complete Drive-aware email workflow in one milestone phase — AI auto-files incoming attachments into Picker-chosen folders with confidence + review queue, rules can attach curated Drive files to outbound replies, and the entire pipeline keeps attachment bytes in memory only with an ArchUnit composite enforcing the invariant. This is the **largest phase by design** — Drive integration ships as one user-visible feature pack, not three independently-shippable slices.
**Effort hint**: largest phase (18 requirements — 5 connection + 9 filing engine + 4 attachment-source rules); shipped as one feature pack by product decision.
**Depends on**: Phase 12 (OAuth scope ledger reused to enforce `drive.file`-only).
**Requirements**: DRV-CONN-01, DRV-CONN-02, DRV-CONN-03, DRV-CONN-04, DRV-CONN-05, DRV-FILE-01, DRV-FILE-02, DRV-FILE-03, DRV-FILE-04, DRV-FILE-05, DRV-FILE-06, DRV-FILE-07, DRV-FILE-08, DRV-FILE-09, DRV-ATCH-01, DRV-ATCH-02, DRV-ATCH-03, DRV-ATCH-04
**Success Criteria** (what must be TRUE):

  1. User can click "Connect Google Drive" in settings (consent never requested implicitly during signup) and complete an OAuth grant on `drive.file` ONLY — no `drive`, no `drive.readonly`, no `drive.metadata.readonly` — enforced by the Phase 12 scope ledger and `OAuthScopeAllowListTest`; the `drive_connection` table has no `gmail_connection_id` FK (workspace-shared, not per-mailbox), per-connection refresh tokens are AES-GCM-encrypted via the existing `OAuthTokenStore`, and disconnecting Drive cascade-revokes `filing_folders` + `attachment_sources` + stops the filing engine while retaining `document_filing` audit. A CI integration test asserts `files.list` on a fresh Drive connection returns empty (proving the `drive.file` UX reality) and the user-facing folder picker is Picker-only with no Browser fallback.
  2. The filing engine analyses incoming Gmail attachments on `MailMessageObserved` using metadata only (filename, MIME type, size, sender, subject, prior filings for this sender) with NO content extraction, NO OCR, and no `PDFBox` / `Tika` / `poi-ooxml` dependency added; AI suggests a destination folder + confidence (0.0–1.0); above `filing_auto_threshold` (default 0.85, user-configurable) the attachment is auto-filed, below it enters the ASK-USER queue and auto-discards after 7 days.
  3. Attachment bytes flow `Gmail.users.messages.attachments.get(...).executeMediaAsInputStream()` → `InputStreamContent(mimeType, gmailStream)` → `Drive.files.create(...).execute()` in a single try-with-resources block per attachment — bytes never hit disk in `/tmp/`, never sit in a `byte[]` field, never enter a DB row — enforced by the `AttachmentBytesNotPersistedRule` ArchUnit composite (fails CI on any field/parameter/return type/column matching `attachment_bytes|body_bytes|*Plaintext` outside the `AttachmentBytesCarrier` package); a dedicated bounded `FilingExecutor` (`maxConcurrent=4`) isolates filing from the shared worker pool, attachments >10MB skip AI analysis (`confidence=0.0`, `status=PENDING_REVIEW`), and attachments >25MB are skipped entirely with a `SIZE_EXCEEDED` audit; `document_filing` persists metadata only (no content excerpt, no AI reasoning text, no extracted summary).
  4. User can view filing activity, correct a filing (move + persist sender-level learning metadata-only), and reject a filing (delete from Drive if confidence below 1.0 and we placed it); notifications deliver via in-app activity feed + email (each independently toggleable) and NEVER contain attachment body text — only filename + sender + folder path.
  5. A rule can declare an `attachment_source` containing one or more Drive `fileId`s selected via Google Picker at rule create/edit time (Mode A: static pin; Mode B explicitly deferred to v1.5); the new `attach_from_source` rule action attaches every referenced file by streaming fresh via `Drive.files.get().executeMediaAsInputStream()` at send time (never cached on disk or in memory beyond the request), assembles the outbound message as raw RFC 2822, and routes it through the existing Gmail `OutboundSendGateway` — preserving the v1.2 one-Gmail-send-call-site invariant; send audit records the attached `fileId`s + filenames, and a missing or inaccessible Drive file produces a failed audit with `SOURCE_FILE_UNAVAILABLE` with NO partial message sent.

**Plans**: TBD
**UI hint**: yes

### Phase 16: AI Meeting Briefs — Cron + Agentic Loop

**Goal**: A scheduled job generates an AI brief on each upcoming external meeting (guest context from past email + past meetings) and delivers it via email; the brief summary becomes the third explicit ARCH-02 carve-out documented in PROJECT.md side-by-side with the v1.1 chat draft body and the v1.0 in-memory triage carve-outs.
**Depends on**: Phase 13 (`CalendarReadGateway` for source events + free/busy interaction), Phase 15 (re-tests the existing send gateway pattern for the email delivery seam), Spring AI 2.0.0 GA `BeanOutputConverter` + `DefaultToolCallingManager`.
**Requirements**: INFRA-02, CAL-BRIEF-01, CAL-BRIEF-02, CAL-BRIEF-03, CAL-BRIEF-04, CAL-BRIEF-05, CAL-BRIEF-06, CAL-BRIEF-07, CAL-BRIEF-08
**Success Criteria** (what must be TRUE):

  1. PROJECT.md privacy section enumerates three named ARCH-02 carve-outs side-by-side — (a) v1.1 chat-assistant draft body (user-authored, persistable), (b) v1.4 meeting-brief summary (assistant-authored narrative, persistable, source bodies never persisted), (c) v1.0 triage (in-memory at process time only) — with the legal storage shape called out per carve-out; the `meeting_brief` row persists only `summary_text` (no `body_text`, no `prompt`, no `completion`, no raw email column), and `meeting_brief_audit` persists fingerprints + token counts + cost only — never prompt/completion text.
  2. `MeetingBriefScheduler` ShedLock cron runs every 5 minutes, finds calendar events within the user's configured lead-time (default 24h, tenant-configurable 1–72), filters for events with at least one external attendee, and enqueues a `processing_job` per event; brief content is generated at delivery time (NOT schedule time) and source email bodies live only in a request-scoped buffer that is zeroed on close.
  3. Brief generation runs in `backend/worker` as an agentic Spring AI tool-calling loop driven by an explicit `DefaultToolCallingManager` (NOT the auto-loop `ToolCallingAdvisor`) so per-iteration budget checks (token cap, wall-clock cap, BYOK USD cap, brief-credit cap) land between iterations with `maxIterations=8`; the only tools exposed are `past_emails(guestEmail)` and `past_meetings(guestEmail)` — no web search — and the structured brief output is parsed via `BeanOutputConverter<MeetingBriefSchema>`.
  4. Briefs deliver via email only in v1.4 through the existing Resend pipeline + idempotency machinery (in-app digest, Slack, Teams, Telegram, web push explicitly deferred); briefs are premium-gated by a per-tenant per-day brief credit cap (default 50/day, SEPARATE from the LLM spend cap) and a BYOK daily USD cap (default $5/day) — both checked before the loop starts and user-configurable in tenant settings.
  5. User can globally enable/disable meeting briefs per workspace, opt individual calendars in/out of brief-source role via `mailbox_calendar_preference.role=brief_source`, preview the upcoming brief queue, and send a manual test brief; a scheduled brief that cannot be generated within budget (iteration cap, token cap, BYOK cap, Resend fail) records a `FAILED` audit with the reason — no fallback brief is sent, no partial summary is persisted.

**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 12 → 13 → 14 → 15 → 16. Phase 14 may execute in parallel after Phase 13's `CalendarReadGateway` lands. Phase 15 depends on Phase 12 (scope ledger). Phase 16 depends on both Phase 13 (`CalendarReadGateway`) and Phase 15 (existing send gateway re-test pattern). Phase 15 is the largest phase by design and is intentionally NOT split — Google Drive ships as one user-visible feature pack.

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1-6 (collapsed) | v1.0 | 123/123 | Complete | 2026-05-15 |
| 7. Chat Email Assistant | v1.1 | 6/6 | Complete | 2026-05-18 |
| 8. Admin Console & Operator Tooling | v1.2 | 6/6 | Complete | 2026-05-20 |
| 08.1. Inbox Zero-style Rule Actions & Examples Catalog | v1.2 | 6/6 | Complete | 2026-05-25 |
| 08-bulk-unsubscribe. Bulk Unsubscribe Campaign | v1.2 | — | Complete | 2026-05 |
| 9. User Settings UI on Curated Catalog | v1.2 | 7/7 | Complete | 2026-05-29 |
| 10. Gmail Mailbox Foundation and Account Management | v1.3 | 6/6 | Complete | 2026-06-09 |
| 11. Mailbox-Scoped Ingestion, Automation, UI, and Verification | v1.3 | 6/6 | Complete | 2026-06-15 |
| 12. Calendar Connection + Triage Foundation | v1.4 | 2/6 | In Progress|  |
| 13. Calendar Intelligence — AI Availability + propose_meeting Rule Action | v1.4 | 0/TBD | Not started | - |
| 14. Booking Links + Public Booking Page | v1.4 | 0/TBD | Not started | - |
| 15. Google Drive Integration — Connection + Filing Engine + Attachment-Source Rules | v1.4 | 0/TBD | Not started | - |
| 16. AI Meeting Briefs — Cron + Agentic Loop | v1.4 | 0/TBD | Not started | - |

---

*v1.0 archived 2026-05-15. v1.1 archived 2026-05-19 (Phase 7 only). v1.2 archived 2026-06-01. v1.3 archived 2026-06-16. v1.4 re-decomposed 2026-06-17 into 5 feature-driven phases (12-16): each phase = one top-level user-visible Co-Pilot capability shipped end-to-end on the v1.3 multi-Gmail workspace baseline. The previous 8-phase horizontal decomposition was retired. Spring AI 2.0.0 GA migration was confirmed a no-op via pre-v1.4 audit and is not a v1.4 phase. INFRA-03 was deleted from REQUIREMENTS.md because CASA/GA-tag deferral is already documented in PROJECT.md "Explicitly deferred to v1.5+" and OPS-FUT-04. Total v1.4 requirements: 59. The formal GA tag remains deferred.*
