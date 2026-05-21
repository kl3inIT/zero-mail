---
created: 2026-05-21T00:00:00Z
title: LOW — TriageAuditControllerContractTest expects 10-field DTO; actual is 12 after Phase 4
priority: low
area: testing
source:
  phase: 04 (introducing change)
  introduced_commit: f15c6038 — feat(triage): show real sender + subject in audit log + AI manual sender add
  introduced_date: 2026-05-17
  test_introduced_commit: 52c5f402 — fix(05B): WR-03 use backend audit undo deadline
  detected_in: Phase 8 verify-work (./gradlew :backend:api:test 2026-05-21)
  status: pre_existing_contract_test_drift
files:
  - backend/api/src/test/java/com/zeromail/api/controllers/triage/TriageAuditControllerContractTest.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/AuditEntryResponse.java
---

## Problem

`TriageAuditControllerContractTest.audit_list_endpoint_returns_items_and_next_cursor_contract`
fails ở line 43 với:

```
Expecting actual:
  ["auditId", "gmailThreadId", "gmailMessageId", "subject", "senderEmail",
   "ruleName", "action", "reason", "decisionState", "createdAt",
   "undoableUntil", "draftId"]                                          <- 12 fields actual
to contain exactly (and in same order):
  ["auditId", "gmailThreadId", "gmailMessageId",
   "ruleName", "action", "reason", "decisionState", "createdAt",
   "undoableUntil", "draftId"]                                          <- 10 fields expected
but some elements were not expected:
  ["subject", "senderEmail"]
```

**Root cause:**
- Phase 4 (commit `f15c6038` ngày 2026-05-17) extend `AuditEntryResponse` DTO thêm 2 field
  `subject` + `senderEmail` (mapped từ `AuditLogRow.sanitizedSubject` + `sanitizedSenderEmail`).
- `TriageAuditControllerContractTest` (commit `52c5f402`) hardcode danh sách 10 field gốc, không
  update khi DTO mở rộng.

**Tác động:**
- 1 test fail trong `:backend:api:test`.
- Không affect production behaviour — endpoint `GET /api/triage/audit` chạy đúng và trả 12 field.
- Frontend đã consume 12 field (Phase 4 frontend update đã hoàn tất).
- Chỉ block aggregate test suite GREEN signal.

**Liên quan tới Issue HIGH `draft-privacy-sweep-fail-sanitized-subject-leak`:**
Khi fix Issue HIGH theo Approach A (rename `subject` → `subjectExcerpt`), DTO field cũng đổi
tên → test này phải update cùng. Có thể bundle 2 fix vào cùng 1 plan.

## Solution

Cập nhật array literal trong `TriageAuditControllerContractTest.java`:

```java
assertThat(fieldNames)
    .containsExactly(
        "auditId",
        "gmailThreadId",
        "gmailMessageId",
        "subject",          // ADD — match AuditEntryResponse field order
        "senderEmail",      // ADD
        "ruleName",
        "action",
        "reason",
        "decisionState",
        "createdAt",
        "undoableUntil",
        "draftId");
```

(Nếu Approach A của Issue HIGH được pick → đổi `subject` → `subjectExcerpt` ở đây.)

**Acceptance:**
- `./gradlew :backend:api:test --tests "*TriageAuditControllerContractTest*"` GREEN.

## Estimated effort

Trivial — 1 file, 2-line addition. ~5 phút. **Bundle vào fix của Issue HIGH** (privacy sweep)
để 1 plan giải quyết cả 2 — tránh churn DTO field name 2 lần.

**Trigger phase:** Cùng plan với HIGH issue, hoặc dedicated quick task nếu HIGH chưa schedule.
