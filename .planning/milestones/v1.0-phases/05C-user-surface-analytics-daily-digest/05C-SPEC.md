# Phase 5C: User Surface — Analytics & Daily Digest — Specification

**Created:** 2026-05-13
**Ambiguity score:** 0.16 (gate: ≤ 0.20)
**Requirements:** 6 locked

## Goal

`apps/web` gains an authenticated **analytics screen** (preset windows 7d / 30d / 90d) that surfaces volume triaged, estimated time saved, top-3 senders by triaged-message count, and per-rule hit counts with applied-vs-reverted breakdown — derived strictly from `triage_audit` and `mail_message_observed` metadata, with **no email bodies, prompts, completions, or embeddings read or stored**; and every connected tenant whose digest is enabled receives a **daily digest email** (default ON, opt-out at `/settings`) at their **configured local-time hour (default 20:00)**, delivered through a **channel-agnostic notification layer** whose **email** adapter is the v1 implementation (Zalo / Telegram / in-app adapters are explicitly future, not v1).

## Background

The backend audit-write side already produces everything analytics needs:

- `triage_audit` (Liquibase changeset 04-02) — per-message metadata: `tenant_id`, `gmail_message_id`, `gmail_thread_id`, `rule_id`, `rule_name_snapshot`, `action_type` (`label` / `archive` / `save_draft`), `decision`, `decided_at`, `applied_at`, `reverted_at`, `attempt_count`. **Hard-coded by table design to metadata only — no bodies, no prompts, no completions.** This is the analytics source-of-truth.
- `AuditLogQueryService` exists for keyset-paginated row reads (consumed by 5B's `GET /api/triage/audit`). **No aggregation projections exist** — no count/group-by/window queries against `triage_audit` anywhere in `core.triage.projection`.
- `mail_message_observed` (Phase 2A) — `tenant_id`, `gmail_message_id`, `gmail_thread_id`, `sender_email` (sanitized `From`), `observed_at`. Source for top-senders volume (every observed message, not only those that hit a rule).
- `RuleEntity.rule_name_snapshot` is denormalized onto every audit row, so analytics survives rule rename/delete.

What does NOT exist:

- **No analytics aggregation backend** — no `AnalyticsController`, no projection service, no SQL over `triage_audit` / `mail_message_observed` for counts/group-by/top-N. Frontend `apps/web` has no `features/analytics/` and no `/analytics` route.
- **No outbound mail infrastructure at all.** Grep for `JavaMailSender` / `spring-boot-starter-mail` / `Resend` / `Postmark` / `SES` / `SendGrid` / `Mailgun` across the repo returns zero hits. No mailer abstraction, no template renderer, no provider config, no suppression list, no test fakes.
- **No notification preferences.** No `notification_preference` (or equivalent) table, no settings UI section, no tenant-timezone column on `tenants` (verified — `tenants` schema has no `time_zone` field).
- **No scheduled digest worker.** `backend/worker` has `@Scheduled` patterns (`TriagePendingReaperJob`, `BillingIntentExpirySweeper`, `DriftDetectionJob`) but nothing time-zone-aware or per-tenant-fanout.
- **REQ ANL-01 / ANL-02 / ANL-03 are all Pending.** Roadmap row reads `5C. ... 0/TBD | Not started`.

Phase 5A delivered the protected app shell + nav + Phase 1.6 design tokens + shared loading/empty/error primitives + i18n vi/en — 5C consumes those without modifying them. Phase 5B closed the 5A audit-list gap (`GET /api/triage/audit`); 5C reuses the same `triage_audit` source but for aggregation, not row reads.

## Requirements

1. **Analytics screen (web)**: An authenticated analytics page renders volume / time saved / top senders / rule hits over a preset window.
   - Current: no `features/analytics/` folder, no `/analytics` route, no aggregation endpoint, no UI; `BillingController` / `RulesController` / `TriageAuditController` have no analytics counterpart.
   - Target: An authenticated route (e.g. `/analytics`) inside the existing `(protected)/(app)` shell with preset window chips **7d / 30d / 90d** (default 7d), showing four panels for the selected window: (a) **volume triaged** — total observed messages and total applied actions; (b) **estimated time saved** — sum of per-action-type weights × applied (non-reverted) actions, formula stated below; (c) **top-3 senders** — by triaged-message count (count of `mail_message_observed` rows with that sender in the window); (d) **rule hits** — flat per-rule table over the window with columns `rule_name_snapshot`, total decisions, applied count, reverted count; vi + en localized; on Phase 1.6 design tokens; shared loading / empty / error primitives.
   - Acceptance: Visiting `/analytics` while authenticated renders all four panels for the default 7d window without horizontal scroll at 320px; switching to 30d / 90d re-queries and re-renders; with **zero audit + zero observed** rows the page renders explicit empty states (no NaN, no spinner stuck); with **seeded** data the four panels show the expected numbers (Playwright + seeded DB).

2. **Analytics aggregation backend**: A REST endpoint serves the four-panel data from metadata only.
   - Current: no aggregation query, no controller. Frontend has nothing to call.
   - Target: A backend endpoint (e.g. `GET /api/analytics/summary?window=7d|30d|90d`) returns a single typed response containing volume, time saved, top-3 senders, and per-rule hits for the requested window, scoped to the authenticated tenant. Queries read **only** from `triage_audit` and `mail_message_observed`; **no** query reads any column storing message bodies, headers beyond what's already in those metadata tables, prompts, completions, or embeddings (the latter three columns do not exist by project invariant). Estimated time saved uses **per-action-type constants**: `label` = 10s, `archive` = 30s, `save_draft` = 180s, summed only over rows with non-null `applied_at` and null `reverted_at`. The constants are defined as Java constants (not user-configurable in v1).
   - Acceptance: Code/grep inspection confirms aggregation SQL touches only `triage_audit` and `mail_message_observed`; a contract test asserts the response shape (volume / time saved / top-3 senders / rule hits); given a seeded fixture with N applied label + M applied archive + K applied save_draft, the response's time-saved equals `N*10 + M*30 + K*180` seconds; reverted rows are excluded; a log assertion confirms no `sender_email` / `gmail_message_id` / `gmail_thread_id` leaves the JSON beyond what the spec explicitly returns (sender email is owner-visible by design as part of top-senders).

3. **Daily digest email (v1 channel)**: Each enabled tenant receives one email per day summarizing the prior day.
   - Current: no mailer infra, no template, no provider config, no scheduler.
   - Target: A daily scheduled worker job fans out per-enabled-tenant digest deliveries through the channel-agnostic notification layer (req 5). For each tenant whose digest preference is enabled, the job composes the digest for the **prior 24h closed at the tenant's local-time send hour** containing: total volume triaged (observed messages + applied actions), estimated time saved (same formula as req 2), top-3 senders by triaged-message count, top-3 rules by hit count with applied/reverted, and a CTA link back to `/analytics`. The email is HTML with a plaintext fallback, localized vi or en according to the tenant's stored `preferred_language`, and includes a footer link to `/settings` for opt-out. Email-out goes through a **transactional email provider**; the specific vendor (Resend / Postmark / SES / Mailgun / etc.) is a discuss-phase decision and is selected behind a provider-agnostic interface so it can be swapped without touching digest code.
   - Acceptance: On a seeded tenant with known prior-day audit + observed rows, running the digest job once produces exactly one outbound message through the notification adapter with subject + body containing the expected totals, top-3 sender display strings, top-3 rule names, and a CTA URL; running the job twice for the same `(tenant, digest-day)` produces **at most one** outbound message (idempotency); for a zero-activity day the digest **is still sent** with the explicit "no activity yesterday" wording (decision: send-anyway, deferred-deselect-or-suppress to backlog), unless the tenant has opt-out enabled in which case **no** message is produced; vi + en bodies pass `i18n:check`.

4. **Digest preferences (default-on + per-tenant hour)**: Each tenant controls digest opt-out and send hour.
   - Current: no `notification_preference` (or equivalent) table; no settings UI section; `tenants` table has no `time_zone` column.
   - Target: Persistent per-tenant digest preference with at minimum: `digest_enabled` (boolean, default `true`), `digest_send_hour_local` (integer 0–23, default `20`), `time_zone` (IANA string, default `Asia/Ho_Chi_Minh` for v1 Vietnam beta). A `/settings` "Notifications" section exposes the opt-out toggle and the send-hour selector; vi + en localized; shared form / error primitives. The fan-out scheduler reads these preferences and only enqueues a digest for tenants where `digest_enabled = true`, computing the run-window in the tenant's `time_zone`.
   - Acceptance: A migration adds the preference table/columns with the documented defaults; for a tenant with `digest_enabled = false`, the digest job produces no outbound message regardless of activity; for a tenant with `digest_send_hour_local = 8` and `time_zone = Asia/Ho_Chi_Minh`, the job's "prior 24h" window is `[yesterday 08:00 ICT, today 08:00 ICT)` (verifiable by unit test on the window-resolver); the settings UI persists changes and reflects the new state without a full page reload (Playwright).

5. **Channel-agnostic notification layer**: Digest dispatch decouples *content* from *channel*.
   - Current: no notification abstraction; no mailer; no Zalo / Telegram / in-app integration of any kind.
   - Target: A small notification module separates (a) a `DigestPayload` (or equivalently named) record carrying the structured digest content (totals, top senders, top rules, time saved, link, locale) from (b) a `NotificationChannel` interface with a single v1 implementation `EmailNotificationChannel` backed by the chosen transactional provider. The scheduler builds the payload once per tenant and hands it to the channel resolver; today the resolver returns `EmailNotificationChannel`; adding a Zalo / Telegram / in-app adapter later is a new `NotificationChannel` implementation, not a digest-code change. **No** Zalo / Telegram / in-app adapter is implemented in this phase.
   - Acceptance: Code inspection confirms `DigestPayload` has no email-only fields (no `mimeType`, no `htmlBody` — those live inside the email adapter); an ArchUnit (or equivalent) test asserts digest composition code does not import the email-provider SDK directly; replacing the email channel with a `NoopNotificationChannel` in a test produces a payload that is constructed but not dispatched, and the digest job still completes without error.

6. **Privacy & logging invariants (5C-specific reaffirmation)**: 5C reads + writes nothing that breaches the project privacy invariants.
   - Current: project-wide privacy logging format (`event=<name> tenantId={}` + structured fields, no bodies/prompts/completions/token-bytes) is enforced in existing modules; 5C must not regress it.
   - Target: Every 5C log line uses the structured privacy format; no `From` address, no message body, no draft body, no rule body / pattern, no prompts, no completions appear in logs (sender email may appear in **outbound digest email body** because it is owner-visible by design, but never in **server logs**). Aggregation SQL is forbidden from joining tables that store bodies / prompts / completions (those tables do not exist; the prohibition is an invariant, not a runtime check).
   - Acceptance: A log-scrub test (mirroring existing `Triage*PrivacySweepTest` patterns) over the analytics endpoint and digest job confirms no sender email, no message body, no prompts, no completions in stdout / stderr / logfile during a seeded run; an ArchUnit / boundary test asserts analytics code does not import or reference any embedding / vector store / message-body table.

## Boundaries

**In scope:**

- New `GET /api/analytics/summary?window=7d|30d|90d` backend endpoint with metadata-only aggregation over `triage_audit` + `mail_message_observed`.
- New authenticated `/analytics` route in `apps/web`, inside the existing `(protected)/(app)` shell, with four panels (volume / time saved / top-3 senders / per-rule hits) on Phase 1.6 design tokens, vi + en, responsive to 320px.
- New transactional email provider integration behind a provider-agnostic interface (vendor choice = discuss-phase decision).
- New channel-agnostic notification layer (`DigestPayload` + `NotificationChannel`); v1 ships only `EmailNotificationChannel`.
- New digest scheduler: per-tenant fan-out at the tenant's local send hour, idempotent per `(tenant, digest-day)`.
- New per-tenant digest preferences table/columns (`digest_enabled`, `digest_send_hour_local`, `time_zone`) with documented defaults.
- New `/settings` "Notifications" subsection: opt-out toggle + send-hour selector, vi + en.
- New digest email HTML + plaintext templates, vi + en, with `/analytics` CTA and `/settings` opt-out footer link.
- 5C-scope privacy + logging tests (analytics endpoint + digest job).

**Out of scope:**

- **CSV / JSON export of analytics data** — deferred to backlog; user reads on screen.
- **Per-rule time-series histogram (mini chart per rule)** — round 2 locked flat per-rule count + applied/reverted; histogram deferred.
- **Bounce / complaint / unsubscribe-list management at app layer** — transactional provider's built-in suppression handles deliverability; project does not maintain a suppression table in v1.
- **Custom digest cadence (weekly / monthly / pause-N-days)** — v1 = daily only; cadence customization deferred to backlog.
- **Zalo / Telegram / in-app digest adapters** — only `EmailNotificationChannel` ships in v1; the abstraction is built so they can be added without touching digest code, but no adapter is implemented in 5C.
- **Marketing emails / product-update emails / re-engagement emails** — out; this phase only owns the daily activity digest.
- **Cross-tenant / admin analytics** — v1 analytics is tenant-scoped only; no global / admin / cohort view.
- **Drill-down from analytics into individual audit rows** — Phase 5A's `/triage` audit log already serves row-level inspection; analytics is aggregate-only.
- **Real-time / SSE / websocket analytics updates** — preset-window refetch on chip change only; no live updating.
- **Anomaly detection / alerts (e.g. "your inbox volume spiked")** — out.
- **Embedded charts / images inside the digest email** — v1 is text-only HTML + plaintext (top-N as lists, totals as inline numbers); charts deferred.
- **Backfill of pre-5C historical audit data** — 5C analytics reads existing `triage_audit` and `mail_message_observed` rows as-is; no synthetic backfill of pre-Phase-4 data.

## Constraints

- **Privacy invariant (hard)**: 5C aggregation SQL reads only `triage_audit` + `mail_message_observed` columns. No body / prompt / completion / embedding column may be referenced — those columns do not exist by project policy and aggregation must remain inside that envelope. Sender email may appear inside the **outbound digest body** (owner-visible by design) and inside the **analytics-screen top-senders panel** but **never in server logs**.
- **Privacy logging format (project-wide)**: every 5C log line uses `event=<name> tenantId={}` + structured fields; no bodies, prompts, completions, addresses, or token bytes.
- **No long-term storage of email content / prompts / completions / embeddings** — 5C does not introduce any such storage. Per-tenant digest preferences store only `digest_enabled` / `digest_send_hour_local` / `time_zone`, plus an idempotency key per `(tenant, digest-day)` if needed.
- **Transactional email provider behind an interface**: provider-specific imports stay inside a single email-adapter package; digest-composition code is provider-free (ArchUnit-style boundary). Vendor (Resend / Postmark / SES / Mailgun / etc.) is a discuss-phase decision.
- **Channel-agnostic schedule model**: `DigestPayload` carries content + locale only; channel selection is resolver-driven. Email is the v1 adapter; Zalo / Telegram / in-app adapters are explicitly NOT built in 5C.
- **Tenant-local digest scheduling**: digest send time is per-tenant in the tenant's IANA `time_zone`; default `Asia/Ho_Chi_Minh` (Vietnam beta), default hour `20`. Fan-out scheduler must respect DST behavior of the IANA TZ via `java.time.ZonedDateTime`.
- **Idempotency**: the digest job must produce **at most one** delivery per `(tenant, digest-day)` — re-runs after a crash / restart / retry do not double-send. Mechanism is a discuss-phase decision (DB unique key on `(tenant_id, digest_day_local)` is one likely option).
- **Phase 5A consumption rules carry forward**: typed OpenAPI client only (no ad-hoc `fetch`); shared loading / empty / error primitives; Phase 1.6 design tokens; shadcn/ui primitives first (rule of three before wrapping); `pnpm i18n:check` passes; visual-design pass via `frontend-design` skill; standard gates (`tsc`, ESLint, Vitest, `i18n:check`, Playwright) green.
- **Vietnamese-first i18n**: vi + en lock-step parity for all new strings (analytics labels + digest body + settings copy + opt-out footer).
- **Backend code style**: enterprise-readability naming (`request` not `req`, `response` not `res`, etc.); records for DTOs, classes for entities, Lombok-free; Java 25 / Spring Boot 4 / Spring AI 2.0.0-M6.
- **Send window definition**: "prior 24h" is `[hour_local − 24h, hour_local)` in the tenant's `time_zone`, anchored at the digest send moment; zero-activity windows still send the digest (with explicit empty wording) unless the tenant has opted out.

## Acceptance Criteria

- [ ] `/analytics` route exists inside `(protected)/(app)` shell; renders volume, estimated time saved, top-3 senders, and per-rule hits (applied + reverted) for preset windows 7d / 30d / 90d (default 7d); vi + en; responsive to 320px without horizontal scroll.
- [ ] `GET /api/analytics/summary?window=7d|30d|90d` returns the typed four-panel payload, tenant-scoped, reading only from `triage_audit` + `mail_message_observed` (verified by SQL grep + contract test).
- [ ] Estimated time saved equals `appliedLabelCount*10 + appliedArchiveCount*30 + appliedSaveDraftCount*180` seconds (reverted rows excluded); verified by a fixture-driven test.
- [ ] Empty analytics state (no audit + no observed rows in window) renders explicit empty UI — no NaN, no infinite spinner.
- [ ] A daily scheduled worker job fans out one digest per enabled tenant at that tenant's `digest_send_hour_local` in their `time_zone`.
- [ ] Digest email contains: prior-day totals, estimated time saved, top-3 senders, top-3 rules with applied/reverted, `/analytics` CTA link, `/settings` opt-out footer; HTML + plaintext; vi or en per tenant's `preferred_language`.
- [ ] Re-running the digest job for the same `(tenant, digest-day)` produces at most one outbound message (idempotency test).
- [ ] A tenant with `digest_enabled = false` receives no digest regardless of activity (test).
- [ ] A zero-activity tenant with `digest_enabled = true` receives the digest with explicit "no activity yesterday" wording (test).
- [ ] `/settings` exposes a "Notifications" section with an opt-out toggle and a send-hour selector; persists via backend; vi + en; reflects new state without full page reload.
- [ ] Liquibase migration adds the digest-preference table/columns with defaults `digest_enabled = true`, `digest_send_hour_local = 20`, `time_zone = 'Asia/Ho_Chi_Minh'`.
- [ ] `DigestPayload` is channel-free (no email-specific fields); `NotificationChannel` interface exists with one v1 implementation `EmailNotificationChannel`; digest-composition code has no transactional-provider SDK import (ArchUnit-style boundary test).
- [ ] Privacy-sweep test confirms no sender email, no message body, no prompts, no completions in 5C logs during a seeded analytics-query + digest-job run.
- [ ] Standard frontend gates (`tsc`, ESLint, Vitest, `i18n:check`) and a Playwright e2e for `/analytics` + `/settings → Notifications` are green.

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                                 |
|--------------------|-------|------|--------|-----------------------------------------------------------------------|
| Goal Clarity       | 0.90  | 0.75 | ✓      | Window UX, time-saved formula, digest content + cadence + timing locked |
| Boundary Clarity   | 0.86  | 0.70 | ✓      | Explicit out-of-scope: CSV, per-rule histogram, suppression, custom cadence, non-email channels |
| Constraint Clarity | 0.78  | 0.65 | ✓      | Metadata-only invariant, channel-agnostic abstraction, per-tenant TZ, transactional provider behind interface |
| Acceptance Criteria| 0.75  | 0.70 | ✓      | 13 pass/fail checks; time-saved formula is fixture-driven; idempotency + opt-out + zero-activity covered |
| **Ambiguity**      | 0.16  | ≤0.20| ✓      |                                                                       |

Status: ✓ = met minimum, ⚠ = below minimum (planner treats as assumption).

**Deferred to discuss-phase (open "how" decisions, not gaps):**

- Specific transactional email provider (Resend / Postmark / SES / Mailgun / other).
- Exact aggregation SQL shape (single endpoint with one round trip vs separate panel queries; window pre-aggregation tables vs raw rollups).
- Idempotency mechanism for `(tenant, digest-day)` (DB unique key on a digest-delivery log vs Spring Modulith event-publication completion vs Redis).
- Send-hour scheduler architecture (single hourly cron that fans out tenants whose local hour matches "now" vs per-tenant `@SchedulerLock` jobs).
- Whether `digest_send_hour_local` and `time_zone` live on `tenants` directly or on a new `notification_preference` table (and whether the `notification_preference` table is generalized for future per-channel preferences).
- Time-saved-formula constants (`label = 10s`, `archive = 30s`, `save_draft = 180s`) are locked for v1 but may be tuned during discuss-phase if a defensible source is cited.
- Top-3 tie-breaking rule (ties on count → alphabetical? by `decided_at`?).
- HTML email template engine (Thymeleaf / Pebble / Mustache / raw string templates) — discuss-phase choice.

## Interview Log

| Round | Perspective     | Question summary                                  | Decision locked                                                                 |
|-------|-----------------|---------------------------------------------------|---------------------------------------------------------------------------------|
| 1     | Researcher      | Window UX (preset / custom / slider)?             | Preset chips 7d / 30d / 90d (default 7d); custom range deferred                  |
| 1     | Researcher      | "Estimated time saved" formula?                   | Per-action-type weights × applied (non-reverted): label 10s, archive 30s, save_draft 180s; constants are Java-side, not user-configurable in v1 |
| 1     | Researcher      | Daily digest delivery medium?                     | Transactional email provider behind a **channel-agnostic** notification layer; email is v1; Zalo / Telegram / in-app are future adapters, not v1 |
| 2     | Researcher      | "Top senders" definition?                         | Top-3 by **triaged-message count** (rows in `mail_message_observed` with that `sender_email` over the window) |
| 2     | Simplifier      | "Rule hits" display shape?                        | Flat per-rule list: total decisions + applied count + reverted count; histogram deferred |
| 2     | Boundary Keeper | Digest default + opt-out behavior?                | Default ON for every connected tenant; `/settings` toggle to opt out             |
| 3     | Boundary Keeper | Digest email body content?                        | Totals + top-3 senders + top-3 rules + estimated time saved + `/analytics` CTA + `/settings` opt-out footer; HTML + plaintext |
| 3     | Boundary Keeper | Send-time policy?                                 | Default 20:00 **tenant-local**; user-configurable send hour in `/settings → Notifications`; channel-agnostic schedule model |
| 3     | Boundary Keeper | Confirmed explicitly OUT?                         | CSV export, per-rule time-series histogram, bounce/complaint suppression list, custom (weekly/monthly) digest cadence |

---

*Phase: 05C-user-surface-analytics-daily-digest*
*Spec created: 2026-05-13*
*Next step: /gsd-discuss-phase 5C — implementation decisions (transactional provider choice, aggregation SQL shape, scheduler architecture, preference-table topology, email template engine, idempotency mechanism)*
