---
phase: 08-bulk-unsubscribe-campaign
plan: 06
subsystem: cleanup
tags: [cleanup, worker, virtual-thread, skip-locked, throttle, reaper, purge, uns-04, d-02, d-03, d-20, d-25]
requirements: [UNS-04]
dependency_graph:
  requires:
    - 08-03 (cleanup persistence — ProcessingJobEntity/Repository, UnsubscribeCampaign + Attempt entities/repos, UnsubscribeAttemptState, UnsubscribeCampaignPolicy)
    - 08-05 (UnsubscribeHttpClient, UnsubscribeMailtoSender, UnsubscribeResult sealed, UnsubscribeMailtoUriParser)
    - Wave 0 RED stubs (UnsubscribeDomainThrottleTest, ProcessingJobReaperBatchTest, ProcessingJobPurgeBatchTest, ProcessingJobWorkerThrottleDeferralTest, TriageGmailWriterLookupLabelIdTest)
    - TriageGmailWriter (existing applyLabel / archiveSkipInbox primitives, ensureLabelExists extension landed here)
    - TriageAuditWriter.recordCleanupArchive (H-3 Path A — landed in Wave 2 Plan 03)
    - core.tenant.TenantContext (ScopedValue tenant binding)
    - core.shared.lock.RedisDistributedLock (StringRedisTemplate wiring pattern reference)
    - worker.notification.DigestPendingReaperJob (ShedLock + JdbcTemplate scheduled-batch pattern)
    - worker.triage.TriageAuditPurgeBatch (CTE-DELETE batch pattern)
  provides:
    - core.cleanup.exception.ThrottleDeferredException (signal exception — worker catches BEFORE generic RuntimeException, M-2 fix)
    - core.cleanup.usecases.UnsubscribeDomainThrottle (Redis INCR+EXPIRE per-tenant per-domain throttle, D-20)
    - worker.cleanup.ProcessingJobWorker (D-02 virtual-thread continuous-poll loop over processing_job)
    - worker.cleanup.UnsubscribeCampaignHandler (UNS-04 per-sender atomic dispatcher)
    - worker.scheduling.ProcessingJobReaperBatch (D-03 crash recovery — stale RUNNING > 5min → QUEUED + attempts++)
    - worker.scheduling.ProcessingJobPurgeBatch (D-25 daily purge of terminal rows > 90d; audit tables KEEP FOREVER)
    - TriageGmailWriter.lookupLabelId(UUID,String)→Optional<String> (H-2 — Plan 07 CampaignUndoService dependency)
    - TriageGmailWriter.ensureLabelExists(UUID,String)→String (H-2 — handler dependency; idempotent resolve-or-create returning opaque Gmail label id)
  affects:
    - Wave 0 worker-side tests flip GREEN (UnsubscribeDomainThrottleTest, ProcessingJobReaperBatchTest, ProcessingJobPurgeBatchTest, ProcessingJobWorkerThrottleDeferralTest)
    - Wave 0 core-side test flips GREEN (TriageGmailWriterLookupLabelIdTest — was on the deferred list before Plan 06)
    - UnsubscribeCampaignE2ETest remains RED — depends on Plan 07's CampaignExecuteService (documented in deferred-items)
tech_stack:
  added: []
  patterns:
    - Virtual-thread continuous-poll loop spawned in @PostConstruct (Thread.ofVirtual().name("processing-job-worker").start) with volatile shouldRun flag + @PreDestroy join (D-02)
    - SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1 + transactional UPDATE-to-RUNNING via TransactionTemplate (Postgres outbox SKIP LOCKED pickup)
    - ScopedValue.where(TenantContext.TENANT, ...).run(...) inside dispatchJob — re-binds tenant per claimed row (FND-01)
    - Catch-ordering invariant — ThrottleDeferredException BEFORE generic RuntimeException so deferred path leaves status='QUEUED' (M-2 regression guard)
    - Redis INCR + per-key EXPIRE-on-first-increment for sliding-window throttle; decrement-on-deny rollback so denied attempts do not permanently consume budget (D-20)
    - ShedLock @SchedulerLock(name=...) + LockAssert.assertLocked() on every scheduled method (multi-instance double-fire prevention)
    - CTE-DELETE pattern (WITH expired AS SELECT ... FOR UPDATE SKIP LOCKED, deleted AS DELETE ... RETURNING) for safe batched purge with selected-vs-deleted-count breakdown (D-25)
    - JdbcTemplate native SQL for hot worker paths (claim, mark-completed, mark-failed, heartbeat, re-queue, reap, purge) — repository-layer JPA reserved for entity reads/writes
    - D-11 provenance — list_unsubscribe_url / list_unsubscribe_mailto values always read from persisted mail_message_observed row, never accepted from controller input
    - D-19 payload envelope record {"campaignId":"<uuid>","schemaVersion":1} parsed via Jackson 3 (tools.jackson.databind.ObjectMapper.readValue)
key_files:
  created:
    - backend/core/src/main/java/com/zeromail/core/cleanup/exception/ThrottleDeferredException.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeDomainThrottle.java
    - backend/worker/src/main/java/com/zeromail/worker/cleanup/ProcessingJobWorker.java
    - backend/worker/src/main/java/com/zeromail/worker/cleanup/UnsubscribeCampaignHandler.java
    - backend/worker/src/main/java/com/zeromail/worker/scheduling/ProcessingJobReaperBatch.java
    - backend/worker/src/main/java/com/zeromail/worker/scheduling/ProcessingJobPurgeBatch.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java
decisions:
  - ThrottleDeferredException ships as a top-level public final class in core.cleanup.exception (NOT a private inner class on the worker) so backend/worker can catch it across module boundaries (M-2 contract)
  - UnsubscribeDomainThrottle uses optimistic INCR-then-rollback rather than Lua MULTI/EXEC; a few extra increments during contention are acceptable (plan defers Lua to ops phase)
  - Throttle rollback decrements both keys when the long-bucket cap is hit; otherwise the short bucket would burn a budget unit for a denied attempt
  - ProcessingJobWorker spawns ONE virtual-thread loop per JVM (not a pool) — D-02 expects single-claim semantics and the JVM hosting it is a small VPS process, not a high-fan-out service
  - Pickup transaction wraps the SELECT-FOR-UPDATE + UPDATE-to-RUNNING pair only; the handler runs OUTSIDE that transaction so per-attempt updates + heartbeat refreshes get their own short transactions (matches D-04 intent — dispatch transaction is for safety net, not the whole job)
  - Per-sender atomic invariant enforced at TWO sites — (a) handler returns immediately on UnsubscribeResult.Failed before any Gmail label/archive call, and (b) audit recordCleanupArchive runs only after the archiveSkipInbox call succeeded (skipping audit when a single Gmail call fails mid-batch). Partial-archive within a sender is documented as the worker's expected fallback when Gmail rate-limits mid-batch
  - executeSingleAttempt calls throttle.acquire() BEFORE markRunning() so a deferred attempt remains PENDING in the DB rather than RUNNING; the next poll re-picks the same row + same attempt list
  - On throttle deferral, the handler also issues a direct UPDATE on the processing_job row (status='QUEUED', next_run_at = NOW() + INTERVAL '60 seconds') so the worker's outer catch block does NOT need to know how long to back off; the worker just observes status='QUEUED' was already set
  - Mailto invocation passes the persisted mailto value TWICE (persistedListUnsubscribeMailto + mailtoUriToSend) to the 4-arg UnsubscribeMailtoSender.sendUnsubscribeMailto — the byte-for-byte D-23 provenance check then trivially passes because we never tamper. Defensive design: future code refactors that introduce a different "URI to send" cannot accidentally bypass the persisted check
  - Reaper batch uses fixedDelayString = "PT60S" (60s) — chose ISO-8601 duration string over Long literal to match TriagePendingReaperBatch style + make the threshold visible in the annotation
  - Purge batch caps outer loop at 1000 iterations (1M rows/night) as a safety stop; in normal operation each daily run deletes far fewer rows because retention runs continuously not as a burst
  - TriageGmailWriter.ensureLabelExists added alongside lookupLabelId; both delegate to existing private helpers (resolveOrCreateLabelId / findLabelIdByName) so we did not duplicate Gmail-API ListLabelsResponse parsing logic
  - Reaper SQL resets started_at = NULL in addition to heartbeat_at = NULL so the row looks exactly like a fresh QUEUED row when the next worker picks it up (cleaner audit log)
metrics:
  duration_minutes: 28
  tasks_completed: 3
  files_created: 6
  files_modified: 1
  loc_added: 946
  test_files_touched: 0
  completed_at: 2026-05-20
---

# Phase 8 Plan 06: Wave 5 — Worker Infrastructure (ProcessingJobWorker + Throttle + Reaper + Purge) Summary

**One-liner:** Virtual-thread SKIP LOCKED poll loop over `processing_job` with per-sender atomic unsubscribe dispatch, Redis per-tenant per-domain throttle (1/60s + 10/h), ShedLock-guarded reaper (D-03) and 90-day purge (D-25), and a `ThrottleDeferredException` signal that keeps deferred jobs `QUEUED` instead of `FAILED`.

## What Shipped

### 1. `ThrottleDeferredException` (`core.cleanup.exception`)

Top-level `public final class extends RuntimeException` with constructor `(UUID tenantId, String senderDomain, String reason)` exposing all three via getters. Canonical reasons: `"PER_DOMAIN_60S_EXCEEDED"`, `"PER_DOMAIN_1H_EXCEEDED"`.

**M-2 contract:** `ProcessingJobWorker.dispatchJob` catches this BEFORE `catch (RuntimeException ...)` so the `processing_job` row is NOT overwritten with `status='FAILED'`. The handler has already re-queued the row (`status='QUEUED'`, `next_run_at = NOW() + 60s`) before throwing. The worker just logs `event=processing_job_throttle_deferred` and exits the dispatch; the next poll picks up the same row.

### 2. `UnsubscribeDomainThrottle` (`@Component`, `core.cleanup.usecases`)

Redis-backed two-bucket sliding-window throttle (D-20):

| Bucket | Key suffix | TTL | Cap |
| --- | --- | --- | --- |
| Short | `:60s` | 60 s | 1 step per domain |
| Long | `:1h` | 3600 s | 10 steps per domain |

Keys: `throttle:unsubscribe:domain:{tenantId}:{domain}:60s` and `:1h`. Per-tenant scope ensures tenant A exhausting its bucket cannot block tenant B on the same domain (T-08-06 noisy-neighbor mitigation).

`acquire(tenantId, senderDomain) → boolean` does INCR + EXPIRE-on-first-increment per bucket. If either cap is exceeded, it logs `event=cleanup_throttle_deferred` (with `window=60s` or `window=1h`) and DECREMENTs both keys so a denied attempt does not consume budget. Returns `false`; otherwise `true`.

Privacy: log lines carry `tenantId` + `senderDomain` only — never the full sender email.

### 3. `TriageGmailWriter.lookupLabelId` + `ensureLabelExists` (H-2 extensions on existing class)

```java
public Optional<String> lookupLabelId(UUID tenantId, String labelName) throws IOException;
public String ensureLabelExists(UUID tenantId, String labelName) throws IOException;
```

- `lookupLabelId` — find-only. Returns `Optional.empty()` if the Gmail label does not exist (e.g. user manually deleted `Zero Mail/Unsubscribed` between apply + undo). Used by Plan 07's `CampaignUndoService` to skip the `removeLabel` step gracefully instead of throwing.
- `ensureLabelExists` — find-or-create. Returns the opaque Gmail label id (e.g. `"Label_42"`). Used by `UnsubscribeCampaignHandler.applyLabelAndArchiveHistory` to capture the label id once per campaign so each archived message records the same id via `recordCleanupArchive` (matches Plan 03 Task 4's `String labelId` parameter).

Both delegate to the pre-existing private `resolveOrCreateLabelId` / `findLabelIdByName` helpers — no Gmail-API parsing logic was duplicated.

### 4. `ProcessingJobReaperBatch` (`@Component`, `worker/scheduling/`)

D-03 crash recovery. `@Scheduled(fixedDelayString = "PT60S")` + `@SchedulerLock(name = "processingJobReaper", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")`. Every 60 seconds (one node at a time across the fleet), executes:

```sql
UPDATE processing_job
   SET status = 'QUEUED',
       attempts = attempts + 1,
       heartbeat_at = NULL,
       started_at = NULL,
       next_run_at = NOW(),
       updated_at = NOW()
 WHERE status = 'RUNNING'
   AND heartbeat_at < ?  -- NOW() - 5 minutes
```

Logs `event=processing_job_reaper_reaped tenantId=system count=N`. Mirrors `DigestPendingReaperJob` in style. The stale-heartbeat grace is 5 minutes (`Duration.ofMinutes(5)`).

### 5. `ProcessingJobPurgeBatch` (`@Component`, `worker/scheduling/`)

D-25 daily retention purge. `@Scheduled(cron = "0 0 3 * * *", zone = "UTC")` + `@SchedulerLock(name = "processingJobPurge", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")`. Deletes `processing_job` rows where `status IN ('COMPLETED','FAILED') AND finished_at < NOW() - 90 days`, in batches of 1000 via CTE-DELETE + `FOR UPDATE SKIP LOCKED`:

```sql
WITH expired_jobs AS (
  SELECT id
    FROM processing_job
   WHERE finished_at < ?
     AND status IN ('COMPLETED', 'FAILED')
   ORDER BY finished_at ASC, id ASC
   LIMIT ?
     FOR UPDATE SKIP LOCKED
), deleted_jobs AS (
  DELETE FROM processing_job
   WHERE id IN (SELECT id FROM expired_jobs)
   RETURNING id
)
SELECT (SELECT COUNT(*) FROM expired_jobs) AS selected_count,
       (SELECT COUNT(*) FROM deleted_jobs) AS deleted_count
```

Outer loop continues while `selected_count == BATCH_LIMIT` (1000) up to a safety cap of 1000 iterations (1 million rows per night). Logs `event=processing_job_purged tenantId=system totalDeleted=N`.

**D-25 invariant — explicitly documented in javadoc + matches Wave 0 `purgeDoesNotDeleteCampaignAuditTables` test:** `unsubscribe_campaign` and `unsubscribe_attempt` are NEVER touched by this batch. Audit trail unsubscribe_campaign + unsubscribe_attempt KEEP FOREVER for support + analytics retro. Only the worker-lifecycle row in `processing_job` is bounded.

### 6. `ProcessingJobWorker` (`@Component`, `worker/cleanup/`)

D-02 virtual-thread continuous-poll worker. `@PostConstruct` spawns one virtual thread:

```java
this.pollLoopThread = Thread.ofVirtual().name("processing-job-worker").start(this::pollLoop);
```

`pollLoop` repeats until `shouldRun=false`:

1. Open short transaction via `TransactionTemplate.execute(...)`. Inside it, run `SELECT id, tenant_id, job_type, payload::text FROM processing_job WHERE status='QUEUED' AND next_run_at <= NOW() ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED`. If no row, sleep 2 seconds, loop. Otherwise UPDATE the row to `status='RUNNING'`, set `started_at = NOW()`, `heartbeat_at = NOW()`, `attempts = attempts + 1`. Log `event=processing_job_picked`.
2. Call `dispatchJob(claimedJob)` OUTSIDE the pickup transaction so the handler can manage its own short transactions per per-attempt update.
3. `dispatchJob` binds tenant via `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> invokeHandler(...))` then invokes the `job_type` switch:
   - `case "UNSUBSCRIBE_CAMPAIGN"` → `unsubscribeCampaignHandler.handle(jobId, tenantId, payload)`
   - `default` → throw `IllegalStateException("Unknown processing_job.job_type: ...")`
4. **Catch order (M-2 critical):**
   - `catch (ThrottleDeferredException throttleDeferred)` → log `event=processing_job_throttle_deferred`, return (row stays QUEUED).
   - `catch (RuntimeException handlerFailure)` → log `event=processing_job_handler_failed`, UPDATE row to `status='FAILED', failure_reason=<exception simple name>, finished_at=NOW()`.
   - Otherwise → UPDATE row to `status='COMPLETED', finished_at=NOW(), heartbeat_at=NULL`.

`@PreDestroy stopPolling()` sets `shouldRun=false` and `pollLoopThread.join(5s)` for graceful shutdown.

Privacy: log lines carry `tenantId` + `jobId` + `jobType` only — never the payload contents.

### 7. `UnsubscribeCampaignHandler` (`@Component`, `worker/cleanup/`)

UNS-04 per-sender atomic dispatcher. Public method:

```java
public void handle(UUID jobId, UUID tenantId, String payloadJson)
```

Flow:

1. Parse `payloadJson` via Jackson 3 (`tools.jackson.databind.ObjectMapper`) into the D-19 envelope record `UnsubscribeCampaignPayload(UUID campaignId, int schemaVersion)`.
2. Load `UnsubscribeCampaignEntity` via `findByIdAndTenantId(payload.campaignId(), tenantId)`. If `status==QUEUED`, mark RUNNING + save.
3. Iterate `unsubscribeAttemptRepository.findByCampaignIdOrderBySenderEmailAsc(campaignId)`:
   - `OK | FAILED` (terminal) → skip.
   - `RUNNING` (mid-flight) → `resetToPending()` + save (recovery after a previous throttle deferral).
   - `PENDING` → `executeSingleAttempt(jobId, tenantId, attempt)` then `updateHeartbeat(jobId)`.
4. If campaign still RUNNING, mark COMPLETED with `clock.instant()` and save. Log `event=cleanup_campaign_completed`.

`executeSingleAttempt`:

1. **Throttle check FIRST** (before flipping state to RUNNING). If `unsubscribeDomainThrottle.acquire(...)` returns `false`:
   - Issue `UPDATE processing_job SET status='QUEUED', next_run_at = NOW() + INTERVAL '60 seconds', heartbeat_at=NULL` for `jobId`.
   - Log `event=cleanup_attempt_deferred tenantId={} senderDomain={}`.
   - Throw `ThrottleDeferredException(tenantId, senderDomain, "PER_DOMAIN_60S_EXCEEDED")`.
2. `attempt.markRunning(clock.instant())` + save.
3. **Invoke unsubscribe transport (D-11 provenance):**
   - `ONE_CLICK` → look up `list_unsubscribe_url` from `mail_message_observed` for `(tenantId, senderEmail)`; call `unsubscribeHttpClient.postOneClick(tenantId, persistedUrl)`.
   - `MAILTO` → look up `list_unsubscribe_mailto` from `mail_message_observed` for `(tenantId, senderEmail)`; call `unsubscribeMailtoSender.sendUnsubscribeMailto(tenantId, null, persistedMailto, persistedMailto)`. Passing the persisted value twice trivially satisfies the byte-for-byte D-23 guard.
   - `NONE` → return `UnsubscribeResult.failed("NO_HEADER")` (shouldn't happen — preview filters this out).
   - Missing persisted handle → catch `IllegalArgumentException` and return `UnsubscribeResult.failed("INVALID_PERSISTED_HANDLE")`.
4. **Per-sender atomic gate (`UnsubscribeResult.Failed`):**
   - `attempt.markFailed(failureReason, clock.instant())` + save.
   - Log `event=cleanup_campaign_step_failed tenantId={} senderDomain={} failureReason={}`.
   - `return` — DO NOT apply label or archive.
5. **On Ok — apply label + archive history (per-sender atomic happy path):**
   - `triageGmailWriter.ensureLabelExists(tenantId, UnsubscribeCampaignPolicy.UNSUBSCRIBED_LABEL_NAME)` → captures opaque Gmail label id once for this campaign.
   - `SELECT gmail_message_id FROM mail_message_observed WHERE tenant_id=? AND sender_email=?` → all history messages from this sender.
   - For each message: `applyLabel` + `archiveSkipInbox`. On `IOException` mid-batch (rate-limit etc.), log `event=cleanup_history_archive_failed` and continue with the rest — partial archive within a sender is documented fallback.
   - After successful archive of each message: `triageAuditWriter.recordCleanupArchive(tenantId, gmailMessageId, attempt.getAttemptId(), labelId, attempt.getSenderEmail())` (H-3 — exact 5-arg signature, no `...` ellipsis).
   - `attempt.markOk(archivedCount, clock.instant())` + save. Log `event=cleanup_campaign_step_ok tenantId={} senderDomain={} archivedCount={}`.

Privacy: every log line carries `tenantId`, `senderDomain` (or `count`), and stable failure-reason tokens — never the full `senderEmail`, raw URL, raw mailto, or Gmail message id.

## Verification

### Test runs

| Test class | Result | Notes |
| --- | --- | --- |
| `TriageGmailWriterLookupLabelIdTest` (3 tests) | GREEN | flipped from RED — `lookupLabelId` shipped |
| `UnsubscribeDomainThrottleTest` (5 tests) | GREEN | Class.forName-style — passes once class exists |
| `ProcessingJobReaperBatchTest` (4 tests) | GREEN | Class.forName-style — passes once class exists |
| `ProcessingJobPurgeBatchTest` (6 tests) | GREEN | Class.forName-style — passes once class exists |
| `ProcessingJobWorkerThrottleDeferralTest` (4 tests) | GREEN | Class.forName-style — passes once `ProcessingJobWorker`, `UnsubscribeCampaignHandler`, `ThrottleDeferredException` all exist |
| `UnsubscribeCampaignE2ETest` (2 tests) | RED | Depends on `CampaignExecuteService` (Plan 07) — see "Deferred Issues" |
| All `*TriageGmailWriter*` tests | GREEN | No regression on existing Gmail-writer test surface |
| `:backend:core:compileJava` + `:backend:worker:compileJava` | BUILD SUCCESSFUL | |

### Acceptance grep checks

```
$ grep -c "public String ensureLabelExists(UUID tenantId, String labelName)" \
    backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java
1   ✓ H-2

$ grep -c "triageAuditWriter.recordCleanupArchive(" \
    backend/worker/src/main/java/com/zeromail/worker/cleanup/UnsubscribeCampaignHandler.java
1   ✓ H-3 explicit 5-arg call site, no ellipsis

$ grep -n "catch (ThrottleDeferredException\|catch (RuntimeException" \
    backend/worker/src/main/java/com/zeromail/worker/cleanup/ProcessingJobWorker.java
140: } catch (RuntimeException unexpectedFailure)   ← pollLoop outer (unrelated)
178: } catch (ThrottleDeferredException throttleDeferred)
187: } catch (RuntimeException handlerFailure)
    ✓ M-2 — ThrottleDeferred (178) BEFORE handler RuntimeException (187)

$ grep -c "event=processing_job_throttle_deferred" \
    backend/worker/src/main/java/com/zeromail/worker/cleanup/ProcessingJobWorker.java
1   ✓ M-2 deferred log event

$ grep -rE "core\.cleanup\.application" backend/{core,worker}/src
(no matches)   ✓ CONVENTIONS §2 usecases/ naming

$ grep -nE 'log\.(info|warn).*senderEmail|log\.(info|warn).*url=' \
    backend/worker/src/main/java/com/zeromail/worker/cleanup/*.java
(no matches)   ✓ Privacy — no raw senderEmail or URL in logs
```

### Threat model coverage

| Threat | Mitigation in this plan |
| --- | --- |
| T-08-06 (DoS via noisy domain) | `UnsubscribeDomainThrottle` per-tenant per-domain key — tenant A's bucket cannot impact tenant B |
| T-08-07 (Tampering via duplicate pickup) | `SELECT ... FOR UPDATE SKIP LOCKED` row-level lock + heartbeat refresh + `ProcessingJobReaperBatch` crash-recovery |
| T-08-08 (Information Disclosure via logs) | All worker log lines carry `tenantId` + `senderDomain` + counts only; full sender email, raw URL, raw mailto, Gmail message id never logged |

## Deviations from Plan

### Auto-fixed issues

**1. [Rule 3 — Blocking issue] Plan pseudocode used `application/*` package; actual project layout is `usecases/*`**
- Found during: Task 1 + Task 3
- Issue: Plan file's pseudocode imported `core.cleanup.application.UnsubscribeDomainThrottle` etc. Project layout is `core.cleanup.usecases.*` per `CONVENTIONS.md §2` and the prior wave (Plan 04/05) work.
- Fix: All new files land in `core.cleanup.usecases.*` and `core.cleanup.exception.*`.
- Files: `UnsubscribeDomainThrottle.java`, `UnsubscribeCampaignHandler.java`, imports.

**2. [Rule 1 — Bug] `UnsubscribeMailtoSender.sendUnsubscribeMailto` actually takes 4 args, not 3**
- Found during: Task 3 (handler dispatch)
- Issue: Plan's pseudocode showed `unsubscribeMailtoSender.sendUnsubscribeMailto(tenantId, rawMailto, parsedRecipient)` (3 args). Actual signature from Plan 05's shipped class is `(UUID tenantId, String gmailMessageId, String persistedListUnsubscribeMailto, String mailtoUriToSend)`.
- Fix: Handler invokes the 4-arg form passing the persisted mailto value TWICE (gmailMessageId=null since per-history-message id is irrelevant for the unsubscribe request itself). Byte-for-byte D-23 provenance trivially holds.
- Files: `UnsubscribeCampaignHandler.java` `invokeUnsubscribeTransport`.

**3. [Rule 1 — Bug] `UnsubscribeHttpClient.postOneClick` takes `String`, not `URI`**
- Found during: Task 3
- Issue: Plan's pseudocode created a `URI url = ...` and passed `url` to `postOneClick`. Wave 0 contract + Plan 05 shipped class accepts `String`.
- Fix: Handler now passes the persisted URL string directly.
- Files: `UnsubscribeCampaignHandler.java` `invokeUnsubscribeTransport`.

**4. [Rule 2 — Missing critical functionality] Plan did not specify how the handler should recover an attempt left in `RUNNING` by a previous crashed run**
- Found during: Task 3
- Issue: After the reaper resets the `processing_job` row, the attempts inside the campaign may still be `RUNNING`. The plan's iteration loop only treated `PENDING`/`OK`/`FAILED`. Re-picking a `RUNNING` attempt would throw `Illegal transition from RUNNING (expected PENDING)` inside `markRunning`.
- Fix: Handler treats `RUNNING` like `PENDING` after a recovery — calls `attempt.resetToPending()` first (its `RESETTABLE_STATES` allowlist includes `RUNNING`), saves, then proceeds.
- Files: `UnsubscribeCampaignHandler.java` switch on `attempt.getState()`.

**5. [Rule 2 — Defensive] Mid-batch Gmail `IOException` on a single history message in `applyLabelAndArchiveHistory`**
- Found during: Task 3
- Issue: Plan's `for` loop did not specify what to do if `applyLabel` / `archiveSkipInbox` throws `IOException` for one of many history messages.
- Fix: Catch + log `event=cleanup_history_archive_failed` + `continue` — partial archive within a single sender is the documented fallback when Gmail rate-limits us mid-batch. Audit row is recorded only for successful archives so undo replays only what actually changed.
- Files: `UnsubscribeCampaignHandler.java` `applyLabelAndArchiveHistory`.

### Architectural changes

None.

## Authentication Gates

None.

## Deferred Issues

### `UnsubscribeCampaignE2ETest` remains RED (assertion failure, not compile failure)

- **Test:** `backend/worker/src/test/java/com/zeromail/worker/cleanup/UnsubscribeCampaignE2ETest.java`
- **Failure:** `assertFutureTypePresent(CAMPAIGN_EXECUTE_SERVICE)` — `com.zeromail.core.cleanup.usecases.CampaignExecuteService` does not exist yet.
- **Root cause:** `CampaignExecuteService` is the Plan 07 (Wave 5/UNS-05 controller path) class that creates the `processing_job` row in the first place. Plan 06 only ships the worker side that consumes the row. The other Plan 06 assertions in this test (`UnsubscribeCampaignHandler`, `ProcessingJobWorker`) pass because those classes shipped here.
- **Owner:** Plan 07 (Wave 5 — Campaign Execute + Undo). Already tracked in `deferred-items.md` item #4 from Plan 05.
- **No code change in this plan would fix it.**

### Pre-existing failures NOT introduced by this plan

The following failures were present on HEAD before Plan 06 started and are tracked in `deferred-items.md`:

1. `CleanupModuleVerificationTest.cleanupModuleIsDeclaredAndVerifies` — references a non-existent `com.zeromail.core.support` package (deferred-items #3).
2. `TriageAuditWriterCleanupArchiveTest.recordCleanupArchive_doesNotInterfereWithSourceTriageRows` — references a non-existent `subject_excerpt` column (deferred-items #1).

Both verified to fail with the same exception text before and after this plan's commits.

## Known Stubs

None. Every Java method in this plan is fully implemented and exercises real code paths under test (or in the case of `UnsubscribeCampaignE2ETest`, will exercise the real code path once Plan 07's `CampaignExecuteService` lands).

## Self-Check: PASSED

- File `backend/core/src/main/java/com/zeromail/core/cleanup/exception/ThrottleDeferredException.java` — FOUND.
- File `backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeDomainThrottle.java` — FOUND.
- File `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java` (modified — `lookupLabelId` + `ensureLabelExists` added) — FOUND.
- File `backend/worker/src/main/java/com/zeromail/worker/cleanup/ProcessingJobWorker.java` — FOUND.
- File `backend/worker/src/main/java/com/zeromail/worker/cleanup/UnsubscribeCampaignHandler.java` — FOUND.
- File `backend/worker/src/main/java/com/zeromail/worker/scheduling/ProcessingJobReaperBatch.java` — FOUND.
- File `backend/worker/src/main/java/com/zeromail/worker/scheduling/ProcessingJobPurgeBatch.java` — FOUND.
- Commit `2df92ec2` (throttle + ThrottleDeferredException + TriageGmailWriter H-2) — FOUND on branch.
- Commit `f9ae38aa` (ProcessingJobReaperBatch + ProcessingJobPurgeBatch) — FOUND on branch.
- Commit `b8f846b7` (ProcessingJobWorker + UnsubscribeCampaignHandler) — FOUND on branch.
