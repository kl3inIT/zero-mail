# Requirements: Zero Mail v1.4 Calendar Co-Pilot + Drive Filing

**Defined:** 2026-06-17
**Status:** Active (planning)
**Core Value:** AI auto-triage that users trust with their real Gmail inbox — extended in v1.4 to schedule + attachment workflows that pair with email.

## Scope Inputs

- **SEED-006** — calendar-scheduling-and-meeting-briefs. Promoted from dormant; calendar-aware email workflows are high-value for founders and busy professionals (per the original seed Why-This-Matters). Both Inbox Zero and Shortwave treat calendar as a natural extension of email.
- **Reference codebase (read-only):** Inbox Zero's Calendar + Drive feature surface at `../inbox-zero/apps/web/`. Used as product / UX parity reference; Java/Spring architecture is rebuilt, not ported. Schema names borrowed (`CalendarConnection`, `BookingLink`, `MeetingBriefing`, `DriveConnection`, `FilingFolder`, `DocumentFiling`, `AttachmentSource`); IZ's `AttachmentDocument {content, summary}` persistence is explicitly **rejected** as incompatible with ARCH-02.
- **Baseline assumed shipped (v1.0–v1.3):** Spring Boot 4.1 + Spring Modulith 2.1 + Spring AI 2.0.0 GA + multi-Gmail mailbox foundation + `MailboxContext` ScopedValue + `OAuthTokenStore` (AES-GCM) + single `LlmGateway` + single `OutboundSendGateway` + ARCH-02 privacy + `processing_job` Postgres queue + Liquibase YAML.
- **Locked product decisions made during requirement scoping (2026-06-17):**
  - One booking link per user in v1.4 (multi-link deferred to v1.5).
  - AI meeting briefs are premium-gated with a per-tenant per-day brief credit cap (default 50 briefs/day, separate from LLM spend cap) and a BYOK USD cap (default $5/day).
  - Brief delivery in v1.4 is **email only** (in-app digest section and web-push notifications deferred to v1.5).
  - Brief agentic loop tools restricted to `past_emails` + `past_meetings` only — **no web-search tool** in v1.4 (avoids picking a search vendor + cost surprise).
  - Both Calendar and Drive connections are **workspace-shared** (one logical connection per Google account, shared across all mailboxes in the workspace), NOT per-mailbox.

## v1.4 Requirements

### Infrastructure and Privacy Foundation

- [x] **INFRA-01**: A single source-of-truth OAuth scope ledger lives under version control; CI fails if any production code requests a Google OAuth scope that is not in the approved set, preventing accidental introduction of restricted scopes such as `drive`, `drive.readonly`, or full `calendar`.
- [ ] **INFRA-02**: PROJECT.md privacy section enumerates three named ARCH-02 carve-outs side-by-side — (a) v1.1 chat-assistant draft body (user-authored, persistable), (b) v1.4 meeting-brief summary (assistant-authored narrative, persistable, source bodies never persisted), and (c) v1.0 triage (in-memory at process time only) — with the legal storage shape called out per carve-out.

> **CASA / GA-tag note (NOT a v1.4 requirement):** The formal GA tag + CASA scope-verification refresh are already explicitly deferred via PROJECT.md "Explicitly deferred to v1.5+" and OPS-FUT-04; v1.4 inherits that deferral. No v1.4 requirement re-states this.

> **Prerequisite (NOT a v1.4 requirement):** Spring AI 2.0.0 GA migration (from the v1.3 baseline pin of 2.0.0-M6, plus evaluation of new GA features such as the tool-callback search tool) is treated as **pre-v1.4 work** and handled outside this milestone via a separate discuss-then-quick-task effort. v1.4 phases assume Spring AI GA is already live before Phase 12 starts.

### Calendar Connection Management

- [x] **CAL-CONN-01**: User can connect one or more Google Calendar accounts to their workspace via an explicit "Connect Google Calendar" action on a settings page; consent is never requested implicitly during signup or first-login flows.
- [x] **CAL-CONN-02**: Calendar OAuth is registered as a separate `ClientRegistration` from the v1.3 login bundle, requests only `calendar.freebusy` + `calendar.events` + `calendar.readonly` (no full `calendar` scope, ever), shares the existing Google Cloud OAuth client, and uses `include_granted_scopes=true` + `access_type=offline` + `prompt=consent`.
- [x] **CAL-CONN-03**: System stores per-connection encrypted OAuth tokens via the existing AES-GCM `OAuthTokenStore`; refresh tokens are never logged, never persisted in plaintext, and never reused across connections.
- [x] **CAL-CONN-04**: User can view all connected Google Calendar accounts with provider email, status, last sync, and per-calendar enable/disable toggles; user can disconnect a connection and the system cascade-revokes derived state (preferences, brief subscriptions, booking-link destination if applicable, audit retained).
- [x] **CAL-CONN-05**: Each `CalendarConnection` enumerates the calendars Google exposes for it (primary + secondary), each with a per-calendar `is_enabled` flag; only enabled calendars participate in free/busy and brief source data.
- [x] **CAL-CONN-06**: Calendar connections are workspace-shared (one logical row per Google account in the workspace), NOT per-mailbox; the `calendar_connection` table has no `gmail_connection_id` foreign key.
- [x] **CAL-CONN-07**: A `mailbox_calendar_preference (mailbox_id, calendar_connection_id, role ∈ {freebusy, event_write, brief_source})` table disambiguates which workspace-shared calendar a specific Gmail mailbox uses for each role, preventing personal-availability leakage into work-mailbox drafts.
- [x] **CAL-CONN-08**: Calendar connection has a three-state state machine (`CONNECTED` / `DISCONNECTED` / `REVOKED`); mid-flight reads against a `DISCONNECTED` calendar fail fast and emit a Modulith event that evicts the free/busy cache.

### Calendar Availability (Draft Reply + Chat)

- [ ] **CAL-AVAIL-01**: A `CalendarReadGateway` is the single legal call site for `freebusy.query` and event reads; ArchUnit fails any direct call to `Calendar.Builder` outside the gateway package.
- [ ] **CAL-AVAIL-02**: AI draft reply pipeline calls `UnifiedAvailabilityService` when an inbound message asks for scheduling; the service unions free/busy across all enabled calendars belonging to the mailbox's `freebusy`-role preferences, infers meeting duration from message context, respects business hours and the user's timezone, and returns 3–5 candidate slots.
- [ ] **CAL-AVAIL-03**: Free/busy results are cached per `(connectionId, dayBucket)` for 60 seconds in Redis with single-flight `SETNX` deduplication; `quotaUser=sha256(tenantId:calendarId)` always set; `calendarExpansionMax=50` enforced; per-tenant 60/minute outbound free/busy rate cap.
- [ ] **CAL-AVAIL-04**: Chat assistant gains a tenant-safe `getCalendarAvailability(durationMinutes, dayWindow)` tool that wraps `UnifiedAvailabilityService` and returns the same slot shape; assistant uses it only when the conversation explicitly asks about scheduling.
- [ ] **CAL-AVAIL-05**: Free/busy raw results are NEVER persisted (no `calendar_event_cache` table); the only derived persistence is the assistant-authored draft body containing the proposed slots, which falls under the v1.1 `draft_body` carve-out.
- [ ] **CAL-AVAIL-06**: When the user has a booking link configured AND the inbound message asks only for a scheduling method (not specific times), the draft preferentially routes recipients to the booking link instead of proposing specific slots.

### Calendar-Aware Triage

- [x] **CAL-TRIAGE-01**: Gmail ingestion parses `text/calendar` MIME parts and classifies messages as `INVITE` / `CANCEL` / `RESCHEDULE` / `RSVP`; classification persists on the existing mail-projection row, no new long-term body storage.
- [ ] **CAL-TRIAGE-02**: Calendar-class messages are pinned at top-of-inbox in the web UI for a 24-hour window after the event date, with explicit "Cancellation" / "Time changed" badges.
- [ ] **CAL-TRIAGE-03**: New tenants are seeded with a default `SystemType=CALENDAR` rule (action: `label "Calendar"`) that auto-matches messages classified as calendar-class via `isCalendarInvite` (`.ics` attachment OR `text/calendar` MIME OR `BEGIN:VCALENDAR` body marker). The Calendar rule runs as a `PRESET` match before AI matching; user-authored rules retain full action authority — no backend downgrade, no `CalendarAwareGuard`. Users may disable, edit, or delete the seeded Calendar rule like any other rule. (Revised 2026-06-20 from initial guard-downgrade design to follow Inbox Zero's proven pattern; see Phase 12 CONTEXT for rationale.)
- [ ] **CAL-TRIAGE-04**: Calendar-aware triage ships without requiring any Calendar OAuth scope — `text/calendar` parsing is purely message-side and works for all users regardless of whether they connect Google Calendar.

### Booking Links (Calendly-style, 1 per user)

- [ ] **CAL-BOOK-01**: User can create one personal booking link with a slug (≥12 characters, random suffix), duration (15 / 30 / 45 / 60 minutes), slot interval, weekly availability windows, minimum notice, max days ahead, and location type (Google Meet / phone / in-person / custom).
- [ ] **CAL-BOOK-02**: Booking link writes events through a separate `CalendarOutboundGateway` (NOT the Gmail `OutboundSendGateway`) that handles `events.insert` with `conferenceDataVersion=1` for Google Meet creation.
- [ ] **CAL-BOOK-03**: Postgres `UNIQUE (destination_calendar_id, starts_at)` is the source of truth for double-booking prevention; Redis `booking:slot-lock` is a 30-second UX cushion only.
- [ ] **CAL-BOOK-04**: Public `/book/{slug}` page is served by a sessionless `@Order(40)` Spring Security filter chain, isolated from the v1.3 user chain `@Order(50)` and v1.2 admin chain `@Order(1)`; the public chain has its own controller package + ArchUnit isolation test.
- [ ] **CAL-BOOK-05**: Every booking submission requires a valid hCaptcha (or Turnstile) token AND an `Idempotency-Key` header; per-IP (3/hour), per-slug (10/hour), per-attendee-email (5/day), and platform-wide (1000/hour) Redis token-buckets enforce abuse caps.
- [ ] **CAL-BOOK-06**: Booking link emits `robots.txt` Disallow + `X-Robots-Tag: noindex` headers; slug discovery requires the URL.
- [ ] **CAL-BOOK-07**: Booking-link availability respects free/busy across all calendars the user enabled for `freebusy` role plus the destination calendar; suggested slots filtered against minimum-notice and max-days-ahead.
- [ ] **CAL-BOOK-08**: Booking confirmation sends both a Gmail confirmation email (through existing `OutboundSendGateway`, audited) AND a Google Calendar invite to the attendee; both actions are idempotent.
- [ ] **CAL-BOOK-09**: An operator dashboard surfaces booking-abuse counters (CAPTCHA failures, per-IP-throttled requests, attempted slot collisions) for the global ops view.

### Rule Action: propose_meeting

- [ ] **CAL-RULE-01**: The rule action catalog gains a `propose_meeting` action with structured arguments (`durationMinutes`, `windowDays`, `tone`); the LLM rule compiler extracts these via structured output, never via regex or substring inference.
- [ ] **CAL-RULE-02**: `propose_meeting` is a two-stage compound action — stage 1 calls `CalendarReadGateway` for free/busy, stage 2 calls the existing `OutboundSendGateway.sendReply(...)` with the assistant-authored draft body containing suggested slots.
- [ ] **CAL-RULE-03**: Stage 2 inherits — without modification — every gate that already protects v1.2 outbound rule actions: global `Auto-send rules` setting, sender safety net, per-tenant outbound rate cap, idempotency, ArchUnit-locked outbound gateway boundary, append-only audit.
- [ ] **CAL-RULE-04**: When stage 2 is blocked (safety net, rate cap, idempotency collision) or fails (Gmail 5xx), the outcome is recorded as a failed audit with the reason — there is NO fallback to a Gmail draft (consistent with the v1.3 product decision for send/forward).

### AI Meeting Briefs

- [ ] **CAL-BRIEF-01**: A `MeetingBriefScheduler` ShedLock cron job runs every 5 minutes, finds calendar events within the user's configured lead-time window (default 24 hours, tenant-configurable 1–72), filters for events with at least one external attendee, and enqueues a `processing_job` per event.
- [ ] **CAL-BRIEF-02**: Brief generation runs in `backend/worker` as an agentic Spring AI tool-calling loop driven by an explicit `DefaultToolCallingManager` (NOT the `ToolCallingAdvisor` auto-loop) so per-iteration budget checks (token cap, wall-clock cap, BYOK USD cap, brief-credit cap) land between iterations; `maxIterations=8`; the only tools exposed are `past_emails(guestEmail)` and `past_meetings(guestEmail)` — no web-search tool. The structured brief output is parsed via `BeanOutputConverter<MeetingBriefSchema>` rather than ad-hoc JSON parsing.
- [ ] **CAL-BRIEF-03**: Brief content is generated at delivery time (NOT scheduled time); source email bodies live only in a request-scoped buffer that is zeroed on close; `meeting_brief_audit` persists fingerprints + token counts + cost only, never prompt/completion text.
- [ ] **CAL-BRIEF-04**: A `meeting_brief` row persists the assistant-authored `summary_text` (narrative bullets per guest); this row is the explicit v1.4 ARCH-02 carve-out, documented in INFRA-03 alongside the v1.1 chat draft body carve-out. There is no `body_text`, no `prompt`, no `completion`, no raw email content column.
- [ ] **CAL-BRIEF-05**: Briefs deliver via email only in v1.4, through the existing Resend pipeline + idempotency machinery; in-app digest, Slack, Teams, Telegram, and web push are explicitly deferred to v1.5+.
- [ ] **CAL-BRIEF-06**: Briefs are premium-gated by a per-tenant per-day brief credit cap (default 50 briefs/day) that is SEPARATE from the per-tenant LLM spend cap; BYOK tenants additionally hit a BYOK daily USD cap (default $5/day) before the loop starts; both caps are user-configurable in tenant settings.
- [ ] **CAL-BRIEF-07**: User can enable/disable meeting briefs globally per workspace; can opt individual calendars in or out of being a brief-source via `mailbox_calendar_preference.role=brief_source`; can preview the upcoming brief queue and send a manual test brief from settings.
- [ ] **CAL-BRIEF-08**: A scheduled brief that cannot be generated within budget (iteration cap hit, token cap hit, BYOK USD cap hit, Resend send fail) records a `FAILED` audit with the reason; no fallback brief is sent, no partial summary is persisted.

### Drive Connection Management

- [ ] **DRV-CONN-01**: User can connect one Google Drive account to their workspace via an explicit "Connect Google Drive" action on a settings page; consent is never requested implicitly during signup.
- [ ] **DRV-CONN-02**: Drive OAuth registers as a separate `ClientRegistration` and requests `drive.file` ONLY — no `drive`, no `drive.readonly`, no `drive.metadata.readonly`. This is enforced by the INFRA-02 scope ledger.
- [ ] **DRV-CONN-03**: Drive connection is workspace-shared (one connection per workspace, NOT per Gmail mailbox); the `drive_connection` table has no `gmail_connection_id` foreign key.
- [ ] **DRV-CONN-04**: System stores per-connection encrypted OAuth tokens via the existing AES-GCM `OAuthTokenStore`; never logged, never persisted in plaintext, never reused across connections.
- [ ] **DRV-CONN-05**: User can disconnect Drive; system cascade-revokes filing folders, attachment sources, and stops the filing engine for that workspace; `document_filing` audit retained.

### AI Document Auto-Filing

- [ ] **DRV-FILE-01**: User selects destination folders via the Google Picker (`drive.file` scope reality: `files.list` does NOT enumerate user's existing folders, Picker is the only legal browse UI); selected folders are persisted as `filing_folders` rows with the Picker-returned `folderId`.
- [ ] **DRV-FILE-02**: Filing engine analyzes incoming Gmail attachments on `MailMessageObserved` for tenants with `filing_enabled = true`; analysis input is metadata only (filename, MIME type, size, sender, subject, prior filings for this sender) — NO attachment content text is extracted, NO OCR is performed, NO `PDFBox`/`Tika`/`poi-ooxml` dependency is added.
- [ ] **DRV-FILE-03**: AI suggests destination folder + confidence score (0.0–1.0); above the user-configurable `filing_auto_threshold` (default 0.85), the attachment is filed automatically; below, the attachment enters an ASK-USER queue for explicit review and is auto-discarded from the queue after 7 days.
- [ ] **DRV-FILE-04**: Attachment bytes flow `Gmail.users.messages.attachments.get(...).executeMediaAsInputStream()` → `InputStreamContent(mimeType, gmailStream)` → `Drive.files.create(...).execute()` in a single try-with-resources block per attachment; bytes never hit disk in `/tmp/`, never sit in a `byte[]` field, never enter a DB row.
- [ ] **DRV-FILE-05**: An `AttachmentBytesCarrier` package-private + `AutoCloseable` wrapper carries the input stream within the engine; close zeroes any sensitive buffers it owns. An ArchUnit composite test (`AttachmentBytesNotPersistedRule`) fails CI on any field, parameter, return type, or DB column whose declared name matches `attachment_bytes|body_bytes|*Plaintext` outside the carrier package.
- [ ] **DRV-FILE-06**: `document_filing` schema persists ONLY metadata: `tenant_id, mailbox_id, message_id, attachment_filename, mime_type, size_bytes, suggested_folder_id, suggested_folder_path, confidence, status ∈ {FILED, PENDING_REVIEW, REJECTED, ERROR, EXPIRED}, filed_at, drive_file_id, sha256` — no content excerpt, no AI reasoning text, no extracted summary.
- [ ] **DRV-FILE-07**: A dedicated bounded `FilingExecutor` (`maxConcurrent=4`) isolates filing work from the shared worker pool; attachments >10MB skip AI analysis (stream straight through with `confidence=0.0` and `status=PENDING_REVIEW`); attachments >25MB are skipped entirely with a `SIZE_EXCEEDED` audit.
- [ ] **DRV-FILE-08**: User can view filing activity (recent filings + status + confidence + manual override), correct a filing (move to a different folder — correction persists for sender-level learning, metadata-only), and reject a filing (deletes from Drive if confidence below 1.0 and we placed it).
- [ ] **DRV-FILE-09**: Filing notifications deliver via in-app activity feed + email (Resend); user can toggle each independently. Notifications NEVER contain attachment body text — only filename + sender + folder path.

### Attachment-Source Rules (Mode A: Static Pin)

- [ ] **DRV-ATCH-01**: A rule can declare an `attachment_source` containing one or more Drive file references (`fileId`s) selected via Google Picker at rule create/edit time (Mode A: static pin). Mode B (smart pick by filename + metadata) is explicitly deferred to v1.5.
- [ ] **DRV-ATCH-02**: A new `attach_from_source` rule action attaches every file referenced by the rule's `attachment_source` to the outbound reply; files are fetched fresh from Drive via `Drive.files.get().executeMediaAsInputStream()` at send time, never cached on disk or in memory beyond the request.
- [ ] **DRV-ATCH-03**: The outbound message is assembled as raw RFC 2822 with the streamed attachment parts and sent through the existing Gmail `OutboundSendGateway` — preserving the v1.2 one-Gmail-send-call-site invariant.
- [ ] **DRV-ATCH-04**: Send audit records the attached `fileId`s and filenames; if a referenced Drive file is missing or inaccessible at send time, the rule action records a failed audit with `SOURCE_FILE_UNAVAILABLE` and does NOT send a partial message.

## Future Requirements

### Deferred to v1.5+

**Calendar:**

- Multi booking links per user (CAL-BOOK-FUT-01)
- Booking link custom branding / buffer-between-meetings / pre-meeting question fields (CAL-BOOK-FUT-02)
- One-click invite accept/decline from triage UI (CAL-TRIAGE-FUT-01)
- Calendar push notifications via `events.watch` instead of polling (CAL-AVAIL-FUT-01)
- Multi-attendee availability / round-robin / paid bookings / iframe widget (CAL-BOOK-FUT-03)
- In-app digest section + web-push delivery for meeting briefs (CAL-BRIEF-FUT-01)
- Web-search tool in brief loop (CAL-BRIEF-FUT-02 — gated by vendor decision)

**Drive:**

- Multiple Drive connections per workspace (DRV-CONN-FUT-01)
- Attachment-source Mode B (smart pick from a curated folder by filename + Drive metadata; in-memory only) (DRV-ATCH-FUT-01)
- AI document analysis with content extraction (would require OCR/Tika + ARCH-02 carve-out, NOT planned) (DRV-FILE-FUT-01)

**GA gate (still outstanding after v1.3):**

- OPS-FUT-04 — hostile-corpus aiEval suite, Grafana ops dashboards, CASA evidence refresh (now also covering Calendar + Drive scopes), LAUNCH-GO-NOGO checklist, formal GA tag.
- VISUAL-REFRESH-01..06 — purple palette alignment of user pages; v1.4 surfaces should adopt purple as they are built, but a project-wide refresh is not scoped.

## Out of Scope (Permanent)

- **Outlook / Microsoft 365 calendar integration** — Gmail/Google-only locked. Microsoft Graph subscriptions are a different ecosystem and doubling provider surface for a single-VPS solo project is not the right trade.
- **Microsoft OneDrive / SharePoint** — same reasoning as Outlook.
- **iCloud, CalDAV, Dropbox, generic Google Workspace-vs-personal-account heuristics beyond what Google's own consent flow provides.**
- **Persistent attachment indexing or content extraction (Tika / PDFBox / mammoth-style)** — incompatible with ARCH-02 and CASA posture; Inbox Zero's `AttachmentDocument {content, summary}` model is explicitly rejected.
- **Embeddings over user mail or Drive files / RAG / vector DB.**
- **Persisted meeting brief body or pre-generated brief storage** — briefs are generated in-memory at delivery time; only the assistant-authored summary narrative persists.
- **Slack / Teams / Telegram / SMS meeting brief delivery** — no messaging-channel infrastructure exists in v1.4; revisit when a SEED activates that.
- **Auto-accept/decline of calendar invites via rules** — auto-send-class trust risk; explicit user interaction required.
- **Full `drive`, `drive.readonly`, or `drive.metadata.readonly` scopes** — would re-anchor CASA scope verification and increase privacy surface beyond v1.4 posture.
- **Second Google OAuth Cloud Console client for Calendar/Drive** — same Google client-id reused via split `ClientRegistration`; avoids a second consent screen branding pass.
- **Booking-page localization beyond Vietnamese + English.**

## Traceability

Phase mapping rewritten by `gsd-roadmapper` on 2026-06-17 after the v1.4 roadmap was re-decomposed from 8 horizontal phases into **5 feature-driven phases (12-16)**, each shipping one top-level user-visible Co-Pilot capability end-to-end. INFRA-03 was removed from REQUIREMENTS.md because CASA / GA-tag deferral is already documented in PROJECT.md "Explicitly deferred to v1.5+" and OPS-FUT-04 — no v1.4 requirement should restate it. Each v1.4 requirement maps to exactly one phase in `.planning/ROADMAP.md`.

| REQ-ID | Phase |
|--------|-------|
| INFRA-01 | Phase 12 |
| INFRA-02 | Phase 16 |
| CAL-CONN-01 | Phase 12 |
| CAL-CONN-02 | Phase 12 |
| CAL-CONN-03 | Phase 12 |
| CAL-CONN-04 | Phase 12 |
| CAL-CONN-05 | Phase 12 |
| CAL-CONN-06 | Phase 12 |
| CAL-CONN-07 | Phase 12 |
| CAL-CONN-08 | Phase 12 |
| CAL-TRIAGE-01 | Phase 12 |
| CAL-TRIAGE-02 | Phase 12 |
| CAL-TRIAGE-03 | Phase 12 |
| CAL-TRIAGE-04 | Phase 12 |
| CAL-AVAIL-01 | Phase 13 |
| CAL-AVAIL-02 | Phase 13 |
| CAL-AVAIL-03 | Phase 13 |
| CAL-AVAIL-04 | Phase 13 |
| CAL-AVAIL-05 | Phase 13 |
| CAL-AVAIL-06 | Phase 13 |
| CAL-RULE-01 | Phase 13 |
| CAL-RULE-02 | Phase 13 |
| CAL-RULE-03 | Phase 13 |
| CAL-RULE-04 | Phase 13 |
| CAL-BOOK-01 | Phase 14 |
| CAL-BOOK-02 | Phase 14 |
| CAL-BOOK-03 | Phase 14 |
| CAL-BOOK-04 | Phase 14 |
| CAL-BOOK-05 | Phase 14 |
| CAL-BOOK-06 | Phase 14 |
| CAL-BOOK-07 | Phase 14 |
| CAL-BOOK-08 | Phase 14 |
| CAL-BOOK-09 | Phase 14 |
| DRV-CONN-01 | Phase 15 |
| DRV-CONN-02 | Phase 15 |
| DRV-CONN-03 | Phase 15 |
| DRV-CONN-04 | Phase 15 |
| DRV-CONN-05 | Phase 15 |
| DRV-FILE-01 | Phase 15 |
| DRV-FILE-02 | Phase 15 |
| DRV-FILE-03 | Phase 15 |
| DRV-FILE-04 | Phase 15 |
| DRV-FILE-05 | Phase 15 |
| DRV-FILE-06 | Phase 15 |
| DRV-FILE-07 | Phase 15 |
| DRV-FILE-08 | Phase 15 |
| DRV-FILE-09 | Phase 15 |
| DRV-ATCH-01 | Phase 15 |
| DRV-ATCH-02 | Phase 15 |
| DRV-ATCH-03 | Phase 15 |
| DRV-ATCH-04 | Phase 15 |
| CAL-BRIEF-01 | Phase 16 |
| CAL-BRIEF-02 | Phase 16 |
| CAL-BRIEF-03 | Phase 16 |
| CAL-BRIEF-04 | Phase 16 |
| CAL-BRIEF-05 | Phase 16 |
| CAL-BRIEF-06 | Phase 16 |
| CAL-BRIEF-07 | Phase 16 |
| CAL-BRIEF-08 | Phase 16 |

**Coverage:** 59/59 v1.4 requirements mapped (1 INFRA-01 + 8 CAL-CONN + 4 CAL-TRIAGE + 6 CAL-AVAIL + 4 CAL-RULE + 9 CAL-BOOK + 5 DRV-CONN + 9 DRV-FILE + 4 DRV-ATCH + 1 INFRA-02 + 8 CAL-BRIEF). No orphans, no duplicates. Per-phase totals: Phase 12 = 13, Phase 13 = 10, Phase 14 = 9, Phase 15 = 18, Phase 16 = 9 (sum = 59).
