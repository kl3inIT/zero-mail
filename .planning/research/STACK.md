# Stack Research — Zero Mail v1.4 (Google Calendar Co-Pilot + Drive Filing)

**Domain:** Google Calendar API + Google Drive API integration layered on existing Spring Boot 4.1 + Spring AI 2.0.0 GA multi-tenant Gmail SaaS.
**Researched:** 2026-06-17
**Overall confidence:** HIGH on backend additions (verified via Context7 `/websites/developers_google_workspace_calendar_api`, `/websites/developers_google_workspace_drive`, `/websites/spring_io_spring-ai_reference_2_0-snapshot`, and live Maven Central index probes for `com.google.apis:google-api-services-calendar` and `com.google.apis:google-api-services-drive`). HIGH on frontend (no new runtime deps — `calendar.tsx` shadcn primitive + `react-day-picker@^10.0.1` + `date-fns@^4.4.0` already in `apps/web/package.json`).

> **Scope of this document.** This is the **v1.4 delta**. The v1.0 → v1.3 baseline (Java 25 / Spring Boot 4.1.0 GA / Spring Modulith 2.1.0 / Spring AI 2.0.0 GA / PostgreSQL 17 / Redis 7 / Next.js 16.2 / React 19.2 / Tailwind 4 / shadcn/ui / TanStack Query / openapi-typescript + openapi-fetch / Liquibase 5 / Spring Session Redis cookie / AES-GCM at app layer / `MailboxContext` ScopedValue from v1.3 / `LlmGateway` single Spring AI adapter / single bundled Google OAuth client / `google-api-services-gmail v1-rev20250331-2.0.0` / `google-auth-library-oauth2-http 1.48.0`) is **locked and validated** — see prior STACK.md revisions in git history. This document only catalogs what v1.4 **adds**.

> **What v1.4 does not add or change:** no new IdP (still single Google OAuth client + bundled scopes); no new database; no new queue; no new observability tool; no new LLM provider SDK (Calendar briefing agent reuses the existing `LlmGateway` + Spring AI 2.0.0 GA `ToolCallingManager` / `ChatClient.tools()`); no vector DB (privacy ARCH-02 still bans embeddings of email content and now bans persisted attachment content too); no Microsoft Graph SDK / Outlook starter; no PDF/OCR/text-extraction library (attachment filing analyzes metadata + AI vision-capable model in-memory, not Tesseract/Tika); no second OAuth client; no incremental-authorization second consent screen.

---

## TL;DR — Prescriptive v1.4 Additions

**Backend — TWO new runtime dependencies, both Google API Java client artifacts in the same family already on the classpath for Gmail.**

| Capability | New artifact | Module pin |
|---|---|---|
| Google Calendar API (freebusy, events.insert, calendarList.list, events.watch later) | `com.google.apis:google-api-services-calendar:v3-rev20260517-2.0.0` | `backend/core` (`api` configuration, like the existing Gmail artifact) |
| Google Drive API (files.create with media, files.list under `drive.file` scope, optional `permissions` for sharing) | `com.google.apis:google-api-services-drive:v3-rev20260428-2.0.0` | `backend/core` (`api` configuration) |

**No Spring Boot starter exists for either API and we deliberately would not use one if it did.** Google publishes only the auto-generated Discovery-style `google-api-services-*` artifacts plus the unrelated `spring-cloud-gcp-*` family — and `spring-cloud-gcp` is **explicitly banned** in CLAUDE.md ("No GCP hosting baseline — do not add `spring-cloud-gcp` starters by default"). The pattern v1.0 chose for Gmail (`google-api-services-gmail` + `google-auth-library-oauth2-http`, hand-wired `Gmail.Builder` per request) is what v1.4 replicates for Calendar and Drive. Same client transport (`NetHttpTransport`), same `GsonFactory` JSON, same `HttpCredentialsAdapter`, same AES-GCM-encrypted refresh-token storage path through the existing OAuth token store. **No transitive version conflicts** — both new artifacts pull the same `com.google.api-client:google-api-client:2.x` + `com.google.http-client:google-http-client:1.47.x` that the existing Gmail artifact already requires; verified Maven Central index 2026-06-17.

**Frontend — ZERO new runtime dependencies.** Booking page rendering, weekly availability windows editor, slot picker, and meeting-brief display all compose on top of primitives **already installed** in `apps/web/components/ui/**` on 2026-06-17:

| Booking / Calendar UI need | Existing primitive | Source |
|---|---|---|
| Public booking page date picker (visitor picks a day) | `calendar.tsx` (shadcn wrapper over `react-day-picker@^10.0.1`) | already installed |
| Weekly availability windows editor (Mon-Sun × multiple time ranges) | `select.tsx` + `input.tsx` + `button.tsx` + `toggle.tsx` hand-composed | already installed |
| Slot picker (post date-pick, list of 30-min slots) | `button.tsx` + `card.tsx` + `radio-group.tsx` hand-composed | already installed |
| Timezone picker on booking page | `command.tsx` + `popover.tsx` + IANA list inlined (no `moment-timezone` runtime dep) | already installed; **Intl.supportedValuesOf("timeZone")** is the source of truth in modern browsers + Node 22 |
| Date/time formatting + arithmetic for slot generation | `date-fns@^4.4.0` (browser) + `java.time` (backend) | already installed |
| Filing folder picker (admin's Drive folder tree) | `command.tsx` + `popover.tsx` + recursive `Tree` from existing primitives | already installed |
| Drive folder confidence display | `badge.tsx` + `progress.tsx` | already installed |
| Meeting brief surface (in-app + email) | Existing `card.tsx` + `badge.tsx` + same `MailNotification` channel used by digest emails (no new email lib — Resend already pinned 4.13.0) | already installed |

**Net new shadcn primitives required: zero.** Net new npm runtime deps: zero. **Verified** by `ls apps/web/components/ui/` and `grep` of `apps/web/package.json` on 2026-06-17.

---

## What v1.4 Adds — Backend Dependencies (Two Artifacts)

### Google Calendar API Java client — `google-api-services-calendar`

**Verified version (Maven Central index 2026-06-17):**

```
com.google.apis:google-api-services-calendar
…
  v3-rev20260225-2.0.0
  v3-rev20260517-2.0.0   ← LATEST as of 2026-06-17
```

**Pin via `libs.versions.toml`:**

```toml
[versions]
calendarApi = "v3-rev20260517-2.0.0"
driveApi    = "v3-rev20260428-2.0.0"
# existing pins unchanged
gmailApi = "v1-rev20250331-2.0.0"

[libraries]
google-api-services-calendar = { module = "com.google.apis:google-api-services-calendar", version.ref = "calendarApi" }
google-api-services-drive    = { module = "com.google.apis:google-api-services-drive",    version.ref = "driveApi" }
```

**Consumed in `backend/core/build.gradle.kts` next to the existing Gmail line:**

```kotlin
api(libs.google.api.services.gmail)
api(libs.google.api.services.calendar)   // ← NEW
api(libs.google.api.services.drive)      // ← NEW
```

**Module placement decision: `backend/core`, `api` configuration (not `implementation`).** Matches the existing Gmail pattern: the request/response models (`Event`, `FreeBusyRequest`, `File`, `FileList`) are referenced from `domain/`, `application/`, **and** `persistence/` packages (audit row shape mirrors a subset of `Event`), so the transitive types must stay on the consumer compile classpath. **The `Calendar.Builder` and `Drive.Builder` construction is confined to dedicated gateway packages** (`core.calendar.gateway.google` and `core.drive.gateway.google`), enforced by a new ArchUnit rule — same shape as the existing `core.llm.gateway.springai` confinement plus the v1.3 `core.gmail.gateway` confinement.

**No Spring Boot autoconfiguration ships for these artifacts.** Hand-wired per-request client construction is the locked pattern (see "Why no Spring Boot starter for Google Calendar/Drive" below).

**Calendar APIs we use (mapped to existing v1.4 requirements):**

| Use case | Calendar API call | Notes |
|---|---|---|
| List a connection's calendars on the **Manage calendars** screen | `calendarList.list()` | Filter `accessRole >= writer` for "destination calendar" slot. |
| AI availability in draft reply / `propose_meeting` rule action | `freebusy.query(FreeBusyRequest)` with up to 50 calendar IDs across enabled connections | Pre-filter to **enabled** calendar IDs per `calendar_connections` ↔ `calendars` join; do NOT call `events.list` for availability — `freebusy` is metadata-only (no event titles/locations) which fits the privacy posture nicely. |
| Booking link writes the event | `events.insert(calendarId, Event)` with `conferenceDataVersion=1` when Google Meet is the chosen location type | `createRequest.requestId = UUID` for idempotency. The locked outbound gateway boundary is widened: in addition to Gmail send via `OutboundSendGateway`, calendar writes go through a parallel `OutboundCalendarGateway` so ArchUnit can keep the "no direct provider write outside the gateway" rule. |
| Calendar-aware triage (detect invite / cancellation / reschedule) | No new Calendar call needed — we parse the Gmail `text/calendar` part already accessible via the v1.3 Gmail client, then optionally cross-check with `events.get(calendarId, eventId)` to confirm RSVP state | The cross-check is the only Calendar **read** done from the triage hot path. |
| AI meeting brief cron | `events.list(calendarId, timeMin, timeMax)` filtered to events with external attendees (≥1 attendee whose `email` domain ≠ connection's primary domain) | Run from `backend/worker`; the resulting attendee list seeds the agentic AI loop. Event titles/locations are processed in-memory and the **brief text** is what gets persisted (it is user-derived narrative, not raw email content — analogous to the v1.1 chat `draft_body` carve-out). |

**Scopes required (added to the existing single Google OAuth client):**

| Scope | Why | Sensitivity | CASA tier |
|---|---|---|---|
| `https://www.googleapis.com/auth/calendar.freebusy` | freebusy.query for AI availability + propose_meeting + brief seeding | **Non-sensitive** in Google's classification — no event titles, locations, or attendees returned | No CASA assessment required |
| `https://www.googleapis.com/auth/calendar.events` | events.insert (booking write), events.get (triage RSVP cross-check), events.list (briefing cron) | **Restricted** | CASA Tier 2 (same tier as the existing Gmail scopes) |
| `https://www.googleapis.com/auth/calendar.readonly` | calendarList.list for the **Manage calendars** screen and reading per-calendar timezones for slot rendering | **Sensitive** | Standard CASA |

**Drive APIs we use:**

| Use case | Drive API call | Notes |
|---|---|---|
| Suggest folder for an incoming attachment | `files.list(q = "mimeType='application/vnd.google-apps.folder' and 'me' in owners and trashed=false", fields = "files(id,name,parents)")` | Limited by `drive.file` scope to files **the app has previously created or that the user explicitly picks via the Drive picker** — so the initial folder set comes from a Drive Picker-driven onboarding step where the user nominates root folders. (Inbox Zero accepted this UX trade for the same privacy reason; we keep it.) |
| Create destination folder if one of the AI's suggestions doesn't exist yet | `files.create(File metadata with mimeType="application/vnd.google-apps.folder", parents)` | App-created folders are in-scope for `drive.file` forever — accumulates a clean "Zero Mail filed" sub-tree per workspace. |
| File the attachment | `files.create(File metadata, AbstractInputStreamContent media)` with `media = new InputStreamContent(mimeType, gmailAttachmentInputStream)` | **CRITICAL for privacy ARCH-02:** we use `InputStreamContent` (subclass of `AbstractInputStreamContent`) NOT `FileContent` — the Gmail attachment stream is piped directly from the Gmail `users.messages.attachments.get` response through `InputStreamContent` into the Drive upload **without ever touching the filesystem or a persistent buffer**. See "In-memory attachment streaming pattern" below. |
| Attach a file from a user-curated Drive folder to an outbound reply | `files.get(fileId).executeMediaAsInputStream()` → pipe into the Gmail MIME multipart builder | Same in-memory streaming pattern, reverse direction; routed through the existing `OutboundSendGateway`. |

**Scopes required:**

| Scope | Why | Sensitivity | CASA tier |
|---|---|---|---|
| `https://www.googleapis.com/auth/drive.file` | files.create (upload + folder), files.list scoped to app-created/Picker-nominated files, files.get media download | **Non-sensitive** in Google's classification (per-file scope, app-installed) | No CASA assessment required — **this is the central reason `drive.file` is locked in over the full `drive` scope** |

We deliberately **reject** the full `drive` scope (Restricted, CASA Tier 2, full filesystem access) and the `drive.readonly` scope (Restricted, CASA Tier 2). The product loss from `drive.file` (we can't enumerate the user's full folder tree on first onboarding, so the user has to point at a few root folders via the Drive Picker) is small and the CASA + trust win is large. This mirrors Inbox Zero's choice and is consistent with our locked privacy posture.

### Google auth library — already on classpath

`com.google.auth:google-auth-library-oauth2-http:1.48.0` (already in `libs.versions.toml`) handles refresh-token-based credential construction for **all three** Google APIs. No version bump needed; verified compatible with both new artifacts (both publish against `google-auth-library-oauth2-http >= 1.30`).

### Why no Spring Boot starter for Google Calendar / Drive

Three reasons — listed in order of "most blocking" first:

1. **`spring-cloud-gcp-starter-*` is explicitly banned in CLAUDE.md.** Quote: *"No GCP hosting baseline — do not add `spring-cloud-gcp` starters by default. Gmail push arrives as plain HTTP POSTs to a Spring MVC controller on the VPS."* The same reasoning applies — we are not on GCP infrastructure, we don't want autoconfigured Pub/Sub publishers, and we don't want a `CredentialsProvider` chain that defaults to GCP service-account discovery (which is irrelevant on the VPS and confusing to debug).
2. **No first-party Google "Spring Boot starter for Calendar/Drive" exists.** Google publishes `google-api-services-*` (auto-generated from Discovery) and `spring-cloud-gcp-*` (GCP-runtime helpers). There is no `spring-google-calendar-starter` and creating a thin starter would be a v2 nice-to-have, not a v1.4 requirement.
3. **Our `MailboxContext` ScopedValue model from v1.3 already supplies per-request credentials.** The client construction is `new Calendar.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), new HttpCredentialsAdapter(perRequestCredentials)).setApplicationName("Zero Mail").build()` — three lines, identical shape to the v1.3 Gmail gateway. A starter would only save those three lines per request and would conceal the per-request credential injection that the multi-mailbox architecture requires.

### Spring AI 2.0.0 GA for the briefing agent (tool-calling loop)

The AI meeting brief generator and the AI availability tool (when the rule compiler/chat assistant asks "what slots could I propose for next Tuesday?") both need a **nested / multi-turn tool loop** — i.e. the model emits a tool call, we run it, feed the result back, the model may emit another tool call, etc. This is supported in Spring AI 2.0.0 GA out of the box.

**Two execution modes — pick per call site:**

| Mode | API | When to use | Iteration limit |
|---|---|---|---|
| **Framework-controlled** | `ChatClient.create(chatModel).prompt(...).tools(...).call().content()` (or `.stream()`) | Brief generation cron — fire-and-forget, simple prompt, no fine-grained step inspection needed | Configured by `ToolCallingChatOptions.maxIterations` (default 10 in Spring AI 2.0.0 GA — pin it explicitly to a low number, e.g. 6, for the briefing agent to bound LLM cost). |
| **User-controlled** | `DefaultToolCallingManager.builder().build()` + `internalToolExecutionEnabled(false)` + manual `while (response.hasToolCalls()) { … }` loop | `propose_meeting` rule action — we want to enforce a hard tool-allow-list per iteration, audit each step into `triage_audit`, and respect the per-tenant LLM spend cap | Manual `for (int step = 0; step < MAX_STEPS; step++)` — gives us explicit per-step budget gates and reconciles with the existing per-call `LlmGateway` cost-and-cap pipeline (LLM-10). |

**Verified via Context7 `/websites/spring_io_spring-ai_reference_2_0-snapshot`:** the `ToolCallingManager` + `internalToolExecutionEnabled(false)` pattern is documented across all four Spring AI provider chat pages (Anthropic, Bedrock, DeepSeek, Google GenAI) we already have starters for, and `MessageAggregator` is the documented streaming variant. No new dependency — `ToolCallingManager`, `DefaultToolCallingManager`, `FunctionToolCallback`, and `ToolExecutionResult` all ship in `spring-ai-core` already pulled by the existing chat-model starters. **HIGH** confidence.

**Tools the briefing agent gets:**

| Tool | Backed by | Returns |
|---|---|---|
| `searchInboxByContact(email, limit)` | v1.3 Gmail client + `MailboxContext` | Last N message metadata (subject, snippet ≤200 chars) — same `ToolOutputSanitizer` clamp the v1.1 chat tools use |
| `getRecentMeetingsWithContact(email, limit)` | Calendar `events.list` filtered to attendee == `email` | Past meeting count, last meeting date, last meeting title |
| `webSearch(query)` | **Optional v1.4-late** — gated behind a tenant feature flag, default OFF; if ON, routes through OpenRouter web-search-enabled models | Snippets only |

Per the locked rule "Tool-call allow-list + structured schema; safety violation rejects pre-execution" (LLM-07), every tool above is registered with a JSON Schema (Spring AI auto-generates from `inputType(Class)`) and an explicit allow-list check before `ToolCallingManager.executeToolCalls(...)` runs.

### In-memory attachment streaming pattern (privacy ARCH-02)

The Drive filing path looks like this end-to-end **without ever touching disk or a persistent buffer**:

```
Gmail Pub/Sub push
  → /api/internal/pubsub/gmail webhook
  → MessageObservedEvent (Spring Modulith, in-process, AFTER_COMMIT)
  → AttachmentFilingService.handle(event)
      ├── For each attachment part:
      │     InputStream gmailStream =
      │         gmail.users().messages().attachments().get(userId, messageId, attachmentId)
      │              .executeMediaAsInputStream();   // Gmail v1 API
      │     // AI folder suggestion runs on METADATA only (filename, mimeType, sender,
      │     // subject, prior filings). Attachment CONTENT is NEVER read by the AI.
      │     FolderSuggestion suggestion = filingAi.suggest(attachmentMetadata);
      │     File metadata = new File()
      │         .setName(attachment.filename)
      │         .setParents(List.of(suggestion.folderId));
      │     drive.files()
      │          .create(metadata, new InputStreamContent(attachment.mimeType, gmailStream))
      │          .setFields("id, parents")
      │          .execute();   // pipes Gmail stream directly into Drive upload
      └── Persist ONLY metadata into document_filing:
            (id, tenant_id, gmail_connection_id, gmail_message_id, attachment_id,
             filename, mime_type, sha256_of_bytes,            -- hash for dedup; bytes themselves discarded
             drive_file_id, destination_folder_id,
             confidence, status, filed_at, audit_jsonb)
```

**Why this is ARCH-02-safe:**

1. The `gmailStream` is an `InputStream`, not a `byte[]`. It is consumed exactly once by `InputStreamContent`, which forwards bytes to the Drive HTTP upload as they arrive. No `Files.write(...)`, no `ByteArrayOutputStream`, no `@Cacheable`.
2. The AI folder suggestion runs on **metadata only** — `filename`, `mimeType`, `sender`, `subject`, optionally the user's prior filing decisions. The attachment **body** is never read by Java code, never sent to the LLM, never persisted. (Optional v1.5+: a tenant-opt-in vision model that reads the attachment in-memory for higher-accuracy filing — explicitly **out of scope** for v1.4.)
3. The `sha256_of_bytes` column is computed by tee-ing the input stream through a `DigestInputStream` during upload. The hash is metadata (32 bytes), not content; it lets us deduplicate "the same PDF was sent twice" without re-uploading. This is consistent with the privacy posture.
4. A new ArchUnit rule (`AttachmentBytesNotPersistedRule`) bans any persistence-layer field named `attachment_bytes`, `content_bytes`, `body_bytes`, or `*Plaintext` on `document_filing` / `attachment_source` / `filing_*` tables — same shape as the v1.2 admin audit ArchUnit rule.
5. Logback `@Sensitive` scrub continues to apply — attachment filenames are `@Sensitive` (filenames can contain personal data: "Q4-payroll-2026-jane-doe.pdf"). Sender email stays under the existing v1.0 privacy logging convention.

The same pattern reversed (`drive.files().get(fileId).executeMediaAsInputStream()` → pipe into Gmail MIME `MimeBodyPart.setDataHandler(new DataHandler(new InputStreamDataSource(...)))`) covers the **attachment_source rule** (rule attaches a Drive file to an outbound reply). The existing `OutboundSendGateway` is the single send-side boundary; we wire attachment streaming **inside** the gateway so ArchUnit's existing send-call-site rule keeps holding.

---

## What v1.4 Adds — Backend Persistence (New Liquibase Changelogs Only)

Ten new Liquibase YAML changelogs. **No new database library; Postgres 17 + Liquibase 5.0.3 already pinned.** Naming follows the v1.3 connection convention (`gmail_connections`) — `calendar_connections` and `drive_connections` are **workspace-scoped** (matching the workspace-shared vs mailbox-isolated boundary defined in v1.3 — see Architecture for the rule).

| # | Changelog | Owner module | Purpose |
|---|---|---|---|
| 1 | `calendar_connections` | `backend/core` (new `core.calendar` package) | `(id, tenant_id, google_account_id, google_account_email_sensitive, refresh_token_ciphertext_b64, refresh_token_iv_b64, kek_version, scopes_granted, status, connected_at, last_refreshed_at, disconnected_at, disconnect_reason)`. Same column shape as `gmail_connections` (v1.3 WSP-01) so the existing `OAuthTokenStore` + AES-GCM crypto code is reused verbatim. `status ∈ {CONNECTED, DISCONNECTED, REVOKED}`. **One CONNECTED Calendar connection per `google_account_id` per tenant** (unique index), parallel to GMA-06's global active-Gmail uniqueness rule. |
| 2 | `calendars` | `backend/core` (`core.calendar`) | `(id, calendar_connection_id, google_calendar_id, summary, access_role, timezone, primary_flag, enabled_for_freebusy, enabled_as_booking_destination, synced_at)`. Snapshot of `calendarList.list()` per connection; `enabled_for_freebusy` defaults TRUE for the primary calendar and FALSE for others (the user opts in additional calendars). |
| 3 | `booking_links` | `backend/core` (`core.booking`) | `(id, tenant_id, calendar_connection_id, destination_calendar_id, slug_global_unique, name, description, duration_minutes, buffer_before_minutes, buffer_after_minutes, location_type, location_payload_jsonb, status, created_at, updated_at)`. `location_type ∈ {GOOGLE_MEET, PHONE, IN_PERSON, CUSTOM}`. `status ∈ {ACTIVE, PAUSED, DELETED}`. **Global unique slug** (booking page lives at `/book/{slug}`); slug claim race resolved with Postgres unique constraint + friendly error. |
| 4 | `booking_windows` | `backend/core` (`core.booking`) | `(id, booking_link_id, day_of_week, start_local_time, end_local_time)`. Multiple rows per `(booking_link_id, day_of_week)` for split availability (e.g. 9–12 and 14–17 on Mondays). Stored in the booking link's destination-calendar timezone (rendered for the visitor in the visitor's chosen timezone). |
| 5 | `bookings` | `backend/core` (`core.booking`) | `(id, booking_link_id, visitor_name, visitor_email_sensitive, visitor_timezone, start_at_utc, end_at_utc, google_event_id, status, cancellation_reason, idempotency_key, created_at, cancelled_at, payload_jsonb)`. `status ∈ {CONFIRMED, CANCELLED, RESCHEDULED}`. `idempotency_key UNIQUE` for the public booking endpoint (anti-double-submit). |
| 6 | `meeting_briefings` | `backend/core` (`core.briefing`) | `(id, tenant_id, calendar_connection_id, google_event_id, event_start_at_utc, generated_at, brief_markdown, brief_tokens_used, model_id_at_generation, delivery_channels, delivery_status_jsonb)`. `delivery_channels` is an array subset of `{EMAIL, DIGEST}`. **`brief_markdown` is persistable** (same carve-out as the v1.1 chat `draft_body` — it is AI-derived narrative authored by Zero Mail for the user, not extracted email content received from Gmail). The seed messages that produced it are NOT persisted. |
| 7 | `drive_connections` | `backend/core` (new `core.drive` package) | Same shape as `calendar_connections`; **workspace-scoped, one CONNECTED per tenant** (Drive is a per-workspace data store, not per-mailbox). |
| 8 | `filing_folders` | `backend/core` (`core.drive`) | `(id, drive_connection_id, google_drive_folder_id, name, parent_google_drive_folder_id, source, registered_at)`. `source ∈ {USER_PICKED, APP_CREATED}`. Onboarding nominates a few user-picked roots via the Drive Picker; subsequent app-created sub-folders inherit from those roots. |
| 9 | `document_filings` | `backend/core` (`core.drive`) | `(id, tenant_id, gmail_connection_id, drive_connection_id, gmail_message_id, gmail_attachment_id, filename_sensitive, mime_type, sha256_of_bytes, drive_file_id, destination_folder_id, confidence, status, filed_at, audit_jsonb)`. `status ∈ {SUGGESTED, FILED, NEEDS_REVIEW, REJECTED}`. **`audit_jsonb` MUST NOT contain attachment body excerpts** — enforced by the new `AttachmentBytesNotPersistedRule` ArchUnit rule. |
| 10 | `attachment_sources` | `backend/core` (`core.drive`) | `(id, tenant_id, name, drive_connection_id, source_folder_google_id, scope, status, created_at)`. Used by the `attach_from_source` rule action — a curated folder from which rule-triggered replies may pull attachments. `scope ∈ {WORKSPACE_SHARED, MAILBOX_ISOLATED}` — explicit per-source choice that the user makes when creating the source (default WORKSPACE_SHARED for invoice templates / signature PDFs; MAILBOX_ISOLATED for mailbox-specific stationery). |

**Privacy & sensitivity columns (marked `@Sensitive`, scrubbed by Logback per FND-03):**
- `calendar_connections.google_account_email_sensitive`
- `calendar_connections.refresh_token_ciphertext_b64`
- `drive_connections.google_account_email_sensitive`
- `drive_connections.refresh_token_ciphertext_b64`
- `bookings.visitor_email_sensitive`
- `document_filings.filename_sensitive`

**FK + cascade strategy** mirrors v1.3 — `ON DELETE CASCADE` from `calendar_connections` and `drive_connections` down to dependent rows so the existing account-deletion cleanup (AUTH-03) sweeps Calendar/Drive metadata atomically.

---

## How v1.4 Touches the Existing OAuth Flow (Without Breaking v1.3's Single Bundled Scope)

**This is the most important integration question and the one most likely to be gotten subtly wrong.** The answer is: we deliberately **do** request the new Calendar + Drive scopes incrementally — **but only through a second, explicitly user-initiated OAuth round-trip per feature**, not through Google's "incremental authorization" auto-prompt anti-pattern that v1.3 already rejected.

### What "incremental authorization" means in our context

Google's "incremental authorization" is an OAuth grant flow where the client adds `include_granted_scopes=true` to a fresh `/o/oauth2/v2/auth` request that asks for new scopes; Google merges the new grant with the existing grant and returns a fresh refresh token covering the union. **It is not a separate OAuth client and not a separate IdP.** Memory note "Bundle OAuth scopes (inbox-zero pattern)" rejects the **automatic two-leg login experience** where the user signs in for `openid email profile` and is then surprised by a second consent screen at signup. It does **not** reject explicit user-initiated grant additions for **opt-in features**.

### v1.4 OAuth flow decision matrix

| Trigger | Scope set requested | When the user sees a consent screen | Justification |
|---|---|---|---|
| **First-time signup** (v1.3 behavior, unchanged) | `openid email profile` + `gmail.modify` + `gmail.send` + `gmail.compose` + `gmail.metadata` (the v1.3 bundle) | At signup — one consent screen | "Bundle OAuth scopes" — locked |
| **Add a second Gmail mailbox** (v1.3 behavior, unchanged) | Same as above | Per the v1.3 OAuth intent split | Locked |
| **Connect a Google Calendar account** (new in v1.4) | `openid email profile` + `calendar.events` + `calendar.freebusy` + `calendar.readonly` **+ `include_granted_scopes=true`** | Per user click on "Connect Calendar" in `/settings/calendar` | This is a feature-add the user explicitly opted into. We are NOT re-prompting for Gmail scopes; `include_granted_scopes=true` carries them forward so the merged refresh token covers everything. **Crucially, calendar consent is shown only when the user clicks "Connect Calendar".** No surprise mid-onboarding screen. |
| **Connect Google Drive** (new in v1.4) | `openid email profile` + `drive.file` **+ `include_granted_scopes=true`** | Per user click on "Connect Drive" in `/settings/drive` | Same justification. `drive.file` is non-sensitive so the consent screen is short. |

**Implementation in Spring Security OAuth2 Client (already on classpath):**

We register **one additional `ClientRegistration`** in `application.yml` per added feature — `google-calendar` and `google-drive` — but **both point at the same Google OAuth client ID/secret** as the existing `google` registration. The differences are only the `scope` list and the `authorization-uri` query params. This is the same pattern v1.3 used to split the "sign in" intent from the "add mailbox" intent (GMA-07) — it is **not** a second OAuth client at Google's end, it is a Spring Security routing convenience.

```yaml
# application.yml — additive only; existing 'google' registration unchanged
spring:
  security:
    oauth2:
      client:
        registration:
          google:                # existing, unchanged
            scope: openid,email,profile,https://www.googleapis.com/auth/gmail.modify, ...
          google-calendar:       # NEW v1.4
            provider: google
            client-id: ${GOOGLE_OAUTH_CLIENT_ID}      # same client as 'google'
            client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET}
            scope:
              - openid
              - email
              - profile
              - https://www.googleapis.com/auth/calendar.events
              - https://www.googleapis.com/auth/calendar.freebusy
              - https://www.googleapis.com/auth/calendar.readonly
            authorization-grant-type: authorization_code
            redirect-uri: '{baseUrl}/login/oauth2/code/google-calendar'
          google-drive:          # NEW v1.4
            provider: google
            client-id: ${GOOGLE_OAUTH_CLIENT_ID}
            client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET}
            scope:
              - openid
              - email
              - profile
              - https://www.googleapis.com/auth/drive.file
            authorization-grant-type: authorization_code
            redirect-uri: '{baseUrl}/login/oauth2/code/google-drive'
```

**A custom `OAuth2AuthorizationRequestResolver` adds `include_granted_scopes=true` + `access_type=offline` + `prompt=consent`** (the last one is needed to force return of a fresh refresh token even when scopes are merged — Google's documented behavior). This `Resolver` exists already in v1.3 for the Gmail-add flow; we extend its switch statement with the two new registration IDs and reuse the same `additionalParameters` map.

**Success handlers — three of them, each constructive:**

- `GoogleSignInSuccessHandler` (existing v1.0/v1.3): creates session, attaches authorities.
- `GoogleCalendarConnectSuccessHandler` (NEW v1.4): looks up the active tenant from the session, persists `calendar_connections` row, encrypts and stores the refresh token via the **existing** `OAuthTokenStore`, fires `CalendarConnectedEvent` (Modulith application event, AFTER_COMMIT) so that `core.calendar.application.CalendarListSyncService` runs `calendarList.list()` and seeds `calendars` rows.
- `GoogleDriveConnectSuccessHandler` (NEW v1.4): same shape, fires `DriveConnectedEvent`, no immediate sync needed (folder picker is launched from the UI on next page load).

**Why this works without breaking v1.3:**

- The existing `google` registration is untouched — first-time signup flow is byte-identical to v1.3.
- The "Bundle OAuth scopes" memory note's concern was the **automatic** two-leg login (login screen → surprise "Connect Gmail" screen). v1.4's Calendar/Drive consents happen **only** when the user clicks an explicit "Connect …" button on a settings page they navigated to. No surprise.
- `include_granted_scopes=true` is Google's documented mechanism for additive grants without re-prompting for previously-granted scopes — it is the **opposite** of what "incremental authorization" meant in the v1.3 rejection, which was a Google-side UX that interrupted login. Here it is a refresh-token-merge directive on a user-initiated screen.

**What happens when a user revokes Calendar/Drive in their Google account:** the next API call returns `invalid_grant`. The existing v1.0 `DISCONNECTED` state machine (AUTH-05) is parameterized by `*_connection.status`; we set `calendar_connections.status = DISCONNECTED` and show the reconnect prompt — same UX as the Gmail reconnect.

---

## Frontend Codegen Pipeline — Unchanged from v1.3

The two-spec / two-typed-client split (`apps/web/lib/api/schema.d.ts` for public + `apps/admin/src/lib/api/admin-schema.d.ts` for admin) shipped in v1.2 and is reused. v1.4 adds new endpoints under `/api/calendar/**`, `/api/booking/**`, `/api/drive/**` — all live in the **public** spec (consumed by the user-facing `apps/web`). The **public booking page** at `/book/{slug}` is also Next.js (same `apps/web`) — its `POST /api/public/bookings` endpoint is in the public spec but **scope-gated to `permitAll()`** in `SecurityConfig` (the only public-write endpoint we have, besides Pub/Sub which is OIDC-token-verified).

**No new shadcn primitives. No new npm packages.** Per the prior `ls apps/web/components/ui/` audit:

```
calendar.tsx  ← already present (shadcn wrapper over react-day-picker@10)
card.tsx      ← already present
command.tsx   ← already present
popover.tsx   ← already present
radio-group.tsx
select.tsx
…
```

`react-day-picker@^10.0.1` and `date-fns@^4.4.0` are already pinned in `apps/web/package.json`. Memory note "Use raw shadcn primitives first" applies — wait for the rule-of-three before introducing a calendar-specific composite library.

---

## Version Compatibility Matrix (v1.4 Delta)

| Component | Version | Compatible with | Verified via |
|---|---|---|---|
| `com.google.apis:google-api-services-calendar` | `v3-rev20260517-2.0.0` | Existing `google-api-services-gmail v1-rev20250331-2.0.0` (same `google-api-client 2.x` transitive); JDK 25; Spring Boot 4.1.0 | Maven Central index probe 2026-06-17 |
| `com.google.apis:google-api-services-drive` | `v3-rev20260428-2.0.0` | Same transitive set as Calendar; JDK 25; Spring Boot 4.1.0 | Maven Central index probe 2026-06-17 |
| `com.google.auth:google-auth-library-oauth2-http` | `1.48.0` (already pinned) | Both new artifacts publish against `google-auth-library-oauth2-http >= 1.30`; no bump needed | Existing repo + Context7 Drive/Calendar Java samples |
| Spring AI 2.0.0 GA `ChatClient.tools(...)` framework-controlled loop | `2.0.0` (already pinned) | All four chat-model starters already on classpath (OpenAI, Anthropic, Google GenAI, DeepSeek) — verified `ToolCallingManager` + `internalToolExecutionEnabled(false)` pattern documented for each | Context7 `/websites/spring_io_spring-ai_reference_2_0-snapshot` |
| Spring AI 2.0.0 GA `DefaultToolCallingManager` for user-controlled loop | `2.0.0` (already pinned) | Same | Context7 same source |
| Spring Security OAuth2 Client multi-`ClientRegistration` with shared `client-id` | Spring Security 7.x via Boot 4.1.0 | Pattern used in v1.3 GMA-07 for Gmail intent split; extended in v1.4 to Calendar + Drive | Existing repo (v1.3 `OAuth2AuthorizationRequestResolver`) |
| `react-day-picker` | `^10.0.1` (already in `apps/web/package.json`) | React 19.2.5 + Next.js 16.2.4; shadcn `calendar.tsx` wrapper compatible | `apps/web/package.json` |
| `date-fns` | `^4.4.0` (already in `apps/web/package.json`) | `react-day-picker@10`; ESM-only — already accommodated in Next.js 16 build | `apps/web/package.json` |
| Liquibase 5.0.3 YAML | already pinned | Ten new changelogs, no schema feature outside basic types + JSONB | Existing repo |
| AES-GCM via JDK `Cipher` | JDK 25 (already in toolchain) | Reuses existing `OAuthTokenStore` + `AesGcmEncryptor` from v1.0 (LLM-04) and v1.3 multi-Gmail token storage | Existing repo |
| `MailboxContext` ScopedValue (v1.3) | already in place | Calendar gateway carries `CalendarConnectionContext` (parallel ScopedValue) so AUD-07 / FND-01 logging stays clean | v1.3 ArchUnit rules cover the pattern |

---

## What NOT to Use in v1.4

| Avoid | Why | Use Instead |
|---|---|---|
| **`spring-cloud-gcp-starter-pubsub` / `spring-cloud-gcp-starter-storage` / any `spring-cloud-gcp-*`** | Explicitly banned in CLAUDE.md "Hard do-not-use list" — single-VPS posture, no GCP autoconfiguration. Drags in GCP credential discovery, Pub/Sub autoconfiguration, and a `CredentialsProvider` chain none of which we want on a non-GCP host. | Hand-wired `Calendar.Builder` / `Drive.Builder` + existing `HttpCredentialsAdapter`. |
| **`com.microsoft.graph:microsoft-graph` / MS Graph SDK / Outlook starter** | Constraint: Gmail / Google Workspace only — locked. Outlook is v2 candidate. | n/a — defer. |
| **Full `drive` scope or `drive.readonly`** | Restricted + CASA Tier 2 + violates least-privilege; bigger consent screen → install drop-off; full access on a single privacy incident is catastrophic. | `drive.file` only — accept the Picker-based onboarding trade-off (Inbox Zero made the same choice). |
| **`drive.metadata.readonly` to enumerate the whole folder tree** | Still Sensitive scope; only marginal product win over `drive.file` + Picker onboarding. | Drive Picker onboarding flow; record nominated roots in `filing_folders`. |
| **`apache-tika` / `pdfbox` / `tesseract4j` for attachment text extraction** | Adds 60+ MB to the runtime image; **and** any extracted text is "raw email content received from Gmail" → cannot be persisted (ARCH-02) → cannot be re-used → has no value beyond a single in-memory LLM call we're not making anyway. Filing decision in v1.4 is **metadata-only** (filename + MIME + sender + subject). | Filename + MIME + sender + subject heuristics + AI suggestion on metadata; revisit vision-model in-memory extraction in v1.5 as opt-in only. |
| **`opencv` / `tensorflow-java` for image classification of attachments** | Same reason as Tika — content classification of email-borne content is ARCH-02-blocked in v1.4. | Metadata-only suggestion. |
| **A vector DB (pgvector, Pinecone, Weaviate, Qdrant)** | Privacy constraint forbids embeddings of user mail. The briefing agent's contact-history retrieval uses **structured filters** (Gmail search by `from:email`) not embeddings. | Gmail search-by-contact + Calendar `events.list` filter; bounded N most-recent. |
| **Persisting the raw `events.list` / `freebusy` API response** | Calendar event titles / locations / attendee names are personal data under our privacy posture even though they are not "email content" strictly. Keep as in-request only. | Only `meeting_briefings.brief_markdown` (AI-derived narrative) and `bookings` (the user's own bookings, owned data) persist. |
| **Persisting attachment bytes anywhere — including a "temporary" `attachment_blobs` table for retry** | ARCH-02 carve-out for `draft_body` does NOT extend to attachments — attachments are 100% extracted email content. The IZ `AttachmentDocument` table pattern is **rejected**. | If filing fails mid-stream, surface the failure to the user and **re-pull** the attachment from Gmail on retry. Gmail attachment retention is long; the operational cost of re-fetching on rare retries is negligible vs. the privacy gain. |
| **A separate "incremental authorization" interstitial that intercepts signup** | Memory note "Bundle OAuth scopes" — v1.3 explicitly rejected the auto-prompt UX. | User clicks "Connect Calendar" / "Connect Drive" on an explicit settings page; consent screen shown only then. |
| **A second Google OAuth Cloud Console client for Calendar/Drive** | Forces a second OAuth approval, a second app-name shown to the user, a second CASA assessment for the same SaaS. | Reuse the same OAuth client ID; only the `ClientRegistration` in Spring Security is split, which is a client-side routing convenience. |
| **`@tanstack/react-table` for the bookings list / filings list / calendars list** | Same v1.2/v1.3 reasoning — memory note "Use raw shadcn primitives first"; the lists are small (single-tenant scale, few hundred rows). | Hand-compose pagination/sort on existing `table.tsx` + `select.tsx`. Revisit if a list crosses ~500 rows in real telemetry. |
| **`luxon` / `moment-timezone` for timezone math on the booking page** | Adds 60-200 KB gz of timezone data. `Intl.supportedValuesOf("timeZone")` + `date-fns` + `date-fns-tz` (if needed) cover the booking-page math at fraction of the size. | Browser/Node native `Intl` + existing `date-fns`. If `date-fns-tz` is needed, defer until UI demands it (≤ 20 KB gz). |
| **A new email-delivery library for the meeting-brief email** | Resend (`com.resend:resend-java:4.13.0`) already pinned in v1.0 for the daily digest. | Reuse the existing `DigestEmailSender` / Resend client. |
| **A second `OutboundSendGateway` for calendar event writes** | We want one ArchUnit-enforced "no direct provider write outside the gateway" rule, but adding a `Calendar`-specific gateway alongside the Gmail one is the right architectural move — it is **not** a second copy of `OutboundSendGateway`. | `OutboundCalendarGateway` is a sibling, not a duplicate; same pattern, different provider. |
| **Spring AI custom `Advisor` for the briefing agentic loop** | Advisors are for cross-cutting prompt augmentation (RAG, memory). The briefing loop's "what next?" decision lives in the model + tool schema, not in an Advisor. | `DefaultToolCallingManager` user-controlled loop OR framework-controlled `ChatClient.tools(...)` with explicit `maxIterations`. |
| **Spring AI prompt/completion observation export** | Already disabled and locked by `LlmGatewayObservabilityTest` (privacy). v1.4 reinforces — even though briefing brief text is persistable, the **prompts** (which include past email metadata) are not. | Micrometer counters + traces, metadata labels only. |

---

## Stack Patterns by Variant

**If a request hits `/api/calendar/**` and needs a Calendar API client:**
- Controller in `backend/api/controllers/calendar/`
- Service in `backend/core/application/calendar/`
- `CalendarGateway` provider in `backend/core/calendar/gateway/google/` — the **only** package allowed to construct `Calendar.Builder` (new ArchUnit rule, sibling to the v1.3 Gmail rule and the v1.0 LLM rule).
- Per-request credential injection via `MailboxContext` (when the calendar belongs to a Gmail-tenant relationship) or a new `CalendarConnectionContext` ScopedValue (when the operation is calendar-only — e.g. the booking page POST writes to `destination_calendar_id` which is bound to a `calendar_connection_id` not a `gmail_connection_id`).

**If a request needs to write a calendar event (booking, `propose_meeting` confirm, briefing-attendee RSVP update):**
- Goes through `OutboundCalendarGateway.insertEvent(calendarConnectionId, calendarId, EventCommand)`.
- The gateway computes idempotency key, sets `createRequest.requestId = idempotencyKey` so Calendar API dedupes Meet creation, writes the audit row in the same `@Transactional`, then calls `Calendar`.
- Existing per-tenant outbound rate cap (v1.2 RACT-09) extended to count calendar writes alongside Gmail sends — single rate-limit pool per tenant; per-feature counters but one cap.

**If a request needs to read free/busy:**
- `FreeBusyGateway.query(connectionIds, calendarIds, timeMin, timeMax)` — read-only, no audit row needed (it's a query, not a state change). Cached per-`(connectionId, day)` in Redis for 60s to amortize multiple draft-reply availability checks within one user editing session.

**If the briefing cron runs:**
- Lives in `backend/worker` (Modulith module: `briefing`).
- Trigger: `ScheduledJob` that wakes every 5 min, queries `events.list` per connected calendar for the next 24h, picks events `start_at - now ∈ [briefing.hours_before - 5min, briefing.hours_before]` that don't yet have a `meeting_briefings` row.
- For each picked event: run the agentic AI loop (user-controlled `ToolCallingManager` to gate budget per step), persist `meeting_briefings`, fire `MeetingBriefingReady` Modulith event for the email/digest dispatcher.

**If an attachment arrives for filing:**
- `MessageObservedEvent` (v1.0) is consumed by `AttachmentFilingService` in `backend/worker`.
- For each attachment part: AI suggests folder (metadata only), then in-memory stream into Drive.
- If `confidence >= HIGH`: file directly, `document_filings.status = FILED`.
- If `confidence ∈ {LOW, MEDIUM}`: queue for review, `document_filings.status = NEEDS_REVIEW`, surface in `/filing/review`.

---

## Integration Points (where v1.4 touches v1.0 → v1.3)

| Touch point | v1.4 change | Risk |
|---|---|---|
| `libs.versions.toml` | Add 2 versions + 2 libraries (`calendarApi`, `driveApi`, `google-api-services-calendar`, `google-api-services-drive`). | Low — version pins independently chosen; no transitive conflict with existing Gmail artifact (verified Maven Central). |
| `backend/core/build.gradle.kts` | Two `api(...)` lines next to the existing Gmail line. | Low — additive. |
| `backend/api/application.yml` | Add two `ClientRegistration` entries (`google-calendar`, `google-drive`) sharing the existing client-id/secret env vars. | Low — additive; existing `google` registration untouched. |
| Existing `OAuth2AuthorizationRequestResolver` (v1.3) | Extend its switch statement with the two new registration IDs; reuse the same `additionalParameters` map (`include_granted_scopes=true`, `access_type=offline`, `prompt=consent`). | Low — same shape as the v1.3 GMA-07 split. |
| `OAuthTokenStore` (v1.0/v1.3) | Reused verbatim — `calendar_connections.refresh_token_ciphertext_b64` and `drive_connections.refresh_token_ciphertext_b64` are the same column shape as `gmail_connections`. No code change. | None — verified shape match. |
| `MailboxContext` ScopedValue (v1.3) | Add sibling `CalendarConnectionContext` and `DriveConnectionContext` ScopedValues. Same binding-filter pattern. Same ArchUnit rule against `findByTenantId` bypass extended to cover the new context types. | Low — pattern is well-trodden. |
| `OutboundSendGateway` (v1.2 RACT) | Add `OutboundCalendarGateway` sibling. The "no direct Gmail send outside the gateway" ArchUnit rule (RACT-12) gets a parallel rule for `Calendar` `events().insert/update/delete` and another for `Drive` `files().create/delete`. | Low — additive ArchUnit rules. |
| Per-tenant outbound rate cap (v1.2 RACT-09) | Extended to count calendar writes alongside Gmail sends — single rate-limit pool per tenant; per-feature counters but one cap. Configured per Calendar feature flag. | Low — Redis-backed counter already exists; one new key namespace. |
| `LlmGateway` + `LlmGatewayObservabilityTest` (v1.0) | Briefing agent goes through `LlmGateway` like every other LLM call. Observability test extended to cover briefing call-sites (no prompt/completion capture). | Low — additive call-sites, same gateway. |
| Liquibase changelogs | Ten new YAML files under `backend/core/src/main/resources/db/changelog/changes/`. Append to `db.changelog-master.yaml`. | Low — standard pattern. |
| ArchUnit rules | Add three: `CalendarBuilderConfinedToGateway`, `DriveBuilderConfinedToGateway`, `AttachmentBytesNotPersistedRule`. Sibling-shape to existing v1.0/v1.3 rules. | Low — same enforcement layer. |
| Logback `@Sensitive` scrub (existing) | Mark the six new `@Sensitive` columns. No new code. | None — additive annotations. |
| Micrometer + OTel (existing) | New counters: `zero_mail_calendar_event_total{op,connection_id,result}`, `zero_mail_drive_filing_total{result,confidence_bucket}`, `zero_mail_briefing_run_total{result,steps_used}`, `zero_mail_booking_created_total{slug}`. | Low — additive labels. |
| `apps/web` | New pages: `/calendar`, `/booking-links`, `/book/[slug]` (public), `/filing/review`. Compose existing shadcn primitives — zero new deps. | Low. |
| `apps/admin` | Read-only views of `calendar_connections`, `drive_connections`, `meeting_briefings` count, `document_filings` count per tenant. Same patterns as v1.2 admin tenant inspection. | Low. |

---

## Sources

**Context7 (HIGH confidence):**
- `/websites/developers_google_workspace_calendar_api` — Java client setup, `events.insert`, `events.list`, `freebusy.query` request/response shapes, `EventDateTime` timezone semantics. Fetched 2026-06-17.
- `/websites/developers_google_workspace_drive` — Java client setup, `files.create` with `FileContent` and `InputStreamContent`, `drive.file` scope semantics, folder-as-mimeType pattern. Fetched 2026-06-17.
- `/websites/spring_io_spring-ai_reference_2_0-snapshot` — `ChatClient.tools(...)` framework-controlled loop; `DefaultToolCallingManager` + `internalToolExecutionEnabled(false)` user-controlled loop; `MessageAggregator` streaming variant; `ToolExecutionResult` for conversation-history continuation. Fetched 2026-06-17. Pattern documented across Anthropic, Bedrock, DeepSeek, and Google GenAI chat pages — covers all four provider starters we already pin.

**Maven Central index probes (HIGH confidence):**
- `https://repo1.maven.org/maven2/com/google/apis/google-api-services-calendar/` — latest `v3-rev20260517-2.0.0`. Probed 2026-06-17.
- `https://repo1.maven.org/maven2/com/google/apis/google-api-services-drive/` — latest `v3-rev20260428-2.0.0`. Probed 2026-06-17.
- `https://repo1.maven.org/maven2/com/google/api-client/google-api-client/maven-metadata.xml` — current line `2.9.0` (verifies transitive compatibility with existing Gmail artifact). Probed 2026-06-17.
- `https://repo1.maven.org/maven2/com/google/http-client/google-http-client/maven-metadata.xml` — current line `2.1.0`. Probed 2026-06-17.

**Existing repo (HIGH confidence — single source of truth for v1.0-v1.3 baseline):**
- `gradle/libs.versions.toml` — current pins (Spring Boot 4.1.0, Spring AI 2.0.0, Modulith 2.1.0, Liquibase 5.0.3, `google-api-services-gmail v1-rev20250331-2.0.0`, `google-auth-library-oauth2-http 1.48.0`).
- `backend/core/build.gradle.kts` — existing `api(libs.google.api.services.gmail)` line and `implementation(libs.google.auth.library.oauth2.http)` provide the integration template.
- `apps/web/package.json` — `react-day-picker@^10.0.1`, `date-fns@^4.4.0`, `recharts@3.8.1` already present.
- `apps/web/components/ui/calendar.tsx`, `command.tsx`, `popover.tsx`, `radio-group.tsx`, `card.tsx` — all already present (verified `ls` 2026-06-17).
- CLAUDE.md "Hard do-not-use list" — `spring-cloud-gcp`, vector DB, raw vendor SDKs outside the locked LLM adapter package (extended in v1.4 to Calendar/Drive gateways).
- Memory notes — bundled OAuth scopes (extended carefully in v1.4 via user-initiated incremental grants on opt-in features, not auto-prompts), draft_body carve-out (extended in v1.4 to `meeting_briefings.brief_markdown` for the same reason), raw shadcn first, skip de-risking spikes.

---

*Stack research for: Zero Mail v1.4 — Google Calendar Co-Pilot + Drive Filing.*
*Researched: 2026-06-17 by gsd-researcher (Context7 Calendar/Drive/Spring AI + Maven Central index probes + existing repo state).*
