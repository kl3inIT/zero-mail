# Phase 10: Telegram Messaging Assistant — Research

**Researched:** 2026-05-28
**Domain:** Telegram Bot transport + Spring AI streaming + Spring Modulith eventing + PostgreSQL outbox + Bucket4j rate-limiting + Java 25 / Spring Boot 4 / Spring Security 7
**Confidence:** HIGH (CONTEXT.md đã lock 8 D-* decisions; research thu hẹp vào verify cú pháp + closing 2 ambiguity gates: dedup-window không dùng `NOW()` và Bucket4j 8.x vs 9.x pin).

---

## 1. Tóm tắt

Phase 10 thêm 1 Telegram Bot toàn cục cho Zero Mail: webhook double-secret + IP-allowlist trên `/webhooks/telegram/{urlSecret}`, deep-link pairing JWT HS256 10-min TTL (fit ≤64 chars), inline-button notifications khi rule fire (qua `TriageDecisionRecorded` + outbox `processing_job` extension), free-text chat streaming qua `ChatOrchestrator.stream(..., surface=TELEGRAM)` với Reactor `bufferTimeout(40, 800ms)` → `editMessageText`. Send/reply/forward routed qua **single** `OutboundSendGateway` với `source=TELEGRAM_CHAT_CONFIRMED|TELEGRAM_INLINE_BUTTON*`. Audit lưu vào table mới `outbound_action_audit` (D-01) — **không** extend `triage_audit.source`. Pending action CAS dùng `assistant_pending_action` (D-02) — **không** iterate `chat_message.parts`. Drain outbox ở `backend/worker` only (D-04).

Hai chỉnh sửa quan trọng research thu được:

1. **Bucket4j artifact**: pin `com.bucket4j:bucket4j_jdk17-core:8.19.0` (line 8.x, không phải 9.x — verified Maven Central 2026-05). Có Boot4-compatible starter (`bucket4j-spring-boot-starter` 0.20.0) nhưng không cần — `LocalBucketBuilder` đủ dùng trực tiếp.
2. **Dedup window 24h không thể dùng `NOW()` trong partial UNIQUE index**: PostgreSQL từ chối vì `now()` non-IMMUTABLE. Thay bằng **dedup table riêng** `telegram_notification_dedup(tenant_id, gmail_message_id, sent_at)` với UNIQUE `(tenant_id, gmail_message_id)` + scheduled `DELETE WHERE sent_at < NOW() - INTERVAL '24h'` (vacuum cron), HOẶC **time-bucketed col** (`bucket_day INTEGER GENERATED ALWAYS AS (EXTRACT(EPOCH FROM initiated_at)::bigint / 86400) STORED`) + partial UNIQUE trên `(tenant_id, gmail_message_id, bucket_day) WHERE source LIKE 'TELEGRAM_%'`. Khuyến nghị Option A — dedup table riêng — vì semantic rõ ràng hơn cho người đọc + dễ vacuum.

**Primary recommendation:** Plan-phase mint 6 changesets (#099-#104) cho `telegram_account` / `telegram_notification_log` / `outbound_action_audit` / `telegram_notification_dedup` / `processing_job` CHECK extension / OptimisticLock-friendly state CAS. ArchUnit thêm 5 tests. ResponseSurface enum + TelegramChatStreamSink ship cùng wave với worker drain. Phase phải gate `OnlyOneGmailSendCallSiteTest` GREEN sau mỗi commit.

---

## 2. Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Webhook reception + double-secret verify | backend/api | — | Public HTTPS endpoint cần trên cùng host với reverse proxy (CLAUDE.md deploy). Filter chain `@Order(1)`. |
| Pairing JWT mint + status polling | backend/api | — | Authenticated REST surface; cùng SecurityFilterChain với session. |
| Outbox enqueue khi rule fire | `core.messaging.telegram.notification` (loaded trong cả api+worker) | — | `@TransactionalEventListener(AFTER_COMMIT)` chạy trong process publish event (api). DB-only INSERT, không trực tiếp gọi Telegram. |
| Outbox drain → Telegram send/edit | backend/worker | — | D-04 locked: reuse `processing_job` SKIP LOCKED infra. |
| LLM streaming → `editMessageText` | backend/worker hoặc backend/api (theo nơi chat đến) | — | Free-text chat đến qua webhook (api), nhưng nếu phải background-drain để tránh ApiClient timeout, có thể chuyển sang worker. Mặc định: webhook handler trong api ↔ Spring AI stream trực tiếp (chat đã được api làm — chỉ replace SSE sink bằng TelegramSink). |
| `outbound_action_audit` write | `core.outbound.usecases` (cho send) + `core.mailaction.usecases` (cho non-send) | — | Atomic with mutation; same `@Transactional`. |
| Bucket4j throttle | `core.messaging.telegram.gateway` | — | In-memory only, shared singleton per process. Worker process owns global+per-chat buckets. |
| ArchUnit invariant tests | `backend/core/src/test/java/.../arch/` | — | Established pattern (per existing 19 arch tests). |
| Settings UI | `apps/web/features/telegram-integration/` | `apps/web/app/(protected)/(app)/settings/connected-apps/` | Per CLAUDE.md Convention 8. |

---

## 3. Q1 — Telegram Bot API current state (cookbook for plan-phase)

### setWebhook + secret_token + IP allowlist [VERIFIED: core.telegram.org/bots/api]

- `setWebhook` param `secret_token`: 1-256 chars `A-Z a-z 0-9 _ -`. Telegram inject vào header `X-Telegram-Bot-Api-Secret-Token` của mọi POST. **Constant-time compare** trong filter trước khi parse body.
- **IP allowlist** (defense-in-depth, không thay thế secret_token): `149.154.160.0/20` + `91.108.4.0/22`. Spring Security: `IpAddressMatcher`. Codebase chưa dùng `IpAddressMatcher` (verified) — thêm mới trong filter, KHÔNG dùng `RemoteAddressMatcher` (deprecated).
- Combined check: `secret_token` mismatch HOẶC IP outside allowlist → 401 + Bucket4j throttle 10/min per source IP (TG-06).

### setMyCommands idempotent [CITED: gramio.dev/telegram/methods/setmycommands]

- `getMyCommands(scope=default, language_code='')` → JSON array `[{command,description}]`.
- Compare against expected constant list (ordered + canonical JSON). Equal → skip `setMyCommands`. WireMock test: persistent mock state across 2 boots → exactly 1 setMyCommands call total.
- Scope: default (no `BotCommandScope` needed cho phase 10 — 3 commands chỉ cho private chats).

### editMessageText rate [CITED: core.telegram.org/bots/faq + multiple community sources]

- **Per chat:** ≤1 msg/s. Short bursts OK. Implicit budget áp cho cả `sendMessage` + `editMessageText` + `sendChatAction`.
- **Global:** ~30/s/bot token. Áp cho mọi method.
- **429 response:** body `{ok: false, error_code: 429, parameters: { retry_after: <seconds> } }`. Telegram **không** publish header `Retry-After` chính thức — parse từ body `parameters.retry_after`.
- Telegram-published values là community-derived, không có guarantee. Hard cap an toàn = 1/s per chat đã locked trong D-06.

### callback_query.from.id cross-actor check

- `Update.callback_query.from.id` = Telegram user ID của người tap, **không** phải owner của chat. Forwarded messages giữa users vẫn fire callback → MUST compare `callback_query.from.id == telegram_account.telegram_user_id` (locked TG-13).
- `answerCallbackQuery` MUST be called within 30 seconds, kể cả khi từ chối → reply "Bạn không có quyền xác nhận draft này".

### Deep-link `t.me/<bot>?start=<code>` 64-char limit [VERIFIED: core.telegram.org/api/links]

- Start parameter **64 chars** cho regular bots (Mini Apps có 512 chars khác — không applicable).
- Charset: `A-Z a-z 0-9 _ -` (URL-safe base64url subset).
- **Critical gate cho TG-08 (JWT HS256):** JWT compact = `header.payload.signature` base64url. Empirical: header (~24 chars `eyJhbGciOiJIUzI1NiJ9`) + payload (32-bit tenantId + 64-bit nonce + 64-bit iat + 32-bit exp + claim cleanup, expected ~60-80 chars base64url) + signature (~43 chars HS256). **Tổng ~130+ chars >> 64.** ⚠️ **JWT không fit deep-link.**

**Khuyến nghị bắt buộc cho plan-phase:** Đổi từ JWT format sang **compact signed code**:
- Format: `<base64url(payload)>.<base64url(hmac-sha256 truncated 16 bytes)>`
- Payload bytes: `tenantId(16) + nonce(8) + issuedAtUnix(4) = 28 bytes` → base64url ~38 chars
- Signature bytes: HMAC-SHA256 truncated to 16 bytes → base64url ~22 chars
- Total: ~38 + 1 + 22 = **~61 chars** ✓ fits ≤64.

Hoặc đơn giản hơn: token ngẫu nhiên 16 bytes (~22 chars base64url) + Redis row `{tenantId, expiresAt}` với 10-min TTL. Nhưng CONTEXT.md đã lock "no DB row at generation, signed JWT-like" → giữ compact signed code shape thay vì JWT.

### Inline keyboard "More" submenu + destructive confirm-twice

- Telegram bots **không** có modal API → simulate qua `editMessageReplyMarkup` đổi keyboard inline (recommended) hoặc send new message.
- **Locked pattern (TG-12):** Primary message giữ `[💬 Trả lời][📥 Lưu trữ][🔗 Mở][⋯ Khác]`. Tap `[⋯ Khác]` → `editMessageReplyMarkup` đổi sang `[😴 1h][😴 Đến mai][🚫 Spam][🗑 Xoá][← Quay lại]`. Tap `[🗑 Xoá]` → đổi sang `[✅ Xác nhận xoá][❌ Huỷ]`. Tap `[✅ Xác nhận]` → execute `MailActionService.trash(source=TELEGRAM_INLINE_BUTTON_DESTRUCTIVE_CONFIRMED)` → final `editMessageText("🗑 Đã xoá lúc HH:mm")`.

### DM-only (chat.type='private')

- `Update.message.chat.type ∈ {'private','group','supergroup','channel'}`. Router reject nếu khác `'private'` cho mọi command + free-text + callback.

---

## 4. Q2 — Bucket4j on Boot 4 + JDK 25 [VERIFIED: maven central + bucket4j.com]

- **Pin:** `com.bucket4j:bucket4j_jdk17-core:8.19.0` (released 2026-05-19) — **không có version 9.x trên Maven Central** tại research date 2026-05-28. CLAUDE.md "latest stable" → 8.19.0.
- JDK 25 forward-compat: artifact ID `bucket4j_jdk17-core` chỉ là minimum-version marker (compile target jdk17), runs fine trên JDK 25.
- **KHÔNG** dùng `bucket4j-spring-boot-starter` của MarcGiffing — version 0.20.0 nói compatible Spring Boot 4 + Bucket4j 8.16.x, nhưng phase 10 chỉ cần 2 buckets local; thêm starter dependency overhead không cần thiết. Direct `LocalBucketBuilder` API là pattern locked.

### LocalBucketBuilder API (8.19.0) [CITED: bucket4j.com/8.14.0]

```java
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bandwidth;
import java.time.Duration;

// Per-chat bucket: 1 token/s, capacity 1
Bucket perChatBucket = Bucket.builder()
    .addLimit(Bandwidth.builder()
        .capacity(1)
        .refillIntervally(1, Duration.ofSeconds(1))
        .build())
    .build();

// Global bucket: 30 tokens/s, capacity 30
Bucket globalBucket = Bucket.builder()
    .addLimit(Bandwidth.builder()
        .capacity(30)
        .refillIntervally(30, Duration.ofSeconds(1))
        .build())
    .build();
```

### D-07 pause-replenish cú pháp [VERIFIED: bucket4j.com 8.4.0+]

CONTEXT.md D-07 phrase `bucket.addTokens(-cap); bucket.replenishAt(now()+retry_after)` — đây là **không** API thực của Bucket4j. Bucket4j không có method `replenishAt`. Cú pháp đúng:

```java
// Drain to negative balance — addTokens caps at capacity nhưng KHÔNG floor at 0
// Actually: addTokens accepts negative to "burn" tokens
bucket.addTokens(-bucket.getAvailableTokens()); // first zero it
// To "pause" for retry_after seconds → consume tokens equal to capacity*retry_after future refills
// Bucket4j không có direct pause API. Pattern: dùng VerboseAPI hoặc tracker.
```

**Recommended pattern for 429 pause** (cleaner than addTokens negative trick):

```java
// Option A: Manual sleep + reject in window (simpler)
private final ConcurrentMap<Long, Instant> chatPausedUntil = new ConcurrentHashMap<>();

public boolean tryAcquire(long chatId) {
    Instant pausedUntil = chatPausedUntil.get(chatId);
    if (pausedUntil != null && Instant.now().isBefore(pausedUntil)) {
        return false; // skip, wait for outbox retry
    }
    return perChatBucket(chatId).tryConsume(1) && globalBucket.tryConsume(1);
}

public void notify429(long chatId, int retryAfterSeconds) {
    chatPausedUntil.put(chatId, Instant.now().plusSeconds(retryAfterSeconds));
}
```

**Why D-07's `addTokens` + `replenishAt` doesn't work:** `Bucket.addTokens(long)` formula: `newTokens = Math.min(capacity, currentTokens + tokensToAdd)`. Negative tokensToAdd có effect (compensating transaction pattern), nhưng không reset refill schedule. `replenishAt` không tồn tại trong API.

→ **Plan-phase action:** Replace D-07 pause cú pháp bằng `ConcurrentMap<Long, Instant> chatPausedUntil` pattern. Bucket4j tokens vẫn dùng cho non-429 throttle.

---

## 5. Q3 — JWT HS256 pairing link [VERIFIED: docs.spring.io/spring-security/reference/api]

**⚠️ Đã verify trong Q1: JWT compact format ~130 chars > 64 char limit của Telegram deep-link. JWT KHÔNG fit.**

### Recommendation (overrides CONTEXT D-discretion JWT choice)

**Use compact signed code (not JWT):**

```java
// Mint
byte[] payload = ByteBuffer.allocate(28)
    .putLong(tenantId.getMostSignificantBits())
    .putLong(tenantId.getLeastSignificantBits())
    .putLong(secureRandom.nextLong())          // nonce 8 bytes
    .putInt((int)(System.currentTimeMillis() / 1000)) // iat 4 bytes
    .array();

Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret256bit, "HmacSHA256"));
byte[] sig16 = Arrays.copyOf(mac.doFinal(payload), 16);

String code = base64url(payload) + "." + base64url(sig16);  // ~61 chars total
```

- Secret: 256-bit HS256 (32 bytes base64-encoded), env `TELEGRAM_MESSAGING_LINK_SECRET`.
- Validate: extract `iat` from payload → reject if `iat + 600 < now()`. Constant-time `MessageDigest.isEqual` cho signature.
- **No DB row at mint.** Idempotent consume: `INSERT INTO telegram_account ... ON CONFLICT (tenant_id) DO UPDATE` per TG-08 + TG-09.

### Why not `NimbusJwtEncoder` ?

[VERIFIED: docs.spring.io/spring-security/reference/api/java/.../NimbusJwtEncoder]
NimbusJwtEncoder produces standard JWS Compact (`header.payload.signature`). Header alone (`eyJhbGciOiJIUzI1NiJ9`) = 20 chars + dot. Minimum payload với tenantId UUID + iat + exp = ~60 chars base64url. Sig HS256 truncated to standard 32 bytes = 43 chars. Tổng >120 chars. Không workable cho 64-char deep-link.

### If user insists JWT shape

Document warning in 10-PLAN: cần custom JwtEncoder without standard `alg` header (header chỉ chứa ký hiệu protocol version), payload chỉ là binary packed. Implementation tốn hơn compact signed code.

---

## 6. Q4 — Spring Modulith bootstrap [VERIFIED: docs.spring.io/spring-modulith/docs/current/api]

### `@ApplicationModule(allowedDependencies = {...})`

Existing pattern (verified `chat/package-info.java`):
```java
@ApplicationModule(
    displayName = "Messaging - Telegram",
    allowedDependencies = {
        "messaging :: api",            // shared messaging interfaces (if any)
        "chat", "chat :: usecases", "chat :: domain",
        "outbound", "outbound :: api", "outbound :: usecases", "outbound :: domain",
        "mailaction", "mailaction :: usecases",
        "triage :: domain",            // TriageDecisionRecorded NamedInterface
        "gmail :: gateway",            // GmailApiClientFactory (avoid)? actually no — only mailaction touches Gmail
        "tenant", "tenant :: usecases",
        "rules :: domain",             // rule id lookup for notification_filter
        "shared :: persistence",
        "shared :: lang",
        "shared :: exception",
        "llm :: domain",               // ChatResponse, ChatMessage types
        "llm :: gateway.springai"      // StreamingChatModel access via existing seam
    })
package com.zeromail.core.messaging.telegram;
```

### `core.mailaction` Modulith pkg [matches D-03]

```java
@ApplicationModule(
    displayName = "Mail Action (archive/markRead/markSpam/trash/snooze facade)",
    allowedDependencies = {
        "gmail", "gmail :: gateway",
        "tenant", "tenant :: usecases",
        "outbound :: domain",           // OutboundActionSource enum
        "shared :: persistence",
        "shared :: lang",
        "shared :: exception"
    })
package com.zeromail.core.mailaction;
```

Note: `outbound :: domain` thêm vào mailaction's allowedDependencies — `OutboundActionSource` lives ở `core.outbound.domain` per TG-03, và `MailActionService` cần ref enum.

### `@ApplicationModuleListener` vs `@TransactionalEventListener(AFTER_COMMIT)` [CITED: docs.spring.io/spring-modulith/reference/events.html]

| Pattern | Behavior | Use for TG-11 ? |
|---------|----------|------------------|
| `@ApplicationModuleListener` | Shortcuts `@Async + @Transactional(REQUIRES_NEW) + @TransactionalEventListener(AFTER_COMMIT)` | **Recommended** — listener cần INSERT vào `processing_job` (DB work) sau khi triage tx commit. |
| `@TransactionalEventListener(phase=AFTER_COMMIT)` (no `@Transactional`) | Listener runs sau commit nhưng KHÔNG có tx → INSERT fail (no tx, no rollback). | Reject — would silently fail DB writes. |
| `@TransactionalEventListener(phase=AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` | Equivalent to ApplicationModuleListener mà không có `@Async`. Sync — blocks publisher. | Acceptable nếu cần guarantee enqueue trước khi publisher returns; chấp nhận latency. |

**Plan-phase decision:** Use `@ApplicationModuleListener` cho `TelegramNotificationListener`. Async + REQUIRES_NEW means: triage transaction commits → event published → listener runs in separate thread + tx → INSERT row in `processing_job` → worker drains. Aligns với existing `ChatModelCacheEvictionListener` style (CONTEXT existing patterns).

**Reuse `core.modulith.event_publication` table** (Liquibase 024) for at-least-once delivery in case API crashes between commit and listener INSERT.

---

## 7. Q5 — Spring AI M7 streaming → Telegram (D-05..D-08)

### StreamingChatModel.stream [CITED: docs.spring.io/spring-ai/reference/api/chatmodel.html]

```java
Flux<ChatResponse> stream(Prompt prompt);
```

Returns `Flux<ChatResponse>` — chunk-by-chunk content + tool_call deltas + final usage.

### Buffer to Telegram editMessageText [CITED: reactor-core github issues 1557, 3012]

`Flux.bufferTimeout(maxSize, maxTime)` known issues:
- **Overflow risk** khi sink produces exactly `N * bufferSize` items + downstream slow (`OverflowException: Could not emit buffer due to lack of requests`).
- **Mitigation:** `.onBackpressureBuffer()` upstream of `bufferTimeout`, hoặc dùng `.buffer(Duration)` thuần thời gian (không có size cap → an toàn hơn cho rate-limited downstream).

**Pattern locked cho phase 10:**

```java
chatStream
    .map(chatResponse -> extractText(chatResponse))   // String chunk
    .scan("", (accumulated, chunk) -> accumulated + chunk)
    .sample(Duration.ofMillis(800))                   // throttle to 1 emit/800ms
    .filter(text -> !text.isEmpty())
    .concatMap(accumulated -> editMessageTextRateLimited(chatId, messageId, accumulated))
    .doOnComplete(() -> finalEditClearingTypingIndicator(chatId, messageId, fullText))
    .subscribe();
```

`Flux.sample(Duration)` instead of `bufferTimeout(N, D)` — emits LAST item per window. Simpler, deterministic for streaming text accumulator pattern. Per Q4 of CONTEXT D-06, **revise**: planner MAY substitute `sample(800ms)` for `bufferTimeout(40, 800ms)` if WireMock evidence shows backpressure flakes.

### Virtual threads vs Reactor scheduler

CLAUDE.md: `spring.threads.virtual.enabled=true`. Reactor `Flux.stream()` ChatModel sẽ chạy trên Spring AI default scheduler (typically `boundedElastic`). Subscribe trên virtual thread chỉ cho lúc gửi HTTP edit messages — KHÔNG cần `publishOn(Schedulers.fromExecutor(virtualThreadExecutor))` vì Reactor đã owned scheduler — virtual thread benefit lost ngay khi `Flux.subscribe()` enters Reactor's worker pool. Document this caveat trong plan.

### Tool-call interleaving

Spring AI M7: tool calls emit qua `chatResponse.getResult().getOutput().toolCalls` trong giữa stream. Pattern: nếu chunk có tool_call → KHÔNG accumulate vào display text (CLAUDE.md draft-body carve-out: tool args là user-authored draft, render qua chat preview card với inline keyboard `[✅ Gửi][📝 Sửa][💾 Lưu nháp][❌ Huỷ]` per TG-17/D-02). Tool output (post-execution) sanitized qua `ToolOutputSanitizer` (existing) trước khi vào `chat_message.parts`.

### Cancellation

User gửi message mới trong khi previous stream chưa xong → cancel previous Flux via `Disposable.dispose()`. Stored ở per-chat session cache. Final edit không gửi nếu cancelled.

---

## 8. Q6 — `processing_job` outbox extension (D-04)

### Current state [VERIFIED: changeset 068]

- `processing_job.job_type VARCHAR(64) NOT NULL` — **KHÔNG có CHECK constraint** (verified grep). Phase 10 thêm CHECK lần đầu, hoặc skip CHECK vì nếu CHECK đã không có thì việc thêm chỉ cho `MESSAGING_NOTIFICATION` không bảo vệ được existing values khác.
- Existing values quan sát: `CATALOG_SYNC` (Phase 8D), `TRIAGE_*` (Phase 4), v.v.

### Recommended changeset (Liquibase YAML)

**Option A: Add brand-new CHECK with all known values** (cleaner long-term):

```yaml
changeSet:
  id: 099-01-processing-job-job-type-check-with-messaging
  comment: Add CHECK constraint to processing_job.job_type including new MESSAGING_NOTIFICATION value
  changes:
    - sql:
        sql: |
          ALTER TABLE processing_job
            ADD CONSTRAINT ck_processing_job_job_type CHECK (
              job_type IN (
                'CATALOG_SYNC',
                'TRIAGE_RETRY',
                'TRIAGE_DLQ_REQUEUE',
                'UNSUBSCRIBE_CAMPAIGN_STEP',
                'MESSAGING_NOTIFICATION'
                -- list all existing values from production scan
              )
            )
        rollback: |
          ALTER TABLE processing_job DROP CONSTRAINT ck_processing_job_job_type;
```

**Critical pre-step for plan-phase:** Scan production DB cho `SELECT DISTINCT job_type FROM processing_job` để liệt kê đầy đủ existing values. Adding CHECK against partial list → break existing rows.

**Option B: Skip CHECK entirely** (current state, lower risk):

Per CONTEXT D-04, only mention "added to existing CHECK constraint". Vì CHECK chưa tồn tại, plan-phase decision = **skip CHECK** + rely on enum-level validation in Java (`JobType` enum at write site, ArchUnit `WorkerJobTypeEnumOnlyTest` mirror `WorkerFailureReasonEnumOnlyTest`).

Recommendation: **Option B (skip CHECK)** vì zero migration risk, enforcement đã ở app layer per existing pattern.

### Payload shape [matches D-04]

```jsonc
{
  "channel": "TELEGRAM",
  "tenantId": "uuid",
  "telegramChatId": 123456789,
  "notificationKind": "RULE_FIRED",  // or COMMAND_REPLY, EDIT_MESSAGE
  "payload": {
    "gmailMessageId": "...",
    "senderDomain": "...",
    "senderDisplayName": "...",
    "subjectTruncated": "...",       // ≤80 chars, NO body
    "classification": "...",
    "actionTaken": "..."
  }
}
```

**ARCH-02 ban:** payload MUST NOT contain `body`, `bodyHtml`, `snippet`, `messageHtml`, `content`, `prompt`, `completion`, `token`. Enforced by `TelegramPathBodyBanTest` ArchUnit + JSON schema validation in `MessagingNotificationOutboxRepository.enqueue()`.

### Worker drain reuse [reuses Phase 8E pattern]

`processing_job` already has `SKIP LOCKED` claim query (verified changeset 068 index `idx_processing_job_claim`). Worker thêm new `@Component MessagingNotificationProcessor` reading `job_type='MESSAGING_NOTIFICATION'` rows → render TelegramSendCommand → call `TelegramApiClient.sendMessage()` with rate limiter → on 429 reschedule via `available_at = NOW() + retry_after`. Existing `last_failure_reason` enum extends với `TELEGRAM_RATE_LIMIT`, `TELEGRAM_FORBIDDEN_BLOCKED`, `TELEGRAM_UNKNOWN_CHAT`.

---

## 9. Q7 — `outbound_action_audit` + dedup window [PARTIALLY VERIFIED]

### Audit table changeset (sequential after #098)

```yaml
changeSet:
  id: 100-01-outbound-action-audit
  changes:
    - createTable:
        tableName: outbound_action_audit
        columns:
          - column: { name: id, type: uuid, defaultValueComputed: gen_random_uuid(), constraints: { primaryKey: true, nullable: false } }
          - column: { name: tenant_id, type: uuid, constraints: { nullable: false } }
          - column: { name: gmail_message_id, type: varchar(64) }
          - column: { name: gmail_thread_id, type: varchar(64) }
          - column: { name: action, type: varchar(32), constraints: { nullable: false } }
          - column: { name: source, type: varchar(64), constraints: { nullable: false } }
          - column: { name: initiated_by_user_id, type: uuid }
          - column: { name: initiated_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
          - column: { name: succeeded, type: boolean, constraints: { nullable: false } }
          - column: { name: failure_reason, type: varchar(100) }
          - column: { name: metadata_json, type: jsonb, defaultValueComputed: "'{}'::jsonb", constraints: { nullable: false } }
    - sql:
        sql: |
          ALTER TABLE outbound_action_audit
            ADD CONSTRAINT fk_outbound_action_audit_tenant
              FOREIGN KEY (tenant_id) REFERENCES user_account(id) ON DELETE CASCADE;
          ALTER TABLE outbound_action_audit
            ADD CONSTRAINT ck_outbound_action_audit_action CHECK (action IN
              ('SEND','REPLY','FORWARD','ARCHIVE','MARK_READ','MARK_UNREAD','MARK_SPAM',
               'TRASH','SNOOZE','STAR','UNSTAR','ADD_TO_DIGEST','SAVE_DRAFT'));
          ALTER TABLE outbound_action_audit
            ADD CONSTRAINT ck_outbound_action_audit_source CHECK (source IN
              ('RULE_AUTO','WEB_CHAT_CONFIRMED','TELEGRAM_INLINE_BUTTON',
               'TELEGRAM_INLINE_BUTTON_DESTRUCTIVE_CONFIRMED','TELEGRAM_CHAT_CONFIRMED',
               'TELEGRAM_DEEPLINK_FROM_NOTIFICATION','WEB_LEGACY'));
          CREATE INDEX idx_outbound_action_audit_tenant_initiated_at
            ON outbound_action_audit (tenant_id, initiated_at DESC);
          CREATE INDEX idx_outbound_action_audit_gmail_msg
            ON outbound_action_audit (tenant_id, gmail_message_id)
            WHERE gmail_message_id IS NOT NULL;
```

### Notification dedup — `NOW()` problem [VERIFIED: postgresql.org docs partial-index limits]

CONTEXT specifics text:
> partial UNIQUE index on `outbound_action_audit (tenant_id, gmail_message_id) WHERE source LIKE 'TELEGRAM_%' AND initiated_at > NOW() - INTERVAL '24 hours'`

**This will FAIL at index creation:** PostgreSQL rejects `NOW()` in index predicate ("functions in index predicate must be marked IMMUTABLE"). `now()`, `current_timestamp`, `current_date` đều volatile.

### Three alternatives evaluated

**Alternative A — separate dedup table (RECOMMENDED):**

```yaml
changeSet:
  id: 101-01-telegram-notification-dedup
  changes:
    - createTable:
        tableName: telegram_notification_dedup
        columns:
          - column: { name: tenant_id, type: uuid, constraints: { nullable: false } }
          - column: { name: gmail_message_id, type: varchar(64), constraints: { nullable: false } }
          - column: { name: sent_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
    - addPrimaryKey:
        tableName: telegram_notification_dedup
        columnNames: tenant_id, gmail_message_id
        constraintName: pk_telegram_notification_dedup
    - sql:
        sql: |
          CREATE INDEX idx_telegram_notification_dedup_sent_at
            ON telegram_notification_dedup (sent_at);
```

- **Enqueue logic:** `INSERT INTO telegram_notification_dedup (tenant_id, gmail_message_id) VALUES (?, ?) ON CONFLICT DO NOTHING RETURNING tenant_id` — if 0 rows returned, suppress notification.
- **Vacuum:** ShedLock-protected `@Scheduled(cron = "0 0 * * * *")` cleanup deleting `sent_at < NOW() - INTERVAL '24 hours'`. Existing `shedlock-spring` already on classpath (libs.versions.toml).
- **Pros:** Semantic clarity ("this table tracks dedup state, period"), simple SQL, easy vacuum.
- **Cons:** New table to schema.

**Alternative B — time-bucketed generated col:**

```sql
ALTER TABLE outbound_action_audit
  ADD COLUMN dedup_day BIGINT GENERATED ALWAYS AS (
    EXTRACT(EPOCH FROM initiated_at)::bigint / 86400
  ) STORED;
CREATE UNIQUE INDEX idx_outbound_action_audit_telegram_dedup_24h
  ON outbound_action_audit (tenant_id, gmail_message_id, dedup_day)
  WHERE source LIKE 'TELEGRAM_%';
```

- **Pros:** No new table, dedup inline với audit.
- **Cons:** Bucket boundary at midnight UTC → 2 notifications in 23h59m → 0h01m của day sau đều fit different buckets, NOT suppressed (false negative). Để fix needs sliding 12h overlap → 2 partial indexes → complex.
- **Semantic problem:** `outbound_action_audit` ghi nhận execution; suppression nên là pre-execution check. Two different concerns trộn lẫn.

**Alternative C — EXISTS check at app layer (no DB constraint):**

```sql
INSERT INTO processing_job (job_type, payload_json, ...)
SELECT 'MESSAGING_NOTIFICATION', :payload, ...
WHERE NOT EXISTS (
  SELECT 1 FROM outbound_action_audit
  WHERE tenant_id = :tenantId
    AND gmail_message_id = :gmailMessageId
    AND source LIKE 'TELEGRAM_%'
    AND initiated_at > NOW() - INTERVAL '24 hours'
);
```

- **Pros:** Zero new schema. Uses existing audit rows.
- **Cons:** Race condition between two concurrent triage decisions on same message (rare nhưng possible via duplicate Pub/Sub delivery). Without UNIQUE constraint, both check + insert can succeed.
- **Mitigation:** Wrap in serializable tx OR add advisory lock OR accept rare double-fire.

**Plan-phase decision:** **Alternative A** (separate dedup table). Best clarity + correctness + simplest vacuum. Cost = 1 extra table + 1 ShedLock cron, both well-understood patterns in this codebase.

### `OutboundActionAuditMandatoryArchTest` shape

```java
package com.zeromail.core.arch;

@Test
void every_gmail_mutation_must_write_outbound_action_audit_in_same_tx() {
    // Pattern matches existing OnlyOneGmailSendCallSiteTest.
    // For each call site invoking GmailApiClient.users().messages().{send, modify, trash, untrash},
    // assert the enclosing method also calls OutboundActionAuditRepository.save(...)
    // OR the method is annotated @AuditedOutboundAction (whitelist marker).
}

@Test
void mailaction_service_methods_each_write_one_audit_row() {
    // ArchUnit can't reliably verify "exactly 1 row written" at compile time.
    // Pair this ArchUnit test with a JUnit integration test verifying row count
    // post each MailActionService method via Mockito spy on the repo.
}
```

---

## 10. Q8 — Webhook SecurityFilterChain (Spring Security 7)

Pattern copy từ `PubSubSecurityConfig` (verified Phase 2A):

```java
@Configuration
public class TelegramWebhookSecurityConfig {

    @Bean
    @Order(1)  // Ahead of PubSub @Order(1) — check whether @Order collision needs Order(0) or Order(2)
    SecurityFilterChain telegramWebhookFilterChain(
            HttpSecurity http,
            TelegramWebhookSecretFilter secretFilter,
            TelegramWebhookIpAllowlistFilter ipFilter) {
        return http
            .securityMatcher("/webhooks/telegram/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .addFilterBefore(ipFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(secretFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

**Order collision warning:** Existing `PubSubSecurityConfig` uses `@Order(1)`. Cùng order với Telegram → Spring may pick either. Plan-phase action: use `@Order(2)` for Telegram (still ahead of user-session chain which is unspecified-order/default = LOWEST_PRECEDENCE_HALF_BACK). Verify với `ApplicationModulesTest` + integration test that webhook chain matches before session chain for `/webhooks/telegram/*` paths.

### secret_token + IP allowlist filter

```java
public class TelegramWebhookIpAllowlistFilter extends OncePerRequestFilter {
    private static final List<IpAddressMatcher> ALLOWED = List.of(
        new IpAddressMatcher("149.154.160.0/20"),
        new IpAddressMatcher("91.108.4.0/22"));

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        String remoteAddress = req.getRemoteAddr();
        boolean allowed = ALLOWED.stream().anyMatch(matcher -> matcher.matches(remoteAddress));
        if (!allowed) {
            res.setStatus(401);
            return;
        }
        chain.doFilter(req, res);
    }
}
```

**X-Forwarded-For caveat:** If VPS sits behind reverse proxy, `getRemoteAddr()` returns proxy IP. MUST configure Spring `ForwardedHeaderFilter` OR use `X-Forwarded-For` header (validated against trusted proxy IPs). CLAUDE.md deploys: "single VPS hosting reverse proxy + apps/web + backend/api". Phase 10 plan must verify `ForwardedHeaderFilter` enabled OR document workaround.

### CSRF

`/webhooks/telegram/**` không cùng SessionConfig với user session, KHÔNG cần `csrf().ignoringRequestMatchers` — `csrf(disable)` đủ.

---

## 11. Q9 — Frontend `apps/web/features/telegram-integration/`

Per CLAUDE.md Convention 8:
- `features/telegram-integration/api/telegram-api.ts` — typed via generated OpenAPI from `apps/web/lib/api/schema.d.ts`. Calls: `POST /api/integrations/telegram/pairing`, `GET /api/integrations/telegram/status`, `POST /api/integrations/telegram/disconnect`, `PATCH /api/integrations/telegram/notification-filter`.
- `features/telegram-integration/query-keys.ts` — `telegramKeys.status()`, `telegramKeys.pairing()`.
- `features/telegram-integration/hooks/useTelegramStatus.ts` — TanStack Query `refetchInterval: 2000`, only while dialog open (use `enabled: dialogOpen`).
- `features/telegram-integration/hooks/useStartPairing.ts` — mutation, `meta.errorMessage: 'connectedApps.telegram.errors.pairingFailed'`.
- `features/telegram-integration/hooks/useDisconnect.ts` — mutation, `meta.successMessage: 'connectedApps.telegram.toasts.disconnected'`.
- `features/telegram-integration/components/TelegramCard.tsx` — disconnected vs connected branching.
- `features/telegram-integration/components/ConnectDialog.tsx` — QR code (`qrcode` library — verify availability; or use `<img src=data:image/svg+xml>` with backend-generated QR PNG base64 returned by pairing endpoint).
- `features/telegram-integration/components/DisconnectConfirm.tsx` — shadcn `AlertDialog`.
- `features/telegram-integration/components/NotificationFilterEditor.tsx` — Toggle (use shadcn `Switch` if installed; else accessible `<button>` matching plan 02A-04 pattern).

### Poll pattern

```typescript
const { data } = useTelegramStatus({
  enabled: dialogOpen && !data?.connected,
  refetchInterval: dialogOpen ? 2000 : false,
});

useEffect(() => {
  if (data?.connected) {
    setDialogOpen(false);
    queryClient.invalidateQueries({ queryKey: telegramKeys.status() });
    toast.success(t('connectedApps.telegram.toasts.connected'));
  }
}, [data?.connected]);
```

Display TTL countdown 8 min (visual buffer of 2 min vs backend 10 min) — per TG-18.

---

## 12. Q10 — Privacy + observability

### Logback format

`event=telegram.{webhook.received|callback.routed|notification.sent|stream.transport_error|pairing.consumed} tenantId={} chatId={} kind=<short-enum>`

Banned substrings in log lines from `core.messaging.telegram.*` package:
- `body`, `bodyHtml`, `snippet`, `messageHtml`, `content`
- `prompt`, `completion`, `token`
- Unmasked email regex `[a-z0-9._+-]+@[a-z0-9.-]+\.[a-z]{2,}`
- Telegram payload text (sendMessage `text`, editMessageText `text`)

### Micrometer counters [matches TG-19]

- `telegram.notifications.sent.total{tenant_id, kind}`
- `telegram.callback.received.total{action_id}`
- `telegram.api.latency.seconds{method}` (histogram)
- `telegram.errors.total{kind}` (kind ∈ {`rate_limited_429`, `forbidden_user_blocked`, `unknown_chat`, `unknown_5xx`})
- `telegram.outbox.queue_depth` (gauge — for ops dashboard)

### OTel spans

Wrap each `TelegramApiClient.*` call in `Tracer.spanBuilder("telegram.api." + method)` with attributes `chat_id` (numeric only — no PII), `http.status_code`, `retry_after_seconds` (on 429). NO span attributes containing `body`/`subject`/`from_email`.

### `TelegramPathBodyBanTest` regex [extends ARCH-02]

```java
// Mirror ChatPersistenceContentBanTest but scope to core.messaging.telegram.*
private static final Pattern BODY_FIELD_PATTERN = Pattern.compile(
    "(?i)\\b(emailBody|messageBody|bodyHtml|bodyText|htmlBody|textBody|body|snippet|messageHtml|content)\\b"
);
// Allowlist: chat.preview subpackage already exempt per CLAUDE.md Privacy draft-body carve-out.
// Phase 10 introduces NO new exempt path.

@Test
void telegram_payload_classes_have_no_body_shaped_fields() { ... }

@Test
void telegram_notification_log_entity_does_not_persist_body_columns() { ... }

@Test
void telegram_api_request_records_do_not_reference_gmail_body_types() { ... }
```

### `TelegramPrivacySweepTest` shape

Mirror existing triage `Phase 1 privacy sweep`. Test pipeline: pairing + 10 notifications + 5 callbacks + 3 chat turns + 1 send confirmation. Capture all log output via `Logback test appender`. Assert:
- No email regex unmasked.
- No `body|snippet|prompt|completion|token` substrings.
- All `event=telegram.*` lines present + valid (positive assertion: structured logging fires).

---

## 13. Q11 — Testing

### WireMock fixtures structure

`backend/api/src/test/resources/telegram-fixtures/`:
- `getMe.200.json`
- `sendMessage.200.json` / `sendMessage.429.json` / `sendMessage.401.json` / `sendMessage.403.user_blocked.json`
- `editMessageText.200.json` / `editMessageText.429.json`
- `editMessageReplyMarkup.200.json`
- `setMyCommands.200.json` / `getMyCommands.empty.json` / `getMyCommands.match.json`
- `answerCallbackQuery.200.json`

### WireMock per-test [CITED: wiremock.org/docs/junit-jupiter]

```java
@WireMockTest(httpPort = 8089)
class TelegramApiClientTest {
    @Test
    void send_message_returns_201_on_429_with_retry_after(WireMockRuntimeInfo info) {
        stubFor(post(urlPathEqualTo("/bot<token>/sendMessage"))
            .willReturn(aResponse()
                .withStatus(429)
                .withBody(Files.readString(Path.of("src/test/resources/telegram-fixtures/sendMessage.429.json")))));
        // assert TelegramApiClient parses retry_after and pauses bucket
    }
}
```

### Playwright e2e

Existing pattern (verified `apps/web/e2e/**`): drive `/settings/connected-apps`, click Connect, capture deep-link, simulate webhook via test helper POSTing forged `/start <code>` with valid `X-Telegram-Bot-Api-Secret-Token` (test profile uses fixed secret).

```typescript
test('Connect → notify → callback-confirm-send happy path', async ({ page, request }) => {
  await page.goto('/settings/connected-apps');
  await page.getByRole('button', { name: /connect/i }).click();
  const deepLink = await page.getByTestId('telegram-deep-link').getAttribute('href');
  const code = new URL(deepLink).searchParams.get('start');

  // Simulate Telegram webhook
  await request.post('/webhooks/telegram/<urlSecret>', {
    headers: { 'X-Telegram-Bot-Api-Secret-Token': process.env.TELEGRAM_WEBHOOK_SECRET! },
    data: { message: { chat: { id: 12345, type: 'private' }, from: { id: 67890 }, text: `/start ${code}` } }
  });

  await expect(page.getByText(/@.*linked/)).toBeVisible({ timeout: 5000 });
});
```

### LLM stream test via VirtualTimeScheduler [CITED: reactor blog]

`StepVerifier.withVirtualTime(() -> chatStream.sample(Duration.ofMillis(800)))` — verify exact emit cadence without sleeping real time. Combined with WireMock OpenAI streaming endpoint emitting 20 chunks over 5s real-time → flip to virtual-time mock for deterministic test.

---

## 14. Validation Architecture

> Nyquist Dimension 8 — required heading. Phase 10 inbound signals are heterogeneous (webhook POST, callback_query, free-text DM, outbox drain heartbeat), each with distinct shapes + rates + replay/aliasing risks.

### Inbound signals + payload shapes + rates

| Signal | Source | Shape | Expected Rate | Burst |
|--------|--------|-------|---------------|-------|
| `POST /webhooks/telegram/{urlSecret}` (message) | Telegram | `{ update_id, message: { chat, from, text } }` | ~1-10/tenant/hour | 20/s/bot global (Telegram-side) |
| `POST /webhooks/telegram/{urlSecret}` (callback_query) | Telegram | `{ update_id, callback_query: { from, message, data } }` | ~1-5/tenant/hour | bursts on rule-fire notification taps |
| `POST /webhooks/telegram/{urlSecret}` (my_chat_member) | Telegram | `{ my_chat_member: { new_chat_member: { status } } }` | rare | n/a |
| `TriageDecisionRecorded` event | `TriageOrchestratorService` | record `(tenantId, gmailMessageId, classification, actionTaken, ...)` | matches Gmail receive rate per tenant | none — already throttled by triage cap |
| `processing_job` row claim (`MESSAGING_NOTIFICATION`) | worker poll | per row | 100ms claim interval (existing) | n/a |
| `editMessageText` outbound during LLM stream | api/worker | `{ chat_id, message_id, text }` | up to 1/s/chat (capped) | per-stream bursts |

### Sampling cadence

- **Webhook handler:** sync (≤500ms). No sampling — handler must ack 200 promptly so Telegram doesn't retry.
- **Outbox drain:** existing `@Scheduled(fixedDelay = 100ms)` per Phase 8E. No change.
- **LLM stream → editMessageText:** `Flux.sample(Duration.ofMillis(800))` emits LAST accumulated text per 800ms window. Capped by Bucket4j per-chat 1/s downstream.
- **Pairing status poll (frontend):** TanStack Query `refetchInterval: 2000` while dialog open.
- **Dedup vacuum:** `@Scheduled(cron = "0 0 * * * *")` ShedLock-protected (hourly).

### Aliasing risks

| Risk | Mechanism | Mitigation |
|------|-----------|------------|
| **Duplicate Telegram updates** | Telegram retries unacknowledged updates after 60s | Idempotent webhook handler keyed by `update_id` — write `telegram_update_processed(update_id PRIMARY KEY)` row before processing (or in-memory LRU for 10k recent IDs) |
| **Duplicate Pub/Sub triage** | Pub/Sub at-least-once delivery → same Gmail message triage twice → 2 notifications | Existing `MailMessageObserved` idempotency (per Phase 2A) + new dedup table (Q7 Alt A) |
| **Callback replay** | User shares deep-link / message to second user; second user taps | Cross-actor check (TG-13): `callback_query.from.id == telegram_account.telegram_user_id`. Deterministic token verification. CAS state PENDING→PROCESSING (TG-17/D-02) rejects second tap. |
| **Telegram 429 cascade** | First edit hits 429 → retry → 429 again → infinite loop | Per-chat `chatPausedUntil` map (Q4 pattern). Single final edit after pause expires (D-07). NEVER incremental retries. |
| **Outbox row stuck PROCESSING** | Worker dies mid-process | Existing `processing_job.locked_until` lease expiry (Phase 8E). Reaper re-claims after 5min. |
| **Race: two concurrent notification enqueues** | Two Pub/Sub deliveries arriving within ms | `INSERT INTO telegram_notification_dedup ... ON CONFLICT DO NOTHING` returns 0 rows → suppress the loser |

### Invariants (must hold for every observable execution)

1. **Single Gmail send call site:** `OnlyOneGmailSendCallSiteTest` GREEN. Telegram send path = `OutboundSendGateway.send(source=TELEGRAM_*)` only.
2. **Single non-send Gmail mutation surface:** `MailActionServiceArchTest`: archive/markRead/markSpam/trash/snooze only inside `core.mailaction.usecases`.
3. **Audit-per-mutation:** Every `OutboundSendGateway.send` + every `MailActionService.*` writes exactly 1 `outbound_action_audit` row in same transaction. Verified by `OutboundActionAuditMandatoryArchTest` + integration test row-count.
4. **Cross-actor permission:** Every callback validates `callback_query.from.id == telegram_account.telegram_user_id`. Reject otherwise.
5. **Double-secret webhook:** Reject 401 unless URL `urlSecret` path var matches `TelegramProperties.urlSecret` AND `X-Telegram-Bot-Api-Secret-Token` header matches `TelegramProperties.webhookSecret`. Both constant-time compare.
6. **IP allowlist (defense-in-depth):** Reject 401 unless source IP in `149.154.160.0/20 | 91.108.4.0/22`. X-Forwarded-For configured per VPS reverse-proxy setup.
7. **DM-only:** `message.chat.type == 'private'` for every routed command + free-text + callback.
8. **CAS state transitions:** `assistant_pending_action.state` flows `PENDING → PROCESSING → CONFIRMED|FAILED`. Optimistic-lock via `version` column. Second tap returns "Đã xử lý" with no service re-invocation.
9. **Dedup window 24h:** Same `(tenant_id, gmail_message_id)` notification fired twice within 24h → second suppressed. `INSERT ... ON CONFLICT DO NOTHING` semantics.
10. **No body leaks in Telegram payload:** ArchUnit `TelegramPathBodyBanTest` + runtime regex sweep `TelegramPrivacySweepTest`.
11. **Streaming-only chat:** ArchUnit asserts `core.messaging.telegram.chat.*` only imports `StreamingChatModel.stream(...)`, never non-streaming `ChatModel.call(...)`.
12. **No new Gmail send call site:** Grep gate (Phase 04 enforcement) stays at exactly 1 match.

### Test surfaces for each invariant

| Invariant | Test |
|-----------|------|
| 1 | `OnlyOneGmailSendCallSiteTest` (existing, no change needed) |
| 2 | `MailActionServiceArchTest` (NEW) — ArchUnit |
| 3 | `OutboundActionAuditMandatoryArchTest` (NEW) — ArchUnit + integration test |
| 4 | `TelegramCallbackCrossActorTest` integration — forge `callback_query.from.id` mismatch → 403 + audit |
| 5 | `TelegramWebhookSecretTest` integration — wrong path secret 401; wrong header 401 |
| 6 | `TelegramWebhookIpAllowlistTest` — RemoteAddr outside CIDR → 401 |
| 7 | `TelegramDmOnlyRouterTest` — chat.type='group' → reply DM-required, no consume |
| 8 | `AssistantPendingActionCasTest` (integration) — double-tap simulation |
| 9 | `TelegramNotificationDedupTest` integration — 2 enqueues same `(tenant, gmail_message_id)` within 24h → 1 row in dedup table |
| 10 | `TelegramPathBodyBanTest` (ArchUnit) + `TelegramPrivacySweepTest` (Logback appender capture) |
| 11 | `TelegramChatStreamingOnlyArchTest` (NEW) — import scan |
| 12 | Existing `OnlyOneGmailSendCallSiteTest` |

### Sampling rate per Nyquist Dim 8

| Test surface | Quick run | Full run | Phase gate |
|-------------|-----------|----------|-----------|
| ArchUnit (NEW + existing) | `./gradlew :backend:core:test --tests "*Arch*"` (<10s) | full `:backend:core:test` | All ArchUnit GREEN before /gsd:verify-work |
| WireMock Telegram fixtures | `./gradlew :backend:api:test --tests "*Telegram*"` | full `:backend:api:test` | All GREEN |
| Playwright e2e | `pnpm --filter web exec playwright test telegram` | full `pnpm --filter web exec playwright test` | Happy-path GREEN |
| Logback privacy sweep | included in full backend test | — | GREEN |

---

## 15. Liquibase changesets summary (plan-phase work)

| # | File | Purpose | Owner |
|---|------|---------|-------|
| 099 | `099-telegram-account.yaml` | `telegram_account` table + indexes + lifecycle CHECK | Phase 10 |
| 100 | `100-outbound-action-audit.yaml` | New `outbound_action_audit` table + CHECKs + indexes (D-01) | Phase 10 |
| 101 | `101-telegram-notification-dedup.yaml` | `telegram_notification_dedup(tenant_id, gmail_message_id, sent_at)` (Q7 Alt A) | Phase 10 |
| 102 | `102-telegram-notification-log.yaml` | `telegram_notification_log` audit (`acted_on_at` for idempotency) | Phase 10 |
| 103 | `103-processing-job-job-type-noop.yaml` | Documentation-only changeset (no schema change, just notes new `MESSAGING_NOTIFICATION` value) | Phase 10 |
| 104 | `104-app-db-grants-telegram.yaml` | App DB user grants on new tables (no DELETE on `telegram_account` per TG-09) | Phase 10 |

---

## 16. Architecture Patterns

### System Architecture Diagram

```
                                    USER (Telegram client)
                                          │
                                          │ HTTPS
                                          ▼
                          ┌──────────────────────────────┐
                          │   reverse proxy (VPS)         │
                          │   149.154.160.0/20 allowlist │
                          └──────────────────────────────┘
                                          │
                ┌─────────────────────────┼─────────────────────────┐
                │                         │                          │
                ▼                         ▼                          ▼
        /webhooks/telegram/*    /api/integrations/...     /settings/connected-apps
        (Order(2) SecurityCfg)  (Order(default) Session)  (Next.js apps/web)
                │                         │
                ▼                         ▼
    TelegramWebhookController  TelegramPairingController
                │                         │
                ▼                         ▼
        TelegramUpdateRouter     PairingService (mint compact signed code)
                │
   ┌────────────┼────────────┬────────────┐
   ▼            ▼            ▼            ▼
/start <code>  /help     callback    free-text DM
   │            │       cross-actor      │
   ▼            ▼          check         ▼
PairingConsume reply    │             ChatOrchestrator.stream(
(INSERT...                ▼               surface=TELEGRAM)
 ON CONFLICT) AssistantPendingAction │
              CAS PENDING→           ▼
              PROCESSING       TelegramChatStreamSink
                │              + Bucket4j throttle
                ▼              + editMessageText
       OutboundSendGateway.send(source=TELEGRAM_CHAT_CONFIRMED)
                │
                ▼
       outbound_action_audit INSERT (same tx)
                │
                ▼
              Gmail API single send call site


TriageOrchestratorService                       worker process
  │                                                 │
  ▼ publishes TriageDecisionRecorded                │
  │                                                 │
  └─→ TelegramNotificationListener (@AppModuleListener async)
       │
       ▼
   INSERT telegram_notification_dedup ON CONFLICT
       │ (if 0 rows → suppress)
       ▼
   INSERT processing_job (job_type=MESSAGING_NOTIFICATION)
       │
       └─────────────────────────────────────────────┐
                                                      │
                                                      ▼
                                   MessagingNotificationProcessor
                                   (worker, SKIP LOCKED claim)
                                          │
                                          ▼
                                   TelegramApiClient.sendMessage
                                   + Bucket4j throttle
                                   + on 429 → reschedule via available_at
                                          │
                                          ▼
                                   INSERT telegram_notification_log
                                   (privacy-bounded: NO body)
```

### Recommended package layout

```
backend/core/src/main/java/com/zeromail/core/
├── messaging/
│   ├── api/                              # MessagingChannel interface (forward-compat for Zalo)
│   └── telegram/
│       ├── package-info.java             # @ApplicationModule
│       ├── domain/                       # records: TelegramAccount, TelegramUpdate, TelegramMessage, TelegramCallbackQuery, OutboundActionSource ref
│       ├── usecases/                     # PairingService, DisconnectService, NotificationFilterService
│       ├── persistence/                  # TelegramAccountEntity (class), TelegramNotificationLogEntity, TelegramNotificationDedupEntity + repositories
│       ├── gateway/                      # TelegramApiClient (RestClient), TelegramSendRateLimiter (Bucket4j), TelegramProperties
│       ├── notification/                 # TelegramNotificationListener (@AppModuleListener), MessagingNotificationProcessor (worker), TelegramButtonLabels
│       ├── chat/                         # TelegramChatStreamSink implements ChatStreamSink
│       └── webhook/                      # TelegramUpdateRouter, TelegramCallbackRouter, TelegramCommandRouter
└── mailaction/
    ├── package-info.java                 # @ApplicationModule
    └── usecases/                         # MailActionService interface + DefaultMailActionService
```

```
backend/api/src/main/java/com/zeromail/api/
├── controllers/
│   └── integrations/
│       └── TelegramWebhookController.java         # POST /webhooks/telegram/{urlSecret}
│       └── TelegramPairingController.java          # POST /api/integrations/telegram/pairing
│       └── TelegramStatusController.java           # GET  /api/integrations/telegram/status
└── security/
    └── TelegramWebhookSecurityConfig.java          # @Order(2) SecurityFilterChain
    └── TelegramWebhookSecretFilter.java
    └── TelegramWebhookIpAllowlistFilter.java
```

---

## 17. Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Token-bucket rate limit | Custom semaphore + counter | **Bucket4j 8.19.0** | Race-prone; Bucket4j handles atomic decrement, refill scheduling, multi-bandwidth composition |
| Telegram bot SDK | `rubenlagus/TelegramBots` | **Spring `RestClient` + Java records** | Per CLAUDE.md "No 3rd-party Telegram SDK". Avoid framework lock-in for 8 API methods |
| OIDC/JWT mint+verify | Hand-rolled HMAC | **Compact signed code via `javax.crypto.Mac` HMAC-SHA256** | JWT compact format too verbose for 64-char deep-link; custom compact format = ~61 chars |
| Outbox queue | Kafka/Rabbit/Redis Streams | **`processing_job` Postgres SKIP LOCKED** | CLAUDE.md hard rule; existing infra in Phase 8E |
| Streaming Reactor pipeline | Custom thread pool + bytes accumulator | **`Flux.sample(Duration)` + `.scan()` accumulator** | Reactor handles cancellation, backpressure, scheduler hopping |
| Cross-actor check | Pull session info | **Direct compare `callback_query.from.id == telegram_account.telegram_user_id`** | Telegram callback already authenticates — no session needed |
| Webhook secret verify | App-level reading | **Spring SecurityFilterChain `@Order(2)`** | Filter-level rejection halts before any controller logic, log noise minimized |
| Idempotent INSERT for pairing | SELECT + INSERT | **`INSERT ... ON CONFLICT (tenant_id) DO UPDATE`** | Atomic, no race |
| Distributed cron for dedup vacuum | Custom leader election | **ShedLock JDBC (already on classpath)** | `shedlock-spring` 7.7.0 verified in `libs.versions.toml` |

---

## 18. Common Pitfalls

### Pitfall 1: `NOW()` in partial UNIQUE index predicate — PostgreSQL rejects
**What goes wrong:** `CREATE UNIQUE INDEX ... WHERE ... AND created_at > NOW() - INTERVAL '24h'` → ERROR `functions in index predicate must be marked IMMUTABLE`.
**Why:** PostgreSQL must guarantee index entries remain valid across time without re-evaluation.
**How to avoid:** Use separate dedup table with TTL vacuum (Q7 Alt A).
**Warning signs:** Liquibase changeset deploy errors on first apply; partial index never created → dedup constraint silently absent.

### Pitfall 2: JWT compact format exceeds 64-char deep-link limit
**What goes wrong:** Spring Security `NimbusJwtEncoder` produces 130+ char JWS. Plug into `t.me/<bot>?start=<jwt>` → Telegram truncates or refuses URL.
**Why:** JWT header (`{"alg":"HS256"}`) alone is ~20 chars; HS256 sig 43 chars.
**How to avoid:** Custom compact signed code (binary payload + truncated HMAC, base64url, no JWT header).

### Pitfall 3: Bucket4j D-07 `replenishAt` API doesn't exist
**What goes wrong:** Implementing CONTEXT D-07 verbatim → compile error / runtime no-op.
**Why:** Bucket4j 8.x API has no `replenishAt`. `addTokens(-N)` works but doesn't reset refill schedule.
**How to avoid:** Use `ConcurrentMap<Long, Instant> chatPausedUntil` pattern (Q4) layered on top of Bucket4j.

### Pitfall 4: Telegram update_id replay → duplicate processing
**What goes wrong:** Telegram retries unacknowledged updates after 60s → handler processes same `/start <code>` twice → second tap may receive "Đã kết nối từ trước" instead of legitimate first tap response.
**Why:** Webhook ack only on HTTP 200. If handler crashes mid-process → Telegram retries.
**How to avoid:** Idempotent webhook handler — `update_id` LRU cache (10k entries) or `telegram_update_processed(update_id PRIMARY KEY)` table.

### Pitfall 5: X-Forwarded-For absent → `getRemoteAddr()` returns proxy IP
**What goes wrong:** IP allowlist filter blocks ALL Telegram requests (proxy IP outside 149.154.160.0/20).
**Why:** VPS reverse proxy terminates TLS; servlet sees only proxy.
**How to avoid:** Enable Spring `ForwardedHeaderFilter` OR read `X-Forwarded-For` first IP (validated against trusted proxy).

### Pitfall 6: `bufferTimeout` overflow on slow downstream
**What goes wrong:** `Flux.bufferTimeout(40, 800ms)` + downstream rate-limited → backpressure overflow → stream errors.
**Why:** `bufferTimeout` emits unconditionally on timeout, ignores downstream demand.
**How to avoid:** Switch to `Flux.sample(Duration)` (emits LAST item per window, naturally backpressure-friendly) OR wrap with `.onBackpressureBuffer(maxSize, OnOverflowStrategy.ERROR)` then handle ERROR explicitly.

### Pitfall 7: `setMyCommands` not strictly idempotent if order changes
**What goes wrong:** Refactor reorders command list → `getMyCommands` returns commands in old order → comparison wrongly detects diff → second API call on every boot.
**Why:** Telegram returns commands in INSERT order, not sorted.
**How to avoid:** Canonicalize both sides before compare (sort by `command` field alphabetically).

### Pitfall 8: Streaming Telegram + tool_call interleaving renders raw tool args
**What goes wrong:** Spring AI M7 emits tool_call chunks; if `TelegramChatStreamSink` blindly accumulates `chatResponse.getResult().getOutput().getContent()` → tool argument JSON appears in Telegram message text.
**Why:** Tool calls are NOT text content; should be rendered as separate inline-keyboard preview cards.
**How to avoid:** Filter chunks where `result.output.hasToolCalls()` → route to preview-card rendering path (TG-17/D-02), NOT to text accumulator.

### Pitfall 9: `processing_job` no CHECK on `job_type` → typos silently enqueue
**What goes wrong:** Worker drains row with `job_type='MESSAGNG_NOTIFICATION'` (typo) → no handler matches → row stuck PENDING.
**Why:** Verified — Phase 8E did NOT add CHECK constraint.
**How to avoid:** Enum-at-write-site enforcement + `WorkerJobTypeEnumOnlyTest` ArchUnit. OR add CHECK constraint in Phase 10 (Q6 Option A) listing all known values.

### Pitfall 10: Cross-actor check skipped on bot-initiated callback
**What goes wrong:** Bot edits preview card → `editMessageReplyMarkup` returns updated message with `callback_data` → next user tap fires callback as if from user. If `from` in callback comes from a forwarded message, cross-actor check might fail false positively.
**Why:** Telegram callback `from` is always the user who tapped, never the bot. But forwarded buttons preserve original `callback_data` → tapped by different user → caught by cross-actor.
**How to avoid:** Always validate `callback_query.from.id == telegram_account.telegram_user_id`. Log `event=telegram.callback.unauthorized_actor` on mismatch.

---

## 19. Code Examples (verified patterns)

### Compact signed code (TG-08 replacement for JWT)

```java
// Source: composed from javax.crypto.Mac + Base64.getUrlEncoder (JDK 25 standard)
public final class PairingCodeService {
    private static final int PAYLOAD_SIZE = 28;       // 16 (UUID) + 8 (nonce) + 4 (iat)
    private static final int SIG_TRUNCATED_SIZE = 16; // HMAC-SHA256 truncated

    private final SecretKeySpec messagingLinkSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public String mint(UUID tenantId) {
        byte[] payload = ByteBuffer.allocate(PAYLOAD_SIZE)
            .putLong(tenantId.getMostSignificantBits())
            .putLong(tenantId.getLeastSignificantBits())
            .putLong(secureRandom.nextLong())
            .putInt((int)(Instant.now().getEpochSecond()))
            .array();

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(messagingLinkSecret);
        byte[] sigFull = mac.doFinal(payload);
        byte[] sig = Arrays.copyOf(sigFull, SIG_TRUNCATED_SIZE);

        Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
        return urlEncoder.encodeToString(payload) + "." + urlEncoder.encodeToString(sig);
        // ~38 + 1 + 22 = 61 chars
    }

    public ConsumedCode verify(String code) {
        String[] parts = code.split("\\.");
        if (parts.length != 2) throw new InvalidPairingCodeException();
        Base64.Decoder urlDecoder = Base64.getUrlDecoder();
        byte[] payload = urlDecoder.decode(parts[0]);
        byte[] signature = urlDecoder.decode(parts[1]);
        if (payload.length != PAYLOAD_SIZE || signature.length != SIG_TRUNCATED_SIZE)
            throw new InvalidPairingCodeException();

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(messagingLinkSecret);
        byte[] expectedSigFull = mac.doFinal(payload);
        byte[] expectedSig = Arrays.copyOf(expectedSigFull, SIG_TRUNCATED_SIZE);
        if (!MessageDigest.isEqual(signature, expectedSig))
            throw new InvalidPairingCodeException();

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        UUID tenantId = new UUID(buffer.getLong(), buffer.getLong());
        long nonce = buffer.getLong();
        int issuedAt = buffer.getInt();
        if (Instant.now().getEpochSecond() - issuedAt > 600)
            throw new PairingCodeExpiredException();
        return new ConsumedCode(tenantId, nonce, issuedAt);
    }
}
```

### Bucket4j throttle with 429-aware pause

```java
public final class TelegramSendRateLimiter {
    private final Bucket globalBucket;
    private final ConcurrentMap<Long, Bucket> perChatBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Instant> chatPausedUntil = new ConcurrentHashMap<>();

    public TelegramSendRateLimiter() {
        this.globalBucket = Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(30).refillIntervally(30, Duration.ofSeconds(1)).build())
            .build();
    }

    public boolean tryAcquire(long telegramChatId) {
        Instant pausedUntil = chatPausedUntil.get(telegramChatId);
        if (pausedUntil != null && Instant.now().isBefore(pausedUntil)) return false;

        Bucket perChat = perChatBuckets.computeIfAbsent(telegramChatId, id ->
            Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(1).refillIntervally(1, Duration.ofSeconds(1)).build())
                .build());
        return perChat.tryConsume(1) && globalBucket.tryConsume(1);
    }

    public void notify429(long telegramChatId, int retryAfterSeconds) {
        chatPausedUntil.put(telegramChatId, Instant.now().plusSeconds(retryAfterSeconds));
    }
}
```

### `@ApplicationModuleListener` for notification enqueue

```java
@Component
public class TelegramNotificationListener {
    private final TelegramNotificationDedupRepository dedupRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final TelegramAccountRepository telegramAccountRepository;

    @ApplicationModuleListener  // = @Async + @Transactional(REQUIRES_NEW) + @TransactionalEventListener(AFTER_COMMIT)
    void onTriageDecisionRecorded(TriageDecisionRecorded event) {
        TelegramAccount account = telegramAccountRepository.findActiveByTenantId(event.tenantId()).orElse(null);
        if (account == null || !account.notificationsEnabled()) return;
        if (!matchesFilter(event, account.notificationFilter())) return;

        int dedupRows = dedupRepository.insertIfAbsent(event.tenantId(), event.gmailMessageId());
        if (dedupRows == 0) return;  // already notified within 24h

        ProcessingJob job = ProcessingJob.builder()
            .jobType("MESSAGING_NOTIFICATION")
            .payloadJson(buildPayload(event, account.telegramChatId()))
            .build();
        processingJobRepository.save(job);
    }
}
```

### Telegram callback router (cross-actor)

```java
@Component
public class TelegramCallbackRouter {
    public void route(TelegramCallbackQuery callback) {
        if (callback.message().chat().type() != ChatType.PRIVATE) {
            logger.warn("event=telegram.callback.non_private chatId={}", callback.message().chat().id());
            return;
        }
        TelegramAccount account = telegramAccountRepository
            .findByTelegramChatId(callback.message().chat().id())
            .orElseThrow(() -> new TelegramAccountNotFoundException(callback.message().chat().id()));
        if (callback.from().id() != account.telegramUserId()) {
            answerCallback(callback.id(), "Bạn không có quyền xác nhận draft này");
            logger.warn("event=telegram.callback.unauthorized_actor tenantId={} chatId={}",
                account.tenantId(), account.telegramChatId());
            return;
        }
        // ... dispatch by callback_data prefix to {send/reply/forward/save_draft, archive, markRead, ...}
    }
}
```

---

## 20. State of the Art (Telegram bot architecture, 2026)

| Old approach | Current approach | Impact |
|--------------|------------------|--------|
| Long polling `getUpdates` | Webhook + secret_token + IP allowlist | Real-time, no idle resource use |
| `setWebhook` without secret_token | `setWebhook` with `secret_token` (1-256 chars) | Mandatory for production — verifies origin |
| JWT pairing | Compact signed code (binary HMAC truncated) | Fits 64-char `start` parameter |
| Per-process Bucket4j | In-memory Bucket4j (single-process worker) | OK for v1.3; swap to Redis backend when scaling out (deferred) |
| Postgres LISTEN/NOTIFY for outbox | `SKIP LOCKED` polling | Simpler operationally; observability via existing Phase 8E |
| Polling Telegram API after send for ack | Optimistic send + retry on 429 | Lower latency |
| Per-tenant bot | Single global bot, identify via chat_id ↔ telegram_account row | Cheaper ops, single BotFather registration |

---

## 21. Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `com.bucket4j:bucket4j_jdk17-core` | TG-04 rate limit | Need to add | 8.19.0 (Maven Central) | none — phase blocked without |
| Spring Security 7 OAuth2 `JwtEncoder` | TG-08 (initially) | ✓ (Spring Boot 4.0.6 manages) | 7.0.5 | switched to manual HMAC (sees Q3) |
| Spring AI M7 `StreamingChatModel` | TG-16 | ✓ (already used by Phase 7) | 2.0.0-M7 | none |
| Spring Modulith 2.0.x events + `@ApplicationModuleListener` | TG-01 + TG-11 | ✓ (already used by Phase 4) | 2.0.6 | none |
| PostgreSQL 18 `ON CONFLICT DO NOTHING`, `gen_random_uuid()` | various | ✓ | 18.4 | none |
| ShedLock JDBC | dedup vacuum cron | ✓ | 7.7.0 | manual cron without distributed lock (worse, but workable single-process) |
| `IpAddressMatcher` (Spring Security) | webhook IP allowlist | ✓ (Spring Security ships) | 7.0.5 | manual `InetAddress.getByName` + CIDR parse (worse) |
| Telegram BotFather (external) | TG-05 bot registration | manual user step | n/a | docs/integrations/telegram-setup.md |
| QR code library for pairing dialog | TG-18 | not currently in `apps/web` | — | render via backend `qrPngBase64` field on `/api/integrations/telegram/pairing` response (DataMatrix or QRCode via `com.google.zxing` already? — verify; else use simple SVG QR via `qrcode` npm) |

**Missing dependencies with no fallback:** Bucket4j (must add).
**Missing dependencies with fallback:** QR code library (backend renders + base64 inline → frontend `<img src=>`).

---

## 22. Validation Architecture (TL;DR for Quick Reference)

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito (existing); ArchUnit 1.4.2 (existing) |
| Config file | `backend/core/src/test/resources/application-test.yml` (existing) + new fixtures `backend/api/src/test/resources/telegram-fixtures/` |
| Quick run command | `./gradlew :backend:core:test --tests "*Telegram*"` + `./gradlew :backend:core:test --tests "*Arch*"` |
| Full suite command | `./gradlew check` |
| Frontend test command | `pnpm --filter web test` + `pnpm --filter web exec playwright test telegram` |

### Phase Requirements → Test Map (subset; full table in plan-phase)

| Req ID | Behavior | Test Type | Automated Command |
|--------|----------|-----------|-------------------|
| TG-01 | TriageDecisionRecorded payload has zero body fields | ArchUnit | `./gradlew :backend:core:test --tests TriageDecisionRecordedFieldShapeArchTest` |
| TG-02 | MailActionService is only Gmail mutation surface | ArchUnit | `./gradlew :backend:core:test --tests MailActionServiceArchTest` |
| TG-03 | `outbound_action_audit` row written per send | Integration (Mockito spy) | `./gradlew :backend:core:test --tests OutboundActionAuditMandatoryIntegrationTest` |
| TG-04 | Bucket4j throttle 50 chats <30 in 1s | Integration | `./gradlew :backend:core:test --tests TelegramSendRateLimiterTest` |
| TG-06 | Webhook double-secret + IP allowlist | Integration | `./gradlew :backend:api:test --tests TelegramWebhookSecurityTest` |
| TG-07 | `setMyCommands` idempotent | WireMock | `./gradlew :backend:api:test --tests TelegramBotInitializerTest` |
| TG-08 | Pairing code expiry 10min | Unit | `./gradlew :backend:core:test --tests PairingCodeServiceTest` |
| TG-10 | DM-only enforcement | Integration | `./gradlew :backend:core:test --tests TelegramDmOnlyRouterTest` |
| TG-11 | Notification body-ban | ArchUnit + Logback | `./gradlew :backend:core:test --tests "*TelegramPathBodyBanTest|TelegramPrivacySweepTest*"` |
| TG-13 | Cross-actor check rejects forwarded callback | Integration | `./gradlew :backend:core:test --tests TelegramCallbackCrossActorTest` |
| TG-14 | Single Gmail send call site | ArchUnit | existing `OnlyOneGmailSendCallSiteTest` |
| TG-16 | LLM stream cadence ≥3 edits over 5s | WireMock + VirtualTime | `./gradlew :backend:core:test --tests TelegramChatStreamSinkTest` |
| TG-17 | `assistant_pending_action` CAS double-tap | Integration | `./gradlew :backend:core:test --tests AssistantPendingActionCasTest` |
| TG-18 | E2E happy path | Playwright | `pnpm --filter web exec playwright test telegram-happy-path` |
| TG-19 | Privacy sweep no body / unmasked email | Logback | included in `TelegramPrivacySweepTest` |

### Sampling Rate

- **Per task commit:** `./gradlew :backend:core:test --tests "*Telegram*" --tests "*Arch*"` (under 15s)
- **Per wave merge:** `./gradlew :backend:core:test :backend:api:test` (full backend)
- **Phase gate:** `./gradlew check` + `pnpm --filter web test` + Playwright telegram suite GREEN

### Wave 0 Gaps

- [ ] `backend/core/src/test/java/com/zeromail/core/arch/TelegramPathBodyBanTest.java` — extends ARCH-02 to messaging
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/MailActionServiceArchTest.java` — non-send Gmail mutation surface invariant
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/OutboundActionAuditMandatoryArchTest.java` — every Gmail mutation writes audit
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/TelegramOutboxDrainArchTest.java` — drain code only in worker
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/TelegramChatStreamingOnlyArchTest.java` — only `StreamingChatModel.stream` in chat path
- [ ] `backend/api/src/test/resources/telegram-fixtures/*.json` — WireMock fixtures (8 fixtures)
- [ ] `apps/web/e2e/telegram-happy-path.spec.ts` — Playwright

---

## 23. Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Compact signed code HMAC-SHA256 (pairing), session cookie (web endpoints) |
| V3 Session Management | yes | Spring Session Redis (existing) for `/api/integrations/telegram/*`; stateless for webhook |
| V4 Access Control | yes | Cross-actor permission check on every callback (TG-13); tenant_id scoping via `@TenantId` |
| V5 Input Validation | yes | Bean Validation on REST DTOs; JSON schema validation on outbox payload; CHECK constraints on enum-shaped DB columns |
| V6 Cryptography | yes | HMAC-SHA256 via `javax.crypto.Mac`; never hand-roll. Secret managed via env + key rotation drill (deferred follow-on) |
| V7 Errors & Logging | yes | Privacy logging format `event=telegram.* tenantId={}`; no body/prompt/completion/token (TG-19) |
| V8 Data Protection | yes | ARCH-02 extension to Telegram surface (`TelegramPathBodyBanTest`) |
| V9 Communication | yes | HTTPS enforced; secret_token + IP allowlist on webhook |

### Known threat patterns for {Telegram + Spring Boot 4 + Spring AI}

| Pattern | STRIDE | Standard mitigation |
|---------|--------|---------------------|
| Webhook spoofing (forged Telegram POST) | Spoofing | secret_token + IP allowlist (TG-06) |
| Callback hijack (forwarded button) | Spoofing | cross-actor `callback_query.from.id == account.telegram_user_id` (TG-13) |
| Replay attack (paired link reuse) | Tampering | Compact signed code with iat + TTL 10min; idempotent consume via `ON CONFLICT` |
| Token forgery (tamper deep-link) | Tampering | HMAC-SHA256 truncated 16 bytes; constant-time compare |
| Body content leak in logs | Information Disclosure | `TelegramPathBodyBanTest` ArchUnit + `TelegramPrivacySweepTest` regex sweep |
| Body content leak in Telegram message | Information Disclosure | Notification renderer uses only header + classification + action; never gmail body |
| Prompt injection via Telegram free-text | Repudiation / Elevation | Existing chat sanitization pipeline (Phase 2C) applied unchanged — `ResponseSurface=TELEGRAM` doesn't bypass sanitizers |
| Rate-limit DDoS via 401-spam | DoS | Bucket4j 10/min per source IP on webhook 401 path |
| Outbox poison message (malformed payload) | DoS | DLQ via `processing_job.status='DEAD_LETTER'` + admin requeue (Phase 8E) |
| Telegram bot token theft | Spoofing / Elevation | Env var only (never in DB, never in logs); rotation drill deferred to follow-on |
| Cross-tenant data leak (user A sees user B notification) | Information Disclosure | `telegram_chat_id UNIQUE` + tenant_id FK in queries; integration test |

---

## 24. Project Constraints (from CLAUDE.md)

- **No Lombok.** Records for DTOs / events / config props.
- **No WebFlux.** Spring MVC + virtual threads.
- **No `javax.*`** (Jakarta only) — except `javax.crypto.Mac` which is still `javax.crypto` standard JDK (verify: yes, JDK keeps `javax.crypto` for crypto APIs — only Servlet/Persistence moved to `jakarta.*`).
- **No raw HTTP LLM outside `core.llm.gateway.springai`.**
- **No non-streaming fallback for chat.** Telegram-edit-rate-limit fallback is transport layer only (per CLAUDE.md).
- **No LLM prompt/completion logging.**
- **No polling Gmail.** Pub/Sub-driven only (no change in Phase 10).
- **Liquibase YAML changelogs only.**
- **Backend enterprise naming.** No `req`/`res`/`repo`/`svc`/`cfg`/`ctx`/`msg`/`err`/`ex`/`e`/`conn`/`tx`. Use `request`, `response`, `telegramAccountRepository`, `mailActionService`, `telegramProperties`, `tenantContext`, `telegramUpdate`, `webhookAuthenticationException`.
- **Single Gmail send call site.** `OnlyOneGmailSendCallSiteTest` stays at 1 match.
- **No 3rd-party Telegram SDK.**
- **No `rubenlagus/TelegramBots`.**
- **In-memory Bucket4j only** for v1.3 (Redis backend deferred).
- **DM-only enforcement** at router entry.
- **Privacy: body-ban extends to Telegram payload.** Draft-body carve-out (user-authored send/reply/forward args) remains exempt only in `chat.preview` subpackage.

---

## 25. Assumptions Log

| # | Claim | Section | Risk if wrong |
|---|-------|---------|---------------|
| A1 | Bucket4j has no version 9.x on Maven Central at 2026-05-28 | Q2 | If 9.x exists, plan would pin 8.19.0 vs target 9.x — minor; 8.19.0 still compatible |
| A2 | JWT compact format will exceed 64 char limit | Q3 | If we find a compact JWT variant <64 chars, can switch back — research recommends manual HMAC anyway for security simplicity |
| A3 | `processing_job.job_type` has NO CHECK constraint | Q6 | Verified via grep — claim is `[VERIFIED: codebase grep]` |
| A4 | `IpAddressMatcher` exists in Spring Security 7.0.5 | Q8 | Spring Security has had `IpAddressMatcher` since 3.x — high confidence |
| A5 | `Flux.sample(Duration)` safer than `bufferTimeout(N, D)` for rate-limited downstream | Q5/Pitfall 6 | Cadence may differ slightly — plan-phase WireMock should validate; CONTEXT D-06 still locks numeric defaults |
| A6 | Dedup table preferred over generated-col day bucket (Q7 Alt A vs B) | Q7 | If team wants single-table architecture, can switch to B; A is cleaner for ops |
| A7 | `@ApplicationModuleListener` triggers `@Async` automatically | Q4 | Confirmed via Spring Modulith 2.0.6 API docs |
| A8 | Worker process scans `com.zeromail.core` package per Phase 4 plan 05 decision | Q4/Section 6 | Confirmed by STATE.md "worker component scanning already includes com.zeromail.core" |

---

## 26. Open Questions

1. **`@Order` collision between TelegramWebhookSecurityConfig and PubSubSecurityConfig (both want @Order(1))**
   - What we know: Spring picks one arbitrarily when same order; existing Phase 2A uses `@Order(1)`.
   - What's unclear: Whether plan should change Pub/Sub to @Order(1) and Telegram to @Order(2), or both with explicit numeric ordering.
   - Recommendation: Use `@Order(2)` for Telegram (still ahead of session default); leave Pub/Sub `@Order(1)` untouched. Integration test verifies path routing.

2. **QR code generation library for pairing dialog**
   - What we know: `apps/web` does not currently have QR library; backend may need `com.google.zxing:core` (verify) for `qrPngBase64` field.
   - What's unclear: Whether ZXing already in transitive deps (Google API libraries sometimes ship ZXing).
   - Recommendation: Plan-phase Wave 0 verify ZXing presence; otherwise add `com.google.zxing:core` to backend and produce PNG inline.

3. **Whether to add `processing_job.job_type` CHECK constraint in Phase 10**
   - What we know: No CHECK exists today; phase introduces `MESSAGING_NOTIFICATION` value.
   - What's unclear: Production data may have unexpected values from Phase 4/8E that we'd need to enumerate.
   - Recommendation: Skip CHECK; rely on enum-at-write-site enforcement + ArchUnit. Cheaper, lower risk.

4. **X-Forwarded-For configuration on VPS reverse proxy**
   - What we know: Single VPS hosts reverse proxy; `ForwardedHeaderFilter` may not be enabled.
   - What's unclear: Whether existing PubSub IP-based checks work today or if they're bypassed.
   - Recommendation: Plan-phase task to verify ForwardedHeaderFilter status; required for Telegram IP allowlist correctness.

5. **`notification_filter` quietHours field — keep or defer**
   - What we know: CONTEXT.md `Claude's Discretion`: initial shape includes quietHours but planner may simplify.
   - What's unclear: Whether UI work in this phase has bandwidth.
   - Recommendation: Backend reserves field in JSONB; UI deferred to follow-on (per CONTEXT Deferred).

6. **Existing chat preview card rendering parity (web SSE) for Telegram**
   - What we know: Phase 7 web SSE chat preview card uses React component. Telegram needs equivalent text + inline keyboard rendering.
   - What's unclear: Is the textual rendering logic reusable, or does it need a parallel renderer in `core.messaging.telegram.chat`?
   - Recommendation: Extract pure-Java renderer from `chat.preview` to `chat.domain.PreviewCardRenderer`; Telegram + web both consume.

---

## 27. Sources

### Primary (HIGH confidence)
- `core.telegram.org/bots/api` — Bot API methods, `secret_token`, `setMyCommands`, `Update` shape — [Telegram Bot API](https://core.telegram.org/bots/api)
- `core.telegram.org/api/links` — Deep link 64-char limit — [Deep links](https://core.telegram.org/api/links)
- `core.telegram.org/bots/faq` — Rate limits 1/s/chat, 30/s global — [Bots FAQ](https://core.telegram.org/bots/faq)
- `docs.spring.io/spring-security/reference/api/...` — `NimbusJwtEncoder`, `SecurityFilterChain`, `csrf().ignoringRequestMatchers` — [Spring Security 7 docs](https://docs.spring.io/spring-security/reference/)
- `docs.spring.io/spring-modulith/docs/current/api/.../ApplicationModuleListener.html` — `@ApplicationModuleListener` semantics — [Spring Modulith 2.0.6 API](https://docs.spring.io/spring-modulith/docs/current/api/org/springframework/modulith/events/ApplicationModuleListener.html)
- `docs.spring.io/spring-modulith/reference/events.html` — Event delivery patterns — [Working with Application Events](https://docs.spring.io/spring-modulith/reference/events.html)
- `docs.spring.io/spring-ai/reference/api/chatmodel.html` — `StreamingChatModel.stream()` Flux — [Chat Model API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)
- `bucket4j.com/8.14.0/toc.html` — Bandwidth/Bucket builder — [Bucket4j 8.14.0 Reference](https://bucket4j.com/8.14.0/toc.html)
- `bucket4j.com/` — Current 8.19.0 — [Bucket4j current](https://bucket4j.com/)
- `central.sonatype.com/artifact/com.bucket4j/bucket4j-core` — Maven coordinates — [Maven Central Bucket4j](https://central.sonatype.com/artifact/com.bucket4j/bucket4j-core)
- `postgresql.org/docs/current/indexes-partial.html` — Partial indexes IMMUTABLE requirement — [Partial Indexes](https://www.postgresql.org/docs/current/indexes-partial.html)
- `postgresql.org/docs/current/sql-createindex.html` — CREATE INDEX — [CREATE INDEX](https://www.postgresql.org/docs/current/sql-createindex.html)
- `docs.liquibase.com/change-types/drop-check-constraint.html` — `dropCheckConstraint` — [Liquibase dropCheckConstraint](https://docs.liquibase.com/change-types/drop-check-constraint.html)
- `docs.liquibase.com/change-types/add-check-constraint.html` — `addCheckConstraint` — [Liquibase addCheckConstraint](https://docs.liquibase.com/change-types/add-check-constraint.html)
- `wiremock.org/docs/junit-jupiter/` — JUnit 5 integration — [WireMock JUnit Jupiter](https://wiremock.org/docs/junit-jupiter/)
- Codebase (verified via Grep + Read):
  - `backend/core/src/main/resources/db/changelog/changes/068-catalog-tables-prep.yaml` — `processing_job` schema, no CHECK on `job_type`
  - `backend/core/src/main/resources/db/changelog/changes/078-processing-job-extend.yaml` — `admin_requeue_count`, `last_failure_reason`
  - `backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java` — `@Order(1)` SecurityFilterChain pattern
  - `backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java` — single Gmail send call site invariant
  - `backend/core/src/test/java/com/zeromail/core/arch/ChatPersistenceContentBanTest.java` — ARCH-02 body-ban pattern
  - `backend/core/src/main/java/com/zeromail/core/chat/package-info.java` — `@ApplicationModule` allowedDependencies pattern
  - `backend/core/src/main/java/com/zeromail/core/outbound/package-info.java` — Outbound module deps
  - `gradle/libs.versions.toml` — Boot 4.0.6, Spring AI 2.0.0-M7, Modulith 2.0.6, ArchUnit 1.4.2, ShedLock 7.7.0

### Secondary (MEDIUM confidence)
- gramio.dev/telegram/methods/setmycommands — Idempotency notes — [GramIO setMyCommands](https://gramio.dev/telegram/methods/setmycommands)
- nguyenthanhluan.com/en/glossary/secret_token-for-setwebhook-en — secret_token best practices — [secret_token glossary](https://nguyenthanhluan.com/en/glossary/secret_token-for-setwebhook-en/)
- github.com/MarcGiffing/bucket4j-spring-boot-starter — Boot 4 compat — [bucket4j-spring-boot-starter](https://github.com/MarcGiffing/bucket4j-spring-boot-starter)
- gramio.dev/rate-limits — Telegram rate-limit community notes — [Telegram rate limits](https://gramio.dev/rate-limits)
- baeldung.com/spring-bucket4j — Bucket4j Spring patterns — [Rate Limiting with Bucket4j](https://www.baeldung.com/spring-bucket4j)
- github.com/reactor/reactor-core/issues/1557 — `bufferTimeout` overflow — [bufferTimeout overflow](https://github.com/reactor/reactor-core/issues/1557)
- github.com/reactor/reactor-core/issues/3012 — `OverflowException` patterns — [OverflowException + bufferTimeout](https://github.com/reactor/reactor-core/issues/3012)
- gitlab.com/gitlab-org/gitlab/-/issues/339091 — `NOW()` IMMUTABLE error empirical — [GitLab NOW() IMMUTABLE](https://gitlab.com/gitlab-org/gitlab/-/issues/339091)

### Tertiary (LOW confidence — verify in plan-phase)
- D-07 `bucket.addTokens(-cap); bucket.replenishAt(now()+retry_after)` cú pháp — **API does NOT exist in Bucket4j 8.19; planner MUST replace with `ConcurrentMap<Long, Instant>` pattern** (research-driven correction)
- Whether reverse-proxy on VPS forwards `X-Forwarded-For` — codebase did not verify (env-dependent)
- Whether `com.google.zxing:core` already transitively included — codebase scan inconclusive

---

## 28. Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Bucket4j 8.19.0 verified Maven Central; Spring Boot 4.0.6 / Spring AI M7 / Modulith 2.0.6 already in `libs.versions.toml`
- Architecture: HIGH — patterns are direct copies of established Phase 2A (`PubSubSecurityConfig`), Phase 4 (`@ApplicationModuleListener`), Phase 7 (`ChatOrchestrator` + `ChatStreamSink`), Phase 8E (`processing_job` SKIP LOCKED)
- Pitfalls: HIGH — Pitfall 1 (NOW() in index) verified via PostgreSQL docs; Pitfall 2 (JWT > 64 chars) verified arithmetically; Pitfall 3 (Bucket4j `replenishAt` does not exist) verified via API docs grep
- D-07 verbatim implementability: LOW — CONTEXT D-07 cú pháp is research-corrected; planner must use alternative pattern

**Research date:** 2026-05-28
**Valid until:** 2026-06-28 (30 days — Spring Boot 4 / Spring AI M7 / Bucket4j 8.19 all stable lines; Telegram Bot API itself rarely changes breaking)

---

## RESEARCH COMPLETE
