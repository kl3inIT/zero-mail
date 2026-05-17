# Phase 8: Bulk Unsubscribe Campaign - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-17
**Phase:** 08-bulk-unsubscribe-campaign
**Areas discussed:** Worker job framework, Mailto send + auto-send boundary, List-Unsubscribe URL persistence, Frontend page namespace

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| Worker job framework | Generic `processing_job` (outbox + SKIP LOCKED) vs campaign-specific table | ✓ |
| Mailto send + auto-send boundary | Extend `TriageGmailWriter` vs sibling `UnsubscribeMailtoSender` | ✓ |
| List-Unsubscribe URL persistence | Extend `mail_message_observed` 3 column vs sidecar table | ✓ |
| Frontend page namespace | `/unsubscribe-campaign` (top-level) vs `/cleanup/...` namespaced | ✓ |

**User's choice:** All 4 areas selected. Per-domain throttle bucket = Redis (default per CLAUDE.md); Candidate query data source = method mới trong `core.cleanup` (Claude's discretion, planner xác nhận).

---

## Worker Job Framework

### Question 1: Job table shape

| Option | Description | Selected |
|--------|-------------|----------|
| Generic `processing_job` (Recommended) | 1 table chung cho mọi async job, JSONB payload, reusable cho 3+ phase SEED-009 tương lai, match CLAUDE.md "Postgres-backed processing_job" | ✓ |
| Campaign-specific `unsubscribe_campaign_job` | Bounded scope, không over-engineer, nhưng vi phạm CLAUDE.md lock | |
| Hybrid: campaign table + reuse later | Match SPEC.md exact (chỉ `unsubscribe_campaign` + `unsubscribe_attempt`) nhưng không track CLAUDE.md goal | |

**User's choice:** Generic `processing_job`.
**Notes:** Anticipate SEED-009 siblings (bulk-archive, cold-email-blocker, attachment-filing).

### Question 2: Worker polling + crash recovery

| Option | Description | Selected |
|--------|-------------|----------|
| Continuous loop + heartbeat (Recommended) | 2s poll + `SELECT FOR UPDATE SKIP LOCKED LIMIT 1` + heartbeat_at + reaper @Scheduled 60s reset stale RUNNING > 5min | ✓ |
| @Scheduled fixed rate, no heartbeat | Đơn giản nhưng row mắc kẹt RUNNING khi worker restart | |
| Spring Modulith Event Outbox | Reuse event_publication table; không phù hợp cho long-running user-triggered job | |

**User's choice:** Continuous loop + heartbeat reaper.
**Notes:** Stale threshold = 5min, reaper cadence = 60s. Worker restart-safe.

### Question 3: API tx attempt row creation

| Option | Description | Selected |
|--------|-------------|----------|
| API tx tạo ngay (Recommended) | API endpoint trong 1 tx tạo campaign + N attempt rows + 1 processing_job; frontend thấy perSender đủ ngay | ✓ |
| Worker tạo khi pick job | API chỉ tạo campaign + processing_job; worker create attempt rows; UX yếu | |

**User's choice:** API tx tạo ngay.
**Notes:** Idempotent + immediate frontend feedback.

---

## Mailto Send + Auto-Send Boundary

### Question 1: Class architecture

| Option | Description | Selected |
|--------|-------------|----------|
| New isolated class + narrow ArchUnit allow (Recommended) | `UnsubscribeMailtoSender` trong `core.cleanup.application` với `@TriageGmailWriteAllowed`; ArchUnit allow Gmail send chỉ class này + `TriageGmailWriter` | ✓ |
| Extend `TriageGmailWriter` method | 1 boundary class duy nhất nhưng vi phạm SRP (triage + cleanup trộn lẫn) | |
| Layered: low-level `GmailSendClient` + high-level guard | 2 class, enforce input validation ở high-level; robust nhưng over-engineer cho 1 use case | |

**User's choice:** New isolated `UnsubscribeMailtoSender`.
**Notes:** Giữ SRP. `TriageGmailWriteBoundaryTest` extend cover.

### Question 2: HTTP client implementation

| Option | Description | Selected |
|--------|-------------|----------|
| Spring `RestClient` (Recommended) | Spring 6.1+ synchronous client, virtual-thread friendly, configurable timeout/interceptor, default redirect=NEVER (RFC 8058) | ✓ |
| JDK 25 `HttpClient` | Built-in, không cần dependency; nhưng không align Spring ecosystem (Micrometer manual wire) | |
| Resilience4j-wrapped client | Circuit breaker + retry + bulkhead; over-engineer cho v1, throttle đã đủ | |

**User's choice:** Spring `RestClient`.
**Notes:** Timeout connect=5s, read=10s. ArchUnit ban `WebClient` vẫn enforce + ban `RestClient.create()` ngoài `UnsubscribeHttpClient`.

### Question 3: Success gate

| Option | Description | Selected |
|--------|-------------|----------|
| 2xx only, timeout 10s (Recommended) | Chỉ 200/202/204 → OK; 3xx/4xx/5xx/timeout → FAILED với failureReason chi tiết | ✓ |
| Strict 200/204 + body check | 200 + body không chứa "error"; heuristic body-check sai và vi phạm "no content matching" | |
| Lenient: any 2xx + 3xx | Provider redirect đến confirm page coi thành công; vi phạm RFC 8058 "no user interaction required" | |

**User's choice:** 2xx only, timeout 10s.
**Notes:** Per-sender atomic: FAILED → không archive. failureReason enum: HTTP_3XX_REDIRECT, HTTP_4XX_{code}, TIMEOUT, NETWORK_ERROR.

---

## List-Unsubscribe URL/Mailto Persistence

### Question 1: Schema location

| Option | Description | Selected |
|--------|-------------|----------|
| Extend `mail_message_observed` 3 column (Recommended) | `list_unsubscribe_url VARCHAR(2048) NULL` + `list_unsubscribe_mailto VARCHAR(512) NULL` + `list_unsubscribe_one_click BOOLEAN NOT NULL DEFAULT false`; 1 query lookup | ✓ |
| Sidecar `mail_message_unsubscribe_metadata` | FK table; JOIN khi build candidate; hot table không phình | |
| Single JSONB column `unsubscribe_meta` | Flexible nhưng khó index, vi phạm CLAUDE.md "JSONB chỉ cho rule matchers" | |

**User's choice:** Extend `mail_message_observed` 3 column.
**Notes:** Liquibase changelog kế tiếp `039-...`. VARCHAR null tiny, hot table impact minimal.

### Question 2: Backfill

| Option | Description | Selected |
|--------|-------------|----------|
| Forward-only, no backfill (Recommended) | Liquibase chỉ ADD COLUMN; row cũ giữ NULL; candidate query filter URL/mailto NOT NULL; user chờ 30d (= window) đủ candidate | ✓ |
| Backfill batch job | Gọi Gmail API cho mỗi row cũ; quota burn, không đáng v1 | |
| Re-extract khi user truy cập | Lazy fetch; UX chậm + quota risk | |

**User's choice:** Forward-only, no backfill.
**Notes:** User chờ 30 ngày để có candidate đủ; no Gmail quota burn.

### Question 3: HTTPS validation timing

| Option | Description | Selected |
|--------|-------------|----------|
| Parse-time + execute-time (defense in depth) (Recommended) | `GmailPreviewReadService` DROP URL non-https + `UnsubscribeHttpClient` re-validate; invariant: mọi row có URL LUÔN https | ✓ |
| Execute-time only | Persist nguyên URL; execute reject; simpler nhưng DB chứa URL không an toàn | |

**User's choice:** Defense in depth (parse + execute).
**Notes:** Invariant: persistent row LUÔN https. Mailto value lưu đầy đủ `mailto:user@host?subject=...`.

---

## Frontend Page Namespace

### Question 1: Route layout

| Option | Description | Selected |
|--------|-------------|----------|
| Cleanup namespace (Recommended) | `/cleanup/unsubscribe-campaign` + `/cleanup/unsubscribe-campaign/[jobId]` + `/cleanup/suppression`; sidebar "Cleanup" entry; phase SEED-009 siblings ở cùng cấp | ✓ |
| Flat top-level routes | `/unsubscribe-campaign` + `/unsubscribe-suppression`; match SPEC.md exact; sidebar phình khi 4-5 cleanup phase | |
| Settings-nested suppression + top-level campaign | Hỗn hợp; UX rõ nhưng future cleanup vẫn top-level | |

**User's choice:** Cleanup namespace.
**Notes:** Features folder = `apps/web/features/cleanup/unsubscribe-campaign/` + `apps/web/features/cleanup/suppression/`. TanStack Query key factories `unsubscribeCampaignKeys` + `suppressionKeys`.

### Question 2: Entry point

| Option | Description | Selected |
|--------|-------------|----------|
| Menu only, tự chọn từ candidate list (Recommended) | User vào `/cleanup/unsubscribe-campaign` từ sidebar; tách concern analytics (read) vs cleanup (action) | ✓ |
| Menu + CTA từ Phase 7 TopSendersPanel | Better discovery nhưng risk regression Phase 7 vừa ship | |
| Deep-link preselect + menu | `?senders=...` query param; defer UI CTA cho power user/docs | |

**User's choice:** Menu only.
**Notes:** Phase 7 TopSendersPanel CTA defer cho phase polish sau khi UX cleanup ổn.

---

## Claude's Discretion

- **Throttle bucket implementation:** Default Redis (CLAUDE.md "Redis cho rate-limit"). Key format `throttle:unsubscribe:domain:{domain}:60s` + `:1h` với Redis INCR + EXPIRE. Planner finalize.
- **Candidate query data source:** Default = method mới trong `core.cleanup.application.CandidateQueryService` (tránh `core.analytics` expose internal). Planner xem có overlap đủ lớn với `AnalyticsSummaryQueryService` không.
- **Package layout `core.cleanup`:** Standard `domain/application/persistence/projection/exception/` theo CONVENTIONS §2.
- **`processing_job` payload schema:** `{"campaignId": "uuid"}` cho `UNSUBSCRIBE_CAMPAIGN`.
- **Reaper batch placement:** `backend/worker/.../cleanup/ProcessingJobReaperBatch.java` hoặc `worker/scheduling/` — planner chọn.
- **i18n keys:** Dự kiến `cleanup.unsubscribe.*`, `cleanup.suppression.*` namespace; planner chốt key list chi tiết.
- **ArchUnit rules cho `core.cleanup.*`:** Cấm `HttpClient`/`RestClient.create()` ngoài `UnsubscribeHttpClient.java`; cấm `Gmail.send()` ngoài `UnsubscribeMailtoSender.java` + `TriageGmailWriter`.
- **mailto URI parsing:** RFC 6068; Java built-in hay regex — planner chọn.
- **UI risk badge tokens:** `SAFE`=teal, `NO_HEADER_DISABLED`=muted/gray, `SUPPRESSED_BLOCKED`=destructive.
- **`processing_job` retention/purge batch:** Defer cho phase ops sau (90d standard).

## Deferred Ideas

- CTA "Unsubscribe from this sender" trong Phase 7 TopSendersPanel — polish sau.
- Multi-tenant team suppression list — out of scope SPEC.md.
- Scheduled/recurring campaign — out of scope SPEC.md.
- Provider-aware success heuristic (HTML "click to confirm" recognition) — vi phạm RFC 8058, defer observability phase.
- `processing_job` purge batch — phase ops sau.
- Bulk archive / cold-email blocker / attachment auto-filing — phase riêng SEED-009; cleanup module + namespace dọn đường ở phase 8.
