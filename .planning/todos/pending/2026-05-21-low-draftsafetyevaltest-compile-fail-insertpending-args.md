---
created: 2026-05-21T00:00:00Z
title: LOW — DraftSafetyEvalTest compile fail vì TriageAuditWriter.insertPending signature widened
priority: low
area: testing
source:
  phase: 04 (introducing change)
  introduced_commit: f15c6038 — feat(triage): show real sender + subject in audit log + AI manual sender add
  introduced_date: 2026-05-17
  test_introduced_commit: ae8d477b — test(05B-07): add deterministic draft eval gate
  test_introduced_date: 2026-05-13
  detected_in: Phase 8 verify-work (JetBrains build_project 2026-05-21)
  status: pre_existing_aiEval_test_drift
files:
  - backend/core/src/aiEval/java/com/zeromail/core/aiEval/DraftSafetyEvalTest.java
---

## Problem

`./gradlew :backend:core:compileAiEvalJava` fail với 2 compile error:

```
DraftSafetyEvalTest.java:124: error: method insertPending in class TriageAuditWriter
  cannot be applied to given types;
  required: UUID,String,String,String,String,UUID,String,RuleActionType,TriageActionResult,String
  found:    Object,String,String,Object,String,Object,Object,String
  reason: actual and formal argument lists differ in length

DraftSafetyEvalTest.java:132: error: incompatible types: String cannot be converted to RuleActionType
```

**Root cause:**
- Phase 4 (commit `f15c6038` ngày 2026-05-17) extend `TriageAuditWriter.insertPending(...)` từ
  8 arg lên 10 arg (thêm `sanitizedSubject`, `sanitizedSenderEmail`).
- `DraftSafetyEvalTest.java` (commit `ae8d477b` ngày 2026-05-13, trước Phase 4) `verify(...).insertPending(any(), anyString(), anyString(), any(), anyString(), any(), any(), anyString())` — 8 arg.
- aiEval test chưa được update khi Phase 4 mở rộng signature.

**Tác động:**
- aiEval source set là **separate Gradle source set** với `@Tag("llm-eval")` — KHÔNG chạy mặc định
  trong `./gradlew test`.
- Chỉ chạy qua dedicated task `./gradlew llmEval` (gate bởi env var `OPENROUTER_API_KEY`).
- Không ảnh hưởng CI pipeline mặc định, không ảnh hưởng production.
- Chỉ block người chạy LLM eval suite thủ công.

## Solution

Update Mockito verify trong `DraftSafetyEvalTest.java`:

```java
verify(triageAuditWriter, never())
        .insertPending(
                any(),              // tenantId
                anyString(),        // gmailMessageId
                anyString(),        // gmailThreadId
                any(),              // sanitizedSubject (NEW arg 4)
                anyString(),        // sanitizedSenderEmail (NEW arg 5)
                any(),              // ruleId
                anyString(),        // ruleNameSnapshot
                any(RuleActionType.class), // FIX: was anyString() at line 132
                any(),              // preWriteIntent (TriageActionResult)
                anyString());       // reasonEvidence
```

Cross-check thứ tự arg với `TriageAuditWriter.java` signature hiện tại.

**Acceptance:**
- `./gradlew :backend:core:compileAiEvalJava` BUILD SUCCESSFUL.
- `./gradlew :backend:core:aiEvalTest` (nếu có task) compile OK (không cần chạy unless có
  OPENROUTER_API_KEY trong env).

## Estimated effort

Trivial — 1 file, 2-line fix. ~5 phút. Có thể bundle vào next phase's housekeeping commit
hoặc tạo quick task riêng.

**Trigger phase:** Bất kỳ phase nào touch aiEval source set, hoặc dedicated quick fix.
Không khẩn cấp.
