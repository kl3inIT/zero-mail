---
phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification
plan: 03
subsystem: gmail-ingestion
tags: [gmail, pubsub, mailbox-scope, inbox-projection, processing-job, spring-data-jpa]

requires:
  - phase: 10-gmail-mailbox-foundation-and-account-management
    provides: GmailConnection mailbox identity, primary mailbox ordering, mailbox-aware Gmail client factory
  - phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification/11-02
    provides: mailbox-scoped ingestion/projection/sync-state/job schema and transitional mailbox event constructors
provides:
  - Pub/Sub email to concrete tenant/mailbox resolution with safe unknown-mailbox drop
  - Mailbox-scoped Gmail delivery processing, history cursor updates, observed rows, projection upserts, and domain events
  - Mailbox-scoped watch health/history-lost handling plus per-mailbox inbox backfill job idempotency
  - Removed transitional null-mailbox Gmail event constructors after migrating callers
affects: [phase-11, gmail-ingestion, inbox-projection, inbox-backfill, worker-watch-renewal, rules-runtime, triage]

tech-stack:
  added: []
  patterns:
    - MailboxRef is the runtime boundary for Gmail client lookup and mailbox-scoped ingestion health
    - processing_job mailbox dedup uses tenant_id + gmail_connection_id + job_type + open statuses
    - Gmail event constructors require gmailConnectionId after Plan 03

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/lowlevel/TenantMailboxRef.java
    - backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxSyncStateId.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/PubSubIngestionService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java
    - backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxBackfillEnqueuer.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/InboxBackfillService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/BackfillNeedsReplyService.java
    - backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java
    - backend/worker/src/main/java/com/zeromail/worker/inbox/InboxBackfillJobHandler.java

key-decisions:
  - "GmailDeliveryProcessingService now resolves the Gmail client from the delivery mailbox via buildClientForMailbox; its primary-shim allow-list entry intentionally remains for Plan 05 to drain."
  - "Unknown/no-CONNECTED Pub/Sub mailbox deliveries are dropped and acked; lookup exceptions are allowed to propagate so Pub/Sub can redeliver."
  - "Inbox backfill jobs are deduped per mailbox and carry gmailConnectionId in processing_job.gmail_connection_id and payload JSON."
  - "RecentInboxReadService keeps a narrow transitional primary-mailbox lookup only for the lazy backfill enqueue; broader MailboxContext migration remains Plan 05."

patterns-established:
  - "Use MailboxRef(tenantId, gmailConnectionId) for mailbox-scoped Gmail connection mutation APIs."
  - "Mailbox-aware logs retain event=... tenantId={} and add gmailConnectionId={} without email, subject, sender, body, token, prompt, or completion content."
  - "GmailInboxSyncState identity is the composite (tenantId, gmailConnectionId)."

requirements-completed: [ING-01, ING-02, ING-03, ING-05, ING-06, AUD-07]

duration: multi-session
completed: 2026-06-09
---

# Phase 11 Plan 03 Summary

**Mailbox-scoped Pub/Sub ingestion, Gmail delivery processing, watch health, and inbox backfill idempotency.**

## Performance

- **Duration:** multi-session resume
- **Started:** 2026-06-09T21:56:38+07:00
- **Completed:** 2026-06-09T23:42:42+07:00
- **Tasks:** 3
- **Files modified:** 44

## Accomplishments

- Added `TenantMailboxRef` and routed Pub/Sub email resolution to a connected `(tenantId, gmailConnectionId)` pair; unknown/no-CONNECTED mailbox deliveries now drop safely without falling back to a tenant primary.
- Reworked `GmailDeliveryProcessingService` to build the Gmail client via `buildClientForMailbox(MailboxRef)`, update history cursors per connection, and publish mailbox-carrying observed/outbound events.
- Added mailbox-scoped Gmail connection health APIs: `markHistoryLost(MailboxRef, Long)`, `markWatchUnhealthy(MailboxRef)`, `recordWatchSuccess(MailboxRef, Long, Instant)`, `incrementWatchFailure(MailboxRef)`, and `clearForReconnect(MailboxRef)`.
- Converted inbox sync state to composite identity `(tenantId, gmailConnectionId)` and moved backfill success/failure/status updates onto that key.
- Changed `InboxBackfillEnqueuer.enqueueIfNotPending(MailboxRef)` so open-job dedup includes `tenant_id`, `gmail_connection_id`, `job_type`, and open statuses, while the inserted payload is `{"gmailConnectionId":"<uuid>"}`.
- Migrated `OAuthProvisioningService` reconnect/first-login, `RecentInboxReadService` lazy read-triggered backfill, `InboxBackfillJobHandler`, `GmailWatchScheduler`, and `BackfillNeedsReplyService` to mailbox-aware APIs.
- Removed the Plan 02 transitional old-arity constructors from `MailMessageObserved` and `MailOutboundObserved` after migrating all callers in this plan.

## Task Commits

1. **Task 1: Pub/Sub lookup returns tenant and mailbox** - `2b0461a1` (`feat`)
2. **Task 2: Delivery processing uses mailbox-scoped client/cursor/projection/events** - `f4612c5a` (`feat`)
3. **Task 3: Watch health and backfill idempotency scoped by mailbox** - `260d5b61` (`feat`)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/lowlevel/TenantMailboxRef.java` - Pub/Sub lookup result record carrying tenant and Gmail connection ids.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/lowlevel/PubSubTenantLookupRepository.java` - adds connected mailbox lookup with invariant failure on multiple rows.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/PubSubIngestionService.java` - stores `pubsub_delivery.gmail_connection_id` and safely drops unresolved deliveries.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java` - routes delivery processing through `MailboxRef`, mailbox cursor updates, mailbox observed/projection writes, and mailbox events.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java` - exposes mailbox-scoped watch/history/health/reconnect APIs plus `primaryMailboxRef` and mailbox disconnect support.
- `backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxBackfillEnqueuer.java` - enqueues and dedups inbox projection backfills per mailbox with mailbox payload JSON.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/InboxBackfillService.java` - backfills a concrete mailbox and records sync state by composite mailbox key.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java` - uses mailbox sync-state lookup and mailbox-aware lazy enqueue; broader `MailboxContext` migration remains Plan 05.
- `backend/core/src/main/java/com/zeromail/core/triage/usecases/BackfillNeedsReplyService.java` - replays needs-reply backfill events with the primary mailbox id.
- `backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java` - requires `gmailConnectionId`; old null-mailbox overload removed.
- `backend/core/src/main/java/com/zeromail/core/gmail/event/MailOutboundObserved.java` - requires `gmailConnectionId`; old null-mailbox overload removed.
- `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java` - renews watches and records watch failures/health against each connection mailbox.
- `backend/worker/src/main/java/com/zeromail/worker/inbox/InboxBackfillJobHandler.java` - parses mailbox payload and calls `backfillMailbox(MailboxRef)`.

## Verification

- `./gradlew.bat :backend:core:compileJava :backend:core:compileTestJava :backend:worker:compileJava :backend:api:compileJava` - passed.
- `./gradlew.bat :backend:core:test --tests "*InboxBackfillEnqueuerTest" --tests "*GmailInboxSyncStateRepositoryTest" --tests "*RecentInboxReadServiceOrchestratorTest" --tests "*BackfillNeedsReplyServiceTest"` - passed.
- `./gradlew.bat :backend:core:test --tests "*PubSubMailboxLookup*" --tests "*ObservedMailboxPk*" --tests "*ProjectionAadContinuity*"` - passed.
- `./gradlew.bat :backend:worker:compileJava` - passed after the final handler warning cleanup.
- `git diff --cached --check` - passed before Task 3 commit.
- Grep confirmed no `findByTenantId` or `buildClientForConnection` remains in `GmailDeliveryProcessingService`.
- Grep confirmed no UUID/tenant-only `enqueueIfNotPending` call path survives.
- Grep confirmed no old-arity `MailMessageObserved` or `MailOutboundObserved` constructor definitions remain.
- Grep confirmed no tenant-only watch/history/health/reconnect methods remain in `GmailConnectionService`.
- JetBrains inspections reported no errors on the main Task 3 files; remaining warnings are existing transaction/unused-service and SQL datasource assistance warnings.

## Decisions Made

- `GmailDeliveryProcessingService` no longer calls the primary Gmail client shim. Its `GmailClientLookupBoundaryTest` allow-list entry is intentionally left untouched for Plan 05 so Wave 3 file ownership stays disjoint.
- `GmailConnectionService.upsert(...)` now returns the saved `gmailConnectionId`; provisioning uses that id to enqueue the just-connected mailbox instead of resolving a tenant-only connection later.
- `InboxBackfillJobHandler` treats missing or malformed `gmailConnectionId` payloads as invalid worker jobs, not as tenant-primary fallback opportunities.
- `BackfillNeedsReplyService` resolves the primary mailbox through `GmailConnectionService.primaryMailboxRef(...)`, keeping Gmail repository lookup boundaries intact.

## Deviations from Plan

None - plan intent executed as written. During closeout, unused tenant-only watch/history/health/reconnect methods were removed because no documented single-mailbox caller remained.

## Issues Encountered

- The initial Task 3 implementation left unused tenant-only `GmailConnectionService` watch/health variants. They were removed before the Task 3 commit and the compile/test checks were rerun.
- `apply_patch` could not accept multiline PowerShell payloads in this environment, so targeted JetBrains MCP replacements were used for the final Java cleanup.
- Two pre-existing formatting-only working-tree edits remain intentionally unstaged: `GmailApiClientFactory.java` and `MailboxSummaryProjection.java`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 11-04 can now make rules and outbound execution consume mailbox-scoped runtime state. Ingestion and worker backfill/watch paths carry the concrete Gmail connection id; the remaining `GmailDeliveryProcessingService` primary-shim allow-list entry and the broader `RecentInboxReadService` `MailboxContext` migration are explicitly left for Plan 05.

## Self-Check

PASSED - mailbox resolution, mailbox client lookup, per-connection sync state/backfill, event seam removal, privacy-log shape, and focused compile/test verification all satisfy Plan 11-03 acceptance criteria.

---
*Phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification*
*Completed: 2026-06-09*
