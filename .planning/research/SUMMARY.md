# Research Synthesis — Zero Mail v1.4 (Calendar Co-Pilot + Drive Filing)

**Synthesized:** 2026-06-17
**Sources:** STACK.md, FEATURES.md, ARCHITECTURE.md, PITFALLS.md, PROJECT.md
**Overall confidence:** HIGH on backend stack additions, Modulith boundaries, ArchUnit pattern reuse, IZ schema parity, and v1.3 multi-mailbox integration seams. MEDIUM-HIGH on CASA/scope-tier mechanics, Spring AI 2.0.0 M6→GA migration churn, and `drive.file` UX trade-off. MEDIUM on booking-page abuse modeling at a new SaaS scale.

## 1. Milestone TL;DR

v1.4 adds **Google Calendar Co-Pilot** (multi-Google-Calendar incremental OAuth, unified free/busy in draft replies + chat tool, public Calendly-style booking links, calendar-aware triage that protects invites from aggressive rules, a `propose_meeting` rule action, and a cron-driven AI meeting brief agent) and **Google Drive Filing** (one workspace-shared Drive connection on the minimal `drive.file` scope, in-memory-only attachment auto-filing with confidence + review queue, and an `attach_from_source` rule action for curated outbound attachments) on top of the v1.3 multi-Gmail workspace baseline.

The whole milestone reuses v1.3's primitives: `MailboxContext` ScopedValue, `OAuthTokenStore` + AES-GCM, single `LlmGateway`, `OutboundSendGateway` boundary, ARCH-02 privacy invariants, Postgres `SKIP LOCKED` queue, Liquibase YAML. Net-new runtime dependencies are two Google API Java client artifacts (Calendar + Drive); the frontend ships **zero new npm deps**. The hardest invariants to preserve: (a) the v1.3 single-bundled-OAuth-scope login experience — Calendar/Drive consents fire only on explicit clicks in settings, never as a surprise during signup — and (b) ARCH-02 — attachment bytes never persisted; meeting-brief output is the same carve-out shape as v1.1 chat `draft_body` (assistant-authored summary persistable, raw email body NOT).

## 2. Stack Additions (net-new, ArchUnit-confined)

| Net-new dep | Version pin | Module | ArchUnit boundary |
|---|---|---|---|
| `com.google.apis:google-api-services-calendar` | `v3-rev20260517-2.0.0` | `backend/core` (`api` config) | `CalendarBuilderConfinedToGateway`; `CalendarOutboundGatewayCallSiteAllowlistTest` |
| `com.google.apis:google-api-services-drive` | `v3-rev20260428-2.0.0` | `backend/core` (`api` config) | `DriveBuilderConfinedToGateway`; `DriveUploadCallSiteAllowlistTest` (`@AllowedDriveUploadCallSite`) |

**Reused (no version bump):** `google-auth-library-oauth2-http 1.48.0`; Spring AI 2.0.0 GA `ToolCallingManager`/`ChatClient.tools()`; Spring Security OAuth2 Client multi-`ClientRegistration` with shared client-id (v1.3 GMA-07 pattern).

**Frontend:** zero new npm deps. `calendar.tsx` (shadcn over `react-day-picker@10`), `command.tsx`, `popover.tsx`, `radio-group.tsx`, `card.tsx`, `date-fns@4` already installed. `Intl.supportedValuesOf("timeZone")` is the IANA source.

**ArchUnit rules added (9):** 3 gateway call-site allowlists (Calendar read, Calendar write, Drive write), `AttachmentBytesInMemoryOnlyTest` (composite: ArchUnit + Liquibase reader + CI grep), `MailboxScopedReadIntoWorkspaceResourceTest` (workspace-shared × mailbox-context seam), `PublicBookingChainIsolationTest`, `MeetingBriefAuditNoPromptTextTest`, `CalendarConnectionRefreshTokenAesGcmTest`, two `ModuleBoundaryTest`s, plus a new `OAuthScopeAllowListTest` that fails CI on any scope string not in the approved set (catches a typo'd `drive` or `calendar` full scope before CASA re-clock).

**`spring-cloud-gcp` stays banned.** Hand-wired `Calendar.Builder` / `Drive.Builder` per request, identical shape to v1.0 `Gmail.Builder`.

## 3. Feature Table Stakes

### Must-ship to be credible

1. Calendar connection management (multi-Google-Calendar incremental OAuth, enable/disable, primary marker, timezone, AES-GCM token, `DISCONNECTED` + reconnect, cascade delete).
2. AI calendar availability in draft replies + chat tool (free/busy fan-out, LLM-inferred duration, business-hours-aware, free/busy never persisted).
3. Calendar-aware triage (parse `text/calendar` invite/cancel/reschedule, guard against `archive`/`mark_read`/`mark_spam`/auto-send, top-of-inbox surface). Cheapest credibility win — ships without OAuth.
4. Booking links — single-link, Google Meet + phone + in-person, weekly windows, DB-locked slot uniqueness, idempotency token, public `/book/{slug}` with hCaptcha.
5. `propose_meeting` rule action — two-stage compound: `CalendarReadGateway` → existing `OutboundSendGateway` (inherits all v1.2 gates unchanged).
6. Drive connection management (workspace-scoped, `drive.file` ONLY, Picker-driven onboarding).
7. AI document auto-filing (in-memory `Gmail.getInputStream` → `InputStreamContent` → Drive upload; metadata-only suggestion; confidence + ASK-USER queue).
8. Attachment-source rules — Mode A only (static file pin via Picker multi-select; raw RFC2822 through `OutboundSendGateway`).

### Differentiator (ship if cheap)

- "Same-week priority" slot bias (cheap prompt tweak — ship).
- Booking-link fallback inline in `propose_meeting` reply (cheap — ship).
- **AI meeting briefs** — headline feature but most expensive; premium-gate per-tenant per-day brief credit cap separate from LLM cap.
- Filing correction learning (metadata-only, ARCH-02-safe).
- Recipient-TZ inference, one-click invite accept, Mode B-safe attachment picker, multi-Drive — defer to v1.5.

### Anti-features (deliberately omit)

- Outlook / Office 365 / OneDrive / iCloud / CalDAV / Dropbox (Gmail/Google-only locked).
- Full `drive` or `drive.readonly` scope (CASA Tier 2 re-clock + ARCH-02).
- `AttachmentDocument.content`/`.summary` persistence (PROJECT.md L66 explicit rejection of IZ pattern).
- Embeddings of user mail or Drive files; RAG; vector DB.
- Persisted brief body column (generated at delivery time, in-memory only).
- Slack / Teams / Telegram brief delivery (no messaging-channel infra).
- Auto-accept invite via rule / auto-confirm-on-recipient-reply (auto-send-class trust risk).
- Multi-attendee availability / round-robin / paid bookings / iframe widget.
- OCR / Tika / PDFBox / docx4j for attachment text extraction (filing is metadata-only in v1.4).
- Second Google OAuth Cloud Console client (reuse same client-id; split `ClientRegistration` only).
- Incremental authorization auto-prompt during signup.
- `@tanstack/react-table` for new lists (raw shadcn first).
- Booking-page localization beyond VI/EN.

## 4. Architectural Seams (top 5)

1. **Multi-mailbox × workspace-shared Calendar/Drive.** Connections are workspace-shared; rules/triage are mailbox-isolated. New `WorkspaceResourceLookup` is the only legal path for a mailbox-scoped caller to read a workspace-shared connection. `MailboxScopedReadIntoWorkspaceResourceTest` ArchUnit rule fails any service that injects both `MailboxContext.required()` and a `CalendarConnectionRepository` directly. New `mailbox_calendar_preference (mailbox_id, calendar_connection_id, role ∈ {freebusy, event_write, brief_source})` disambiguates which calendar a mailbox uses — without it v1.4 leaks personal availability into work drafts.

2. **OAuth bundling × incremental grants.** v1.3 `google` registration stays byte-identical. Two new `ClientRegistration`s `google-calendar` and `google-drive` share the same client-id/secret but expose dedicated `/oauth2/authorization/*` routes triggered ONLY by explicit settings clicks. `include_granted_scopes=true` + `access_type=offline` + `prompt=consent` merges grants without re-prompting Gmail. Custom `OAuth2AuthorizedClientService` routes the new registrations into `calendar_connection`/`drive_connection` tables.

3. **`OutboundSendGateway` × `propose_meeting` × `CalendarOutboundGateway`.** `propose_meeting` is **two-stage compound**: stage 1 free/busy → `CalendarReadGateway`; stage 2 Gmail reply → existing `OutboundSendGateway` (inherits Auto-send, safety-net, rate cap, idempotency, audit, "blocked = failed audit, NO draft fallback"). Booking event writes + all other `events.insert` go through a **separate** `CalendarOutboundGateway` (different gates, independent quota). One-Gmail-send-call-site invariant preserved.

4. **ARCH-02 × meeting briefs + Drive filing.** Two new privacy carve-outs:
   - **Meeting brief:** assistant-authored summary persists in `meeting_brief.summary_text` (same shape as v1.1 `draft_body` carve-out). Source bodies live only in request-scoped buffer zeroed on close. `meeting_brief_audit` stores fingerprints + tokens + cost, never prompt/completion text.
   - **Drive filing:** bytes flow `Gmail.attachments.get().executeMediaAsInputStream()` → `InputStreamContent` → `Drive.files.create` in one try-with-resources block; never hit disk, `byte[]`, or `INSERT`. `document_filing` stores filename/MIME/size/folder/confidence/sha256 — no body, no extracted text excerpt.

5. **`MailboxContext` × public booking chain.** `/api/public/**` has no session. New `SecurityFilterChain` `@Order(40)` matches it (above v1.3 `@Order(50)` user chain, below v1.2 `@Order(1)` admin chain). `PublicBookingFilter` resolves slug → `tenant_id`, binds `TenantContext`, binds `MailboxContext` to `MailboxRef.SYSTEM_NONE`. Slot uniqueness enforced by Postgres `UNIQUE (destination_calendar_id, starts_at)` — Redis `booking:slot-lock` is 30s UX cushion only.

## 5. Watch Out For (top 7 + fixes)

| # | Pitfall | Fix |
|---|---|---|
| 1 | **CASA re-clock from new scopes** — `drive`/`drive.readonly` re-anchors the whole verification timeline; team thinks CASA was done but assessment is per-scope-set | `docs/oauth-scopes.md` ledger + `OAuthScopeAllowListTest` ArchUnit rule fails CI on any scope string not approved. Phase 1 demo-video checklist locks UX before submission. PROJECT.md: v1.4 does NOT unblock GA. |
| 2 | **Spring AI M6 → GA silently breaks tool execution** — RC1 dropped the in-`ChatModel` tool loop; v1.3 `chatModel.call()` with tools stops looping mid-conversation; brief never iterates | Phase 0 before any feature work: bump to GA, migrate to `ChatClient.builder().defaultAdvisors(toolCallingAdvisor)` or explicit `DefaultToolCallingManager`, per-provider no-arg-tool smoke test, grep `PromptChatMemoryAdvisor`. |
| 3 | **`drive.file` UX reality** — `files.list` returns empty under `drive.file`; engineers expect "browse my Drive" but the scope only sees app-created or Picker-opened files | UX is Picker-only, never Browser. ADR `docs/adr/drive-file-only.md`. CI integration test asserts empty `files.list` on a fresh connection. AI suggests folder NAMES; user Picker-creates. Attachment-source = pick files once at rule create. |
| 4 | **Public booking abuse** — slug enumeration, bot calendar-DOS, invite-spam relay; Calendly's "CAPTCHA after 2-3/h" insufficient for a new app | hCaptcha on every submit. Slugs ≥12 chars + random suffix. `robots.txt` + `X-Robots-Tag: noindex`. DB `UNIQUE (destination_calendar_id, starts_at)` is source of truth. Per-IP (3/h), per-slug (10/h), per-attendee-email (5/day), platform-wide (1000/h) Redis token-buckets. `Idempotency-Key` required. All booking writes through `CalendarOutboundGateway` with `extendedProperties.private.zeromail_booking_id`. |
| 5 | **Brief = extracted email content** — `meeting_brief.body_text` or pre-generated-and-stored brief silently re-opens ARCH-02; `draft_body` carve-out is USER-authored, NOT LLM-extracted-from-email | Brief generated at DELIVERY time, not schedule time. `meeting_brief.summary_text` persistable (assistant-authored narrative, parallel to v1.1 `draft_body`). Source bodies in request-scoped buffer zeroed on close. Audit = fingerprints + tokens + cost. `ToolOutputSanitizer` extended to `BriefOrchestrator`. PROJECT.md explicit third carve-out enumeration. |
| 6 | **Attachment streaming OOMs worker JVM** — 25MB cap × N concurrent filings × shared worker pool × 4GB VPS heap | Streaming pipe: `MediaHttpDownloader` → temp file (chunked 256KB) → text-only summary → `Drive.Files.create` with `uploadType=resumable`. Dedicated bounded `FilingExecutor` (`maxConcurrent=4`). Heap-budget startup assertion. >10MB skips AI summarization. Janitor on `/tmp/zeromail-filing/`. try-with-resources mandatory. |
| 7 | **Free/busy quota exhaustion in draft hot path** — `freebusy.query` ~600/min/user; one draft = N calendars × M LLM retries × P windows; LLM tool retries amplify silently | Two-tier cache: per-request `ConcurrentHashMap` + Redis L2 `TTL=60s` singleflighted via `SETNX`. `quotaUser=sha256(tenantId+":"+calendarId)` always set. Hard cap `calendarExpansionMax=50`. All `Freebusy.query` confined to `CalendarReadGateway` wrapper. On 429 return `{free_busy_unavailable: true}` to LLM (told to propose ranges); never auto-retry inside the tool. Per-tenant 60/min outbound free/busy rate cap. |

**Honorable mentions:** brief budget runaway caps (`MAX_ITERATIONS=8`, token + wall-clock + per-day-credit caps, BYOK cost preview); multi-Gmail × workspace-Calendar cross-account leak (`mailbox_calendar_preference` + `CalendarContext` ScopedValue + `findAllCalendarsForTenant` ArchUnit ban); calendar mid-flight disable (3-state machine, Modulith event evicts cache, per-iteration status check); Liquibase changeset stacking (small append-only single-purpose changesets per Convention 10).

## 6. Suggested Phase Order

Reconciled ARCHITECTURE.md (A-I) and FEATURES.md MVP. Resolution: keep ARCHITECTURE's order, but lift calendar-aware triage iCal-parsing into Phase 1 (additive ingestion work, no OAuth dependency). Add explicit Phase 0 for Spring AI GA bump per Pitfall 8.

| # | Phase | Goal |
|---|---|---|
| 0 | **Spring AI 2.0.0 M6 → GA migration** | Bump pin, migrate every embedded-tool `chatModel.call()` to `ChatClient` advisors or explicit `ToolCallingManager`, smoke-test streaming on real OpenRouter route before any feature touches LLM gateway. |
| 1 | **Calendar OAuth + connection foundation + calendar-aware triage** | Two new `ClientRegistration`s, `calendar_connections` + `calendar_selection` + `mailbox_calendar_preference` schema, AES-GCM token reuse, scope ledger + ArchUnit allow-list, 3-state state machine, plus `text/calendar` parsing in Gmail ingestion guarded against `archive`/`mark_read`/`mark_spam`. |
| 2 | **Calendar read gateway + AI availability in drafts + chat tool** | `CalendarReadGateway` with Redis 60s cache + singleflight + quota cap, `UnifiedAvailabilityService` workspace-unioned, draft pipeline `suggestMeetingSlots`, chat tool `getCalendarAvailability`. |
| 3 | **Booking links + public booking page** | `booking_links` + `booking_windows` + `bookings` schema, DB-UNIQUE slot constraint, `@Order(40)` sessionless filter chain, hCaptcha, idempotency, `CalendarOutboundGateway` `events.insert` with `conferenceDataVersion=1`. |
| 4 | **`propose_meeting` rule action** | New action in When/Then schema + compiler (structured output, no regex), `ProposeMeetingActionAdapter` orchestrates `CalendarReadGateway` → `OutboundSendGateway.sendReply`, inherits all v1.2 gates. |
| 5 | **Drive OAuth + connection foundation + Picker onboarding** | `drive_connections` (workspace-shared, one per tenant), `drive.file`-only OAuth, Google Picker UX for `filing_folders`, ADR, semantic CI test. |
| 6 | **AI document auto-filing engine** | `AttachmentBytesCarrier` + try-with-resources streaming, `FilingEngine` with metadata-only `AnalyzeDocumentService`, `document_filing` metadata-only schema, confidence + ASK-USER queue, `AttachmentBytesInMemoryOnlyTest`, bounded `FilingExecutor`, load test. |
| 7 | **Attachment-source rules (Mode A static pin)** | `attachment_sources` schema, `attach_from_source` rule action, Picker multi-select editor, in-memory `Drive.files.get().executeMediaAsInputStream()` → `OutboundSendGateway` raw RFC2822, audit. |
| 8 | **AI meeting briefs (cron + agentic loop)** | `meeting_brief` + `meeting_brief_audit` schema (NO body column), `MeetingBriefScheduler` ShedLock cron, `MeetingBriefAgent` Spring AI loop with `maxIterations=8` + token + wall-clock caps, `BriefOrchestrator` reuses `ToolOutputSanitizer`, Resend at fire time, premium credit gate. |

**Dependencies:** 0 → 1 → 2 → (3, 4 parallel). 5 → 6 → 7. 8 depends on 2 + 4.

## 7. Open Questions for Requirement Definition

1. **Connection ownership boundary** — lock workspace-shared for both Calendar and Drive in REQUIREMENTS.md so Phase 1 doesn't relitigate.
2. **Booking link multiplicity** — recommend ONE per user in v1.4 (defer multi-link to v1.5); confirm.
3. **Booking link custom branding / buffer-between-meetings / question fields** — recommend defer to v1.5.
4. **Brief default lead-time window** — recommend 24h default, tenant-configurable, no per-event override.
5. **Premium-gating for briefs** — recommend separate per-tenant per-day brief credit cap (default 50 credits/day) on top of LLM cap.
6. **BYOK brief cost preview** — recommend yes on first brief + tenant setting `byok_brief_daily_usd_cap` (default $5).
7. **Calendar push notifications (`events.watch`)** — recommend OUT of v1.4 (cron polls; brief and drafts are pull-only).
8. **Web-search MCP tool in brief loop** — recommend IN as tenant feature flag default OFF.
9. **Attachment-source Mode B (smart pick by filename + metadata)** — recommend v1.5.
10. **Calendar invite one-click accept/decline from triage UI** — recommend v1.5 (chat tool already covers it).
11. **Drive multi-connection** — recommend v1.5.
12. **Brief delivery channel default** — recommend email ON + in-app digest ON by default, each independently disable-able.
13. **CASA timeline expectation in PROJECT.md** — write explicitly that v1.4 does NOT unblock GA; CASA refresh window opens only once Gmail + Calendar + Drive scopes are stable across one consecutive milestone.
14. **Privacy carve-out documentation** — PROJECT.md privacy section gains explicit third carve-out enumeration (chat draft / brief summary / triage in-memory), in the SAME PR as first brief code.

---

### Confidence

Overall: **HIGH** (stack + architecture + IZ feature parity + ARCH-02 enforcement). MEDIUM-HIGH on Spring AI GA migration (Phase 0 mitigates) and OAuth incremental-authorization custom `OAuth2AuthorizedClientService` (most novel piece). MEDIUM on public booking abuse scale at launch.

**Gaps:** Phase 0 success criterion needs explicit pass gate (chat streaming smoke test on OpenRouter). Picker UX + `mailbox_calendar_preference` UX surface need design in Phase 1, not deferred to Phase 2. Operator dashboard for booking-abuse counts is a v1.4 ship requirement in Phase 3.
