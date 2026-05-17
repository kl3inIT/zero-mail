# Phase 8: Bulk Unsubscribe Campaign — Research

**Researched:** 2026-05-17
**Domain:** RFC 8058 one-click unsubscribe, RFC 6068 mailto, Postgres-backed worker queue (SKIP LOCKED), Redis throttle bucket, Spring Modulith new module (`core.cleanup`), ArchUnit boundary, Liquibase YAML schema, Next.js multi-select + job polling UX.
**Confidence:** HIGH (RFC + Spring stack + project patterns all verified against authoritative sources or existing codebase). MEDIUM-LOW only on a few discretion items flagged in Open Questions.

---

## Project Constraints (from CLAUDE.md)

These directives must be honored by every plan and task in Phase 8:

- **Stack lock:** Java 25 + Spring Boot 4.0.6 + Gradle 9 Kotlin DSL + Spring AI 2.0.0-M6. No Lombok. No WebFlux. No `javax.*`. No raw HTTP outside Spring AI adapter — **explicit exception** carved out for `core.cleanup.UnsubscribeHttpClient` (RFC 8058 POST) and `core.cleanup.UnsubscribeMailtoSender` (Gmail send-as-self).
- **Queue lock:** Postgres-backed (`SKIP LOCKED` + outbox). Redis is **rate-limit / cache / session only**, never a queue.
- **Privacy lock:** No long-term storage of email bodies / subjects / LLM prompts / completions / tokens. No PII in logs (sender_domain OK; full sender_email masked or hashed).
- **Write surface lock:** v1 only `label / archive / save_draft`. Auto-send forbidden. `UNSUBSCRIBE` MUST NOT be added to `RuleActionType` — campaign is a tách biệt code path.
- **Backend style lock:** Domain-revealing names — no `req/res/svc/cfg/ctx/msg/err/ex/e`. Use `request`, `response`, `unsubscribeAttempt`, `gmailMessage`, `processingJob`, etc.
- **DTO/entity lock:** Records cho DTOs, classes cho entities, `OrderedEnum`/`IdentifiedEnum` + `fromId` fail-loud cho enum state machines.
- **Mail provider lock:** Gmail only (v1). Pub/Sub push for ingest. KHÔNG polling.
- **Test discipline:** Smallest Spring Boot slice; never H2 (Testcontainers Postgres); `@MockitoBean` not `@MockBean`; never call real LLM/Gmail in `./gradlew test`; ArchUnit > runtime test khi enforce boundary.

---

## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01 … D-15 — chốt từ /gsd:discuss-phase)

**Worker job framework:**
- **D-01:** Generic `processing_job` table (Postgres outbox). Cột: `id UUID`, `tenant_id`, `job_type` enum (`UNSUBSCRIBE_CAMPAIGN` v1), `payload JSONB`, `status` (QUEUED/RUNNING/COMPLETED/FAILED), `attempts INT`, `next_run_at`, `heartbeat_at`, `created_at`, `started_at`, `finished_at`, `failure_reason`. Reusable cho SEED-009.
- **D-02:** Worker pickup = continuous poll mỗi 2s với `SELECT ... FROM processing_job WHERE status='QUEUED' AND next_run_at <= NOW() ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1`. UPDATE → RUNNING + heartbeat.
- **D-03:** Crash recovery = Reaper batch `@Scheduled(fixedDelayString=60s)` reset stale RUNNING (`heartbeat_at < NOW() - INTERVAL '5 minutes'`) về QUEUED + attempts++.
- **D-04:** Transactional boundary tại POST `/execute`: 1 transaction INSERT campaign row + N attempt rows (PENDING) + 1 processing_job row. GET trả `perSender[]` đầy đủ từ QUEUED.

**Mailto + auto-send boundary:**
- **D-05:** New `UnsubscribeMailtoSender` trong `core.cleanup.application` với annotation boundary mở rộng từ Phase 4. ArchUnit cover. **KHÔNG extend `TriageGmailWriter`** (SRP).
- **D-06:** `sendUnsubscribeMailto(...)` validate: (a) URL `mailto:`, (b) recipient từ `List-Unsubscribe` header persist trong `mail_message_observed`, (c) body cố định "unsubscribe". ArchUnit `TriageGmailWriteBoundaryTest` extend cover class mới.
- **D-07:** `UnsubscribeHttpClient` dùng Spring `RestClient` synchronous. `redirectPolicy=NEVER` (RFC 8058 cấm). Connect timeout 5s, read timeout 10s. ArchUnit ban `new HttpClient()` / `RestClient.create()` ngoài file `UnsubscribeHttpClient.java`.
- **D-08:** Success gate (gate archive): chỉ HTTP `200`, `202`, `204` → state=OK. 3xx / 4xx / 5xx / timeout / IO → FAILED với failureReason chi tiết. Per-sender atomic: FAILED → KHÔNG archive.

**List-Unsubscribe persistence:**
- **D-09:** Extend `mail_message_observed` với 3 cột: `list_unsubscribe_url VARCHAR(2048) NULL`, `list_unsubscribe_mailto VARCHAR(512) NULL`, `list_unsubscribe_one_click BOOLEAN NOT NULL DEFAULT false`. **KHÔNG sidecar table, KHÔNG JSONB** (CLAUDE.md JSONB chỉ cho rule matchers).
- **D-10:** Backfill = forward-only. Row cũ giữ NULL. Candidate query filter `(list_unsubscribe_url IS NOT NULL OR list_unsubscribe_mailto IS NOT NULL)`. User chờ 30 ngày candidate window.
- **D-11:** URL `https://` validation = parse-time (DROP non-HTTPS) + execute-time re-validate. Persistent invariant: mọi row có `list_unsubscribe_url` LUÔN là `https://...`.

**Frontend:**
- **D-12:** Route layout = cleanup namespace anticipate SEED-009: `/cleanup/unsubscribe-campaign`, `/cleanup/unsubscribe-campaign/[jobId]`, `/cleanup/suppression`. Sidebar nav "Cleanup" group.
- **D-13:** Features = `apps/web/features/cleanup/unsubscribe-campaign/{api,hooks,components,query-keys.ts}` + `apps/web/features/cleanup/suppression/{...}`. Tách sub-feature thay vì 1 folder lớn.
- **D-14:** Entry point = menu only. KHÔNG can thiệp Phase 7 `TopSendersPanel`. CTA cross-link deferred.
- **D-15:** Status polling = TanStack Query `refetchInterval: 2000` khi status ∈ {QUEUED, RUNNING}, dừng khi terminal.

### Claude's Discretion (planner / executor được quyết)
- i18n namespace keys chi tiết (`cleanup.unsubscribe.*`, `cleanup.suppression.*`).
- Exact package layout trong `core.cleanup` (domain/application/persistence/projection/exception per CONVENTIONS §2).
- Reaper batch class placement (likely `backend/worker/src/main/java/com/zeromail/worker/cleanup/ProcessingJobReaperBatch.java`).
- `processing_job` payload schema cho `UNSUBSCRIBE_CAMPAIGN` (`{"campaignId":"uuid"}`).
- Throttle bucket key format chính xác (đề xuất trong section 5 dưới đây).
- Candidate query data source (default = `CandidateQueryService` mới trong `core.cleanup`).
- ArchUnit rule chi tiết.
- Recipient parsing từ `mailto:` URI (Java `URI` built-in vs manual).
- UI risk badge color tokens (`SAFE`=teal, `NO_HEADER_DISABLED`=muted, `SUPPRESSED_BLOCKED`=destructive).
- `processing_job` purge batch retention (90d retention chuẩn — deferred).

### Deferred Ideas (OUT OF SCOPE)
- CTA "Unsubscribe from this sender" trong `TopSendersPanel` (Phase 7).
- Multi-tenant team suppression list.
- Scheduled / recurring campaign.
- Provider-aware success heuristic (recognize "click to confirm" HTML).
- `processing_job` purge batch (90d retention).
- Bulk archive, cold-email blocker, attachment auto-filing (SEED-009).

---

## Phase Requirements

> Mapped to SPEC.md requirements 1–9. Planner sẽ assign ID chính thức (đề xuất: `UNS-01` … `UNS-09`).

| ID (đề xuất) | Description (rút gọn từ SPEC.md) | Research Support |
|--------------|-----------------------------------|------------------|
| UNS-01 | Candidate discovery API — `GET /api/unsubscribe/candidates?window=30d&limit=25` trả mảng sender + `unsubscribeMethod` ∈ {ONE_CLICK, MAILTO, NONE} + suppressed flag. | §8 (DB schema for `mail_message_observed` extension cột 039); §4 (CandidateQueryService pattern). |
| UNS-02 | Suppression list CRUD + auto-add khi user reply ≥1 / 90d. | §8 (`sender_suppression` schema 042); §9 (audit-based heuristic). |
| UNS-03 | Campaign preview (dry-run) — POST `.../preview` với 25-sender + 2000-msg cap; risk badge per sender. | §10 (frontend Dialog pattern); §11 (preview test). |
| UNS-04 | Campaign execute (async job) — POST `.../execute` trả jobId; worker per-sender atomic; throttle 1/domain/60s + 10/domain/h. | §4 (processing_job framework); §5 (Redis throttle); §11 (testcontainers integration). |
| UNS-05 | Campaign status & per-sender state — GET `.../{jobId}` trả progressPct + perSender[]; frontend poll 2s. | §10 (TanStack polling); D-15. |
| UNS-06 | Per-sender retry — POST `.../senders/{email}/retry` idempotent (409 nếu OK). | §4 (re-enqueue step pattern). |
| UNS-07 | Undo campaign trong 30 ngày → restore INBOX + remove label `Zero Mail/Unsubscribed`; HTTP 410 sau 30d. | §9 (audit `revertedAt`); existing `TriageGmailWriter.restoreToInbox` + `TriageUndoPolicy.UNDO_WINDOW`. |
| UNS-08 | HTTP unsubscribe gate — `UnsubscribeHttpClient` chỉ chấp nhận URL `https://` đến từ `List-Unsubscribe` header persist. ArchUnit ban HTTP outside boundary. | §2 (RFC 8058); §3 (RestClient); §7 (ArchUnit rules). |
| UNS-09 | Privacy invariant — `CleanupPrivacySweepTest` mirror `TriagePrivacySweepTest`. KHÔNG log full sender email / body / subject. | §11 (privacy sweep pattern). |

---

## Executive Summary

Phase 8 là phase phức tạp về **multi-protocol HTTP / mail boundary + async worker framework + ba-bốn-năm bảng DB mới + multi-screen frontend** nhưng độ phức tạp **được khoanh vùng tốt** nhờ 15 lock decisions trong CONTEXT.md. Approach:

1. **Persist trước, execute sau** — extend `mail_message_observed` với 3 cột List-Unsubscribe (forward-only); người dùng đợi ≥30 ngày candidate window có data trước khi UX hữu ích. Đây là trade-off hợp lý: không Gmail quota burn cho backfill.
2. **Generic `processing_job` framework** — table + worker poll + reaper batch là asset chiến lược tái dùng cho cả SEED-009 (bulk archive, cold-email blocker, attachment filing). Job type enum khởi đầu chỉ một giá trị `UNSUBSCRIBE_CAMPAIGN`, mở rộng dần.
3. **HTTP boundary đơn — class duy nhất** — `UnsubscribeHttpClient` là class duy nhất trong toàn `core.cleanup.*` được phép tạo `RestClient` hoặc `HttpClient`, enforce bằng ArchUnit pattern y hệt `TriageGmailWriteBoundaryTest`. `UnsubscribeMailtoSender` là class thứ hai (sibling của `TriageGmailWriter`) duy nhất được phép gọi `Gmail.users().messages().send()`.
4. **Throttle = Redis INCR + EXPIRE per-domain** — tách key prefix theo tenant để tránh noisy-neighbor across tenants. Khi quota vượt → worker reschedule chứ KHÔNG FAILED.
5. **Undo = thuần restore Gmail labels** — không cố gắng "un-unsubscribe" với provider (RFC 8058 là one-way). Reuse `TriageGmailWriter.restoreToInbox` + `removeLabel` đã có sẵn từ Phase 4.

**Primary recommendation:** Bắt đầu phase với 2 plan song song không phụ thuộc: (A) DB migrations + List-Unsubscribe extraction extend (bắt đầu thu data ngay), (B) `processing_job` framework + reaper. Plan campaign API/worker/UX bám sau khi A+B xong.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|-----------------|-----------|
| List-Unsubscribe header parsing | `backend/core` (`core.gmail.usecases.GmailPreviewReadService`) | `backend/core` (persistence layer Phase 2A) | Giữ Gmail header parsing chỉ ở 1 chỗ; tránh duplicate parser. |
| Candidate query (top sender + filter suppressed) | `backend/core` (`core.cleanup.application.CandidateQueryService`) | — | Read-side projection, `@Transactional(readOnly=true)` JdbcTemplate giống `AnalyticsSummaryQueryService`. |
| Campaign preview validation (25/2000 cap) | `backend/api` (controller validates cap) → `backend/core` (CandidateQueryService re-uses) | — | Cap check là HTTP contract → controller layer; CONVENTIONS §1 (thin controllers nhưng được phép validate input). |
| Campaign execute (transaction INSERT campaign + attempts + job row) | `backend/core` (`core.cleanup.application.CampaignExecuteService` `@Transactional`) | — | Multi-row commit là service-owned transaction (CONVENTIONS §1). |
| Worker job dispatch loop | `backend/worker` (`worker.cleanup.ProcessingJobWorker`) | `backend/core` (handler injection) | Worker process; `SKIP LOCKED` chạy trên worker JVM. |
| HTTP POST RFC 8058 | `backend/core` (`core.cleanup.application.UnsubscribeHttpClient`) | — | Class duy nhất trong cleanup được phép tạo RestClient. |
| Mailto send-as-self | `backend/core` (`core.cleanup.application.UnsubscribeMailtoSender`) | — | Sibling boundary class cùng cấp `TriageGmailWriter`. |
| Gmail label apply + archive (post-unsubscribe step) | `backend/core` (reuse `TriageGmailWriter.applyLabel` + `archiveSkipInbox`) | — | KHÔNG duplicate Gmail write code — reuse Phase 4 boundary class. |
| Per-sender state polling | `backend/api` GET endpoint → `apps/web` TanStack Query | `apps/web` (TanStack `refetchInterval`) | Frontend owns polling cadence; backend trả snapshot. |
| Undo (restore INBOX) | `backend/core` (`core.cleanup.application.CampaignUndoService`) → reuse `TriageGmailWriter.restoreToInbox` + `removeLabel` | — | Cùng pattern `TriageUndoService` Phase 4. |
| Domain throttle bucket | `backend/worker` (Redis client) | `backend/core` (key format constants) | Throttle là worker-side gating; key format có thể chia sẻ trong core. |

---

## Standard Stack

### Core (đã có sẵn — reuse)

| Library / Component | Version | Purpose | Why Standard |
|---------------------|---------|---------|--------------|
| Spring `RestClient` (Framework 7) | 7.0.7 (Boot 4.0.6 managed) | Synchronous HTTP POST cho RFC 8058 | Virtual-thread friendly; project đã dùng cho BYOK validation (`ByokValidationGateway`). `RestClient.Builder` bean đã sẵn trong `RestClientConfig.java`. [CITED: Spring Framework 7.0.7 reference] |
| JDK `java.net.http.HttpClient` | JDK 25 built-in | Underlying transport — `RestClient.Builder` đang dùng `JdkClientHttpRequestFactory(httpClient)` | Đã có `followRedirects(HttpClient.Redirect.NEVER)` trong `RestClientConfig.java`. **Cleanup module CẦN một bean riêng vì redirect policy bắt buộc per RFC 8058**. [VERIFIED: `backend/core/src/main/java/com/zeromail/core/config/RestClientConfig.java`] |
| Spring Data Redis (Lettuce) | Boot 4.0.6 managed | Throttle bucket INCR + EXPIRE per-domain | `StringRedisTemplate` đã được dùng trong `RedisDistributedLock.java`. [VERIFIED: codebase] |
| Spring Data JDBC / `JdbcTemplate` | Boot 4.0.6 managed | Worker poll `SELECT FOR UPDATE SKIP LOCKED` | Read-side hot path per CONVENTIONS §2; pattern y hệt `AuditLogQueryService.java`. |
| Spring Data JPA + Hibernate 7 | Boot 4.0.6 managed | Campaign + attempt + suppression entity (write-side aggregate) | Project pattern: write = JPA, hot read = JdbcTemplate. |
| Spring Modulith | Boot 4.0.6 managed | New `core.cleanup` module với `@ApplicationModule` | Existing pattern, e.g. `core.analytics.package-info.java`. |
| Spring Scheduling (`@Scheduled`) + ShedLock | đã có sẵn trong `backend/worker` | Reaper batch fixedDelay=60s | `ShedLockConfig.java` đã `@EnableScheduling` + `JdbcTemplateLockProvider` (lock table). [VERIFIED: `backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java`] |
| Liquibase 5.0.2 (YAML) | Boot 4.0.6 managed | Schema migrations | Sequential numbering — next free số là **`041`** (trên thực tế đã có 040 — không phải 038 như CONTEXT.md ghi nhầm). [VERIFIED: `backend/core/src/main/resources/db/changelog/changes/` ls — max 040]. |
| Google Gmail API client (`google-api-services-gmail`) | đã có | `Gmail.users().messages().send()` cho mailto | Boundary class `UnsubscribeMailtoSender` only. |
| ArchUnit 1.4.x | đã có | Boundary tests cho HTTP + Gmail send + Modulith deps | Existing pattern `TriageGmailWriteBoundaryTest`. |

### Supporting (frontend)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| shadcn `dialog` | đã có | Preview modal | Per CONVENTIONS §7. Verify `pnpm dlx shadcn@latest add dialog` nếu chưa cài. |
| shadcn `checkbox` | đã có | Multi-select candidate table | Per CONVENTIONS §7. |
| shadcn `data-table` / `table` | đã có | Candidate list + per-sender state | Existing pattern (Phase 7 TopSendersPanel). |
| shadcn `badge` | đã có | Risk badges (SAFE/NO_HEADER_DISABLED/SUPPRESSED_BLOCKED) | Color tokens — teal / muted / destructive (Phase 7 D-15 palette). |
| TanStack Query 5.100.1 | đã có | `useQuery` polling + mutation | `refetchInterval` từ data-conditional callback. |
| `openapi-typescript` + `openapi-fetch` | đã có | Typed client codegen | `springdoc-openapi` regen `apps/web/lib/api/openapi-types.ts` tự động khi backend ship endpoint mới. |

### Alternatives Considered (rejected)

| Instead of | Could Use | Why rejected |
|------------|-----------|--------------|
| `RestClient` (synchronous) | `WebClient` (reactive) | CLAUDE.md hard-bans WebFlux trong toàn project. |
| `processing_job` table | Spring Modulith `event_publication` (table 024) | D-01 explicit: outbox cho user-triggered async, `event_publication` chỉ cho domain events trong-process. |
| Redis Streams cho job | Postgres `SKIP LOCKED` | CLAUDE.md hard-lock "Redis NOT a queue". |
| Quartz scheduler cho retry | `processing_job.next_run_at` + worker poll | Adds dep; reuse outbox. |
| Custom JSON parser cho `mailto:` | `java.net.URI` (JDK built-in) | URI parse handles `?subject=` + `?body=` via raw query string. |
| ChromaDB / vector "newsletter classifier" | Header-based filter only | CLAUDE.md hard-ban embeddings v1; SPEC.md restricts to `List-Unsubscribe` header presence. |
| Lombok `@Builder` for attempt entity | Java 25 explicit constructor + record commands | Lombok banned. |

### Installation (no new top-level deps)

**Backend:** Không cần `build.gradle.kts` thay đổi. Tất cả lib (RestClient, Lettuce, JPA, ArchUnit, ShedLock) đã có trong Boot 4 BOM + project versions catalog.

**Frontend:** Nếu thiếu thì:
```bash
cd apps/web
pnpm dlx shadcn@latest add dialog       # nếu chưa có
pnpm dlx shadcn@latest add checkbox     # nếu chưa có
```
Verify trước bằng `ls apps/web/components/ui/dialog.tsx` etc.

### Version Verification

| Component | Verified version | Source |
|-----------|------------------|--------|
| Spring Framework | 7.0.7 | Spring Boot 4.0.6 BOM (project CLAUDE.md) |
| `RestClient.Builder` redirect-NEVER pattern | Available (verified existing usage) | `backend/core/src/main/java/com/zeromail/core/config/RestClientConfig.java` |
| Liquibase next free changelog id | **041** | `ls backend/core/src/main/resources/db/changelog/changes/` → max 040 |
| Spring Modulith `@ApplicationModule` | Available (existing usage) | `backend/core/src/main/java/com/zeromail/core/analytics/package-info.java` |

[ASSUMED] CONTEXT.md D-09 ghi "next-039" — sai vì có `039-drop-triage-shadow-mode.yaml` và `040-triage-audit-message-ref.yaml` đã ship. **Planner phải dùng `041` trở đi**, không phải 039. ASSUMPTION 1.

---

## Package Legitimacy Audit

> Phase 8 KHÔNG cài package external mới (backend dùng Boot BOM, frontend dùng shadcn local copy + đã có sẵn TanStack Query / openapi-fetch). Bỏ qua slopcheck — không có installs.

Nếu plan executor thêm package mới (e.g., shadcn primitive chưa có), gate bằng `checkpoint:human-verify` trước `pnpm dlx shadcn@latest add ...`.

---

## RFC 8058 + RFC 6068 Protocol Details

### RFC 8058 — One-Click HTTPS Unsubscribe

**Trigger pair (cả hai header phải có mặt thì mới one-click):**
```
List-Unsubscribe: <https://example.com/unsub?token=abc>, <mailto:unsub@example.com>
List-Unsubscribe-Post: List-Unsubscribe=One-Click
```

**Bắt buộc theo RFC 8058:**

| Item | Requirement | Source |
|------|-------------|--------|
| URL scheme | MUST be `https://` (HTTP cấm) | RFC 8058 §3 [CITED: datatracker.ietf.org/doc/html/rfc8058] |
| Method | HTTPS POST | RFC 8058 §3 |
| Content-Type | `multipart/form-data` SHOULD; `application/x-www-form-urlencoded` MAY | RFC 8058 §3 |
| Body | exactly `List-Unsubscribe=One-Click` | RFC 8058 §3 |
| Redirect | Sender MUST NOT return 3xx redirects. **Client should not follow redirects.** | RFC 8058 §3.2 |
| Cookies / auth | POST MUST NOT include cookies, HTTP auth, or context info | RFC 8058 §3 |
| DKIM | Mail MUST have valid DKIM covering List-Unsubscribe* headers | RFC 8058 §4 |
| Response | Spec không quy định mã trả về; client coi 2xx = ack | RFC 8058 §3 |
| Timeout / retry | Spec không quy định | (No guidance) |

**Implications cho `UnsubscribeHttpClient`:**

1. **Content-Type chọn:** Đề xuất `application/x-www-form-urlencoded` (đơn giản hơn multipart, RFC 8058 cho phép MAY).
2. **Redirect:** `HttpClient.Redirect.NEVER` — nếu 3xx → state=FAILED (per D-08).
3. **Success codes:** `200` + `202` + `204` → OK. Mọi status ≠ 2xx (kể cả 1xx informational không nên xảy ra trên POST) → FAILED.
4. **Timeouts:** Connect 5s + read 10s per D-07. Lý do: provider unsubscribe endpoint thường nhanh (< 1s); 10s là an toàn cho slow proxy.
5. **DKIM verification:** KHÔNG ở phía client. Gmail Pub/Sub đã filter spam có DKIM fail; nếu user nhận được email vào INBOX nghĩa là DKIM đã pass. Không re-verify.

**Error mapping (D-08 cụ thể hoá):**

| Outcome | `state` | `failureReason` | Notes |
|---------|---------|------------------|-------|
| 200 / 202 / 204 | OK | NULL | Mở đường archive history. |
| 201 | OK | NULL | Hiếm trên unsubscribe nhưng vẫn 2xx — chấp nhận. |
| 3xx redirect | FAILED | `HTTP_3XX_REDIRECT` | RFC 8058 cấm redirect — provider không tuân thủ → reject. |
| 400-499 | FAILED | `HTTP_4XX_{code}` | E.g., `HTTP_4XX_410` cho expired token. |
| 500-599 | FAILED | `HTTP_5XX_{code}` | Provider downtime. |
| Connect timeout / IO | FAILED | `TIMEOUT` hoặc `NETWORK_ERROR` | Phân biệt `HttpConnectTimeoutException` vs `IOException`. |
| Java SSL handshake fail | FAILED | `SSL_HANDSHAKE_FAILED` | Edge case — provider cert issues. |
| Body content-length > 1MB | FAILED | `RESPONSE_TOO_LARGE` | Spam protection — limit response size dù không spec yêu cầu. |

### RFC 2369 — `List-Unsubscribe` Header Format

Header có thể chứa **một hoặc nhiều URI** ngăn bằng dấu phẩy, mỗi URI bọc trong `<>`:

```
List-Unsubscribe: <https://example.com/u/a1>, <mailto:unsub@example.com?subject=unsubscribe>
```

**Parsing rules:**
- Mỗi `<...>` là 1 URI riêng biệt.
- URI có thể là `https`, `http`, `mailto`. Project bỏ qua `http://` (D-11).
- Khi có cả HTTPS + mailto: **ưu tiên HTTPS** nếu cũng có `List-Unsubscribe-Post: List-Unsubscribe=One-Click`. Nếu không → fallback mailto.
- Nếu chỉ có HTTPS mà KHÔNG có `List-Unsubscribe-Post` header → coi như không one-click → fallback mailto nếu có; nếu không → `unsubscribeMethod=NONE`.

**Persistence rule (per D-09 + D-11):**
- `list_unsubscribe_url` = chỉ HTTPS URL (đã validate `startsWith("https://")`). Nếu header chỉ có mailto → cột NULL.
- `list_unsubscribe_mailto` = giá trị raw `mailto:...` (giữ nguyên subject/body params).
- `list_unsubscribe_one_click` = `true` chỉ khi cả HTTPS URL hiện diện VÀ header `List-Unsubscribe-Post: List-Unsubscribe=One-Click` hiện diện.

### RFC 6068 — `mailto:` URI Parsing

Format: `mailto:user@host?subject=...&body=...`

**Parsing approach (Claude's Discretion D-15 ghi nhận):** Dùng `java.net.URI` (JDK built-in). Java `URI`:
- `uri.getScheme()` → `"mailto"`
- `uri.getRawSchemeSpecificPart()` → `"user@host?subject=Unsubscribe&body=unsubscribe"`
- Manual split tại `?` để tách path khỏi query string.
- Tham số `subject` + `body` cần URL-decode (RFC 3986 percent-encoded).

**Pseudocode:**
```java
URI mailtoUri = URI.create(rawMailtoValue);  // throws IllegalArgumentException on malformed
if (!"mailto".equals(mailtoUri.getScheme())) {
    throw new IllegalArgumentException("not a mailto URI");
}
String schemeSpecific = mailtoUri.getRawSchemeSpecificPart();
int queryIndex = schemeSpecific.indexOf('?');
String recipientPart = queryIndex < 0 ? schemeSpecific : schemeSpecific.substring(0, queryIndex);
String queryString = queryIndex < 0 ? "" : schemeSpecific.substring(queryIndex + 1);
// Parse queryString: subject=...&body=...
String recipientEmail = URLDecoder.decode(recipientPart, StandardCharsets.UTF_8);
```

**Defensive validation:** Recipient phải là valid email — apply same regex/pattern dùng trong `extractEmailAddress` của `GmailPreviewReadService.java:587`.

**Subject + body defaults (per D-06):**
- Default subject (nếu mailto không có `?subject=`): `"unsubscribe"`.
- Default body: `"unsubscribe"`.
- Nếu URI có subject/body params → ưu tiên dùng (provider biết format mình cần).

---

## Spring `RestClient` Configuration cho `UnsubscribeHttpClient`

### Bean wiring (recommended — separate from BYOK validation bean)

`UnsubscribeHttpClient` cần redirect=NEVER + timeouts CỤ THỂ. Không reuse global `restClientBuilder` (timeouts khác — BYOK đang 5s/15s+; cleanup cần 5s/10s per D-07).

**Approach A (đề xuất):** Inner factory method trong `UnsubscribeHttpClient` class, không expose bean toàn cục.

```java
@Component
public class UnsubscribeHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final long MAX_RESPONSE_BODY_BYTES = 1024L * 1024L; // 1 MB

    private final RestClient unsubscribeRestClient;

    public UnsubscribeHttpClient() {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .version(java.net.http.HttpClient.Version.HTTP_1_1) // safer for unsubscribe endpoints
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.unsubscribeRestClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public UnsubscribeResult postOneClick(URI validatedHttpsUrl) {
        if (!"https".equalsIgnoreCase(validatedHttpsUrl.getScheme())) {
            throw new IllegalArgumentException("only https URLs accepted");
        }
        LinkedMultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("List-Unsubscribe", "One-Click");
        try {
            return unsubscribeRestClient.post()
                    .uri(validatedHttpsUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formBody)
                    .exchange((request, response) -> {
                        int statusCode = response.getStatusCode().value();
                        if (statusCode == 200 || statusCode == 202 || statusCode == 204 || statusCode == 201) {
                            return UnsubscribeResult.ok(statusCode);
                        }
                        if (statusCode >= 300 && statusCode < 400) {
                            return UnsubscribeResult.failed("HTTP_3XX_REDIRECT");
                        }
                        if (statusCode >= 400 && statusCode < 500) {
                            return UnsubscribeResult.failed("HTTP_4XX_" + statusCode);
                        }
                        return UnsubscribeResult.failed("HTTP_5XX_" + statusCode);
                    }, false);
        } catch (ResourceAccessException timeoutOrIo) {
            return UnsubscribeResult.failed(timeoutOrIo.getCause() instanceof HttpConnectTimeoutException
                    ? "TIMEOUT" : "NETWORK_ERROR");
        } catch (RestClientException restClientFailure) {
            return UnsubscribeResult.failed("NETWORK_ERROR");
        }
    }
}
```

**Approach B (alternative):** `@Bean RestClient unsubscribeRestClient(...)` named bean trong `core.cleanup.config` package. Pros: tách concern cleanly. Cons: thêm 1 file config + ArchUnit guard phải allow class này.

**Recommendation:** Approach A — single-file boundary class. Inner `HttpClient` construction is one-time init, không expose bean ra ngoài. Còn ArchUnit thì cấm mọi class khác trong `..core.cleanup..` tạo `HttpClient` / `RestClient` — class `UnsubscribeHttpClient` được exempt by name (cùng pattern `TriageGmailWriter`).

### Exchange vs Retrieve

Dùng `exchange(...)` (như code trên) thay vì `retrieve()` vì:
1. `retrieve()` auto-throw 4xx/5xx → cần catch nhiều exception types và inspect statusCode lại — verbose.
2. `exchange()` cho full control — match D-08 success-gate exactly.
3. 3xx redirect (RFC 8058 cấm) → `retrieve()` mặc định không throw 3xx → fall through → broken. `exchange()` xử lý explicit.

Reference: Spring Framework 7.0 docs (verified via WebFetch above).

### Error-mapping summary table (re-stated for planner clarity)

| Java exception/condition | `failureReason` value |
|--------------------------|------------------------|
| 2xx (200/201/202/204) | (NULL — state=OK) |
| 3xx | `HTTP_3XX_REDIRECT` |
| 4xx (e.g., 410 Gone) | `HTTP_4XX_410` |
| 5xx | `HTTP_5XX_503` |
| `HttpConnectTimeoutException` (wrapped in `ResourceAccessException`) | `TIMEOUT` |
| `IOException` (network reset, DNS, etc.) | `NETWORK_ERROR` |
| `SSLHandshakeException` | `SSL_HANDSHAKE_FAILED` |
| Response body > 1MB | `RESPONSE_TOO_LARGE` |
| `IllegalArgumentException` (URL not HTTPS) | `URL_NOT_HTTPS` (defensive — should never happen if D-11 enforced) |
| Provider sent URL không có audit trail trong `mail_message_observed` | `URL_NOT_FROM_LIST_UNSUBSCRIBE` (caller must pre-validate) |

---

## Postgres `processing_job` Framework Design

### Liquibase DDL sketch (`041-processing-job.yaml`)

```yaml
databaseChangeLog:
  - changeSet:
      id: 041-processing-job
      author: zeromail
      comment: >
        Generic Postgres-backed worker outbox for user-triggered async jobs. Phase 8 introduces
        UNSUBSCRIBE_CAMPAIGN as the first job_type; SEED-009 (bulk-archive, cold-email-blocker,
        attachment-filing) reuses this table with new job_type enum values. SKIP LOCKED pickup is
        the lone contract — heartbeat-based crash recovery via reaper batch (5-minute stale
        threshold) guarantees crash-safety without distributed coordination.
      changes:
        - createTable:
            tableName: processing_job
            columns:
              - column:
                  name: id
                  type: uuid
                  defaultValueComputed: gen_random_uuid()
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: tenant_id
                  type: uuid
                  constraints:
                    nullable: false
                    foreignKeyName: fk_processing_job_tenant
                    references: tenants(id)
                    deleteCascade: true
              - column:
                  name: job_type
                  type: varchar(64)
                  constraints:
                    nullable: false
              - column:
                  name: payload
                  type: jsonb
                  constraints:
                    nullable: false
              - column:
                  name: status
                  type: varchar(16)
                  defaultValue: 'QUEUED'
                  constraints:
                    nullable: false
              - column:
                  name: attempts
                  type: int
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
              - column:
                  name: next_run_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
              - column:
                  name: heartbeat_at
                  type: timestamptz
              - column:
                  name: created_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
              - column:
                  name: started_at
                  type: timestamptz
              - column:
                  name: finished_at
                  type: timestamptz
              - column:
                  name: failure_reason
                  type: varchar(255)
              - column:
                  name: updated_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
        - sql:
            sql: ALTER TABLE processing_job ADD CONSTRAINT ck_processing_job_status CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED'))
        - sql:
            sql: ALTER TABLE processing_job ADD CONSTRAINT ck_processing_job_job_type CHECK (job_type IN ('UNSUBSCRIBE_CAMPAIGN'))
        - createIndex:
            tableName: processing_job
            indexName: idx_processing_job_pickup
            columns:
              - column: { name: status }
              - column: { name: next_run_at }
              - column: { name: created_at }
        - sql:
            comment: Partial index for reaper scans of stale RUNNING rows.
            sql: CREATE INDEX idx_processing_job_running_heartbeat ON processing_job (heartbeat_at) WHERE status = 'RUNNING'
        - createIndex:
            tableName: processing_job
            indexName: idx_processing_job_tenant_type
            columns:
              - column: { name: tenant_id }
              - column: { name: job_type }
      rollback:
        - dropTable:
            tableName: processing_job
```

### Worker poll loop pseudocode (`backend/worker/src/main/java/com/zeromail/worker/cleanup/ProcessingJobWorker.java`)

```java
@Component
public class ProcessingJobWorker {

    private static final Logger log = LoggerFactory.getLogger(ProcessingJobWorker.class);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private final JdbcTemplate jdbcTemplate;
    private final UnsubscribeCampaignHandler unsubscribeCampaignHandler;
    private volatile boolean shouldRun = true;

    @PostConstruct
    void startPolling() {
        Thread.ofVirtual().name("processing-job-worker").start(this::pollLoop);
    }

    @PreDestroy
    void stopPolling() { shouldRun = false; }

    private void pollLoop() {
        while (shouldRun) {
            try {
                Optional<UUID> pickedJobId = pickQueuedJob();
                if (pickedJobId.isEmpty()) {
                    Thread.sleep(POLL_INTERVAL);
                    continue;
                }
                dispatch(pickedJobId.get());
            } catch (InterruptedException interrupt) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException pollFailure) {
                log.warn("event=processing_job_poll_failure", pollFailure);
            }
        }
    }

    @Transactional
    Optional<UUID> pickQueuedJob() {
        return jdbcTemplate.query(
                """
                SELECT id FROM processing_job
                WHERE status = 'QUEUED' AND next_run_at <= NOW()
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED LIMIT 1
                """,
                resultSet -> resultSet.next()
                        ? Optional.of(UUID.fromString(resultSet.getString("id")))
                        : Optional.empty())
                .map(pickedId -> {
                    jdbcTemplate.update(
                            "UPDATE processing_job SET status='RUNNING', started_at=NOW(), heartbeat_at=NOW(), updated_at=NOW() WHERE id = ?",
                            pickedId);
                    return pickedId;
                });
    }

    private void dispatch(UUID jobId) {
        // Read job_type + payload, route to handler, handler updates heartbeat periodically.
        // Handler returns terminal status (COMPLETED/FAILED) → UPDATE processing_job.
    }
}
```

### Reaper batch (`backend/worker/src/main/java/com/zeromail/worker/cleanup/ProcessingJobReaperBatch.java`)

```java
@Component
public class ProcessingJobReaperBatch {

    private static final Duration STALE_HEARTBEAT = Duration.ofMinutes(5);

    @Scheduled(fixedDelayString = "PT60S")
    @SchedulerLock(name = "processingJobReaper", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    @Transactional
    public void reapStaleRunning() {
        int reapedRowCount = jdbcTemplate.update(
                """
                UPDATE processing_job
                SET status='QUEUED', attempts = attempts + 1, heartbeat_at=NULL, updated_at=NOW(),
                    next_run_at=NOW()
                WHERE status='RUNNING'
                  AND heartbeat_at < NOW() - INTERVAL '5 minutes'
                """);
        if (reapedRowCount > 0) {
            log.info("event=processing_job_reaper_reaped count={}", reapedRowCount);
        }
    }
}
```

### Payload JSONB schema cho `UNSUBSCRIBE_CAMPAIGN`

```json
{
  "campaignId": "uuid"
}
```

Đủ. Worker query `unsubscribe_attempt` rows bằng `campaign_id` để biết per-sender state. Tránh thông tin trùng lặp giữa payload và relational rows.

### Single-attempt semantic

Per-sender retry (UNS-06) **không** dùng `attempts++` của `processing_job`. Retry là user-triggered → INSERT một processing_job row mới hoặc set existing campaign attempt state back to PENDING + INSERT 1 retry job. **Đề xuất:** **dùng cùng `processing_job` row** — UPDATE `processing_job SET status='QUEUED', next_run_at=NOW()` + reset specific attempt row's state to PENDING. Khi worker re-pick, handler chỉ xử lý attempt rows ở state PENDING.

[ASSUMPTION 2] Single processing_job per campaign + state-driven attempt loop. Planner xác nhận.

---

## Redis Throttle Bucket Pattern

### Key format (RECOMMENDED — Claude's Discretion item resolved here)

```
zm:throttle:unsub:tenant:{tenantId}:domain:{domain}:60s   # 60-second bucket
zm:throttle:unsub:tenant:{tenantId}:domain:{domain}:1h    # 1-hour bucket
```

**Rationale cho prefix `zm:`:** Project convention (cũng được dùng cho session keys via Spring Session). Throttle key có namespace `:throttle:` để tách khỏi locks, session, cache.

**Rationale cho per-tenant prefix:** Tránh noisy-neighbor — tenant A spamming domain X không drain quota của tenant B (cross-tenant isolation).

### INCR + EXPIRE pattern (Lettuce + StringRedisTemplate)

```java
@Component
public class UnsubscribeDomainThrottle {

    private static final long LIMIT_60S = 1L;
    private static final long LIMIT_1H = 10L;
    private static final Duration TTL_60S = Duration.ofSeconds(60);
    private static final Duration TTL_1H = Duration.ofHours(1);

    private final StringRedisTemplate stringRedisTemplate;

    public ThrottleDecision tryConsume(UUID tenantId, String senderDomain) {
        String key60 = "zm:throttle:unsub:tenant:" + tenantId + ":domain:" + senderDomain + ":60s";
        String key1h = "zm:throttle:unsub:tenant:" + tenantId + ":domain:" + senderDomain + ":1h";

        Long count60 = stringRedisTemplate.opsForValue().increment(key60);
        if (count60 != null && count60 == 1L) {
            stringRedisTemplate.expire(key60, TTL_60S);
        }
        if (count60 != null && count60 > LIMIT_60S) {
            return ThrottleDecision.deferred(TTL_60S);  // worker reschedules next_run_at += 60s
        }

        Long count1h = stringRedisTemplate.opsForValue().increment(key1h);
        if (count1h != null && count1h == 1L) {
            stringRedisTemplate.expire(key1h, TTL_1H);
        }
        if (count1h != null && count1h > LIMIT_1H) {
            return ThrottleDecision.deferred(TTL_1H);
        }
        return ThrottleDecision.allowed();
    }

    public record ThrottleDecision(boolean allowed, Duration retryAfter) {
        static ThrottleDecision allowed() { return new ThrottleDecision(true, Duration.ZERO); }
        static ThrottleDecision deferred(Duration retryAfter) { return new ThrottleDecision(false, retryAfter); }
    }
}
```

### Worker pre-step check

Trước khi POST unsubscribe cho sender X:
1. Extract `senderDomain` từ `senderEmail` (sau `@`).
2. `throttle.tryConsume(tenantId, senderDomain)`.
3. Nếu `allowed` → execute unsubscribe step.
4. Nếu `deferred(retryAfter)` → KHÔNG mark sender FAILED. Update `processing_job.next_run_at = NOW() + retryAfter`, status vẫn QUEUED, attempt state vẫn PENDING. Worker pickup tiếp theo sẽ retry sau khi TTL hết.

**Pitfall:** Race condition giữa INCR và EXPIRE — nếu Redis crash giữa hai lệnh, bucket key có thể không có TTL → vĩnh viễn block. Giải pháp: dùng Lua script atomic, **OR** dùng pattern "INCR + EXPIRE only on count == 1" (như trên — race window vẫn có nhưng <1ms; nếu mất EXPIRE thì lần INCR thứ 2 sẽ vẫn count++, lần 3 cũng vậy → khi count vượt limit thì block, EXPIRE bị skip → block vĩnh viễn). 

**Defensive:** Cho thread-safe, dùng Lua script:

```java
private static final String INCR_EXPIRE_LUA =
    "local current = redis.call('INCR', KEYS[1]); " +
    "if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end; " +
    "return current;";

DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_EXPIRE_LUA, Long.class);
Long count = stringRedisTemplate.execute(script,
        List.of(key60),
        String.valueOf(TTL_60S.toMillis()));
```

[ASSUMPTION 3] Lua script atomic INCR+EXPIRE — đề xuất, planner chốt khi viết unit test với mocked Redis.

### Test coverage cho throttle

- **Unit test:** `@DataRedisTest` (or `@SpringBootTest` slice với Testcontainers Redis) — 12 sender cùng domain → 1 thành công per 60s, 10 trong 1h, 11+ defer.
- **Integration test:** Worker fixture 12 sender cùng domain → assert deferral pattern timely (mock clock advance).

---

## Spring Modulith Module Declaration cho `core.cleanup`

### `package-info.java`

```java
/**
 * Cleanup domain: bulk unsubscribe campaigns (Phase 8) + future bulk-archive,
 * cold-email-blocker, attachment-filing (SEED-009). All write actions go through
 * UnsubscribeMailtoSender or reuse TriageGmailWriter.
 */
@ApplicationModule(
        displayName = "Cleanup",
        allowedDependencies = {
            "gmail",
            "triage",
            "analytics",
            "tenant",
            "shared.privacy",
            "shared.persistence",
            "shared.lang"
        })
package com.zeromail.core.cleanup;

import org.springframework.modulith.ApplicationModule;
```

### Module verification test

Existing `ApplicationModulesTest` (project-wide) sẽ pick up `core.cleanup` tự động. Không cần test class mới — Spring Modulith verify allowedDependencies tự động khi `Modulith.of(...)` boot. Đảm bảo:

1. `core.cleanup` chỉ import `core.gmail.*`, `core.triage.*`, `core.analytics.*`, `core.tenant.*`, `core.shared.*` symbols — KHÔNG import `core.account.*`, `core.rules.*`, `core.llm.*`, `core.billing.*`.
2. KHÔNG có module nào khác import `core.cleanup.*` (campaign là endpoint-driven, không có module nào downstream).

### Event published từ `core.cleanup` (optional)

Per CONTEXT.md `core.gmail.event.MailMessageObserved` đã có. Phase 8 có thể publish:

- `UnsubscribeCampaignCompleted(campaignId, tenantId, completedAt)` — consumer? Analytics có thể tăng counter "unsubscribe completed". **Hữu ích nhưng KHÔNG cần thiết v1** — defer.

[ASSUMPTION 4] Bỏ event publish v1. Planner thêm nếu cần invalidate frontend cache via SSE/websocket sau (defer).

---

## ArchUnit Rules cho `core.cleanup.*`

### Rule 1 — HTTP client boundary (extend existing pattern)

Cấm mọi class trong `core.cleanup.*` ngoài `UnsubscribeHttpClient` được phép tạo `RestClient` hoặc `java.net.http.HttpClient`:

```java
@ArchTest
static final ArchRule only_unsubscribe_http_client_constructs_http_client =
    noClasses()
        .that()
        .resideInAPackage("..core.cleanup..")
        .and()
        .haveSimpleNameNotEndingWith("UnsubscribeHttpClient")
        .should()
        .callConstructor(java.net.http.HttpClient.class)
        .orShould()
        .callMethod(RestClient.class, "create")
        .orShould()
        .callMethod(RestClient.class, "builder")
        .because("UNS-08: HTTP unsubscribe traffic is centralized behind UnsubscribeHttpClient.");
```

**Pitfall:** `RestClient.builder()` là static; ArchUnit pattern dùng `callMethod` với declared owner class. Verify khi viết.

### Rule 2 — Gmail send-as-self boundary (extend `TriageGmailWriteBoundaryTest`)

Update existing `TriageGmailWriteBoundaryTest.java:14` để cover cả `core.cleanup`. Pattern mở rộng:

```java
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class GmailWriteBoundaryTest {

    private static final List<String> ALLOWED_GMAIL_WRITE_CLASSES = List.of(
        "com.zeromail.core.triage.usecases.TriageGmailWriter",
        "com.zeromail.core.cleanup.application.UnsubscribeMailtoSender"
    );

    @ArchTest
    static final ArchRule only_allow_listed_classes_call_gmail_write_apis =
        classes()
            .that()
            .resideInAPackage("..core..")
            .should(new ArchCondition<JavaClass>("call Gmail write APIs only from allow-list") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    if (ALLOWED_GMAIL_WRITE_CLASSES.contains(javaClass.getName())) return;
                    javaClass.getMethodCallsFromSelf().forEach(methodCall -> {
                        if (isGmailWriteCall(methodCall.getTargetOwner().getName(), methodCall.getName())) {
                            events.add(SimpleConditionEvent.violated(methodCall,
                                "Only allow-listed classes may call Gmail write APIs; found " +
                                methodCall.getSourceCodeLocation()));
                        }
                    });
                }
            });

    private static boolean isGmailWriteCall(String targetOwner, String methodName) {
        String normalized = targetOwner.replace('$', '.');
        return (normalized.endsWith("Gmail.Users.Messages") && (methodName.equals("modify") || methodName.equals("send")))
            || (normalized.endsWith("Gmail.Users.Drafts") && (methodName.equals("create") || methodName.equals("delete")));
    }
}
```

**Note:** Bổ sung `send` vào `Gmail.Users.Messages` allowed methods — hiện tại pattern `TriageGmailWriteBoundaryTest.java:64` chỉ cover `modify`. Phase 8 cần `Gmail.users().messages().send()` for mailto.

### Rule 3 — Cleanup module boundary (Modulith verification)

Existing `ApplicationModulesTest` đã tự verify. Không cần rule mới — chỉ cần `package-info.java` đúng.

### Rule 4 — ScopedValue / no ThreadLocal (project carry-over)

`core.cleanup` phải dùng `TenantContext` (ScopedValues) — existing project-wide ArchUnit FND-02 rule đã cover. Không thêm.

### Rule 5 — Mailto recipient must come from persisted header

KHÓ enforce qua ArchUnit (semantic — runtime check). Đề xuất **unit test** thay vì ArchUnit:

```java
@Test
void unsubscribeMailtoSender_rejects_recipient_not_from_persisted_list_unsubscribe_header() {
    // Given mail_message_observed has list_unsubscribe_mailto = "mailto:original@a.test"
    // When sendUnsubscribeMailto called with senderEmail="evil@b.test"
    // Then throws IllegalArgumentException
}
```

---

## Database Schema Migrations

> **Numbering correction:** Latest existing changelog là `040-triage-audit-message-ref.yaml`, NOT `038-billing-packages.yaml` như CONTEXT.md D-09 ghi nhầm. Phase 8 changelogs phải bắt đầu từ **`041`**.

### Changelog 041 — `processing_job` table

Xem section 4 chi tiết DDL. Generic + reusable cho SEED-009.

### Changelog 042 — `sender_suppression` table

```yaml
databaseChangeLog:
  - changeSet:
      id: 042-sender-suppression
      author: zeromail
      comment: >
        Per-tenant suppression list: senders or domains that bulk-unsubscribe campaigns must skip.
        Either sender_email or sender_domain (not both), enforced by CHECK. reason='manual' (user
        added) or 'replied' (auto-added when user replied >=1 time within 90d window — heuristic
        in core.cleanup.application.SuppressionAutoAddService).
      changes:
        - createTable:
            tableName: sender_suppression
            columns:
              - column:
                  name: id
                  type: uuid
                  defaultValueComputed: gen_random_uuid()
                  constraints: { primaryKey: true, nullable: false }
              - column:
                  name: tenant_id
                  type: uuid
                  constraints:
                    nullable: false
                    foreignKeyName: fk_sender_suppression_tenant
                    references: tenants(id)
                    deleteCascade: true
              - column: { name: sender_email, type: varchar(320) }
              - column: { name: sender_domain, type: varchar(253) }
              - column:
                  name: reason
                  type: varchar(32)
                  constraints: { nullable: false }
              - column:
                  name: created_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints: { nullable: false }
        - sql:
            sql: ALTER TABLE sender_suppression ADD CONSTRAINT ck_sender_suppression_one_target CHECK ((sender_email IS NOT NULL) <> (sender_domain IS NOT NULL))
        - sql:
            sql: ALTER TABLE sender_suppression ADD CONSTRAINT ck_sender_suppression_reason CHECK (reason IN ('manual','replied','auto'))
        - sql:
            comment: Unique per tenant per target.
            sql: CREATE UNIQUE INDEX ux_sender_suppression_email ON sender_suppression (tenant_id, sender_email) WHERE sender_email IS NOT NULL
        - sql:
            sql: CREATE UNIQUE INDEX ux_sender_suppression_domain ON sender_suppression (tenant_id, sender_domain) WHERE sender_domain IS NOT NULL
      rollback:
        - dropTable: { tableName: sender_suppression }
```

### Changelog 043 — `unsubscribe_campaign` table

```yaml
databaseChangeLog:
  - changeSet:
      id: 043-unsubscribe-campaign
      author: zeromail
      changes:
        - createTable:
            tableName: unsubscribe_campaign
            columns:
              - column: { name: id, type: uuid, defaultValueComputed: gen_random_uuid(), constraints: { primaryKey: true, nullable: false } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false, foreignKeyName: fk_unsubscribe_campaign_tenant, references: tenants(id), deleteCascade: true } }
              - column: { name: processing_job_id, type: uuid }
              - column: { name: status, type: varchar(16), defaultValue: 'QUEUED', constraints: { nullable: false } }
              - column: { name: progress_pct, type: smallint, defaultValueNumeric: 0, constraints: { nullable: false } }
              - column: { name: applied_at, type: timestamptz }
              - column: { name: reverted_at, type: timestamptz }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: updated_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
        - sql:
            sql: ALTER TABLE unsubscribe_campaign ADD CONSTRAINT ck_unsubscribe_campaign_status CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED'))
        - createIndex:
            tableName: unsubscribe_campaign
            indexName: idx_unsubscribe_campaign_tenant_created
            columns:
              - column: { name: tenant_id }
              - column: { name: created_at }
```

### Changelog 044 — `unsubscribe_attempt` table

```yaml
databaseChangeLog:
  - changeSet:
      id: 044-unsubscribe-attempt
      author: zeromail
      comment: >
        One row per sender within a campaign. Per-sender atomic: state=OK requires successful
        unsubscribe; state=FAILED means archive skipped per UNS-04. failureReason follows
        UnsubscribeHttpClient error mapping (HTTP_3XX_REDIRECT, HTTP_4XX_{code}, TIMEOUT,
        NETWORK_ERROR, etc.). archived_message_count tracked once history archive completes.
      changes:
        - createTable:
            tableName: unsubscribe_attempt
            columns:
              - column: { name: id, type: uuid, defaultValueComputed: gen_random_uuid(), constraints: { primaryKey: true, nullable: false } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false, foreignKeyName: fk_unsubscribe_attempt_tenant, references: tenants(id), deleteCascade: true } }
              - column: { name: campaign_id, type: uuid, constraints: { nullable: false, foreignKeyName: fk_unsubscribe_attempt_campaign, references: unsubscribe_campaign(id), deleteCascade: true } }
              - column: { name: sender_email, type: varchar(320), constraints: { nullable: false } }
              - column: { name: sender_domain, type: varchar(253), constraints: { nullable: false } }
              - column: { name: unsubscribe_method, type: varchar(16), constraints: { nullable: false } }   # ONE_CLICK | MAILTO
              - column: { name: state, type: varchar(16), defaultValue: 'PENDING', constraints: { nullable: false } }
              - column: { name: failure_reason, type: varchar(255) }
              - column: { name: archived_message_count, type: int, defaultValueNumeric: 0, constraints: { nullable: false } }
              - column: { name: applied_at, type: timestamptz }
              - column: { name: reverted_at, type: timestamptz }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: updated_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
        - sql: { sql: ALTER TABLE unsubscribe_attempt ADD CONSTRAINT ck_unsubscribe_attempt_state CHECK (state IN ('PENDING','RUNNING','OK','FAILED')) }
        - sql: { sql: ALTER TABLE unsubscribe_attempt ADD CONSTRAINT ck_unsubscribe_attempt_method CHECK (unsubscribe_method IN ('ONE_CLICK','MAILTO')) }
        - createIndex:
            tableName: unsubscribe_attempt
            indexName: idx_unsubscribe_attempt_campaign
            columns:
              - column: { name: campaign_id }
        - sql: { sql: CREATE UNIQUE INDEX ux_unsubscribe_attempt_campaign_sender ON unsubscribe_attempt (campaign_id, sender_email) }
```

### Changelog 045 — `mail_message_observed` List-Unsubscribe extension

```yaml
databaseChangeLog:
  - changeSet:
      id: 045-mail-message-observed-list-unsubscribe
      author: zeromail
      comment: >
        Persist List-Unsubscribe URL/mailto + RFC 8058 one-click flag for Phase 8 candidate
        discovery. Forward-only: legacy rows keep NULL even when listUnsubscribePresent=true,
        candidate query filters where (list_unsubscribe_url IS NOT NULL OR list_unsubscribe_mailto IS NOT NULL).
        HTTPS-only invariant for url enforced at parser time in GmailPreviewReadService; column
        accepts non-HTTPS values only via direct SQL (no UI/code path).
      changes:
        - addColumn:
            tableName: mail_message_observed
            columns:
              - column: { name: list_unsubscribe_url, type: varchar(2048) }
              - column: { name: list_unsubscribe_mailto, type: varchar(512) }
              - column:
                  name: list_unsubscribe_one_click
                  type: boolean
                  defaultValueBoolean: false
                  constraints: { nullable: false }
        - sql:
            comment: HTTPS-only invariant for URL column (safety net — parser enforces too).
            sql: ALTER TABLE mail_message_observed ADD CONSTRAINT ck_mail_message_observed_unsubscribe_url_https CHECK (list_unsubscribe_url IS NULL OR list_unsubscribe_url LIKE 'https://%')
        - createIndex:
            tableName: mail_message_observed
            indexName: idx_mail_message_observed_has_unsubscribe
            columns:
              - column: { name: tenant_id }
              - column: { name: sender_email }
              - column: { name: observed_at }
      rollback:
        - dropColumn: { tableName: mail_message_observed, columnName: list_unsubscribe_url }
        - dropColumn: { tableName: mail_message_observed, columnName: list_unsubscribe_mailto }
        - dropColumn: { tableName: mail_message_observed, columnName: list_unsubscribe_one_click }
```

**Note partial index:** Existing analytics queries dùng `WHERE sender_email IS NOT NULL`. New candidate query có thể dùng partial index `WHERE list_unsubscribe_url IS NOT NULL OR list_unsubscribe_mailto IS NOT NULL` để speed up — đề xuất:

```yaml
- sql:
    sql: CREATE INDEX idx_mail_message_observed_unsubscribe_candidate ON mail_message_observed (tenant_id, sender_email, observed_at) WHERE list_unsubscribe_url IS NOT NULL OR list_unsubscribe_mailto IS NOT NULL
```

---

## Audit Log Integration Strategy

### Decision: New `CleanupAuditEntity` + new `cleanup_audit` table (đề xuất)

**Lý do KHÔNG extend `TriageAuditEntity`:**

1. **Schema mismatch:** `triage_audit` cột bắt buộc: `rule_id`, `rule_name_snapshot`, `args_hash`, `action_args_json` (theo `025-triage-audit.yaml`). Cleanup không có rule_id → bắt buộc nullable hoặc dummy values → schema drift.
2. **SRP violation:** Triage decision + cleanup unsubscribe step là 2 use case khác nhau. Mixing breaks `TriageAuditWriter.actionTypeFor(...)` switch (chỉ cover LABEL/ARCHIVE/SAVE_DRAFT — hard-coded trong `TriageAuditWriter.java:120`).
3. **`RuleActionType` enum lock:** SPEC.md hard-lock "UNSUBSCRIBE không được thêm vào `RuleActionType`". Nếu extend `triage_audit` → bắt buộc thêm `action_type='UNSUBSCRIBE_ONE_CLICK'` etc. → vi phạm lock.

### Recommendation: Composite — 2 audit signal sources

**(A) `cleanup_audit` table mới (đề xuất changelog 046)**

1 row per unsubscribe step (POST or mailto) + 1 row per archive batch operation.

```yaml
- changeSet:
    id: 046-cleanup-audit
    changes:
      - createTable:
          tableName: cleanup_audit
          columns:
            - column: { name: id, type: uuid, defaultValueComputed: gen_random_uuid() }
            - column: { name: tenant_id, type: uuid, constraints: { nullable: false } }
            - column: { name: campaign_id, type: uuid }
            - column: { name: attempt_id, type: uuid }
            - column: { name: gmail_message_id, type: varchar(255) }                # NULL for unsubscribe step, set for archive step
            - column: { name: action_type, type: varchar(48), constraints: { nullable: false } }  # UNSUBSCRIBE_ONE_CLICK | UNSUBSCRIBE_MAILTO | ARCHIVE_FOR_UNSUBSCRIBE | LABEL_UNSUBSCRIBED
            - column: { name: applied_at, type: timestamptz }
            - column: { name: reverted_at, type: timestamptz }
            - column: { name: failure_reason, type: varchar(255) }
            - column: { name: created_at, type: timestamptz, defaultValueComputed: now() }
```

**(B) Reuse `TriageGmailWriter.applyLabel + archiveSkipInbox`** for the actual Gmail write. **DO NOT** insert `triage_audit` rows from cleanup path (`TriageGmailWriter` doesn't auto-insert audit — only the orchestrator does). Cleanup writes its own `cleanup_audit` rows via new `CleanupAuditWriter` (sibling pattern of `TriageAuditWriter`).

**Note:** `TriageGmailWriter.applyLabel + archiveSkipInbox` đã có log line `event=triage_gmail_write` — đó là log line vô hại, không insert audit row. Code path: `CleanupExecutor` → `TriageGmailWriter.archiveSkipInbox(...)` → `CleanupAuditWriter.insertArchive(...)`.

### Undo flow

`POST /api/unsubscribe/campaigns/{id}/undo`:

1. Validate `now - campaign.applied_at <= 30 days` (reuse `TriageUndoPolicy.UNDO_WINDOW`); 410 GONE nếu hết.
2. For each `cleanup_audit` row where `action_type IN ('ARCHIVE_FOR_UNSUBSCRIBE', 'LABEL_UNSUBSCRIBED')` and `reverted_at IS NULL`:
   - `TriageGmailWriter.restoreToInbox(tenantId, gmailMessageId)` (already exists, line 142).
   - `TriageGmailWriter.removeLabel(tenantId, gmailMessageId, "Zero Mail/Unsubscribed")` (already exists, line 124).
   - UPDATE `cleanup_audit.reverted_at = NOW()`.
3. UPDATE `unsubscribe_campaign.reverted_at = NOW()`.
4. KHÔNG cố gắng "un-unsubscribe" với provider — RFC 8058 one-way (SPEC.md §7 explicit).

[ASSUMPTION 5] Tạo `cleanup_audit` table riêng (đề xuất A) thay vì extend `triage_audit`. Planner xác nhận khi viết PLAN.md.

---

## Frontend Implementation Patterns

### Route layout (per D-12)

```
apps/web/app/(protected)/(app)/cleanup/
├── layout.tsx                              # cleanup namespace shell, sidebar highlight
├── unsubscribe-campaign/
│   ├── page.tsx                            # candidate list + multi-select + "Preview" button
│   └── [jobId]/
│       └── page.tsx                        # job status + per-sender table + retry/undo
└── suppression/
    └── page.tsx                            # suppression CRUD
```

### Feature folders (per D-13)

```
apps/web/features/cleanup/
├── unsubscribe-campaign/
│   ├── api/unsubscribe-api.ts              # fetch candidates, preview, execute, status, retry, undo
│   ├── query-keys.ts                       # unsubscribeCampaignKeys factory
│   ├── hooks/
│   │   ├── useCandidates.ts                # GET candidates, 30d window
│   │   ├── usePreviewCampaign.ts           # mutation POST preview
│   │   ├── useExecuteCampaign.ts           # mutation POST execute → returns jobId
│   │   ├── useCampaignStatus.ts            # GET status, refetchInterval polling
│   │   ├── useRetrySender.ts               # mutation POST retry
│   │   └── useUndoCampaign.ts              # mutation POST undo
│   ├── components/
│   │   ├── CandidateTable.tsx
│   │   ├── CampaignPreviewDialog.tsx       # shadcn dialog
│   │   ├── PerSenderStateTable.tsx
│   │   ├── RiskBadge.tsx                   # SAFE/NO_HEADER_DISABLED/SUPPRESSED_BLOCKED
│   │   ├── ProgressBar.tsx                 # job progress %
│   │   └── UndoButton.tsx                  # hidden when appliedAt + 30d < now
│   └── messages.ts                         # i18n keys
└── suppression/
    ├── api/suppression-api.ts
    ├── query-keys.ts                       # suppressionKeys
    ├── hooks/{useSuppressionList, useAddSuppression, useRemoveSuppression}.ts
    ├── components/{SuppressionTable, AddSuppressionDialog}.tsx
    └── messages.ts
```

### TanStack Query polling pattern (per D-15)

```typescript
export function useCampaignStatus(jobId: string) {
  return useQuery({
    queryKey: unsubscribeCampaignKeys.status(jobId),
    queryFn: () => fetchCampaignStatus(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'QUEUED' || status === 'RUNNING' ? 2000 : false;
    },
    // Stop refetch when terminal — match SPEC.md requirement 5.
  });
}
```

### Risk badge color tokens (Claude's Discretion → recommended)

| Badge | Token | Tailwind class |
|-------|-------|----------------|
| SAFE | teal | `bg-teal-100 text-teal-900` (existing project teal palette) |
| NO_HEADER_DISABLED | muted/gray | `bg-muted text-muted-foreground` + checkbox disabled |
| SUPPRESSED_BLOCKED | destructive (red) | `bg-destructive/10 text-destructive` + checkbox disabled |

Match Phase 7 D-15 trust-score thresholds palette pattern.

### i18n namespace (Claude's Discretion → recommended)

Per CONVENTIONS §10 — strings live in `features/cleanup/unsubscribe-campaign/messages.ts` + `features/cleanup/suppression/messages.ts`. Generated bundles via `pnpm i18n:build`.

```typescript
// features/cleanup/unsubscribe-campaign/messages.ts
export default {
  vi: {
    "cleanup.unsubscribe.title": "Hủy đăng ký hàng loạt",
    "cleanup.unsubscribe.candidates.empty": "Chưa có sender đủ điều kiện. Hãy đợi ≥30 ngày để có data List-Unsubscribe.",
    "cleanup.unsubscribe.preview.cap.tooManySenders": "Tối đa 25 sender mỗi chiến dịch",
    "cleanup.unsubscribe.preview.cap.tooManyMessages": "Tối đa 2000 email lịch sử mỗi chiến dịch",
    "cleanup.unsubscribe.risk.safe": "An toàn",
    "cleanup.unsubscribe.risk.noHeader": "Không có header — bị disable",
    "cleanup.unsubscribe.risk.suppressed": "Trong suppression list — bị chặn",
    "cleanup.unsubscribe.status.queued": "Đang chờ",
    "cleanup.unsubscribe.status.running": "Đang chạy",
    "cleanup.unsubscribe.status.completed": "Hoàn tất",
    "cleanup.unsubscribe.status.failed": "Thất bại",
    "cleanup.unsubscribe.action.retry": "Thử lại",
    "cleanup.unsubscribe.action.undo": "Hoàn tác",
    "cleanup.unsubscribe.action.undoExpired": "Hết hạn hoàn tác (>30 ngày)",
  },
  en: {
    "cleanup.unsubscribe.title": "Bulk unsubscribe",
    "cleanup.unsubscribe.candidates.empty": "No eligible senders yet. Wait ≥30 days for List-Unsubscribe data to accumulate.",
    // ... (parallel)
  }
};
```

**Sidebar nav (per D-12)** — extend `apps/web/components/shell/Sidebar` with new "Cleanup" group containing 2 sub-items.

---

## Testing Strategy

### Slice ladder mapping (per TESTING.md §3)

| Test target | Slice | Justification |
|-------------|-------|---------------|
| `UnsubscribeHttpClient.postOneClick` — status mapping table | Plain JUnit + `WireMockExtension` for upstream | No Spring context needed — pure HTTP mapping. Cheap. |
| `UnsubscribeMailtoSender.sendUnsubscribeMailto` — validation logic | Plain JUnit + mocked Gmail client | Pure validation; matches mocked-Gmail pattern in Phase 4 tests. |
| `mailto:` URI parser | Plain JUnit | Pure logic. |
| `CandidateQueryService.findCandidates` | `@DataJpaTest` + Testcontainers Postgres | Real SQL against real Postgres — required by TESTING.md §3 "Never H2". Tests partial index hit + suppression filter. |
| `processing_job` SKIP LOCKED + 2 workers concurrent pickup | `@SpringBootTest` + Testcontainers + 2 threads | Concurrency invariant — requires committed transactions per TESTING.md §3 DB hygiene. |
| Throttle bucket Redis INCR + EXPIRE TTL | `@SpringBootTest` with Testcontainers Redis (`@ServiceConnection`) | Real Redis (no embedded). Clock injection for TTL expiry tests. |
| Campaign execute → worker → audit row roundtrip | `@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers Postgres + Testcontainers Redis | True E2E flow. ONE test only per TESTING.md §2 — others stay at slice. |
| Undo within 30d + 410 GONE after 30d | Plain JUnit + Clock injection | Per `TriageUndoPolicy.undoableUntil` pattern. |
| ArchUnit: HTTP boundary + Gmail boundary (extended) | `@AnalyzeClasses` (existing harness) | Static analysis — cheap, fast, stable per TESTING.md §1. |
| Privacy sweep — `CleanupPrivacySweepTest` mirror | Same harness as `TriagePrivacySweepTest` (Postgres container + Logback `ListAppender` + sentinel tokens) | Mirror pattern, FORBIDDEN_CONTENT_TOKENS list. |
| Frontend Playwright e2e — golden path | `apps/web/e2e/cleanup.spec.ts` | Per CONVENTIONS §8: Playwright lives only in `apps/web/e2e/**`. |

### Wave 0 test files needed

- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/UnsubscribeHttpClientTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/UnsubscribeMailtoSenderTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/MailtoUriParserTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/CandidateQueryServiceTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/CleanupPrivacySweepTest.java` (mirror `TriagePrivacySweepTest.java`)
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/GmailWriteBoundaryTest.java` (rename/extend existing `TriageGmailWriteBoundaryTest.java`)
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/UnsubscribeHttpClientBoundaryTest.java` (new)
- [ ] `backend/worker/src/test/java/com/zeromail/worker/cleanup/ProcessingJobWorkerConcurrencyTest.java`
- [ ] `backend/worker/src/test/java/com/zeromail/worker/cleanup/ProcessingJobReaperBatchTest.java`
- [ ] `backend/worker/src/test/java/com/zeromail/worker/cleanup/UnsubscribeDomainThrottleTest.java`
- [ ] `apps/web/features/cleanup/unsubscribe-campaign/__tests__/useCampaignStatus.test.ts` (TanStack polling stop-on-terminal)
- [ ] `apps/web/e2e/cleanup-unsubscribe.spec.ts` (Playwright golden path)
- [ ] `apps/web/e2e/cleanup-suppression.spec.ts` (Playwright suppression CRUD)

### Privacy sweep — what `CleanupPrivacySweepTest` MUST verify

Mirror `TriagePrivacySweepTest.java:65` `FORBIDDEN_CONTENT_TOKENS` pattern with cleanup-specific sentinels:

- `RAW_SENDER_EMAIL = "newsletter-sender@example.test"` — must NEVER appear in `cleanup_audit.failure_reason`, `processing_job.failure_reason`, log lines.
- `LIST_UNSUBSCRIBE_URL_SENTINEL = "https://provider.example.test/u/SECRET_TOKEN_X"` — must NOT appear in logs (URL OK in DB column, NOT in logs).
- `EMAIL_BODY_SENTINEL` — must NOT appear anywhere (unsubscribe path never reads body — defensive).
- Assert ArchUnit-style: regex `[a-z0-9._+-]+@[a-z0-9.-]+\.[a-z]{2,}` không xuất hiện trong log lines trừ masked form.

### What NOT to test (per TESTING.md §2)

- DTO shape (record getters): no.
- Controller copy/path wording: no.
- "Service A calls service B": no — test observable outcome.
- More than 1 happy path + 1 error contract per controller endpoint.
- Spring property binding for `processing_job` properties (single integration test enough).
- Token counts, LLM latency (no LLM in this phase).

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Mockito + AssertJ + Spring Boot Test + Testcontainers (Postgres 17, Redis 7) + ArchUnit 1.4.x + Spring Modulith Test |
| Frontend framework | Vitest + Testing Library + Playwright |
| Config files | `backend/core/build.gradle.kts` (test task), `apps/web/vitest.config.ts`, `apps/web/playwright.config.ts` |
| Quick run (per-task) | `./gradlew :backend:core:test :backend:worker:test` (backend) + `pnpm --filter web test` (frontend Vitest) |
| Full suite (per-wave) | `./gradlew check` + `pnpm --filter web test && pnpm --filter web e2e` + `pnpm i18n:check` |
| Phase gate | Full suite green + ArchUnit + Modulith verification + privacy sweep + Playwright golden path |

### Phase Requirements → Test Map

| Req ID | Behavior | Test type | Automated command | File exists? |
|--------|----------|-----------|-------------------|--------------|
| UNS-01 | Candidate query: fixture 3 sender → 2 valid + 1 no-header excluded | Integration (DataJpaTest + Testcontainers) | `./gradlew :backend:core:test --tests "*CandidateQueryServiceTest*"` | ❌ Wave 0 |
| UNS-02 | Suppression CRUD + auto-add heuristic (reply ≥1/90d) | Integration (DataJpaTest + Testcontainers) | `./gradlew :backend:core:test --tests "*SuppressionService*"` | ❌ Wave 0 |
| UNS-03a | Preview reject >25 sender → HTTP 400 `CAMPAIGN_TOO_MANY_SENDERS` | WebMvcTest | `./gradlew :backend:api:test --tests "*UnsubscribeCampaignController*"` | ❌ Wave 0 |
| UNS-03b | Preview reject >2000 history → HTTP 400 `CAMPAIGN_TOO_MANY_MESSAGES` | WebMvcTest | (same) | ❌ Wave 0 |
| UNS-04a | Execute → jobId returned; worker pickup; per-sender atomic | SpringBootTest E2E (Postgres + Redis Testcontainers) | `./gradlew :backend:worker:test --tests "*UnsubscribeCampaignE2ETest*"` | ❌ Wave 0 |
| UNS-04b | Throttle 1/domain/60s + 10/domain/h enforced (12 sender cùng domain) | Integration with Testcontainers Redis | `./gradlew :backend:worker:test --tests "*UnsubscribeDomainThrottleTest*"` | ❌ Wave 0 |
| UNS-04c | RFC 8058 POST status mapping (200/201/202/204=OK; 3xx/4xx/5xx/timeout=FAILED) | Unit + WireMock | `./gradlew :backend:core:test --tests "*UnsubscribeHttpClientTest*"` | ❌ Wave 0 |
| UNS-05 | Status polling: progressPct + perSender array correct | TanStack Vitest + WebMvcTest | `pnpm --filter web test useCampaignStatus` + `./gradlew :backend:api:test --tests "*CampaignStatusController*"` | ❌ Wave 0 |
| UNS-06 | Retry OK sender → HTTP 409 (idempotent) | WebMvcTest | (same controller test) | ❌ Wave 0 |
| UNS-07a | Undo within 30d → INBOX restored + label removed + revertedAt set | SpringBootTest with Clock injection | `./gradlew :backend:core:test --tests "*CampaignUndoService*"` | ❌ Wave 0 |
| UNS-07b | Undo after 30d → HTTP 410 `UNDO_WINDOW_EXPIRED` | WebMvcTest with Clock injection | (same controller test) | ❌ Wave 0 |
| UNS-08a | ArchUnit: `HttpClient` / `RestClient` cấm ngoài `UnsubscribeHttpClient` | ArchUnit | `./gradlew :backend:core:test --tests "*UnsubscribeHttpClientBoundaryTest*"` | ❌ Wave 0 |
| UNS-08b | ArchUnit: `Gmail.users().messages().send()` cấm ngoài 2 allow-listed classes | ArchUnit (extend existing) | `./gradlew :backend:core:test --tests "*GmailWriteBoundaryTest*"` | ❌ Wave 0 (rename/extend) |
| UNS-08c | `UnsubscribeHttpClient` reject `http://` URL | Unit | `./gradlew :backend:core:test --tests "*UnsubscribeHttpClientTest*"` | ❌ Wave 0 |
| UNS-09 | Privacy sweep: no log/audit leak of full email/body/subject | SpringBootTest + Logback `ListAppender` | `./gradlew :backend:core:test --tests "*CleanupPrivacySweepTest*"` | ❌ Wave 0 (mirror) |
| Golden path | candidate list → preview → execute → status polling → undo | Playwright | `pnpm --filter web e2e -- --grep "cleanup unsubscribe"` | ❌ Wave 0 |
| Suppression UI | Add manual → candidates does not show; auto-add visible | Playwright | `pnpm --filter web e2e -- --grep "cleanup suppression"` | ❌ Wave 0 |

### Sampling Rate (per Nyquist Dimension 8)

| Cadence | Command | Coverage |
|---------|---------|----------|
| **Per task commit** | `./gradlew :backend:core:test :backend:worker:test --rerun-tasks` + `pnpm --filter web typecheck && pnpm --filter web lint` | Touched module tests + lint |
| **Per wave merge** | `./gradlew check` + `pnpm --filter web test && pnpm --filter web e2e --grep "cleanup"` + `pnpm i18n:check` | All backend tests + ArchUnit + Modulith + frontend Vitest + cleanup e2e |
| **Phase gate (`/gsd:verify-work`)** | Full suite (above) + Playwright full e2e + privacy sweep explicit + Liquibase migration apply/rollback test on fresh Postgres container | All acceptance criteria 14/14 checkboxes + privacy invariant + reversibility verification |

### Wave 0 Gaps (test infrastructure)

All test files in the requirements map are **NEW**. None exist yet. Wave 0 must create:

- [ ] Backend slice + ArchUnit + Modulith + privacy sweep test files (10 new files).
- [ ] Worker concurrency + reaper + throttle test files (3 new files).
- [ ] Frontend hook unit test files (2 new files).
- [ ] Playwright e2e specs (2 new files).
- [ ] Liquibase rollback verification — re-use existing `LiquibaseRollbackTest` pattern (already exists project-wide).

**Test data fixtures needed:**
- 3 sender Gmail fixtures: 1 one-click, 1 mailto-only, 1 no `List-Unsubscribe` header (HTTP fixtures via WireMock for HTTPS POST).
- 1 suppressed sender (in `sender_suppression` table).
- Auto-add fixture: triage_audit row with `action_type='SAVE_DRAFT'` + sent reply trace.

### Invariants list (must hold across all phase 8 changes)

1. **No PII in logs:** sender_email never appears unmasked. Body/subject never logged.
2. **HTTPS-only HTTP POST:** `UnsubscribeHttpClient` rejects non-HTTPS URLs at parse-time AND execute-time.
3. **Per-sender atomic:** Unsubscribe FAILED → archive count = 0 for that sender.
4. **Throttle enforced:** Domain X with 12 attempts → at most 1 per 60s, 10 per hour.
5. **Undo reversibility:** Within 30 days, restore label `INBOX` + remove `Zero Mail/Unsubscribed` per archived message.
6. **HTTP unsubscribe only from persisted header:** `UnsubscribeHttpClient.postOneClick` requires URL provenance from `mail_message_observed.list_unsubscribe_url`.
7. **Gmail send-as-self only for unsubscribe-mailto:** ArchUnit guard.
8. **No auto-send:** `UNSUBSCRIBE` not added to `RuleActionType` enum.
9. **Tenant isolation:** Per-tenant Redis throttle keys; per-tenant `sender_suppression`; per-tenant campaign + attempt rows.
10. **Crash safety:** Reaper batch reclaims stale RUNNING jobs after 5 minutes.

---

## Open Questions for Planner

1. **`cleanup_audit` table vs extend `triage_audit`?** (Section 9). Đề xuất: tạo bảng riêng. Final decision khi viết PLAN.md.
2. **Single `processing_job` row per campaign vs multiple (1 per retry)?** Đề xuất: single + state-driven attempt loop (Section 4). Final khi viết worker.
3. **Auto-add suppression heuristic data source:** SPEC.md ghi "user reply ≥1/90d" but doesn't specify how to detect "reply". `TriageAuditEntity` có `action_type='SAVE_DRAFT'` (user-confirmed-send qua Gmail), nhưng "user gửi reply trong Gmail" KHÔNG được ingest qua Pub/Sub (Pub/Sub chỉ push received, not sent). **Cách đo tốt nhất:** scan `mail_message_observed.label_ids` cho `SENT` label + sender_email = current user's gmail address, JOIN với original sender's domain. Planner verify khi viết.
4. **Suppression sender_email vs sender_domain priority:** Nếu suppressed `boss@example.com` AND suppressed `example.com`, query treat both. SPEC.md không clarify. Đề xuất: suppression check OR (email match OR domain match).
5. **Frontend candidate list pagination:** SPEC.md không nói. 25-cap là per-campaign, không per-page. Nếu user có 100 candidates thì paginate thế nào? Đề xuất: top 50 by message count, scroll, không paginate v1.

---

## Risks & Mitigations

| Risk | Likelihood | Severity | Mitigation |
|------|-----------|----------|------------|
| **Provider 200 OK nhưng KHÔNG thực sự unsubscribe** (user vẫn nhận email sau campaign) | MEDIUM | HIGH (trust killer) | UI hiển thị disclaimer "Provider acknowledged — bạn vẫn có thể nhận email tới khi sender refresh list". Don't over-promise. Defer "provider-aware success heuristic" per CONTEXT deferred. |
| **HTTPS unsubscribe URL phishing** (provider URL trong header trỏ tới attacker-controlled host) | LOW | HIGH | DKIM enforced by Gmail upstream (RFC 8058 §4); URL provenance check ensures only persisted header values used; HTTPS-only constraint blocks accidental MITM. |
| **Throttle bucket key starvation** (1 tenant với 1000 domain → 2000 keys) | LOW | MEDIUM | TTL 60s/1h tự xóa. Memory cost: <100KB per tenant worst case. Acceptable. |
| **Reaper batch reclaims slow-but-live worker** (worker đang xử lý 2000 mail archive batch chậm > 5min) | MEDIUM | MEDIUM | Heartbeat update mỗi batch (e.g., every 50 messages archived). 5-min threshold rất rộng. ShedLock for reaper prevents double-reaper across worker instances. |
| **Forward-only backfill khiến UX trống 30 ngày đầu** (user mới install phase 8 → không thấy candidate) | HIGH | LOW | UX disclaimer trên empty state "Wait ≥30 days for List-Unsubscribe data to accumulate". Sidebar nav vẫn show "Cleanup" để user khám phá. |
| **Liquibase changelog conflict** (CONTEXT.md ghi 039 nhưng phải dùng 041) | LOW | MEDIUM | Planner MUST verify `ls .../changelog/changes/` và dùng số tiếp theo sau 040. Documented as ASSUMPTION 1 above. |
| **Mailto delivery failure không retry** (provider mail server tạm thời down) | MEDIUM | LOW | Mailto fail → state=FAILED → user retry button (UNS-06). Acceptable manual recovery. |

---

## State of the Art

| Old approach | Current approach | When changed | Impact |
|--------------|------------------|--------------|--------|
| RFC 2369 List-Unsubscribe mailto only | RFC 8058 one-click HTTPS POST | 2018 (Yahoo / Google mandate) | Modern mailers send both; Phase 8 prefers HTTPS per provider docs. |
| Click HTTP unsubscribe URL from body | Header-only `List-Unsubscribe` | Forever (security boundary) | Phase 8 enforces explicitly (UNS-08). |
| `WebClient` (reactive) for HTTP | `RestClient` (synchronous + virtual threads) | Spring Framework 6.1+ | Project standard (CLAUDE.md WebFlux ban). |
| `@MockBean` | `@MockitoBean` | Spring Boot 3.4+ | TESTING.md §3 mandate. |
| `@DynamicPropertySource` | `@ServiceConnection` | Spring Boot 3.1+ | TESTING.md §3 mandate. |
| Quartz / external scheduler | `@Scheduled` + ShedLock | Project standard | Existing pattern. |

**Deprecated/outdated:**
- H2 in-memory DB for tests: BANNED per TESTING.md §3 — use Testcontainers Postgres.
- `javax.*` imports: BANNED per CLAUDE.md — Jakarta only.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Liquibase next free changelog id is **041** (not 039 as CONTEXT.md D-09 ghi nhầm) | §8 | Migration conflict; build failure on Liquibase apply. Verifiable by `ls .../changes/`. |
| A2 | Single `processing_job` row per campaign + state-driven attempt loop (retry resets attempt state to PENDING, not new job row) | §4 | Different worker semantic if wrong; planner must reconcile. |
| A3 | Lua script atomic INCR+EXPIRE for throttle (vs INCR+conditional-EXPIRE Java-side) | §5 | Race window may leave key TTL-less, blocking forever; defensive Lua mitigates. |
| A4 | KHÔNG publish `UnsubscribeCampaignCompleted` domain event v1 (defer to polish) | §6 | Frontend cache must be invalidated manually by user refresh, OR via polling cessation. |
| A5 | New `cleanup_audit` table vs extending `triage_audit` (sibling pattern of `TriageAuditEntity`) | §9 | Schema decision; if extend triage_audit, must add UNSUBSCRIBE_* values to `ck_triage_audit_decision` CHECK — schema drift. |
| A6 | Auto-add suppression via `mail_message_observed.label_ids` SENT scan, not separate "user reply" event | OQ§3 | If detection method differs (e.g., requires `users.history.list`), need different ingest path. |
| A7 | Pagination: top 50 candidates by message count, scroll, no explicit page UI v1 | OQ§5 | Tenant with 500+ candidates may have UX cliff; defer scroll → pagination if user reports. |

**If user/planner confirms A1–A7 at discuss-phase, this RESEARCH.md is sufficient for plan writing.**

---

## Sources

### Primary (HIGH confidence)

- **RFC 8058 — One-Click Unsubscribe** [CITED: https://datatracker.ietf.org/doc/html/rfc8058] — POST semantics, content-type, redirect prohibition, security requirements (fetched 2026-05-17 via WebFetch).
- **Spring Framework 7.0.7 — RestClient reference** [CITED: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html] — `JdkClientHttpRequestFactory`, `exchange()` vs `retrieve()`, redirect configuration (fetched 2026-05-17 via WebFetch).
- **Project codebase — verified file reads (HIGH):**
  - `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java` — Gmail write boundary class pattern (verified).
  - `backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java` — ArchUnit boundary test pattern (verified).
  - `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java:515` — `listUnsubscribePresent` extraction point (verified).
  - `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java` — audit row write pattern (verified).
  - `backend/core/src/test/java/com/zeromail/core/triage/TriagePrivacySweepTest.java` — privacy sweep pattern (verified — FORBIDDEN_CONTENT_TOKENS, ListAppender, sentinel approach).
  - `backend/core/src/main/resources/db/changelog/changes/` — verified latest is `040-triage-audit-message-ref.yaml`; next free = 041.
  - `backend/core/src/main/java/com/zeromail/core/config/RestClientConfig.java` — existing RestClient bean pattern (HttpClient.Redirect.NEVER + timeouts) verified.
  - `backend/core/src/main/java/com/zeromail/core/shared/lock/RedisDistributedLock.java` — StringRedisTemplate usage pattern verified.
  - `backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java` — `@Scheduled` + ShedLock pattern verified.
  - `backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperBatch.java` — reaper batch pattern verified.
  - `backend/core/src/main/java/com/zeromail/core/triage/domain/TriageUndoPolicy.java` — 30d undo window constant verified (`Duration.ofDays(30)`).
  - `backend/core/src/main/java/com/zeromail/core/analytics/usecases/AnalyticsSummaryQueryService.java` — TopSenders SQL pattern verified.
  - `backend/core/src/main/java/com/zeromail/core/analytics/package-info.java` — Modulith `allowedDependencies` pattern verified.
  - `apps/web/features/` directory structure verified — sub-folder per feature pattern.
  - CLAUDE.md / CONVENTIONS.md / TESTING.md / 08-CONTEXT.md / 08-SPEC.md — all read in full.

### Secondary (MEDIUM confidence)

- **RFC 2369 List-Unsubscribe header format** [ASSUMED via WebFetch + training] — header multi-URI format `<https://...>, <mailto:...>`. Standard parser approach.
- **RFC 6068 mailto: URI scheme** [ASSUMED via training] — `mailto:user@host?subject=&body=` format. Java `URI` built-in parser sufficient.
- **Spring Modulith `@ApplicationModule`** [VERIFIED via codebase pattern, training docs] — `allowedDependencies` literal nested form `"shared.privacy"`.

### Tertiary (LOW confidence — flag for validation)

- **PostgreSQL `SKIP LOCKED` behavior with virtual threads** [ASSUMED] — Spring Boot 4 + virtual-thread-enabled JdbcTemplate. Existing pattern likely OK. Verify under concurrency test.
- **Spring Data Redis `opsForValue().increment(key)`** [ASSUMED via training + similar codebase usage] — atomic INCR via Lettuce. Lua script approach more defensive for INCR+EXPIRE race.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries are project-existing with verified version pins.
- Architecture (Modulith module, ArchUnit, worker poll, throttle): HIGH — every pattern has an existing codebase exemplar.
- RFC 8058 protocol: HIGH — fetched directly from datatracker.ietf.org.
- Liquibase schema sketches: MEDIUM — DDL is illustrative; planner may tune column sizes / index strategies.
- Audit table strategy: MEDIUM — recommended new table, but planner could reconcile to extend triage_audit if appetite for schema drift exists.
- Suppression auto-add heuristic data source: LOW — SPEC.md doesn't specify implementation, multiple plausible signal sources (see OQ§3).

**Research date:** 2026-05-17
**Valid until:** 2026-06-17 (30 days for stable stack; RFC 8058 + Spring Framework 7.0.7 are slow-moving).

---

## RESEARCH COMPLETE

**Phase:** 08 - bulk-unsubscribe-campaign
**Confidence:** HIGH (with 7 explicit ASSUMPTIONS flagged for planner / discuss-phase confirmation)

### Key Findings

- **RFC 8058 protocol details fully mapped to Spring `RestClient` config** — connect 5s / read 10s, redirect=NEVER, `application/x-www-form-urlencoded` body `List-Unsubscribe=One-Click`, success gate = 2xx only, all other outcomes (3xx/4xx/5xx/timeout/IO) → FAILED with specific failureReason taxonomy.
- **Liquibase numbering correction:** CONTEXT.md D-09 says next changelog is `039-...`; actual next free is **`041`** — verified via `ls`. **Planner must use 041+** for `processing_job`, `sender_suppression`, `unsubscribe_campaign`, `unsubscribe_attempt`, `mail_message_observed-list-unsubscribe`, `cleanup_audit` (6 new changelogs total).
- **Audit recommendation:** Create new `cleanup_audit` table (sibling pattern of `triage_audit`) rather than extend — preserves `RuleActionType` enum lock and avoids schema CHECK drift.
- **HTTP boundary class pattern is reusable:** Extend existing `TriageGmailWriteBoundaryTest` with allow-list (rename to `GmailWriteBoundaryTest`) adding `UnsubscribeMailtoSender` + `send` method to the boundary check. New ArchUnit `UnsubscribeHttpClientBoundaryTest` for the HTTP client boundary.
- **Throttle bucket Redis pattern uses Lua atomic INCR+EXPIRE** for race-free TTL setup. Per-tenant key prefix prevents cross-tenant interference.
- **Worker framework (`processing_job` + reaper) is a Phase 8 asset that pays off SEED-009** — generic enough to host bulk-archive, cold-email-blocker, attachment-filing in future without re-design.

### File Created

`D:/Semester-8/zero-mail/.planning/phases/08-bulk-unsubscribe-campaign/08-RESEARCH.md`

### Confidence Assessment

| Area | Level | Reason |
|------|-------|--------|
| Standard Stack | HIGH | Every library is project-existing; verified file paths cited. |
| RFC 8058 + RFC 6068 protocol | HIGH | Fetched directly from datatracker.ietf.org. |
| Spring `RestClient` configuration | HIGH | Verified via WebFetch on Spring 7.0.7 docs + existing codebase RestClientConfig.java. |
| Architecture patterns (Modulith, ArchUnit, worker poll, reaper) | HIGH | Every pattern has codebase exemplar (TriagePendingReaperBatch, ShedLockConfig, TriageGmailWriteBoundaryTest, AnalyticsSummaryQueryService). |
| DB schema migrations | MEDIUM | DDL sketches illustrative; planner tunes column sizes / index strategies during plan writing. |
| Audit log integration | MEDIUM | Recommended approach but final decision deferred to planner; ASSUMPTION 5 logged. |
| Suppression auto-add heuristic | LOW | SPEC.md doesn't specify implementation signal source; planner picks during plan writing. |

### Open Questions

5 open questions logged in `## Open Questions for Planner`:
1. `cleanup_audit` vs extend `triage_audit`
2. Single `processing_job` row per campaign vs multiple
3. Auto-add suppression heuristic data source
4. Suppression sender_email vs sender_domain priority
5. Frontend candidate list pagination strategy

### Ready for Planning

Research complete. Planner can now create PLAN.md files. Recommend 4–6 plans:

1. **Plan 01 — Schema migrations + List-Unsubscribe persistence extension** (changelogs 041–045 + GmailPreviewReadService extend) — independent, can start immediately.
2. **Plan 02 — `core.cleanup` Modulith module + `processing_job` framework + reaper batch** — independent, parallel with Plan 01.
3. **Plan 03 — `UnsubscribeHttpClient` + `UnsubscribeMailtoSender` + ArchUnit boundaries + Mailto URI parser** — depends on Plan 02 (module exists).
4. **Plan 04 — Campaign API (candidates / preview / execute / status / retry / undo) + service layer + suppression CRUD + auto-add heuristic + Redis throttle + audit writer + `CleanupPrivacySweepTest`** — depends on Plans 01–03.
5. **Plan 05 — Frontend candidate list + preview dialog + execute + status polling + per-sender state + retry + undo** — depends on Plan 04 (OpenAPI client regen).
6. **Plan 06 — Frontend suppression page + sidebar nav + Playwright e2e golden path + i18n strings** — depends on Plan 05.
