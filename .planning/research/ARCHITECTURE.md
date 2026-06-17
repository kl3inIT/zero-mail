# Architecture Research — Zero Mail v1.4 (Calendar Co-Pilot + Drive Filing)

**Domain:** Integration of Google Calendar (free/busy + events + booking) and Google Drive (in-memory attachment filing) into the v1.3 multi-mailbox Spring Modulith backend + Next.js frontend.
**Researched:** 2026-06-17
**Confidence:** HIGH on package layout / Modulith boundaries / ArchUnit pattern reuse (verified against v1.0–v1.3 module list in `backend/core/src/main/java/com/zeromail/core/`: `outbound`, `mailbox`, `inbox`, `messaging`, `composer`, `support` already exist alongside the v1.0/v1.1/v1.2 modules — Calendar/Drive slot in as siblings). HIGH on the v1.3 `MailboxContext` ScopedValue + binding-filter pattern (the rule we must coexist with). HIGH on the v1.2 `OutboundSendGateway` single-call-site shape. MEDIUM-HIGH on Calendar/Drive Spring Security OAuth2 incremental authorization (verified against Spring Security 6.5+ docs — `OAuth2AuthorizedClientRepository` supports per-`registrationId` storage so a second registration is additive, not breaking). MEDIUM on Spring AI 2.0.0 GA streaming + tool-call loop for the agentic meeting brief — same primitive as v1.1 chat, scaled down to a non-streaming batched call.

> **Scope.** This is the **v1.4 architecture delta only**. The v1.0–v1.3 baseline (Modulith module list, Scoped Values tenant/mailbox context, single `LlmGateway`, `OutboundSendGateway` boundary, ARCH-02 privacy invariants, Liquibase YAML, feature-folder layout, OpenAPI codegen) is locked. This document only describes new modules, new gateways, new tables, new ArchUnit gates, and new dependency edges introduced by Calendar + Drive. The v1.1 chat + v1.2 admin architecture content lives in this file's git history.

---

## Executive Summary

**Two new top-level Spring Modulith modules in `backend/core`:**

1. `core.calendar` — connection registry, free/busy gateway, event-write gateway, booking-link configuration, public booking handler, calendar-aware triage classifier, `propose_meeting` action adapter, agentic meeting-brief use case.
2. `core.drive` — connection registry, Drive gateway (folder list + file upload), in-memory filing engine (no `attachment_document` table), attachment-source rule adapter.

**One modified existing module:**

- `core.outbound` — `OutboundSendGateway` keeps ownership of all Gmail send execution; a new sibling `core.calendar.gateway.google.CalendarOutboundGateway` owns all Google Calendar event writes (events `insert`/`patch`/`delete`). `propose_meeting` is a **two-stage compound action**: stage 1 (free/busy read) goes through `CalendarReadGateway`; stage 2 (draft a Gmail reply with the suggested slots) goes through the existing `OutboundSendGateway` and inherits its v1.2 Auto-send + safety-net + rate-cap + idempotency + audit gates unchanged.

**Connection model (locked recommendation):**

- Calendar connections are **workspace-shared** (per-tenant, not per-`gmail_connection`). Free/busy is unioned across all connected calendars (matching IZ's `unified-availability` UX) and any active mailbox in the workspace reads the same availability. **Justification:** the user-mental-model is "my schedule" not "my schedule for inbox A vs inbox B"; calendar resources are workspace assets like a shared template.
- Drive connections are also **workspace-shared** (per-tenant). Filing rules belong to a mailbox (because rules already do per v1.3 `AUTO-01`), but the destination Drive workspace they file into is workspace-shared.
- This creates **one new architectural seam: workspace-shared resource × mailbox-isolated rule × ScopedValue mailbox context.** Section 11 documents how reads from a mailbox-scoped service into a workspace-shared resource work without bypassing `MailboxContext`.

**OAuth incremental authorization:**

- A second `ClientRegistration` `google-calendar` (scopes `calendar.freebusy` + `calendar.events`) and a third `google-drive` (scope `drive.file`) are registered additively to the existing bundled `google` login registration. The v1.3 login flow is untouched — no scopes added to the login bundle.
- Frontend triggers incremental grant via `GET /api/calendar/connect/start` → 302 to `/oauth2/authorization/google-calendar?intent=calendar-connect`. Spring Security stores the resulting tokens in a per-registration row (`calendar_connection.refresh_token_ciphertext`), distinct from `gmail_connection.refresh_token_ciphertext`.

**ArchUnit gates added in v1.4 (9 new tests, listed in full in the ArchUnit Tests section):**

1. `CalendarOutboundGatewayCallSiteAllowlistTest` — only `CalendarOutboundGateway` may call `Calendar.Events.insert/patch/delete`.
2. `DriveUploadCallSiteAllowlistTest` — only `DriveGateway` may call `Drive.Files.create`.
3. `AttachmentBytesInMemoryOnlyTest` — composite: no `BYTEA`/`OID`/`@Lob` in any `drive_*` / `document_filing` / `attachment_source` table; no `byte[]` field outside `core.drive.application.AttachmentBytesCarrier`.
4. `MailboxScopedReadIntoWorkspaceResourceTest` — any service that calls `MailboxContext.required()` and also injects `CalendarConnectionRepository` / `DriveConnectionRepository` fails; must use `WorkspaceResourceLookup`.
5. `PublicBookingChainIsolationTest` — `/api/public/**` reachable only via the new `@Order(40)` filter chain.
6. `MeetingBriefAuditNoPromptTextTest` — `meeting_brief_audit` columns include no `prompt_text`/`completion_text`; only fingerprints + tokens + cost.
7. `CalendarConnectionRefreshTokenAesGcmTest` — `calendar_connection.refresh_token_ciphertext` is `bytea`; changelog never references `pgp_sym_encrypt`.
8. `CalendarModuleBoundaryTest` + `DriveModuleBoundaryTest` — Modulith `allowedDependencies` lists match the documented sets.

**Build order (locked):**

1. **Phase A — OAuth foundation** (Calendar + Drive `ClientRegistration` + storage + revoke + reconnect; no behavior yet).
2. **Phase B — Calendar reads** (`CalendarReadGateway` free/busy; UI shows connection state and free/busy debug view; no draft integration yet).
3. **Phase C — Calendar-aware draft replies + AI availability tool** (draft pipeline gains a `suggestMeetingSlots` step; LLM tool call returns slots).
4. **Phase D — Booking links** (config CRUD; public `/book/[slug]` handler; `CalendarOutboundGateway` event write).
5. **Phase E — Calendar-aware triage** (invite/cancellation/reschedule classifier; new digest section).
6. **Phase F — `propose_meeting` rule action** (compound action through existing `OutboundSendGateway`).
7. **Phase G — AI meeting briefs** (worker cron + agentic Spring AI tool loop + Gmail send via `OutboundSendGateway`).
8. **Phase H — Drive connection + in-memory filing engine** (foundation in-memory bytes carrier + AI suggest + `DriveGateway.uploadFile`; rule-driven filing on new attachment).
9. **Phase I — Attachment-source rules** (rule action attaches files pulled from a curated Drive folder onto outbound replies; reuses in-memory bytes carrier + `OutboundSendGateway` raw-RFC2822 path).

Dependencies are linear: A → B → C, D, E, F (parallel after B), G (after C+F), H → I.

---

## System Overview (v1.4 additions on top of v1.3)

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                              apps/web (Next.js 16, React 19)                          │
├──────────────────────────────────────────────────────────────────────────────────────┤
│   v1.3 routes (kept):  /, /login, /(app)/inbox, /(app)/rules, /(app)/chat, ...        │
│                                                                                       │
│   NEW v1.4 routes:                                                                    │
│     app/(protected)/(app)/calendar/page.tsx        ← Calendar settings + booking list │
│     app/(protected)/(app)/calendar/links/[id]/page.tsx ← Booking-link editor          │
│     app/(protected)/(app)/calendar/briefs/page.tsx ← Meeting-brief history + config   │
│     app/(protected)/(app)/drive/page.tsx           ← Drive connections + filing review│
│     app/(public)/book/[slug]/page.tsx              ← PUBLIC booking page (no auth)    │
│     app/(public)/book/[slug]/confirmed/page.tsx    ← post-booking confirmation        │
│                                                                                       │
│   NEW v1.4 features:                                                                  │
│     features/calendar/{api,components,hooks,query-keys.ts,messages.ts}                │
│     features/booking-links/{api,components,hooks,query-keys.ts,messages.ts}           │
│     features/meeting-briefs/{api,components,hooks,query-keys.ts,messages.ts}          │
│     features/drive/{api,components,hooks,query-keys.ts,messages.ts}                   │
│     features/public-booking/{api,components,hooks,messages.ts}                        │
│       └── NO query-keys.ts — public booking is one-shot, no cache.                    │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          │  HTTP same-origin behind reverse proxy
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                       backend/api (Spring MVC, Tomcat + virtual threads)              │
├──────────────────────────────────────────────────────────────────────────────────────┤
│   v1.3 controllers (kept).                                                            │
│                                                                                       │
│   NEW v1.4 controllers (controllers/calendar/, dto/calendar/):                        │
│     CalendarConnectionController       /api/calendar/connections (start/list/revoke)  │
│     CalendarSelectionController        /api/calendar/connections/{id}/calendars       │
│     CalendarAvailabilityController     /api/calendar/availability (workspace-unified) │
│     BookingLinkController              /api/calendar/booking-links (CRUD)             │
│     PublicBookingController            /api/public/book/{slug}  (NO session — sep chain)│
│     MeetingBriefController             /api/calendar/briefs (config + history)        │
│                                                                                       │
│   NEW v1.4 controllers (controllers/drive/, dto/drive/):                              │
│     DriveConnectionController          /api/drive/connections (start/list/revoke)     │
│     DriveFilingController              /api/drive/filings (list + review + correct)   │
│     AttachmentSourceController         /api/drive/attachment-sources (CRUD)           │
│                                                                                       │
│   SecurityConfig adds: a second SecurityFilterChain @Order(40) for /api/public/**     │
│     — sessionless, CSRF off (idempotency-token guarded), Captcha gate, rate-limited.  │
│     Existing user chain (@Order(50)) and admin chain (@Order(1)) untouched.           │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          │  in-process service calls
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                       backend/core (Spring Modulith modules)                          │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  v1.3 modules: tenant account gmail llm rules triage draft thread analytics billing   │
│                notification onboarding chat outbound mailbox inbox messaging composer │
│                queue cleanup admin support shared.*                                   │
│                                                                                       │
│  NEW v1.4 Modulith module:  com.zeromail.core.calendar                                │
│    Allowed deps: tenant, mailbox, account, llm, gmail, draft, thread, outbound,       │
│                  rules, triage, analytics, billing, notification, queue,              │
│                  shared.persistence, shared.lang, shared.privacy, shared.crypto       │
│                                                                                       │
│    Sub-packages:                                                                      │
│     calendar/                                                                         │
│      ├── package-info.java                                                            │
│      ├── domain/             CalendarProvider (GOOGLE), CalendarConnectionStatus,     │
│      │                       BookingSlotDuration, BusyPeriod (record), LocationType,  │
│      │                       BookingLinkStatus, MeetingBriefStatus,                   │
│      │                       CalendarInviteIntent (INVITE|CANCEL|RESCHEDULE|RSVP),    │
│      │                       @AllowedCalendarWriteCallSite (marker)                   │
│      ├── application/        CalendarConnectionService, CalendarSelectionService,     │
│      │                       UnifiedAvailabilityService (cross-connection union),     │
│      │                       SuggestMeetingSlotsService (AI tool backing),            │
│      │                       BookingLinkService (CRUD), PublicBookingService,         │
│      │                       BookingIdempotencyService (Redis),                       │
│      │                       MeetingBriefScheduler (worker entry), MeetingBriefAgent  │
│      │                       (Spring AI tool loop), CalendarInviteClassifier,         │
│      │                       ProposeMeetingActionAdapter,                             │
│      │                       WorkspaceResourceLookup (the seam — see §11),            │
│      │                       commands/results records                                 │
│      ├── gateway/            CalendarReadGateway        — Calendar.FreeBusy.query     │
│      │                       CalendarOutboundGateway    — Calendar.Events.insert/...  │
│      │                       (both inside gateway.google.* — same confinement model   │
│      │                       as llm.gateway.springai)                                 │
│      ├── projection/         CalendarConnectionProjection, BookingLinkProjection,     │
│      │                       MeetingBriefProjection, UnifiedAvailabilityProjection    │
│      ├── persistence/        CalendarConnectionEntity, CalendarSelectionEntity,       │
│      │                       BookingLinkEntity, BookingLinkSlotEntity,                │
│      │                       BookingEntity (writes from /public/book),                │
│      │                       MeetingBriefEntity, MeetingBriefAuditEntity,             │
│      │                       *Repository interfaces                                   │
│      └── exception/          CalendarConnectionNotFoundException,                     │
│                              CalendarScopeMissingException, BookingSlotConflictException,│
│                              BookingLinkSlugTakenException, ...                       │
│                                                                                       │
│  NEW v1.4 Modulith module:  com.zeromail.core.drive                                   │
│    Allowed deps: tenant, mailbox, account, llm, gmail, outbound, rules, queue,        │
│                  notification, shared.persistence, shared.lang, shared.privacy,       │
│                  shared.crypto                                                        │
│                                                                                       │
│    Sub-packages:                                                                      │
│     drive/                                                                            │
│      ├── package-info.java                                                            │
│      ├── domain/             DriveProvider (GOOGLE), DriveConnectionStatus,           │
│      │                       FilingDecision (FILE|ASK|SKIP),                          │
│      │                       FilingConfidence (LOW|MEDIUM|HIGH),                      │
│      │                       AttachmentMetadata (record: id, filename, mimeType,size, │
│      │                                            messageId, sender) — NO body field, │
│      │                       FolderSuggestion, @AllowedDriveUploadCallSite (marker)   │
│      ├── application/        DriveConnectionService, FolderListingService,            │
│      │                       FilingEngine (in-memory orchestrator),                   │
│      │                       AttachmentBytesCarrier (try-with-resources, zero-on-close)│
│      │                       AnalyzeDocumentService (LLM call: text-extract metadata) │
│      │                       AttachmentSourceService (rule adapter for outbound)      │
│      ├── gateway/            DriveGateway              — Drive.Files.create / list    │
│      │                       (inside gateway.google.*)                                │
│      │                       DocumentTextExtractor    — Tika or Google Doc convert    │
│      ├── projection/         DriveConnectionProjection, FilingProjection (METADATA    │
│      │                       ONLY: filename, folderPath, confidence, status — NO body)│
│      ├── persistence/        DriveConnectionEntity, DocumentFilingEntity (metadata    │
│      │                       only), AttachmentSourceEntity                            │
│      └── exception/          DriveConnectionNotFoundException, FilingDuplicateException,│
│                              DriveScopeMissingException                               │
│                                                                                       │
│  v1.4 changes to EXISTING modules (small):                                            │
│   account/    ADD CalendarConnectedEvent / DriveConnectedEvent publication on initial │
│                connect (existing pattern: GmailConnectedEvent)                        │
│   outbound/   ADD an internal API ProposeMeetingDraftRequest record consumed by       │
│                ProposeMeetingActionAdapter; OutboundSendGateway boundary unchanged.   │
│                ADD attachment-bytes carrier parameter to send raw RFC2822 with        │
│                in-memory attachments (Phase I); ArchUnit ensures the bytes carrier    │
│                is scope-limited and not persisted.                                    │
│   triage/     ADD a CalendarInvite-aware top-of-inbox classification step             │
│                downstream of existing semantic-intent triage. Tagged via labels +     │
│                a new triage_audit `reason_code` value `CALENDAR_INVITE_PROTECTED`.    │
│   rules/      ADD propose_meeting and attach_drive_source RuleAction types (When/Then │
│                schema extended — additive change, no migration of existing rules).    │
│                Action handlers live in calendar / drive modules, dispatched via       │
│                existing RuleActionDispatcher SPI.                                     │
│   notification/  ADD digest sections: "meeting briefs today", "attachments filed",    │
│                "attachments needing review".                                          │
│   queue/      NO change — new processing_job kinds added enumerably:                  │
│                CALENDAR_MEETING_BRIEF, DRIVE_ATTACHMENT_FILING.                       │
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          │  JDBC
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                           PostgreSQL 18.4 (same VPS)                                  │
├──────────────────────────────────────────────────────────────────────────────────────┤
│  NEW v1.4 tables (changelogs 129+, all append-only per Convention 10):                │
│   129-calendar-connection.yaml       calendar_connection (workspace-shared)           │
│   130-calendar-selection.yaml        calendar_selection (per-calendar enable + tz)    │
│   131-booking-link.yaml              booking_link + booking_link_slot                 │
│   132-booking.yaml                   booking (public writes; UNIQUE on idempotency)   │
│   133-meeting-brief.yaml             meeting_brief + meeting_brief_audit              │
│   134-drive-connection.yaml          drive_connection (workspace-shared)              │
│   135-document-filing.yaml           document_filing  (METADATA ONLY — NO body bytes) │
│   136-attachment-source.yaml         attachment_source (rule-scoped curated folder)   │
│   137-rule-action-extensions.yaml    rule_action JSONB schema extension validators    │
│   138-calendar-audit.yaml            calendar_audit (per-domain audit table)          │
│   139-drive-audit.yaml               drive_audit (per-domain audit table)             │
│   140-audit-unified-view.yaml        DB VIEW audit_unified_v UNIONing the 4 audit srcs│
└──────────────────────────────────────────────────────────────────────────────────────┘
                                          ▲
                                          │  Redis 7 (same VPS)
                                          │
┌──────────────────────────────────────────────────────────────────────────────────────┐
│   Spring Session Redis (existing) + NEW Redis namespaces:                             │
│     booking:idempotency:{slug}:{token}   TTL=24h  — public booking dedupe              │
│     booking:slot-lock:{calId}:{startIso} TTL=30s  — short lease while writing event    │
│     drive:filing:dedup:{tenantId}:{messageId}:{attachmentId} TTL=24h                   │
│     calendar:freebusy:{calId}:{rangeHash} TTL=60s — cache (no PII; calendarId only)    │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Answers to the 12 Numbered Architecture Questions

### 1. Module placement — two new top-level Modulith modules

**Decision:** `core.calendar` and `core.drive` are new top-level Modulith modules (siblings of `core.gmail`, `core.outbound`, `core.chat`, etc.). **Not** sub-packages of `core.gmail`, **not** combined into `core.google-integrations`.

**Why:**

- `core.gmail` is a single-responsibility gateway+ingestion module (Pub/Sub watch refresh, mailbox-scoped client factory, raw Gmail message read). Inflating it with Calendar + Drive widens its public surface and forces every `gmail` consumer to recompile against unrelated APIs.
- A combined `core.google` module obscures the three independent OAuth grants (login bundle, calendar grant, drive grant) and the three independent revoke flows. Each lifecycle deserves its own dependency-direction module.
- `core.outbound` already exists as a horizontal write gateway for Gmail send; the Calendar equivalent (`CalendarOutboundGateway` for event writes) parallels it cleanly **inside** `core.calendar.gateway` rather than being absorbed into `core.outbound` — see Q4.

**Spring Modulith boundaries (locked):**

`core.calendar.package-info.java`:

```java
@ApplicationModule(
    displayName = "Calendar",
    allowedDependencies = {
        "tenant", "mailbox", "account", "llm",
        "gmail", "draft", "thread", "outbound",
        "rules", "triage", "analytics", "billing", "notification", "queue",
        "shared.persistence", "shared.lang", "shared.privacy", "shared.crypto"
    })
package com.zeromail.core.calendar;
```

`core.drive.package-info.java`:

```java
@ApplicationModule(
    displayName = "Drive",
    allowedDependencies = {
        "tenant", "mailbox", "account", "llm",
        "gmail", "outbound", "rules", "queue", "notification",
        "shared.persistence", "shared.lang", "shared.privacy", "shared.crypto"
    })
package com.zeromail.core.drive;
```

Both modules are wider than most v1.0 modules but narrower than `core.chat` (which adds `triage`, `analytics` and `draft` for tool-call dispatch). The width is justified: each is the cross-cutting wire between an external Google API and 5–10 internal capabilities. Add a `ModuleBoundaryTest` per module that asserts the allowedDependencies list matches `package-info.java` (already a pattern from v1.1/v1.2).

Internal sub-package layout follows Convention 2 (`domain/`, `application/`, `gateway/`, `projection/`, `persistence/`, `exception/`). The only deviation from earlier modules is using `gateway/` as a sibling of `application/` (rather than collapsing both into `usecases/`) because the gateway is a meaningful Spring AI–style confinement boundary worth elevating.

### 2. Connection model — workspace-shared for both Calendar and Drive

**Recommendation:** Calendar = workspace-shared. Drive = workspace-shared. Both are per-tenant resources, **not** per-`gmail_connection`.

**Why workspace-shared for Calendar:**

- User mental model: "my calendar" not "my-calendar-for-mailbox-A vs my-calendar-for-mailbox-B". A user with two Gmails (personal + work) still has one schedule they must not double-book.
- Free/busy union across all enabled calendars across all connections (the IZ `unified-availability` pattern is correct UX; we keep it but route through the workspace boundary instead of `emailAccountId`).
- Booking links advertise availability and write events to a destination calendar — this destination is a property of the booking link, not of the active mailbox. A user can build "30-min sales call" routed to their work calendar and "1:1 coffee" routed to their personal calendar regardless of which Gmail mailbox is currently active.
- Calendar grants are coarser than Gmail watches: a single Google account often owns both Gmail and Calendar. Forcing 1:1 with `gmail_connection` would require a separate Calendar grant per Gmail account even though Google ties them.

**Why workspace-shared for Drive:**

- Drive is also account-coarse and users think "Drive" not "Drive-for-this-inbox". Filing destination is a workspace organization choice.
- Attachment-source rule action (Phase I) attaches files curated by the workspace, not by a mailbox; one rule per workspace covers "always attach my deck when replying to investors".
- The downside (a Drive connection for the work Google account cannot see Drive folders in the personal Google account) is *acceptable* because the user can connect multiple Drive accounts to the workspace — same model as multi-Gmail in v1.3, but the destination selector is per-booking-link / per-filing-rule, not per-active-mailbox.

**Schema seam:**

```sql
calendar_connection (
  id              uuid PRIMARY KEY,
  tenant_id       uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  -- NOTE: no gmail_connection_id FK. Independent OAuth grant.
  google_account_email_hash bytea NOT NULL,   -- AES-GCM-encrypted lookup hash (matches gmail_connection)
  refresh_token_ciphertext  bytea NOT NULL,   -- app-layer AES-GCM (project ban on pgp_sym_encrypt)
  status                    text NOT NULL,    -- CONNECTED | DISCONNECTED | REVOKED
  primary_calendar_id       text,             -- "primary" or specific Google calendar id
  default_timezone          text,
  created_at, updated_at,
  CONSTRAINT calendar_conn_active_one UNIQUE (tenant_id, google_account_email_hash)
    WHERE status = 'CONNECTED'
);
```

The same shape for `drive_connection`. Both are sibling tables of `gmail_connection`, not foreign-keyed to it.

### 3. OAuth incremental authorization — additive ClientRegistrations, dedicated callback handlers

**The v1.3 bundled-login registration is locked.** `ClientRegistration[id=google]` scopes stay `openid email profile` + the v1.3 Gmail scopes. Do **not** add calendar/drive scopes to it.

**Two new `ClientRegistration`s, registered through `ClientRegistrationRepository`:**

```yaml
# backend/api/src/main/resources/application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:           # v1.3 bundled login — UNCHANGED
            scope: openid,email,profile,https://www.googleapis.com/auth/gmail.modify,https://www.googleapis.com/auth/gmail.send,...
          google-calendar:  # NEW
            provider: google
            client-id: ${GOOGLE_CLIENT_ID}            # same client; just additional scopes
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - https://www.googleapis.com/auth/calendar.freebusy
              - https://www.googleapis.com/auth/calendar.events
            redirect-uri: "{baseUrl}/login/oauth2/code/google-calendar"
            client-name: Google Calendar
            authorization-grant-type: authorization_code
          google-drive:     # NEW
            provider: google
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - https://www.googleapis.com/auth/drive.file
            redirect-uri: "{baseUrl}/login/oauth2/code/google-drive"
            client-name: Google Drive
            authorization-grant-type: authorization_code
```

**Why this doesn't break v1.3 login:**

- Spring Security `OAuth2AuthorizedClientRepository` is keyed on `(registrationId, principalName)`. Storing a `google-calendar` token does not overwrite a `google` token.
- The v1.3 `SecurityFilterChain` for `/api/**` still rejects unauthenticated requests; the OAuth login filter chain only triggers for `/oauth2/authorization/*` and `/login/oauth2/code/*`. Adding two registration ids adds two new authorize/callback URIs without touching the existing login path.
- We use a **custom `OAuth2AuthorizedClientService`** that, for `google-calendar` / `google-drive` registrations only, delegates to `CalendarConnectionService.persist(...)` / `DriveConnectionService.persist(...)` after AES-GCM-encrypting the refresh token. This bypasses Spring's default Postgres-table-per-authorized-client storage for these registrations and lands tokens in `calendar_connection.refresh_token_ciphertext` / `drive_connection.refresh_token_ciphertext`. The `google` (login) registration continues to use the v1.3 storage path unchanged. (This is the same dual-path pattern that v1.3 implemented for the OAuth intent split — `Authentication` token vs `Reconnect` token go to different tables; we extend by `registrationId`.)

**Frontend incremental-grant initiation:**

```
[User clicks "Connect Calendar" on /calendar settings page]
    ↓ GET /api/calendar/connect/start  (POST is fine too; current pattern)
[CalendarConnectionController.start()] (in backend/api)
    ↓ generates anti-CSRF state token, stores in Redis with TTL=10min
    ↓ returns 302 to /oauth2/authorization/google-calendar?intent=connect&state=<token>
[Browser redirects to Google consent screen for calendar.freebusy + calendar.events]
    ↓ user grants → Google → /login/oauth2/code/google-calendar?code=...&state=...
[Spring Security exchanges code → AuthorizedClient]
    ↓ our custom OAuth2AuthorizedClientService routes the google-calendar registration to
[CalendarConnectionService.persist(tenantId, refreshToken, accessToken, googleAccountEmail)]
    ↓ AES-GCM encrypt, INSERT into calendar_connection
    ↓ publish CalendarConnectedEvent (Spring Modulith)
[Default success handler redirects to /calendar?connected=1]
```

**Anti-pattern guardrails:** the bundled-login registration must never silently widen — an `OAuth2LoginConfigurer` registration test asserts the `google` registration's scopes are exactly the v1.3 set.

### 4. Free/busy gateway — `CalendarReadGateway` + `CalendarOutboundGateway`, ArchUnit-locked

**Decision:** Mirror the `OutboundSendGateway` boundary, but **per-domain** rather than centralized.

- `core.calendar.gateway.google.CalendarReadGateway` — the only caller of `Calendar.FreeBusy.query` and `Calendar.CalendarList.list` / `Calendar.Calendars.get`.
- `core.calendar.gateway.google.CalendarOutboundGateway` — the only caller of `Calendar.Events.insert` / `patch` / `delete`. Annotated `@AllowedCalendarWriteCallSite`.

**Why split read and write:** the read gateway has a tight freshness contract (60-second Redis cache, soft-fail open if Google API is down — falls back to "availability data temporarily unavailable" rather than blocking draft). The write gateway has a hard-fail contract (must not silently retry — booking writes are idempotent only when the caller supplies a Redis-issued idempotency token; see Q5). Putting both behind one class invites mixing the semantics.

**Why NOT extend `core.outbound.OutboundSendGateway`:** that gateway exists specifically for Gmail RFC 2822 send semantics (auto-send gate, sender safety net, rate cap, idempotency, send-audit). Calendar event writes have a different gate set: no sender safety net (writes are to user's own calendar, not an external recipient), no rate cap shared with Gmail (Calendar quota is independent), and a different idempotency surface (event id + slot conflict). Forcing Calendar through `OutboundSendGateway` would require parameterizing the gate set, defeating the "single-call-site for Gmail send" invariant that ArchUnit greps for.

**Each gateway is its own ArchUnit allowlist** (Patterns 4 from v1.1, extended): three marker annotations (`@AllowedSendCallSite` already exists for v1.1 `AssistantSendExecutor`; v1.2 generalized the v1.1 carve-out into `OutboundSendGateway`'s marker; add `@AllowedCalendarWriteCallSite`, `@AllowedDriveUploadCallSite`), three single-call-site allowlist tests, three repo-wide grep gates in CI. The marker annotation pattern (rather than path-based allowlist) keeps the test refactor-safe.

`propose_meeting` uses **both gateways in series**: `CalendarReadGateway` for the free/busy lookup (stage 1), `OutboundSendGateway` for the actual outbound Gmail reply that contains the slot list (stage 2). It does **not** call `CalendarOutboundGateway` — `propose_meeting` does not write an event; it drafts a reply offering slots and waits for the recipient to pick one (which then routes through a booking link or a manual confirmation).

### 5. Public booking page security — sessionless `SecurityFilterChain` with idempotency + DB UNIQUE

**Five-layer guard for `POST /api/public/book/{slug}`:**

1. **Dedicated `SecurityFilterChain` at `@Order(40)`**, matching `/api/public/**` only. `csrf().disable()` (no session → no CSRF cookie to verify), `sessionManagement().sessionCreationPolicy(STATELESS)`, no `OAuth2Login`. The v1.3 user chain (`@Order(50)`) and admin chain (`@Order(1)`) match different paths, so adding the public chain is additive and ordered above the user fallthrough.
2. **Slug resolution into tenant context.** A custom `PublicBookingFilter` reads `/api/public/book/{slug}`, looks up `booking_link.tenant_id` via `BookingLinkRepository.findBySlug(slug)`, and binds `TenantContext` (and `MailboxContext` to a `MailboxRef.SYSTEM_NONE` sentinel — the booking write is workspace-scoped, not mailbox-scoped). Binding tenant context off a public slug is safe because `slug` is opaque, tenant-anonymous, and rate-limited; the binding is necessary so downstream services that assert `TenantContext.required()` work uniformly.
3. **Idempotency token, server-issued.** `GET /api/public/book/{slug}/availability` returns the available slots plus a one-shot `bookingIdempotencyToken` stored in Redis at `booking:idempotency:{slug}:{token}` with TTL=24h. The subsequent `POST /api/public/book/{slug}` must include this token; the booking handler `DEL`s the token before INSERTing the `booking` row. A second submit with the same token returns the original booking record (200), not a duplicate.
4. **DB UNIQUE constraint AS the source of truth for double-booking prevention**, not application logic. `booking` table:

   ```sql
   booking (
     id uuid PRIMARY KEY,
     booking_link_id uuid NOT NULL REFERENCES booking_link(id) ON DELETE CASCADE,
     destination_calendar_id text NOT NULL,
     starts_at timestamptz NOT NULL,
     ends_at   timestamptz NOT NULL,
     guest_email text NOT NULL,
     idempotency_token_hash bytea NOT NULL,
     google_event_id text,                       -- nullable until Calendar API write completes
     status text NOT NULL,                        -- PENDING | CONFIRMED | FAILED
     created_at, updated_at,
     CONSTRAINT booking_one_slot_per_calendar UNIQUE (destination_calendar_id, starts_at),
     CONSTRAINT booking_one_per_idem UNIQUE (idempotency_token_hash)
   );
   ```

   Two races for the same slot: first INSERT wins, second gets `23505 unique_violation` → handler returns 409 with the alternate slot list. **Why DB UNIQUE and not application-locked:** application locks are racy across replicas / restarts; Postgres UNIQUE is the only durable guarantee. (Redis `booking:slot-lock` is a 30-second performance cushion to avoid the lossy "two users see the same slot in the picker for 5 seconds" UX, *not* the correctness guarantee.)
5. **Captcha decision — yes, hCaptcha, gated by failure rate.** A first-time public booking from a fresh IP/UA gets a hCaptcha challenge embedded in the booking form. Captcha verification token validated server-side before idempotency-token issuance. **Rationale:** booking links are spam-magnets (scheduling spam bots scrape Calendly-style links); the cost of one bad invite landing in a user's calendar is high (trust break). hCaptcha is the established choice (CASA-compatible, EU-data-resident option, free tier covers v1.4 scale). Captcha gate is configurable per booking link (default ON; user can disable for internal links).

After the `booking` row is INSERTed (status=PENDING), the handler calls `CalendarOutboundGateway.createEvent(...)`; on success updates status=CONFIRMED + `google_event_id`; on failure marks FAILED and returns a generic error to the user (do not leak Calendar API errors to a public page).

### 6. AI meeting briefs — worker cron + Spring AI tool loop, no body persistence

**Location:** `backend/worker` is the host; the cron + the agentic loop both live in `backend/worker` Spring modules that delegate to `core.calendar.application.MeetingBriefAgent` for the Spring AI tool-call orchestration.

**Pattern:**

- `MeetingBriefScheduler` (worker `@Scheduled` job, ShedLock-coordinated) runs every 5 minutes. Queries `calendar_selection` for events starting in (`now + brief_lead_hours`, `now + brief_lead_hours + 5min`) for tenants who have meeting briefs enabled. Inserts one `processing_job` row per (tenant, mailbox-of-record, event_id) with kind=`CALENDAR_MEETING_BRIEF`.
- A worker consumer (existing `processing_job` `SKIP LOCKED` poll) leases each job and calls `MeetingBriefAgent.brief(tenantId, mailboxId, eventId)`.
- `MeetingBriefAgent` runs a **non-streaming** Spring AI tool loop with three tools:
  - `searchGuest(emailAddress)` — calls `core.account.GuestLookupService` (which reads `messaging_conversation` + projection — no body).
  - `checkPastEmails(emailAddress, count=10)` — calls `core.gmail.GmailApiClientFactory.client(tenantId, mailboxId).users().messages().list(...)` filtered by from/to address, returns **subject + snippet + dates only** (per ARCH-02). The full bodies are held in a **request-scoped in-memory cache** (TTL = single brief turn) keyed by `messageId`; the LLM sees a body excerpt only inside the in-flight prompt, the tool **output** persisted in `meeting_brief_audit` carries snippet (≤120 chars) only — same anti-pattern guardrail as v1.1 chat's `readEmail`.
  - `checkPastMeetings(emailAddress, count=5)` — queries `meeting_brief` (the brief table itself stores brief output text, which is **assistant-generated metadata about the meeting**, not user email content, so it is persistable). Returns prior brief summaries.

**Privacy compliance:**

- Email bodies fetched during the brief stay in `MeetingBriefAgent`'s request-scoped `AttachmentBytesCarrier`-style buffer (reused conceptually from Drive — see Q7). When the brief turn ends, the buffer is closed and the bytes are zeroed.
- The brief **output text** (assistant-written summary) IS persisted in `meeting_brief.summary_text` — same carve-out as v1.1 chat `draft_body` (user-or-assistant-authored text is allowed; extracted Gmail body is not). The summary contains the AI's *interpretation*, not the raw quotes.
- `meeting_brief_audit` stores prompt **fingerprint** (sha256) and completion **fingerprint** + model + tokens + cost, never the prompt/completion text. Same pattern as v1.0's `llm_call_audit`.

**Gmail delivery of the brief:** the brief is emailed via the existing `OutboundSendGateway` (using a system-generated `send_email` request marked `system=true` to bypass user-facing Auto-send gate; the brief is internal trust — sending the user a brief about their own upcoming meeting). It is also surfaced inside the existing in-app daily digest channel (notification module's digest aggregator picks up `meeting_brief` rows for "today").

**Why worker (not API):** brief generation is asynchronous (5-minute cron tick), long-running (~10–30s including 2–3 LLM tool calls), and must survive API restarts. Same shape as the v1.0 triage worker.

**Spring Modulith event boundary:** the worker module subscribes to nothing for this flow — it polls `processing_job`. It emits one event after success: `MeetingBriefSent` (for analytics + digest). It does NOT use Spring Modulith events to *trigger* brief generation, because cross-process (API → worker) handoff goes through `processing_job` per Convention 6.

### 7. Drive in-memory-only attachment streaming — `AttachmentBytesCarrier` + 4-layer enforcement

**The core invariant:** attachment bytes flow `Gmail API stream → AttachmentBytesCarrier → DocumentTextExtractor (text only) + DriveGateway.uploadFile (raw bytes upload) → discard`. The bytes never touch the disk, never hit `INSERT`, never hit a log line.

**`AttachmentBytesCarrier` shape:**

```java
// core.drive.application.AttachmentBytesCarrier — package-private, never returned across module boundary
final class AttachmentBytesCarrier implements AutoCloseable {
    private byte[] bytes;
    private final AttachmentMetadata metadata; // SAFE: filename, mimeType, size, messageId, sender

    static AttachmentBytesCarrier streamFrom(GmailAttachmentSource source) { ... }

    @AllowedDriveUploadCallSite
    byte[] borrowBytesFor(DriveUploadCall caller) {
        // package-private; only DriveGateway can borrow
        return java.util.Objects.requireNonNull(bytes);
    }

    String extractTextForAnalysis(DocumentTextExtractor extractor) {
        return extractor.extract(bytes, metadata.mimeType()); // returns text only
    }

    @Override public void close() {
        if (bytes != null) {
            java.util.Arrays.fill(bytes, (byte) 0); // zero out
            bytes = null;
        }
    }

    // PRIVATE: never expose bytes outside the package
    // No getter for bytes. No toString that includes bytes. No serializer.
}
```

**`FilingEngine` orchestration:**

```java
public FilingResult fileAttachment(GmailAttachmentSource source, FilingPolicy policy) {
    try (AttachmentBytesCarrier carrier = AttachmentBytesCarrier.streamFrom(source)) {
        String extractedText = carrier.extractTextForAnalysis(documentTextExtractor);
        FolderSuggestion suggestion = analyzeDocumentService.analyze(carrier.metadata(), extractedText, policy);
        return switch (suggestion.decision()) {
            case FILE -> {
                String driveFileId = driveGateway.uploadFile(carrier, suggestion.folderPath());
                yield filingProjection.recordFiled(carrier.metadata(), suggestion, driveFileId);
            }
            case ASK -> filingProjection.recordPendingReview(carrier.metadata(), suggestion);
            case SKIP -> filingProjection.recordSkipped(carrier.metadata(), suggestion.reason());
        };
        // try-with-resources triggers close() → zero bytes
    }
}
```

**4 layers of ArchUnit / build-time enforcement:**

1. **`AttachmentBytesInMemoryOnlyTest` (ArchUnit + Liquibase reader):**
   - No JPA entity in `core.drive.persistence` has a `byte[]` / `@Lob` / `Blob` / `Clob` field.
   - Liquibase changelogs `134-*`, `135-*`, `136-*` parsed at test time: no column of type `BYTEA`, `OID`, `LARGEOBJECT`, `LONGBLOB` in tables `document_filing`, `drive_connection` (except `refresh_token_ciphertext` which is the AES-GCM token, allowlisted), `attachment_source`.
   - No Java record in `core.drive` (anywhere) has a `byte[]` field outside the package `core.drive.application` (where `AttachmentBytesCarrier` lives).
2. **`DriveUploadCallSiteAllowlistTest`** (marker-annotation): only `DriveGateway.uploadFile(...)` is annotated `@AllowedDriveUploadCallSite`; only classes annotated with that marker may call `Drive.Files.create`.
3. **`AttachmentBytesCarrierBoundaryTest`:** the class `AttachmentBytesCarrier` is package-private; no `public` getter for `bytes`; `toString()` overridden to exclude bytes. Reflection unit test asserts these via class scanning.
4. **CI grep gate:** `! grep -rn "bytes()" backend/core/src/main/java/com/zeromail/core/drive | grep -v AttachmentBytesCarrier` — no other class returns raw bytes from a Drive concern.

`document_filing` schema is **metadata only** (rejecting IZ's `attachment_document.content` approach explicitly):

```sql
document_filing (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  mailbox_id uuid NOT NULL,            -- the mailbox the source email arrived at
  message_id text NOT NULL,
  attachment_id text NOT NULL,
  filename text NOT NULL,
  mime_type text NOT NULL,
  size_bytes bigint NOT NULL,
  folder_path text NOT NULL,
  destination_file_id text,            -- Google Drive file id; nullable for ASK/SKIP
  decision text NOT NULL,              -- FILE | ASK | SKIP
  confidence text NOT NULL,            -- LOW | MEDIUM | HIGH
  llm_summary_short text,              -- ≤120 chars assistant-authored summary (not body excerpt)
  created_at, updated_at,
  CONSTRAINT filing_one_per_attachment UNIQUE (mailbox_id, message_id, attachment_id)
);
-- explicit comment in changelog: "No BYTEA / OID / LOB columns by design — see ARCH-02 + ATTACH-MEM-ONLY."
```

### 8. `propose_meeting` rule action — compound, routes stage 2 through existing `OutboundSendGateway`

**Decision:** `propose_meeting` is a **two-stage compound rule action**:

- **Stage 1 (read):** `ProposeMeetingActionAdapter` (in `core.calendar.application`) calls `UnifiedAvailabilityService.suggestSlots(tenantId, durationMinutes, n=5, lookaheadDays=14)`. This routes through `CalendarReadGateway` → Google `FreeBusy.query`. Pure read; no gates beyond OAuth-grant validity check.
- **Stage 2 (write):** the adapter constructs a Gmail draft reply containing the suggested slots in natural language + a booking-link URL (if user has one configured), then submits this draft as a `SendReplyRequest` to the **existing `OutboundSendGateway.sendReply(...)`**. From the gateway's perspective this is indistinguishable from a normal `send_reply` rule action — same Auto-send toggle gate, same safety-net check, same rate cap, same idempotency, same audit row (just with a `triage_audit.reason_code = 'PROPOSE_MEETING'` for analytics).

**Why NOT a separate `CalendarOutboundGateway` for stage 2:** `propose_meeting` does not write a Calendar event. It writes a Gmail message. Putting it through a separate "calendar" gateway would (a) duplicate all the v1.2 outbound gates, (b) break the v1.2 invariant "all Gmail send goes through one gateway", (c) split audit between Gmail-audit and Calendar-audit for an action that is functionally a smart `send_reply`.

**Why two stages (not one):** keeps the read concern (free/busy) cleanly inside `core.calendar` and the send concern (RFC 2822 → Gmail) cleanly inside `core.outbound`. The action adapter is the only orchestrator that knows both — and it lives in `core.calendar.application` because its inputs (slot duration, location preferences) are calendar-domain.

**Gate inheritance (locked):**

- Auto-send rules global toggle: **applies** (the outbound gateway enforces).
- User-managed sender safety-net list: **applies** (gateway enforces).
- Per-tenant outbound rate cap: **applies** (gateway enforces).
- Idempotency on `(tenant, message_id, rule_id, action_index)`: **applies** (gateway enforces).
- Tenant-context correctness: **applies** (gateway enforces).
- **Blocked outbound = failed audit, NO Gmail draft fallback** (per the v1.3 product decision in CLAUDE.md: send/forward rules that cannot send leave no surprise draft). `propose_meeting` inherits this — a blocked propose_meeting is recorded as `triage_audit` failed row with `reason_code='PROPOSE_MEETING_BLOCKED'` + the gate name.

The Calendar module's only contribution to outbound at this stage is the slot-list text body; everything else is the v1.2 outbound machinery.

### 9. Frontend feature placement — 5 feature folders, public route in `(public)` group

**Decomposition:**

- `apps/web/features/calendar/` — connection settings (list, connect, disconnect, calendar-selection toggle, primary calendar choice).
- `apps/web/features/booking-links/` — booking-link CRUD (slug, duration, weekly availability windows, destination calendar, location type).
- `apps/web/features/meeting-briefs/` — brief history view + per-mailbox brief enable toggle + lead-time setting.
- `apps/web/features/drive/` — connection settings + filing review queue + filing rule editor + attachment-source CRUD.
- `apps/web/features/public-booking/` — the public `/book/[slug]` feature: slot picker, guest info form, captcha, confirmation. No TanStack Query keys (single-shot fetch, no cache); plain `fetch` + local `useState`. Lives **inside `features/`** rather than `app/(public)/book/[slug]/` because keeping the feature folder structure consistent makes the OpenAPI codegen + i18n bundles uniform.

**Routes:**

```
app/(public)/book/[slug]/page.tsx           ← public booking (no auth)
app/(public)/book/[slug]/confirmed/page.tsx ← post-booking confirmation page
app/(protected)/(app)/calendar/page.tsx     ← connections + booking-link list (tabs)
app/(protected)/(app)/calendar/links/[id]/page.tsx  ← booking-link editor
app/(protected)/(app)/calendar/briefs/page.tsx
app/(protected)/(app)/drive/page.tsx
```

**Why `(public)` group, not a separate top-level route:** Next.js App Router route groups are exactly the mechanism for "a route that lives outside the protected layout." The v1.3 codebase already uses `(public)`, `(auth)`, `(protected)/(app)` route groups. Public booking joins `(public)` next to the existing landing page. **No middleware change needed** — the existing `middleware.ts` already redirects unauthenticated `(protected)` traffic to login and lets `(public)` through.

**Why `features/public-booking/` is a feature folder (not inline page code):** the public booking flow has three screens (picker, form, confirmation), shared client logic (timezone resolution, slot polling), and i18n strings that need the same bundle treatment as other features. Pages stay thin (`app/(public)/book/[slug]/page.tsx` imports `<PublicBookingApp />` from the feature folder and passes only `slug`).

Per Convention 8: `features/public-booking/` does **not** have a `query-keys.ts` (no TanStack cache concerns); the rest follow the standard `api/`, `components/`, `hooks/`, `query-keys.ts`, `messages.ts` layout.

### 10. Spring Modulith events — which are async events vs direct calls

Per Convention 6: events = in-process after-commit side effects; direct calls = commands needing immediate result or transaction safety.

| New event | Publisher | Subscribers | Why event (vs direct call) |
|-----------|-----------|-------------|------------------------------|
| `CalendarConnected(tenantId, calendarConnectionId, googleAccountEmail)` | `CalendarConnectionService.persist(...)` | `notification` (welcome toast/digest line), `analytics` (connection count), `onboarding` (advance onboarding step) | Multiple decoupled reactions; after-commit safety so a notification doesn't fire on a rolled-back connect. |
| `CalendarDisconnected(tenantId, calendarConnectionId, reason)` | `CalendarConnectionService.revoke(...)` | `calendar.BookingLinkService` (mark links with this destination as DISCONNECTED), `notification` | Same reasoning. |
| `BookingCreated(tenantId, bookingLinkId, bookingId, googleEventId, guestEmail)` | `PublicBookingService.confirm(...)` | `notification` (email the host), `analytics` (booking conversion) | After-commit so a failed Calendar event write does not produce a phantom notification. |
| `MeetingBriefSent(tenantId, mailboxId, meetingBriefId, eventId)` | Worker's `MeetingBriefAgent` after successful Gmail send | `analytics` (brief count + LLM spend), `notification` (digest aggregation) | After-commit; multiple subscribers. |
| `DriveConnected(tenantId, driveConnectionId, googleAccountEmail)` | `DriveConnectionService.persist(...)` | `notification`, `analytics`, `onboarding` | Same reasoning as CalendarConnected. |
| `AttachmentFiled(tenantId, mailboxId, documentFilingId, decision, confidence)` | `FilingEngine` after persist | `notification` (digest "filed N attachments"), `analytics` | After-commit; metadata only in event payload. |
| `AttachmentReviewRequired(tenantId, mailboxId, documentFilingId)` | `FilingEngine` when decision=ASK | `notification` (immediate email + in-app), worker (24h reminder via `processing_job`) | After-commit; latency-tolerant. |

**NOT events — must be direct calls:**

- **Free/busy check during draft generation** — `DraftGenerationService` calls `UnifiedAvailabilityService.suggestSlots(...)` directly because the draft pipeline needs the result inline.
- **Slot reservation during public booking** — `PublicBookingService.confirm(...)` calls `BookingIdempotencyService.consume(...)` and `CalendarOutboundGateway.createEvent(...)` directly because the HTTP response depends on success/failure.
- **Filing decision** — `FilingEngine.fileAttachment(...)` calls `AnalyzeDocumentService` and `DriveGateway.uploadFile(...)` directly inside one virtual-thread invocation because the bytes carrier must be open for both calls.
- **Brief generation tool calls** — the agent's tool dispatch is a synchronous Spring AI tool loop; no event spine inside the loop.
- **Cross-process triggers (API → worker)** — `processing_job` table writes, not Spring Modulith events (Convention 6).

**Process boundary discipline:** all events above stay inside one process (whichever module published them). API-side publishes (`CalendarConnected`, `BookingCreated`) are consumed by API-side modules. Worker-side publishes (`MeetingBriefSent`, `AttachmentFiled`) are consumed by worker-side modules. The shared event records (`CalendarConnectedEvent`, etc.) live in `backend/core` so both processes can deserialize them — but the *spine* doesn't bridge processes (Convention 6 hard rule).

### 11. Integration with v1.3 `MailboxContext` — workspace-shared resources via `WorkspaceResourceLookup`

**The seam:** a `DraftGenerationService` invocation is mailbox-scoped (`MailboxContext.required().mailboxId()` is bound by the v1.3 binding filter). When it asks for free/busy, it wants the **workspace-wide** schedule — Calendar connections are not mailbox-bound.

**Solution — explicit workspace-resource lookup:**

```java
// core.calendar.application.WorkspaceResourceLookup — public API
public interface WorkspaceResourceLookup {
    List<CalendarConnection> forActiveTenant();           // tenant-scoped; ignores mailbox
    List<DriveConnection>    drivesForActiveTenant();
}

// Inside DraftGenerationService (mailbox-scoped caller):
TenantContext.required();          // assert tenant bound (v1.0)
MailboxContext.required();         // assert mailbox bound (v1.3) — for draft destination
List<BusyPeriod> busy = unifiedAvailabilityService
        .forWorkspace()             // explicit: I want workspace-shared, NOT mailbox-scoped
        .between(start, end);
```

**Why this works without bypassing `MailboxContext`:**

- `WorkspaceResourceLookup.forActiveTenant()` is a **deliberate workspace boundary cross**. The repository call inside is `calendarConnectionRepository.findByTenantId(tenantId)` — which is normally banned by the v1.3 ArchUnit `findByTenantId` ban — but `CalendarConnectionRepository` lives in `core.calendar.persistence` and is **explicitly allowlisted** by the v1.3 ban's exception list (the ban targets repositories of *mailbox-isolated* entities like `rule`, `triage_audit`, `gmail_message_observed`; calendar connections are workspace-shared and excluded by table-name allowlist).
- The new ArchUnit test `MailboxScopedReadIntoWorkspaceResourceTest` enforces: any mailbox-scoped service (i.e. any class that calls `MailboxContext.required()` anywhere in its public methods) that injects `CalendarConnectionRepository` or `DriveConnectionRepository` **must** go through `WorkspaceResourceLookup` (not the repository directly). This codifies "workspace-shared resources are read explicitly, never accidentally."
- Tenant isolation is still enforced — `WorkspaceResourceLookup` uses `TenantContext.required()` internally. The seam is *only* about widening from mailbox → workspace, not about leaking across tenants.

**`propose_meeting` mailbox semantics:**

- Stage 1 (free/busy read): unioned across all workspace calendars regardless of active mailbox (`WorkspaceResourceLookup.forActiveTenant()`).
- Stage 2 (Gmail send via `OutboundSendGateway`): mailbox-scoped — the reply goes out **from the active mailbox** (the mailbox the inbound message arrived at, set by the v1.3 binding filter). The reply body says "Here are my available times" not "Here are my available times for inbox X" — to the recipient it's just the user's schedule.

**Booking link → destination calendar:** the booking link stores `destination_calendar_id` AND `destination_calendar_connection_id` (FK to `calendar_connection`). Public booking writes the event to that specific calendar regardless of where in the workspace it came from — there is no active mailbox at the public booking moment (the slug binds the tenant; mailbox is the `MailboxRef.SYSTEM_NONE` sentinel per Q5).

### 12. Audit trail — per-domain tables sharing a unified `AuditEntry` projection

**Recommendation:** Three separate audit tables (`triage_audit` already exists; add `calendar_audit`, `drive_audit`) **sharing one read-side `AuditEntry` projection** in the support module.

**Why per-domain tables (not one unified table):**

- **Schema fit.** A Gmail send audit row has `from_address`, `to_addresses`, `subject_hash`, `gmail_message_id`, `gmail_thread_id`. A Calendar event audit row has `google_event_id`, `attendees_count`, `starts_at`. A Drive filing audit row has `google_file_id`, `folder_path`, `confidence`. Forcing these into one table with a `payload_jsonb` column makes every query path do JSONB extraction; per-domain typed columns get proper indexes and EXPLAIN plans.
- **Write contention.** Each domain has its own write pattern (rule triage runs ~10–100/sec; bookings run ~10/day; filings run ~1–10/min). One unified table puts unrelated write paths on the same partition / WAL contention.
- **Privacy classification.** Different audit tables can have different retention policies (e.g. `triage_audit` keeps 90 days for undo; `drive_audit` keeps indefinitely as a metadata-only filing history; `calendar_audit` keeps 1 year per CASA expectation).
- **v1.3 precedent.** v1.3 already separates `triage_audit` from `llm_call_audit` and from the `outbound_audit` (the outbound gateway writes its own row). Adding two more typed tables is consistent.

**Why one shared read-side projection:**

- The admin console (`apps/admin`) and the user "Activity" view want a unified timeline across triage + outbound + calendar + drive. Building one read-only DB view `audit_unified_v` that UNIONs the four tables into a common shape `(surface, surface_id, tenant_id, mailbox_id, occurred_at, actor, outcome, reason_code, metadata_short_text)` gives the admin a single query target without conflating write paths.
- `support` module's `AuditTimelineQueryService` reads from `audit_unified_v` via Spring Data JDBC (read-side, hot-path pattern from Convention "Spring Data JDBC for read-side").
- Discriminator: `surface IN ('TRIAGE','OUTBOUND','CALENDAR','DRIVE','LLM')`. The Liquibase changelog `140-audit-unified-view.yaml` creates the view; no table changes to the four underlying audit tables.

**v1.4 audit additions:**

`calendar_audit` (workspace-scoped — no `mailbox_id` for booking writes; nullable `mailbox_id` for `propose_meeting`):

```sql
calendar_audit (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  mailbox_id uuid,              -- NULL for booking writes (workspace event); set for propose_meeting
  calendar_connection_id uuid,  -- which connection performed the write
  surface text NOT NULL,        -- BOOKING_WRITE | PROPOSE_MEETING | BRIEF_SEND | EVENT_EDIT
  outcome text NOT NULL,        -- OK | BLOCKED | FAILED
  reason_code text,             -- e.g. AUTO_SEND_OFF, SLOT_CONFLICT, SCOPE_REVOKED
  google_event_id text,         -- nullable
  occurred_at timestamptz NOT NULL,
  metadata_short_text text      -- ≤200 chars; never body content
);
```

`drive_audit`:

```sql
drive_audit (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  mailbox_id uuid NOT NULL,
  drive_connection_id uuid,
  surface text NOT NULL,        -- FILING | ATTACHMENT_SEND | FOLDER_CREATE
  outcome text NOT NULL,
  reason_code text,
  google_file_id text,
  occurred_at timestamptz NOT NULL,
  metadata_short_text text      -- ≤200 chars; filename + folder path only, no body excerpt
);
```

Both inherit the existing `AuditWritePort` SPI from the support module (already used by `triage_audit` and `outbound_audit`), so the privacy-log convention (`event=<name> tenantId={}`) and the AUD-07-clean logging contract apply transparently.

---

## Component Responsibilities (v1.4 only)

| Component | Responsibility | Where it lives |
|-----------|----------------|----------------|
| `CalendarConnectionService` | OAuth callback persistence, refresh-token rotation, list/revoke. | `core.calendar.application` |
| `CalendarSelectionService` | Per-calendar enable/disable + timezone resolution for a connection. | `core.calendar.application` |
| `CalendarReadGateway` | Sole caller of `Calendar.FreeBusy.query`, `CalendarList.list`, `Calendars.get`. 60s Redis cache. | `core.calendar.gateway.google` |
| `CalendarOutboundGateway` | Sole caller of `Calendar.Events.insert/patch/delete`. `@AllowedCalendarWriteCallSite`. | `core.calendar.gateway.google` |
| `UnifiedAvailabilityService` | Unions busy periods across all enabled calendars in the workspace; returns `List<BusyPeriod>`. | `core.calendar.application` |
| `SuggestMeetingSlotsService` | Computes N candidate slots given duration + lookahead + working hours. Backs the `suggestMeetingSlots` LLM tool. | `core.calendar.application` |
| `BookingLinkService` | CRUD for `booking_link` + `booking_link_slot`; slug allocation; destination-calendar enforcement. | `core.calendar.application` |
| `PublicBookingService` | Slot listing for `/api/public/book/{slug}/availability`; booking confirmation handler. | `core.calendar.application` |
| `BookingIdempotencyService` | Redis `SET NX EX 86400` for booking idempotency tokens; consume-on-POST. | `core.calendar.application` |
| `MeetingBriefScheduler` | Worker `@Scheduled` cron job — enqueues `processing_job` per upcoming meeting. | `backend/worker` |
| `MeetingBriefAgent` | Spring AI tool-call loop with `searchGuest` + `checkPastEmails` + `checkPastMeetings` tools. | `core.calendar.application` |
| `CalendarInviteClassifier` | Inspects ingested Gmail messages for invite/cancellation/reschedule headers; tags + protects from auto-archive. | `core.calendar.application` (called from `core.triage` pipeline) |
| `ProposeMeetingActionAdapter` | Stage 1 free/busy + stage 2 outbound; routes through `OutboundSendGateway`. | `core.calendar.application` |
| `DriveConnectionService` | Drive OAuth callback persistence, list/revoke. | `core.drive.application` |
| `FolderListingService` | Lists workspace Drive folders, caches per tenant for filing dropdowns. | `core.drive.application` |
| `FilingEngine` | The in-memory orchestrator: download → extract → analyze → upload → record metadata → zero bytes. | `core.drive.application` |
| `AttachmentBytesCarrier` | `AutoCloseable` package-private bytes holder with zero-on-close. | `core.drive.application` |
| `AnalyzeDocumentService` | LLM call (via `LlmGateway.chat(...)`): text + metadata → `FolderSuggestion`. | `core.drive.application` |
| `DriveGateway` | Sole caller of `Drive.Files.create` / `Files.list`. `@AllowedDriveUploadCallSite`. | `core.drive.gateway.google` |
| `AttachmentSourceService` | CRUD for `attachment_source`; per-rule attachment bundle resolver for outbound replies. | `core.drive.application` |
| `WorkspaceResourceLookup` | The single seam between mailbox-scoped callers and workspace-shared connections. | `core.calendar.application` (with mirror in `core.drive.application` if a shared interface is not yet warranted) |

---

## Anti-Patterns

### Anti-Pattern 1: Adding Calendar scopes to the login bundle

Tempting because "we already have one OAuth flow." Wrong because (a) it forces every signup to consent to Calendar — many users don't want that, (b) it widens the v1.3 CASA-verified scope set, (c) it makes selective revoke impossible (revoking Calendar would revoke login). **Correct:** additive `ClientRegistration` per Q3.

### Anti-Pattern 2: One unified `OutboundSendGateway` for Gmail send + Calendar event writes

Tempting because "they're both writes." Wrong because the gate set differs entirely (no sender safety net for calendar events, different idempotency surface). Forcing parameterization breaks the v1.2 invariant that exactly one Gmail send call site exists. **Correct:** separate `CalendarOutboundGateway` with its own marker annotation per Q4.

### Anti-Pattern 3: Persisting attachment bytes in any v1.4 table

Including "temporarily until the user reviews the filing decision." This rebuilds IZ's `attachment_document` table that we explicitly rejected in `PROJECT.md`. **Correct:** in-memory carrier with zero-on-close per Q7; ASK decisions get a re-fetch path on review (user re-clicks → backend re-pulls from Gmail).

### Anti-Pattern 4: A mailbox-scoped service injecting `CalendarConnectionRepository` directly

Tempting because "I just need the connection list." Wrong because it bypasses the `WorkspaceResourceLookup` seam, and the next refactor will mistakenly add a `mailboxId` filter to the query (because the rest of the service is mailbox-scoped) — silently breaking workspace-shared semantics. **Correct:** inject `WorkspaceResourceLookup` per Q11.

### Anti-Pattern 5: Public `/book/{slug}` endpoint binding `TenantContext` from a request header

Tempting because "we'd reuse the existing tenant context plumbing." Wrong because the public booking page has no authenticated principal — the tenant is bound from the **slug lookup**, not from a client-supplied value. Any client-supplied tenant header on a public endpoint is a tenancy spoof vector. **Correct:** `PublicBookingFilter` resolves slug → `booking_link.tenant_id` server-side per Q5.

### Anti-Pattern 6: Storing meeting brief bodies in `meeting_brief.summary_text` AND copying Gmail body text into the prompt logs

The brief output is OK (assistant-authored, persistable per ARCH-02 carve-out). But streaming the prompt+completion (which contains Gmail body excerpts pulled by `checkPastEmails`) into `llm_call_audit.prompt_text` rebuilds the body-storage ban violation. **Correct:** `meeting_brief_audit` and `llm_call_audit` both store sha256 **fingerprints** + token counts + model + cost, never prompt/completion text (per v1.0 LLM-09 + ARCH-02).

### Anti-Pattern 7: Calling `findByTenantId` on `rule` / `triage_audit` from a Calendar-aware service

The v1.3 `findByTenantId` ArchUnit ban exists for mailbox-isolated entities. Calendar/Drive code that needs to read user rules must respect the existing `MailboxContext.required()` discipline for those tables — the workspace allowlist is for **calendar/drive own tables only**. **Correct:** if a Calendar service needs rule data, it goes through `RuleManagementService` (which enforces mailbox scope internally) like every other consumer.

---

## Integration Points

### New cross-module dependency edges (calendar → existing modules)

| Caller (in `core.calendar.application`) | Callee | Purpose |
|-----------------------------------------|--------|---------|
| `MeetingBriefAgent` | `LlmGateway.chat(...)` | Tool-call loop |
| `MeetingBriefAgent` | `GmailApiClientFactory.client(tenantId, mailboxId)` | `checkPastEmails` tool |
| `MeetingBriefAgent` | `OutboundSendGateway.sendEmail(SystemSendRequest)` | Deliver brief via Gmail |
| `ProposeMeetingActionAdapter` | `OutboundSendGateway.sendReply(...)` | Stage 2 reply |
| `CalendarInviteClassifier` | `core.triage.TriageLabelService` | Apply protected label |
| `PublicBookingService` | `CalendarOutboundGateway.createEvent(...)` | Booking event write |
| `BookingLinkService` | `CalendarReadGateway.listCalendars(...)` | Destination calendar picker |
| `UnifiedAvailabilityService` | `CalendarReadGateway.queryFreeBusy(...)` | Free/busy union |
| `*Service` (CalendarConnected event) | `core.notification.NotificationDispatcher` | Welcome notification |

### New cross-module dependency edges (drive → existing modules)

| Caller (in `core.drive.application`) | Callee | Purpose |
|--------------------------------------|--------|---------|
| `FilingEngine` (Gmail attachment ingestion) | `GmailApiClientFactory.client(tenantId, mailboxId)` | Pull attachment bytes |
| `AnalyzeDocumentService` | `LlmGateway.chat(...)` | Folder suggestion call |
| `FilingEngine` | `DriveGateway.uploadFile(...)` | Upload to Drive |
| `AttachmentSourceService` | `DriveGateway.downloadFile(...)` | Pull bytes for outbound reply attachment |
| `AttachmentSourceService` | `OutboundSendGateway.sendReplyWithAttachments(...)` | Attach + send (new gateway overload Phase I) |

### Worker triggers (cross-process via `processing_job`)

| `processing_job.kind` | Producer | Consumer |
|----------------------|----------|----------|
| `CALENDAR_MEETING_BRIEF` | `MeetingBriefScheduler` (worker cron) | Worker `MeetingBriefJobConsumer` |
| `DRIVE_ATTACHMENT_FILING` | API-side Gmail ingestion when new attachment arrives + filing rule matches | Worker `FilingJobConsumer` |
| `DRIVE_FILING_REVIEW_REMINDER` | `FilingEngine` when decision=ASK | Worker after 24h |

### Spring Modulith events (in-process, after-commit)

Defined in Q10 above. All event types live in `backend/core` (each module's own `domain/events/` sub-package, exported in `package-info.java` `@NamedInterface` only if they need to be re-published from another module — none do in v1.4).

---

## Scaling Considerations

| Scale | Notes |
|-------|-------|
| 0–50 tenants | No changes. Free/busy queries are cached 60s; bookings are ~10/day per tenant; filings are ~1–10/min per tenant. Single Postgres handles. |
| 50–500 tenants | Add index `(tenant_id, occurred_at DESC)` on `calendar_audit` and `drive_audit`. Partial index `WHERE decision = 'ASK'` on `document_filing` for the review queue. `booking_link.slug` UNIQUE index already required. |
| 500–5000 tenants | Move `meeting_brief.summary_text` (the biggest column) to a separate compressed table if it exceeds ~50% of `meeting_brief` row width. Consider connection pooling cap per-tenant for Drive uploads — Google Drive API per-user quota caps at ~1000 uploads/100s, well above v1.4 expected load. |
| 5000+ tenants | Partition `calendar_audit` by month if > 10M rows. Move meeting-brief generation off ShedLock to a leader-elected `processing_job` consumer (worker can scale to N replicas). |

**First bottleneck (expected):** Google Calendar `freebusy.query` quota (default ~500 queries/100s/user). The 60s Redis cache mitigates; if a single tenant has very active draft-reply patterns, raise quota.

**Second bottleneck:** Drive API uploads are ~10MB/s per connection. Filing pipeline is in-memory so it's bounded by JVM heap — set `-Xmx` headroom and reject attachments > 25MB at the Gmail ingestion boundary.

**Third bottleneck:** LLM cost for meeting briefs (~3 tool calls × ~2K tokens). At 5K tenants × 5 briefs/day × 4 LLM calls × $0.001/call ~ $100/day. Already accounted for in the per-tenant credit ledger.

---

## ArchUnit Tests Added in v1.4 (Summary)

1. `CalendarModuleBoundaryTest` — `package-info.java` allowed-deps list matches actual edges.
2. `DriveModuleBoundaryTest` — same.
3. `CalendarOutboundGatewayCallSiteAllowlistTest` — only `CalendarOutboundGateway` (`@AllowedCalendarWriteCallSite`) calls `Calendar.Events.insert/patch/delete`. Repo-wide grep gate in CI.
4. `DriveUploadCallSiteAllowlistTest` — only `DriveGateway` (`@AllowedDriveUploadCallSite`) calls `Drive.Files.create`. Repo-wide grep gate.
5. `AttachmentBytesInMemoryOnlyTest` — composite test: no BYTEA/OID/LOB in `drive_*` / `document_filing` / `attachment_source` tables (except allowlisted AES-GCM token columns); no `byte[]` field outside `core.drive.application.AttachmentBytesCarrier`; no `@Lob` in `core.drive.persistence`.
6. `MailboxScopedReadIntoWorkspaceResourceTest` — any class calling `MailboxContext.required()` that also injects `CalendarConnectionRepository` / `DriveConnectionRepository` fails. Must inject `WorkspaceResourceLookup` instead.
7. `PublicBookingChainIsolationTest` — `PublicBookingController` and `PublicBookingFilter` are reachable only from `SecurityFilterChain` at `@Order(40)`; no authenticated user filter chain matches `/api/public/**`.
8. `MeetingBriefAuditNoPromptTextTest` — `meeting_brief_audit` has no `prompt_text` / `completion_text` columns; only fingerprints + counts.
9. `CalendarConnectionRefreshTokenAesGcmTest` — `calendar_connection.refresh_token_ciphertext` is `bytea` and the changelog never references `pgp_sym_encrypt`.

---

## Sources

- v1.3 architecture baseline: this file's git history prior to 2026-06-17 (v1.0 + v1.1 + v1.2 + v1.3 deltas).
- v1.0–v1.3 module list: direct read of `backend/core/src/main/java/com/zeromail/core/` (24 modules confirmed including `outbound`, `mailbox`, `inbox`, `messaging`, `composer`, `chat`, `triage`, `gmail`, `admin`).
- v1.3 Liquibase changeset count: direct read of `backend/core/src/main/resources/db/changelog/changes/` (last applied: `128-processing-job-mailbox-nullable.yaml`).
- v1.3 `MailboxContext` ScopedValue pattern: `core.mailbox.MailboxContext.java`.
- Spring Security 6.5+ OAuth2 client multi-registration: official Spring docs (verified via Context7 patterns used in v1.0/v1.2 admin chain `@Order(1)` + user chain `@Order(50)`).
- IZ unified-availability reference (UX shape only; rewritten for workspace-shared model): `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/calendar/unified-availability.ts`.
- IZ filing-engine reference (in-memory flow shape only; persistence rejected per ARCH-02): `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/drive/filing-engine.ts`.
- Project privacy + outbound gate set: `CLAUDE.md` (ARCH-02 carve-out, v1.2 Phase 08.1 outbound gates, v1.3 product decision on no-draft-fallback).
- Spring AI 2.0.0 GA streaming + tool-call loop (`internalToolExecutionEnabled(false)`): verified pattern from v1.0 `SpringAiLlmModelClient` (cited in v1.1 ARCHITECTURE history).

---

*Architecture research for: Zero Mail v1.4 — Calendar Co-Pilot + Drive Filing integration into existing Spring Boot 4 + Spring Modulith + Next.js 16 monorepo*
*Researched: 2026-06-17 by gsd-researcher (direct codebase read + v1.3 multi-mailbox seam analysis + IZ reference structural cues + verified ArchUnit/Modulith patterns)*
