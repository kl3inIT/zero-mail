# Phase 10: Telegram Messaging Assistant — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-28
**Phase:** 10-telegram-messaging-assistant
**Areas discussed:** Audit table shape, Pending-action storage path, Outbox drain process, Chat streaming → Telegram edit cadence

---

## Audit Table Shape

Existing state surfaced by codebase scout: `triage_audit.source` already exists with CHECK `('TRIAGE','CLEANUP_CAMPAIGN')` (Liquibase changeset 086 from Phase 08-bulk-unsubscribe-campaign). SPEC TG-03 read as if `OutboundActionSource` enum + audit `source` were both brand-new; needed reconciliation.

| Option | Description | Selected |
|--------|-------------|----------|
| (A) Extend `triage_audit.source` CHECK | Add 5 new enum values to existing CHECK. One unified audit table. Tradeoff: semantically overloads `triage_audit` ("triage decision" + "outbound action source" conflated). | |
| (B) New `outbound_action_audit` table | Dedicated table for Gmail mutations (send/archive/markRead/markSpam/trash/snooze/reply/forward). `triage_audit` untouched. Clean separation; forward-compat for Zalo/web push. | ✓ |
| (C) Three tables (triage_audit / mail_action_audit / outbound_send_audit) | Maximum decoupling. Over-engineered for v1.3. | |

**User's choice:** Option B — new `outbound_action_audit` table (locked as D-01 in CONTEXT.md).
**Notes:** Confirmed via Claude recommendation. Schema sketch with 13 action types + 7 source values + JSONB metadata. No backfill (new table). Triage_audit decision-vs-execution separation kept explicit. Mandatory writer pattern enforced by new `OutboundActionAuditMandatoryArchTest`.

---

## Pending-Action Storage Path

Existing state surfaced by codebase scout: `assistant_pending_action` table already exists (Liquibase changeset 043 from Phase 7 HIGH-4 fix). Comment header explicitly states "confirmation CAS lives on assistant_pending_action, not chat_message.parts. chat_message is append-only projection data." SPEC TG-17 wording "iterate last 50 assistant chat_message.parts entries" needed correction.

| Option | Description | Selected |
|--------|-------------|----------|
| (A) `assistant_pending_action` CAS keyed by `(chat_id, tool_call_id)` | Lookup row → recompute token16 → constant-time compare → atomic CAS state PENDING→PROCESSING→CONFIRMED with version optimistic-lock → invoke `confirmAssistantEmailActionForAccount`. Reuses Phase 7 HIGH-4 fix path. | ✓ |
| (B) Iterate `chat_message.parts` JSONB | Original SPEC wording. Race-prone and re-opens the Phase 7 HIGH-4 bug. | |
| (C) Combined: pending_action for CAS + chat_message.parts for render | Confusing dual-source; chat_message.parts already serves render-only role naturally. | |

**User's choice:** Option A — `assistant_pending_action` CAS path (locked as D-02 in CONTEXT.md).
**Notes:** Confirmed via Claude recommendation. SPEC TG-17 explicitly superseded by D-02 — CONTEXT.md takes precedence per workflow contract; SPEC.md is not rewritten. Callback flow documented in 8 numbered steps. `chat_message.parts` retains append-only projection role for Telegram preview message rendering.

---

## Outbox Drain Process

| Option | Description | Selected |
|--------|-------------|----------|
| (A) `backend/worker` only, extend existing `processing_job` | New `job_type='MESSAGING_NOTIFICATION'` + `payload_json.channel='TELEGRAM'`. Reuses Phase 8E queue health / Phase 4 saga retry infrastructure. Webhook sync replies bypass outbox (direct controller→TelegramApiClient). | ✓ |
| (B) `backend/api` only | API process already handles webhook reception; bundling outbound drain keeps everything in one process. Tradeoff: API stealing under load + duplicates worker's SKIP LOCKED/retry/lease infrastructure. | |
| (C) Both processes drain | Dual drain with SKIP LOCKED safety. Adds operational complexity for no real benefit at v1.3 scale. | |

**User's choice:** Option A — worker-only drain via `processing_job` extension (locked as D-04 in CONTEXT.md).
**Notes:** Confirmed via Claude recommendation. Pattern explicitly mirrors Phase 8 8D `CATALOG_SYNC` step-via-payload_json. `messaging_notification_outbox` NOT created as a new table — CLAUDE.md "Queue = Postgres-backed (single outbox + processing_job table)" honored. Telegram 429 reschedules via existing `processing_job.available_at` mechanism without bumping `admin_requeue_count` (reserved for manual operator requeues). New `TelegramOutboxDrainArchTest` enforces.

---

## Chat Streaming → Telegram Edit Cadence

| Option | Description | Selected |
|--------|-------------|----------|
| (A) Placeholder + 800ms buffer + per-chat 1/s cap + 429 pause-then-final + 1-retry transport backoff | Specific tuned defaults: `✍️ Đang viết...` placeholder before first chunk; `Reactor.bufferTimeout(40 chunks, 800ms)`; max 1 `editMessageText` per second (Bucket4j enforced); on 429 pause bucket for `retry_after`, accumulate in memory, then single final edit; non-429 retries once with 500ms backoff. | ✓ |
| (Original SPEC) 80 chunks / 1000ms buffer, generic 429 handling | Loose numbers; planner would have to make UX-impacting tuning decisions later without grounding. | |

**User's choice:** Tuned defaults (locked as D-05..D-08 in CONTEXT.md).
**Notes:** Confirmed via Claude recommendation. Numbers are defaults; planner may revise during plan-phase if WireMock evidence suggests otherwise. Architecture (`ResponseSurface` enum + `TelegramChatStreamSink`) unchanged. LLM stream NOT aborted on transport errors — tool calls in-flight still complete; their outputs flow to `chat_message.parts` per Phase 7 normal flow.

---

## Claude's Discretion

Areas where the user did not specify and Claude chose defaults (subject to planner revision):

- **JWT key length for `messaging-link.secret`** — 256-bit HS256 (matches refresh-token-key strength).
- **Bot username** — fallback `@ZeroMailAssistantBot`; final pick at plan-phase BotFather step.
- **`notification_filter` JSONB schema** — initial shape `{ enabledRuleIds: string[] | null, vipOnly: boolean, classifications: string[] | null, quietHours: { startHour, endHour, timezone } | null }`. `quietHours` may be deferred.
- **Bucket4j version pin** — latest stable compatible with Java 25 + Spring Boot 4 at plan-phase time (likely 9.x line).
- **Telegram WireMock fixture structure** — per-test JSON fixtures under `backend/api/src/test/resources/telegram-fixtures/`; mirrors existing Pub/Sub WireMock pattern.

## Deferred Ideas

(See `<deferred>` section of CONTEXT.md for the full list with reasoning.)

- `/pause [duration]`, `/digest`, `/unread` slash commands — defer to dedicated follow-on phases.
- Daily digest auto-push to Telegram — defer to a "subscriptions" mini-phase.
- Reply Tracker → Telegram follow-up reminders — defer; Phase 10 stays independent.
- Bucket4j Redis backend — defer until worker scales horizontally.
- Zalo OA integration — separate follow-on phase pending business registration.
- Slack / Teams integration — different segment, not on v1.x roadmap.
- Tab-shaped Settings refactor — out of Phase 10 scope; would be its own all-sections phase.
- `telegram_event_audit` dedicated table for non-Gmail-mutation Telegram events — route to existing audit + Logback in v1.3.
- Bot-token rotation drill — out of v1.3 scope.
- Notification quiet hours UI editor — backend schema reserves the field; UI may defer to follow-on.
