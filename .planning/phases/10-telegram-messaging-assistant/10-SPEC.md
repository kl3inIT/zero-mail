# Phase 10: Telegram Messaging Assistant — Specification

**Created:** 2026-05-28
**Ambiguity score:** 0.102 (gate: ≤ 0.20)
**Requirements:** 19 locked
**Source seed:** `.planning/seeds/SEED-007-messaging-assistant-slack-telegram-zalo.md` (upgraded 2026-05-28)

## Goal

A Zero Mail user can connect their Telegram account from `/settings/connected-apps` in 3 clicks, then (a) receive real-time inline-button notifications when AI rules fire on incoming Gmail, (b) chat with the same AI assistant from Telegram DM with Spring AI streaming + tool-use, and (c) confirm send/reply/forward actions through preview cards routed through the existing v1.1 outbound send executor — with strict DM-only enforcement, cross-actor permission checks, deterministic action tokens, and the entire `core.messaging.telegram` Spring Modulith module covered by ArchUnit body-ban tests that extend ARCH-02 to messaging payloads. No raw Gmail email body, snippet, or token bytes may appear in any Telegram message, log, or callback payload.

## Background

**v1.0 + v1.1 shipped** Gmail OAuth + Pub/Sub ingest + rules engine + chat assistant + outbound send gateway + AES-GCM `RefreshTokenCipher` + Spring Modulith event spine. **v1.2 shipped/shipping** Admin console + Settings UI on curated catalog (Phases 8, 8.1, 9).

**Codebase scout findings vs SEED-007 (2026-05-28):**

| SEED assumption | Reality | Resolution (locked in this SPEC) |
|---|---|---|
| `TriageDecisionRecorded` Spring event already published | Only `MailMessageObserved` exists; triage runs synchronously inside `TriageOrchestratorService.onMailMessageObserved`. No "rule fired" event. | TG-01: Phase 10 introduces `TriageDecisionRecorded` event published from `TriageOrchestratorService` after action applied. |
| `OutboundActionSource` enum + `audit_outbound_action.source` column exist | Only `OutboundSendCommand(tenantId, gmailMessage)` exists. No source enum, no audit column. | TG-03: Phase 10 introduces `OutboundActionSource` enum + audit source field. |
| `MailActionService.archive/markRead/snooze/markSpam/trash` exists | Only `TriageGmailWriter` (internal to `core.triage`). No shared facade. | TG-02: Phase 10 introduces `MailActionService` facade in `core.mailaction.usecases`. |
| `DigestService.generateNow` + `MailPauseService.pause(duration)` exist | Neither exists. `TenantService.setTriagePaused(boolean)` has no duration. | TG-15: `/digest` `/pause` `/unread` deferred. Phase 10 ships `/start` `/help` `/disconnect` only. |
| Settings UI is tabs (SEED: "5th tab Connected Apps") | `SettingsClient.tsx` is single-page card grid; touching it would conflict with Phase 9. | TG-18: New `/settings/connected-apps` sub-route; main Settings unchanged. |
| Bucket4j on classpath | Only in `SEED-016` (dormant). | TG-04: Phase 10 adopts Bucket4j as new dep + promotes SEED-016 to closed. |

**Reference repo (architecture only, no code port):** Inbox Zero (`E:/Project/inbox-zero/apps/web/utils/messaging/**`) validated the broad design (1 global bot, stateless signed link code, deterministic pending-action token, More-submenu for destructive notification actions, `responseSurface` flag on the shared chat pipeline). Zero Mail diverges via deep-link `/start <code>` pairing, Java records + Spring `RestClient` instead of chat-SDK, and an explicit typed `OutboundActionSource` for forensic clarity.

**Privacy boundary extended in this phase:** ARCH-02 currently bans extracted Gmail body content from `chat_message.parts`. Phase 10 **extends** ARCH-02 to also ban it from any Telegram outbound payload (`sendMessage.text`, `editMessageText.text`, `inline_keyboard.callback_data`, notification rendering output) and from `telegram_notification_log`. The carve-out for user-authored draft data in `chat_message.parts` (`sendEmail`/`replyEmail`/`forwardEmail` tool arguments) remains valid — those bodies are user-reviewed draft content, not Gmail-extracted content.

## Requirements

> Numbered TG-* for traceability. Each entry: **Current** (existing state) → **Target** (post-phase state) → **Acceptance** (verifiable check).

### Foundation (prerequisites surfaced by codebase scout)

1. **TG-01: `TriageDecisionRecorded` Spring Modulith event published from triage flow.**
   - **Current:** `TriageOrchestratorService.onMailMessageObserved` runs triage synchronously inside the listener. No "rule fired / triage decided" event is published. Only `MailMessageObserved` exists.
   - **Target:** New record `TriageDecisionRecorded(UUID tenantId, String gmailMessageId, String gmailThreadId, String classification, String actionTaken, String senderDomain, String senderDisplayName, String subjectTruncated, Instant decidedAt)` in `core.triage.domain`. Published by `TriageOrchestratorService` via `ApplicationEventPublisher` after each rule action is applied (inside the same `tenantScopedOrchestrationTransaction`). Payload contains **only** header + classification + action metadata — never `body`, `snippet`, `messageHtml`, `prompt`, `completion`, or `token`. Modulith `package-info` of `core.triage` re-exposes the new record via `@NamedInterface` so messaging/other modules can subscribe.
   - **Acceptance:** Integration test: inject a `TestTriageDecisionRecordedListener` `@TransactionalEventListener(AFTER_COMMIT)` bean; trigger triage on a fixture message; listener receives exactly 1 event with non-null `gmailMessageId` + `classification` + `actionTaken`; ArchUnit assertion: `TriageDecisionRecorded` record class has zero fields matching regex `body|bodyHtml|snippet|messageHtml|content|prompt|completion|token`.

2. **TG-02: `MailActionService` facade for non-send Gmail actions.**
   - **Current:** Non-send Gmail actions (archive, markRead, snooze, markSpam, trash) live inside `TriageGmailWriter` (`core.triage.usecases`) which is internal to triage flow. No shared facade callable from messaging / chat / future REST endpoints.
   - **Target:** New `MailActionService` interface + default implementation in `core.mailaction.usecases`, exposing: `archive(tenantId, gmailMessageId, source)`, `markRead(tenantId, gmailMessageId, source)`, `markSpam(tenantId, gmailMessageId, source)`, `trash(tenantId, gmailMessageId, source)`, `snooze(tenantId, gmailMessageId, snoozeUntil, source)`. Each method writes an audit row with `OutboundActionSource source` (TG-03) and routes Gmail mutation through existing `GmailApiClient` (NOT a new Gmail call site). `TriageGmailWriter` is refactored to delegate to `MailActionService` (preserving its existing behavior + audit shape) so there is a single execution surface for these 5 actions. New Modulith module `core.mailaction` with `allowedDependencies = {gmail, gmail :: gateway, tenant, shared :: persistence, shared :: lang}`.
   - **Acceptance:** ArchUnit test: no class outside `core.mailaction.usecases` calls `GmailApiClient.users().messages().modify(...)` or `.trash(...)` directly; Mockito unit test: `MailActionService.archive(...)` calls `GmailApiClient` once + writes 1 audit row with the passed `source`; existing triage tests still green after refactor (zero regression).

3. **TG-03: `OutboundActionSource` enum + audit source field.**
   - **Current:** `OutboundSendCommand` carries `tenantId + gmailMessage` only. No source tracking. No `audit_outbound_action.source` column.
   - **Target:** New enum `OutboundActionSource` in `core.outbound.domain` implementing `IdentifiedEnum`: `RULE_AUTO`, `WEB_CHAT_CONFIRMED`, `TELEGRAM_INLINE_BUTTON`, `TELEGRAM_INLINE_BUTTON_DESTRUCTIVE_CONFIRMED`, `TELEGRAM_CHAT_CONFIRMED`, `TELEGRAM_DEEPLINK_FROM_NOTIFICATION`, `WEB_LEGACY` (backfill sentinel for pre-Phase-10 audit rows). Existing `OutboundSendCommand` and `MailActionService` (TG-02) signatures gain a non-null `OutboundActionSource source` parameter. `triage_audit` (or a sibling `outbound_action_audit` table — final shape decided in discuss-phase) gains `source VARCHAR(64) NOT NULL` column with a CHECK constraint listing the enum values; pre-existing rows backfilled to `WEB_LEGACY`. ArchUnit: every call to `OutboundSendGateway.send(...)` or any `MailActionService` method passes a literal enum value (no `null`, no string).
   - **Acceptance:** Liquibase changeset adds `source` column + CHECK + backfill; integration test: trigger a triage rule-auto send → audit row has `source='RULE_AUTO'`; trigger a web chat confirm-send → `source='WEB_CHAT_CONFIRMED'`; ArchUnit test `OutboundActionSourceArchTest` confirms all 7 enum values are referenced from at least one production call site OR are flagged as `@Reserved` (forward-compat).

4. **TG-04: Bucket4j adoption for per-chat + global Telegram throttle.**
   - **Current:** No rate-limit library on classpath. `SEED-016-bucket4j-rate-limiting-evaluation.md` evaluated but dormant.
   - **Target:** Add `bucket4j-core` (latest stable compatible with Java 25 + Spring Boot 4) to `libs.versions.toml`. Implement `TelegramSendRateLimiter` (in `core.messaging.telegram.gateway`) with two in-memory `Bucket` instances: per-chat bucket (1 token/s, capacity 1, 1 per `telegram_chat_id`), and global bucket (30 tokens/s, capacity 30, single shared). All outbound calls from `TelegramApiClient` (sendMessage / editMessageText / answerCallbackQuery) acquire both tokens before HTTP. On Telegram `429 Too Many Requests`, reschedule with `retry_after` seconds via the outbox row. SEED-016 status flipped to `closed-by-phase-10`.
   - **Acceptance:** Integration test: enqueue 50 notifications for 50 distinct chat IDs within 100ms → exactly 30 are sent in the first 1s window, remaining are deferred (Mockito verify call counts at `TelegramApiClient.sendMessage`); enqueue 5 notifications to the same chat ID within 100ms → exactly 1 sent in first 1s, 4 deferred ≥1s.

### Transport & Identity

5. **TG-05: Bot token configured at deployment, single global bot.**
   - **Current:** No Telegram code. No env var.
   - **Target:** `TELEGRAM_BOT_TOKEN` + `TELEGRAM_WEBHOOK_SECRET` + `TELEGRAM_URL_SECRET` + `TELEGRAM_MESSAGING_LINK_SECRET` env vars consumed by `backend/api/src/main/resources/application.yml` and `backend/worker/src/main/resources/application.yml` bound to `TelegramProperties` (`@ConfigurationProperties("zero-mail.messaging.telegram")`). When `bot-token` is absent or blank, `/api/integrations/telegram/*` returns HTTP 503 with error code `TELEGRAM_NOT_CONFIGURED`; the `/settings/connected-apps` Telegram card renders disabled with localized tooltip "Telegram chưa được cấu hình trên server."
   - **Acceptance:** Integration test boots context with `TELEGRAM_BOT_TOKEN=""` → `GET /api/integrations/telegram/status` returns 503 + JSON `{ code: "TELEGRAM_NOT_CONFIGURED" }`; with all 4 secrets set → returns 200 with `{ configured: true, connected: false }`.

6. **TG-06: Webhook receiver with double-secret verification.**
   - **Current:** No Telegram webhook controller.
   - **Target:** `POST /webhooks/telegram/{urlSecret}` controller in `backend/api/controllers/integrations/TelegramWebhookController`. Two-layer verification: (a) `{urlSecret}` path variable must equal `TelegramProperties.urlSecret`; (b) `X-Telegram-Bot-Api-Secret-Token` header must equal `TelegramProperties.webhookSecret`. Either mismatch → 401 + audit row `telegram.webhook.unauthorized` (rate-limited 10/min per source IP via Bucket4j from TG-04). Body parsed as `TelegramUpdate` record; only `message`, `callback_query`, `my_chat_member` update types accepted; others return 200 + ignored. Controller is excluded from the user-session SecurityFilterChain via dedicated `@Order(1)` `TelegramWebhookSecurityConfig`.
   - **Acceptance:** Integration test: POST with wrong URL secret → 401; POST with wrong header → 401; POST with both correct + valid update → 200 within 500ms; ≥11 bad requests in 60s from one source IP triggers Bucket4j throttle (12th returns 429).

7. **TG-07: `setMyCommands` + bot metadata registered on startup, idempotent.**
   - **Current:** No bot command registration.
   - **Target:** `TelegramBotInitializer` `@Component` runs on `ApplicationReadyEvent`. Calls `getMyCommands`, compares against expected list (locked in TG-15: `/start`, `/help`, `/disconnect`), calls `setMyCommands` only when different. Expected list is a versioned constant in code — changing it requires a PR. Profile photo set once via `setMyProfilePhoto` if not already present.
   - **Acceptance:** Boot fresh container with empty bot via WireMock fixture → `getMyCommands` after startup returns the 3 commands; reboot with same code → zero second `setMyCommands` API calls (WireMock verify count = 1 across both boots when restarted with persistent mock state).

8. **TG-08: Pairing via stateless signed link code.**
   - **Current:** No pairing infrastructure.
   - **Target:** `POST /api/integrations/telegram/pairing` (authenticated, current tenant) generates a signed payload `{ type: "telegram-link", tenantId, nonce, issuedAt }` using `JwtEncoder` HS256 with dedicated `TelegramProperties.messagingLinkSecret` (NOT shared with session/refresh-token secrets). TTL 10 minutes. Response: `{ deeplink: "https://t.me/<botUsername>?start=<signed-code>", qrPngBase64, expiresAt }`. No DB row at generation time. On `/start <code>`: `JwtDecoder` validates signature + age + `type=="telegram-link"` + `tenantId` exists in `user_account`. Consume step uses `INSERT INTO telegram_account ... ON CONFLICT (tenant_id) DO UPDATE relinked_at = NOW(), status = 'CONNECTED'` atomically.
   - **Acceptance:** Generate code → wait 11 min → `/start <code>` fails with "Mã hết hạn"; generate code → `/start <code>` twice within 10 min → first succeeds, second returns "Đã kết nối từ trước" (idempotent); tamper code (change 1 char) → signature verify fails before DB lookup.

9. **TG-09: `telegram_account` table + lifecycle states.**
   - **Current:** No schema.
   - **Target:** Liquibase YAML changeset creates `telegram_account` with columns: `id UUID PRIMARY KEY`, `tenant_id UUID NOT NULL UNIQUE REFERENCES user_account(id)`, `telegram_chat_id BIGINT NOT NULL UNIQUE`, `telegram_user_id BIGINT NOT NULL`, `telegram_username VARCHAR(64)`, `language_code VARCHAR(8)`, `status VARCHAR(20) NOT NULL CHECK (status IN ('CONNECTED','BLOCKED','DISCONNECTED'))`, `notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE`, `notification_filter JSONB NOT NULL DEFAULT '{}'::jsonb`, `linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `last_active_at TIMESTAMPTZ`, `blocked_at TIMESTAMPTZ`, `disconnected_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`. Indexes on `tenant_id`, `telegram_chat_id`, `(status, last_active_at)`. App DB user has SELECT + INSERT + UPDATE on a restricted column set; DELETE forbidden — disconnection sets `status = 'DISCONNECTED'` + `disconnected_at = NOW()`, preserving audit history. `user_account` deletion cascade transitions related `telegram_account` rows to `DISCONNECTED` via a `TenantDeletionRegistry` participant.
   - **Acceptance:** ArchUnit test: no class outside `core.messaging.telegram.persistence` references `TelegramAccountEntity`; integration test: delete a `user_account` → post-cascade `telegram_account.status = 'DISCONNECTED'` + `disconnected_at` non-null; DB-level test: `DELETE FROM telegram_account WHERE ...` from app role raises permission-denied.

10. **TG-10: DM-only enforcement on every command + callback.**
    - **Current:** No commands exist.
    - **Target:** `TelegramUpdateRouter` rejects any inbound update where `message.chat.type != "private"` with reply "Vui lòng nhắn riêng với bot — các lệnh không hoạt động trong nhóm." Applies to ALL commands (`/start`, `/help`, `/disconnect`) and free-text messages. `callback_query` routed only when originating chat is private AND `callback_query.from.id` matches the `telegram_user_id` on the resolved `telegram_account` row (cross-actor permission check from TG-13).
    - **Acceptance:** Integration test: bot added to group → `/start <code>` posted in group → bot replies DM-required message + DOES NOT consume pairing code (subsequent DM `/start <code>` still succeeds); callback_query forwarded by user A to user B (different `telegram_user_id`) who taps Send → backend returns "Bạn không có quyền xác nhận draft này" + audit row `telegram.callback.unauthorized_actor`.

### Outbound — Notifications

11. **TG-11: Rule-fire notification with privacy-bounded payload.**
    - **Current:** `TriageDecisionRecorded` event introduced in TG-01; no Telegram listener.
    - **Target:** `TelegramNotificationListener` `@TransactionalEventListener(phase = AFTER_COMMIT)` subscribed to `TriageDecisionRecorded`. Renders payload from `sender_display_name`, `sender_domain`, `subject_truncated_80_chars`, `classification`, `action_taken`, `gmail_message_id` ONLY. Never includes `body`, `snippet`, `messageHtml`, `prompt`, `completion`, `token`. Outbound flow: `MessagingNotificationOutboxRepository.enqueue(channel='TELEGRAM', payload)` → `TelegramNotificationSenderWorker` (Postgres `SKIP LOCKED`, same pattern as v1.0 outbox) drains with `TelegramSendRateLimiter` (TG-04). Notifications gated by `telegram_account.notifications_enabled = TRUE` AND `telegram_account.notification_filter` predicate match.
    - **Acceptance:** `ArchUnit TelegramPathBodyBanTest`: `core.messaging.telegram.notification.*` cannot reference `GmailMessageBody`, `MessageContent`, `EmailBodyRepository`, fields matching regex `body|bodyHtml|snippet|messageHtml|content` (user-authored draft carve-out exempt only inside `chat.preview` subpackage via package-level allowlist). Integration test: trigger rule fire on 50 fixture messages → all 50 notifications delivered within 30s, none contains body (asserted via Logback test appender + sent-payload regex sweep), all `telegram_notification_log` rows have `gmail_message_id` set + zero rows with `body_excerpt` column (column does not exist — schema enforcement).

12. **TG-12: Inline keyboard with primary actions + More submenu for destructive.**
    - **Current:** N/A.
    - **Target:** Notification message `inline_keyboard` row 1: `[💬 Reply]` `[📥 Archive]` `[🔗 Open]`. Row 2: `[⋯ More]`. Tapping `[⋯ More]` posts a follow-up message (Telegram bots lack modal API) with row 1: `[😴 Snooze 1h]` `[😴 Until tomorrow]`. Row 2: `[🚫 Mark spam]` `[🗑 Trash]`. Destructive actions (`Mark spam`, `Trash`) require a second tap that edits the message to "Xác nhận xoá? [✅ Xác nhận] [❌ Huỷ]" before executing. The primary message retains the same `gmail_message_id` reference; expansion message references parent via `reply_to_message_id`.
    - **Acceptance:** Integration test: user taps `[⋯ More]` → second message posted within 1s → user taps `[🗑 Trash]` → confirm prompt appears → user taps `[✅ Xác nhận]` → `MailActionService.trash(...)` called once with `source = TELEGRAM_INLINE_BUTTON_DESTRUCTIVE_CONFIRMED` → original notification edited to "🗑 Đã xoá lúc HH:mm".

13. **TG-13: Callback verification via deterministic token + cross-actor permission check.**
    - **Current:** N/A.
    - **Target:** `callback_data` format `<actionId>:<resourceId>:<token16>` where `token16 = sha256(actionId + ":" + tenantId + ":" + resourceId + ":" + messageSentEpochMinute).hex.substring(0, 16)`. Resolve: parse → lookup `telegram_account` by `chat_id` → derive expected token → constant-time compare. Cross-actor check: `callback_query.from.id == telegram_account.telegram_user_id` (rejects forwarded button taps). Idempotency: `telegram_notification_log.acted_on_at` set atomically; double-tap returns "Đã xử lý" without re-execution.
    - **Acceptance:** Integration test: tamper `token16` → "Action không hợp lệ"; tamper `resourceId` → "Action không hợp lệ"; replay same callback twice → second returns "Đã xử lý" + downstream service not re-invoked (Mockito `verify(times(1))` on `MailActionService` / `OutboundSendGateway`).

14. **TG-14: All Gmail writes from Telegram route through existing `OutboundSendGateway` + new `MailActionService`.**
    - **Current:** `OutboundSendGateway` is the single Gmail send call site (enforced by `OnlyOneGmailSendCallSiteTest`). No equivalent invariant for archive/trash/markRead/snooze/markSpam yet (now established in TG-02).
    - **Target:** Telegram handlers call `OutboundSendGateway.send(...)` for send/reply/forward and `MailActionService.{archive,markRead,markSpam,trash,snooze}` for non-send actions. Each call carries an `OutboundActionSource source` from TG-03. NO new Gmail send call site introduced; `OnlyOneGmailSendCallSiteTest` still passes (exactly 1 match for `gmailClient.users().messages().send`). NO new Gmail mutation call site outside `core.mailaction.usecases` for non-send actions.
    - **Acceptance:** Grep CI gate stays at exactly 1 match for the Gmail send call site signature; `OnlyOneGmailSendCallSiteTest` green; `MailActionServiceArchTest` (new) asserts non-send Gmail mutation calls live only inside `core.mailaction.usecases`; audit rows from a Telegram-confirmed send have `source = 'TELEGRAM_CHAT_CONFIRMED'`; audit rows from inline-button archive have `source = 'TELEGRAM_INLINE_BUTTON'`.

### Inbound — Commands & Chat

15. **TG-15: Minimal slash-command surface — `/start`, `/help`, `/disconnect`.**
    - **Current:** N/A.
    - **Target:** `/start <code>` triggers pairing (TG-08). `/start` without code returns Vietnamese welcome + "Hãy mở Zero Mail → Settings → Connected Apps để lấy mã kết nối." `/help` returns Vietnamese help text listing the 3 available commands + brief privacy note + link to docs. `/disconnect` flips `telegram_account.status = 'DISCONNECTED'` + `disconnected_at = NOW()`, preserves `notification_filter`, replies "Đã ngắt kết nối. Mở Zero Mail web để kết nối lại bất cứ lúc nào.". `/pause`, `/digest`, `/unread` explicitly out of scope (see Boundaries).
    - **Acceptance:** Each of the 3 commands has 1 integration test verifying happy-path; unknown command (e.g. `/foo`) returns "Lệnh không hợp lệ. Gõ /help để xem các lệnh khả dụng."

16. **TG-16: Free-text chat assistant via existing pipeline with streaming, `responseSurface = TELEGRAM`.**
    - **Current:** `ChatOrchestrator` (Phase 7) drives the chat pipeline through `ChatStreamSink`. No `responseSurface` enum exists; the sink interface implicitly assumes web SSE.
    - **Target:** Introduce `ResponseSurface` enum (`WEB_SSE`, `TELEGRAM`) in `core.chat.domain`. `ChatOrchestrator.stream(...)` gains a `ResponseSurface surface` parameter that is plumbed into sink selection. A new `TelegramChatStreamSink` implements `ChatStreamSink` and translates streaming events to Telegram `editMessageText` calls. Any free-text Telegram DM message (not a slash command) → forwarded to `ChatOrchestrator.stream(tenantId, sessionId, userMessage, ResponseSurface.TELEGRAM)`. Streaming uses Spring AI `StreamingChatModel.stream(...)` per CLAUDE.md "no fallback" rule. Telegram delivery: bot posts placeholder "✍️ Đang viết...", then `editMessageText` every ~1s (Reactor `bufferTimeout(80 chunks, Duration.ofMillis(1000))`) with accumulated text, final edit clears the indicator. On Telegram `429` during edit, falls back to **single final-message post** (NOT a streaming-disable fallback at the LLM layer — only a Telegram-edit-rate-limit transport fallback).
    - **Acceptance:** Integration test with WireMock OpenAI streaming endpoint emitting 20 chunks over 5s → `TelegramApiClient.editMessageText` invoked ≥3 times + 1 final edit removing typing indicator; ArchUnit assertion: `core.messaging.telegram.chat.*` does NOT import or reference any non-streaming chat call path (only `StreamingChatModel` + `ChatOrchestrator.stream`).

17. **TG-17: Pending email confirmation via existing `chat_message.parts` + deterministic token.**
    - **Current:** v1.1 chat preview card uses `chat_message.parts` JSONB with `confirmationState: "pending"` tool-output entries; web confirm flow calls `confirmAssistantEmailActionForAccount`.
    - **Target:** Reuse the same `chat_message.parts` storage — Telegram simply renders the pending tool-part as a Telegram message + `inline_keyboard` `[✅ Gửi]` `[📝 Sửa]` `[💾 Lưu nháp]` `[❌ Huỷ]`. The `[✅ Gửi]` `callback_data` carries `token16 = sha256(actionType + ":" + chatMessageId + ":" + toolCallId).substring(0, 16)`. On callback: server iterates last 50 assistant `chat_message.parts` entries for the chat, recomputes token, matches → calls existing `confirmAssistantEmailActionForAccount(...)` which routes through `OutboundSendGateway` with `source = TELEGRAM_CHAT_CONFIRMED`. NO new `assistant_pending_action` table is introduced.
    - **Acceptance:** Integration test: AI in Telegram chat generates a draft → preview card posted → user taps `[✅ Gửi]` → `confirmAssistantEmailActionForAccount` invoked once (Mockito verify) → `audit_outbound_action` row (or sibling table from TG-03) has `source = 'TELEGRAM_CHAT_CONFIRMED'`; user taps `[✅ Gửi]` again → "Đã gửi rồi" + service not re-invoked.

### Settings UI + Audit / Observability

18. **TG-18: `/settings/connected-apps` sub-route with Telegram card.**
    - **Current:** Settings UI is a single-page card grid in `apps/web/app/(protected)/(app)/settings/SettingsClient.tsx`. No Connected Apps view exists.
    - **Target:** New route `apps/web/app/(protected)/(app)/settings/connected-apps/page.tsx`. Main Settings page gains one navigation link "Connected Apps" (or equivalent card) pointing to the sub-route — `SettingsClient.tsx` otherwise unchanged. Feature module: `apps/web/features/telegram-integration/{api,components,hooks,query-keys.ts}` per CLAUDE.md convention 8. Telegram card states: (a) disconnected → Connect button + Vietnamese explainer; (b) connected → `@username · linked HH:mm DD/MM/YYYY` + Notification filter editor (`notifications_enabled` toggle + per-rule allow-list multi-select + sender VIP-only toggle + classification multi-select) + Disconnect button (confirm-once dialog). Connect button opens dialog with QR code + `[Open Telegram]` deep-link button + 8-minute countdown of code TTL (display 8 min though backend TTL is 10 min, buffer for clock skew). Dialog polls `GET /api/integrations/telegram/status` every 2s for up to 10 min; on `connected: true` closes dialog + invalidates feature query keys. All copy uses `next-intl` keys under `connectedApps.telegram.*`.
    - **Acceptance:** Playwright e2e: start at `/settings/connected-apps` disconnected → click Connect → dialog opens with QR + countdown → simulate webhook `/start <code>` via test helper → dialog closes within 4s → card now shows connected state with `@username`; click Disconnect → confirm dialog → card returns to disconnected state. Backend integration test: pairing code generation + status polling endpoints return expected shape; OpenAPI schema regenerated (`pnpm --filter web run generate:api`) and committed.

19. **TG-19: Audit + observability + `TelegramPrivacySweepTest`.**
    - **Current:** v1.2 `admin_audit_event` exists for admin actions; v1.1 audit for outbound actions tracked via `triage_audit` rows (now extended in TG-03).
    - **Target:** Tenant-facing Telegram events (link, unlink, blocked-by-user, command-invocation summary) write to existing audit infrastructure with `event_kind = 'TELEGRAM_*'`. Logged metrics: counter `telegram.notifications.sent.total{tenant_id}`, counter `telegram.callback.received.total{action_id}`, histogram `telegram.api.latency.seconds{method}`, counter `telegram.errors.total{kind}`. Log format follows project standard: `event=telegram.notification.sent tenantId={} sender_domain={} action_taken={}` — never contains body, subject, full sender email, prompt, completion, or callback token. New `TelegramPrivacySweepTest` (sibling of triage privacy sweep tests from Phase 1) runs a fixture pipeline: pairing + 10 notifications + 5 callbacks + 3 chat turns + 1 send confirmation; captures all log output via Logback test appender; asserts regex `[a-z0-9._+-]+@[a-z0-9.-]+\.[a-z]{2,}` never appears unmasked; asserts `body`, `snippet`, `prompt`, `completion`, `token` substrings never appear in log lines from `core.messaging.telegram.*` package.
    - **Acceptance:** `TelegramPrivacySweepTest` green; Micrometer registry exposes all 4 declared metrics with non-zero samples after the fixture pipeline; Logback test appender's captured output passes the privacy regex sweep.

## Boundaries

**In scope:**

- New Spring Modulith module `core.messaging.telegram` with subpackages `domain/`, `usecases/`, `persistence/`, `gateway/`, `notification/`, `chat/`, `webhook/`
- New Spring Modulith module `core.mailaction` (facade for archive/markRead/snooze/markSpam/trash)
- New domain event `TriageDecisionRecorded` in `core.triage.domain` + publisher wired into `TriageOrchestratorService`
- New enum `OutboundActionSource` in `core.outbound.domain` (7 values incl. `WEB_LEGACY` backfill sentinel)
- New enum `ResponseSurface` in `core.chat.domain` (2 values: `WEB_SSE`, `TELEGRAM`) + `ChatOrchestrator.stream` signature update + new `TelegramChatStreamSink`
- New tables: `telegram_account`, `telegram_notification_log`, `messaging_notification_outbox` (or extension of existing outbox with `channel = 'TELEGRAM'`)
- New audit column `source VARCHAR(64) NOT NULL` on the existing outbound audit table (or a new sibling `outbound_action_audit` table — final shape decided in discuss-phase), with CHECK constraint over `OutboundActionSource` values + backfill of existing rows to `WEB_LEGACY`
- Webhook controller `TelegramWebhookController` in `backend/api/controllers/integrations/` + dedicated `@Order(1)` `TelegramWebhookSecurityConfig`
- Settings UI sub-route `/settings/connected-apps` + feature module `apps/web/features/telegram-integration/`
- ArchUnit body-ban extension `TelegramPathBodyBanTest` covering all Telegram packages
- Privacy sweep test `TelegramPrivacySweepTest` for the Telegram surface
- `MessagingChannel` interface in `core.messaging.api` with Telegram as the first implementation (Zalo to follow in a separate phase)
- Bot init script using existing Spring `@Component` + `ApplicationReadyEvent` pattern
- Bucket4j dep + `TelegramSendRateLimiter` (per-chat 1/s + global 30/s, in-memory) + closure of SEED-016
- 3 slash commands only: `/start`, `/help`, `/disconnect`
- Inline keyboard (primary + More submenu + destructive confirm-twice flow)
- Deep-link pairing UX via `t.me/<bot>?start=<signed-code>` (zero typing)
- Cross-actor permission check on every callback
- Documentation `docs/integrations/telegram-setup.md` (BotFather setup, env vars, webhook config, troubleshooting)
- Playwright e2e covering: Connect → notify → callback-confirm-send happy path

**Out of scope:**

- `/pause [duration]`, `/digest`, `/unread` slash commands — defer to follow-on phase; requires `MailPauseService` + `DigestService` + duration enum, none of which exist today
- 5th-tab tabbed Settings layout — current single-page card grid stays; new sub-route adopted instead (lower blast radius, lower Phase 9 regression risk)
- Zalo OA integration — separate follow-on phase pending business registration; `MessagingChannel` interface designed to accommodate but no Zalo code in this phase
- Slack / Teams integration — different segment, not on v1.x roadmap
- Group chat support — DM-only enforcement is mandatory (TG-10)
- Multi-Gmail-account `/switch` command — Zero Mail is currently 1 tenant per Google account; deferred until multi-account is itself a phase
- Voice / audio messages — text only
- File attachments inbound (Telegram → email) — rejected with "Đính kèm file chưa hỗ trợ"
- Inline-mode bot (no `@ZeroMailBot search...` from other chats)
- Persistence of free-text Telegram chat outside `chat_message.parts` — chat history reuses existing v1.1 schema
- Embedding / vector index of chat content — privacy posture forbids embeddings of user mail
- Real-time presence / typing-status from user → bot — Telegram does not expose this to bots in DMs
- Custom bot username per tenant — one global `@ZeroMailBot` (or fallback name per TG-05 open question, locked in discuss-phase)
- "Login with Telegram" as an alternative auth path — Google OAuth remains the only sign-in
- Cross-process Spring events between API and worker — `TriageDecisionRecorded` is in-process in whichever module published it; cross-process handoff stays on the existing PostgreSQL outbox / processing tables (per CLAUDE.md Convention 6)
- Bucket4j Redis backend — in-memory only for v1.3 (single-process worker); Redis backend reserved for a future scale-out phase
- Rule-builder assistant prompt changes — Phase 10 does not touch rule compilation prompts

## Constraints

- **Privacy ARCH-02 extension (HARD):** ArchUnit body-ban regex extends to Telegram payload classes. No Gmail-extracted body content in `sendMessage.text`, `editMessageText.text`, `callback_data`, `telegram_notification_log` columns, or any log line from `core.messaging.telegram.*`. User-authored draft data in `chat_message.parts` remains the only exception, and only when rendered via the `chat.preview` subpackage.
- **Single Gmail send call site (HARD):** Telegram MUST NOT introduce a new Gmail send call site. All sends route through `OutboundSendGateway`. Grep gate stays at exactly 1 match. `OnlyOneGmailSendCallSiteTest` green.
- **Single non-send Gmail mutation surface (NEW HARD):** Archive / markRead / snooze / markSpam / trash routed exclusively through `MailActionService` (TG-02). New `MailActionServiceArchTest` enforces.
- **Streaming-only chat (HARD):** Free-text chat replies use `StreamingChatModel.stream(...)` per CLAUDE.md. No non-streaming fallback at the LLM layer. Telegram-edit-rate-limit fallback to single-message post is a Telegram **transport** concern only and is allowed.
- **One global bot, never per-tenant:** Single `TELEGRAM_BOT_TOKEN`. Tenant differentiation via `chat_id` lookup.
- **Stateless link code:** Pairing code is HMAC-signed JWT-like with 10-min TTL. No `telegram_pairing` table.
- **Deterministic action token:** No HMAC nonce table. Token = `sha256(actionId:tenantId:resourceId:messageEpochMinute)` truncated 16 chars. Combined with cross-actor permission check.
- **DM-only (HARD):** Group chat usage rejected at router. No exception.
- **Cross-actor permission check (HARD):** `callback_query.from.id == telegram_account.telegram_user_id` on every callback. Forwarded button taps rejected.
- **Per-chat rate limit 1 msg/s, global 30 msg/s (in-memory Bucket4j):** Telegram `429` honored with `retry_after`.
- **Spring Modulith boundaries:** `core.messaging.telegram` may depend on `core.gmail`, `core.triage` (events + audit via `@NamedInterface`), `core.chat`, `core.outbound`, `core.mailaction`, `core.rules`, `core.tenant`, `core.shared`. Does NOT depend on `backend/api` packages.
- **Cross-process:** Worker process imports the same `core.messaging.telegram` module and drains its own outbox rows; no in-process events cross the API ↔ worker boundary.
- **Spring Boot 4 / Java 25 / Gradle 9 (HARD):** No Lombok. Records for DTOs / events / config props. Virtual threads enabled.
- **No 3rd-party Telegram SDK:** Spring `RestClient` + Java records. No `rubenlagus/TelegramBots`. No chat-SDK abstraction layer.
- **Backend enterprise naming (HARD):** Per CLAUDE.md Backend Code Style: no `req`/`res`/`repo`/`svc`/`cfg`/`ctx`/`msg`/`err`/`ex`/`e`. Prefer `request`, `response`, `telegramAccountRepository`, `mailActionService`, `telegramProperties`, `tenantContext`, `telegramUpdate`, `webhookAuthenticationException`.
- **Audit source field (HARD):** Every call to `OutboundSendGateway.send(...)` and every `MailActionService` method passes a literal `OutboundActionSource` enum value. ArchUnit asserts no `null` and no string literal.

## Acceptance Criteria

- [ ] `TriageDecisionRecorded` event published from `TriageOrchestratorService` after each rule action applied; ArchUnit asserts the record has zero body-shaped fields
- [ ] `MailActionService` facade in `core.mailaction.usecases` exposes archive / markRead / markSpam / trash / snooze; `TriageGmailWriter` delegates to it; ArchUnit asserts no Gmail mutation outside the facade for these 5 actions
- [ ] `OutboundActionSource` enum (7 values incl. `WEB_LEGACY`) used by every outbound + mail-action call site; audit table has `source` column with CHECK; existing rows backfilled to `WEB_LEGACY`
- [ ] Bucket4j on classpath; `TelegramSendRateLimiter` enforces per-chat 1/s + global 30/s; SEED-016 status flipped to `closed-by-phase-10`
- [ ] `TELEGRAM_BOT_TOKEN` env missing → `/api/integrations/telegram/status` returns 503; Settings card shows disabled state
- [ ] Webhook double-secret (URL secret + header secret) verifies; either mismatch → 401 + Bucket4j rate-limit on bad source IP
- [ ] `setMyCommands` runs once on startup, idempotent on reboot (no duplicate API call to Telegram)
- [ ] Pairing via signed JWT-like code, 10-min TTL, single-use idempotent consume; tampered code rejected before DB lookup
- [ ] `telegram_account` table created via Liquibase; DELETE forbidden by DB grants; `user_account` deletion cascades to `status = 'DISCONNECTED'`
- [ ] DM-only enforced on all commands + callbacks; group chat usage rejected
- [ ] Rule-fire notification listener delivers within 30s of `TriageDecisionRecorded` AFTER_COMMIT phase; payload contains NO body / snippet / prompt
- [ ] Inline keyboard: 3 primary actions + More submenu; destructive (Trash, Mark spam) requires confirm-twice; routes through `MailActionService` with correct `OutboundActionSource`
- [ ] Callback verified via deterministic token + cross-actor permission check; tamper / replay rejected
- [ ] Every Gmail write from Telegram routes through `OutboundSendGateway` or `MailActionService` with `source = TELEGRAM_*`; grep gate stays at 1 Gmail send call site; new `MailActionServiceArchTest` green
- [ ] 3 slash commands only: `/start`, `/help`, `/disconnect`
- [ ] Free-text chat uses `StreamingChatModel.stream(...)` with `ResponseSurface = TELEGRAM`; Reactor 1s buffer → `editMessageText` cadence ≥3 edits over 5s on 20-chunk fixture
- [ ] Pending email confirm reuses `chat_message.parts` storage; deterministic 16-char token lookup; no new `assistant_pending_action` table
- [ ] `/settings/connected-apps` sub-route with Telegram card (Connect dialog: QR + deep-link + countdown; Disconnect; Filter editor); main Settings page untouched
- [ ] `TelegramPathBodyBanTest` ArchUnit test green
- [ ] `TelegramPrivacySweepTest` green — no body / snippet / unmasked email regex / prompt / completion in any log line from `core.messaging.telegram.*`
- [ ] Bucket4j rate limits enforced: per-chat 1/s, global 30/s, Telegram 429 honored with `retry_after`
- [ ] Playwright e2e: full Connect → notify → callback-confirm-send happy path passes in < 30s
- [ ] Documentation `docs/integrations/telegram-setup.md` covers BotFather setup, env vars, webhook config, troubleshooting

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                              |
|--------------------|-------|------|--------|--------------------------------------------------------------------|
| Goal Clarity       | 0.92  | 0.75 | ✓      | Outcome specific + measurable + 3-click + privacy-bounded          |
| Boundary Clarity   | 0.92  | 0.70 | ✓      | Explicit in/out lists; slash commands deferred; UI sub-route locked |
| Constraint Clarity | 0.88  | 0.65 | ✓      | Privacy ARCH-02 extension + single-call-site + Bucket4j all locked |
| Acceptance Criteria| 0.85  | 0.70 | ✓      | 23 pass/fail items, all falsifiable                                |
| **Ambiguity**      | 0.102 | ≤0.20| ✓      | Strong pass — codebase scout closed all SEED-vs-reality gaps       |

## Interview Log

| Round | Perspective    | Question summary                              | Decision locked                                                                 |
|-------|----------------|-----------------------------------------------|---------------------------------------------------------------------------------|
| 0     | Researcher (initial) | Codebase scout vs SEED-007 assumptions | 6 mismatches identified: trigger event, OutboundActionSource, MailActionService, Digest/Pause services, Settings UI shape, Bucket4j |
| 1     | Researcher     | Trigger event for Telegram notifications?     | TG-01 — Publish new `TriageDecisionRecorded` Spring Modulith event from `TriageOrchestratorService` |
| 1     | Researcher     | Service surface for archive/snooze/trash/markSpam/markRead? | TG-02 — Create `MailActionService` facade in new `core.mailaction.usecases` module |
| 1     | Researcher     | Settings UI shape (tabs vs grid vs sub-route)? | TG-18 — New `/settings/connected-apps` sub-route; do not touch existing single-page Settings grid |
| 2     | Simplifier     | `OutboundActionSource` enum + audit column?   | TG-03 — Create enum (7 values incl. `WEB_LEGACY` backfill) + audit `source` column with CHECK constraint |
| 2     | Simplifier     | Slash commands `/pause` `/digest` `/unread`?  | TG-15 — Defer all 3; ship `/start` `/help` `/disconnect` only; pause/digest/unread go to follow-on phases |
| 2     | Simplifier     | Bucket4j vs custom throttle vs skip?          | TG-04 — Adopt Bucket4j (in-memory only); promote and close SEED-016             |

## Source Material

- `.planning/seeds/SEED-007-messaging-assistant-slack-telegram-zalo.md` (planted 2026-05-14, upgraded to draft-SPEC 2026-05-28) — primary input
- `.planning/seeds/SEED-016-bucket4j-rate-limiting-evaluation.md` — closed by TG-04
- Inbox Zero local clone (`E:/Project/inbox-zero/apps/web/utils/messaging/**`) — architectural reference only
- Codebase scout 2026-05-28: `OutboundSendGateway`, `ChatOrchestrator`, `TriageOrchestratorService`, `TriageGmailWriter`, `TenantService.setTriagePaused`, `SettingsClient.tsx`, `ChatPersistenceContentBanTest`, `OnlyOneGmailSendCallSiteTest`, Liquibase changelog state (latest #094 / #098)
- CLAUDE.md privacy / streaming / single-call-site / no-Lombok / Modulith conventions
- Phase 7 (chat assistant) — pipeline reused
- Phase 8 (admin console) — audit row pattern reused via `OutboundActionSource`
- Phase 9 (user settings) — Settings sub-route adopted to avoid Phase 9 regression risk

---

*Phase: 10-telegram-messaging-assistant*
*Spec created: 2026-05-28*
*Next step: /gsd-discuss-phase 10 — implementation decisions (final audit table shape, JWT key rotation policy, worker vs API outbox drain, Modulith @NamedInterface exposure shape, Bucket4j version, openapi-typescript regen workflow, etc.)*
