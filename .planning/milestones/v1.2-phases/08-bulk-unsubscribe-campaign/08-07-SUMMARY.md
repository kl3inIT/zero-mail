---
phase: 08-bulk-unsubscribe-campaign
plan: 07
subsystem: cleanup
tags: [cleanup, usecases, preview, execute, status, retry, undo, transaction-d04, h-3-precise-undo, m-9-progress-guard, uns-03, uns-04, uns-05, uns-06, uns-07]
requirements: [UNS-03, UNS-04, UNS-05, UNS-06, UNS-07]
dependency_graph:
  requires:
    - 08-03 (cleanup persistence — UnsubscribeCampaignEntity/Repository, UnsubscribeAttemptEntity/Repository, ProcessingJobEntity/Repository, SenderSuppressionRepository, UnsubscribeCampaignPolicy, exceptions)
    - 08-04 (CandidateQueryService — preview reuses the same 30-day mail_message_observed window contract; cap policy lives in UnsubscribeCampaignPolicy)
    - 08-06 (worker — UnsubscribeCampaignHandler consumes the processing_job row this plan writes; TriageGmailWriter.lookupLabelId added in Plan 06 is the H-2 dependency for undo)
    - core.cleanup.projection.CampaignStatusProjection + PerSenderAttemptProjection (Wave 3 read-side records)
    - core.triage.persistence.TriageAuditRepository.findCleanupArchiveRowsForUndo (Plan 03 Task 4 — H-3 query path)
    - core.triage.persistence (changelog 046 — triage_audit.source column + idx_triage_audit_cleanup partial index)
    - core.tenant.TenantContext (ScopedValue tenant binding for inflight controller delegation)
    - tools.jackson.databind.ObjectMapper (Jackson 3 — payload serialization for processing_job.payload)
  provides:
    - core.cleanup.usecases.CampaignPreviewService (UNS-03 dry-run cap validation + risk-badge mapping)
    - core.cleanup.usecases.CampaignExecuteService (UNS-04 single-transaction INSERT campaign + N attempts + processing_job, D-04)
    - core.cleanup.usecases.CampaignStatusQueryService (UNS-05 jobId -> CampaignStatusProjection read-side polling source, M-9 div-by-zero guard)
    - core.cleanup.usecases.CampaignRetryService (UNS-06 per-sender retry with 409 idempotency via CampaignRetryConflictException)
    - core.cleanup.usecases.CampaignUndoService (UNS-07a within-30d INBOX restore + label remove, UNS-07b past-30d UndoWindowExpiredException, H-3 precise undo via source='CLEANUP_CAMPAIGN')
  affects:
    - Wave 0 RED test CampaignUndoServiceTest flipped GREEN (5 tests including 2 new H-2 + H-3 pin tests)
    - Wave 0 RED test UnsubscribeCampaignE2ETest (worker) flipped GREEN — CampaignExecuteService is the missing class
    - Wave 0 RED test CleanupPrivacySweepTest.future_campaign_execute_service_is_present flipped GREEN (the sibling log-leak test is still RED for an unrelated seed bug — tracked in deferred-items)
    - Wave 5 carry-over TriageAuditWriterCleanupArchiveTest.recordCleanupArchive_doesNotInterfereWithSourceTriageRows flipped GREEN (Rule 1 auto-fix of same schema-column seed bug)
    - Cleared deferred-items.md entries #1 (TriageAuditWriterCleanupArchiveTest) and #5 (CampaignUndoServiceTest) — remaining items #3 (CleanupModuleVerification) + privacy-sweep log-leak test are still deferred to a future wave
tech_stack:
  added: []
  patterns:
    - "@Transactional(readOnly = true) at class level on read-side query services (CampaignPreviewService, CampaignStatusQueryService) — mirrors CandidateQueryService"
    - "@Transactional (write mode, default REQUIRED propagation) at class level on write-side services (CampaignExecuteService, CampaignRetryService, CampaignUndoService) — D-04 atomic INSERT discipline"
    - "D-19 payload schema {\"campaignId\":\"<uuid>\",\"schemaVersion\":1} serialized via injected tools.jackson.databind.ObjectMapper; private record CampaignJobPayload nested inside the service"
    - "Caller-side UUID.randomUUID() per entity construction — UnsubscribeCampaignEntity / UnsubscribeAttemptEntity / ProcessingJobEntity all take id as the first ctor arg (Hibernate proxy gotcha — never rely on @GeneratedValue)"
    - "M-9 div-by-zero guard: explicit `if (attempts.isEmpty()) return 0;` literal BEFORE division in CampaignStatusQueryService.computeProgressPct; long arithmetic `terminalCount * 100L / attempts.size()` prevents int overflow even though caps are small"
    - "H-3 Path A precise undo: SQL filters `source = 'CLEANUP_CAMPAIGN' AND external_ref = campaignId.toString() AND reverted_at IS NULL` so rule-driven `source='TRIAGE'` rows for the same sender are never touched"
    - "H-2 graceful label-missing handling: TriageGmailWriter.lookupLabelId returns Optional<String>; undo restoreToInbox always runs, removeLabel only when the label still exists in Gmail"
    - "Defense-in-depth: CampaignExecuteService re-invokes CampaignPreviewService.preview() even when the controller already validated, so a bypassed/missing controller cap-check cannot leak through"
    - "Privacy: every log statement uses senderDomain + counts only (no senderEmail); structured `event=` prefix on every log line per CONVENTIONS §5"
    - "Backend naming: fully-resolved identifiers (tenantId, jobId, campaignId, senderEmail, gmailMessageId, archivedGmailMessageIds, restoredCount, attempt, normalizedSenderEmail) — no req/res/svc/cfg/ctx/msg/err/ex/e abbreviations"
key_files:
  created:
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignPreviewService.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignExecuteService.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignStatusQueryService.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignRetryService.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignUndoService.java
  modified:
    - backend/core/src/test/java/com/zeromail/core/cleanup/usecases/CampaignUndoServiceTest.java (Wave 0 seed bugs fixed + new H-2 + H-3 pin tests added)
    - backend/core/src/test/java/com/zeromail/core/triage/persistence/TriageAuditWriterCleanupArchiveTest.java (Wave 5 seed bug fixed — Rule 1 auto-fix)
    - .planning/phases/08-bulk-unsubscribe-campaign/deferred-items.md (cleared resolved entries, documented remaining out-of-scope failures)
decisions:
  - "M-5 invariant pinned in code: CampaignExecuteService.execute(...) sets campaign.jobId before commit; the schema column stays NULLABLE for forward-compat with future preview-only campaign states, but the transactional path always populates it."
  - "Status query keyed by jobId (not campaignId) — frontend's polling key from CampaignExecuteResult is the jobId; the read-side resolves jobId -> campaignId via unsubscribe_campaign.job_id FK lookup, so the controller does not need a separate mapping layer."
  - "Retry semantics restricted to FAILED -> PENDING only. PENDING / RUNNING attempts also throw CampaignRetryConflictException (in-flight, would race with worker). OK is the headline 409 from SPEC."
  - "Undo method takes UUID campaignId (not jobId) — locked by Wave 0 CampaignUndoServiceTest's reflective method lookup. Controller will use unsubscribe_campaign.job_id -> id lookup if the user clicks undo on a job-id polling URL."
  - "Constructor signature CampaignUndoService(JdbcTemplate, TriageGmailWriter, Clock) — locked by Wave 0 test's 3-arg reflective constructor lookup. No repository injection; all DB access via JdbcTemplate."
  - "Wave 0 test rewrite under Rule 1: the original seed assertions (applyLabel(INBOX) + removeLabel(LABEL_NAME)) were stale relative to the cleaner Wave 5 production primitives (restoreToInbox + lookupLabelId-then-removeLabel(labelId)). The test was updated to verify the production-correct shape, plus a new H-3 false-positive pin test and a new H-2 user-deleted-label tolerance test."
  - "Empty-archivable-selection raises IllegalStateException (not CampaignCapExceededException) — the caps are about UPPER bounds; selecting zero willArchive() senders is a controller-side validation bug, not a policy cap violation."
metrics:
  duration: 25m
  completed: 2026-05-20
---

# Phase 8 Plan 07: Campaign Orchestration Services (Wave 6) Summary

**Wave 6 — Five `core.cleanup.usecases` services that turn the candidate-query + worker-handler primitives from Waves 2..5 into a complete end-to-end campaign lifecycle.** Preview validates caps; Execute writes the D-04 atomic transaction; StatusQuery feeds frontend polling with M-9 div-by-zero guard; Retry re-queues a single FAILED sender with 409 idempotency; Undo reverses INBOX archive within the 30-day window using H-3 precise SQL filter to leave rule-driven audit rows untouched.

## Objective

Land the 5 missing usecase services so Wave 5 (controllers) can delegate directly into them — covering UNS-03 / UNS-04 / UNS-05 / UNS-06 / UNS-07.

## What Shipped

### Production code (5 new files, all in `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/`)

1. **`CampaignPreviewService`** (UNS-03) — `@Transactional(readOnly = true)`. Validates the caller's sender selection against `UnsubscribeCampaignPolicy.MAX_SENDERS_PER_CAMPAIGN` (25, UNS-03a) and `MAX_HISTORY_MESSAGES_PER_CAMPAIGN` (2000, UNS-03b), each raising `CampaignCapExceededException` with the matching `Kind` discriminator. Per-sender lookup aggregates `mail_message_observed` over the 30-day window (mirrors `CandidateQueryService`) and cross-checks `sender_suppression` on both `sender_email` and `sender_domain`. Nested `PerSenderPreview.riskBadge()` returns the UI-facing badge string (`SAFE` / `NO_HEADER_DISABLED` / `SUPPRESSED_BLOCKED`); `willArchive()` is the single source of truth for executor inclusion.

2. **`CampaignExecuteService`** (UNS-04 / D-04) — `@Transactional`. Re-invokes `CampaignPreviewService.preview()` defense-in-depth, filters to `willArchive()` senders, then performs the D-04 atomic INSERT sequence:
   1. `UnsubscribeCampaignEntity` (archivable count + history count) with caller-generated UUID.
   2. N × `UnsubscribeAttemptEntity` (state = PENDING by entity invariant).
   3. `ProcessingJobEntity` with `jobType='UNSUBSCRIBE_CAMPAIGN'` and the D-19 payload `{"campaignId":"<uuid>","schemaVersion":1}` serialized via `tools.jackson.databind.ObjectMapper`.
   4. `campaign.linkJob(jobId)` back-edge → `save()` so the read side can resolve campaign ↔ job either direction.
   Job inserted LAST so the worker's `SKIP LOCKED` pickup (D-02) can never observe a job before its children. Returns `(campaignId, jobId)`; the jobId is what the frontend polls.

3. **`CampaignStatusQueryService`** (UNS-05) — `@Transactional(readOnly = true)`. `findByJobId(tenantId, jobId)` resolves job → campaign via the `unsubscribe_campaign.job_id` FK lookup (raw JdbcTemplate against `SELECT id FROM unsubscribe_campaign WHERE job_id = ? AND tenant_id = ?`) then loads attempts ordered by `sender_email ASC` (stable list ordering for the UI). Returns `Optional<CampaignStatusProjection>`; the controller maps `empty` to HTTP 404 without leaking whether the job vs the campaign was the missing piece. **M-9 invariant** lives in the static helper `computeProgressPct(List<UnsubscribeAttemptEntity>)`: explicit `if (attempts.isEmpty()) return 0;` literal BEFORE division, long arithmetic `terminalCount * 100L / attempts.size()` prevents overflow.

4. **`CampaignRetryService`** (UNS-06) — `@Transactional`. Validates the job + campaign + attempt all exist for the calling tenant, then enforces the idempotency gate: **only `state == FAILED` is retryable**. `PENDING` / `RUNNING` / `OK` all raise `CampaignRetryConflictException` (HTTP 409 at the controller). On a valid retry: `attempt.resetToPending()` (entity-level state-machine guard) → raw SQL UPDATE on `processing_job` setting `status='QUEUED'`, clearing `started_at` / `finished_at` / `heartbeat_at` / `failure_reason`, setting `next_run_at = NOW()` so the next worker tick picks it back up.

5. **`CampaignUndoService`** (UNS-07) — `@Transactional`. Constructor `(JdbcTemplate, TriageGmailWriter, Clock)` — locked by the Wave 0 test's 3-arg reflective lookup. `undo(UUID tenantId, UUID campaignId)`:
   - Looks up the campaign row with tenant scope (raw JdbcTemplate `queryForMap`); throws `CampaignNotFoundException` on empty result.
   - Validates the campaign was applied (non-null `applied_at`) and not already reverted (`reverted_at IS NULL`).
   - **UNS-07b window check**: `UnsubscribeCampaignPolicy.undoableUntil(appliedAt)` → throw `UndoWindowExpiredException(campaignId, appliedAt, UNDO_WINDOW)` if past 30 days.
   - **H-3 precise undo lookup**: SQL selects `gmail_message_id` from `triage_audit` where `tenant_id = ? AND source = 'CLEANUP_CAMPAIGN' AND external_ref = ? AND reverted_at IS NULL`. The `source = 'CLEANUP_CAMPAIGN'` predicate keeps rule-driven `source='TRIAGE'` rows out of the result set even when they share the same sender — false-positive risk from earlier drafts is eliminated.
   - **H-2 graceful label handling**: `triageGmailWriter.lookupLabelId(tenantId, UNSUBSCRIBED_LABEL_NAME)` returns `Optional<String>`. For each archived message: `restoreToInbox(tenantId, gmailMessageId)` ALWAYS; `removeLabel(tenantId, gmailMessageId, labelId)` only if the label still exists in Gmail (user may have manually deleted it between apply + undo).
   - Atomic mark-reverted: one UPDATE per audit row (`triage_audit.reverted_at`), one UPDATE on the campaign (`unsubscribe_campaign.reverted_at`).

### Test-side fixes (Rule 1 auto-fix)

- **`CampaignUndoServiceTest`** — multiple Wave 0 seed bugs corrected so the test could actually reach its assertions:
  - Wrong schema column names: `total_sender` → `total_sender_count`, `total_history_msg` → `total_history_message_count` (per changelog 044).
  - `triage_audit` seed used non-existent columns `subject_excerpt` + `matcher_evidence`; corrected to `sanitized_subject` (changelog 040) and the `matcher_evidence` column is dropped (never existed).
  - `triage_audit` rows now explicitly set `source = 'CLEANUP_CAMPAIGN'` (default `'TRIAGE'` per changelog 046 would have defeated the H-3 undo query filter).
  - `java.time.Instant` args wrapped in `Timestamp.from(...)` (Postgres JDBC type-inference requirement).
  - `hasRootCauseInstanceOf` → `isInstanceOf` for the expired-window assertion (production throws directly, no wrapping cause).
  - 2 new pin tests: `undoDoesNotTouchTriageSourcedAuditRows` (H-3 false-positive guard) + `undoTolerates_userDeletedLabelInGmail` (H-2 lookupLabelId empty path).
- **`TriageAuditWriterCleanupArchiveTest`** — Wave 5 carry-over with the SAME `subject_excerpt`/`matcher_evidence` seed bug. Same fix applied so `:backend:core:test --tests "*Triage*Test*"` is fully GREEN.

## Deviations from Plan

### Auto-fixed (Rule 1 — Bug)

1. **Wave 0 `CampaignUndoServiceTest` seed used non-existent schema columns and bound `java.time.Instant` directly to JDBC** — fixed in `a09e9976`. Without this fix the test could never reach the production class under test (RED at SQL grammar level, not at class-presence level).
2. **Wave 5 `TriageAuditWriterCleanupArchiveTest` seed had the same column-name bug** — fixed in the same commit `a09e9976` since the diagnosis and remediation were identical. This restored `:backend:core:test --tests "*Triage*Test*"` to fully GREEN.

### Auto-added (Rule 2 — Missing critical functionality)

3. **2 new pin tests in `CampaignUndoServiceTest`** — the plan acceptance criteria called out the H-2 (Optional label-id) and H-3 (source filter false-positive) invariants but the Wave 0 stub did not yet pin either. Added `undoDoesNotTouchTriageSourcedAuditRows` and `undoTolerates_userDeletedLabelInGmail` so any future refactor that drops the `source` filter or the label-Optional handling is caught.

### Spec interpretation calls

4. **`CampaignUndoService.undo(...)` takes `campaignId`, not `jobId`** — the Wave 0 test's reflective method lookup pins this signature. The plan text mentioned "Lookup campaign via job_id + tenant_id" but the test contract is authoritative. The phase 8 controller (Wave 5 follow-up) will resolve the user's polled `jobId` to `campaignId` via the same `unsubscribe_campaign.job_id` FK lookup used elsewhere if needed.
5. **`CampaignUndoService` uses `restoreToInbox` + `lookupLabelId` + `removeLabel(labelId)` rather than the Wave 0 stub's `applyLabel(INBOX)` + `removeLabel(LABEL_NAME)`** — the plan recommended the former design (cleaner, single-purpose primitives shipped in Wave 5). The Wave 0 test mock-verification asserts were updated to match the production design.
6. **Retry semantics restricted to FAILED-only** — the plan suggested treating non-FAILED as a 409 conflict and that's what landed. PENDING / RUNNING attempts also raise `CampaignRetryConflictException` so the user can't race with the worker.

## Verification

Run from the project root:

```bash
./gradlew :backend:core:compileJava                                              # BUILD SUCCESSFUL
./gradlew :backend:core:test --tests "*CampaignUndoServiceTest*"                 # 5 tests GREEN
./gradlew :backend:worker:test --tests "*UnsubscribeCampaignE2ETest*"            # GREEN — was RED, now unlocked
./gradlew :backend:core:test --tests "*Triage*Test*"                             # GREEN — no regression
```

Acceptance greps:

```bash
grep -c "source = 'CLEANUP_CAMPAIGN'" backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignUndoService.java          # 5 (H-3 query filter)
grep -c "if (attempts.isEmpty())"     backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignStatusQueryService.java  # 1 (M-9 guard)
grep -c "100L /"                      backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignStatusQueryService.java  # 1 (long arithmetic)
grep -E "log\..*senderEmail[^.]"      backend/core/src/main/java/com/zeromail/core/cleanup/usecases/CampaignRetryService.java        # 0 matches (privacy)
find  backend/core/src/main -path "*cleanup/application*"                                                                            # 0 (CONVENTIONS §2 usecases/ only)
```

## Commits

| Commit     | Subject                                                                                  |
| ---------- | ---------------------------------------------------------------------------------------- |
| `c8ee73fc` | feat(phase-08-wave-6): CampaignPreviewService + CampaignExecuteService (UNS-03 + UNS-04) |
| `0f90c26f` | feat(phase-08-wave-6): CampaignStatusQueryService + CampaignRetryService (UNS-05 + UNS-06) |
| `a09e9976` | feat(phase-08-wave-6): CampaignUndoService (UNS-07) + repair Wave 0 test seeds           |

## Deferred Issues

See [`deferred-items.md`](deferred-items.md) for the running list. Plan 07 closed deferred items #1 and #5 and adds a clean status section for items it specifically verified are still out-of-scope (Modulith verifier package fix + privacy-sweep log-leak seed bug).

## Self-Check: PASSED

- [x] All 5 production files exist at the declared paths.
- [x] All 3 commits exist on the branch (`c8ee73fc`, `0f90c26f`, `a09e9976`).
- [x] `:backend:core:compileJava` BUILD SUCCESSFUL.
- [x] `:backend:core:test --tests "*CampaignUndoServiceTest*"` BUILD SUCCESSFUL — 5 tests GREEN.
- [x] `:backend:worker:test --tests "*UnsubscribeCampaignE2ETest*"` BUILD SUCCESSFUL.
- [x] `:backend:core:test --tests "*Triage*Test*"` BUILD SUCCESSFUL — no regression.
- [x] Acceptance greps all match (H-3, M-9, long-arithmetic, no-senderEmail-in-log, no-application-package).
