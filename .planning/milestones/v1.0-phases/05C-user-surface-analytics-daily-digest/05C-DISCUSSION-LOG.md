# Phase 5C: User Surface — Analytics & Daily Digest - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-13
**Phase:** 05C-user-surface-analytics-daily-digest
**Areas discussed:** Email vendor + template engine, Scheduler architecture, Idempotency mechanism, Preferences topology + Aggregation SQL shape
**Mode:** advisor (calibration tier = full_maturity per Vendor Philosophy: thorough-evaluator)
**Research:** 4 parallel gsd-advisor-researcher agents (one per area)

---

## Area 1A: Transactional Email Vendor

| Option | Description | Selected |
|--------|-------------|----------|
| Resend | Java SDK official `com.resend:resend-java`, 3K/mo free permanent, inbox-zero reference validated; US/EU hosted | ✓ |
| AWS SES (ap-southeast-1 Singapore) | Cheapest at scale, low VN latency; AWS sandbox approval friction + raw-MIME DX | |
| Postmark | Best deliverability, strict transactional-only; free only 100/mo, no Java SDK | |
| Mailgun (EU) | Mature 15+y, EU region; trial-only free | |
| Direct SMTP via Jakarta Mail | Vendor-agnostic at protocol; reuse existing `jakarta.mail.api`; no idempotency keys, manual bounce handling | |

**User's choice:** Resend (Recommended)
**Notes:** Vendor lock-in mitigated by the channel-agnostic adapter boundary (ArchUnit-enforced); swap to SES Singapore later is a one-package change.

---

## Area 1B: HTML Email Template Engine

| Option | Description | Selected |
|--------|-------------|----------|
| Thymeleaf 3.1.x | Spring default, first-class `MessageSource` i18n, `#{key}` fail-loud, slower render | ✓ |
| JTE (3.x) | Compiled to bytecode, type-safe; manual i18n bridge per template, smaller email community | |
| Pebble (3.2.x) | Twig-like, ~78% faster, native `i18n()`; Boot 4 compat unverified | |
| Java 25 text blocks | Zero deps, type-checked; XSS risk on user-supplied strings, designer-unfriendly | |

**User's choice:** Thymeleaf (Recommended)
**Notes:** Spring `MessageSource` already drives vi/en parity in app; HTML + sibling `.txt` template iterating same model for plaintext fallback.

---

## Area 2: Send-Hour Scheduler Architecture

| Option | Description | Selected |
|--------|-------------|----------|
| Hourly UTC cron + DB fanout | `@Scheduled` + `@SchedulerLock` + Postgres `EXTRACT(HOUR FROM now() AT TIME ZONE tz)`; mirrors 5 existing scheduler precedents | ✓ |
| Outbox-driven (scanner + SKIP LOCKED drainer) | Tách "decide-to-send" vs "actually-send"; reserved as evolution path | |
| Per-tenant `@Scheduled` beans | Precise minute fire; doesn't scale, fragile on preference edit | |
| Per-minute tick + exact match | Lowest latency; 60× wakeups overhead for hour-aligned use case | |

**User's choice:** Hourly cron + DB fanout (Recommended)
**Notes:** Postgres-side DST math via IANA tzdata; worst-case ~59min lateness on worker outage; evolution to outbox-driven reserved for Phase 6+ when 2nd channel ships or tenants >5K.

---

## Area 3: Idempotency Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Postgres `digest_delivery` + UNIQUE(tenant, day) | Durable source of truth; `ConstraintViolationException` = dedupe signal; channel-agnostic | ✓ |
| Postgres UNIQUE + Modulith event dispatch | (A) for dedupe + (B) `@ApplicationModuleListener` for after-commit dispatch + auto-retry | |
| Postgres UNIQUE + Redis SETNX hot-path | (A) durable + (C) Redis pre-check optimization | |
| Redis SETNX only | Non-durable; eviction = guarantee evaporates | |

**User's choice:** Postgres digest_delivery + UNIQUE (Recommended)
**Notes:** Mirrors existing precedents (`mail_message_observed` PK dedup, billing top-up `code` UNIQUE). Resend Idempotency-Key header passes `${tenantId}:${digestDayLocal}` as defense-in-depth. Modulith composition (publish + listen for after-commit dispatch) reserved as planner's call inside D-12.

---

## Area 4A: Preferences Topology

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid: `tenants.time_zone` + `notification_preference(tenant_id, channel)` | tz tenant-global (reusable beyond digest), enable+hour per-channel composite PK | ✓ |
| `notification_preference(tenant_id, channel)` only | Composite PK day 1; tz duplicate per channel (drift risk) | |
| `notification_preference(tenant_id PK)` single-row | Migration #2 needed when v2 channels arrive | |
| 3 columns on `tenants` | Minimal churn; violates channel-agnostic clause | |

**User's choice:** Hybrid (Recommended)
**Notes:** v2 channels (Zalo / Telegram / in-app) = single INSERT to `notification_preference`, no schema migration. `time_zone` reusable by future quiet hours / scheduled rules / weekly reports.

---

## Area 4B: Analytics Aggregation SQL Shape

| Option | Description | Selected |
|--------|-------------|----------|
| 4 sequential queries in 1 readOnly tx (JdbcTemplate) | Mirrors `AuditLogQueryService`; per-fragment EXPLAIN-able; digest job reuses fragments | ✓ |
| Single CTE/UNION ALL mega-query | 1 round-trip; heterogeneous result-set unpacking hack | |
| Parallel `CompletableFuture` × 4 | Wall-clock win; 4× connection cost + virtual-thread JDBC pinning risk | |
| Nightly pre-aggregation table | Right architecture for wrong scale (forecast <225K rows/90d/tenant) | |
| Postgres materialized view | pg_cron not installed; "missing today" gap | |

**User's choice:** 4 sequential queries (Recommended)
**Notes:** Indexes accompany: `triage_audit (tenant_id, rule_name_snapshot, decided_at)` and `mail_message_observed (tenant_id, sender_email, observed_at)` are NEW; `triage_audit (tenant_id, decided_at)` and `mail_message_observed (tenant_id, observed_at)` verify-existing. Digest job reuses the same fragments for "prior 24h" computation.

---

## Small Locks (Pre-baked Defaults — User Confirmed)

| Decision | Locked Value | Rationale |
|---|---|---|
| Top-3 tie-break | Alphabetical secondary key ASC (`sender_email`, `rule_name_snapshot`) | Deterministic, fixture-stable, no timestamp drift dependency |
| Time-saved constants | SPEC values (`label=10s, archive=30s, save_draft=180s`) | SPEC-locked; telemetry-driven retune is v2 concern |
| Endpoint shape | `GET /api/analytics/summary?window=7d\|30d\|90d` → one typed `AnalyticsSummaryResponse` | Matches typed OpenAPI client convention |
| Default window | 7d when `?window=` omitted | SPEC default |

---

## Claude's Discretion (Deferred to Planner)

- Package placement: `core.notification.channel.email.resend` vs `backend/worker/.../notification` (depends on where the channel resolver bean lives).
- Whether `core.analytics` becomes a new Spring Modulith module with `allowedDependencies = {triage, gmail, shared.persistence}` or whether analytics queries live inside `core.triage.projection`.
- Liquibase changeset numbering (continues from `031`).
- Whether retry of `FAILED` digest deliveries is a separate `@Scheduled` job or folded into the next-tick hourly run.
- Exact i18n key namespace (`digest.*`, `analytics.*`, `settings.notifications.*`) — align with existing namespacing.
- `IdentifiedEnum` `ChannelType` shape: single id `{ EMAIL }` v1 only, vs already shape `{ EMAIL, ZALO, TELEGRAM, IN_APP }` with future ids gated behind ArchUnit.
- Thymeleaf fragment decomposition (header / footer / vi-en switcher).
- Whether `DigestDispatchScheduler` uses `@Scheduled(cron=...)` or `@Scheduled(fixedRate=3_600_000L)` with explicit hour-of-day match inside the method (matches `BillingIntentExpirySweeper` literal pattern).
- Whether Modulith composition (publish `DigestDueEvent` + `@ApplicationModuleListener` for dispatch) is added on top of the UNIQUE dedupe in v1, or saved as a Phase 6 evolution.

---

## Deferred Ideas

(All deferred items are already inside `<deferred>` of CONTEXT.md. Listed here only because they came up during discussion.)

- Outbox-driven scheduler (Area 2 Option 4) — evolution path for Phase 6+ when 2nd channel or scale jump.
- Pre-aggregation rollup / materialized view (Area 4B Options b4/b5) — when >10M observed rows / 90d / tenant.
- Telemetry-driven time-saved constants retune — v2.
- Multi-channel preference rows (Zalo / Telegram / in-app) — schema future-proof (composite PK includes `channel`), no code change at insert time when v2 lands.
- Resend → SES Singapore vendor swap — single-package change if Vietnam deliverability degrades; not v1.

---

*Generated by gsd-discuss-phase advisor mode on 2026-05-13.*
