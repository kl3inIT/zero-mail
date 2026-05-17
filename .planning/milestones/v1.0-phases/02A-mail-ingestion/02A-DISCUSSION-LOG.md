# Phase 2A: Mail Ingestion - Discussion Log

**Date:** 2026-04-28
**Mode:** discuss (default), Vietnamese-mirroring per user memory
**Facilitator:** Claude (Opus 4.7)

This log captures the conversation that produced `02A-CONTEXT.md`. Audit/retrospective use only — downstream agents (researcher, planner, executor) read CONTEXT.md, not this file.

## Selected Areas

User selected ALL 4 areas for deep discussion:
- Push pipeline shape
- Sync state + dedup schema
- Watch lifecycle + failure
- Global pause UX + gate

## Area A — Push pipeline shape

**Recommendation rationale presented:** Pub/Sub default ack ~10s, redelivery indefinite on non-200. Pipeline shape decision drives api↔worker boundary, idempotency point, OIDC test scope.

### Q1: Push pipeline shape

| Option | Description | Selected |
|---|---|---|
| Ack-fast + Postgres handoff | Controller verify+dedup-row+200 < 200ms; worker SKIP LOCKED pulls | ✓ Recommended |
| Ack-fast + Spring @Async in api | Controller verify+row+200, then @Async on virtual threads inside same JVM | |
| Sync inline | Controller does full verify+history+observe+200 | |

**Decision:** Ack-fast + Postgres handoff (D-A1).

**Reasoning captured:** Controller stays sub-300ms p99 + trivially testable. Worker reusable for Phase 4. Aligns with CLAUDE.md "Postgres-backed SKIP LOCKED queue, no Kafka/RabbitMQ." Trade-off: 0–2s poll latency. ROADMAP success #1 "within seconds" satisfied with `fixedDelay=1000ms` on idle.

### Q2: Pub/Sub topology

| Option | Description | Selected |
|---|---|---|
| Single shared topic+subscription | All tenants on one topic; controller looks up tenant by emailAddress | ✓ Recommended |
| Per-tenant topic+subscription | Stronger isolation, multiplies GCP setup, requires provisioning automation | |

**Decision:** Single shared topic + subscription (D-A2). Rejected per-tenant: multiplies GCP setup, requires automation, no v1 isolation benefit.

## Area B — Sync state + dedup schema

**Recommendation rationale presented:** Two distinct dedup invariants (Pub/Sub redelivery vs Gmail message recurrence) → two tables. Multi-account deferred → extend `gmail_connections` directly.

### Q1: Schema placement + dedup table split

| Option | Description | Selected |
|---|---|---|
| Extend gmail_connections + 2 new tables | 4 columns + pubsub_delivery + mail_message_observed | ✓ Recommended |
| Separate gmail_sync_state 1:1 table | More forward-compat, adds 1 join per read | |
| Single table with nullable columns | Couples 2 semantics into 1 UNIQUE | |

**Decision:** Extend gmail_connections + 2 new tables (D-B1, D-B2).

### Q2: MessageObserved emit policy

| Option | Description | Selected |
|---|---|---|
| Per messagesAdded entry from history (INBOX-filtered) | Worker emits one row per new INBOX message | ✓ Recommended |
| Per messagesAdded + labelsAdded + labelsRemoved | Inbox-zero pattern; v1 doesn't react to label changes | |

**Decision:** messagesAdded only, INBOX-filtered (D-B4).

## Area C — Watch lifecycle: register + renew + failure

**Recommendation rationale presented:** External Gmail call inside OAuth handler couples login critical path to Gmail API health. Async-via-worker keeps OAuth trivial and unifies register+renew into one query.

### Q1: Watch lifecycle pattern

| Option | Description | Selected |
|---|---|---|
| Async worker, INBOX only, hourly+24h margin | Unified register+renewal query; 3-fail threshold flips ingestion_health | ✓ Recommended |
| Sync register in OAuth handler, INBOX only | Lower initial latency but couples OAuth to Gmail | |
| INBOX+SENT, async worker | Inbox-zero parity; doubles Pub/Sub volume for no v1 value | |

**Decision:** Async worker, INBOX only, every-minute scheduler with 24h margin (D-C1, D-C2, D-C3, D-C4).

**Note:** Adopted minute-tick rather than hourly to keep first-connect latency sub-60s while still acting as renewal cadence (per-tenant scatter natural).

## Area D — History-404 + watch-failure UX

**Recommendation rationale presented:** Three independent failure axes (token / scheduler / data) → orthogonal column rather than coupling into status enum. End-user CTA is identical regardless of cause → one copy.

### Q1: Status modeling

| Option | Description | Selected |
|---|---|---|
| ingestion_health column, status untouched | Orthogonal axis; UI gates on either being non-healthy | ✓ Recommended |
| Extend GmailConnectionStatus enum directly | Couples 3 axes into 1 enum, harder to reason about | |
| Two boolean columns | Doesn't follow IdentifiedEnum convention | |

**Decision:** New `GmailIngestionHealth` IdentifiedEnum + new column on gmail_connections; `GmailConnectionStatus` unchanged (D-D1).

### Q2: ReconnectPrompt copy variation

| Option | Description | Selected |
|---|---|---|
| 1 copy + 1 CTA for all causes | End-user doesn't care about root cause; OAuth re-grant fixes all | ✓ Recommended |
| 3 distinct copies for 3 causes | More precise but +6 i18n keys + maintenance churn | |

**Decision:** Single copy, single CTA (D-D3). `ingestion_health` value used for telemetry/admin only.

## Area E — Global pause toggle (MAIL-06)

**Recommendation rationale presented:** v1 single-tenant invariant → 1 boolean column, not a settings table. ROADMAP success #5 wording splits "events received" (Phase 2A unconditional) vs "write actions queued" (Phase 4 reads flag). UI = both Settings entry + persistent banner so paused state stays visible.

### Q1: Pause flag data model

| Option | Description | Selected |
|---|---|---|
| Column on tenants | One boolean, one migration | ✓ Recommended |
| tenant_settings table | Forward-compat; over-engineering for 1 flag | |

**Decision:** `tenants.triage_paused BOOLEAN NOT NULL DEFAULT false` (D-E1).

### Q2: UI surfaces

| Option | Description | Selected |
|---|---|---|
| Settings toggle + persistent banner | Toggle is entry point; banner constant reminder | ✓ Recommended |
| Settings toggle only | User may forget paused state | |
| Banner with inline toggle (no Settings) | Settings page is expected location for future flags | |

**Decision:** Both — Settings toggle + non-dismissible PauseBanner in `(protected)/layout.tsx` (D-E5).

## Locked-from-Prior-Phases (not re-asked)

- Push receiver = plain HTTP `@PostMapping` in `backend/api`, NO `spring-cloud-gcp` (CLAUDE.md)
- OIDC verification via `google-auth-library-oauth2-http` 1.35.0 (STACK.md)
- Postgres-backed SKIP LOCKED queue (CLAUDE.md)
- Scheduled jobs in `backend/worker` (Phase 1 pattern)
- ScopedValue `TenantContext.TENANT` binding before persistence (Phase 1 D-B1/D-B2)
- Privacy: zero email content / token bytes in logs or DB; `event=opaque tenantId={}` format (Phase 1 D-E1)
- `(tenantId, historyId, messageId)` idempotency triplet (MAIL-04)
- History-404 → bounded recovery, no full mailbox rescan (MAIL-05)
- Pause = events still observed, only write actions blocked (ROADMAP success #5)
- Reconnect URL = `/tenant/connect-gmail` with `prompt=consent` (Phase 01.5 D-A5)
- All-or-nothing OAuth provisioning preserved; watch is async post-provisioning (Phase 01.5 D-A2 unaffected)
- Single bundled `google` OAuth registration with `gmail.modify` scope (Phase 01.5 D-A1)
- `RefreshTokenCipher` AES-GCM envelope consumed verbatim (Phase 1 D-G2)

## Deferred Ideas Captured

(See `<deferred>` section of CONTEXT.md.)

## Claude's Discretion Items

(See "Claude's Discretion" subsection of `<decisions>` in CONTEXT.md — 16 items left to researcher/planner/executor judgment within the locked decisions.)

---

*Phase: 02A-mail-ingestion*
*Discussion completed: 2026-04-28*
