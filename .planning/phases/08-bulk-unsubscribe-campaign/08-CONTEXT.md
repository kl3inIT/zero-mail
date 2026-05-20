# Phase 8: Bulk Unsubscribe Campaign - Context

**Gathered:** 2026-05-17
**Status:** Ready for planning

<domain>
## Phase Boundary

User chọn ≤25 newsletter sender → preview bắt buộc → execute campaign "unsubscribe + archive lịch sử" (≤2000 history mail) theo lô. Unsubscribe path ưu tiên `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058 POST), fallback `mailto:`; KHÔNG bao giờ click HTTP unsubscribe URL từ body email. Per-sender atomic: fail unsubscribe → không archive. Mọi campaign reversible bằng Undo trong 30 ngày kể từ `appliedAt` (restore archived mail về INBOX + remove label `Zero Mail/Unsubscribed`). Suppression list user-managed + auto-add khi user reply ≥1 lần trong 90d.

New Spring Modulith module `core.cleanup` (depend `core.gmail`, `core.triage`, `core.analytics`). Frontend dưới namespace mới `/cleanup/*`. Phase tương lai SEED-009 (bulk-archive, cold-email-blocker, attachment-filing) sẽ reuse cleanup namespace + `processing_job` framework xây ở phase này.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**9 requirements are locked.** See `08-SPEC.md` for full requirements, boundaries, and acceptance criteria.

Downstream agents MUST read `08-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- `core.cleanup` Spring Modulith module (domain/application/persistence)
- Tables: `sender_suppression`, `unsubscribe_campaign`, `unsubscribe_attempt`
- Endpoints: `GET /api/unsubscribe/candidates`, `POST /api/unsubscribe/campaigns/preview|execute`, `GET /api/unsubscribe/campaigns/{id}`, `POST /api/unsubscribe/campaigns/{id}/senders/{email}/retry`, `POST /api/unsubscribe/campaigns/{id}/undo`, suppression CRUD
- `UnsubscribeExecutor` + `UnsubscribeHttpClient` (RFC 8058 one-click POST) + `UnsubscribeMailtoSender` (mailto qua Gmail send-as-self)
- Extend `GmailPreviewReadService` extract `List-Unsubscribe` URL/mailto + `List-Unsubscribe-Post` flag, persist vào `mail_message_observed` (Liquibase changelog)
- Worker job type `UNSUBSCRIBE_CAMPAIGN` với throttle bucket per-domain
- Frontend `/cleanup/unsubscribe-campaign` + `/cleanup/suppression`: candidate list, preview modal, execute button, job status, undo button, suppression page
- Audit log integration: 1 audit row per message archived; 1 audit row per unsubscribe step
- Privacy sweep test riêng cho module mới (`CleanupPrivacySweepTest`)

**Out of scope (from SPEC.md):**
- Bulk archiver (archive theo sender/category/age mà KHÔNG unsubscribe) — phase riêng từ SEED-009
- Cold-email blocker (classify first-time senders) — phase riêng từ SEED-009
- Smart filing of attachments (Drive/OneDrive) — phase riêng từ SEED-009
- Auto-unsubscribe (rule-based) — `UNSUBSCRIBE` KHÔNG được thêm vào `RuleActionType`; chỉ user-triggered campaign
- Scheduled/recurring campaign — manual one-shot only
- Permanent delete — chỉ archive + label
- Click HTTP unsubscribe URL từ email body — security boundary
- Per-campaign custom label name — label cố định `Zero Mail/Unsubscribed`
- Multi-tenant team suppression list — phase 8 chỉ per-user-account

</spec_lock>

<decisions>
## Implementation Decisions

### Worker job framework
- **D-01:** Generic `processing_job` table (Postgres-backed outbox pattern, match CLAUDE.md "Postgres-backed queue" lock). Schema: `id UUID`, `tenant_id`, `job_type` enum (v1: `UNSUBSCRIBE_CAMPAIGN`), `payload JSONB`, `status` (QUEUED/RUNNING/COMPLETED/FAILED), `attempts INT`, `next_run_at`, `heartbeat_at`, `created_at`, `started_at`, `finished_at`, `failure_reason`. Reusable cho phase SEED-009 tương lai (bulk-archive, cold-email-blocker, attachment-filing) — KHÔNG tạo table campaign-specific.
- **D-02:** Worker pickup = continuous loop (polling interval 2s) với `SELECT ... FROM processing_job WHERE status='QUEUED' AND next_run_at <= NOW() ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1`. Khi pick → UPDATE status=RUNNING + heartbeat_at NOW(). Job step UPDATE heartbeat_at thường xuyên.
- **D-03:** Crash recovery = Reaper batch `@Scheduled(fixedDelayString=60s)` reset stale RUNNING row với `heartbeat_at < NOW() - INTERVAL '5 minutes'` về QUEUED + attempts++. Crash-safe khi worker restart giữa campaign.
- **D-04:** Transactional boundary khi POST `/execute`: API endpoint trong 1 transaction INSERT `unsubscribe_campaign` row + N × `unsubscribe_attempt` rows (state=PENDING) + 1 `processing_job` row payload `{campaignId}`. Worker pick job → loop qua attempt rows và update state. GET endpoint trả `perSender[]` đầy đủ ngay từ status=QUEUED → frontend không phải handle empty state mid-flight.

### Mailto send + auto-send boundary
- **D-05:** New isolated class `UnsubscribeMailtoSender` trong `core.cleanup.application` với `@TriageGmailWriteAllowed` boundary annotation (mở rộng từ Phase 4). ArchUnit rule extend cover class này: chỉ `TriageGmailWriter` + `UnsubscribeMailtoSender` được phép gọi `Gmail.users().messages().send()`. KHÔNG extend `TriageGmailWriter` (giữ SRP: triage vs cleanup tách biệt).
- **D-06:** Method `sendUnsubscribeMailto(...)` validate input: (a) URL ngồn là `mailto:`, (b) recipient đến từ `List-Unsubscribe` header đã persist trong `mail_message_observed`, (c) body cố định "unsubscribe" theo RFC convention. ArchUnit `TriageGmailWriteBoundaryTest` (Phase 4) extend cover class mới.
- **D-07:** New class `UnsubscribeHttpClient` trong `core.cleanup` dùng Spring `RestClient` (synchronous, virtual-thread friendly). Configuration: `redirectPolicy=NEVER` (RFC 8058 cấm follow redirect), connect timeout 5s, read timeout 10s. ArchUnit ban `WebClient` (WebFlux) vẫn enforce. ArchUnit rule mới trong `core.cleanup`: cấm `new HttpClient()` / `RestClient.create()` ngoài file `UnsubscribeHttpClient.java`.
- **D-08:** Success gate (gate archive): chỉ HTTP `200`, `202`, `204` → state=OK. 3xx (redirect) / 4xx / 5xx / timeout / IO exception → state=FAILED với `failureReason` chi tiết (`HTTP_3XX_REDIRECT`, `HTTP_4XX_{code}`, `TIMEOUT`, `NETWORK_ERROR`). Per-sender atomic: FAILED → không archive history. Mailto: nếu `UnsubscribeMailtoSender` trả messageId → OK; bất kỳ exception → FAILED.

### List-Unsubscribe URL/mailto persistence
- **D-09:** Extend `mail_message_observed` table với 3 column mới. **5 changelog tổng cho Phase 8** (numbering `041..045`, all YAML — next free sau `040-triage-audit-message-ref.yaml`):
  - `041-mail-message-observed-list-unsubscribe.yaml` — ADD COLUMN trên `mail_message_observed`:
    - `list_unsubscribe_url VARCHAR(2048) NULL`
    - `list_unsubscribe_mailto VARCHAR(512) NULL`
    - `list_unsubscribe_one_click BOOLEAN NOT NULL DEFAULT false`
  - `042-processing-job.yaml` — Generic worker queue (D-01): `id UUID PK`, `tenant_id`, `job_type` enum, `payload JSONB`, `status`, `attempts INT`, `next_run_at`, `heartbeat_at`, `created_at`, `started_at`, `finished_at`, `failure_reason` + index `(status, next_run_at)` cho SKIP LOCKED query.
  - `043-sender-suppression.yaml` — `tenant_id`, `sender_email NULL`, `sender_domain NULL` (1 trong 2 NOT NULL, check constraint), `reason` enum (`manual`, `replied`, `auto`), `created_at` + unique index `(tenant_id, sender_email)` và `(tenant_id, sender_domain)`.
  - `044-unsubscribe-campaign.yaml` — `id UUID PK`, `tenant_id`, `job_id UUID FK→processing_job`, `status`, `applied_at`, `reverted_at`, `total_sender INT`, `total_history_msg INT`, `created_at`.
  - `045-unsubscribe-attempt.yaml` — `id UUID PK`, `campaign_id FK→unsubscribe_campaign`, `sender_email`, `sender_domain`, `unsubscribe_method` enum (`ONE_CLICK`, `MAILTO`), `state` enum (`PENDING/RUNNING/OK/FAILED`), `failure_reason`, `archived_message_count INT DEFAULT 0`, `started_at`, `finished_at` + index `(campaign_id, state)`.
  KHÔNG dùng sidecar table (JOIN overhead trên candidate query), KHÔNG dùng JSONB cho domain table (CLAUDE.md "JSONB chỉ cho rule matchers + processing_job payload").
- **D-10:** Backfill = forward-only. Liquibase chỉ ADD COLUMN với default NULL/false. Row cũ giữ `list_unsubscribe_url IS NULL` dù `listUnsubscribePresent=true`. Candidate query filter `(list_unsubscribe_url IS NOT NULL OR list_unsubscribe_mailto IS NOT NULL)`. User chờ 30 ngày (= candidate window) để có đủ data — không Gmail quota burn.
- **D-11:** URL HTTPS validation = parse-time + execute-time (defense in depth). Trong `GmailPreviewReadService` parser: DROP URL nếu không bắt đầu bằng `https://` (không persist vào DB). `UnsubscribeHttpClient.post(url)` re-validate `url.startsWith("https://")` + lookup audit trail từ `mail_message_observed` để verify URL có nguồn header. Persistent invariant: mọi row trong DB có `list_unsubscribe_url` LUÔN là `https://...`. Mailto value lưu dạng `mailto:user@host?subject=...` đầy đủ (không strip subject).

### Frontend page namespace
- **D-12:** Route layout = cleanup namespace, anticipate SEED-009 siblings:
  - `/cleanup/unsubscribe-campaign` — candidate list, multi-select checkbox, "Preview campaign" button
  - `/cleanup/unsubscribe-campaign/[jobId]` — job status, per-sender state table, retry button per FAILED, undo button (nếu trong 30d)
  - `/cleanup/suppression` — suppression list CRUD (manual add/remove + xem auto-added entries)
  Sidebar nav thêm 1 entry "Cleanup" với sub-item "Unsubscribe" + "Suppression". Phase SEED-009 tương lai add `/cleanup/bulk-archive` etc. ở cùng cấp.
- **D-13:** Features folder = `apps/web/features/cleanup/unsubscribe-campaign/{api,hooks,components,query-keys.ts}` + `apps/web/features/cleanup/suppression/{...}`. TanStack Query key factory `unsubscribeCampaignKeys` + `suppressionKeys`. Tách 2 sub-feature nhỏ thay vì 1 feature `cleanup/` lớn.
- **D-14:** Entry point = menu only — không can thiệp Phase 7 `TopSendersPanel`. User vào `/cleanup/unsubscribe-campaign` từ sidebar → candidate list multi-select. Tách concern: analytics = read, cleanup = action. CTA cross-link từ analytics có thể add ở phase polish sau khi UX phase 8 đã ổn.
- **D-15:** Status page polling = TanStack Query `refetchInterval: 2000` khi `data.status ∈ {QUEUED, RUNNING}`, polling dừng khi terminal. Match SPEC requirement 5.

### Locked Decisions — addendum (chốt 2026-05-19 sau evaluation review)

- **D-16:** i18n namespace = `cleanup.unsubscribe.*` + `cleanup.suppression.*` (2 root namespace tách biệt, lock-step `apps/web/i18n/messages/{vi,en}.json`). Sidebar nav key = `nav.cleanup`. Planner viết key list chi tiết khi sinh wave frontend; bắt buộc qua `pnpm i18n:check`.
- **D-17:** Package layout `core.cleanup` = `domain/`, `application/`, `persistence/`, `projection/`, `exception/` (đúng CONVENTIONS §2 + Phase 7 pattern). `package-info.java` ở root khai báo `@ApplicationModule(allowedDependencies = {"core.gmail", "core.triage", "core.analytics", "core.shared"})`.
- **D-18:** Reaper batch placement = `backend/worker/src/main/java/com/zeromail/worker/scheduling/ProcessingJobReaperBatch.java` (generic dưới `worker/scheduling/` để reuse cho SEED-009 job types, KHÔNG đặt trong `worker/cleanup/`).
- **D-19:** `processing_job` payload cho `UNSUBSCRIBE_CAMPAIGN` = `{"campaignId": "uuid", "schemaVersion": 1}`. Worker resolve attempt rows qua `unsubscribe_attempt.campaign_id`. `schemaVersion` field reserved cho payload evolution sau này.
- **D-20:** Throttle bucket key format (Redis INCR + EXPIRE):
  - 60s window: `throttle:unsubscribe:domain:{tenantId}:{domain}:60s` (TTL 60s)
  - 1h window: `throttle:unsubscribe:domain:{tenantId}:{domain}:1h` (TTL 3600s)
  Per-tenant scope **bắt buộc** để tenant này không block tenant khác trên cùng domain. Implementation qua `RedisTemplate.opsForValue().increment(...)` + `expire(...)`.
- **D-21:** Candidate query data source = `CandidateQueryService` mới trong `core.cleanup.application` với `JdbcTemplate` + `@Transactional(readOnly=true)`. KHÔNG share với `AnalyticsSummaryQueryService` (khác filter — top-sender không quan tâm `List-Unsubscribe`). Spring Modulith boundary giữ sạch.
- **D-22:** ArchUnit rule (planner viết trong Wave 0 test stubs):
  - `core.cleanup.*` cấm `import java.net.http.HttpClient` + `org.springframework.web.client.RestClient` ngoài file `UnsubscribeHttpClient.java`.
  - Extend `GmailWriteBoundaryTest` (rename từ `TriageGmailWriteBoundaryTest`): allow-list `TriageGmailWriter` + `UnsubscribeMailtoSender` cho `Gmail.users().messages().send()`.
  - `core.cleanup.*` cấm import `java.lang.ThreadLocal` + cấm `WebClient` (carry-over Phase 1).
- **D-23:** Mailto URI parsing = `java.net.URI` built-in (Java 25 đã RFC 6068 conform). KHÔNG regex. Parser: `URI.create("mailto:...").getSchemeSpecificPart()` lấy recipient + query string; recipient validate match `mail_message_observed.list_unsubscribe_mailto` (đã persist qua D-09).
- **D-24:** UI risk badge color = `--green/--green-soft` cho `SAFE` (KHÔNG dùng `--primary`/teal để tránh conflict với CTA), `--muted/--muted-foreground` cho `NO_HEADER_DISABLED`, `--red/--red-soft` cho `SUPPRESSED_BLOCKED`. **Lock-step UI-SPEC D-15** (đã chốt trong UI-SPEC). Phase 1.6 D-15 palette áp dụng.
- **D-25:** `processing_job` retention = **purge 90 ngày** sau `finished_at` (status `COMPLETED`|`FAILED`). Implementation = `@Scheduled(cron = "0 0 3 * * *")` daily 03:00 UTC, DELETE batch ≤ 1000 row/lần. Audit trail `unsubscribe_campaign` + `unsubscribe_attempt` **giữ vĩnh viễn** (cho support + analytics retro). Placement = `backend/worker/scheduling/ProcessingJobPurgeBatch.java` (sibling của reaper batch D-18).

### Phase 7 Dependency Note

**Verify trước khi execute Phase 8:** ROADMAP.md ghi Phase 8 depends on Phase 7 (Analytics Enhancement). CONTEXT D-14 đã clarify entry point của Phase 8 là menu only — KHÔNG truy cập Phase 7 `TopSendersPanel` endpoint. Phase 7 dependency là **soft** (cho UX cross-link sau ship), **không phải hard runtime dependency**. Planner có thể bắt đầu Wave 0 Phase 8 ngay cả khi Phase 7 chưa land.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 8 spec (MUST read first)
- `.planning/phases/08-bulk-unsubscribe-campaign/08-SPEC.md` — Locked requirements (9), boundaries, constraints, acceptance criteria. Authoritative source of WHAT to build.

### Prior phase foundation
- `.planning/phases/07-analytics-enhancement/07-CONTEXT.md` — Phase 7 implementation decisions (analytics service extension pattern, shadcn chart usage, privacy logging). Top-sender query là feeder candidate.
- `.planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md` — Analytics endpoint shape + frontend feature folder convention.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java` — Boundary class hiện tại cho Gmail write API (`applyLabel`, `archiveSkipInbox`, `createDraft`). Sibling `UnsubscribeMailtoSender` follow same boundary pattern.
- `backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java` — ArchUnit guard hiện tại cho boundary. Extend cover `core.cleanup`.
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java` — Audit row write pattern; reuse cho per-message audit trong campaign.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java` (line ~515) — `listUnsubscribePresent` extraction điểm. Extend để bóc URL/mailto/one-click.
- `backend/core/src/test/java/com/zeromail/core/triage/TriagePrivacySweepTest.java` — Pattern mẫu cho `CleanupPrivacySweepTest` (Phase 8 requirement 9).

### Schema baselines
- `backend/core/src/main/resources/db/changelog/changes/032-mail-message-observed-sender-email.yaml` — Latest schema mod cho `mail_message_observed`. Phase 8 thêm changelog kế tiếp `039-...`.
- `backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml` — Audit table schema reference.
- `backend/core/src/main/resources/db/changelog/changes/024-modulith-event-publication.yaml` — Spring Modulith event_publication baseline.

### Project conventions
- `CLAUDE.md` — Java 25, Spring Boot 4, privacy invariants, backend code style (no abbreviations), Postgres-backed queue (`SKIP LOCKED`), Redis chỉ rate-limit/cache, KHÔNG WebFlux, KHÔNG raw HTTP outside Spring AI adapter (exception: `core.cleanup.UnsubscribeHttpClient` cho RFC 8058).
- `CONVENTIONS.md` §2 (backend domain package layout: domain/application/persistence/projection/exception), §5 (privacy logging format `event=<name> tenantId={}`), §6 (Spring Modulith events vs direct calls), §7 (shadcn/ui primitive selection), §8 (feature API/hooks/query keys).
- `TESTING.md` — Spring Boot 4 slice ladder; ArchUnit rules; privacy sweep pattern.

### RFC standards (external)
- RFC 8058 — One-Click Unsubscribe POST semantics. Phase 8 `UnsubscribeHttpClient` MUST conform: no redirect follow, POST only, success = 2xx.
- RFC 2369 — `List-Unsubscribe` header format.
- RFC 6068 — `mailto:` URI scheme parsing.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TriageGmailWriter` (`backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java`) — `applyLabel` + `archiveSkipInbox` reusable cho post-unsubscribe step (apply `Zero Mail/Unsubscribed` + archive history).
- `TriageAuditEntity` + `TriageAuditWriter` — đã có `revertedAt` + `gmailChangeToken`. Reuse cho per-message audit row trong campaign + Undo flow.
- `GmailPreviewReadService` (line ~515) — extraction điểm `listUnsubscribePresent`. Extend cùng class để parse URL + mailto + one-click flag.
- `AnalyticsSummaryQueryService` pattern (Phase 5C/7) — `@Transactional(readOnly=true)` multi-query service với JdbcTemplate. Áp dụng cho `CandidateQueryService` mới.
- `core.tenant.context` Scoped Values infra (Phase 1) — campaign API + worker handler bind tenant context tương tự `TriageOrchestratorService`.
- `@Sensitive` wrapper + Logback scrub filter (Phase 1) — sender email log via `@Sensitive` để mask local-part.
- Spring Modulith event_publication table (Phase 4, changelog 024) — KHÔNG dùng cho campaign job (xem D-01). Có thể dùng cho domain event `UnsubscribeCampaignCompleted` nếu cần notify analytics/audit consumer.
- Phase 7 `AnalyticsSummaryQueryService` top-sender query — input feeder cho UI hint "đã unsubscribe N sender từ tổng X tuần qua" (deferred polish).

### Established Patterns
- Spring Modulith module với `package-info.java` `@ApplicationModule(allowedDependencies = ...)`. `core.cleanup` follow same pattern: depend `core.gmail`, `core.triage` (audit), `core.analytics` (read-side).
- Liquibase YAML changelog với sequential numbering (`039-...` tiếp `038-billing-packages.yaml`).
- `from(projection, ...)` static factory trên response record cho mapping DTO ← projection.
- ArchUnit boundary tests trong `backend/core/src/test/java/com/zeromail/core/arch/` — extend cover `core.cleanup` (HTTP client ban + Gmail send ban + ThreadLocal ban).
- Per-tenant `@Transactional(readOnly=true)` JdbcTemplate query — pattern cho candidate query + status query.
- Frontend feature folder `apps/web/features/<feature>/{api,hooks,components,query-keys.ts}` (CONVENTIONS §8).
- TanStack Query `refetchInterval` cho polling job status — pattern mới (phase 4 audit không polling).
- Privacy sweep test (`TriagePrivacySweepTest`) — mirror cho `CleanupPrivacySweepTest`.

### Integration Points
- `mail_message_observed` table — 3 column mới (xem D-09), không break Phase 2A ingest path.
- `TriageGmailWriteBoundaryTest` (ArchUnit) — extend rules cover `core.cleanup` (Gmail send permitted from `UnsubscribeMailtoSender`).
- `GmailPreviewReadService` extraction — extend trong cùng class để giữ Gmail header parsing chỉ ở 1 chỗ.
- Worker: tạo `ProcessingJobWorker` + `UnsubscribeCampaignHandler` (handler routed by `job_type`). Generic worker framework có thể reuse cho phase tương lai.
- Frontend `apps/web/components/shell/Sidebar` — add nav entry "Cleanup" với 2 sub-item.
- Frontend OpenAPI typed client — `springdoc-openapi` regen sẽ thêm các endpoint mới; `apps/web/lib/api/openapi-types.ts` regen tự động.
- i18n `apps/web/i18n/messages/{vi,en}.json` lock-step thêm `cleanup.unsubscribe.*` + `cleanup.suppression.*` namespace.

</code_context>

<specifics>
## Specific Ideas

- SPEC.md đã quy định label cố định `Zero Mail/Unsubscribed` (không user-configurable) → planner reuse Gmail label create/lookup pattern từ Phase 4.
- Auto-add suppression heuristic: `triage_audit` đã có thông tin về reply (DRAFT action + user-confirm-send-via-Gmail). Heuristic "user reply ≥1 trong 90d" có thể đọc từ `triage_audit` action=`SAVE_DRAFT` + Gmail `messages.send` history (qua `gmailChangeToken` linkage). Planner xác nhận data source.
- Status terminal `COMPLETED` vs `FAILED` ở campaign level: campaign=COMPLETED khi tất cả attempt rows terminal (OK hoặc FAILED). Không có "all-must-succeed" semantic ở campaign — chỉ progress %.
- UX: preview modal hiển thị per-sender risk badge `SAFE` / `NO_HEADER_DISABLED` (xám, disable checkbox) / `SUPPRESSED_BLOCKED` (đỏ, không cho execute). User vẫn có thể uncheck SAFE sender nếu đổi ý.

</specifics>

<deferred>
## Deferred Ideas

- **CTA "Unsubscribe from this sender" trong Phase 7 TopSendersPanel** — entry point từ analytics → cleanup. Defer để phase 8 ship clean trước; thêm polish sau khi UX cleanup ổn.
- **Multi-tenant team suppression list (shared across workspace)** — out of scope per SPEC.md; phase tương lai khi team plan ra mắt.
- **Scheduled/recurring campaign** — out of scope per SPEC.md; weekly digest "newsletter mới phát hiện" là phase sau khi UX one-shot ổn.
- **Provider-aware success heuristic** (recognize "click to confirm" HTML page) — không làm v1 vì vi phạm RFC 8058 "no user interaction required"; defer cho phase observability sau.
- **Bulk archive (archive theo sender/category/age không unsubscribe), cold-email blocker, attachment auto-filing** — out of scope; phase riêng từ SEED-009. `core.cleanup` module + `processing_job` framework + `/cleanup/*` namespace ở phase 8 dọn đường cho 3 phase này.

</deferred>

---

*Phase: 08-bulk-unsubscribe-campaign*
*Context gathered: 2026-05-17*
