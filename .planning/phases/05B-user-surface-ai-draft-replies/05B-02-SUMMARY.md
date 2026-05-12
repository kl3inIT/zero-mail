---
phase: 05B-user-surface-ai-draft-replies
plan: 02
subsystem: backend
tags: [gmail-api, spring-modulith, postgres, jpa, thread-reply-status]

requires:
  - phase: 05B-00
    provides: thread_reply_status Liquibase schema, FK cascade, and RED classifier contracts
provides:
  - core.thread Modulith module with metadata-only reply-status domain and persistence
  - ThreadReplyBucket IdentifiedEnum with internal ids and public slugs
  - ThreadReplyStatusEntity, repository, and bucket attribute converter over thread_reply_status
  - Heuristic ClassifyThreadReplyStatusService with tenant-bound transaction handling
  - ThreadDraftSaved and MailOutboundObserved payload-free events
  - GmailDeliveryProcessingService SENT-label observation path for outbound classification
affects: [thread, gmail, triage, draft, needs-reply]

tech-stack:
  added: []
  patterns:
    - TenantContext binding before TransactionTemplate opens for tenant-owned JPA writes
    - Metadata-only reply-status classification inputs using booleans and opaque Gmail ids
    - SENT observation comes from Gmail history/watch deltas, not mailbox search

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyBucket.java
    - backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyStatus.java
    - backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusEntity.java
    - backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusRepository.java
    - backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java
    - backend/core/src/main/java/com/zeromail/core/thread/usecases/ThreadReplyClassificationInput.java
    - backend/core/src/main/java/com/zeromail/core/thread/event/ThreadDraftSaved.java
    - backend/core/src/main/java/com/zeromail/core/gmail/event/MailOutboundObserved.java
    - backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceIntegrationTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
    - backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceTest.java

key-decisions:
  - "Classifier transactions are opened inside a TenantContext ScopedValue binding; @Transactional on classify was avoided because Hibernate captures the tenant at session open."
  - "Gmail history processing now accepts INBOX or SENT labels from the watched history stream and still performs no messages.list or in:sent search."
  - "Plan 02 reactions use conservative metadata already carried by ThreadDraftSaved and MailOutboundObserved; richer per-thread Gmail metadata can be added later without changing the public classify(...) contract."

patterns-established:
  - "For tenant-owned JPA writes from event handlers, bind TenantContext first, then enter TransactionTemplate."
  - "Reply-status persistence remains metadata-only: bucket, draft flags, ids, timestamps, and resolved state only."
  - "Gmail SENT classification is event-driven from observed labels, not compensating mailbox enumeration."

requirements-completed: [DRFT-04]

duration: 25min
completed: 2026-05-13
---

# Phase 05B Plan 02: Thread Reply Status Classifier Summary

**Metadata-only thread reply-status persistence and heuristic TO_REPLY/AWAITING classification driven by observed Gmail events**

## Performance

- **Duration:** 25 min
- **Started:** 2026-05-12T21:31:00Z
- **Completed:** 2026-05-12T21:53:00Z
- **Tasks:** 2
- **Files modified:** 20

## Accomplishments

- Added the `core.thread` Modulith module with `ThreadReplyBucket`, `ThreadReplyStatus`, JPA entity, repository, converter, and package boundaries.
- Added payload-free `ThreadDraftSaved` and `MailOutboundObserved` records.
- Implemented `ClassifyThreadReplyStatusService.classify(...)` with the v1 heuristic: tenant-last + SENT + non-auto-reply becomes `AWAITING_THEIR_REPLY`; everything else stays `TO_REPLY`.
- Made classification idempotent for unchanged `(tenantId, gmailThreadId, lastClassifiedMessageId)` and reopen resolved rows on changed activity.
- Updated Gmail delivery processing so SENT-only history observations can publish `MailOutboundObserved` without mailbox enumeration.
- Added unit and integration coverage for bucket ids/slugs, classifier cases, tenant-bound JPA writes, Gmail SENT publish, and FK cascade cleanup.

## Task Commits

1. **Task 1 and Task 2: thread reply-status domain, persistence, classifier, reactions, SENT observation, and tests** - `d402985`

**Plan metadata:** this summary commit

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyBucket.java` - Internal enum ids plus public slug round-trips.
- `backend/core/src/main/java/com/zeromail/core/thread/domain/ThreadReplyStatus.java` - Metadata-only domain result record.
- `backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusEntity.java` - Tenant-owned JPA mapping for `thread_reply_status`.
- `backend/core/src/main/java/com/zeromail/core/thread/persistence/ThreadReplyStatusRepository.java` - Tenant-filtered lookup and future badge count query.
- `backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java` - Heuristic classifier plus Modulith event reactions.
- `backend/core/src/main/java/com/zeromail/core/thread/usecases/ThreadReplyClassificationInput.java` - Metadata-only classifier input contract.
- `backend/core/src/main/java/com/zeromail/core/thread/event/ThreadDraftSaved.java` - Draft-saved event for Plan 03 to publish.
- `backend/core/src/main/java/com/zeromail/core/gmail/event/MailOutboundObserved.java` - SENT-labelled outbound observation event.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java` - Publishes outbound events from observed SENT labels.
- `backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceTest.java` - Converts the RED classifier scaffold to executable assertions.
- `backend/core/src/test/java/com/zeromail/core/thread/ClassifyThreadReplyStatusServiceIntegrationTest.java` - Proves TenantContext is bound before JPA transaction/session use.
- `backend/core/src/test/java/com/zeromail/core/thread/ThreadReplyStatusAccountDeletionCascadeTest.java` - Proves tenant FK cascade removes reply-status rows.
- `backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingServiceTest.java` - Proves SENT-only observations publish `MailOutboundObserved`.

## Decisions Made

- Used `TransactionTemplate` inside `ScopedValue.where(TenantContext.TENANT, ...)` for the classifier. A real Postgres test showed `@Transactional` opened Hibernate with the default tenant before the method body bound the tenant.
- Removed the Gmail history `setLabelId("INBOX")` request filter and replaced the local skip with `INBOX || SENT`, so outbound-only messages can be observed without `messages.list` or `in:sent`.
- Kept the event reactions conservative and metadata-only. `MailOutboundObserved` already proves the SENT label; `ThreadDraftSaved` records draft state and leaves richer thread metadata to Plan 03/07 follow-up coverage.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Tenant-bound JPA write failed with proxy-level @Transactional**
- **Found during:** Task 2 verification
- **Issue:** `@Transactional` opened the Hibernate session before `TenantContext` was bound, causing a current-tenant mismatch on insert.
- **Fix:** Moved the transaction boundary into a `TransactionTemplate` executed inside the tenant ScopedValue.
- **Files modified:** `ClassifyThreadReplyStatusService.java`, `ClassifyThreadReplyStatusServiceIntegrationTest.java`
- **Verification:** `./gradlew.bat :backend:core:test --tests "*ClassifyThreadReplyStatus*"`
- **Committed in:** `d402985`

**2. [Rule 3 - Blocking] SENT-only observations were unreachable under the existing INBOX history filter**
- **Found during:** Task 2 verification
- **Issue:** `GmailDeliveryProcessingService` used `setLabelId("INBOX")` and skipped non-INBOX messages, so a SENT-only observed message could not publish `MailOutboundObserved`.
- **Fix:** Removed the request label filter and accepted already-observed messages carrying either INBOX or SENT; added a unit test proving SENT-only outbound publish.
- **Files modified:** `GmailDeliveryProcessingService.java`, `GmailDeliveryProcessingServiceTest.java`
- **Verification:** focused Gmail delivery test plus grep gate for no mailbox enumeration
- **Committed in:** `d402985`

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes were required for correctness and stayed within the planned metadata-only, mailbox-scan-free classifier scope.

## Issues Encountered

- Full `./gradlew.bat :backend:core:test :backend:api:test` remains expected to fail until later 05B plans turn the remaining Wave 0 RED contracts green. Current broad failure stops in `:backend:core:test` on Plan 03 draft/tone RED contracts (`DraftPathArchUnitTest`, `GenerateThreadDraftServiceTest`, `ToneContextBuilderTest`, `DraftPrivacyLogScrubTest`, `AutomaticTriageDraftUsesToneGenerationTest`). Focused 05B-02 gates pass.

## User Setup Required

None - no external service configuration required.

## Verification

- `./gradlew.bat :backend:core:spotlessApply`
- `./gradlew.bat :backend:core:test --tests "*ClassifyThreadReplyStatus*" --tests "*ThreadReplyBucket*" --tests "*ThreadReplyStatus*" --tests "*AccountDeletion*" --tests "*GmailDeliveryProcessing*" --tests "*ApplicationModules*" --tests "*DomainBoundary*"`
- `./gradlew.bat :backend:api:test --tests "*ApplicationModules*"`
- `rg -n 'messages\(\)\.list|q="in:sent"|in:sent' backend/core/src/main/java/com/zeromail/core/thread` - no matches
- `rg -n 'deleteByTenantId' backend/core/src/main/java/com/zeromail/core/thread backend/core/src/test/java/com/zeromail/core/thread` - no matches
- JetBrains file-problem scans: no errors on touched production files and new tests; non-blocking warnings remain for explicit JPA default lengths and future-use repository count method.

## Next Phase Readiness

Plan 03 can wire the triage inbound sub-step to `ClassifyThreadReplyStatusService.classify(...)`, publish `ThreadDraftSaved` from the draft path, and add the `triage -> thread` Modulith edge. Plan 04 can build the read side on top of the `thread_reply_status` row shape and repository count contract.

---
*Phase: 05B-user-surface-ai-draft-replies*
*Completed: 2026-05-13*

## Self-Check: PASSED
