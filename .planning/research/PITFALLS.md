# Pitfalls Research — Zero Mail v1.4 (Calendar Co-Pilot + Drive Filing)

**Domain:** Adding Google Calendar (multi-calendar OAuth on `calendar.freebusy` + `calendar.events`, free/busy in draft replies, public booking links, AI meeting briefs, calendar-aware triage, `propose_meeting` rule action) and Google Drive (`drive.file` connection, AI document auto-filing, attachment-source rules) to the v1.3 baseline: multi-Gmail workspace foundation, Spring Boot 4.1 / Java 25 / Spring AI 2.0.0 GA, ARCH-02 no-body-persistence privacy contract, `MailboxContext` ScopedValue + ArchUnit `findByTenantId` ban, OutboundSendGateway-enforced send boundary, single-VPS Postgres 18 + Redis 7.
**Researched:** 2026-06-17
**Confidence:** HIGH on baseline invariants (sources read directly: `CLAUDE.md`, `.planning/PROJECT.md`, prior `PITFALLS.md` for v1.2). HIGH on CASA / scope-tier mechanics ([Google sensitive-scope verification docs](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification), [restricted-scope verification docs](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification), [Google CASA 2025 explainer](https://deepstrike.io/blog/google-casa-security-assessment-2025)). HIGH on Calendar API quota mechanics ([Calendar usage limits](https://developers.google.com/workspace/calendar/api/guides/quota), [freebusy.query reference](https://developers.google.com/workspace/calendar/api/v3/reference/freebusy/query)). HIGH on `drive.file` semantics ([Drive Picker overview](https://developers.google.com/workspace/add-ons/studio/drive-picker), [Drive API scope guide](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) — confirmed: a folder picked under `drive.file` grants write-into-folder but NOT read-existing-files-in-folder). HIGH on Spring AI 2.0 tool-execution migration ([Spring AI 2.0.0-RC1 announcement](https://spring.io/blog/2026/06/06/spring-ai-2-0-0-RC1-available-now/), [Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) — RC1 dropped the built-in call/stream tool-execution loop from every `ChatModel`; M6→GA is a real migration). MEDIUM-HIGH on booking-page abuse patterns (Calendly community + ops posts: per-IP CAPTCHA only after 2-3 bookings/hour is insufficient for a brand-new SaaS). MEDIUM on Calendar push-notification channel auth ([Calendar push notifications docs](https://developers.google.com/workspace/calendar/api/guides/push)).

> **Scope.** This document is the v1.4 delta only. v1.0/v1.1/v1.2/v1.3 pitfalls (raw-body persistence, ThreadLocal tenant leaks, JSONB schema drift, BYOK round-trip leaks, admin master-key handling, multi-Gmail isolation) live in prior milestone deltas and shipped phases. We surface only the new failure modes that Calendar + Drive features introduce, plus the **regression vectors** v1.4 features can use to silently undo v1.0–v1.3 trust invariants.
>
> **Mapping convention.** Each pitfall maps to a specific v1.4 phase (P1=Calendar OAuth + connection foundation, P2=Free/busy + draft availability, P3=Booking pages, P4=AI meeting briefs, P5=Calendar-aware triage + `propose_meeting`, P6=Drive OAuth + Picker, P7=AI document auto-filing, P8=Attachment source rules). Phase numbers are research-suggested; ROADMAP.md is authoritative.

---

## Critical Pitfalls

### Pitfall 1: Adding `calendar.events` + `drive.file` scopes re-opens the CASA window and silently pushes GA further out

**What goes wrong:**
v1.0 already kicked off CASA verification for restricted Gmail scopes (FND-07, dormant SEED-012). v1.4 adds three new scopes. Each scope falls into a different verification tier, and the team's mental model "we already did CASA, so this is free" is wrong:

| Scope | Tier (per Google docs) | Verification cost | CASA timeline impact |
|-------|------------------------|-------------------|----------------------|
| `https://www.googleapis.com/auth/calendar.freebusy` | **Non-sensitive** | Brand verification + privacy URL only | None — does not trigger sensitive/restricted review |
| `https://www.googleapis.com/auth/calendar.events` | **Sensitive** | Manual justification + demo video + privacy policy review (no CASA audit) | Adds a verification cycle to the OAuth consent screen; **does NOT** require CASA audit by itself but the OAuth app sits in "Testing" until justified |
| `https://www.googleapis.com/auth/drive.file` | **Non-sensitive** (per Google's "minimum scope" doc, deliberately) | Brand verification only | None — this is precisely why IZ picked `drive.file` over `drive` |
| `https://www.googleapis.com/auth/gmail.modify` (existing) | **Restricted** | CASA Tier 2 lab assessment (annual) | Already in flight via SEED-012 |

The traps:

1. **Mixing `drive.file` with `drive.readonly` "for the auto-filing inbox scan."** A developer convinced that "we need to see existing folders to suggest a destination" adds `drive.readonly` — which is **restricted** — and the entire app's verification timeline gets re-anchored to the new restricted scope. CASA re-clocks; any prior progress on `gmail.modify` doesn't transfer to a re-submission with new restricted scopes. **GA tag slips by an unbounded number of weeks** while CASA processes the new scope.
2. **Adding `calendar` (full) instead of `calendar.events` + `calendar.freebusy`.** The single-string `calendar` scope is **restricted**, not sensitive — same CASA-re-clock hazard as above. A "we'll just take everything to keep options open" decision moves the app from Sensitive verification to Restricted.
3. **Forgetting to update the OAuth consent screen scope list before users see the consent screen.** Adding a scope to the SecurityConfig client registration without updating the verified scope list in Google Cloud Console results in unverified-app warnings ("Google hasn't verified this app") for every user, even existing ones whose previous grant didn't include the new scope.
4. **Demo video drift.** Sensitive-scope verification requires a demo video showing each requested scope being used. v1.0's video covered Gmail only. v1.4 needs a new video showing `calendar.events` write + reasoning. If the video doesn't precisely match the in-app UX (because the UX is still in flight when the video is recorded), Google reviewer rejects → another 1-3 week cycle.
5. **Incremental authorization rejected by product but re-requested by reviewer.** Zero Mail bundles login + Gmail in one consent (per `feedback_bundled_oauth_scopes` memory). Adding Calendar/Drive to the same bundle inflates the initial consent screen to 4-5 permission groups — Google's reviewer may push back asking for incremental authorization (the pattern v1.0 explicitly rejected). Either we bend the product decision or we negotiate with the reviewer; both cost cycles.

**Why it happens:**
- "Scope" is treated as a Spring Security config edit, not a verification-process trigger.
- Tier classifications are non-obvious — Google's "sensitive" / "restricted" / "non-sensitive" mapping is published per-scope and not in any single table the team can grep.
- CASA is felt as a one-time tax; team forgets that the assessment is per-scope-set, not per-app.

**How to avoid:**

1. **One-page scope ledger in `docs/oauth-scopes.md`.** Per-scope: literal scope string, tier (non-sensitive / sensitive / restricted), verification artifact (brand only / video + justification / CASA), `git blame`-style "added in milestone X for feature Y" line. PR template item: "If you added a scope, did you update `docs/oauth-scopes.md`?"
2. **ArchUnit + grep test forbids restricted-tier scopes.** A `OAuthScopeAllowListTest` enumerates the allowed scope strings (Gmail subset already approved + `calendar.events` + `calendar.freebusy` + `drive.file`). Any string literal anywhere in `backend/` matching `googleapis.com/auth/[a-z.]+` that is NOT in the allow-list fails CI. Catches a developer typing `https://www.googleapis.com/auth/drive` (no suffix) or `https://www.googleapis.com/auth/calendar` (no suffix) — both restricted.
3. **Demo video is owned by a phase milestone, not by an individual.** Phase P1 (Calendar OAuth foundation) and Phase P6 (Drive OAuth) each own a checklist item "submit new demo video covering only this phase's scopes." Videos are recorded against a frozen UX snapshot at phase-close; not while UX is still drifting.
4. **CASA timeline expectation written into the milestone.** PROJECT.md "Explicitly deferred" section: "v1.4 ships `calendar.events` + `drive.file` in OAuth Testing mode behind the same SEED-012 production gate as Gmail. GA does not unblock at v1.4 close. CASA refresh window opens when ALL of Gmail / Calendar / Drive scopes are stable across one consecutive milestone." This stops anyone arguing "but v1.4 was supposed to GA us."
5. **Auth flow design proves we never need `drive.readonly`.** The auto-filing engine MUST work entirely with `drive.file` semantics: destination folder is picked once via Google Picker (giving write-into-folder permission), and the engine never enumerates existing files in that folder. This is enforced by Pitfall 3's design.

**Warning signs:**
- A PR adds a scope string not in `docs/oauth-scopes.md`.
- A spike branch experiments with `drive.readonly` or `drive` to "see what files are there."
- CASA / verification work is scheduled in v1.4 closeout planning.
- The OAuth consent screen review submits before the user-visible UX matches the demo video.
- An engineer says "we'll just request `calendar` to keep options open."

**Phase to own prevention:** **P1 (Calendar OAuth foundation)** for the scope ledger + ArchUnit allow-list + Phase 1 demo-video checklist. **P6 (Drive OAuth)** for the second demo-video checklist. PROJECT.md update on milestone start documenting the CASA non-unblock.

---

### Pitfall 2: Free/busy quota exhaustion under the AI draft-reply hot path

**What goes wrong:**
The "AI calendar availability in draft replies" feature calls `freebusy.query` whenever the AI needs to propose meeting times. Naïve implementation calls it on every draft generation, for every enabled calendar, on every model retry. Google Calendar API quota mechanics ([usage limits](https://developers.google.com/workspace/calendar/api/guides/quota)):

- **Default per-user quota** is `~600 queries / 60 seconds / user`. Sounds high until you realize one draft = N calendars × M model retries × P time windows, easily 10-50 calls per draft.
- **Per-project quota** is shared across the entire Zero Mail platform tenant population. A high-traffic tenant can starve every other tenant.
- 403 `quotaExceeded` and 429 `usageLimits` are the failure modes; both require exponential backoff, which directly extends draft latency.

Specific traps:

1. **Cache key omits the time window.** A naive cache `freebusy:{calendarId}` returns yesterday's result for today's draft. Cache key must include normalized time window (e.g., `[start..end]` rounded to 15-min buckets).
2. **Cache TTL too long.** TTL of 24h means a user who books a meeting via a different client (mobile Calendar app, Calendly directly) gets double-booked because Zero Mail's free/busy view is stale.
3. **No coalescing across concurrent drafts.** Two simultaneous draft generations for the same tenant call `freebusy.query` twice for the same window. Without a per-(tenant, calendarId, window) singleflight gate, every concurrent request adds quota pressure.
4. **Quota error fails the entire draft.** A 429 from `freebusy.query` aborts the draft instead of falling back to "Here are some times that work for me, [user]" without explicit slots — degrading gracefully should be the default.
5. **`calendarExpansionMax` defaults blow up on multi-calendar users.** [Per the docs](https://developers.google.com/workspace/calendar/api/v3/reference/freebusy/query), `calendarExpansionMax` is capped at 50 — but the team may not know this until a Workspace user with 80 calendars (team calendars + room resources) triggers a silent truncation.
6. **`quotaUser` parameter not set.** Google distinguishes per-user quota via the `quotaUser` opaque string. If not set, the entire platform shares one bucket; one heavy tenant exhausts the platform.
7. **AI retries amplify quota.** Spring AI agentic loop retries up to N times on tool errors. If the tool wraps `freebusy.query` and returns the raw 429, the LLM sees an error and may retry the tool — each retry costs another quota call.

**Why it happens:**
- Free/busy feels "lightweight" relative to a chat completion — devs forget it has its own quota.
- Caching is added reactively after the first quota incident.
- The "tool" abstraction in Spring AI hides retry semantics — the LLM-level retry is invisible to the call-site engineer.

**How to avoid:**

1. **Two-tier cache: Redis + per-request memo.**
   - L1: per-request `ConcurrentHashMap` keyed by `(calendarId, windowStart, windowEnd)` — caches inside one draft generation across all model retries.
   - L2: Redis `freebusy:{tenantId}:{mailboxId}:{calendarId}:{bucket15min}` with TTL = 60s. Short enough that a sibling-app booking surfaces within a minute; long enough to coalesce burst.
2. **Singleflight per `(tenantId, calendarId, bucket)`.** Use Redis `SETNX` or a per-JVM `ReentrantLock` keyed by the same tuple. Concurrent calls block on the first inflight call's result.
3. **Always set `quotaUser`** to `sha256(tenantId + ":" + calendarId)`. Per-tenant quota isolation. ArchUnit rule: any call to the Calendar `Freebusy.query` builder MUST set `quotaUser` — caught via a wrapper class `CalendarApiClient` that is the only call site permitted, plus an ArchUnit rule confining Google Calendar SDK imports to that class.
4. **Hard cap `calendarExpansionMax = 50`** in the wrapper + chunk requests for users with >50 calendars. Surface a UX warning when truncation would occur.
5. **Quota-failure graceful degradation in `propose_meeting` AND in draft-reply.** On 429, return a placeholder list `[{ "slot": null, "reason": "free_busy_unavailable" }]` to the LLM; the LLM tool description includes "if free_busy_unavailable, propose ranges instead of fixed times." Draft completes with degraded content; user sees a banner "Schedule lookup paused — retry in N seconds."
6. **Per-tenant outbound free/busy rate cap.** Token-bucket in Redis: max 60 free/busy calls / minute / tenant. Caps any one tenant from starving the project quota. Operator dashboard surfaces the top consumers.
7. **Exponential backoff with jitter** for 429/403 retries, capped at 3 retries; never auto-retry inside the LLM tool — the tool returns degraded immediately and the LLM is told.

**Warning signs:**
- A direct `new Calendar.Builder(...).freebusy().query(...)` call exists outside the `CalendarApiClient` wrapper.
- `quotaUser` is unset in any Calendar API call.
- The cache key does not include time bucket.
- LLM tool description doesn't document the "free_busy_unavailable" fallback path.
- No per-tenant outbound free/busy rate cap in Redis.

**Phase to own prevention:** **P2 (Free/busy + draft availability)** owns the wrapper, cache, singleflight, quotaUser convention, and rate cap. ArchUnit confinement test ships at P2 close.

---

### Pitfall 3: `drive.file` scope makes "Browse my Drive" UX impossible — surprise emerges only at integration time

**What goes wrong:**
The natural UX for auto-filing is "let users browse and pick a destination folder in their Drive." This is incompatible with `drive.file` semantics. Per [Google's official Picker docs](https://developers.google.com/workspace/add-ons/studio/drive-picker): *"Even if a user picks a folder with the Picker, the add-on can't read files that have not been created by the add-on. What it does allow is to determine whether the folder exists, and creating new files in that folder."*

The trap concretely:

1. **`/drive/files/list` returns empty** under `drive.file` because no files were created by Zero Mail yet. Engineer assumes a token bug; spends a day debugging OAuth.
2. **"Suggest destination folder based on existing filing patterns"** — the v1.4 SPEC implies the AI suggests folders based on past filing. Past filing under `drive.file` is visible (Zero Mail created those files), so this works. But the FIRST-FILING case for a new tenant has zero history → AI has no destination to suggest → either we make user type folder name manually, or we Google Picker every time.
3. **Folder enumeration `drive.files.list(q="mimeType='application/vnd.google-apps.folder'")`** returns empty too. Cannot offer "select from your existing folders" dropdown.
4. **"Attachment source from a Drive folder"** rule action requires the rule to attach files the user picked into a folder. Under `drive.file` we can read files the user explicitly opened via Picker, but we can NOT scan a folder and grab "any file that landed there since last check." So a rule like "every time someone in marketing emails me, attach the latest deck from my /Sales/Decks folder" cannot work as a server-side scan; the user must Picker-open each file individually, which is high-friction.
5. **Switching to `drive.readonly` or `drive` to unblock UX** is the worst escape hatch — see Pitfall 1 (CASA re-clock).
6. **Picker session-token expiry mid-flow.** Picker requires an OAuth access token that expires in ~1 hour. If the user opens the auto-filing modal, walks away for lunch, comes back, picks a folder — the token is stale and the file creation fails with a confusing error.

**Why it happens:**
- Drive API mental model is "I have an OAuth token → I can browse Drive." `drive.file` deliberately breaks that — and the docs only mention this in passing.
- The Drive Picker is named confusingly: "Picker" implies "pick anything you can see" but under `drive.file` you only see files your app created or files the user explicitly opens via Picker.
- IZ's own implementation may inadvertently rely on `drive.file` files-it-created list — fine for them; if our UX diverges from theirs, the assumption silently breaks.

**How to avoid:**

1. **UX is built around Picker, not Browser.** Every "select destination folder" interaction is a Google Picker invocation. No `<Select>` dropdown of folders. No "Browse my Drive" tree. Design copy: "Choose a folder via Google Drive Picker" with a single button.
2. **First-filing flow asks the user to pick destination, not the AI to suggest.** AI may suggest a folder NAME ("Receipts") but the user must Picker-open or Picker-create that folder. Once picked, the folder ID is stored in `drive_filing_destination` with `(tenant_id, mailbox_id, category)` — subsequent filings into that category go to the stored ID without re-prompting.
3. **AI "suggest destination" uses ONLY Zero-Mail-created file history.** A `drive_filed_document` table records `(tenant_id, mailbox_id, gmail_message_id_hash, gmail_attachment_id_hash, drive_file_id, drive_folder_id, filed_at, category)`. AI input = "user's past filings: 90% of `invoice_*.pdf` went to folder ID X." No live Drive scan. Honors ARCH-02: no attachment content stored, only IDs/hashes.
4. **Attachment-source rule = user-pre-picked file set, not folder scan.** UX: rule editor has a "Choose files to attach via Drive Picker" multi-select. Files are picked once at rule creation; the rule stores file IDs. Adding new files later = re-edit the rule. Trade-off explicitly documented in SPEC.md.
5. **Picker token refresh.** Frontend re-mints the Picker token on modal open (not on Settings page load). If the user idles >50 minutes inside the modal, re-mint silently on Picker open.
6. **`drive.file` semantic test in CI.** Integration test against a sandbox Google account: create a folder via Picker simulation → list folder contents via API → assert empty (proves our code does not depend on listing). Then create a file via our API → re-list → assert one file. Catches any developer who adds a "list files in folder" call expecting it to return Drive-wide results.
7. **Explicit ADR (`docs/adr/drive-file-only.md`) documenting why this UX exists.** Future maintainer asking "why don't we just add `drive.readonly`" hits the answer: CASA Tier 2 re-clock + ARCH-02 alignment + IZ precedent.

**Warning signs:**
- A Drive API code path calls `files.list` without a `q` clause that pins to known file IDs.
- The UX spec has a "Browse Drive" tree component.
- An engineer files a bug "Drive returns empty — token broken?"
- A spike adds `drive.readonly` to "unblock the folder picker."
- Picker is invoked on page load instead of on modal-open.

**Phase to own prevention:** **P6 (Drive OAuth + Picker)** owns the Picker-only UX contract, the ADR, the semantic CI test, the `drive_filing_destination` schema. **P7 (Auto-filing)** consumes the per-tenant history table. **P8 (Attachment source rules)** ships the "pick files once" UX with the trade-off documented.

---

### Pitfall 4: Public booking page is a bot/abuse magnet AND a calendar-DOS vector

**What goes wrong:**
A public, unauthenticated URL like `zeromail.app/book/jane-smith/30min` lets bots:

1. **Enumerate slugs** by walking `/book/[a-z]+` until they find a real one — then spam the form.
2. **Brute-force create events** on the host's calendar. Each successful POST writes a real Calendar event. A bot at 10 req/s creates 864,000 events/day — Calendar API write quota exhausted, host's calendar polluted with thousands of fake meetings, possibly Google Calendar account flagged for abuse.
3. **Replay a successful booking** with different times/emails (the form's `submit` endpoint is idempotent only on slot+email match — varying email defeats idempotency).
4. **Double-book** by racing two simultaneous bookings for the same slot. Without DB-level slot-locking, both writes succeed; host has overlapping events.
5. **Probe slug existence** via timing differences — 404 vs 200 latency differs; bot enumerates slugs even with strict 404 responses.
6. **Use the booking endpoint as a Calendar-event-spam tool against arbitrary hosts** by guessing real users' slugs and creating offensive event titles. Trust kill.
7. **Send invites with adversarial attendee emails** (the form's "Your email" field) — Google sends invitation emails to whatever the bot puts in. Zero Mail's domain becomes an open invite-spam relay.
8. **Bypass per-IP rate limits via residential proxy networks.** Calendly's "after 2-3 bookings/hour" CAPTCHA ([per Calendly community](https://community.calendly.com/how-do-i-40/adding-a-captcha-to-my-booking-page-2295)) is insufficient for a brand-new app whose abuse model isn't yet known.
9. **Carbon-copy slug from a competitor's leaked sitemap.** If `/book/*` is crawlable, search engines index hosts' slugs publicly — anyone can spam any host.

**Why it happens:**
- "Calendly-style booking" is a well-known UX; the failure modes feel like Calendly's problem, not ours.
- Public endpoints are not on Spring Security's authenticated chain; ScopedValue tenant context doesn't apply; per-tenant rate caps don't naturally bind.
- The booking endpoint writes to Calendar (real external state) — abuse has long-tail blast radius.

**How to avoid:**

1. **Slug enumeration mitigation:**
   - `/book/{slug}` returns identical timing + body for non-existent and existing slugs that have been disabled (`410 Gone` only after the slug is known via an authenticated probe). Slugs are user-chosen but minimum 12 chars OR random suffix (`/book/jane-smith-x7q9k`).
   - `robots.txt` disallows `/book/*`. No sitemap entry.
   - Response includes `X-Robots-Tag: noindex, nofollow`.
2. **CAPTCHA on every submit, not just after N attempts.** hCaptcha or Cloudflare Turnstile (free tier, no Google dependency) — server-side verification on the booking POST. Failed verification returns 400 without any DB or Calendar write.
3. **DB-locked slot uniqueness.** Schema: `booking_slot_reservation (booking_link_id, slot_start, slot_end, status, created_at)` with `UNIQUE (booking_link_id, slot_start) WHERE status IN ('reserved', 'confirmed')`. Two concurrent bookings: one INSERT wins, the other gets `unique_violation` → 409 to client. Calendar API write happens AFTER the DB lock is held, inside the same transaction (compensating delete if Calendar fails).
4. **Per-IP + per-slug + per-platform rate caps.** Redis token-bucket: `bookings_per_ip: 3/hour`, `bookings_per_slug: 10/hour`, `bookings_platform_wide: 1000/hour`. The platform-wide cap protects the project's Calendar API quota.
5. **Per-host booking cap.** A booking link has `max_bookings_per_day` (default 20, user-configurable). Hard limit at the slot-reservation layer.
6. **Invite-email throttle.** Per attendee email address (the form input), max 5 bookings to that email across the platform per day. Stops bot-driven invite spam to arbitrary targets.
7. **Email validation:** RFC 5321/5322 + MX lookup + disposable-domain block-list. Bot using `random@gmail-fake.tld` gets 400.
8. **Booking writes route through the OutboundSendGateway-equivalent.** A `BookingCalendarGateway` is the only path that calls `events.insert`. ArchUnit + grep confine it (mirrors v1.0 TRG-03 + v1.2 RACT-12 patterns). All booking events tagged with extended properties: `{private: {zeromail_booking_id: "..."}}` so support can identify Zero-Mail-created events vs user-created.
9. **Idempotency-Key required on submit.** Frontend generates UUID, sends as `Idempotency-Key` header; Redis dedup window 24h. Replays no-op.
10. **Audit:** every booking attempt (success/CAPTCHA fail/rate-limit/dup) writes an `booking_audit` row — append-only. Pattern mirrors `admin_audit_log` (per v1.2 Pitfall 6). Operator dashboard surfaces per-tenant booking abuse counts.
11. **Slug rotation.** User can rotate a slug (invalidating the public URL) without losing the link config — protective response if abuse detected.
12. **Booking page CSP + frame-ancestors.** `Content-Security-Policy: frame-ancestors 'none'` prevents clickjacking-driven bookings.

**Warning signs:**
- `/book/{slug}` has a different response shape for valid vs invalid slugs.
- No CAPTCHA on the public submit.
- Slot uniqueness enforced only in application code, not DB constraint.
- Calendar `events.insert` called from a controller directly, not via a gateway.
- Booking endpoint does not require an `Idempotency-Key` header.
- No per-attendee-email rate cap.

**Phase to own prevention:** **P3 (Booking pages)** owns slug semantics, slot-reservation schema, CAPTCHA wiring, `BookingCalendarGateway` + ArchUnit, idempotency, audit, rate caps. CI test: simulate 100 concurrent bookings for the same slot → assert exactly 1 success + 99 × 409.

---

### Pitfall 5: AI meeting brief contains extracted email content — silently breaks ARCH-02 if persistence/scope is wrong

**What goes wrong:**
The brief is generated by an agentic LLM loop that summarizes "guest context from past email history." The brief literally contains content **extracted from user email**. Where it lives, how long, and which DTO it crosses determines whether this is an ARCH-02 violation.

The traps:

1. **Brief saved to a `meeting_brief` table for the cron delivery window.** Even if "only for 24 hours" — this is persistent storage of email-content-derived output. ARCH-02 bans long-term storage; "24 hours" is long-term relative to the in-memory carve-out used for triage. Audit trail will show this is the same column family as banned bodies.
2. **Brief delivered via the in-app digest channel** which reads from `daily_digest` table. The brief's content lands in `daily_digest.body_text` or similar — same problem.
3. **Brief generation logs include the source emails.** The agentic loop iterates: "fetch past emails with this guest → summarize." Each tool call's args/returns may end up in DEBUG / TRACE logs if not properly scrubbed (LLM-09 logging ban applies but extends only to LLM prompts; tool call args may slip through).
4. **Brief contains an LLM completion** — the entire output IS an LLM completion. v1.0 banned persistent LLM completion storage. The brief is a permitted exception, but only if its storage shape is explicit.
5. **The "draft body carve-out" pattern (chat assistant draft body, per CLAUDE.md) is mis-applied as license for briefs.** The carve-out exists specifically because user-authored draft body is "user data" they review before send. A brief is NOT user-authored — it's LLM-extracted-from-email content. Wrong carve-out → wrong invariant.
6. **Brief delivered via email (Resend) is fine** *if* the email is sent and not persisted as a row in our DB. But if Resend's "scheduled delivery" requires us to persist the body until send time, we hold extracted content for hours.
7. **Brief generation runs as an agentic loop with tool access to `searchInbox`, `getMessage`, `getThread`.** Each tool call returns email bodies into the LLM context window. The body-content ban (CLAUDE.md ARCH-02) requires those outputs go through `ToolOutputSanitizer` and never persist into `chat_message.parts`. The brief loop must use the SAME sanitizer + the SAME in-memory-only short-cache pattern triage uses.

**Why it happens:**
- The product spec says "brief delivered by email + in-app digest" without specifying the storage envelope.
- The "draft body carve-out" precedent feels like cover.
- The agentic loop is new; nobody has wired the existing `ToolOutputSanitizer` into a non-chat-orchestrator call site.

**How to avoid:**

1. **Brief is rendered at delivery time from the same in-memory short-cache pattern used for triage.** No `meeting_brief.body` column. The cron job:
   - Reads `meeting_brief_schedule (id, tenant_id, mailbox_id, calendar_event_id, scheduled_for, delivery_channels, status)`.
   - At fire time, re-runs the agentic loop in-memory using the in-memory cache for body content.
   - Streams the generated brief directly to (a) the Resend email send call, and (b) the in-app digest renderer.
   - Persists only `{ generated_at, channels_delivered, model_used, token_count, success_flag }` — metadata. No body.
2. **In-app delivery uses a `derived_view` pattern, not a stored row.** The "in-app digest channel" surfaces the brief by re-running the loop on user view (cached for 1h in Redis with `EX 3600`, keyed by event ID, value scrubbed of any PII via `@Sensitive` wrapper). After 1h it's regenerated. No DB row.
3. **Resend delivery is fire-and-forget.** We POST the brief to Resend's send API; we do NOT use Resend's scheduled-send feature (which would require us to hold the body server-side until send time). The cron fires at brief-delivery time and sends synchronously.
4. **ToolOutputSanitizer extended to the brief loop.** Same regex/ArchUnit pattern from chat orchestrator (per `feedback_draft_body_carve_out_no_defense` memory — but that "trust the Java gate" applies to chat draft body; here we need active sanitization because the brief is NOT user-authored). The brief loop uses `BriefOrchestrator` which sits next to `ChatOrchestrator` and reuses the sanitizer.
5. **ArchUnit rule explicit on brief tables.** Any column matching `body|content|brief_text|summary|extracted` on any `meeting_brief*` table fails ArchUnit. Test: `MeetingBriefSchemaBanTest` reads `db.changelog/*` and asserts no banned column shape.
6. **PROJECT.md addendum to "Privacy scope" carve-out.** Two explicit carve-outs: (a) chat assistant **draft body** = user-authored, persistable; (b) meeting brief = extracted-email-content, in-memory-only at delivery, **NOT** persistable. Three sources now (chat draft, brief, triage) each with their own envelope explicitly enumerated.
7. **Logging ban on tool args inside the brief loop.** The `BriefOrchestrator` configures a per-thread MDC scrub that redacts `searchInbox`/`getMessage` tool args + returns at DEBUG/TRACE/INFO. Validated by a structured-log test that runs the loop with sentinel content and grep-asserts the sentinel never appears in test logs.
8. **No web-search-tool persistence either.** If the brief loop has optional web search, the web results may include PII matched from email. Those results pass through the same scrubber and are not persisted.

**Warning signs:**
- A Liquibase changeset creates `meeting_brief.body_text` / `meeting_brief.summary` / `meeting_brief.content`.
- The brief is generated at schedule time (hours before delivery) instead of at delivery time.
- The cron job persists the rendered brief to send later.
- `BriefOrchestrator` does not extend `ToolOutputSanitizer`.
- The brief loop's tool calls are visible in INFO-level logs.
- A developer cites "the chat draft body carve-out" to justify storing a brief.

**Phase to own prevention:** **P4 (AI meeting briefs)** owns the in-memory envelope contract, the `BriefOrchestrator`, the ArchUnit schema ban, and the PROJECT.md carve-out addendum. PROJECT.md update lands in the SAME PR as the first brief code.

---

### Pitfall 6: Drive attachment streaming OOMs the JVM at scale

**What goes wrong:**
The auto-filing pipeline: Gmail attachment download → AI summarization (or content type detection) → Drive upload. Each attachment is `≤25MB` per Gmail attachment cap, but the worker JVM hosts the entire `backend/worker` for the platform. Failure modes:

1. **`InputStream` to `byte[]` materialization.** `gmailAttachmentResponse.getBody().getData()` returns the base64 string; naive decode into `byte[]` holds 25MB per attachment in heap. Ten concurrent filings = 250MB; concurrent triage + chat + queue work + filing = OOM.
2. **AI summarization reads entire byte array into model context.** Even though Spring AI rejects 25MB into a prompt, the engineer may add a "summarize the document" step that PDF-extracts text first — PDF text extraction (e.g., PDFBox) allocates document-size structures.
3. **GC pauses spike under burst.** A burst of 50 large attachments → 1.25GB transient allocation → G1 mixed GC pauses spike to seconds. Triage latency P99 explodes; Pub/Sub push retries fire (Google retries after 30s by default), creating duplicate processing.
4. **No bounded executor for filing.** Filing tasks share the worker thread pool with triage. A filing burst starves triage.
5. **Drive resumable upload not used.** Drive supports resumable uploads (`uploadType=resumable`) for >5MB; single-shot uploads (`uploadType=multipart`) hold the whole body for upload + retry — doubling memory.
6. **`MultipartFile.transferTo` to a temp file** uses disk — fine — but the same engineer also reads the file back in to "hand to AI" or "verify hash" — heap pressure returns.
7. **In-flight stream not closed on cancellation.** User disconnects mid-filing; the Gmail input stream stays open until GC; quota leak + socket leak.
8. **JVM heap headroom on a single VPS is shared with `backend/api` + Postgres + Redis + Next.js.** A 4GB VPS with 1.5GB allocated to worker JVM can OOM at 60 concurrent 25MB filings.

**Why it happens:**
- The "stream from Gmail → stream to Drive" model is natural in theory, but the AI-in-the-middle step forces materialization in practice.
- Test environments use small attachments; OOM only manifests at production scale.
- Single-VPS deployment shares heap with everything.

**How to avoid:**

1. **Streaming pipe pattern, no full materialization.**
   - Gmail download: use `MediaHttpDownloader` with a `PipedOutputStream` → drain to a temp file on disk in chunked 256KB writes. NEVER `getBytes()` into a `byte[]`.
   - AI step: process the temp file via streaming readers (PDF text via PDFBox `PDFTextStripper.writeText(Writer)`, images via metadata-only sniff for size/MIME). Output is a small summary (KB), not the file.
   - Drive upload: read the temp file via `FileInputStream` + Drive's `uploadType=resumable` with 256KB chunks.
   - Temp file lives in a bounded directory (`/tmp/zeromail-filing/`) with a janitor that deletes files >1h old.
2. **Bounded filing executor.** Dedicated `Executor` with `maxConcurrent = 4` (configurable). Filing tasks queue separately from triage; backpressure on the Postgres outbox table.
3. **Per-attachment size cap with degraded path.** If size >10MB, skip AI summarization, just file with filename-based categorization. UX informs: "Large attachment — filed by name; AI category skipped."
4. **Heap budget assertion at startup.** A `HeapBudgetCheck` runs on `ApplicationStartedEvent`: reads `-Xmx` and asserts `worker.filing.maxConcurrent * worker.filing.maxAttachmentSizeMB * 4 < heapMax * 0.3`. Fails loud if config implies > 30% of heap can be held by filing.
5. **Resumable upload required on Drive side.** ArchUnit + grep: any Drive `Files.Create` call must set `uploadType=resumable`. Catch via wrapper `DriveApiClient.uploadFile(...)` that's the only call site.
6. **InputStream lifecycle linted.** `try-with-resources` mandatory for any `InputStream` from Gmail/Drive — verified via SpotBugs/SonarQube/checker-framework rule.
7. **Cancellation propagation.** Filing job carries a `CancellationToken`; periodic checks abort the download/upload and close streams.
8. **JVM tuning:** `-XX:+UseG1GC` with `-XX:MaxGCPauseMillis=200`, `-XX:+ExitOnOutOfMemoryError` (fail-fast over OOM-spinning). Single VPS uses container memory limits to fence the worker.
9. **Load test in CI.** Synthetic test enqueues 50 × 10MB attachments and asserts no OOM, no >500ms GC pause, no temp file leaks after completion.

**Warning signs:**
- A code path calls `attachment.getData()` and stores in a local `byte[]`.
- The filing executor is the shared worker pool.
- Drive upload code does not set `uploadType=resumable`.
- A try block opens a stream without try-with-resources.
- No bounded `/tmp/zeromail-filing/` janitor.
- No load test for concurrent filing.

**Phase to own prevention:** **P7 (AI document auto-filing)** owns the streaming pipe, bounded executor, heap budget check, ArchUnit `DriveApiClient` confinement, load test.

---

### Pitfall 7: AI agentic loop runaway — surprise BYOK bill or platform credit drain on meeting briefs

**What goes wrong:**
IZ's reference meeting-brief loop allows up to ~15 tool-calling iterations. Without budget caps:

1. **BYOK user gets a $50 brief.** A 15-step loop with web search + multiple inbox searches across a chatty inbox can cost $1-$5 per brief on a frontier model. Multiply by N briefs/day. User opens their OpenAI bill, sees a $200 surprise charge, refunds, churns.
2. **Platform credits drain on default-model users.** Per-tenant daily LLM spend cap (LLM-10, shipped v1.0) caps total spend but does NOT cap per-brief — one runaway brief consumes the day's cap, blocking all other briefs/triage/chat.
3. **Loop never converges on adversarial inputs.** A guest with weird historical email content triggers the model to keep "researching one more thing" — 15 iterations of `searchInbox` returning empty results, costing tokens for each step.
4. **Tool error → retry → repeat indefinitely.** Without an iteration cap or error-budget cap, a flaky `searchInbox` causes the LLM to retry; tool error returns count against iteration budget but are not visible as "cost."
5. **Web search adds variable third-party cost.** If the brief loop uses Serper/Bing/Tavily for "research the guest," each web search is metered separately AND adds prompt tokens to the next iteration.
6. **Streaming-mode loop doesn't enforce caps until completion.** Token caps need pre-call estimation + post-call settlement; streaming makes pre-call estimation harder.
7. **Cost-cap exception swallowed.** `BUDGET_EXHAUSTED` thrown mid-loop is caught by a generic `Exception` handler that retries → infinite loop within the cap-exhausted state.

**Why it happens:**
- "AI agentic loop" is the feature; "with caps" is the engineering job that comes second.
- Spring AI 2.0 unified the tool-execution loop ([per RC1 notes](https://spring.io/blog/2026/06/06/spring-ai-2-0-0-RC1-available-now/)), making iteration-cap configuration a single config point — but only if the team actually configures it.
- BYOK bypasses platform credit ledger; cap responsibility flips to the user but no user-side guardrail exists.

**How to avoid:**

1. **Per-brief hard caps in three dimensions:**
   - `MAX_ITERATIONS = 8` (cut from IZ's 15 by 40% — empirical default, tunable per tenant later).
   - `MAX_INPUT_TOKENS_PER_BRIEF = 60_000` (sum across all loop iterations).
   - `MAX_OUTPUT_TOKENS_PER_BRIEF = 4_000`.
   - `MAX_WALL_CLOCK_SECONDS = 90`.
   First cap hit → loop terminates with `BriefTruncated{reason}` outcome; brief delivered with "Best-effort summary — analysis depth limited" footer.
2. **Per-tenant per-day brief cost cap separate from LLM cap.** `meeting_brief_daily_spend_cap_credits` (default 50 credits/tenant/day). Brief gen reserves cap-bytes upfront; insufficient → degrade to non-AI brief (just calendar event metadata + last 1 email subject).
3. **BYOK cost preview.** Before generating a brief on BYOK, show "Estimated cost: $0.12 - $0.40 — confirm" in the in-app digest preview. User explicitly opts in to first brief; subsequent briefs auto-approve up to a user-set per-day BYOK budget. Tenant setting: `byok_brief_daily_usd_cap` (default $5).
4. **Configure Spring AI 2.0 `ToolCallingManager` iteration cap** explicitly:
   ```java
   ToolCallingManager.builder()
       .maxIterations(8)
       .build();
   ```
   ArchUnit asserts the `BriefOrchestrator`'s `ToolCallingManager` instance is constructed with explicit caps.
5. **Loop telemetry.** Each brief generation emits Micrometer metrics: `brief.iterations`, `brief.input_tokens`, `brief.output_tokens`, `brief.wall_clock_ms`, `brief.cost_credits`, `brief.outcome (success|truncated|error)`. Operator dashboard alerts on P99 iterations > 6 (regression signal).
6. **`BUDGET_EXHAUSTED` is a typed exception** that propagates through the loop without retry; caught only by the brief-job outermost handler which writes a structured audit row.
7. **Web search is opt-in per tenant**, default OFF for v1.4. Reduces variability and third-party cost surprise.
8. **Tool error budget.** A separate counter `tool_errors_per_brief`; >3 → loop terminates with truncated outcome. Stops the "flaky tool → infinite retry" trap.

**Warning signs:**
- `BriefOrchestrator` does not set `maxIterations` on `ToolCallingManager`.
- No per-brief token cap.
- BYOK briefs auto-fire without user preview.
- A test forces a flaky `searchInbox` and the loop doesn't terminate.
- No Micrometer metrics for brief loop.

**Phase to own prevention:** **P4 (AI meeting briefs)** owns all caps, Micrometer metrics, BYOK preview UX, ArchUnit test on `ToolCallingManager` builder.

---

### Pitfall 8: Spring AI 2.0.0 GA migration churn breaks tool-call streaming the v1.3 chat already exercised

**What goes wrong:**
v1.3 uses Spring AI 2.0.0-M6. v1.4 brings new agentic-loop work (brief generation, `propose_meeting` tool). CLAUDE.md says "Spring AI 2.0.0 GA on 2026-06-12." Per [Spring AI 2.0.0-RC1 blog](https://spring.io/blog/2026/06/06/spring-ai-2-0-0-RC1-available-now/) and [Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html):

1. **Built-in call/stream tool-execution loop dropped from `ChatModel`.** RC1 (and presumably GA) removed the in-`ChatModel` tool loop; consumers must use `ChatClient` + `ToolCallingAdvisor` or build their own `DefaultToolCallingManager` loop. v1.3 code that did `chatModel.call(prompt)` with embedded tools **silently stops looping** — the model emits a tool-call message and execution stops. Chat assistant breaks mid-conversation; brief loop never iterates.
2. **`PromptChatMemoryAdvisor` deprecated** (per M6 notes); migration to advisors that require explicit conversation IDs. v1.3 chat may rely on implicit memory scoping; the migration replaces it.
3. **MCP annotation package renames + transport artifact relocations** (per M3 notes already in place). If v1.4 adds MCP-based tools (unlikely but possible), the import paths differ from M6.
4. **Jackson 2 → Jackson 3** migration was in M3 already, but v1.3 may have stale `com.fasterxml.jackson.*` imports that survived; GA may enforce more strictly.
5. **`ToolContext` no longer carries conversation history.** Tools that read conversation context via `ToolContext` (some v1.3 tools may) return null.
6. **OpenRouter adapter behavior may have changed** for the OpenAI-compatible base-url pattern; tool JSON schema strictness may have tightened (no-arg tools need `properties: {}` per CLAUDE.md memory).
7. **Streaming `StreamingChatModel.stream(...)` signature/return type** may have shifted; v1.3 chat assistant uses streaming heavily.
8. **Anthropic/Google adapters auto-import behavior** may change with GA; provider-specific BYOK derivation in `core.llm.gateway.springai` may need rewrites.

**Why it happens:**
- The team's libs.versions.toml may have already been bumped from M6 to GA at v1.4 start, OR may still be M6 — both states are dangerous (M6 will be deprecated; GA forces migration).
- "GA = stable" feels reassuring but masks 6+ months of breaking changes since M6.
- Tool-execution loop change is the highest-impact: silent behavior change, not a compile error.

**How to avoid:**

1. **Phase 0 / v1.4 entry task: Spring AI version bump + migration audit.** Before any v1.4 feature work:
   - Bump `libs.versions.toml` from `2.0.0-M6` to `2.0.0` GA.
   - Run full test suite; ArchUnit boundary tests on LLM adapter package.
   - Read [Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) end-to-end; check off each breaking change against current code.
   - Convert any `chatModel.call()` with embedded tools to `ChatClient.builder().defaultAdvisors(toolCallingAdvisor).build().prompt(...)`.
2. **Re-verify chat tool streaming on real OpenRouter route** as a smoke test. v1.3 had a real-streaming requirement (CLAUDE.md "no non-streaming fallback for chat assistant model calls").
3. **Explicit `ToolCallingManager` construction in `BriefOrchestrator` + `ChatOrchestrator` + `RuleProposeMeetingExecutor`.** No reliance on in-`ChatModel` loop. Single shared factory `ZeroMailToolCallingManagerFactory` configures iteration cap + retry + error-budget consistently across surfaces.
4. **Provider-shape contract tests.** Per provider + per BYOK adapter, a `@Tag("llm-eval")` smoke test issues a no-arg tool call and asserts the tool fires + result returns. Catches the `properties: {}` JSON schema regression (CLAUDE.md memory).
5. **Conversation memory advisor explicit conversation ID.** Migrate v1.3 chat memory wiring to explicit conversation ID = `chat_session.id`. ArchUnit asserts no `PromptChatMemoryAdvisor` import.
6. **Jackson import sweep.** Grep `apply plugin: spring-boot` modules for `com.fasterxml.jackson.databind` / `com.fasterxml.jackson.core` — flag any still-present. Per CLAUDE.md memory: `jackson-annotations` stays at old package, databind/core moved to `tools.jackson.*`.
7. **`ToolContext` audit.** Grep all `ToolContext` reads; document expected fields; rewrite any that read conversation history.
8. **Run M6 → GA diff in a CI job that fails on dependency-tree change.** `./gradlew dependencyInsight --dependency=org.springframework.ai:spring-ai-bom` artifact in CI logs; reviewed in PR.

**Warning signs:**
- libs.versions.toml still pins `2.0.0-M6` at v1.4 phase 1 close.
- A `chatModel.call(prompt)` call exists where the prompt has tool definitions but no `ChatClient` wrapping.
- `PromptChatMemoryAdvisor` import in any v1.3 chat code.
- A no-arg tool's schema doesn't include `properties: {}`.
- Chat assistant streams successfully but tools never execute on OpenRouter route.
- Brief loop "completes" in one iteration with no tool calls.

**Phase to own prevention:** **P0 / Phase Zero (Spring AI 2.0 GA migration)** — a stub phase before P1 to do the bump + migration audit. P4 (brief) and P5 (propose_meeting rule) inherit the validated `ToolCallingManager` factory.

---

### Pitfall 9: Multi-Gmail × workspace-shared Calendar cross-account leak

**What goes wrong:**
v1.3 shipped multi-Gmail per workspace. v1.4 adds multi-Google-Calendar. The ownership boundary needs explicit design:

- **Are Calendar connections workspace-shared (one set of calendars used across all mailboxes) OR mailbox-isolated (each Gmail has its own Calendar set)?**

If wrong, the leak modes are real:

1. **Free/busy from Google account A used in draft reply from Gmail mailbox B.** User connects Gmail-A (personal) + Gmail-B (work) + Calendar-A (personal). Drafting a reply on Gmail-B uses Calendar-A's free/busy — leaks personal availability into work emails. User intent: "use only work calendar for work emails."
2. **`propose_meeting` rule on Gmail-B writes an event to Calendar-A.** Personal calendar fills with work meetings without user consent.
3. **Booking page tied to Calendar-A but linked from Gmail-B sender signature.** Visitor books on personal calendar.
4. **Brief generation pulls history across both Gmails for a meeting on Calendar-A.** Work meeting brief includes personal email context.
5. **`MailboxContext` ScopedValue (v1.3) carries `gmail_connection_id` but no `calendar_connection_id`.** Calendar code paths must either bind their own scoped value OR consult a per-mailbox calendar preference. Without explicit binding, the default-active-calendar wins — wrong tenant intent.

The v1.4 SPEC says Calendar is per-workspace, not per-mailbox. That's a coherent default but exposes the user to leaks (1)–(4) unless per-mailbox override is supported.

**Why it happens:**
- "Workspace-shared" feels simpler than "per-mailbox" — Calendar is "one user, one schedule" semantically.
- The leak mode requires the user to have **mixed personal + work** accounts, which is the actual target persona.
- `MailboxContext` is the v1.3 isolation primitive; extending it to Calendar wasn't designed.

**How to avoid:**

1. **Explicit per-mailbox → calendar mapping.** Schema: `mailbox_calendar_preference (mailbox_id, calendar_connection_id, role)` where role ∈ {`primary_freebusy`, `event_write_target`, `brief_source`}. A mailbox has at most one mapping per role. Default at mailbox creation: mailbox's owning Google account → matching Calendar connection (if exists) → primary for all three roles.
2. **`CalendarContext` ScopedValue.** Bound by a filter at request start, derived from `MailboxContext.currentOrThrow()` + `mailbox_calendar_preference` lookup. Calendar code paths read `CalendarContext.currentForRole(Role.FREEBUSY)`. No code path reads "the active Calendar" without going through this.
3. **ArchUnit: `findAllCalendarsForTenant` ban** (parallel to v1.3 `findByTenantId` ban). Calendar repository methods that don't carry a mailbox / calendar_connection scope are forbidden in application code.
4. **UX surface in Settings:** "Which calendar should mailbox X use for free/busy / event writes / meeting briefs?" Per-role selector. Default visible; explicit choice required if the user has more than one Calendar connected.
5. **`propose_meeting` rule action carries a `calendar_connection_id`** — not derived at runtime. Rule editor surfaces the picker; rule clone via copy-rules (v1.3) prompts "this rule writes to Calendar-X; copy it to mailbox-B which uses Calendar-Y — keep target or re-pick?"
6. **Booking link tied to a specific `calendar_connection_id` + a specific `gmail_connection_id`.** Stored explicitly; UI prevents implicit-default choice.
7. **Cross-account isolation test extended** from v1.3's mailbox isolation: tenant connects (Gmail-A, Calendar-A) and (Gmail-B, Calendar-B); test asserts a draft on Gmail-B never reads Calendar-A free/busy unless the user explicitly mapped them.
8. **Brief generation reads ONLY the brief-source Gmail mapped for the calendar that owns the meeting.** Brief for a Calendar-A meeting reads Gmail-A inbox; never cross.

**Warning signs:**
- `mailbox_calendar_preference` table does not exist.
- A draft-reply code path resolves "active calendar" without consulting the per-mailbox mapping.
- `propose_meeting` rule executor reads "first connected calendar."
- Booking link schema lacks `gmail_connection_id` and `calendar_connection_id` columns.
- No ArchUnit ban on `findAllCalendarsForTenant`-shaped repository methods.

**Phase to own prevention:** **P1 (Calendar OAuth + connection foundation)** owns the schema + `CalendarContext` + ArchUnit. P2/P3/P4/P5 each verify their feature respects the mapping.

---

### Pitfall 10: Calendar enable/disable mid-flight invalidates a brief or draft generation

**What goes wrong:**

1. **User disables a calendar while a brief is being generated.** Brief loop is mid-iteration when the user toggles `calendar_connection.status = 'DISABLED'`. Tool calls keep returning data from the disabled calendar; brief uses it; brief is delivered with data the user just said "stop using."
2. **Reconnect after `invalid_grant` re-enables a calendar the user disabled.** OAuth reconnect endpoint sets `status = 'CONNECTED'` unconditionally; user's explicit "disable" intent is lost.
3. **Free/busy cache holds disabled-calendar data.** Cache TTL is 60s (per Pitfall 2); for that minute, drafts use disabled-calendar availability.
4. **Per-mailbox preference points at a disabled calendar.** `mailbox_calendar_preference.calendar_connection_id` → disabled connection. Code paths must handle this — drop to "no free/busy available" rather than fall back to a different calendar without consent.
5. **Pub/Sub `users.watch` for Calendar continues firing after disable** because we forgot to call `events.stop` on the channel. Notifications accumulate; storage waste; potentially picked up by stale handlers.
6. **`calendar_connection.status` flag has too many states.** `CONNECTED | DISCONNECTED | DISABLED | INVALID_GRANT | REVOKED | TEMPORARILY_PAUSED`... drift = state-machine bugs.

**Why it happens:**
- Mid-flight cancellation is rarely tested.
- The "reconnect after revoke" path is rare; the engineer assumes reconnect == enable.
- Watch-channel cleanup is an "easy to forget" side effect.

**How to avoid:**

1. **Tight state machine on `calendar_connection`.** Three states only: `ACTIVE`, `INACTIVE` (user disabled OR `invalid_grant`), `REVOKED` (cleanup target). All transitions logged with cause. `OrderedEnum`/`IdentifiedEnum` per CLAUDE.md.
2. **Reconnect from `INACTIVE` does NOT auto-resume usage.** OAuth reconnect flips status to `ACTIVE` but requires explicit "re-enable for use" confirmation from the user (matching the trust-first pattern).
3. **Cache invalidation on status transition.** `calendar_connection` status change fires `CALENDAR_CONNECTION_STATUS_CHANGED` Spring Modulith event; free/busy cache evicts all `freebusy:*:{calendarId}*` keys.
4. **Brief loop and draft-reply re-check status before each tool call** (cheap Redis read of status enum). Mid-flight disable terminates the loop with `CalendarDisabledMidFlight` outcome; brief degrades.
5. **Per-mailbox preference fallback contract.** If preferred calendar is `INACTIVE`, free/busy returns empty (with a UX banner "Free/busy paused for mailbox X — reconnect or change preference"). No silent fallback to another calendar.
6. **`events.stop` on watch channel** when status flips to `INACTIVE` / `REVOKED`. ArchUnit + grep test: any `Channels.watch` call site has a corresponding cleanup path verified by a state-machine test.
7. **CI integration test:** start brief gen, mid-loop set status to `INACTIVE`, assert loop terminates within 1 iteration + brief outcome is `CalendarDisabledMidFlight`.

**Warning signs:**
- `calendar_connection.status` has >3 enum values.
- Reconnect endpoint sets status unconditionally.
- Brief loop does not re-check status per iteration.
- No Modulith event on status change.
- `events.stop` is missing from disable/revoke paths.

**Phase to own prevention:** **P1 (Calendar OAuth foundation)** owns the state machine + status-change event. **P4 (briefs)** + **P2 (draft availability)** consume the status-check contract.

---

### Pitfall 11: Liquibase migration stacking on v1.3's big multi-mailbox migration

**What goes wrong:**
v1.3 just landed major schema shifts (multi-mailbox columns, FK redirects, `MailboxContext` binding tables). v1.4 adds ~10 new tables (calendar_connection, calendar, freebusy_cache_meta, booking_link, booking_slot_reservation, meeting_brief_schedule, drive_connection, drive_filing_destination, drive_filed_document, mailbox_calendar_preference). Failure modes:

1. **FK ordering chaos.** v1.4 tables reference both `tenant_id` and `mailbox_id` (gmail_connection_id). If a changeset orders columns wrong, FK on a not-yet-created table fails on fresh-DB bootstrap.
2. **Changesets edited after merge.** CLAUDE.md rule #10 (Liquibase discipline): applied changesets are immutable. The first developer accidentally edits a v1.4 changeset after staging deploy; checksum mismatch on prod deploy.
3. **Rollback windows missing.** Each new changeset needs explicit rollback block. A late-cycle "we'll skip rollback for now" gets forgotten.
4. **Calendar event_id stored as Postgres `TEXT` vs `VARCHAR(N)`.** Google event IDs can be long (up to 1024 chars per Google docs); a `VARCHAR(255)` cap silently truncates on insert (Postgres errors, but the engineer may default to TEXT — fine — or to a `varchar` with `varchar(255)` — wrong).
5. **JSONB columns for free/busy snapshots or filing metadata without `jsonb_path_ops` index** → query performance regression as data grows.
6. **Migration runs on prod with worker still draining a multi-mailbox migration backfill** from v1.3 → competing DDL locks → worker errors.
7. **Seed data assumes v1.3 default-mailbox already backfilled.** If v1.4 ships before v1.3 backfill completes in any environment, seed crashes.
8. **`mailbox_calendar_preference` UNIQUE constraint** on `(mailbox_id, role)` enforced by Postgres exclusion or unique index — choosing the wrong shape breaks the per-role default.
9. **No `preCondition` on environment-sensitive changesets.** E.g., a changeset that backfills calendar preferences from existing mailboxes runs on a fresh dev DB with no mailboxes → silently no-op'd OR errors confusingly.

**Why it happens:**
- v1.3 just shipped; the team feels schema fatigue and rushes v1.4.
- 10 new tables is a lot — review thoroughness drops.
- Hot-path tables (free/busy cache, filing audit) need index discipline; defaults don't.

**How to avoid:**

1. **Phase-level schema review checklist.** For each v1.4 phase, a `SCHEMA-REVIEW.md` lists: tables created, FK ordering, indexes added, rollback blocks present, preConditions used, JSONB columns + their indexes, column type vs Google's documented max length.
2. **One logical change per changeset** (per CLAUDE.md rule #10). 10 tables → ~30-40 changesets, not 5 mega-changesets.
3. **CI on changeset immutability.** Pre-merge: a git hook (already in repo OR add) prevents modifying any file matching `db/changelog/changes/*.yaml` that has been merged to main. Forces new-roll-forward pattern.
4. **Calendar `event_id` and Drive `file_id` types:** use `VARCHAR(1024)` (Google docs cap). Schema review verifies.
5. **JSONB indexes:** every new JSONB column has an `ON ... USING gin (col jsonb_path_ops)` index in the SAME changeset.
6. **No backfills against multi-mailbox state.** v1.4 schema migrations are additive only. Backfills of calendar preferences happen via application code on first mailbox-touch, not via Liquibase.
7. **Rollback blocks mandatory.** ArchUnit-equivalent for YAML: a CI script greps every new `db/changelog/changes/*.yaml` for `rollback:` block; missing rollback fails CI.
8. **preConditions on env-sensitive changesets.** Backfills (if any) use `preConditions` with `onFail: MARK_RAN` and `sqlCheck` for "do we have data to backfill?"
9. **Postgres MCP `analyze_query_indexes`** run on the new free/busy + booking + filing audit tables as part of phase acceptance.
10. **Deploy gate.** Liquibase changes deploy via a separate "schema-only" pre-deploy step; app pods deploy only after schema migration completes. Avoids worker-vs-migrator lock fights.

**Warning signs:**
- A changeset creates >1 logical schema change.
- A `db/changelog/changes/*.yaml` file is modified in a PR.
- New JSONB column without paired GIN index.
- Calendar `event_id` typed `VARCHAR(255)` or smaller.
- No rollback block on a new changeset.

**Phase to own prevention:** Every phase. Phase-specific schema review at phase planning. Final v1.4 cross-phase schema audit before milestone close.

---

### Pitfall 12: Spring Modulith verification fails on Calendar↔Outbound and Gmail↔Calendar event boundaries

**What goes wrong:**
Spring Modulith verifies module isolation. New cross-module flows:

- **Gmail → Calendar:** calendar-aware triage parses an invite from Gmail and writes to Calendar.
- **Calendar → Outbound:** `propose_meeting` rule action reads free/busy then sends a reply via OutboundSendGateway.
- **Calendar → Drive:** brief generation pulls past attachments from Drive context (unlikely for v1.4 but a future trap).
- **Booking page → Calendar:** public booking writes events.

Failure modes:

1. **Direct cross-module repository call.** `GmailTriageService` calls `CalendarConnectionRepository` directly → `spring-modulith-test` fails the boundary check at build time.
2. **Synchronous cross-module call where event fits.** `CalendarInviteHandler` calls `OutboundSendGateway` synchronously from a request thread → couples Calendar module to Outbound and complicates transaction boundary. Should be an after-commit event.
3. **Listener in `backend/api` for an event published in `backend/core`** — works in-process but breaks if api/worker split processes (per CLAUDE.md convention #6: "Spring events do not cross backend/api ↔ backend/worker processes").
4. **Event payload includes email body content.** A `MailMessageWithInviteObserved` event includes the iCal body; persists if Modulith's event publication registry stores it; ARCH-02 violation.
5. **Booking page in `backend/api` module fires an event that triggers Calendar event creation in `backend/core`** — fine — but the cron retry layer is in `backend/worker`. Cross-process event = wrong.
6. **Listener annotations:** `@ApplicationModuleListener` is only for cross-module inside core (per `feedback_modulith_listener_scope` memory). Using it in `backend/api` to listen to a core event is wrong; use `@TransactionalEventListener(AFTER_COMMIT)`.

**Why it happens:**
- New modules don't have established event types; first implementer reaches for the easy import.
- Modulith verification runs in test phase; first integration test failure surfaces late.

**How to avoid:**

1. **Define event types in `backend/core` ONLY** for cross-module events: `CalendarInviteObserved`, `MeetingBriefScheduled`, `BookingCreated`, `DocumentFiled`, `AttachmentSourceAttached`. Each event carries metadata only — IDs, timestamps, status codes — no email bodies, no LLM completions, no attachment content.
2. **Module boundaries declared in `package-info.java`** per Modulith convention. `core.calendar`, `core.drive`, `core.booking`, `core.brief` are new modules.
3. **`@ApplicationModuleListener` only inside `backend/core`.** API/worker listeners use `@TransactionalEventListener(AFTER_COMMIT)`.
4. **Cross-process handoffs use Postgres outbox** (per CLAUDE.md convention #6) — e.g., booking event creation triggered by API but executed by worker uses an outbox row.
5. **Modulith verification test green** at every phase close.
6. **Event payload ArchUnit test:** events in `core.*.events.*` may not have a field of type `String` longer-than-128-char allowed via a `@PayloadField` annotation that asserts length cap; bodies are banned by type pattern.
7. **Direct calls for transaction-critical commands.** OAuth provisioning, booking slot reservation, brief schedule creation are direct calls (per CLAUDE.md convention #6). After-commit reactions (send brief, notify) are events.

**Warning signs:**
- A Calendar/Drive/Booking class imports a Gmail/Triage repository.
- `@ApplicationModuleListener` in `backend/api` or `backend/worker`.
- An event has a `String body` field.
- Spring Modulith verification test red.
- Cross-process flow goes through Spring events instead of the outbox.

**Phase to own prevention:** **P1 (Calendar foundation)** and **P6 (Drive foundation)** declare module boundaries + events. Every subsequent phase verifies Modulith green at phase close.

---

## Moderate Pitfalls

### Pitfall 13: Frontend bundle size regression from calendar UI + booking page + Drive Picker

**What goes wrong:**
v1.3 just stabilized Turbopack + React 19 builds. Adding:
- Calendar UI components (date pickers, time slot pickers, week views).
- Booking page (public route, SSR'd for SEO-resistant rendering — actually we want noindex, so SSR is optional).
- Google Picker script loader.
- Possibly `@hello-pangea/dnd` or similar for booking slot drag.

Risks bundle bloat → slower TTI → regression vs v1.3.

**How to avoid:**

1. **shadcn first** (per CLAUDE.md rule #7). shadcn has Calendar primitive + DatePicker; reuse rather than installing react-big-calendar (heavy).
2. **Booking page in its own route group** `app/(public)/book/[slug]` — separate bundle from app shell. No app-shell imports.
3. **Google Picker via dynamic import** (`next/dynamic` with `ssr: false`) — loads only when user opens the destination-folder modal.
4. **Bundle-size CI gate.** `next build` + size-limit check; PR fails if `apps/web/.next/static/chunks/pages/_app*.js` grows >5% vs main.
5. **Reuse shadcn primitives** for time slot UI; do not install Radix UI primitives beyond what's in shadcn.

**Phase to own prevention:** Every UI phase (P2/P3/P4 frontend portions). Bundle gate runs on every PR.

---

### Pitfall 14: Forgetting an outbound gate when adding `propose_meeting` to the gateway

**What goes wrong:**
v1.2 Phase 08.1 introduced `OutboundSendGateway` with gates: global Auto-send + safety net + rate cap + idempotency + audit. `propose_meeting` is a new outbound action. Risks:

1. **`propose_meeting` routed through gateway but skips a gate** (e.g., rate cap forgotten).
2. **`propose_meeting` bypasses gateway entirely** because "it's not a send_reply, it's a reply with suggested times" — the engineer adds a new code path.
3. **Booking-page event creation routed through outbound gateway** when it shouldn't be (booking is host-initiated event creation, not an email send — the booking notification email IS an outbound send, but the Calendar event write isn't).
4. **`propose_meeting` invite-to-attendee defaults to "send invite"** in Calendar API — the Calendar `events.insert` has a `sendUpdates` query param. If unset, defaults to `none`; if set to `all`, sends an email invite. Forgetting this = no invite delivered; setting unconditionally = leaks the meeting to attendees without user confirmation in some flows.

**How to avoid:**

1. **`propose_meeting` is a `send_reply` variant in the gateway taxonomy.** Reuses every existing gate. The "suggested times" are a body template, not a new outbound type.
2. **ArchUnit `OutboundCallSiteAllowList` extended.** Existing test ensures all outbound paths route through gateway; add `BookingNotificationGateway` + reuse `OutboundSendGateway` for `propose_meeting`.
3. **Calendar event writes have a separate `BookingCalendarGateway`** (per Pitfall 4) — not the same as the email outbound gateway. ArchUnit confines `events.insert` to that gateway.
4. **`sendUpdates` is explicit at every call site.** Wrapper enforces: callers pass `SendUpdatesPolicy.{NONE, ALL, EXTERNAL_ONLY}`. No default. Test forces a code-review on each value.
5. **`propose_meeting` rule editor surfaces the `sendUpdates` choice** as a per-rule option ("Send Google Calendar invite emails: yes / no").

**Phase to own prevention:** **P5 (calendar-aware triage + propose_meeting)** owns gateway integration; ArchUnit extension lands with the first `propose_meeting` PR.

---

### Pitfall 15: Calendar/Drive OAuth refresh tokens stored in plaintext or in a different vault than Gmail

**What goes wrong:**
v1.0 ships AES-GCM app-layer encryption for Gmail refresh tokens. Adding Calendar + Drive:

1. **New `calendar_oauth_credential` / `drive_oauth_credential` tables** built by an engineer who forgot the AES-GCM pattern → plaintext refresh tokens.
2. **Reuse of `gmail_oauth_credential` schema** but stored in the wrong column (the engineer thinks "Gmail has cipher columns, I'll reuse" but the FK is to `gmail_connection_id` not `calendar_connection_id`).
3. **One token to rule them all.** Calendar/Drive scopes piggyback on the Gmail OAuth grant — same refresh token used for all three APIs. Saves storage but couples revocation: revoking Drive revokes Gmail. UX trade-off.
4. **Token rotation event handler missing.** `OAUTH_TOKEN_ROTATED` event evicts only Gmail caches; Calendar/Drive clients keep stale token until cache expiry.
5. **Refresh token stored in Redis "for fast access."** Redis is not the durable store; Redis dump exposed = plaintext token leak.
6. **`@Sensitive` not applied to new token types.** Refresh tokens log via `String.format` → token in app logs.

**How to avoid:**

1. **Decision: single OAuth credential per Google account, multiple scope sets.** Same refresh token can be used for all Gmail/Calendar/Drive APIs because they're all in the same OAuth grant if bundled at consent. Storage: `google_account_oauth_credential (google_account_subject, encrypted_refresh_token, scopes_granted_csv, ...)`. One row per Google account; `gmail_connection`, `calendar_connection`, `drive_connection` reference the credential row.
2. **OR per-API credential rows for explicit revocation independence.** Trade-off: more storage, simpler revoke-Drive-only flow. Pick one in P1 design phase; document choice.
3. **AES-GCM at the SAME app-layer code path** as Gmail. Reuse `OAuthCredentialEncryptor`. ArchUnit: any new credential column matching `refresh_token` / `access_token` shape must be `BYTEA` (cipher) — not `VARCHAR` / `TEXT`.
4. **`@Sensitive` typed everywhere.** Compiler-enforced.
5. **Sentinel-leak test extended** to calendar + drive token columns. Same pattern as v1.2 master-key sentinel.
6. **No Redis-stored tokens.** Redis caches `ChatModel` / Gmail client by tenant; refresh token always read from DB on cache miss, decrypted in-memory, never re-cached.
7. **Token rotation event covers all three APIs.** `GOOGLE_OAUTH_TOKEN_ROTATED { googleAccountSubject }` event evicts Gmail + Calendar + Drive client caches for that subject.

**Phase to own prevention:** **P1 (Calendar OAuth foundation)** decides + implements the credential storage shape; ArchUnit + sentinel-leak test land in P1. **P6 (Drive OAuth)** reuses the pattern.

---

## Phase-Specific Warning Cross-Reference

| Phase (research-suggested) | Likely Pitfalls | Mitigation summary |
|----------------------------|-----------------|--------------------|
| **P0 — Spring AI 2.0 GA migration** | #8 (Spring AI churn) | Bump bom; migration audit; rebuild `ToolCallingManager` factory; smoke-test streaming + tools on real OpenRouter |
| **P1 — Calendar OAuth + connection foundation** | #1 (CASA), #9 (multi-mailbox×calendar), #10 (enable/disable state machine), #12 (Modulith boundaries), #15 (OAuth token storage) | Scope ledger + ArchUnit allow-list; `mailbox_calendar_preference` + `CalendarContext`; `calendar_connection` state machine; module boundaries declared; AES-GCM token storage |
| **P2 — Free/busy + draft availability** | #2 (quota) | `CalendarApiClient` wrapper, two-tier cache, singleflight, `quotaUser`, rate cap, degradation contract |
| **P3 — Booking pages** | #4 (bot/abuse), #11 (schema), #14 (outbound gateway) | CAPTCHA, DB-locked slot uniqueness, `BookingCalendarGateway` + ArchUnit, idempotency, audit, slug semantics |
| **P4 — AI meeting briefs** | #5 (ARCH-02 brief envelope), #7 (agentic loop runaway), #8 (Spring AI) | In-memory rendering at delivery time; `BriefOrchestrator` + sanitizer; per-brief caps; BYOK preview; PROJECT.md carve-out addendum |
| **P5 — Calendar-aware triage + propose_meeting** | #12 (Modulith), #14 (outbound gateway) | Events for invite-observed; `propose_meeting` reuses gateway; explicit `sendUpdates` |
| **P6 — Drive OAuth + Picker** | #1 (CASA), #3 (drive.file UX), #15 (token storage) | Picker-only UX; ADR; `drive.file` semantic CI test; reuse OAuth credential pattern |
| **P7 — AI document auto-filing** | #6 (streaming OOM), #3 (no folder browse), #11 (schema) | Streaming pipe + bounded executor + heap budget; AI history from `drive_filed_document` (no live scan); JSONB index discipline |
| **P8 — Attachment source rules** | #3 (Picker-once UX), #14 (gateway) | UX: pick files once at rule creation; trade-off documented in SPEC; gateway routing for outbound |
| **Every phase** | #11 (Liquibase discipline) | Per-phase schema review; one-change-per-changeset; immutability hook; rollback blocks; JSONB indexes |
| **Every phase** | #13 (bundle size) | shadcn-first; route-group isolation; dynamic Picker import; CI bundle-size gate |

---

## Sources

- [Google: Sensitive scope verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification) — HIGH confidence — sensitive vs restricted scope classification mechanics
- [Google: Restricted scope verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification) — HIGH — CASA Tier 2/3 requirement triggers
- [Google CASA 2025: Tiers, Costs & Compliance Explained (deepstrike.io)](https://deepstrike.io/blog/google-casa-security-assessment-2025) — MEDIUM-HIGH — non-canonical but corroborates tier timelines
- [Google Calendar API usage limits](https://developers.google.com/workspace/calendar/api/guides/quota) — HIGH — per-user, per-project quota mechanics, 403/429 patterns, `quotaUser` semantics
- [Google Calendar API freebusy.query reference](https://developers.google.com/workspace/calendar/api/v3/reference/freebusy/query) — HIGH — `calendarExpansionMax=50` cap, request shape
- [Google Calendar API push notifications](https://developers.google.com/workspace/calendar/api/guides/push) — HIGH — channel mechanics, HTTPS + cert requirements
- [Google Drive: Select files and folders with Google Picker](https://developers.google.com/workspace/add-ons/studio/drive-picker) — HIGH — definitive source for `drive.file` folder-pick = write-only-into-folder semantics
- [Google Drive: Choose Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) — HIGH — `drive.file` non-sensitive classification
- [Spring AI 2.0.0-RC1 Available Now](https://spring.io/blog/2026/06/06/spring-ai-2-0-0-RC1-available-now/) — HIGH — "built-in call/stream tool-execution loop dropped from every ChatModel"
- [Spring AI Upgrade Notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) — HIGH — comprehensive migration matrix
- [Spring AI 1.0.7, 1.1.6, 2.0.0-M6 release notes](https://spring.io/blog/2026/05/08/spring-ai-1-0-7-1-1-6-2-0-0-M6-available-now/) — HIGH — M6 baseline (`PromptChatMemoryAdvisor` deprecation, explicit conversation ID required)
- [Calendly community: CAPTCHA on booking page](https://community.calendly.com/how-do-i-40/adding-a-captcha-to-my-booking-page-2295) — MEDIUM — confirms Calendly's "2-3 bookings/hour per IP triggers CAPTCHA" pattern is insufficient
- [Calendly community: SPAM Booking Prevention](https://community.calendly.com/how-do-i-40/spam-booking-prevention-ideas-2711) — MEDIUM — confirms ongoing bot abuse pressure on public booking endpoints
- `CLAUDE.md` — HIGH — ARCH-02 scope (email-content pipeline vs chat draft body carve-out), OutboundSendGateway, libs/version locks, `feedback_*` memory entries
- `.planning/PROJECT.md` — HIGH — v1.4 scope, v1.3 baseline (multi-mailbox `MailboxContext`, ArchUnit `findByTenantId` ban), explicit deferrals
- v1.2 `.planning/research/PITFALLS.md` — HIGH — pattern reuse for `AdminContext`/scoped-value isolation, sentinel-leak test design, append-only audit, rate-limit primitives

---

*Pitfalls research for: Zero Mail v1.4 Calendar Co-Pilot + Drive Filing, added on top of v1.0–v1.3 trust-first baseline*
*Researched: 2026-06-17*
