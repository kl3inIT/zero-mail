# Feature Landscape — v1.4 Calendar Co-Pilot + Drive Filing

**Domain:** AI email assistant — Google Calendar + Google Drive integrations
**Researched:** 2026-06-17
**Scope:** new features only. v1.0–v1.3 capabilities (triage, draft replies, rules engine, chat assistant, admin console, multi-Gmail foundation) are taken as already shipped and are referenced as dependencies, not re-researched.
**Reference baseline:** Inbox Zero (`../inbox-zero`) — schema and code inspected directly. Shortwave / Superhuman positioning from competitor research.
**Confidence:** HIGH (IZ schema + handlers + AI tools directly read; product directives in PROJECT.md L53–L73 are authoritative).

## Conventions

- **Table stakes** — must ship in v1.4 for the feature to feel credible. Missing = users will say "this isn't a real calendar/drive integration."
- **Differentiator** — competitive edge; landable inside v1.4 if cheap, otherwise carry-forward.
- **Anti-feature** — deliberately omitted in v1.4. Each anti-feature carries an explicit reason (privacy, scope, infra, locked decision).
- **Complexity** — S (≤ 1 phase, no new infra), M (1 phase + DB schema or external API integration), L (multi-phase, new agentic loop, or new long-running orchestration).
- **Dependencies on v1.0–v1.3** — what this feature reuses so we do not re-invent or fork.

---

## 1. Calendar — Connection Management

**Goal:** Connect one or more Google Calendar accounts (often the same Google identity as a Gmail mailbox, but allowed to differ), enable/disable specific calendars within a connection, resolve timezone for availability math, and recover from `invalid_grant`.

**IZ baseline** (`schema.prisma` L1135–L1172): `CalendarConnection { provider, email, accessToken, refreshToken, expiresAt, isConnected, @@unique(emailAccountId, provider, email) }` with `Calendar { calendarId, name, primary, isEnabled, timezone }` children. Allows multiple Google identities per email account; each connection can enable a subset of its calendars.

### Table stakes
| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| Connect multiple Google Calendar accounts via incremental OAuth | IZ already does it; users routinely have personal + work calendars on different Google identities | M | Reuse v1.3 OAuth-intent split (CONNECT_MAILBOX → CONNECT_CALENDAR). Scopes: `calendar.freebusy` (read busy windows) + `calendar.events` (write events for bookings + propose_meeting). |
| List calendars within a connection; per-calendar `is_enabled` toggle | Users want busy time from work calendar but bookings written to personal | S | Mirror `Calendar.isEnabled`. |
| Per-calendar timezone with workspace fallback | Slot suggestions must show user-local times | S | IZ stores `timezone` on `Calendar` plus a `TimezoneDetector.tsx` (browser-side IANA detection) that backfills the user record. |
| Primary calendar marker | Used as default destination for booking links and `propose_meeting` writes | S | `Calendar.primary` boolean. |
| `DISCONNECTED` state + reconnect prompt on `invalid_grant` | Parity with v1.0 AUTH-05 Gmail behavior | S | Reuse same connection-health surface. |
| AES-GCM encryption of calendar refresh token | Privacy/security parity with Gmail OAuth tokens | S | Reuse v1.0 OAuth refresh-token AES-GCM pattern. |
| Disconnect cascade — revoke + delete calendar connection deletes booking links bound to its calendars (set null on destination) | IZ uses `onDelete: SetNull` for `BookingLink.destinationCalendarId` | S | Avoid orphaned bookings. |

### Differentiator
| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Connection-scoped UI badges in `AccountMenu` (calendar count + health) | Reinforces multi-account model from v1.3 | S | Cheap to copy the v1.3 mailbox switcher pattern. |
| Auto-detect browser timezone on first login | Reduces a settings click | S | IZ's `TimezoneDetector.tsx` already shows the pattern. |

### Anti-feature
| Anti-feature | Why omit | Source |
|---|---|---|
| Microsoft Outlook / Office 365 Calendar | Gmail-only constraint, PROJECT.md L73 | Locked |
| Apple iCloud / generic CalDAV | Different auth model; not in v1 ecosystem | Locked (Gmail-only) |
| Cross-tenant shared calendars | No team feature in v1.4 (TEAM-* deferred) | Out of scope |

### Dependencies on v1.0–v1.3
- **v1.3 multi-Gmail connection model** — copy the `gmail_connections` table shape and `MailboxContext` pattern almost verbatim into `calendar_connections` / `calendars`. Reuse the OAuth intent split so login, Gmail connect, and Calendar connect are three distinct intents on the same Google OAuth registration.
- **v1.0 OAuth refresh-token AES-GCM crypto** — same key, same field-level encryption.
- **v1.0 AUTH-05 reconnect prompt** — reuse the DISCONNECTED state machine.

**Open question:** should a Calendar connection be **workspace-shared** (any mailbox can use it) or **mailbox-isolated** (1 calendar conn per mailbox)? Recommendation: **mailbox-keyed by foreign key but workspace-readable** — write actions (events, propose_meeting) target a specific mailbox's outbound gateway, but free/busy reads can fan out across all calendar connections owned by the workspace. This mirrors v1.3's "credits shared, OAuth isolated" boundary.

---

## 2. Calendar — Availability in Draft Replies

**Goal:** When the user asks the chat assistant or hits "AI draft reply" on a thread that smells like scheduling, the AI fetches free/busy across all enabled calendars and proposes 3–5 concrete slots in the reply body.

**IZ baseline** (`utils/ai/calendar/availability.ts`, `utils/calendar/unified-availability.ts`): a tool-call (`aiGetCalendarAvailability`) takes the thread messages, queries `getUnifiedCalendarAvailability` (fans out across all `isConnected` connections + `isEnabled` calendars), and returns `{ suggestedTimes: [{start, end}], noAvailability? }`. Output schema is `YYYY-MM-DD HH:MM` strings. Duration is **inferred from email context** by the LLM (not hard-coded). If the user has a configured booking link, the prompt is told to mention it as a fallback.

### Table stakes
| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| Free/busy fan-out across all enabled calendars on all connections | Otherwise a slot collides with the user's other calendar | M | Single Gmail FreeBusy API call per connection; union the busy ranges. |
| LLM-inferred meeting duration from thread context | "30-min intro" vs "1-hour deep-dive" — hard-coding is wrong | S | Already the IZ approach; constrain via Zod/Bean Validation in tool schema. |
| Business-hours awareness (configurable working window + days) | A 7 AM Sunday slot is offensive | S | Settings on the user record: `workingHoursStart`, `workingHoursEnd`, `workingDays[]`. |
| Timezone-aware slot rendering — slot shown in the recipient's timezone if detected, else user's | Avoid the classic "1pm your time or mine?" trap | M | IZ shows times in user's TZ + labels. v1.4 minimum: user-TZ slots, clearly labeled. Recipient-TZ inference is differentiator. |
| Booking-link fallback in reply text — "or use my booking link: …" | If we ship booking links (§3), this is the obvious bridge | S | IZ passes `bookingLinkAvailable` boolean into the AI tool. |
| Tool-call surfacing inside chat assistant (`getCalendarAvailability`) | v1.1 chat already has 24 tools — this is one more | S | Add to existing chat tool catalog; tenant context already enforced. |
| Free/busy result NEVER persisted | ARCH-02 — calendar busy times are user data extracted from Google | S | Short-lived in-memory only, like the email-body cache. |

### Differentiator
| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Recipient-timezone inference from prior thread signals (signature, location, sent times) | Big UX win for international scheduling | M | IZ doesn't do this. Defer to v1.5 unless cheap. |
| "Same-week priority" heuristic — prefer slots in the next 5 business days | More natural feel than "first available slot 8 weeks out" | S | Easy bias in the prompt; LOW effort, MED value. |
| Round-up to next 15-min boundary | Avoids "3:07 PM" output | S | Post-processing on tool output. |

### Anti-feature
| Anti-feature | Why omit | Source |
|---|---|---|
| Persisting suggested-slot history | Email content / scheduling state; ARCH-02 | Locked |
| Auto-sending a reply with slots (no preview) | Outbound auto-send through chat already requires preview confirm (CHAT-* v1.1); rules-driven `propose_meeting` is the separate path (§6) | Decision |
| Multi-attendee availability ("find a slot when 5 people are free") | Requires either querying others' calendars (impossible without their OAuth) or invite-and-poll (Doodle-style); huge new surface | Scope |

### Dependencies on v1.0–v1.3
- **v1.1 chat assistant tool catalog** — add `getCalendarAvailability` alongside existing 24 tools; same tenant safety, same Scoped Values context.
- **v1.0 draft replies (DRFT-01..04)** — extend the system prompt to include availability tool when calendar is connected; do not branch the draft flow.
- **v1.0 LLM-09 (no prompt/completion persistence)** — busy times stay in memory.

---

## 3. Calendar — Booking Links (Calendly-style)

**Goal:** Public `/book/<slug>` page where strangers can pick a time, the system writes a Google Calendar event with Google Meet link, sends invites.

**IZ baseline** (`schema.prisma` L1174–L1246 + `app/(app)/[emailAccountId]/calendars/` UI files): `BookingLink { slug @unique, title, description, durationMinutes, slotIntervalMinutes, locationType (GOOGLE_MEET|PHONE|IN_PERSON|CUSTOM), locationValue, minimumNoticeMinutes (default 120), maxDaysAhead (default 90), timezone, destinationCalendarId }`; `BookingWindow { weekday 0–6, startMinutes, endMinutes }` for weekly recurring availability; `Booking { guestName, guestEmail, guestNote, startTime, endTime, status, providerEventId, videoConferenceLink, cancelTokenHash, idempotencyToken }` for the actual reservations.

Notable IZ details:
- `BookingLink.slug` is **globally unique** — `/book/<slug>` is single-tenant-routable.
- `BookingLink.emailAccountId` is `@unique` — **one booking link per email account** in current IZ. Comment says "temporary: relax when we support multiple booking links per account." Worth deciding for v1.4.
- `Booking.cancelTokenHash` enables anonymous self-service cancel/reschedule without account.
- `Booking.idempotencyToken` + `@@unique(bookingLinkId, idempotencyToken)` prevents double-booking from repeated POST.
- `BookingStatus = PENDING_PROVIDER_EVENT | CONFIRMED | …` — event creation in Google is async; the booking exists locally first.

### Table stakes
| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| Public booking page at `/book/<slug>` — no auth required | Whole point of the feature | M | New Next.js route group; tenant resolved from slug; carefully scoped data (only the public booking link's fields, not email/calendar internals). |
| Slug uniqueness + collision-safe creation | Two users picking "alex" must not collide | S | Global unique index, friendly error code on collision. |
| Duration + slot-interval (e.g. 30-min meetings on :00 and :30) | Standard Calendly UX | S | Two ints; IZ uses `slotIntervalMinutes` distinct from `durationMinutes`. |
| Weekly availability windows (per-weekday start/end minutes) | Standard "Mon-Fri 9-5" pattern | S | `booking_windows` child table. |
| Minimum notice (default 2h) + max days ahead (default 90d) | Prevents 5-minute-from-now bookings and 2-year-out spam | S | IZ defaults are sensible. |
| Location type: Google Meet | Most common; auto-add `conferenceData.createRequest` to Calendar event | M | Requires `events.insert?conferenceDataVersion=1`. |
| Location type: phone | Common alt; store phone in `locationValue` | S | |
| Location type: in-person | Store address in `locationValue` | S | |
| Destination calendar — which calendar gets the event | User has work + personal; must pick | S | FK to `Calendar`. `SetNull` on calendar disconnect — link still resolvable, event write would fail until user reselects. |
| Double-booking prevention via free/busy check at time of booking | Concurrent bookings on the same slot | M | Re-query free/busy server-side immediately before `events.insert`; reject with friendly error. |
| Idempotency token on booking POST | Refresh/double-click safety | S | `@@unique(bookingLinkId, idempotencyToken)`. |
| Google Meet link auto-generation | Booking page UX expects it | S | Conference data API. |
| Self-service cancellation via tokenized URL | Standard scheduling UX; avoids inbox storm | S | Hash + opaque token in cancel URL. |
| Timezone shown on public page; auto-detect viewer's TZ | Booker comes from anywhere | S | Browser TZ + select. |

### Differentiator
| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Multiple booking links per user (e.g. "30-min intro" + "1h deep-dive") | IZ flagged as todo; founders + sales people want both | M | Drop the `emailAccountId @unique` on `booking_link`. Naming + slug pattern. |
| Custom branding on public page (logo, color, footer) | Lifts the page above generic Calendly | S | Optional; pull from workspace settings. |
| Buffer between meetings | Real users don't want back-to-back | S | New int `bufferMinutes`. |
| Question fields on booking form ("What do you want to discuss?") | Captured in `guestNote` IZ-style or expanded | S | Stretch. |

### Anti-feature
| Anti-feature | Why omit | Source |
|---|---|---|
| Round-robin / team scheduling | No team feature in v1.4 (TEAM-* deferred) | Scope |
| Paid bookings / Stripe integration | Big surface; not in target user's table stakes | Scope |
| Group events / event registrations | Different product (Eventbrite-shaped) | Scope |
| Embedded `<iframe>` widget for booker's own website | Niche; build standalone page first | Scope |
| Booking-page localization beyond VI/EN | i18n surface already locked to VI/EN | Locked |

### Dependencies on v1.0–v1.3
- **v1.0 web frontend (WEB-01)** — public booking page is a new route group; needs an unauthenticated layout that bypasses login redirect middleware. New attack surface; must be tested.
- **v1.0 LLM-09 / privacy** — guest note may contain free text; not a privacy concern for *Zero Mail's* user (the booker typed it on a public page), but should still be sanitized.
- **§1 Calendar connection** — booking link points to a destination calendar.
- **v1.2 outbound gateway** — confirmation/cancel emails sent from the booking system should route through the existing outbound gateway boundary (ArchUnit-locked) **only if sent from the user's Gmail**; if sent transactionally from a system address, route through Resend like the daily digest.

---

## 4. Calendar — AI Meeting Briefs

**Goal:** N hours before an external meeting, generate a per-guest briefing (role, company, recent thread context, pending items) and deliver it via email + in-app daily digest.

**IZ baseline** (`utils/ai/meeting-briefs/generate-briefing.ts` + `schema.prisma` L1248–L1263): cron walks external meetings in the lookahead window, gathers context per guest (last N emails from that guest, last N meetings with that guest), runs an agentic AI loop with up to 15 steps (`MAX_AGENT_STEPS = 15`), optional Perplexity/web-search MCP tool calls, and finalizes via a structured `finalizeBriefing` tool with `guestBriefingSchema = { name, email, bullets[] }` (max 10 bullets, max 10 words each). Stores only `MeetingBriefing { calendarEventId, eventTitle, eventStartTime, guestCount, status }` — **no briefing content persisted**, only metadata.

### Table stakes
| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| Cron-triggered brief generation N hours before each external meeting | Whole feature mechanism | L | New worker job; runs in `backend/worker`; uses Postgres SKIP LOCKED queue (v1.0 pattern). Configurable N (default ~24h). |
| External-meeting detection — at least one guest with a domain ≠ user's | Don't brief on solo focus blocks or internal 1:1s every day | S | Simple domain compare. |
| Past-email-history context fan-in (N most recent threads with each guest) | The headline value — "remind me who this person is" | M | Reuses Gmail search by participant. IZ caps at 10 emails / guest. |
| Past-meeting context (prior briefings or past calendar events with same guest) | Continuity across recurring meetings | M | Walk prior `MeetingBriefing` for guest emails. |
| Per-guest bullet output (≤ 10 bullets, ≤ 10 words each) | Scannable in 10 seconds | S | Structured tool output; Zod schema in IZ, Spring AI structured output in v1.4. |
| Delivery via transactional email (Resend) | Where else would it go? | S | Reuse v1.0 daily digest delivery infra. |
| Delivery via existing in-app daily digest | Where users already look | S | Add brief block to the daily digest template. |
| Brief content NEVER persisted (only metadata) | ARCH-02 / privacy — brief is derived from email content | S | Strictly enforce; IZ already follows this. |
| Idempotency — `@@unique(emailAccountId, calendarEventId)` | Cron retries must not double-send | S | IZ pattern. |
| Premium gate hook | Brief is expensive (15-step agent + optional web search) | S | Cost-tier check in the trigger; charge platform credits per brief. |

### Differentiator
| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Optional web-search MCP tool (Perplexity / Tavily / SerpAPI) for "who is this guest" | Big quality lift when the user has never emailed the guest | M | IZ uses Perplexity + Google + OpenAI. v1.4: gate behind admin-enabled provider (BYOK or platform). Spring AI MCP client support is GA in 2.0.0. |
| User-configurable trigger window (1h / 4h / 24h before) | Different user rhythms | S | Per-user setting. |
| Skip generation for recurring internal meetings | Don't brief on weekly standup | S | Cheap heuristic. |

### Anti-feature
| Anti-feature | Why omit | Source |
|---|---|---|
| Slack / Teams / Telegram delivery channels | No messaging-channel infra in v1.4; PROJECT.md L62 | Locked |
| Storing brief body in DB | ARCH-02 — brief is derived email content | Locked |
| Briefing for internal-only meetings by default | Mostly noise; opt-in if at all | UX |
| Brief generation for **past** meetings (recap mode) | Different feature (meeting notes / recap); not in scope | Scope |
| Multi-recipient cc'ing of brief (sharing with assistants) | Team feature; deferred | Scope |

### Dependencies on v1.0–v1.3
- **v1.0 Postgres SKIP LOCKED worker queue** — new `meeting_brief_job` row type; reuse outbox/processing_job patterns.
- **v1.0 LLM gateway + per-tenant credit ledger** — brief is a billable LLM action; budget through BILL-05.
- **v1.0 daily digest (ANL-03)** — block within the existing template.
- **v1.0 LLM-05..08 sanitization** — all guest-context email content goes through sanitize + truncate before reaching the LLM. Body cache is short-lived in-memory only.
- **v1.1 chat assistant tool patterns** — agentic loop with structured `finalizeBriefing` tool mirrors v1.1's tool-call discipline.
- **v1.2 admin LLM catalog** — admin selects which model gets `LlmUseCase.MEETING_BRIEF`. New use case key.

**Open question:** which models get `MEETING_BRIEF`? Strong reasoner (Claude Sonnet 4 / GPT-5-class) is right for the agentic loop; cheaper model is wrong because guest context is dense. Premium-gate this behind a credit cost recommendation.

---

## 5. Calendar — Calendar-aware Triage

**Goal:** Make sure calendar-relevant Gmail messages (invites, cancellations, reschedules) are never silently archived by an aggressive rule. Surface them prominently in the triage UI.

**IZ baseline** — IZ has `Rule.skipCalendar` (L904 in schema) as a per-rule opt-out flag, meaning "do not apply this rule to calendar invites." Calendar invites are detected via `text/calendar` MIME parts in the message. No dedicated triage UI pin.

### Table stakes
| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| Detect Google Calendar invite, accept/decline/tentative, cancellation, reschedule on incoming Gmail | These messages have predictable structure (METHOD=REQUEST/CANCEL/REPLY in iCal part) | S | Parse `text/calendar` part during ingestion. |
| Default rule-engine guard: invites/cancellations bypass `archive`, `mark_read`, `mark_spam`, and outbound auto-send actions | Rules engine can be aggressive — user must not silently miss a cancel | S | Hard guard at the rule executor, similar to safety net. |
| Per-rule `skip_calendar` opt-in flag in When/Then schema | Allows power users to override the default guard once they trust their rules | S | IZ's flag. |
| Top-of-inbox surfacing in triage UI for active calendar-invite messages | User must see them | S | Triage projection: add `is_calendar_event` boolean. |
| Auto-extract event details (title, start, end, guests) for display in triage | Improves scannability | S | Parse iCal in-memory; surface metadata, not body. |

### Differentiator
| Feature | Value | Complexity | Notes |
|---|---|---|---|
| One-click accept/decline/tentative from triage UI | Saves a Gmail round-trip; uses `events.patch` + Gmail label | M | Optional; chat assistant can already do this with a tool. |
| Cancellation banner — "this meeting was cancelled" with delta from the original event | High signal | S | Match REPLY/CANCEL iCal METHOD to prior REQUEST. |

### Anti-feature
| Anti-feature | Why omit | Source |
|---|---|---|
| Auto-accepting invites based on rule | Auto-send-class risk; trust-ending bad outcome | Decision |
| Pulling calendar invites from non-Gmail sources | Gmail-only locked | Locked |

### Dependencies on v1.0–v1.3
- **v1.0 triage orchestrator + safety net** — add calendar-invite detection to the safety policy.
- **v1.0 ingestion pipeline (MAIL-01..05)** — parse `text/calendar` during projection; metadata-only persistence (event title + time, not body).
- **v1.0/v1.2 rules engine** — add `skip_calendar` to the rule schema; emit in compiler output.

---

## 6. Calendar — Rule Action `propose_meeting`

**Goal:** A new rule When/Then action: "When X (e.g. cold-email request for a meeting), draft a reply containing my suggested available slots." User-enabled, gated by global Auto-send rules + safety net + rate cap.

**IZ baseline** — IZ does **not** have an explicit `propose_meeting` action as a rule action; it surfaces calendar slots inside chat-driven draft replies. Adding this as a rule action is a Zero Mail-specific extension that fits v1.2's RACT-* outbound rule architecture.

### Table stakes
| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| New rule action `propose_meeting` in When/Then schema and rule compiler | The shape of the feature | M | Add to the action catalog (RACT-* extension). Compiler must extract via structured output, not regex (CLAUDE.md). |
| Action behavior: fetch free/busy → AI-draft reply with 3–5 slots → route through outbound gateway | Reuses §2 availability tool + v1.2 outbound gateway | M | Single execution path. |
| Gated by global `Auto-send rules` setting (default ON) + safety net + per-tenant outbound rate cap + idempotency + audit | All RACT-* outbound rules go through this gate; new action inherits the contract | S | No new gate logic; same architecture. |
| Failure mode: blocked / failed actions logged as failed audit, **no fallback Gmail draft** | Product directive in CLAUDE.md (Write actions section) | S | Same as `send_reply` etc. |
| Slot freshness — re-query free/busy at execution time, not from a stale plan | Otherwise we'd propose a slot the user already booked | S | Execute path computes; rule plan does not encode slots. |
| Per-rule "minimum duration to propose" config (e.g. only propose if recipient asked for ≥30 min) | Prevents 10-min-meeting noise | S | Optional rule param. |

### Differentiator
| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Booking-link fallback inside `propose_meeting` reply | If the user has a booking link configured, include both inline slots and the link | S | Reuses §2 bridge. |
| Slot count parameter (default 3) | Different user preferences | S | Rule param. |

### Anti-feature
| Anti-feature | Why omit | Source |
|---|---|---|
| Auto-confirm-on-recipient-reply ("when they pick a slot, write it to calendar automatically") | Two-step autonomous loop, new safety class; deferred until v1.5+ | Scope |
| Cross-calendar 3-way scheduling between two users of Zero Mail | Team feature | Scope |

### Dependencies on v1.0–v1.3
- **v1.2 RACT-* outbound gateway + safety gates** — `propose_meeting` is just another outbound rule action.
- **§2 availability AI tool** — same code path.
- **v1.0/v1.2 rule compiler** — extend NL→AST extraction to recognize "propose meeting" intent → structured action.
- **v1.0 triage audit + undo** — same audit + 30-day undo window.

---

## 7. Drive — Connection Management

**Goal:** Connect Google Drive on the minimal `drive.file` scope (only see/write files the app created), one connection per workspace (not per mailbox).

**IZ baseline** (`schema.prisma` L1365–L1386, `utils/drive/scopes.ts`): `DriveConnection { provider, email, accessToken, refreshToken, expiresAt, isConnected, @@unique(emailAccountId, provider) }` — **one Drive per email account**, but `email` field can differ from the email account's identity (comment: "can differ from emailAccount - e.g. connect work Drive to personal email"). Scope is the deliberately narrow `drive.file` per IZ's privacy posture, which matches Zero Mail's ARCH-02.

### Table stakes
| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| Connect Google Drive via incremental OAuth on `drive.file` scope only | Critical privacy posture — `drive.file` only sees app-created files | M | New OAuth intent CONNECT_DRIVE on the same Google registration. |
| Reconnect on `invalid_grant` | Same lifecycle as Gmail/Calendar | S | Reuse pattern. |
| AES-GCM encryption of Drive refresh token | Same as Gmail | S | Same key path. |
| Per-**workspace** connection, not per-mailbox | Drive is org-level for most users; multiple Gmail mailboxes can share one filing destination | M | **Recommendation:** keep `drive_connections` at workspace scope (one per workspace, optionally with a `mailbox_id` nullable for users who want per-mailbox isolation). This is a deliberate divergence from IZ's per-email-account model and aligns with v1.3's workspace-shared boundary. |
| Disconnect cascades to `filing_folder` + rule attachment sources | Don't leak orphan references | S | IZ uses `onDelete: Cascade`. |

### Differentiator
| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Multiple Drive connections per workspace (personal + work Drive) | Power-user feature | M | Drop the `@@unique(emailAccountId, provider)` constraint; mirror multi-Gmail. Probably defer. |

### Anti-feature
| Anti-feature | Why omit | Source |
|---|---|---|
| Microsoft OneDrive / SharePoint | Gmail-only ecosystem; PROJECT.md L73 explicitly defers | Locked |
| Dropbox / Box / generic cloud | Same reason | Scope |
| Full `drive` (or `drive.readonly`) scope | Privacy violation; CASA risk; user revolts | Locked |
| Storing Drive file content in any DB column for any duration | ARCH-02 — would mirror IZ's persisted attachment model which we explicitly reject | Locked (PROJECT.md L66) |

### Dependencies on v1.0–v1.3
- **v1.0 OAuth refresh-token AES-GCM** — same crypto.
- **v1.3 workspace-shared / mailbox-isolated boundary** — Drive is workspace-shared.
- **v1.3 OAuth intent pattern** — add CONNECT_DRIVE intent.

---

## 8. Drive — AI Document Auto-Filing

**Goal:** When an email arrives with an attachment (PDF / DOCX / image), an AI engine analyzes it in-memory, picks a destination folder based on user-defined filing prompt + listed folders, uploads via Drive API, and notifies the user.

**IZ baseline** (`utils/drive/filing-engine.ts`, `utils/drive/document-extraction.ts`, `schema.prisma` L1388–L1446):
- **Filing prompt** — `EmailAccount.filingPrompt` free-text instruction ("file invoices in /Finance, contracts in /Legal, …").
- **Folder picker** — user maintains a list of allowed `FilingFolder { folderId, folderName, folderPath }` per Drive connection.
- **Engine pipeline** — download attachment → extract text (PDF via `unpdf`, DOCX via `mammoth`, plaintext) capped at 10k chars / 50 pages → fetch user folders → AI `analyzeDocument` with filing prompt + folder list → produce `{ folderId, folderPath, fileId, reasoning, confidence }` → upload to Drive (`files.create` with parent folderId) → write `DocumentFiling` audit row.
- **Confidence + user review** — `DocumentFiling.confidence` scored; if below threshold, status flips to `ASK_USER` with `wasAsked = true`, notification email asks "should I file this in X?". User correction tracked via `wasCorrected`, `correctedAt`, `originalPath`.
- **Notifications** — confirmation email per filing (configurable: `filingConfirmationSendEmail`); optional messaging-channel notification.
- **Reply learning** — `handle-filing-reply.ts` parses user reply ("no, file under /Receipts instead") and updates the filing.
- **Anti-feature for Zero Mail:** IZ persists the `DocumentFiling` row including `folderPath`, `reasoning`, `confidence`. Reasoning is short and metadata-like; folder path is user-curated. **This row is acceptable to persist** (it's filing metadata, not extracted email content). What we reject is `AttachmentDocument.content` / `.summary` (L1483–L1484) which stores **extracted file content** — that violates ARCH-02. See §9.

### Table stakes
| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| User-configurable filing prompt (free text) | The whole intent-extraction mechanism | S | New field on user/workspace settings. |
| Allowed-folders list — user picks specific Drive folders the AI is allowed to file into | Prevents the AI from creating folders all over the user's Drive; respects `drive.file` scope reality | M | `filing_folders` table; folder browser UI uses Drive API to list folders the app has touched. With `drive.file` scope, the user must explicitly grant per-folder access via Google Picker — UX gotcha. |
| Document text extraction in memory only (PDF, DOCX, plaintext) | Filing requires content understanding | M | Java equivalents: PDFBox 3.x for PDF, `docx4j` or `poi-ooxml` for DOCX. Cap chars + pages identically to IZ (10k / 50). |
| AI `analyzeDocument` call producing `{ folderId, folderPath, confidence, reasoning }` | Structured decision | M | Spring AI structured output. New `LlmUseCase.DOCUMENT_FILING`. |
| Confidence scoring + low-confidence ASK-USER flow | Auto-filing is high-risk; users want a review path | M | Threshold configurable; default ~0.7. Low-confidence → status=ASK_USER, send notification, store decision when user responds. |
| Upload via Drive API to selected folder | Whole point | S | `files.create` with parent. |
| `DocumentFiling` audit row (metadata only — folderId, folderPath, confidence, reasoning, wasAsked, wasCorrected, originalPath) | Auditability + user correction tracking + dedup | S | OK to persist; this is filing metadata, not email content. |
| **Extracted attachment text is NEVER persisted** | Hard ARCH-02 boundary; PROJECT.md L66 explicitly rejects IZ's `AttachmentDocument` model | S | Java pipeline must hold extracted text in stack-frame variables / autocloseable streams; no DB column, no log line, no cache that outlives the request. |
| Idempotency `@@unique(emailAccountId, messageId, attachmentId)` | Pub/Sub retries | S | IZ pattern. |
| In-app + email notification on file/ask | User must know what happened | S | Reuse digest delivery + new in-app feed. |
| User reply to ASK-USER mail can correct the destination | Closes the loop — IZ's `handle-filing-reply.ts` pattern | M | Optional but high-quality. Inbox parsing already exists. |
| Per-tenant LLM credit metering on each filing | This is a billable LLM call | S | Reuse BILL-02..04. |

### Differentiator
| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Correction learning — feed past `originalPath`/`folderPath` corrections back into the prompt for future filings | Quality lift over time | M | Metadata-only; ARCH-02-safe. |
| Calendar-invite attachment skip (don't try to file `.ics`) | Noise reduction | S | IZ has `isCalendarInviteAttachment` check. |
| Multi-attachment batching (one LLM call per message, not per attachment) | Cost reduction | M | Cheaper, but harder to attribute confidence; deferred. |

### Anti-feature
| Anti-feature | Why omit | Source |
|---|---|---|
| **Persistent `AttachmentDocument` indexing (content + summary in DB)** | ARCH-02; PROJECT.md L66 explicit rejection of IZ's pattern | Locked |
| RAG over filed documents | Implies embeddings + persistent content; LLM-09 / locked privacy | Locked |
| Auto-filing into folders the user did not list | Surprises users; abuses `drive.file` semantics | Decision |
| Auto-creating new folders without ASK-USER | High-risk Drive write; surprise factor | Decision |
| OCR for scanned PDFs (images-only) | Big new dep (Tesseract / cloud OCR); deferred until v1.5 demand | Scope |

### Dependencies on v1.0–v1.3
- **v1.0 ingestion + projection** — extension point: per-message attachment list (metadata only) already projected; pipeline must download attachment bytes JIT.
- **v1.0 LLM gateway + per-tenant credits** — `DOCUMENT_FILING` use case; sanitize + truncate extracted text before prompt.
- **v1.0 LLM-09 / ARCH-02** — **hard constraint**: extracted text is short-lived stack-frame state only.
- **v1.1 chat assistant patterns** — structured output + tool-call discipline.
- **v1.2 admin LLM catalog** — bind `DOCUMENT_FILING` to a vision-capable or PDF-capable model (Claude Sonnet handles PDF natively).
- **v1.3 mailbox-aware ingestion** — filing pipeline must respect `MailboxContext` so writes target the correct workspace Drive connection.

**Critical implementation note for v1.4 backend:** the Java pipeline must be structured so that extracted text passes through a single method (e.g. `DocumentFilingService.fileAttachment(messageId, attachmentId)`) as a method-local `String` that is overwritten before the method returns. No field, no cache, no log line, no temp file on disk. Add an ArchUnit rule that forbids extracted text from being assigned to a class field. Optionally, wrap in `Sensitive<String>` (v1.0 FND-03) for log-scrub safety.

---

## 9. Drive — Attachment Source Rules

**Goal:** A rule action can attach specific Drive files (curated by the user) to outbound replies. E.g. "When someone asks for the pitch deck, attach `/Sales/Pitch-Deck-Q3.pdf` to the reply."

**IZ baseline** (`schema.prisma` L1448–L1491):
- `AttachmentSource { name, type (FOLDER|FILE), sourceId, sourcePath, ruleId, driveConnectionId }` binds a rule to a Drive folder or file.
- `AttachmentDocument { fileId, name, mimeType, summary, content, indexedAt }` — **periodically indexed snapshot of files in the source folder**, used so the AI can pick the right file at action time. **This is the ARCH-02 violation Zero Mail explicitly rejects.**
- Action types: `Action.staticAttachments` (Json `AttachmentSourceInput[]`) holds explicit files attached to email actions.

The feature splits into two modes:

**Mode A (static file pin):** "always attach file X". No AI needed. Safe in v1.4.
**Mode B (smart pick from folder):** "pick the right file from folder Y based on the email's intent". IZ does this by indexing folder content into `AttachmentDocument.content` + `.summary`, then asking the AI to pick. This is the part Zero Mail must not implement IZ-style.

### Table stakes
| Feature | Why expected | Complexity | Notes |
|---|---|---|---|
| Mode A: pin a specific Drive file to a rule action as a `static_attachment` | Real use case (NDA, deck, brochure) with zero privacy risk | S | Store `{ fileId, name }` in rule action JSON. At execution, download via Drive API → attach to outbound email → discard buffer. |
| Per-rule file-size limit + Drive→Gmail size cap awareness (25MB Gmail limit) | Bounce-prevention | S | Validate at execution. |
| Audit row recording which Drive file was attached to which outbound | RACT-* audit parity | S | Metadata only. |
| Files attached via outbound gateway → must go through the same v1.2 ArchUnit boundary | Architectural consistency | S | Outbound gateway extension. |

### Differentiator
| Feature | Value | Complexity | Notes |
|---|---|---|---|
| Mode B-safe variant: "pick from folder by **filename + Drive metadata only**" (no content extraction, no embeddings) | Useful for users with `/Sales/*.pdf` and wanting the AI to pick by filename | M | AI sees folder listing `[{fileId, name, mimeType, modifiedAt}]` only — no `content`, no `summary`. ARCH-02-safe. Quality is lower than IZ but acceptable. |
| User-curated folder allow-list separate from §8 filing folders | Separation of concerns | S | Could share `filing_folders` table or split. |

### Anti-feature
| Anti-feature | Why omit | Source |
|---|---|---|
| **IZ-style `AttachmentDocument.content` / `.summary` persistence** | ARCH-02; identical reason as §8 | Locked |
| Embeddings over Drive file contents for retrieval | Locked privacy — no embeddings of user files | Locked |
| Attaching files larger than Gmail's 25 MB inline limit without fallback | Bounce risk | UX |
| Auto-attaching files based on rule **without** the rule being explicitly enabled for outbound | Surprise data exfil via auto-send rule | Safety |

### Dependencies on v1.0–v1.3
- **v1.2 RACT-* + outbound gateway** — attachment-bearing send/reply goes through the same gateway; new `attachments` field on the gateway request.
- **§7 Drive connection** — required for any attachment fetch.
- **v1.0 LLM-09 / ARCH-02** — Mode B sees filename + metadata only.

---

## Feature Dependencies (cross-feature graph)

```
§1 Calendar connection  ──┬─→ §2 Availability ──┬─→ §3 Booking links
                          │                     └─→ §6 propose_meeting rule action
                          └─→ §4 Meeting briefs (needs event read)
                          └─→ §5 Calendar-aware triage (works without §1 for iCal in Gmail, better with §1)

§7 Drive connection ──┬─→ §8 Auto-filing
                      └─→ §9 Attachment source rules
```

§1 unblocks §2, §3, §4, §6. §7 unblocks §8 and §9.

Sub-orderings:
- §2 should ship before §6 (the rule action reuses the availability tool).
- §3 should ship after §2 (booking link is the fallback in §2's reply).
- §5 (calendar-aware triage) is **independent** of §1 connection — it reads iCal parts on the existing Gmail ingestion. Could ship first as a quick win.
- §9 Mode A (static pin) is much smaller than §8 and could ship as an "MVP Drive feature" before the full filing engine.

---

## MVP Recommendation for v1.4

If the milestone runs long and needs a v1.4 cut, ship in this order:

1. **§5 Calendar-aware triage** — S, no calendar OAuth needed, immediate trust value.
2. **§1 Calendar connection (multi-Google, enable/disable, timezone, reconnect)** — M, table-stakes infra.
3. **§2 Availability in draft replies + chat tool** — M, big visible feature.
4. **§3 Booking links (single link per user, Google Meet only, weekly windows)** — M; defer multi-link + custom branding.
5. **§7 Drive connection (single workspace-scoped, `drive.file` only)** — M, table-stakes infra.
6. **§9 Mode A (static attachment pin)** — S, ships the Drive integration credibly with zero ARCH-02 risk.
7. **§6 `propose_meeting` rule action** — M, completes the calendar story.
8. **§8 AI document auto-filing** — L, the headline Drive feature; needs the strictest ARCH-02 discipline.
9. **§4 AI meeting briefs** — L, the most expensive feature; ship last, premium-gate.

**Defer to v1.5+:** recipient-TZ inference (§2), multiple booking links (§3), custom branding (§3), one-click invite accept (§5), correction learning (§8), Mode B safe-variant attachment picker (§9). All have well-understood paths back in.

---

## Anti-feature summary (must NOT ship in v1.4)

| Anti-feature | Why | Origin |
|---|---|---|
| Microsoft Outlook / Office 365 / OneDrive | Gmail-only locked | PROJECT.md L73, Constraints |
| Apple iCloud / CalDAV / Dropbox / Box | Outside Google ecosystem | Same |
| Slack / Teams / Telegram meeting brief delivery | No messaging-channel infra in v1.4 | PROJECT.md L62 |
| Full Drive scope (anything beyond `drive.file`) | CASA + privacy + user-trust risk | ARCH-02 + IZ parity |
| Persistent `AttachmentDocument` content/summary | ARCH-02 | PROJECT.md L66 |
| Embeddings over user mail or files | Locked privacy | CLAUDE.md |
| RAG over filed documents | Implies persistent content | Same |
| Persisting meeting brief body | Brief is derived email content | ARCH-02 |
| Multi-attendee availability (cross-user free/busy beyond user's own calendars) | Requires others' OAuth | Scope + locked TEAM-* |
| Round-robin team scheduling | Team feature | Scope |
| Auto-accept calendar invite via rule | Auto-send-class trust risk | Decision |
| Auto-create new Drive folders without ASK-USER | Surprise factor + abuses `drive.file` | Decision |
| OCR for scanned PDFs | New dep; defer | Scope |
| Auto-confirm-on-recipient-reply loop for `propose_meeting` | Two-step autonomous loop | Scope |
| Localization beyond VI/EN on booking page | i18n locked | Locked |

---

## Sources

- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/prisma/schema.prisma` (L1135–L1491) — IZ Calendar, Drive, Booking, MeetingBriefing, AttachmentSource/Document data model.
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/calendar/` — `unified-availability.ts`, `event-writer.ts`, `timezone-helpers.ts`, `handle-calendar-callback.ts`.
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/ai/calendar/availability.ts` — `aiGetCalendarAvailability` shape, tool schema, booking-link fallback flag.
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/ai/meeting-briefs/generate-briefing.ts` — agentic loop, `MAX_AGENT_STEPS=15`, structured `finalizeBriefing` tool, Perplexity/Google/OpenAI providers, MCP tools integration, guest-bullet schema.
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/drive/filing-engine.ts` + `document-extraction.ts` — pipeline shape, `unpdf` + `mammoth` deps, 10k char / 50 page caps, idempotency on `(emailAccount, message, attachment)`.
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/drive/handle-filing-reply.ts` — reply-learning correction loop.
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/(app)/[emailAccountId]/calendars/` UI tree — `BookingLinksSection`, `CalendarConnections`, `ConfigureBookingLinkDialog`, `TimezoneDetector`.
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/(app)/[emailAccountId]/drive/` UI tree — `AllowedFolders`, `DriveSetup`, `FilingActivity`, `FilingPreferences`, `FilingRulesForm`.
- `.planning/PROJECT.md` L53–L73 — v1.4 milestone scope, explicit anti-features, ARCH-02 rejection of persistent attachment indexing.
- `CLAUDE.md` Privacy + Write Actions sections — privacy scope, outbound gateway boundary, body-content ban distinguishing extracted vs user-authored content.

**Confidence:** HIGH on IZ schema/code (read directly), HIGH on product directives (read directly), MEDIUM on complexity ratings (estimates based on IZ scope + v1.3 phase sizes — to be validated during phase planning).
