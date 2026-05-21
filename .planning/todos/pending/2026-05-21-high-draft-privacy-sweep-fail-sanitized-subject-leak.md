---
created: 2026-05-21T00:00:00Z
title: HIGH — DraftPrivacySweepTest fail vì AuditLogRow.sanitizedSubject lưu raw subject
priority: high
area: privacy
source:
  phase: 04 (introducing change)
  introduced_commit: f15c6038 — feat(triage): show real sender + subject in audit log + AI manual sender add
  introduced_date: 2026-05-17
  detected_in: Phase 8 verify-work (08-UAT.md run 2026-05-21)
  status: pre_existing_blocker
files:
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java
  - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogRow.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/AuditLogQueryService.java
  - backend/core/src/test/java/com/zeromail/core/draft/DraftPrivacySweepTest.java
---

## Problem

`DraftPrivacySweepTest.draft_classify_and_list_success_paths_never_leak_content_to_logs_exceptions_or_storage`
fails on line 397 with this assertion:

```
[audit projection result leaked forbidden token: EMAIL_SUBJECT_SENTINEL_05B_07]
Expecting actual:
  "AuditLogPage[items=[AuditLogRow[...
    sanitizedSubject=EMAIL_SUBJECT_SENTINEL_05B_07, ...]], nextCursor=null]"
not to contain: "EMAIL_SUBJECT_SENTINEL_05B_07"
```

**Root cause:**
- Phase 4 (commit `f15c6038` ngày 2026-05-17) thêm column `sanitized_subject` vào table `triage_audit`
  (Liquibase changelog `040-triage-audit-message-ref.yaml`) với mục đích cho audit log UI render
  được "ai/cái gì". Column được khai báo `VARCHAR(200)` — tức là **trích đoạn 200 ký tự đầu**, không
  truncate / mask / redact PII pattern.
- `TriageAuditEntity.sanitizedSubject` field + writer call site lưu **raw subject** đầy đủ (bị cap
  cứng ở 200 char nhưng không sanitize content).
- `AuditLogRow` projection expose field này nguyên xi qua `AuditLogQueryService.page(...)`.
- Phase 5B-07 `DraftPrivacySweepTest` (commit `798e020c`) seed audit row với subject
  `"EMAIL_SUBJECT_SENTINEL_05B_07"` và assert query projection không chứa token này.
- Test fail vì storage không thực sự sanitize — chỉ trim độ dài.

**Tác động:**
- **Vi phạm privacy invariant** CLAUDE.md: "No long-term storage of raw email bodies"
- Subject email không phải body, nhưng vẫn là PII (chứa người gửi, chủ đề công việc, có thể có
  số đơn hàng / tracking code / OTP-like substring).
- Audit log endpoint `GET /api/triage/audit` trả raw subject về frontend (sau Phase 4) → UI
  hiển thị → bất kỳ ai có session cookie đều xem được lịch sử subject của user.
- Vi phạm 1 trong 9 core constraint CLAUDE.md: "Content always sanitized + truncated +
  prompt-injection-hardened before hitting any LLM" — đây là path storage chứ không phải LLM,
  nhưng tinh thần convention là **không bao giờ persist raw content**.

**Tại sao chưa fix:** Phase 4 ship trong sprint Audit Log UI; reviewer đã accept với premise
là column tên `sanitized_subject` => caller có trách nhiệm sanitize trước khi gọi writer.
Thực tế caller (TriageOrchestratorService) pass subject từ Gmail header gần như nguyên vẹn.

## Solution

**Hai approach, chọn 1:**

### Approach A — Sanitize tại writer seam (recommended)

Trong `TriageAuditWriter.insertPending(...)`:
1. Truncate subject xuống ≤ 64 ký tự (đủ cho "Re: Order #12345 confirmation..." dạng cắt).
2. Replace pattern PII: email regex `[a-z0-9._+-]+@[a-z0-9.-]+\.[a-z]{2,}` → `***@***`;
   sequence ≥ 6 chữ số liên tiếp (đơn hàng, OTP, tracking) → `***`.
3. Lưu vào column `sanitized_subject` đã sanitize, không phải raw.

**Đổi entity field name → `subjectExcerpt`** (rõ hơn về ý nghĩa: trích đoạn, không phải subject
gốc). Liquibase changelog mới rename column (xem D-C2 invariant — name() == id() — không apply
cho column name).

### Approach B — Drop column, dùng hash thay thế

Nếu subject excerpt không cần render trong UI:
1. Liquibase changelog drop `sanitized_subject` + `sanitized_sender_email` columns.
2. AuditLogRow / AuditEntryResponse field `subject` / `senderEmail` → null hoặc remove entirely.
3. Frontend audit log fallback hiển thị `gmailMessageId` (đã có).
4. Trade-off: UX kém hơn (không biết audit row tương ứng email nào nếu mở Gmail ngoài).

**Approach A là choice mặc định** — giữ UX và sanitize đúng cách. Bùn `subjectExcerpt` rename
xảy ra qua single migration + entity field rename + DTO field rename + Wave 8 test fixture
update (TriageAuditWriterCleanupArchiveTest, DraftPrivacySweepTest).

**Acceptance:**
- `./gradlew :backend:core:test --tests "*DraftPrivacySweepTest*"` GREEN.
- New test `TriageAuditWriterSanitizationTest` asserts:
  - Input subject "Re: Order #1234567 confirmed for boss@example.com" → stored as
    "Re: Order *** confirmed for ***@***" (truncated + masked).
  - Input subject 250 chars → stored ≤ 64 chars.
- Audit log UI vẫn render `subjectExcerpt` (UX không regression sau migration).

## Estimated effort

Medium — 1 focused plan với ~6 task:
1. Liquibase changelog rename column + reset constraint check.
2. Entity field rename + writer sanitize logic.
3. AuditLogRow + AuditEntryResponse field rename + DTO migration.
4. Backend test fixture update (DraftPrivacySweep, TriageAuditWriterCleanupArchive, etc.).
5. Frontend audit log column header i18n key rename (subject → subjectExcerpt).
6. UNS-09 CleanupPrivacySweepTest re-verify (phải vẫn GREEN sau migration).

**Trigger phase:** Phase 8.1 hoặc Phase 9 (security hardening). **Phải fix trước public ship.**
