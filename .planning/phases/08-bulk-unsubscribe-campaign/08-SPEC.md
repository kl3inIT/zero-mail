# Phase 8: Bulk Unsubscribe Campaign — Specification

**Created:** 2026-05-17
**Ambiguity score:** 0.10
**Requirements:** 9 locked

## Goal

User chọn một nhóm sender newsletter (≤ 25 sender, ≤ 2000 history message) và thực thi một campaign "unsubscribe sender + archive lịch sử" theo lô — preview-bắt-buộc trước khi execute, đường unsubscribe ưu tiên `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058), fallback `mailto:`, KHÔNG bao giờ click HTTP unsubscribe URL từ body email. Mỗi campaign reversible bằng nút Undo trong 30 ngày (restore archived mail về INBOX + remove label `Zero Mail/Unsubscribed`).

## Background

Codebase hiện chỉ hỗ trợ 3 action: `LABEL`, `ARCHIVE`, `SAVE_DRAFT` qua `RuleActionType.java`. `TriageGmailWriter` (Spring Component, boundary class duy nhất gọi Gmail write API) chỉ có `applyLabel`, `archiveSkipInbox`, `createDraft` — chưa có path nào gọi unsubscribe POST hay gửi mailto.

`GmailPreviewReadService.java:515` đã trích `boolean listUnsubscribePresent` từ header `List-Unsubscribe` của mỗi message, nhưng KHÔNG lưu giá trị URL/mailto thực tế. Cần extend extraction để bóc tách `List-Unsubscribe` value (chứa `<https://...>` và/hoặc `<mailto:...>`) cùng `List-Unsubscribe-Post` (để xác định RFC 8058 one-click).

Phase 7 (Analytics Enhancement) cung cấp top 10 sender + domain grouping qua `/api/analytics/summary` — đây là feeder tự nhiên cho UI chọn candidate.

Audit log (`TriageAuditEntity`) đã có `appliedAt` / `revertedAt` / `gmailChangeToken` đủ để re-use cho per-message rows trong một campaign.

Suppression list ("never touch these senders/domains") chưa có table — cần thêm `sender_suppression` mới.

## Requirements

1. **Candidate discovery API**: Backend liệt kê sender đủ điều kiện cho campaign.
   - Current: Không có endpoint nào trả candidate. Analytics top-sender chỉ trả counts, không có flag `unsubscribeMethod`.
   - Target: `GET /api/unsubscribe/candidates?window=30d&limit=25` trả mảng `{senderEmail, senderDomain, messageCount, lastSeenAt, unsubscribeMethod ∈ {ONE_CLICK, MAILTO, NONE}, suppressed: boolean}` — chỉ trả sender CÓ `List-Unsubscribe` header trong ≥1 message của window và KHÔNG nằm trong suppression list.
   - Acceptance: Với fixture 3 sender (1 one-click, 1 mailto, 1 không có header), endpoint trả đúng 2 sender với `unsubscribeMethod` chính xác; sender thứ 3 bị loại; sender đang ở suppression list không xuất hiện.

2. **Suppression list CRUD**: User quản lý "không bao giờ unsubscribe" danh sách.
   - Current: Không có table, không có endpoint, không có UI.
   - Target: Table `sender_suppression(tenant_id, sender_email | sender_domain, reason, created_at)`. Endpoint `GET/POST/DELETE /api/unsubscribe/suppression`. UI `/settings/unsubscribe-suppression` cho CRUD manual. Auto-add khi user reply (qua `triage_audit` hint) — heuristic: nếu user đã reply ≥1 lần với sender trong 90 ngày → auto-add vào suppression với `reason='replied'`.
   - Acceptance: User add `boss@example.com` → endpoint candidates không bao giờ trả sender đó; user reply 1 lần qua test fixture → sender tự xuất hiện trong suppression list sau next ingest cycle.

3. **Campaign preview (dry-run)**: User xem trước CHÍNH XÁC những gì sẽ xảy ra trước khi execute.
   - Current: Không có flow campaign nào.
   - Target: `POST /api/unsubscribe/campaigns/preview` body `{senderEmails: string[]}` trả `{campaignId (transient), perSender: [{senderEmail, unsubscribeMethod, historyMessageCount, willArchive: boolean, riskBadge ∈ {SAFE, NO_HEADER_DISABLED, SUPPRESSED_BLOCKED}}]}`. Endpoint reject nếu `senderEmails.length > 25` (HTTP 400) hoặc tổng `historyMessageCount > 2000` (HTTP 400 với cap detail).
   - Acceptance: Preview với 26 sender → HTTP 400 với code `CAMPAIGN_TOO_MANY_SENDERS`; preview với 25 sender / 2100 history → HTTP 400 `CAMPAIGN_TOO_MANY_MESSAGES`; preview với 5 sender mix (3 one-click, 1 mailto, 1 không header) → trả riskBadge `NO_HEADER_DISABLED` cho sender không header và `willArchive=false` cho sender đó.

4. **Campaign execute (async job)**: Endpoint nhận campaign và trả jobId, worker thực thi nền.
   - Current: Không có job framework cho user-triggered async. Worker hiện chỉ poll `processing_job` cho Gmail ingest.
   - Target: `POST /api/unsubscribe/campaigns/execute` body `{senderEmails: string[]}` (đã pass preview validation) → trả `{jobId: UUID, status: 'QUEUED'}`. Worker pick `processing_job` type `UNSUBSCRIBE_CAMPAIGN`, thực thi tuần tự per-sender với throttle 1 step/domain/60s, max 10 steps/domain/h. Per-sender: (a) gọi unsubscribe (RFC 8058 POST hoặc mailto via Gmail send-as-self), (b) nếu OK → apply `Zero Mail/Unsubscribed` + archive lịch sử messages, (c) nếu FAIL → bỏ qua archive, mark sender state `FAILED` với `failureReason`.
   - Acceptance: Test fixture với 3 sender (2 one-click OK, 1 mailto bounce) → sau khi job xong: 2 sender đầu có audit row APPLIED + history mail archived; sender thứ 3 có audit row FAILED + KHÔNG có history mail nào bị archive (count `removed_inbox_labelid` = 0 cho sender đó).

5. **Campaign status & per-sender state**: User theo dõi tiến độ job.
   - Current: Không có status endpoint.
   - Target: `GET /api/unsubscribe/campaigns/{jobId}` trả `{jobId, status ∈ {QUEUED, RUNNING, COMPLETED, FAILED}, progressPct (0-100), perSender: [{senderEmail, state ∈ {PENDING, RUNNING, OK, FAILED}, failureReason?, archivedMessageCount}]}`. Frontend `/cleanup/unsubscribe-campaign/{jobId}` poll endpoint mỗi 2s khi `status ∈ {QUEUED, RUNNING}`, dừng poll khi terminal.
   - Acceptance: Trong test với 5 sender, lúc job đang chạy endpoint trả `status=RUNNING` với progressPct tăng dần; khi xong trả `status=COMPLETED` và mảng perSender đủ 5 phần tử với state terminal.

6. **Per-sender retry**: Sender FAILED có thể retry độc lập.
   - Current: Không có retry mechanism cho user-triggered work.
   - Target: `POST /api/unsubscribe/campaigns/{jobId}/senders/{senderEmail}/retry` enqueue thêm 1 sender step vào worker; idempotent (gọi lại khi state đã OK thì HTTP 409). Retry chia sẻ cùng throttle bucket.
   - Acceptance: Sender FAILED → user gọi retry → state về RUNNING → kết thúc thành OK hoặc FAILED mới với failureReason cập nhật; gọi retry lần 2 trên sender đã OK → HTTP 409.

7. **Undo campaign**: Reversible trong 30 ngày kể từ `appliedAt`.
   - Current: `TriageAuditEntity` đã có `revertedAt` cho từng audit row, nhưng chưa có aggregate "undo campaign".
   - Target: `POST /api/unsubscribe/campaigns/{jobId}/undo` chạy nền: với mỗi message đã archive trong campaign → add lại label `INBOX` qua `TriageGmailWriter` + remove label `Zero Mail/Unsubscribed`. Đánh dấu `revertedAt` trên mọi audit row. Endpoint reject nếu `now - appliedAt > 30 ngày` (HTTP 410 GONE với code `UNDO_WINDOW_EXPIRED`). Không cố gắng "un-unsubscribe" với provider (RFC 8058 one-way).
   - Acceptance: Campaign execute hôm nay với 50 history mail → undo trong cùng ngày → tất cả 50 mail có label `INBOX` lại, label `Zero Mail/Unsubscribed` bị remove, `revertedAt` được set; cùng campaign sau 31 ngày → HTTP 410.

8. **HTTP unsubscribe gate (security boundary)**: KHÔNG bao giờ click URL từ body email.
   - Current: Mã nguồn không có chỗ nào fetch URL ngoài Gmail API; nhưng cũng không có guard rõ ràng cho phase này.
   - Target: `UnsubscribeExecutor` chỉ chấp nhận URL từ header `List-Unsubscribe` + flag `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058). URL từ `mailto:` được gửi qua Gmail send-as-self. ArchUnit/Modulith guard: không class nào trong `core.cleanup.*` được phép tạo `HttpClient` ngoài file `UnsubscribeHttpClient.java`; `UnsubscribeHttpClient` chỉ accept URL bắt đầu bằng `https://` và đến từ trường `List-Unsubscribe` header đã được persist (không phải string từ input bất kỳ).
   - Acceptance: ArchUnit test rejects bất kỳ usage `new HttpClient()` hoặc Spring `RestClient` trong `core.cleanup.*` ngoài `UnsubscribeHttpClient`; unit test cho `UnsubscribeHttpClient` reject URL `http://...` và URL không có audit-record nguồn `List-Unsubscribe`.

9. **Privacy invariant** (Phase 1 carry-over): Không log body, subject, hay token.
   - Current: Logback scrub + ArchUnit bans từ Phase 1 đã enforce cho code hiện tại.
   - Target: Mọi log line từ `core.cleanup.*` format `event=<name> tenantId={} senderDomain={} ...` — sender_domain OK (đã decode từ header, không phải PII full); sender_email KHÔNG được log đầy đủ (hash hoặc local-part-masked). Test sweep: grep log output từ campaign fixture run, không tìm thấy subject, body, hay full sender email.
   - Acceptance: `CleanupPrivacySweepTest` (sibling của `TriagePrivacySweepTest`) chạy campaign fixture, capture logs, assert không có pattern email regex `[a-z0-9._+-]+@[a-z0-9.-]+\.[a-z]{2,}` xuất hiện trong any log line trừ dạng masked (`***@example.com`).

## Boundaries

**In scope:**
- `core.cleanup` Spring Modulith module (domain/application/persistence)
- Tables: `sender_suppression`, `unsubscribe_campaign`, `unsubscribe_attempt` (per-sender state)
- Endpoints: `GET /api/unsubscribe/candidates`, `POST /api/unsubscribe/campaigns/preview|execute`, `GET /api/unsubscribe/campaigns/{id}`, `POST /api/unsubscribe/campaigns/{id}/senders/{email}/retry`, `POST /api/unsubscribe/campaigns/{id}/undo`, suppression CRUD
- `UnsubscribeExecutor` + `UnsubscribeHttpClient` (RFC 8058 one-click POST) + `UnsubscribeMailtoSender` (gửi mailto qua Gmail send-as-self qua `TriageGmailWriter`)
- Extend `GmailPreviewReadService` để extract `List-Unsubscribe` URL/mailto + `List-Unsubscribe-Post` flag, persist vào `mail_message_observed` (Liquibase changelog)
- Worker job type `UNSUBSCRIBE_CAMPAIGN` với throttle bucket per-domain
- Frontend `/cleanup/unsubscribe-campaign`, `/cleanup/unsubscribe-campaign/[jobId]`, `/cleanup/suppression` (Next.js pages — namespace `/cleanup/*` chốt tại CONTEXT D-12 để anticipate SEED-009): candidate list, preview modal, execute button, job status, undo button, suppression page
- Audit log integration: 1 audit row per message archived; 1 audit row per unsubscribe step
- Privacy sweep test riêng cho module mới

**Out of scope:**
- Bulk archiver (archive theo sender/category/age mà KHÔNG unsubscribe) — sẽ là phase riêng từ SEED-009
- Cold-email blocker (classify first-time senders) — sẽ là phase riêng từ SEED-009
- Smart filing of attachments (Drive/OneDrive) — sẽ là phase riêng từ SEED-009 (cần scope dependency mới)
- Auto-unsubscribe (rule-based) — `UNSUBSCRIBE` KHÔNG được thêm vào `RuleActionType`. Chỉ user-triggered campaign.
- Scheduled/recurring campaign — manual one-shot only; weekly digest "newsletter mới phát hiện" sẽ là phase sau khi UX của one-shot ổn
- Permanent delete — chỉ archive + label, không bao giờ `users.messages.trash` hay `users.messages.delete`
- Click HTTP unsubscribe URL từ email body — security boundary, không bao giờ
- Per-campaign custom label name hay per-sender label — label cố định `Zero Mail/Unsubscribed`
- Multi-tenant team suppression list (shared across workspace members) — phase 8 chỉ per-user-account
- Phase 5C digest tích hợp — independent

## Constraints

- **Privacy**: Phase 1 invariant carry-over — không lưu body, subject, hay token trong logs hay DB ngoài những gì đã tồn tại.
- **Action surface**: `UNSUBSCRIBE` KHÔNG được thêm vào `RuleActionType` để giữ ràng buộc v1 "label/archive/save_draft only" trong rule engine. Là một code path tách biệt trong module `core.cleanup`.
- **Reversibility**: 30 ngày Undo window cứng (không config được).
- **Hard caps**: 25 sender / 2000 history message per campaign — backend reject HTTP 400. Hard, không bypass.
- **Throttle**: 1 unsubscribe step / domain / 60s, max 10 steps / domain / h — enforce ở worker.
- **HTTP boundary**: `UnsubscribeHttpClient` chỉ accept URL `https://` đến từ `List-Unsubscribe` header đã persist; ArchUnit cấm `HttpClient` / `RestClient` mới trong `core.cleanup.*`.
- **Per-sender atomic**: Unsubscribe fail → KHÔNG archive history. State `FAILED` + retry button.
- **Gmail write boundary**: Mọi write (label, archive, send mailto) phải đi qua `TriageGmailWriter` hoặc class trong cùng `@TriageGmailWriteAllowed` boundary; ArchUnit guard hiện tại được mở rộng để cover module `core.cleanup`.
- **Throughput vs safety**: Async worker chứ không sync — campaign có thể mất vài phút khi đầy 2000 mail.
- **Spring Modulith**: `core.cleanup` là module mới với event contract khai báo trong `package-info`; depend on `core.gmail`, `core.triage` (audit), `core.analytics` (read-side candidate query).

## Acceptance Criteria

- [ ] `GET /api/unsubscribe/candidates` trả đúng candidate trong test fixture (3 sender → 2 hợp lệ, 1 không có header bị loại, suppression sender bị loại)
- [ ] `POST /api/unsubscribe/campaigns/preview` với 26 sender → HTTP 400 `CAMPAIGN_TOO_MANY_SENDERS`
- [ ] `POST /api/unsubscribe/campaigns/preview` với tổng >2000 history mail → HTTP 400 `CAMPAIGN_TOO_MANY_MESSAGES`
- [ ] `POST /api/unsubscribe/campaigns/execute` trả jobId; worker xử lý async; per-sender atomic (fail unsubscribe = không archive)
- [ ] `GET /api/unsubscribe/campaigns/{jobId}` trả progressPct + perSender array với state terminal khi xong
- [ ] Throttle 1 step/domain/60s, max 10/domain/h enforce trong worker (verify qua test với 12 sender cùng domain)
- [ ] Sender FAILED có thể retry độc lập qua `POST .../senders/{email}/retry`; idempotent với HTTP 409 khi đã OK
- [ ] `POST .../undo` trong 30 ngày → restore archived mail về INBOX + remove label `Zero Mail/Unsubscribed` + set `revertedAt`
- [ ] `POST .../undo` sau 30 ngày → HTTP 410 `UNDO_WINDOW_EXPIRED`
- [ ] Suppression list CRUD endpoint + auto-add khi user reply (qua heuristic ≥1 reply trong 90d)
- [ ] ArchUnit test reject `HttpClient` / `RestClient` mới trong `core.cleanup.*` ngoài `UnsubscribeHttpClient`
- [ ] `UnsubscribeHttpClient` reject URL `http://...` và URL không đến từ `List-Unsubscribe` header đã persist
- [ ] `CleanupPrivacySweepTest` (sibling của `TriagePrivacySweepTest`) PASS — không log full email/body/subject
- [ ] Frontend `/cleanup/unsubscribe-campaign` + `/cleanup/unsubscribe-campaign/[jobId]` + `/cleanup/suppression`: candidate list + preview modal + execute + job status + undo button + suppression page — hoạt động trong Playwright e2e golden path
- [ ] `pnpm tsc` + ESLint + Vitest + `i18n:check` GREEN; Spring `./gradlew test` + ArchUnit + Modulith verification GREEN

## Ambiguity Report

| Dimension          | Score | Min  | Status | Notes                                                       |
|--------------------|-------|------|--------|-------------------------------------------------------------|
| Goal Clarity       | 0.90  | 0.75 | ✓      | Scope hẹp (chỉ unsubscribe + archive), action API-only      |
| Boundary Clarity   | 0.92  | 0.70 | ✓      | Out-of-scope liệt kê 9 mục với lý do                        |
| Constraint Clarity | 0.92  | 0.65 | ✓      | Caps cứng, throttle cụ thể, undo window 30d, no HTTP-body   |
| Acceptance Criteria| 0.88  | 0.70 | ✓      | 14 pass/fail checkboxes                                     |
| **Ambiguity**      | 0.10  | ≤0.20| ✓      |                                                             |

## Interview Log

| Round | Perspective        | Question summary                                       | Decision locked                                                              |
|-------|--------------------|--------------------------------------------------------|------------------------------------------------------------------------------|
| 1     | Researcher         | Phase 8 = cả SEED-009 hay chỉ một phần?                | Chỉ bulk unsubscribe + archive history; cold-email & filing là phase khác   |
| 1     | Researcher         | UNSUBSCRIBE vào RuleActionType?                        | KHÔNG — campaign API-only, không là rule action                              |
| 2     | Researcher         | Suppression list trong phase này?                      | CÓ — UI + CRUD + auto-add heuristic (reply ≥1/90d)                          |
| 2     | Simplifier         | Execute flow MVP?                                      | Async job, jobId, status page, progress %, per-sender state                  |
| 2     | Boundary Keeper    | Campaign cap?                                          | 25 sender / 2000 history mail, hard reject HTTP 400                          |
| 3     | Boundary Keeper    | Undo semantics?                                        | Restore archived → INBOX + remove label `Zero Mail/Unsubscribed`             |
| 3     | Boundary Keeper    | Undo window?                                           | 30 ngày, HTTP 410 sau đó                                                     |
| 3     | Failure Analyst    | Unsubscribe POST/mailto fails?                         | Per-sender atomic: fail = không archive; retry button per-sender             |
| 4     | Boundary Keeper    | Label tùy biến?                                        | Cố định `Zero Mail/Unsubscribed`, không user-configurable                    |
| 4     | Failure Analyst    | Per-domain throttle?                                   | 1 step/domain/60s, max 10/domain/h                                           |
| 4     | Boundary Keeper    | Scheduled/recurring campaign?                          | Không — manual one-shot only, weekly digest là phase sau                     |

---

*Phase: 08-bulk-unsubscribe-campaign*
*Spec created: 2026-05-17*
*Next step: /gsd:discuss-phase 8 — implementation decisions (Liquibase changelog, Spring Modulith module, throttle bucket pattern, Playwright fixture structure, etc.)*
