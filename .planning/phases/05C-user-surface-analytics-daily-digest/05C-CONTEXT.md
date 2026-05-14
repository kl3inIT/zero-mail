# Phase 5C: User Surface — Analytics & Daily Digest - Context

**Gathered:** 2026-05-13
**Status:** Ready for planning

<domain>
## Phase Boundary

`apps/web` gains an authenticated **analytics screen** at `/analytics` (preset windows 7d / 30d / 90d) surfacing volume triaged, estimated time saved, top-3 senders by triaged-message count, and per-rule hits with applied/reverted breakdown — derived strictly from `triage_audit` + `mail_message_observed` metadata (no bodies, prompts, completions, embeddings). Every connected tenant whose digest is enabled receives a **daily digest email** at their configured local-time hour (default 20:00 `Asia/Ho_Chi_Minh`), delivered through a channel-agnostic notification layer whose email adapter is the v1 implementation (Zalo / Telegram / in-app adapters are explicit v2). Backend gains `GET /api/analytics/summary?window=...` aggregation endpoint, a new `notification_preference` table + `tenants.time_zone` column, a `digest_delivery` idempotency table, an hourly digest scheduler in `backend/worker`, the Resend email adapter, Thymeleaf HTML+TXT templates (vi+en), and a `/settings` Notifications subsection for the opt-out toggle + send-hour selector.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**6 requirements are locked.** See `05C-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `05C-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- `GET /api/analytics/summary?window=7d|30d|90d` metadata-only aggregation over `triage_audit` + `mail_message_observed`.
- Authenticated `/analytics` route in `apps/web` inside the existing `(protected)/(app)` shell, 4 panels (volume / time saved / top-3 senders / per-rule hits) on Phase 1.6 design tokens, vi+en, responsive to 320px.
- Transactional email provider integration behind a provider-agnostic interface.
- Channel-agnostic notification layer (`DigestPayload` + `NotificationChannel`); v1 ships only `EmailNotificationChannel`.
- Digest scheduler: per-tenant fanout at tenant's local send hour, idempotent per `(tenant, digest-day)`.
- Per-tenant digest preferences (`digest_enabled`, `digest_send_hour_local`, `time_zone`) with documented defaults.
- `/settings` Notifications subsection: opt-out toggle + send-hour selector, vi+en.
- Digest email HTML + plaintext templates (vi+en) with `/analytics` CTA + `/settings` opt-out footer.
- 5C-scope privacy + logging tests (analytics endpoint + digest job).

**Out of scope (from SPEC.md):**
- CSV / JSON export of analytics data (deferred to backlog).
- Per-rule time-series histogram (flat per-rule count + applied/reverted only; histogram deferred).
- Bounce / complaint / unsubscribe-list management at app layer (transactional provider's built-in suppression handles deliverability).
- Custom digest cadence (weekly/monthly/pause-N-days) — v1 daily only.
- Zalo / Telegram / in-app adapters — only `EmailNotificationChannel` ships; abstraction supports future adapters without digest-code change.
- Marketing / product-update / re-engagement emails.
- Cross-tenant / admin analytics — v1 tenant-scoped only.
- Drill-down from analytics into individual audit rows (5A's `/triage` audit log serves row-level inspection).
- Real-time / SSE / websocket analytics updates.
- Anomaly detection / alerts.
- Embedded charts/images inside digest email (v1 text-only HTML + plaintext).
- Backfill of pre-5C historical audit data.

</spec_lock>

<decisions>
## Implementation Decisions

### Transactional email vendor + template engine
- **D-01:** Email vendor v1 = **Resend** via the official Java SDK `com.resend:resend-java`. Rationale: only vendor here with a maintained first-party Java client, 3K/mo permanent free tier covers Vietnam-beta volume (<5K tenants × 1 digest/day), inbox-zero reference repo already validates the API surface. US/EU-hosted POPs are acceptable because digests are non-real-time (20:00 local) and the privacy invariant (no body — only counts + sender_email + rule names) makes Vietnam ISP routing nuance low-stakes. The locked provider-agnostic adapter boundary keeps a vendor swap (e.g., to AWS SES Singapore) a single-package change.
- **D-02:** Template engine = **Thymeleaf 3.1.x** via `spring-boot-starter-thymeleaf` (web auto-config excluded — email-only). Rationale: Spring's `MessageSource` already drives vi/en parity in the rest of `apps/web` + backend; `#{key}` fails loud on missing bundle entries (catches i18n drift in ArchUnit/test). Render perf gap vs Pebble/JTE is irrelevant at <5K sends/day on a worker with virtual threads. Plaintext fallback = sibling `*.txt` template iterating the same model (no content duplication).
- **D-03:** Provider isolation = Resend SDK imports stay inside ONE package `core.notification.channel.email.resend.*` (or `backend/worker/.../notification/...` — planner picks per the module rule); `DigestPayload` and digest-composition code stay provider-free. ArchUnit-style boundary test asserts the import boundary (mirrors the Spring AI adapter boundary from Phase 2C).
- **D-04:** Channel abstraction = `DigestPayload` record (locale, totals, top-3 senders, top-3 rules with applied/reverted, CTA URL, opt-out URL — no `mimeType`, no `htmlBody`, no email-specific fields) + `NotificationChannel` interface (`dispatch(DigestPayload)`); v1 has one implementation `EmailNotificationChannel`. A test `NoopNotificationChannel` proves payload-only composition succeeds without dispatch.

### Send-hour scheduler architecture
- **D-05:** Architecture = **hourly UTC cron + DB fanout** via `@Scheduled(cron="0 ? * * * *") @SchedulerLock(name="digestDispatchScheduler", ...)` in `backend/worker` (planner picks the exact minute offset to avoid colliding with the existing 5 schedulers; current pattern uses `fixedRate=3_600_000L` — switch to cron form for predictable hour-of-day alignment, or stay with fixedRate + explicit hour gate inside the method; planner chooses based on horizontal-scale alignment). Mirrors the proven pattern of `BillingIntentExpirySweeper`, `TriageEventRetryJob`, `TriageEventCleanupJob`, `TriagePendingReaperJob`, `TriageAuditPurgeJob` — same ShedLock 7.7.0 wiring, same Liquibase-managed `shedlock` table.
- **D-06:** Local-hour matching = **Postgres-side** computation using the scheduler's single captured reference instant: `WHERE digest_enabled = true AND EXTRACT(HOUR FROM (?::timestamptz AT TIME ZONE tenants.time_zone))::int = digest_send_hour_local`. The same `referenceInstant` is passed to Java for `digest_day_local` computation, so the SQL hour gate and JVM day calculation cannot drift near boundaries. App-layer digest window math still uses `ZoneId.of(tenant.timeZone)` and anchors the content window on the configured send-hour boundary (`HH:00`), not the cron execution instant (`HH:05`).
- **D-07 (LOCKED v1):** Worst-case lateness is ~59 minutes for a tenant whose `digest_send_hour_local` matches the *current* tick (worker queues a few minutes behind the cron). **If the worker is down through a tenant's exact send-hour tick, that tenant's digest for that day is SKIPPED with NO catch-up** — the next tick sees `EXTRACT(HOUR FROM (referenceInstant AT TIME ZONE t.time_zone))::int != digest_send_hour_local` for that tenant and does not claim a row. The "missed-hour recovery" claim in the previous D-07 wording was inaccurate under D-06's exact-hour match query (OpenCode H1 finding) and is now removed. A catch-up mode (extend the claim query to also accept tenants where `digest_day_local < today_local` and no SENT row exists for that day) is reserved for v2. Communicate this on the `/settings → Notifications` helper text so users with chronically restarted workers understand the trade-off.
- **D-08:** Evolution path to outbox-driven (scanner + `SKIP LOCKED` drainer) is reserved for Phase 6+ when (a) a second `NotificationChannel` ships with non-trivial retry/backoff, or (b) tenant count crosses ~5K. Not built in 5C.

### Idempotency mechanism
- **D-09:** Mechanism = **Postgres `digest_delivery` table with `UNIQUE(tenant_id, digest_day_local)` + status FSM** (`PENDING` → `SENT` / `FAILED`). The `ConstraintViolationException` IS the dedupe signal — distinguishable from other failures via SQLState `23505`. Single durable source for "did tenant X get their 2026-05-13 digest?" via one indexed lookup. Channel-agnostic (row exists before any channel side effect).
- **D-10:** Write order = (1) `INSERT INTO digest_delivery (tenant_id, digest_day_local, status='PENDING', attempt_count=1)` inside a short transaction; on `ConstraintViolation` → skip (already in flight or already sent). (2) Compose `DigestPayload`. (3) `NotificationChannel.dispatch(payload)`. (4) On success → `UPDATE digest_delivery SET status='SENT', dispatched_at=now(), channel='EMAIL'`. (5) On dispatch failure → `UPDATE digest_delivery SET status='FAILED', failure_reason=...`; a janitor/retry job may flip back to `PENDING` for re-attempt (planner decides whether retry lives inside the same `@Scheduled` tick or a separate job).
- **D-11:** Crash recovery = a row stuck in `PENDING` past a configured grace period (e.g. `PT30M`, mirroring `TriagePendingReaperJob`'s pattern) is a reaper signal — promote to `FAILED` so the next-tick `INSERT` is blocked. Crash exactly between (3) `dispatch` and (4) `UPDATE` is the only legitimate double-send window; mitigated because Resend SDK exposes an Idempotency-Key header — pass `tenant_id:digest_day_local` so a Resend retry deduplicates server-side too.
- **D-12:** Modulith composition (optional, planner's call) = the scheduler MAY publish a `DigestDueEvent(tenantId, digestDay)` after the `PENDING` INSERT succeeds, with an `@ApplicationModuleListener` doing the actual dispatch (after-commit semantics + free `event_publication` retry). The Modulith primitive dedupes per `(event, listener-id)` NOT per business key — so the UNIQUE constraint stays the primary dedupe; Modulith is dispatch transport only.
- **D-13:** Redis is NOT involved in digest idempotency. (Redis stays cache/rate-limit only per project invariant — Phase 5B's draft-lock SETNX is a different use case and not generalized here.)

### Preferences topology
- **D-14:** Topology = **hybrid** — `tenants.time_zone` (IANA string, default `Asia/Ho_Chi_Minh`, NOT NULL) added to the existing `tenants` table; PLUS new `notification_preference` table with composite PK `(tenant_id, channel)` and columns `digest_enabled boolean NOT NULL DEFAULT true`, `digest_send_hour_local int NOT NULL DEFAULT 20 CHECK (digest_send_hour_local BETWEEN 0 AND 23)`. `channel` is an `IdentifiedEnum` with v1 single id `'email'` (future ids `'zalo'`, `'telegram'`, `'in_app'` — not built). Rationale: `time_zone` is a tenant-global property (reusable by future quiet hours, scheduled rules, weekly reports), `enabled`+`send_hour_local` are genuinely per-channel. Adding a Zalo row in v2 is a single `INSERT`, not a schema migration.
- **D-15:** Liquibase = two changesets in this phase: (a) `ALTER tenants ADD COLUMN time_zone` with `defaultValueComputed: 'Asia/Ho_Chi_Minh'` + NOT NULL; (b) `CREATE TABLE notification_preference` with composite PK + cascade FK to `tenants(id)` + partial index `WHERE digest_enabled = true AND channel = 'email'` for the scheduler fanout query. A third changeset creates `digest_delivery` (D-09) with UNIQUE `(tenant_id, digest_day_local)` + cascade FK.
- **D-16:** Account-deletion cascade = `notification_preference` and `digest_delivery` both purge on tenant delete (Modulith account-deleted reaction in `core.account` or DB-level `ON DELETE CASCADE` — planner picks; existing convention in `core.tenant` mostly uses service-level cascade orchestration).
- **D-17:** Defaults wiring = OAuth provisioning (Phase 01.5 `OAuthProvisioningService.provisionBundledOAuth`) writes both `tenants.time_zone = 'Asia/Ho_Chi_Minh'` AND `notification_preference (tenant_id, channel='email', digest_enabled=true, digest_send_hour_local=20)` in the SAME `PROPAGATION_REQUIRED` transaction that creates user + tenant + gmail_connection. (Mirrors the Phase 01.5 HIGH-1 atomicity fix.)

### Aggregation SQL shape
- **D-18:** Shape = **4 sequential queries in 1 `@Transactional(readOnly=true)` JdbcTemplate service** (`AnalyticsSummaryQueryService` in `core.triage.projection` or new `core.analytics.projection` — planner picks; the latter is cleaner if non-trivial analytics code accumulates). Mirrors `AuditLogQueryService` shape exactly. The 4 queries:
  - Q1 (volume): `SELECT count(*) FROM mail_message_observed WHERE tenant_id=? AND observed_at >= ?` + `SELECT count(*) FROM triage_audit WHERE tenant_id=? AND applied_at >= ? AND reverted_at IS NULL`.
  - Q2 (time saved): `SELECT action_type, count(*) FROM triage_audit WHERE tenant_id=? AND applied_at >= ? AND reverted_at IS NULL GROUP BY action_type` → app layer multiplies by `{label:10, archive:30, save_draft:180}` constants.
  - Q3 (top-3 senders): `SELECT sender_email, count(*) AS c FROM mail_message_observed WHERE tenant_id=? AND observed_at >= ? GROUP BY sender_email ORDER BY c DESC, sender_email ASC LIMIT 3`.
  - Q4 (rule hits): `SELECT rule_name_snapshot, count(*) AS decisions, count(*) FILTER (WHERE applied_at IS NOT NULL AND reverted_at IS NULL) AS applied, count(*) FILTER (WHERE reverted_at IS NOT NULL) AS reverted FROM triage_audit WHERE tenant_id=? AND decided_at >= ? GROUP BY rule_name_snapshot ORDER BY decisions DESC, rule_name_snapshot ASC`.
- **D-19:** The digest job reuses Q1 + Q2 + Q3 + Q4 directly with a `Duration.ofHours(24)` window anchored at the tenant's local send moment — same code path, different window bounds. Top-3 lists in the digest body use the same tie-break (alphabetical asc on the secondary key) for determinism.
- **D-20:** Required indexes (Liquibase changeset adds any missing; verify existing first):
  - `triage_audit (tenant_id, decided_at)` — present per project context, verify covers `applied_at IS NOT NULL` filters (use `EXPLAIN ANALYZE` during planning).
  - `triage_audit (tenant_id, rule_name_snapshot, decided_at)` — NEW, for Q4 GROUP BY without filesort.
  - `mail_message_observed (tenant_id, observed_at)` — verify; needed for Q1 + window scans.
  - `mail_message_observed (tenant_id, sender_email, observed_at)` — NEW (or partial/covering variant), for Q3 GROUP BY.
  - `notification_preference` composite PK `(tenant_id, channel)` + partial index `WHERE digest_enabled = true AND channel = 'email'` — for D-06 scheduler fanout.
  - `digest_delivery` UNIQUE `(tenant_id, digest_day_local)` — primary idempotency.
- **D-21:** Frontend = TanStack Query hook `useAnalyticsSummary(window)` in `apps/web/features/analytics/`; one HTTP call per window change; 4 panels render from one typed response `{volume, timeSaved, topSenders, ruleHits}`. Empty states are first-class (no NaN, no infinite spinner per SPEC acceptance).

### Small locks
- **D-22:** Top-3 tie-break = alphabetical `sender_email ASC` (Q3) and `rule_name_snapshot ASC` (Q4) on the secondary key after the count DESC primary key. Deterministic, fixture-stable, independent of timestamp drift.
- **D-23:** Time-saved constants = SPEC lock `label = 10s, archive = 30s, save_draft = 180s`. Stored as Java constants in `core.analytics.domain.TimeSavedWeights` (or co-located with the query service). Telemetry-driven retune is a v2 concern.
- **D-24:** Endpoint shape = `GET /api/analytics/summary?window=7d|30d|90d` returning one typed `AnalyticsSummaryResponse` record `{ window, volumeObserved, volumeApplied, timeSavedSeconds, topSenders: [{senderEmail, count}], ruleHits: [{ruleName, decisions, applied, reverted}] }`. `from(...)` mapper on the response record per project convention. Default window = 7d when param is missing.
- **D-25:** Privacy logging — every 5C log line uses `event=<name> tenantId={}` + structured fields. No `sender_email` in server logs (sender email IS in outbound digest body and IS in the analytics-screen top-senders panel — both owner-visible by design). New `Analytics*PrivacySweepTest` + `Digest*PrivacySweepTest` mirror the existing `Triage*PrivacySweepTest` pattern.

### Claude's Discretion
- Exact package placement: `core.notification.channel.email.resend` vs `backend/worker/.../notification` (depending on whether the channel resolver lives in `core` or `worker`).
- Whether `core.analytics` becomes a new Modulith module with `allowedDependencies = {triage, gmail, shared.persistence}` or whether analytics queries live inside `core.triage.projection`.
- Exact Liquibase changeset numbering (sequence continues from `031`).
- Whether retry of `FAILED` digest deliveries is a separate `@Scheduled` job or folded into the next-tick hourly run.
- Exact i18n key namespace (`digest.*`, `analytics.*`, `settings.notifications.*`) — planner aligns with existing namespacing.
- `IdentifiedEnum` id strings for the `channel` enum (`'email'` is locked; future v2 ids are not 5C's concern).
- Whether `ChannelType` enum is one-channel-only in v1 (`{ EMAIL }`) or already shapes for v2 (`{ EMAIL, ZALO, TELEGRAM, IN_APP }` with future ids gated behind ArchUnit "no other channel implementation"); planner picks based on YAGNI vs over-engineering trade-off and the project's preference for IdentifiedEnum-with-future-ids.
- React Email-style component composition vs raw Thymeleaf — Thymeleaf is locked; planner decides on partial fragments (`th:fragment`) for header / footer / vi-en switcher.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### This phase
- `.planning/phases/05C-user-surface-analytics-daily-digest/05C-SPEC.md` — Locked requirements, boundaries, acceptance criteria. MUST read before planning.

### Project-level
- `CLAUDE.md` — language/runtime locks (Java 25, Spring Boot 4, Gradle 9 + Kotlin DSL), backend code style, conventions 1–9, "do not use" list (no Lombok, no WebFlux, no `javax.*`, no Kafka/RabbitMQ in v1, no Stateless JWT, no embedding store, no GCP starters), Tooling section (JetBrains MCP first, Postgres MCP Pro for DB, Playwright MCP for UI verification).
- `CONVENTIONS.md` — examples + anti-patterns for: thin controllers + service-owned `@Transactional`, backend domain package layout (`domain/`/`application/` or `usecases/`/`projection/`/`exception/`/`persistence/`; controllers grouped `controllers/<domain>/`, DTOs `dto/<domain>/`), records-for-DTOs, IdentifiedEnum + fromId fail-loud, privacy logging format, direct calls vs Modulith events, shadcn primitive selection.
- `.planning/PROJECT.md` — product, core value (trust/safety/reliability), single-VPS deployment, no auto-send, no body/prompt/completion storage; Key Decisions table.
- `.planning/REQUIREMENTS.md` — ANL-01 / ANL-02 / ANL-03 (analytics + digest), the "analytics" portion of WEB-02.
- `.planning/ROADMAP.md` §"Phase 5C: User Surface — Analytics & Daily Digest" — phase goal, depends-on (5A UI shell + 4 triage audit), success criteria.
- `.planning/research/STACK.md`, `.planning/research/ARCHITECTURE.md` — stack/arch background.

### Prior-phase context (depends-on)
- `.planning/phases/05A-user-surface-web-ui-core/05A-CONTEXT.md` + `05A-SPEC.md` + `05A-GAPS.md` — app shell (`SidebarProvider` + `SidebarInset` + persistent chrome), `features/triage` patterns, `useIsMobile` / responsive convention, shared loading/empty/error primitives, the `/settings` page structure where the Notifications subsection lands.
- `.planning/phases/05B-user-surface-ai-draft-replies/05B-CONTEXT.md` + `05B-SPEC.md` — `GET /api/triage/audit` cursor-pagination shape and JDBC read-side pattern (precedent for the analytics endpoint shape); `IdentifiedEnum` use; per-(tenant, thread) Redis lock for context (NOT for digest idempotency — see D-13).
- `.planning/phases/04-triage-convergence-hero/04-CONTEXT.md` — `triage_audit` table shape (decided_at / applied_at / reverted_at / rule_name_snapshot / action_type / attempt_count), `TriageAuditWriter`, the Spring Modulith event_publication retry pattern (`TriageEventRetryJob`), `TriagePendingReaperJob` (precedent for D-11 reaper).
- `.planning/phases/02A-mail-ingestion/` — `mail_message_observed` table shape, sanitized `sender_email`, observed_at semantics.
- `.planning/phases/02B-billing-prepaid-credits/` — billing top-up `code` UNIQUE precedent for D-09 (constraint-as-dedupe), `ZeroMailCoreProperties` config style (D-26 hint: digest config nested under it, e.g. `zero-mail.notification.digest.*`).
- `.planning/phases/01.5-inbox-zero-alignment-bundled-oauth-ux-polish-cleanup-sweep-r/` — `OAuthProvisioningService.provisionBundledOAuth` `PROPAGATION_REQUIRED` atomicity contract → D-17 hooks here.
- `.planning/phases/01.6-brand-identity-design-tokens-and-landing-page/` — design tokens for analytics screen (Teal accent + Paper-warm neutrals + Geist/Be Vietnam Pro/Instrument Serif), theme cookie pattern.

### Key source files to modify/extend
- `backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java` — add `time_zone` column (D-14).
- `backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java` — pattern reference for D-18 (NOT to be modified; mirror its shape in a new `AnalyticsSummaryQueryService`).
- `backend/core/src/main/java/com/zeromail/core/account/usecases/AccountService.java` + Phase 01.5 `OAuthProvisioningService` — wire defaults for D-17.
- `backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java` — existing ShedLock wiring (D-05 reuses without modification).
- `backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java` — pattern template for the new `DigestDispatchScheduler` (D-05).
- `backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java` — pattern template for the digest reaper (D-11).
- `backend/api/src/main/java/com/zeromail/api/controllers/` — NEW `controllers/analytics/AnalyticsController.java` per `controllers/<domain>/` convention.
- `backend/api/src/main/resources/application.yml` — Resend API key binding (planner picks property key under `zero-mail.notification.email.resend.*`).
- `backend/worker/src/main/resources/application.yml` — Resend API key + digest scheduler cron config.
- `apps/web/features/` — NEW `features/analytics/` (api + query-keys + hooks + components) + `features/notifications/` (settings subsection).
- `apps/web/app/(protected)/(app)/analytics/page.tsx` — NEW route.
- `apps/web/app/(protected)/(app)/settings/notifications/page.tsx` — NEW Settings subsection or section block on existing settings page.
- `backend/core/src/main/resources/db/changelog/changes/` — NEW changesets (sequence after `031`) for `time_zone` column, `notification_preference` table, `digest_delivery` table, supporting indexes.
- Email template directory (TBD by planner): `backend/worker/src/main/resources/email-templates/digest/{vi,en}/digest.{html,txt}.thymeleaf` or `backend/core/.../notification/templates/...` — planner picks based on which module owns `EmailNotificationChannel`.

### External library docs (use Context7 per global rule)
- **Resend Java SDK** — https://github.com/resend/resend-java , https://resend.com/docs/send-with-java (Idempotency-Key header per D-11; HTML + text plain dual body).
- **Thymeleaf 3.1.x** — https://www.thymeleaf.org/doc/articles/springmail.html (Spring email pattern, `MessageSource` integration, `#{key}` fail-loud).
- **Spring Boot 4 `@Scheduled` + `@SchedulerLock`** — verify `cron` + `zone` + virtual threads semantics via Context7 if Boot 4 introduces drift from Boot 3.
- **ShedLock 7.7.0** — https://github.com/lukas-krecan/ShedLock (`lockAtMostFor` crash-safety net, `usingDbTime()` for clock-coupling).
- **Spring Modulith 2.0.x `@ApplicationModuleListener`** — pinned local SNAPSHOT in this repo; D-12 dispatch transport semantics (at-least-once on listener side).
- **PostgreSQL 17 `AT TIME ZONE`** — https://www.postgresql.org/docs/17/datatype-datetime.html#DATATYPE-TIMEZONES (DST handling, IANA tzdata).
- **shadcn/ui** — `tabs` (window chips 7d/30d/90d), `card` (4 analytics panels), `switch` (digest opt-out toggle), `select` (send-hour 0-23 picker) — all primitives already installed per Phase 5A.
- **TanStack Query v5** — `useQuery` with `?window=` searchParam re-key; `invalidateQueries` after `/settings` Notifications mutation.
- **next-intl** + `apps/web/i18n/messages/{vi,en}.json` + `apps/web/scripts/check-i18n.ts` `EN_SCAN_FILES` — add new namespaces (`analytics.*`, `digest.*`, `settings.notifications.*`).

### Inbox-zero reference (read-only, on disk)
- `D:\study-materials-summer-2026\EXE202\inbox-zero` — daily-digest reference. Specifically:
  - `apps/web/utils/digest/` (digest-enabled.ts, format.ts, schedule.ts, send-digest.ts, summary-limit.ts) — composition and per-user schedule pattern.
  - `apps/web/app/api/resend/digest/route.ts` — provider-routed digest send entry (QStash trigger).
  - `apps/web/app/api/user/digest-schedule/` + `digest-settings/` — settings page wiring.
  - `packages/resend/emails/digest.tsx` + `packages/resend/src/` — Resend + React Email template structure. Inspiration only — Zero Mail re-implements in Java/Spring/Thymeleaf with its own privacy posture (no per-message body in digest, only metadata counts + sender_email + rule names).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AuditLogQueryService`** (`core.triage.projection`) — exact shape template for D-18: `JdbcTemplate` + `@Transactional(readOnly=true)` + keyset-style queries. Copy the bean wiring, not the SQL.
- **`MailMessageObservedEntity` + `MailMessageObservedRepository`** (`core.gmail.persistence`) — composite PK `(tenant_id, gmail_message_id)`; `sender_email` is the pre-sanitized `From` (already privacy-safe). Q1 + Q3 query this directly via JDBC, not the JPA repository.
- **`TriageAuditEntity`** (`core.triage.persistence`) — has `rule_name_snapshot` (denormalized, survives rename/delete per Phase 4), `action_type` (`label`/`archive`/`save_draft`), `decision`, `decided_at`, `applied_at`, `reverted_at`. Q1 + Q2 + Q4 query this via JDBC.
- **`ShedLockConfig`** (`backend/worker/billing`) — already wires `EnableSchedulerLock(defaultLockAtMostFor="PT5M")` + Liquibase-managed `shedlock` table. D-05 reuses without modification.
- **`BillingIntentExpirySweeper`** + 4 Triage* jobs — pattern template for `DigestDispatchScheduler` (`@Scheduled(...)` + `@SchedulerLock(name=..., lockAtLeastFor=PT1M, lockAtMostFor=PT10M)` + `@Transactional(propagation=REQUIRED)`).
- **`TriagePendingReaperJob`** — pattern template for promoting `digest_delivery.status='PENDING'` → `'FAILED'` past grace period (D-11).
- **`IdentifiedEnum` + `OrderedEnum`** (`core.shared.lang`) — for the `NotificationChannel` enum (id = `'email'`) and any digest-status enum (`PENDING`/`SENT`/`FAILED`).
- **`KeysetCursor`** (`core.shared.pagination`) — NOT used by analytics endpoint (no pagination; 4-panel response is fixed-shape), but reference for digest-delivery audit pagination if ever needed.
- **`OAuthProvisioningService.provisionBundledOAuth`** (Phase 01.5) — `PROPAGATION_REQUIRED` host for D-17 default-row insertion.
- **`AbstractTenantOwnedEntity`** (`core.shared.persistence`) — base for `NotificationPreferenceEntity` + `DigestDeliveryEntity` (auto `tenant_id` + auditing).
- **Spring Modulith `event_publication`** table (changeset 024) — available for D-12 dispatch transport if planner chooses Modulith-style after-commit.
- **`apps/web/features/triage/AuditTable`** — table shape reference; **NOT** reused literally (analytics is 4-panel not table-shaped). But the shared loading/empty/error primitives ARE reused.

### Established Patterns
- Thin controllers + service-owned `@Transactional`; controllers `controllers/<domain>/`, DTOs `dto/<domain>/`; response DTO `from(...)` mapper.
- CQRS-lite: Spring Data JPA for writes (entities), Spring Data JDBC for reads/hot paths (D-18 follows).
- `IdentifiedEnum` + static `fromId` fail-loud (`NoSuchElementException`); never `ordinal()` for storage.
- Privacy logging: `event=<name> tenantId={}` + structured fields; no body / addresses / Google subject / token bytes / prompts / completions. New `Analytics*PrivacySweepTest` + `Digest*PrivacySweepTest` mirror existing `Triage*PrivacySweepTest`.
- Direct service calls for transaction-critical commands (D-17 OAuth provisioning); Modulith events for after-commit side effects (D-12 dispatch).
- Liquibase YAML changelogs for all schema (D-15).
- ArchUnit boundary tests for adapter isolation (D-03 mirrors Spring AI boundary from Phase 2C).
- Frontend: shadcn primitives first (`pnpm dlx shadcn@latest add ...`), raw primitives not custom wrappers (rule of three); feature folders own `api/`, `query-keys.ts` (only if cached data), one hook file per use case; Playwright e2e in `apps/web/e2e/**`.
- Subproject-owned config: worker-only props in `backend/worker/.../application.yml`, api-only in `backend/api/.../application.yml`; shared core config nested under `ZeroMailCoreProperties` (precedent from Phase 02B billing).
- Vietnamese-first i18n via `next-intl` for `apps/web` + Spring `MessageSource` for backend email templates; lock-step vi/en bundle parity via `pnpm i18n:check` strict gate.

### Integration Points
- `OAuthProvisioningService.provisionBundledOAuth` → adds writes for `tenants.time_zone` default + `notification_preference (tenant_id, 'email', true, 20)` (D-17).
- `AccountDeletionController` (Phase 01.2 cascade bridge) → adds cascade for `notification_preference` + `digest_delivery` (D-16).
- New `AnalyticsController` (`/api/analytics/summary`) → new `AnalyticsSummaryQueryService` → JDBC over `triage_audit` + `mail_message_observed`.
- New `NotificationPreferencesController` (`/api/notifications/preferences` or `/api/me/notifications`) → new `NotificationPreferencesService` → JPA write + read of `notification_preference`.
- New `DigestDispatchScheduler` (`backend/worker`) → claims `digest_delivery` PENDING row → composes payload via `AnalyticsSummaryQueryService` (24h window) → dispatches via `NotificationChannel`.
- New `EmailNotificationChannel` → Thymeleaf renders HTML + TXT → Resend SDK send with `Idempotency-Key: ${tenantId}:${digestDayLocal}` (D-11).
- `apps/web` regenerates OpenAPI typed client after backend ships analytics + preferences endpoints (`pnpm generate:api`).
- next-intl namespace additions for `analytics.*`, `digest.*`, `settings.notifications.*` + `EN_SCAN_FILES` update in `apps/web/scripts/check-i18n.ts`.

</code_context>

<specifics>
## Specific Ideas

- Top-3 list ties are broken alphabetically asc on the secondary key (sender_email / rule_name_snapshot) — deterministic, fixture-stable; the user explicitly OK'd the default.
- Time-saved formula constants stay SPEC-locked (`label=10s, archive=30s, save_draft=180s`); retune is a v2 telemetry-driven concern.
- Resend is the v1 vendor because inbox-zero validated the API surface AND it is the only vendor here with an official Java SDK; the provider-free adapter boundary (D-03) keeps the swap to AWS SES Singapore a single-package change if Vietnam deliverability ever degrades.
- The digest body lists sender_email and rule_name_snapshot in the open — both are owner-visible by product design. Server logs MUST NOT echo either (privacy sweep test enforces).
- Hourly granularity (vs minute-precise) is intentional: `digest_send_hour_local` is a coarse user preference (int 0-23), so worst-case ~59min lateness on worker downtime is well inside acceptable expectation for a daily summary.

</specifics>

<deferred>
## Deferred Ideas

- CSV / JSON export of analytics data — backlog (out-of-scope per SPEC).
- Per-rule time-series histogram (mini chart per rule) — out-of-scope per SPEC; flat per-rule count + applied/reverted locked for v1.
- Custom digest cadence (weekly / monthly / pause-N-days) — out-of-scope per SPEC; v1 = daily only.
- Bounce / complaint / unsubscribe-list management at app layer — out-of-scope per SPEC; relying on Resend's built-in suppression for v1.
- Zalo / Telegram / in-app `NotificationChannel` adapters — explicitly out-of-scope per SPEC; abstraction is built so future adapters are new implementations of `NotificationChannel`, not digest-code changes.
- Marketing emails / product-update emails / re-engagement emails — out-of-scope per SPEC.
- Cross-tenant / admin analytics — out-of-scope per SPEC.
- Drill-down from analytics into individual audit rows — covered by Phase 5A `/triage` audit log.
- Real-time / SSE / websocket analytics updates — out-of-scope per SPEC.
- Anomaly detection / alerts ("your inbox volume spiked") — out-of-scope per SPEC.
- Embedded charts / images inside digest email — out-of-scope per SPEC; v1 is text-only HTML + plaintext.
- Backfill of pre-5C historical audit data — out-of-scope per SPEC.
- Outbox-driven scheduler (Option 4 in Area 2 research) — reserved as evolution path for Phase 6+ when 2nd channel ships with non-trivial retry/backoff or tenant count crosses ~5K.
- Pre-aggregation rollup table / Postgres materialized view — reserved for when a tenant crosses ~10M observed rows / 90d (not v1 forecast).
- Telemetry-driven retune of time-saved constants — v2 concern; requires opt-in user-feedback signal that is not in v1 scope.
- Multi-channel preference rows (Zalo/Telegram/in-app rows in `notification_preference`) — schema already future-proof (composite PK includes `channel`), no code change needed at insert time.

</deferred>

---

*Phase: 05C-user-surface-analytics-daily-digest*
*Context gathered: 2026-05-13*
