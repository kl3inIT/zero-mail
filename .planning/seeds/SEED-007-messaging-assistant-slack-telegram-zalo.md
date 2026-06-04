---
id: SEED-007
status: ready-for-phase
planted: 2026-05-14
planted_during: Phase 6 launch-readiness discussion
upgraded: 2026-05-28
upgraded_during: post-Phase-9 product expansion discussion + Inbox Zero implementation comparison
trigger_when: "when v1.3 messaging-assistant phase is opened — promote this seed to a new phase via /gsd-phase add then run /gsd-spec-phase against this draft"
scope: medium
promotion_target: Phase 10 — Telegram Messaging Assistant
companion_seeds: SEED-005 (team workspace), SEED-008 (agentic workflows), SEED-016 (Bucket4j rate limiting)
---

# SEED-007: Messaging Assistant — Telegram First, Zalo Later

> **Status note (2026-05-28):** Upgraded from dormant umbrella ("Slack / Telegram / Zalo someday") to **draft SPEC ready for phase promotion**. The Slack/Teams branches are explicitly out of scope for v1.3 (different segment — B2B workspace). Zalo OA is deferred to a follow-on phase pending business registration. **This document is the input artifact for `/gsd-spec-phase`** — read it as "WHAT will be delivered" rather than "what could be considered."

## Why This Matters

Zero Mail's current product gap (validated against Superhuman + Inbox Zero comparison 2026-05-28): users have **AI rules + chat assistant** but no **"don't need to open laptop"** surface. Heavy email users (founders, sales, account managers) live in Telegram and want:

- Real-time notifications when AI rules fire on VIP senders → action without context-switching back to web
- Voice-of-AI replies drafted & confirmed from phone
- Daily digest + follow-up reminders delivered to chat instead of yet-another email
- DM-only safe surface (not another inbox to manage)

Inbox Zero already ships a working Telegram integration (`apps/web/utils/messaging/{providers,chat-sdk}/**` in their codebase, examined 2026-05-28). Their implementation validated the broad architecture; this seed documents the **net design** after harvesting their lessons + applying Zero Mail's stricter privacy posture (ARCH-02 body-content ban).

## Promotion Path

1. `/gsd-phase add` to insert "Phase 10 — Telegram Messaging Assistant" into ROADMAP.md after Phase 9 ships
2. Promote this draft to `.planning/phases/10-telegram-messaging-assistant/10-SPEC.md` via `/gsd-spec-phase` (which will ambiguity-score + lock requirements)
3. `/gsd-discuss-phase` → `/gsd-plan-phase` → `/gsd-execute-phase`
4. After v1.3 ships: open Zalo OA companion phase reusing `MessagingChannel` interface

---

# Draft SPEC — Phase 10: Telegram Messaging Assistant

## Goal

A user can connect their Telegram account to Zero Mail in 3 clicks from Settings → Connected Apps, then (a) receive real-time inline-button notifications when their AI rules fire on incoming Gmail, (b) chat with the same AI assistant from Telegram DM with full Spring AI streaming + tool-use, and (c) confirm send/reply/forward actions through preview cards that route through the existing v1.1 outbound send executor — with strict DM-only enforcement, cross-actor permission checks, deterministic action tokens, and the entire `core.messaging.telegram` module covered by ArchUnit body-ban tests that extend the current ARCH-02 boundary to messaging payloads. No raw Gmail email body, snippet, or token bytes may appear in any Telegram message, log, or callback payload.

## Background

**v1.0 + v1.1 shipped:** Gmail OAuth + ingest + rules engine + chat assistant + send executor + AES-GCM cipher + Spring Modulith event spine + Bucket4j rate limiting (per SEED-016). Code site `OutboundSendExecutor` (Phase 7) is the **single Gmail send call site**, enforced by the grep gate + ArchUnit `single_gmail_send_call_site` test.

**v1.2 shipping:** Admin console + Settings UI on curated catalog (Phases 8, 8.1, 9). Settings layout includes Connected Apps tab — Telegram card slots in here naturally.

**Inbox Zero reference (examined 2026-05-28):**
- Repo path: `E:/Project/inbox-zero` (local clone)
- Key files: `apps/web/app/api/telegram/events/route.ts`, `utils/messaging/{platforms,routes,chat-sdk}/**`, `utils/messaging/providers/telegram/**`, `utils/messaging/rule-notifications.ts`, `docs/telegram/setup.mdx`
- Their architecture: 1 global bot, webhook with `X-Telegram-Bot-Api-Secret-Token` header verify, signed link-code (stateless, no DB row), `MessagingChannel` + `MessagingRoute` tables, pending email confirm via `chat_message.parts` JSONB + sha256(actionType:chatMessageId:toolCallId) token, "More ▾" submenu for destructive notification actions, `bot.onAction([...RULE_NOTIFICATION_ACTION_IDS])` central handler, DM-only enforcement on all commands, `responseSurface: "messaging"` flag passed to same `aiProcessAssistantChat` used by web

**What we adopt from Inbox Zero:** stateless signed link code, deterministic pending-action token, cross-actor permission check, More-submenu for destructive actions, DM-only enforcement, `setMyCommands` registration script, reuse of the existing chat assistant pipeline with a surface flag.

**What we diverge on:**
- Pairing UX via Telegram **deep-link `t.me/<bot>?start=<code>`** (single START tap, zero typing) instead of copy-paste `/connect <code>`
- Use Spring Boot `RestClient` + Java records instead of heavyweight chat-SDK abstraction (we only need Telegram + Zalo, not Slack/Teams)
- Explicit `OutboundActionSource` enum into audit (`TELEGRAM_INLINE_BUTTON`, `TELEGRAM_CHAT_CONFIRMED`, `TELEGRAM_DEEPLINK_FROM_NOTIFICATION`) for forensic clarity

**Privacy boundary that must be enforced:** ARCH-02 currently bans extracted Gmail body content from `chat_message.parts`. This phase **extends** ARCH-02 to also ban it from any Telegram outbound payload (`sendMessage.text`, `editMessageText.text`, `inline_keyboard.callback_data`, notification rendering output) and from any field stored in `telegram_notification_log`. The carve-out for user-authored draft data (`sendEmail`/`replyEmail`/`forwardEmail` tool arguments) remains valid — those bodies are user-reviewed draft content, not Gmail-extracted content, and they MAY appear in `chat_message.parts` and in Telegram preview cards.

## Requirements

> Numbered TG-* for traceability. Each item: **Current** (existing state) → **Target** (post-phase state) → **Acceptance** (verifiable check).

### Transport & Identity

1. **TG-01: Bot token configured at deployment, single global bot.**
   - **Current:** No Telegram code. No env var.
   - **Target:** `TELEGRAM_BOT_TOKEN` + `TELEGRAM_WEBHOOK_SECRET` env vars consumed by `backend/api/src/main/resources/application.yml` and bound to `TelegramProperties` (`@ConfigurationProperties("zeromail.telegram")`). When `bot-token` is absent or blank, `/api/integrations/telegram/*` returns HTTP 503 `TELEGRAM_NOT_CONFIGURED` and the Settings → Connected Apps Telegram card renders disabled with tooltip "Telegram chưa được cấu hình trên server."
   - **Acceptance:** Integration test boots context with `TELEGRAM_BOT_TOKEN=""` → `GET /api/integrations/telegram/status` returns 503; with token set → returns 200 with `{configured: true, connected: false}`.

2. **TG-02: Webhook receiver with secret-token header verification.**
   - **Current:** No webhook controller for Telegram.
   - **Target:** `POST /webhooks/telegram/{urlSecret}` controller in `backend/api/controllers/integrations/TelegramWebhookController.java`. Two-layer verification: (a) `{urlSecret}` path variable must equal `TelegramProperties.urlSecret`; (b) `X-Telegram-Bot-Api-Secret-Token` request header must equal `TelegramProperties.webhookSecret`. Either mismatch → 401 + audit row `telegram.webhook.unauthorized` (rate-limited 10/min per source IP via Bucket4j). Body parsed as `TelegramUpdate` record; only `message`, `callback_query`, and `my_chat_member` update types accepted; others ignored with 200.
   - **Acceptance:** Integration test: POST with wrong URL secret → 401; POST with wrong header → 401; POST with both correct + valid update → 200 within 500ms; rate-limit triggers after 11 bad requests in 60s.

3. **TG-03: `setMyCommands` + bot metadata registered on application start.**
   - **Current:** No bot command registration.
   - **Target:** `TelegramBotInitializer` Spring `@Component` runs on `ApplicationReadyEvent`. On startup: calls `getMyCommands`, compares against expected list (`/start`, `/help`, `/pause`, `/digest`, `/unread`, `/disconnect`), calls `setMyCommands` only when different. Expected list versioned in code constant — change requires PR. Profile photo set once via `setMyProfilePhoto` if not already present (idempotent, matches Inbox Zero pattern).
   - **Acceptance:** Boot fresh container with empty bot → `getMyCommands` after startup returns the 6 commands; reboot with same code → no second `setMyCommands` API call (verified via WireMock).

4. **TG-04: Pairing via stateless signed link code (no DB pairing table).**
   - **Current:** No pairing infrastructure.
   - **Target:** `POST /api/integrations/telegram/pairing` (authenticated, current tenant) generates a signed payload `{type:"telegram-link", tenantId, nonce, issuedAt}` using `JwtEncoder` with HS256 + dedicated `messaging-link.secret` key (NOT shared with session secret). TTL 10 min. Response: `{deeplink: "https://t.me/<botUsername>?start=<signed-code>", qrPngBase64}`. No row written to DB at generation time. Verification on `/start <code>`: `JwtDecoder` validates signature + age + `tenantId` exists + `type=="telegram-link"`. Consume step: `INSERT INTO telegram_account ... ON CONFLICT DO UPDATE relinked_at=NOW()` atomic.
   - **Acceptance:** Generate code → wait 11 min → `/start <code>` fails with "Mã hết hạn"; generate code → `/start <code>` twice within 10 min → first succeeds, second returns "Đã kết nối từ trước" (idempotent); tamper with code (change 1 char) → fails signature verify.

5. **TG-05: `telegram_account` table + lifecycle states.**
   - **Current:** No schema.
   - **Target:** Liquibase YAML changelog creates `telegram_account` with columns: `id UUID PRIMARY KEY`, `tenant_id UUID NOT NULL UNIQUE REFERENCES user_account(id)`, `telegram_chat_id BIGINT NOT NULL UNIQUE`, `telegram_user_id BIGINT NOT NULL`, `telegram_username VARCHAR(64)`, `language_code VARCHAR(8)`, `status VARCHAR(20) NOT NULL CHECK (status IN ('CONNECTED','BLOCKED','DISCONNECTED'))`, `notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE`, `notification_filter JSONB NOT NULL DEFAULT '{}'::jsonb`, `linked_at TIMESTAMPTZ NOT NULL`, `last_active_at TIMESTAMPTZ`, `blocked_at TIMESTAMPTZ`, `disconnected_at TIMESTAMPTZ`. Indexes on `tenant_id`, `telegram_chat_id`, `(status, last_active_at)`. App DB user has SELECT + INSERT + UPDATE (status, last_active_at, blocked_at, disconnected_at, notification_filter, notifications_enabled); DELETE forbidden — disconnection sets `status='DISCONNECTED'` + `disconnected_at`, preserving audit history.
   - **Acceptance:** ArchUnit test: no class outside `core.messaging.telegram.persistence` references `TelegramAccountEntity`; integration test: cascade — when `user_account.deleted_at` set, post-commit listener flips related `telegram_account.status` to DISCONNECTED + emits `TelegramDisconnectedEvent`.

6. **TG-06: DM-only enforcement on every command + callback.**
   - **Current:** No commands exist.
   - **Target:** `TelegramUpdateRouter` rejects any inbound update where `chat.type != "private"` with reply "Vui lòng nhắn riêng với bot — các lệnh không hoạt động trong nhóm." Applies to ALL commands (`/start`, `/help`, `/pause`, `/digest`, `/unread`, `/disconnect`) and to free-text messages. Callback_query routed only when originating chat is `private` AND the click actor's `from.id` matches the `telegram_user_id` on the resolved `telegram_account` row.
   - **Acceptance:** Integration test: bot added to group → `/start <code>` in group → bot replies DM-required message + does NOT consume pairing code; callback_query forwarded by user A to user B (different telegram user) who clicks Send → backend returns "Bạn không có quyền xác nhận draft này" + audit row `telegram.callback.unauthorized_actor`.

### Outbound — Notifications

7. **TG-07: Rule-fire notification with privacy-bounded payload.**
   - **Current:** Rule fire emits `TriageDecisionRecorded` Spring Modulith event; no Telegram listener.
   - **Target:** New `TelegramNotificationListener` `@TransactionalEventListener(phase=AFTER_COMMIT)` for `TriageDecisionRecorded`. Renders payload from header + classification + action metadata ONLY: `sender_display_name`, `sender_domain`, `subject_truncated_80_chars`, `classification`, `action_taken`, `gmail_message_id`. Never includes `body`, `snippet`, `messageHtml`, `prompt`, `completion`, `token`. Outbound flow: `MessagingNotificationOutboxRepository.enqueue` → `TelegramNotificationSenderWorker` (Postgres SKIP LOCKED, same pattern as v1.0 outbox) drains with per-chat Bucket4j throttle 1 msg/s, global throttle 30 msg/s; on Telegram 429 response, reads `retry_after`, reschedules.
   - **Acceptance:** `ArchUnit TelegramPathBodyBanTest`: `core.messaging.telegram.notification.*` cannot reference `GmailMessageBody`, `MessageContent`, `EmailBodyRepository`, fields matching `body|bodyHtml|snippet|messageHtml|content` (with the user-authored draft carve-out exempt only inside `chat.preview` package, validated by package-level allowlist). Integration test with rule firing on 50 messages → all 50 notifications sent within 30s, none contains body, all `telegram_notification_log` rows have `gmail_message_id` set + zero rows with `body_excerpt` column (column doesn't exist — schema enforcement).

8. **TG-08: Inline keyboard with primary actions + More submenu for destructive.**
   - **Current:** N/A.
   - **Target:** Notification message inline_keyboard row 1: `[💬 Reply]` `[📥 Archive]` `[🔗 Open]`. Row 2: `[⋯ More]`. Tapping `[⋯ More]` posts a follow-up message (NOT modal — Telegram bots lack modal API) with row 1: `[😴 Snooze 1h]` `[😴 Until tomorrow]`. Row 2: `[🚫 Mark spam]` `[🗑 Trash]`. Destructive (`Mark spam`, `Trash`) require a second tap that edits the message to "Xác nhận xoá? [✅ Xác nhận] [❌ Huỷ]" before executing. The primary message keeps the same `gmail_message_id` reference; expansion message references parent via `reply_to_message_id`.
   - **Acceptance:** Integration test: user taps `[⋯ More]` → second message posted within 1s → user taps `[🗑 Trash]` → confirm prompt appears → user taps `[✅ Xác nhận]` → backend calls existing trash service via outbound gateway → original notification edited to "🗑 Đã xoá lúc HH:mm".

9. **TG-09: Callback verification via deterministic token + cross-actor permission check.**
   - **Current:** N/A.
   - **Target:** `callback_data` format `<actionId>:<resourceId>:<token16>` where `token16 = sha256(actionId + ":" + tenantId + ":" + resourceId + ":" + messageSentEpochMinute).hex.substring(0,16)`. Resolve: parse → lookup `telegram_account by chat_id` → derive expected token → constant-time compare. Cross-actor check: `callback_query.from.id == telegram_account.telegram_user_id` (rejects forwarded button taps). Idempotency: `telegram_notification_log.acted_on_at` set atomically; double-tap returns "Đã xử lý" without re-execution.
   - **Acceptance:** Integration test: tamper with `token16` → returns "Action không hợp lệ"; tamper with `resourceId` → returns "Action không hợp lệ"; replay same callback twice → second returns "Đã xử lý" + service not re-invoked (verified via Mockito `verify(times(1))` on outbound gateway).

10. **TG-10: All outbound Gmail writes route through existing `OutboundSendExecutor`.**
    - **Current:** Outbound gateway exists (Phase 7); single Gmail send call site invariant enforced via grep gate + ArchUnit `single_gmail_send_call_site`.
    - **Target:** Telegram handlers call existing services: `MailActionService.archive`, `MailActionService.markSpam`, `MailActionService.trash`, `MailActionService.markRead`, `MailActionService.snooze`, `DraftService.saveDraft`, `OutboundSendExecutor.send`. Each call carries `source` parameter from new `OutboundActionSource` enum extended with: `TELEGRAM_INLINE_BUTTON`, `TELEGRAM_INLINE_BUTTON_DESTRUCTIVE_CONFIRMED`, `TELEGRAM_CHAT_CONFIRMED`, `TELEGRAM_DEEPLINK_FROM_NOTIFICATION`. NO new Gmail send call site is introduced; the grep gate still passes with exactly 1 match.
    - **Acceptance:** Grep gate CI step still asserts exactly 1 match for `gmailClient\.users\(\)\.messages\(\)\.send` repository-wide. ArchUnit `single_gmail_send_call_site` test green. `audit_outbound_action` rows from a Telegram-confirmed send have `source='TELEGRAM_CHAT_CONFIRMED'`.

### Inbound — Commands & Chat

11. **TG-11: Slash-command surface.**
    - **Current:** N/A.
    - **Target:** `/start <code>` triggers pairing (TG-04). `/help` returns Vietnamese help text listing available commands + brief privacy note. `/pause [duration]` accepts optional `1h|4h|today|tomorrow` (default `4h`) and calls existing `MailPauseService.pause(tenantId, duration)`; replies with confirmation. `/digest` triggers on-demand digest generation (`DigestService.generateNow`) and returns Telegram-formatted digest content in chat (digest text contains only metadata + counts, no bodies). `/unread` returns count + sender/subject metadata of top 5 unread. `/disconnect` flips `telegram_account.status='DISCONNECTED'` + emits `TelegramDisconnectedEvent` + replies "Đã ngắt kết nối. Mở Zero Mail web để kết nối lại bất cứ lúc nào."
    - **Acceptance:** Each command has 1 integration test verifying happy-path; `/pause 7h` (out of allowed set) returns "Khoảng thời gian không hợp lệ" without invoking pause service.

12. **TG-12: Free-text chat assistant via existing pipeline with streaming.**
    - **Current:** `ChatAssistantService.streamReply` exists (Phase 7); used by web `/chat` endpoint.
    - **Target:** Any free-text message in Telegram DM (not matching a slash command) is forwarded to `ChatAssistantService.streamReply(tenantId, sessionId, userMessage, surface=TELEGRAM)`. The surface enum is added to the service. Streaming uses Spring AI `StreamingChatModel.stream(...)` per CLAUDE.md "no fallback" rule. Telegram delivery: bot posts placeholder message "✍️ Đang viết...", then `editMessageText` every 1s (Reactor `bufferTimeout(80 chunks, Duration.ofMillis(1000))`) with accumulated text, final edit clears the indicator. On `429 Too Many Requests` from Telegram during edit, falls back to single final-message post (NOT a streaming-disable fallback to LLM — only a Telegram-edit-rate-limit fallback).
    - **Acceptance:** Integration test with WireMock OpenAI streaming endpoint emitting 20 chunks over 5s → Telegram receives ≥3 `editMessageText` calls + 1 final edit removing typing indicator; ArchUnit assertion: `core.messaging.telegram.chat.*` does not import or reference any non-streaming chat call path.

13. **TG-13: Pending email confirmation via `chat_message.parts` + deterministic token.**
    - **Current:** v1.1 chat preview card uses `chat_message.parts` JSONB with tool-output `confirmationState: "pending"` and a server-rendered preview; web confirm goes through `confirmAssistantEmailActionForAccount`.
    - **Target:** Reuse the same `chat_message.parts` storage — Telegram simply renders the pending tool-part as a Telegram message + inline_keyboard `[✅ Gửi]` `[📝 Sửa]` `[💾 Lưu nháp]` `[❌ Huỷ]`. The `[✅ Gửi]` callback_data carries `token16 = sha256(actionType + ":" + chatMessageId + ":" + toolCallId).substring(0,16)`. On callback: server iterates last 50 assistant `chat_message.parts` entries for the chat, recomputes token, matches → calls existing `OutboundSendExecutor` with `source=TELEGRAM_CHAT_CONFIRMED`. NO new `assistant_pending_action` table is introduced.
    - **Acceptance:** Test: AI in Telegram chat generates draft → preview card posted → user taps `[✅ Gửi]` → existing `confirmAssistantEmailActionForAccount` is invoked (Mockito verify) → `audit_outbound_action` row has `source='TELEGRAM_CHAT_CONFIRMED'`; user taps `[✅ Gửi]` again → second tap returns "Đã gửi rồi" + service not re-invoked.

### Settings UI

14. **TG-14: Settings → Connected Apps → Telegram card.**
    - **Current:** Settings UI (Phase 9) has Personalization / Behavior / Safety Net / AI Provider/Model tabs. No Connected Apps tab.
    - **Target:** New 5th tab "Connected Apps". Telegram card shows: (a) disconnected state — Connect button + Vietnamese explainer; (b) connected state — `@username · linked HH:mm DD/MM/YYYY` + Notification filter editor (`notifications_enabled` toggle + per-rule allow-list multi-select + sender VIP-only toggle + classification multi-select) + Disconnect button (confirm-once dialog). Connect button opens modal containing QR code + `[Open Telegram]` deep-link button + 8-minute countdown of code TTL (display 8 min though backend TTL is 10 min, providing buffer). Modal polls `GET /api/integrations/telegram/status` every 2s for up to 10 min; on `connected: true` closes modal + refetches parent. Frontend lives at `apps/web/app/(dashboard)/settings/connected-apps/page.tsx` with feature module `apps/web/features/telegram-integration/`.
    - **Acceptance:** Playwright e2e: starting at /settings/connected-apps disconnected → click Connect → modal opens with QR + countdown → simulate webhook `/start <code>` via test helper → modal closes within 4s → tab now shows connected state with username; click Disconnect → confirm dialog → tab returns to disconnected state.

15. **TG-15: Audit + observability.**
    - **Current:** v1.2 `admin_audit_event` exists for admin actions; `audit_outbound_action` exists per Phase 7 for Gmail writes.
    - **Target:** Tenant-facing telegram events (link, unlink, blocked-by-user, command-invocation summary) write to existing `triage_audit`-style table extended with `event_kind='TELEGRAM_*'`. Logged metrics: counter `telegram.notifications.sent.total{tenant_id}`, counter `telegram.callback.received.total{action_id}`, histogram `telegram.api.latency.seconds{method}`, counter `telegram.errors.total{kind}`. Log format follows project standard: `event=telegram.notification.sent tenantId={} sender_domain={} action_taken={}` — never contains body, subject, full sender email, prompt, completion, callback token.
    - **Acceptance:** Privacy sweep test `TelegramPrivacySweepTest` (sibling of `TriagePrivacySweepTest` from Phase 1) runs a fixture: pairing + 10 notifications + 5 callbacks + 3 chat turns + 1 send confirmation. Captures all log output via Logback test appender. Asserts regex `[a-z0-9._+-]+@[a-z0-9.-]+\.[a-z]{2,}` never appears unmasked; asserts `body`, `snippet`, `prompt`, `completion` substring never appears in log lines from `core.messaging.telegram.*`.

## Boundaries

**In scope:**
- New Spring Modulith module `core.messaging.telegram` with subpackages `domain/`, `application/`, `persistence/`, `gateway/`, `notification/`, `chat/`, `webhook/`
- New tables: `telegram_account`, `telegram_notification_log`, `messaging_notification_outbox` (or extension of existing outbox with `channel='TELEGRAM'`)
- Webhook controller in `backend/api/controllers/integrations/`
- Settings UI tab + feature module in `apps/web`
- ArchUnit body-ban extension covering all Telegram packages
- Privacy sweep test for Telegram surface
- Extension of `OutboundActionSource` enum with 4 Telegram source values
- `MessagingChannel` interface in `core.messaging.api` with Telegram as the first implementation (Zalo to follow in a separate phase)
- Bot init script using existing Gradle task pattern
- Bucket4j config for Telegram per-chat + global rate limits

**Out of scope:**
- Zalo OA integration (separate follow-on phase; requires business registration; `MessagingChannel` interface designed to accommodate but no Zalo code in this phase)
- Slack / Teams (different segment; not on v1.x roadmap)
- Group chat support (DM-only enforcement is mandatory)
- Multi-account `/switch` command (Zero Mail is currently 1 tenant per Google account; deferred until multi-account is itself a phase)
- Voice / audio messages (text only)
- File attachments inbound (Telegram → email) — rejected with "Đính kèm file chưa hỗ trợ"
- Bot installation as inline-mode bot (no `@ZeroMailBot search...` from other chats)
- Persistence of free-text Telegram chat outside `chat_message.parts` (chat history reuses existing v1.1 schema)
- Embedding / vector index of chat content (privacy posture forbids)
- Real-time presence / typing-status from user → bot (Telegram does not expose to bots in DMs)
- Custom bot username per tenant (one global `@ZeroMailBot`)
- "Login with Telegram" as an alternative auth path (Google OAuth remains the only sign-in)
- Cross-process Spring events (the `TriageDecisionRecorded` event is in-process in `backend/api` AND `backend/worker`; if rule fires in worker, the outbox row written by worker is drained by the worker → Telegram API; no event crosses API↔worker boundary)

## Constraints

- **Privacy ARCH-02 extension:** ArchUnit body-ban regex extends to Telegram payload classes. No Gmail-extracted body content in `sendMessage.text`, `editMessageText.text`, `callback_data`, `telegram_notification_log` columns, or any log line from `core.messaging.telegram.*`. User-authored draft data in `chat_message.parts` remains the only exception, and only when rendered via `chat.preview` package.
- **Single send call site:** Telegram MUST NOT introduce a new Gmail send call site. All sends route through `OutboundSendExecutor`. Grep gate stays at exactly 1 match.
- **Streaming-only chat:** Free-text chat replies use `StreamingChatModel.stream(...)`. No non-streaming fallback at the LLM layer. Telegram-edit-rate-limit fallback to single-message post is allowed and is a Telegram transport concern only.
- **One global bot, never per-tenant:** Single `TELEGRAM_BOT_TOKEN`. Tenant differentiation via `chat_id` lookup. Industry pattern.
- **Stateless link code:** Pairing code is HMAC-signed JWT-like with 10-min TTL. No `telegram_pairing` table.
- **Deterministic action token:** No HMAC nonce table. Token = sha256(actionId:tenantId:resourceId:messageEpochMinute) truncated 16 chars. Combined with cross-actor permission check.
- **DM-only:** Group chat usage rejected at router. No exception.
- **Cross-actor permission check mandatory:** `callback_query.from.id == telegram_account.telegram_user_id` on every callback. Forwarded button taps are rejected.
- **Per-chat rate limit 1 msg/s, global 30 msg/s:** Bucket4j tokens; Telegram 429 honored with `retry_after`.
- **Spring Modulith boundary:** `core.messaging.telegram` may depend on `core.gmail` (read), `core.triage` (events + audit), `core.chat` (assistant pipeline), `core.outbound` (send executor), `core.rules`. Does NOT depend on `backend/api` packages. Cross-process: the worker process imports the same `core.messaging.telegram` module and uses the same outbox pattern; no in-process events cross the API↔worker boundary.
- **Spring Boot 4 / Java 25 / Gradle 9:** Per project lock. No Lombok. Records for DTOs. Virtual threads enabled.
- **No 3rd-party Telegram SDK:** RestClient + Java records. No `rubenlagus/TelegramBots`. No chat-SDK abstraction.

## Acceptance Criteria

- [ ] `TELEGRAM_BOT_TOKEN` env missing → `/api/integrations/telegram/status` returns 503; Settings tab shows disabled state
- [ ] Webhook URL secret + header secret double-verify; either mismatch → 401 + audit + Bucket4j rate limit on bad source IP
- [ ] `setMyCommands` runs once on startup, idempotent on reboot (no duplicate API call)
- [ ] Pairing via signed JWT-like code, 10-min TTL, single-use idempotent consume
- [ ] `telegram_account` table created via Liquibase; DELETE forbidden via DB grants; `user_account` deletion cascades to `status='DISCONNECTED'`
- [ ] DM-only enforced on all commands + callbacks; group chat usage rejected
- [ ] Rule-fire notification sent within 30s of `TriageDecisionRecorded` AFTER_COMMIT phase; payload contains NO body / snippet / prompt
- [ ] Inline keyboard: 3 primary actions + More submenu; destructive (Trash, Mark spam) requires confirm-twice
- [ ] Callback verified via deterministic token + cross-actor permission check; tamper/replay rejected
- [ ] Every Gmail write from Telegram routes through `OutboundSendExecutor` with `source=TELEGRAM_*`; grep gate stays at 1 send call site
- [ ] 6 slash commands implemented: `/start`, `/help`, `/pause`, `/digest`, `/unread`, `/disconnect`
- [ ] Free-text chat uses `StreamingChatModel.stream(...)` with Reactor 1s buffer → `editMessageText` cadence
- [ ] Pending email confirm reuses `chat_message.parts` storage; deterministic 16-char token lookup; no new pending-action table
- [ ] Settings → Connected Apps → Telegram card with Connect modal (QR + deep-link + countdown), Disconnect, Filter editor
- [ ] `TelegramPathBodyBanTest` ArchUnit test green
- [ ] `TelegramPrivacySweepTest` green — no body / snippet / email regex / prompt in any log line from `core.messaging.telegram.*`
- [ ] Bucket4j rate limits enforced: per-chat 1/s, global 30/s, Telegram 429 honored with `retry_after`
- [ ] Playwright e2e: full Connect → notify → confirm-send flow passes in < 30s
- [ ] Documentation: `docs/integrations/telegram-setup.md` covering BotFather setup, env vars, webhook config, troubleshooting

## Open Questions for `/gsd-discuss-phase`

1. **Notification dedup window:** if a rule fires on the same `gmail_message_id` twice (rare; via re-ingest), should the second notification be suppressed (idempotent on `tenant_id + gmail_message_id`) or sent as an update edit? Inbox Zero suppresses. Recommendation: suppress.

2. **Daily digest delivery time:** TG-11 `/digest` is on-demand. Should the v1.0 ANL-03 daily-digest cron also push to Telegram automatically? Decision affects scope. Recommendation: NO in v1.3 (user can `/digest` on demand); reconsider in v1.4 as part of a "subscriptions" expansion.

3. **Follow-up reminders coupling:** Phase 9 / future v1.3 may introduce Reply Tracker. If Reply Tracker ships first, this phase should consume its events for "you haven't replied to X" notifications. If this phase ships first, defer the event hook to a follow-on phase. Recommendation: keep this phase independent; consume Reply Tracker events in a small follow-on if it ships first.

4. **`/disconnect` aggressiveness:** should `/disconnect` from Telegram side also cascade-clear notification filter? Recommendation: NO — preserve `notification_filter` so user reconnecting later doesn't lose settings.

5. **Bot username collision:** `@ZeroMailBot` may already be taken on Telegram. Need to register early. Recommendation: register `@ZeroMailAssistantBot` or similar as fallback during phase kick-off; document chosen name in `TelegramProperties`.

6. **Worker process: which one drains `messaging_notification_outbox`?** `backend/api` only, `backend/worker` only, or both? Recommendation: `backend/worker` only (avoid API-process drain stealing under load); needs verification during plan-phase.

## Effort Estimate

| Component | Plans | Wave |
|-----------|-------|------|
| Foundation: schema, webhook receiver, secret verify, init bean | 10-A | 1 |
| Pairing flow + Settings UI Connect card | 10-B | 1 (parallel with 10-A on backend, depends on 10-A for FE wiring) |
| Outbound notification pipeline + inline keyboard + More submenu | 10-C | 2 (depends 10-A) |
| Inbound commands + DM-only enforcement | 10-D | 2 (depends 10-A) |
| Free-text chat streaming + pending email confirm reuse | 10-E | 2 (depends 10-A, 10-C) |
| Privacy sweep test + ArchUnit body-ban extension | 10-F | 3 (depends all) |
| Documentation + Playwright e2e | 10-G | 3 (depends all) |

Estimated ~5 weeks solo dev. Confidence: MEDIUM (Inbox Zero implementation removed most unknowns; main risks are Spring AI streaming → Reactor → Telegram edit rate limit interaction, and the privacy sweep test surfacing leaks we didn't anticipate).

---

## Source Material

- Inbox Zero local clone examined 2026-05-28: `E:/Project/inbox-zero/apps/web/utils/messaging/**`, `E:/Project/inbox-zero/docs/telegram/setup.mdx`
- Zero Mail discussion 2026-05-28: feature comparison vs Superhuman/Inbox Zero, Telegram architecture deep-dive, binding flow UX, "one bot vs many" decision, Inbox Zero implementation comparison
- CLAUDE.md privacy constraints (ARCH-02 body-content ban, draft-body carve-out, single Gmail send call site, streaming-only chat, no Lombok, no WebFlux)
- Phase 7 (v1.1 chat assistant) — pipeline reused
- Phase 8 (v1.2 admin console) — OutboundActionSource enum + audit row pattern reused
- Phase 9 (v1.2 user settings) — Settings tab layout extended with 5th tab
- SEED-016 (Bucket4j rate limiting) — referenced for per-chat + global throttle implementation
