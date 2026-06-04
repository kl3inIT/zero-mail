# Phase 10: Telegram Messaging Assistant — Context

**Gathered:** 2026-05-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 10 delivers a Telegram surface that lets a tenant connect their Telegram account from `/settings/connected-apps` in 3 clicks, receive real-time inline-button notifications when AI rules fire on incoming Gmail, chat with the same v1.1 chat assistant from Telegram DM with Spring AI streaming + tool-use, and confirm send/reply/forward actions via preview cards routed through the existing `OutboundSendGateway` and the existing `assistant_pending_action` CAS table. A new Spring Modulith module `core.messaging.telegram` owns the transport (webhook, init bean, Bucket4j throttle, RestClient gateway); a new sibling module `core.mailaction.usecases` owns the 5 non-send mail action methods (archive / markRead / markSpam / trash / snooze) that both Telegram inline buttons and future surfaces call; a new `core.triage.domain.TriageDecisionRecorded` event is published by `TriageOrchestratorService` so that messaging stays decoupled from triage_audit schema; a new `core.outbound.domain.OutboundActionSource` enum + a new `outbound_action_audit` table tag every Gmail mutation by surface. ARCH-02 body-ban is extended to all Telegram payload paths.

Out of phase: `/pause` `/digest` `/unread` slash commands (deferred; would require new services or schema). Out of phase: tab-shaped Settings refactor (kept as new sub-route to avoid Phase 9 regression). Out of phase: Zalo OA, Slack/Teams, group chat, voice/audio messages, inline-mode bot, login-with-Telegram.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**19 requirements are locked.** See `10-SPEC.md` for full requirements (Current/Target/Acceptance triples per requirement), boundaries, and acceptance criteria.

Downstream agents (`gsd-phase-researcher`, `gsd-planner`, `gsd-executor`) MUST read `10-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**Active requirement IDs (proposed, to mint in REQUIREMENTS.md):** TG-01 (TriageDecisionRecorded event), TG-02 (MailActionService facade), TG-03 (OutboundActionSource enum + outbound_action_audit table — per D-01 below), TG-04 (Bucket4j adoption + SEED-016 closure), TG-05 (bot token config), TG-06 (webhook double-secret verify), TG-07 (setMyCommands idempotent — 3 commands), TG-08 (stateless signed link code pairing), TG-09 (telegram_account table + lifecycle), TG-10 (DM-only enforcement), TG-11 (rule-fire notification listener), TG-12 (inline keyboard primary + More submenu), TG-13 (callback verification + cross-actor check), TG-14 (single send call site preserved + MailActionService boundary), TG-15 (3 slash commands only), TG-16 (free-text chat streaming with `ResponseSurface = TELEGRAM`), TG-17 (pending email confirm via assistant_pending_action — per D-02 below), TG-18 (`/settings/connected-apps` sub-route), TG-19 (audit + observability + privacy sweep).

**SPEC corrections surfaced by codebase scout 2026-05-28 (folded into D-01 / D-02 below — SPEC.md does not need to be rewritten because CONTEXT.md takes precedence for downstream agents per workflow contract):**
- `triage_audit.source` **already exists** with CHECK `('TRIAGE','CLEANUP_CAMPAIGN')` (Liquibase changeset 086). SPEC TG-03 reads as if the audit `source` is brand new; D-01 reconciles this by directing Phase 10 to create a new `outbound_action_audit` table instead of extending `triage_audit.source`.
- `assistant_pending_action` table **already exists** with state CAS, `tool_call_id`, `version`, `draft_body` (Liquibase changeset 043) per Phase 7 HIGH-4 fix ("confirmation CAS lives on assistant_pending_action, not chat_message.parts"). SPEC TG-17 mentions "iterate chat_message.parts" — D-02 corrects: Telegram callback path uses `assistant_pending_action` CAS keyed by `(chat_id, tool_call_id)`.

**In scope (from SPEC.md Boundaries):**
- New Spring Modulith module `core.messaging.telegram` (subpackages `domain/`, `usecases/`, `persistence/`, `gateway/`, `notification/`, `chat/`, `webhook/`)
- New Spring Modulith module `core.mailaction.usecases`
- New domain event `TriageDecisionRecorded` in `core.triage.domain` + publisher wired into `TriageOrchestratorService`
- New enum `OutboundActionSource` in `core.outbound.domain` (7 values)
- New enum `ResponseSurface` in `core.chat.domain` (`WEB_SSE`, `TELEGRAM`) + `ChatOrchestrator.stream` signature update + new `TelegramChatStreamSink`
- New tables: `telegram_account`, `telegram_notification_log`, `outbound_action_audit` (per D-01)
- Webhook controller in `backend/api/controllers/integrations/` + dedicated `@Order(1)` `TelegramWebhookSecurityConfig`
- Settings UI sub-route `/settings/connected-apps` + feature module `apps/web/features/telegram-integration/`
- `TelegramPathBodyBanTest` + `TelegramPrivacySweepTest`
- Bucket4j dep + `TelegramSendRateLimiter` (per-chat 1/s + global 30/s) + closure of SEED-016
- 3 slash commands only: `/start`, `/help`, `/disconnect`
- Inline keyboard (primary + More submenu + destructive confirm-twice)
- Deep-link pairing UX via `t.me/<bot>?start=<signed-code>`
- Documentation `docs/integrations/telegram-setup.md`
- Playwright e2e covering Connect → notify → callback-confirm-send

**Out of scope (from SPEC.md Boundaries):**
- `/pause [duration]`, `/digest`, `/unread` slash commands
- Tab-shaped Settings refactor (5th-tab)
- Zalo OA integration (follow-on phase)
- Slack / Teams (different segment)
- Group chat (DM-only mandatory)
- Multi-Gmail-account `/switch`
- Voice / audio / inbound file attachments
- Inline-mode bot
- "Login with Telegram"
- Embedding / vector index of chat content
- Bucket4j Redis backend (in-memory only for v1.3)
- Cross-process Spring events (worker ↔ API stay decoupled via `processing_job` outbox)

</spec_lock>

<decisions>
## Implementation Decisions

### Audit Table Shape (D-01)

- **D-01 (LOCKED 2026-05-28 round 1):** Create a new `outbound_action_audit` table for all Gmail-mutation audit rows; do NOT extend `triage_audit.source`. Reason: `triage_audit` has a clear semantic identity ("triage decisions on inbound mail" — CHECK currently `('TRIAGE','CLEANUP_CAMPAIGN')`). Adding TELEGRAM_INLINE_BUTTON / WEB_CHAT_CONFIRMED / etc. would overload it semantically and confuse future readers ("why is a Telegram button click in triage_audit?"). The new table covers send / reply / forward / archive / markRead / markSpam / trash / snooze regardless of surface (rule auto, web chat, Telegram inline, Telegram chat, Telegram deep-link). Forward-compat for Zalo and web push without touching `triage_audit`.

  **Schema sketch (final shape decided in plan-phase Liquibase task):**
  ```sql
  CREATE TABLE outbound_action_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    gmail_message_id VARCHAR(64),          -- nullable: applies only to message-bound actions
    gmail_thread_id VARCHAR(64),
    action VARCHAR(32) NOT NULL,
    source VARCHAR(64) NOT NULL,
    initiated_by_user_id UUID,             -- nullable for RULE_AUTO
    initiated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    succeeded BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    CONSTRAINT ck_outbound_action_audit_action CHECK (action IN
      ('SEND','REPLY','FORWARD','ARCHIVE','MARK_READ','MARK_UNREAD','MARK_SPAM','TRASH','SNOOZE','STAR','UNSTAR','ADD_TO_DIGEST','SAVE_DRAFT')),
    CONSTRAINT ck_outbound_action_audit_source CHECK (source IN
      ('RULE_AUTO','WEB_CHAT_CONFIRMED','TELEGRAM_INLINE_BUTTON',
       'TELEGRAM_INLINE_BUTTON_DESTRUCTIVE_CONFIRMED','TELEGRAM_CHAT_CONFIRMED',
       'TELEGRAM_DEEPLINK_FROM_NOTIFICATION','WEB_LEGACY'))
  );
  CREATE INDEX idx_outbound_action_audit_tenant_initiated_at ON outbound_action_audit (tenant_id, initiated_at DESC);
  CREATE INDEX idx_outbound_action_audit_gmail_msg ON outbound_action_audit (tenant_id, gmail_message_id) WHERE gmail_message_id IS NOT NULL;
  ```

  **Backfill policy:** none. The table is new; legacy outbound rows (Phase 7 chat confirm-send, Phase 4 triage auto-send) are NOT migrated. The `WEB_LEGACY` enum value is reserved but is NOT used for backfill — it exists only as a forward-compat sentinel if Phase 11+ needs to migrate older surfaces. Forensic queries against pre-Phase-10 sends will continue to rely on Phase 7 `chat_message.parts` + `assistant_pending_action.state='CONFIRMED'` + Phase 4 `triage_audit.action_taken IS NOT NULL`.

  **Mandatory writer:** every `OutboundSendGateway.send(...)` invocation and every `MailActionService` method (D-03 below) writes exactly one `outbound_action_audit` row in the same transaction. ArchUnit test `OutboundActionAuditMandatoryArchTest` asserts no Gmail mutation call site bypasses the audit writer.

  **Triage audit untouched:** `triage_audit.source` retains CHECK `('TRIAGE','CLEANUP_CAMPAIGN')`. No new values, no rename.

### Pending-Action Storage Path (D-02)

- **D-02 (LOCKED 2026-05-28 round 1):** Telegram callback path for pending email confirmation reads and updates `assistant_pending_action` (existing table from changeset 043, Phase 7 HIGH-4 fix). It does NOT iterate `chat_message.parts`. Reason: Phase 7 explicitly relocated confirmation CAS off `chat_message.parts` ("chat_message is append-only projection data; assistant_pending_action is the confirmation-state source of truth"). Re-iterating `chat_message.parts` re-opens that bug.

  **Callback flow (locked):**
  1. Telegram callback arrives with `callback_data = "<actionId>:<resourceId>:<token16>"` where `actionId ∈ {send_email, reply_email, forward_email, save_draft}` and `resourceId = toolCallId`.
  2. `TelegramCallbackRouter` resolves `telegram_account` by `chat_id` (single row), enforces cross-actor check `callback_query.from.id == telegram_account.telegram_user_id`.
  3. Lookup `assistant_pending_action WHERE chat_id = (resolved from chat_message_id linkage) AND tool_call_id = resourceId AND state = 'PENDING'`. If not found → reply "Action không hợp lệ hoặc đã hết hạn." (also covers the replay-after-confirm case, returning "Đã xử lý").
  4. Recompute `token16 = sha256(actionId + ":" + chatMessageId + ":" + toolCallId).hex.substring(0, 16)` from the loaded row; constant-time compare. Mismatch → "Action không hợp lệ."
  5. CAS state transition: `UPDATE assistant_pending_action SET state = 'PROCESSING', version = version + 1 WHERE id = ? AND state = 'PENDING' AND version = ?`. If updated_rows = 0 → another tap won; reply "Đã xử lý."
  6. Invoke the existing `confirmAssistantEmailActionForAccount(...)` (Phase 7) with `source = TELEGRAM_CHAT_CONFIRMED`. This routes through `OutboundSendGateway.send(...)` (no new send call site).
  7. On success: CAS `state = 'CONFIRMED'`; edit the Telegram preview message to "✅ Đã gửi lúc HH:mm".
  8. On failure: CAS `state = 'FAILED'` + `failure_reason` (short enum) + reply Telegram "Gửi không thành công: <code>".

  **`chat_message.parts` role:** unchanged. Used as the append-only projection rendered into Telegram preview message text + inline keyboard. Not the source of truth for CAS state. SPEC TG-17 wording about "iterate last 50 assistant chat_message.parts entries" is superseded by D-02.

### MailActionService Boundary (D-03)

- **D-03:** `MailActionService` lives in a NEW Spring Modulith module `core.mailaction.usecases`. Interface methods: `archive(UUID tenantId, String gmailMessageId, OutboundActionSource source)`, `markRead(...)`, `markSpam(...)`, `trash(...)`, `snooze(UUID tenantId, String gmailMessageId, Instant snoozeUntil, OutboundActionSource source)`. Each method (a) calls `GmailApiClient` once, (b) writes exactly one `outbound_action_audit` row in same TX, (c) emits a Spring event `MailActionPerformed` (in-process, AFTER_COMMIT) for future listeners — Phase 10 does NOT register a listener but the event is published so the schema is forward-compat.

  Modulith `allowedDependencies` for `core.mailaction`: `{gmail, gmail :: gateway, tenant, outbound, shared :: persistence, shared :: lang}`. `core.messaging.telegram`, `core.chat`, `core.triage` all gain `core.mailaction` (or `core.mailaction :: api`) on their `allowedDependencies` list.

  **`TriageGmailWriter` refactor:** the existing 5 archive-like methods inside `TriageGmailWriter` are migrated to delegate to `MailActionService.<method>(..., source = RULE_AUTO)`. Existing triage tests stay green (audit rows now in `outbound_action_audit` instead of being implicit in `triage_audit.action_taken`). `triage_audit` continues to record the triage decision row (with classification + matched rule + action chosen); `outbound_action_audit` records the side-effect execution. Two-table model = decision vs execution clearly separated.

  ArchUnit `MailActionServiceArchTest` asserts: no `GmailApiClient.users().messages().modify(...)` / `.trash(...)` call outside `core.mailaction.usecases`.

### Outbox Drain Process (D-04)

- **D-04 (LOCKED 2026-05-28 round 2):** `messaging_notification_outbox` is **not** introduced as a new table. Instead, Phase 10 extends the existing `processing_job` table (Liquibase changesets 024 + 078 + 081, owner Phase 8E) with two new pieces:
  1. A new `job_type = 'MESSAGING_NOTIFICATION'` value (added to the existing `processing_job.job_type` CHECK constraint).
  2. A `processing_job.payload_json` shape: `{ channel: 'TELEGRAM', tenantId, telegramChatId, notificationKind: 'RULE_FIRED' | 'COMMAND_REPLY' | 'EDIT_MESSAGE', payload: {...} }`.

  Matches Phase 8 8D `CATALOG_SYNC` step-via-payload_json pattern (per STATE.md decision line: "Catalog Sync sub-steps live in processing_job.payload_json->>'step'").

  **Drain location: `backend/worker` only.** Reasons:
  - CLAUDE.md Convention 9 + Phase 4 saga retry both wire on worker; reuse the same SKIP LOCKED / leasing / retry / DLQ infrastructure (Phase 8E queue health) without duplication.
  - `backend/api` handles webhook reception (low-latency 401/200) and direct DB-only writes (pairing consume); it does NOT process outbound. Single drain = no double-claim race.
  - Webhook reply to `/start <code>` and immediate `/help` / `/disconnect` responses do NOT enqueue an outbox row — those are sync HTTP replies from the webhook controller via direct `TelegramApiClient.sendMessage(...)`. Only background-driven outbound (rule-fire notifications + LLM streaming edits + retry-after rescheduled sends) flows through `processing_job`.

  **Telegram 429 handling at the outbox layer:** when worker drains a `MESSAGING_NOTIFICATION` row and `TelegramApiClient.send(...)` raises 429, worker re-enqueues the same row with `available_at = now() + retry_after` (existing `processing_job` mechanism). `admin_requeue_count` is NOT bumped for 429 backoff (it is reserved for manual operator requeues per changeset 078).

  **ArchUnit:** `TelegramOutboxDrainArchTest` asserts (a) no `@Scheduled` annotation in `backend/api` references `MESSAGING_NOTIFICATION`, (b) all worker-side drain code lives in `core.messaging.telegram.notification` or `backend/worker` adapters.

### Chat Streaming Cadence to Telegram (D-05..D-08)

- **D-05 (Placeholder UX):** When `ChatOrchestrator.stream(...)` begins emitting for `ResponseSurface = TELEGRAM`, `TelegramChatStreamSink` immediately calls `TelegramApiClient.sendMessage(chatId, "✍️ Đang viết...", placeholderMessageId capture)` before the first LLM chunk arrives. The returned `message_id` is used by all subsequent `editMessageText` calls. This eliminates the "is the bot dead?" perceived-latency gap during initial LLM TTFT (typically 300-800 ms for OpenAI gpt-4o-mini).

- **D-06 (Buffer parameters):** `Reactor.bufferTimeout(40 chunks, Duration.ofMillis(800))`. Reasoning: 800 ms balances responsiveness vs Telegram edit call rate; 40 chunks ≈ a short paragraph at typical LLM chunk granularity. The 80 chunks / 1000 ms numbers in SPEC TG-16 are loosened to these tighter defaults; planner may revise during plan-phase if WireMock evidence suggests otherwise. **Hard cap:** at most 1 `editMessageText` per chat per second (enforced by `TelegramSendRateLimiter` per-chat bucket from TG-04). When buffer fires faster than 1/s, the buffered chunks accumulate and the next edit ships them all in one call.

- **D-07 (429 fallback at edit layer):** When `TelegramApiClient.editMessageText(...)` returns 429:
  - Parse `retry_after` header.
  - Pause the per-chat `TelegramSendRateLimiter` bucket for `retry_after` seconds via `bucket.addTokens(-cap); bucket.replenishAt(now() + retry_after)`.
  - Continue accumulating LLM chunks in memory (do NOT drop output).
  - When the pause expires, post a **single final `editMessageText`** with the full accumulated text — do NOT resume incremental edits. This avoids cascading 429 chains.
  - The typing indicator `✍️` is removed only on the final edit.

- **D-08 (Non-429 transport errors during streaming):** Retry once with 500 ms backoff. If second attempt also fails → drop the typing indicator + post a terminal message "Lỗi kết nối tạm thời, hãy thử lại." + write a Logback log line `event=telegram.stream.transport_error tenantId={} chatId={} kind=<short-enum>`. The LLM stream is NOT aborted (any tool calls in-flight still complete; their outputs go to `chat_message.parts` per Phase 7 normal flow). Subsequent free-text chats on the same chat continue working.

### Claude's Discretion

- **JWT key length for `messaging-link.secret`:** Claude picks 256-bit HS256 (32 bytes base64-encoded) to match the existing `REFRESH_TOKEN_KEY_BASE64` strength tier. Researcher / planner may revise if Spring Security docs recommend stronger for short-TTL tokens.
- **Bot username:** Claude defers final choice to plan-phase BotFather registration step. Fallback `@ZeroMailAssistantBot` is documented but actual chosen handle is captured in `TelegramProperties.botUsername` + `docs/integrations/telegram-setup.md`.
- **`notification_filter` JSONB schema:** initial shape `{ enabledRuleIds: string[] | null, vipOnly: boolean, classifications: string[] | null, quietHours: { startHour: int, endHour: int, timezone: string } | null }`. `null` on `enabledRuleIds` / `classifications` means "all"; `null` on `quietHours` means "no quiet hours". Backend predicate evaluates AND across non-null fields. Planner may simplify (e.g., defer `quietHours` to a follow-on).
- **Bucket4j version pin:** Claude picks the latest stable release at plan-phase time compatible with Java 25 + Spring Boot 4 (likely the `bucket4j-core` 9.x line). Pin in `libs.versions.toml`.
- **Telegram WireMock fixtures:** Claude picks the structure (per-test JSON fixture files under `backend/api/src/test/resources/telegram-fixtures/`) at plan-phase time. Pattern mirrors existing Pub/Sub WireMock setup.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 10 inputs
- `.planning/phases/10-telegram-messaging-assistant/10-SPEC.md` — **Locked requirements (19) — MUST read before planning.** Current/Target/Acceptance triples + boundaries + constraints + 23 acceptance criteria.
- `.planning/seeds/SEED-007-messaging-assistant-slack-telegram-zalo.md` — Original draft SPEC + 6 open questions (4 now resolved in CONTEXT, 2 still in scope: Q1 notification dedup window, Q5 bot username — see Claude's Discretion).
- `.planning/seeds/SEED-016-bucket4j-rate-limiting-evaluation.md` — Closed by Phase 10 TG-04; reference for adoption rationale.

### Codebase invariants (privacy, audit, send call site)
- `CLAUDE.md` §Privacy — body-content ban (ARCH-02), draft-body carve-out, no logging of LLM exchanges (regardless of source).
- `CLAUDE.md` §"Hard do not use list" — no WebFlux, no Lombok, no `javax.*`, no raw HTTP LLM calls outside `core.llm.gateway.springai`, no non-streaming fallback for chat assistant.
- `CLAUDE.md` §Conventions 5, 6, 9 — privacy logging format, direct calls vs Spring Modulith events, subproject-owned configuration files.
- `backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java` — Single Gmail send call site invariant. Stays at 1 match after Phase 10.
- `backend/core/src/test/java/com/zeromail/core/arch/ChatPersistenceContentBanTest.java` — Body-ban regex pattern (extended in TG-19 via `TelegramPathBodyBanTest`).

### Existing tables Phase 10 reads / extends (NOT replaces)
- `backend/core/src/main/resources/db/changelog/changes/043-assistant-pending-action.yaml` — Pending-action CAS table; D-02 reuse, not modified.
- `backend/core/src/main/resources/db/changelog/changes/086-triage-audit-source.yaml` — `triage_audit.source` CHECK ('TRIAGE','CLEANUP_CAMPAIGN'); D-01 leaves untouched.
- `backend/core/src/main/resources/db/changelog/changes/024-modulith-event-publication.yaml` — Spring Modulith event publication table; TriageDecisionRecorded event from TG-01 uses this infrastructure.
- `backend/core/src/main/resources/db/changelog/changes/078-processing-job-extend.yaml` — `processing_job` outbox columns (`admin_requeue_count`, `last_failure_reason`); D-04 reuses.
- `backend/core/src/main/resources/db/changelog/changes/081-processing-job-tenant-scope.yaml` — `processing_job` tenant scoping; D-04 reuses.

### Code seams Phase 10 plumbs into
- `backend/core/src/main/java/com/zeromail/core/outbound/usecases/OutboundSendGateway.java` — single Gmail send call site. Telegram callback (TG-17 path) routes here via existing `confirmAssistantEmailActionForAccount`.
- `backend/core/src/main/java/com/zeromail/core/outbound/usecases/OutboundSendCommand.java` — record gains `OutboundActionSource source` field per TG-03/D-01.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java` — `stream(...)` gains `ResponseSurface surface` parameter per TG-16/D-05..D-08.
- `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatStreamSink.java` — interface implemented by new `TelegramChatStreamSink`.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java` — `processObservedEvent(...)` adds `applicationEventPublisher.publishEvent(new TriageDecisionRecorded(...))` after action applied (per TG-01).
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java` — refactored to delegate the 5 archive-like methods to `MailActionService` (per TG-02/D-03).
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` — AES-GCM envelope pattern; if Telegram bot-token rotation is added later, follow the same envelope shape.

### Frontend integration
- `apps/web/AGENTS.md` — Generated OpenAPI schema rule; running backend → `pnpm --filter web run generate:api` → commit the regenerated files.
- `apps/web/CLAUDE.md` (via AGENTS.md) — TanStack Query v5 toast pattern (`meta.successMessage`/`errorMessage`), 401 redirect at fetch layer, no hardcoded color hex.
- `apps/web/app/(protected)/(app)/settings/SettingsClient.tsx` — Existing single-page card grid; D-18 (per SPEC TG-18) adds one navigation entry pointing to `/settings/connected-apps`; everything else unchanged.

### Reference (architectural only, no code port)
- `E:/Project/inbox-zero/apps/web/utils/messaging/**` — Inbox Zero local clone; validated overall architecture (1 global bot, stateless signed link code, deterministic token, More-submenu, DM-only).
- `E:/Project/inbox-zero/docs/telegram/setup.mdx` — BotFather setup reference.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`OutboundSendGateway` (`core.outbound.usecases`)** — Single Gmail send call site. Telegram chat-confirmed send (TG-17) reuses this; signature gains `OutboundActionSource source` param.
- **`assistant_pending_action` table + entity (Phase 7)** — Confirmation CAS for pending email actions. Telegram callback uses this directly; no new table needed (D-02).
- **`ChatOrchestrator` + `ChatStreamSink` (`core.chat.usecases`)** — Existing streaming pipeline. Phase 10 adds `ResponseSurface` enum + `TelegramChatStreamSink` implementation; no parallel chat pipeline.
- **`processing_job` table + worker drain (Phase 8E)** — Existing Postgres outbox with `SKIP LOCKED`, lease, retry, DLQ, `admin_requeue_count`, `last_failure_reason`. Telegram extends with `job_type = 'MESSAGING_NOTIFICATION'` payload (D-04).
- **`RefreshTokenCipher` (`core.gmail.persistence.crypto`)** — AES-GCM envelope. Not needed in Phase 10 (Telegram bot token comes from env, not DB); pattern reference for future bot-token rotation phase.
- **`@ApplicationModuleListener` + `@TransactionalEventListener(AFTER_COMMIT)` pattern** — Used by `TriageOrchestratorService.onMailMessageObserved` (sync) and `ChatModelCacheEvictionListener` (AFTER_COMMIT). Telegram notification listener uses the AFTER_COMMIT variant (TG-11).
- **`TenantDeletionRegistry` (`core.admin.tenant`)** — Cascade hook on `user_account` deletion. `telegram_account` registers a participant to flip `status = 'DISCONNECTED'`.
- **`MasterKeyAdminService.testConnection` (Phase 8) + `ProviderConnectionTester` (Phase 9 D-14)** — Pattern reference for the existing-row + inline-payload dual-path service shape; Telegram does not reuse but the architectural style applies to `TelegramApiClient.testConnection` (BotFather check before save).
- **`@Order(1)` `SecurityFilterChain` (Phase 02A `PubSubSecurityConfig`)** — Pattern reference for the Telegram webhook SecurityFilterChain. Order 1 keeps webhook chain ahead of user-session chain (order 2).
- **`triage_audit` table + `TriageAuditWriter` + `TriageAuditSaga`** — Reference for atomic audit writer pattern. `outbound_action_audit` (D-01) follows the same shape: a single writer service, write in same TX as the action it audits.

### Established Patterns
- **No Lombok / records for DTOs / classes for entities.** Apply to `TelegramAccountEntity` (class) + all DTO records (`TelegramUpdate`, `TelegramMessage`, `TelegramCallbackQuery`, `PairingResponse`, etc.).
- **Backend enterprise naming.** Per CLAUDE.md §Backend Code Style: `request`/`response`/`telegramAccountRepository`/`mailActionService`/`telegramProperties`. No `req`, `res`, `repo`, `svc`, `cfg`, `ctx`, `msg`, `err`, `ex`, `e`, `conn`, `tx`.
- **Liquibase YAML changelogs.** Each changeset numbered sequentially after #098 (latest committed). Phase 10 will add ~5 changesets: telegram_account, telegram_notification_log, outbound_action_audit, processing_job CHECK extension (MESSAGING_NOTIFICATION), MailAction module bootstrap if any DB seed needed.
- **Spring Modulith `package-info.java` with `@ApplicationModule(allowedDependencies = {...})`.** Apply to `core.messaging.telegram/package-info.java` and `core.mailaction/package-info.java`.
- **ArchUnit invariant tests next to the rule they enforce.** Phase 10 adds: `TelegramPathBodyBanTest`, `TelegramPrivacySweepTest`, `TelegramOutboxDrainArchTest`, `OutboundActionAuditMandatoryArchTest`, `MailActionServiceArchTest`. All live under `backend/core/src/test/java/com/zeromail/core/arch/`.
- **`zero-mail.*` canonical kebab-case `application.yml` key shape (Phase 02C P05b).** `TelegramProperties` binds under `zero-mail.messaging.telegram.*` (NOT `zeromail.telegram` as SEED draft suggested).

### Integration Points
- `TelegramWebhookController` (backend/api) → `TelegramUpdateRouter` (core.messaging.telegram.webhook) → branches into pairing / command / callback / free-text handlers.
- `TriageOrchestratorService.processObservedEvent` → publishes `TriageDecisionRecorded` → `TelegramNotificationListener` (AFTER_COMMIT) enqueues `processing_job` row → worker drains → `TelegramApiClient.sendMessage`.
- Telegram callback → `assistant_pending_action` CAS → `OutboundSendGateway.send(source = TELEGRAM_CHAT_CONFIRMED)` → `outbound_action_audit` writer.
- Telegram callback (inline keyboard archive/markRead/...) → `MailActionService.<method>(source = TELEGRAM_INLINE_BUTTON or TELEGRAM_INLINE_BUTTON_DESTRUCTIVE_CONFIRMED)` → `outbound_action_audit` writer.
- Free-text Telegram message → `ChatOrchestrator.stream(..., surface = TELEGRAM)` → `TelegramChatStreamSink` → `TelegramApiClient.editMessageText` with D-06 cadence.
- Settings UI `Connect` action → `POST /api/integrations/telegram/pairing` → JWT-signed code → polls `GET /api/integrations/telegram/status` every 2s → `/start <code>` consumed by webhook → `telegram_account INSERT...ON CONFLICT UPDATE` → next status poll returns `connected: true` → dialog closes.

</code_context>

<specifics>
## Specific Ideas

- **JWT signing for pairing link code** — HS256 with dedicated `TelegramProperties.messagingLinkSecret`, separate from session secret and refresh-token key. 10-minute TTL. NO DB row at generation time; DB row only on `/start <code>` consume.
- **Inline keyboard button text** — Vietnamese-first (`💬 Trả lời`, `📥 Lưu trữ`, `🔗 Mở`, `⋯ Khác`, `😴 Tạm hoãn 1h`, `😴 Đến mai`, `🚫 Spam`, `🗑 Xoá`, `✅ Xác nhận`, `❌ Huỷ`, `✅ Gửi`, `📝 Sửa`, `💾 Lưu nháp`). i18n via the `language_code` column on `telegram_account` — if `language_code = 'en'`, render English labels; default Vietnamese. Source of truth: backend Vietnamese/English message bundle (NOT i18n in webhook handler — labels are part of the inline_keyboard JSON literal, so a small `TelegramButtonLabels.vi/en` Java map in `core.messaging.telegram.notification`).
- **Notification dedup (SEED OQ#1)** — Idempotent on `(tenant_id, gmail_message_id)` within a 24-hour window. Second rule fire on the same Gmail message ID → suppressed (no edit, no resend). Mirrors Inbox Zero suppress behavior. Implemented via a partial UNIQUE index on `outbound_action_audit (tenant_id, gmail_message_id) WHERE source LIKE 'TELEGRAM_%' AND initiated_at > NOW() - INTERVAL '24 hours'` — or equivalent check in the listener before enqueue.
- **`/start` without code** — Friendly fallback "Hãy mở Zero Mail → Settings → Connected Apps để lấy mã kết nối." with deep link back to web app.
- **`@ZeroMailBot` username collision** — fallback handle `@ZeroMailAssistantBot`; final pick at plan-phase during BotFather registration; captured in `TelegramProperties.botUsername`.

</specifics>

<deferred>
## Deferred Ideas

These came up during scope-cut and belong in follow-on phases. Captured here so they aren't lost.

- **`/pause [duration]` slash command** — requires `TenantService.setTriagePaused` to accept a `Duration` enum (`1h | 4h | today | tomorrow`) + a new `tenant.pause_until TIMESTAMPTZ` column. Defer to a dedicated "pause durations" mini-phase.
- **`/digest` slash command** — requires a new `DigestService.generateOnDemand` use case + Telegram-formatted digest rendering. Defer to a dedicated digest phase (matches SEED-007 OQ#2 recommendation).
- **`/unread` slash command** — requires Telegram-specific render of top-5 unread metadata; could reuse `RecentInboxReadService`. Defer to follow-on.
- **Daily digest auto-push to Telegram (SEED OQ#2)** — the existing v1.0 `ANL-03` daily digest cron currently emails users. Auto-pushing the same digest to Telegram when `telegram_account.notifications_enabled = TRUE` is a small but separate change. Defer to a "subscriptions" mini-phase.
- **Reply Tracker → Telegram follow-up reminders (SEED OQ#3)** — Phase 9 / future Reply Tracker may introduce a "you haven't replied to X" event. If Reply Tracker ships first, add a small listener that posts to Telegram. Phase 10 stays independent.
- **Bucket4j Redis backend for horizontal scale** — current in-memory Bucket4j is fine for single-process worker. When worker scales horizontally, swap to Redis-backed Bucket4j; the SEED-016 evaluation already covers this transition.
- **Zalo OA integration** — separate follow-on phase pending business registration. `MessagingChannel` interface in `core.messaging.api` (introduced by Phase 10) accommodates Zalo as the second implementation.
- **Slack / Teams integration** — different segment (B2B workspace). Not on v1.x roadmap.
- **Tab-shaped Settings refactor** — `/settings/connected-apps` sub-route avoids the regression risk of reshaping the existing card grid. If we later want to tab-ify Settings, it's its own phase covering all sections at once (not piecemeal).
- **`telegram_event_audit` table for telegram-specific events** (link, unlink, blocked-by-user, command-invocation) — SEED TG-15 mentions this. Phase 10 routes these to the existing audit / Logback path; a dedicated table is not added in v1.3. Revisit if forensic/analytics needs grow.
- **Bot-token rotation drill** — when Telegram bot tokens need rotation, mirror the refresh-token-key rotation drill from CLAUDE.md Blockers/Concerns. Out of scope for v1.3.
- **Notification quiet hours (`notification_filter.quietHours`)** — initial JSONB schema includes the field, but the UI editor in Settings → Connected Apps may defer it to a smaller follow-on. Planner decides.
- **`notification_filter.vipOnly` + `notification_filter.enabledRuleIds` predicate enforcement (plan-checker W-4, 2026-05-28).** Plan 07 `TelegramNotificationFilterEvaluator` returns `true` for both predicates because (a) `TriageDecisionRecorded` does not carry `ruleId` in Phase 10, and (b) no VIP source-of-truth lookup is wired. Phase 10 FE (Plan 10) therefore must NOT render UI editors for these two predicate fields — leave them as schema-only (defaulted to `null` = "all"). Follow-on phase work: thread `ruleId` through `TriageDecisionRecorded` (extends Plan 01 record by one field), look up VIP via existing tenant VIP store inside the evaluator, then re-enable the two editors. The remaining two predicates (`classifications`, `quietHours`) are enforceable today and can ship UI editors as planned.
- **Snooze un-snooze worker (plan-checker W-5, 2026-05-28).** Plan 02 `MailActionService.snooze(...)` ships as label-only (`[Zero Mail] Snoozed` Gmail label + `snoozeUntil` stored in `outbound_action_audit.metadataJson`) — there is NO scheduled un-snooze worker in Phase 10. Effect: snoozed mail stays labeled until manually unsnoozed by the user. Follow-on phase work: add `SnoozeReleaseWorker` to `backend/worker` that scans `outbound_action_audit` for `action='snooze'` rows past their `snoozeUntil` and routes via `MailActionService.unsnooze(...)` to remove the label. SPEC TG-02 acceptance is technically met by the label add; un-snooze is a UX gap that is acceptable for v1.3 ship.
- **Wave 2 plans (10-05 → 10-06 → 10-07 → 10-08) are sequential, not parallel (plan-checker W-2, 2026-05-28).** All four plans carry `wave: 2` in their frontmatter, but their `depends_on:` chain is linear (each plan depends on the previous). The executor must process them in numeric order; no two of these four can run concurrently. Numbering was kept compact rather than expanding to waves 2/3/4/5 because the executor already serializes within a wave when the DAG demands it. Reviewers reading the wave field should NOT infer parallelism for Wave 2; consult `depends_on:` for the real DAG.

### Reviewed Todos (not folded)
None — `gsd-sdk query todo.match-phase 10` returned no matches; the two pending todos (`2026-04-28-wr-06-test-profile-securityconfig-slice`, `2026-05-21-optional-phase-08-e2e-smoke-real-gmail-vps`) are unrelated to messaging/Telegram.

</deferred>

---

*Phase: 10-telegram-messaging-assistant*
*Context gathered: 2026-05-28*
