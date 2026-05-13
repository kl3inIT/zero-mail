---
phase: 05B-user-surface-ai-draft-replies
plan: 04
subsystem: backend
tags: [postgres, jdbc, keyset-pagination, cqrs-read-side, spring-modulith]

requires:
  - phase: 05B-02
    provides: thread_reply_status schema, bucket enum, repository, and classifier state
  - phase: 05B-03
    provides: shared.pagination module edge on parent triage/thread modules and draft state writes
provides:
  - KeysetCursor codec with UUID, String, and NULLS_LAST cursor variants
  - AuditLogQueryService over triage_audit with tenant/action/range filters
  - NeedsReplyInboxQueryService over thread_reply_status with NULLS LAST keyset paging
  - toReplyCount badge query through ThreadReplyStatusRepository
  - MarkThreadResolvedService for tenant-scoped resolved-row updates
affects: [triage, thread, api, needs-reply, audit-log]

tech-stack:
  added: []
  patterns:
    - JDBC read-side query services with explicit keyset SQL and no OFFSET paging
    - Full-precision cursor encoding using epoch-second plus nanos
    - TenantContext binding before transactional repository writes

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/shared/pagination/KeysetCursor.java
    - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java
    - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogRow.java
    - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage.java
    - backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPageQuery.java
    - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyInboxQueryService.java
    - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyRow.java
    - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyPage.java
    - backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyPageQuery.java
    - backend/core/src/main/java/com/zeromail/core/thread/usecases/MarkThreadResolvedService.java
  modified: []

key-decisions:
  - "NeedsReplyRow exposes public bucket slugs (`to-reply`, `awaiting-their-reply`) rather than internal enum ids, so Plan 05 can pass them through directly."
  - "MarkThreadResolvedService uses TransactionOperations inside TenantContext binding instead of method-level @Transactional, matching the tenant-safe pattern established by ClassifyThreadReplyStatusService."
  - "Plan 04 did not edit core/triage/package-info.java or core/thread/package-info.java; Plan 03 already supplied the shared.pagination parent edges."

patterns-established:
  - "KeysetCursor split is limited to the first three separators so string ids containing ':' survive round-trip."
  - "Nullable last_classified_at paging is split into normal timestamp and NULLS_LAST-tail cursor regions."
  - "Projection rows remain metadata-only; Gmail display fields stay out of persisted query rows."

requirements-completed: [DRFT-04]

duration: 10min
completed: 2026-05-13
---

# Phase 05B Plan 04: Read-Side Projection Summary

**Tenant-scoped keyset read services for triage audit history and the needs-reply inbox**

## Performance

- **Duration:** 10 min
- **Started:** 2026-05-12T22:33:00Z
- **Completed:** 2026-05-12T22:43:00Z
- **Tasks:** 2
- **Files modified:** 16

## Accomplishments

- Added `KeysetCursor` with full-precision timestamp encoding, string-id support, UUID overload, fail-loud malformed cursor handling, and a literal `NULLS_LAST` sentinel.
- Added `AuditLogQueryService` and projection records for tenant-scoped, action/range-filterable audit log pages ordered by `(created_at desc, audit_id desc)`.
- Added `NeedsReplyInboxQueryService` and projection records for unresolved bucket pages, resolved pages, NULLS-LAST keyset paging, and the TO_REPLY count badge.
- Added `MarkThreadResolvedService` with tenant-scoped no-op-on-missing update semantics.
- Added Postgres-backed tests covering cursor precision, tenant isolation, action/draft mapping, null-tail pagination, count delegation through tenant context, and mark-resolved scoping.

## Task Commits

1. **Task 1: KeysetCursor + AuditLogQueryService** - `af4de43`
2. **Task 2: NeedsReplyInboxQueryService + MarkThreadResolvedService** - `0707b97`

**Plan metadata:** this summary commit

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/shared/pagination/KeysetCursor.java` - Opaque base64url cursor codec.
- `backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogQueryService.java` - JDBC keyset query over `triage_audit`.
- `backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage*.java` and `AuditLogRow.java` - Audit-list query/result contracts.
- `backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyInboxQueryService.java` - JDBC keyset query over `thread_reply_status` plus count badge.
- `backend/core/src/main/java/com/zeromail/core/thread/projection/NeedsReplyPage*.java` and `NeedsReplyRow.java` - Needs-reply query/result contracts.
- `backend/core/src/main/java/com/zeromail/core/thread/usecases/MarkThreadResolvedService.java` - Tenant-scoped resolved flag command service.
- `backend/core/src/test/java/com/zeromail/core/shared/pagination/KeysetCursorTest.java` - Cursor round-trip and malformed-input coverage.
- `backend/core/src/test/java/com/zeromail/core/triage/AuditLogQueryServiceTest.java` - Audit read-side tenant/order/cursor/filter coverage.
- `backend/core/src/test/java/com/zeromail/core/thread/NeedsReplyInboxQueryServiceTest.java` - Inbox null-tail paging/count/resolved coverage.
- `backend/core/src/test/java/com/zeromail/core/thread/MarkThreadResolvedServiceTest.java` - Tenant-scoped mark-resolved coverage.

## Decisions Made

- Returned public bucket slugs from `NeedsReplyRow.bucket()` because those are the API/UI contract strings Plan 05/06 consume.
- Kept subject, participants, body, and preview display fields out of the read-side projections; Plan 05 remains responsible for live Gmail metadata fan-out.
- Used `TransactionOperations` for mark-resolved writes so the tenant `ScopedValue` is bound before repository access.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Method-level @Transactional would bind tenant too late**
- **Found during:** Task 2 implementation
- **Issue:** The plan suggested `@Transactional` on `MarkThreadResolvedService.markResolved(...)`, but Phase 05B-02 already proved proxy-level transactions can open Hibernate before `TenantContext` is bound.
- **Fix:** Used `TransactionOperations` inside `ScopedValue.where(TenantContext.TENANT, ...)`, matching `ClassifyThreadReplyStatusService`.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/thread/usecases/MarkThreadResolvedService.java`
- **Verification:** `MarkThreadResolvedServiceTest` proves only the current tenant row flips; focused 05B-04 test slice green.
- **Committed in:** `0707b97`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Correctness improvement only; it preserves the planned public behavior and avoids cross-tenant repository access risk.

## Issues Encountered

- Spring context creation initially failed for `MarkThreadResolvedService` after adding a package-private test constructor; adding `@Autowired` to the production constructor fixed constructor selection.

## User Setup Required

None - no external service configuration required.

## Verification

- `./gradlew.bat :backend:core:spotlessApply`
- `./gradlew.bat :backend:core:test --tests "*KeysetCursor*" --tests "*AuditLogQuery*" --tests "*NeedsReplyInboxQuery*" --tests "*MarkThreadResolved*" --tests "*ApplicationModules*"`
- `./gradlew.bat :backend:core:test --tests "*KeysetCursor*" --tests "*AuditLogQuery*" --tests "*NeedsReplyInboxQuery*" --tests "*MarkThreadResolved*" --tests "*ApplicationModules*" --tests "*DomainBoundary*"`
- `rg -n -i "offset\b|count\(\*\)" backend/core/src/main/java/com/zeromail/core/triage/projection backend/core/src/main/java/com/zeromail/core/thread/projection` - no matches
- `git diff --name-only HEAD~2..HEAD` did not include `backend/core/src/main/java/com/zeromail/core/triage/package-info.java` or `backend/core/src/main/java/com/zeromail/core/thread/package-info.java`
- JetBrains file-problem scans and file rebuild: no errors on new production files

## Next Phase Readiness

Plan 05 can now wire `GET /api/triage/audit`, the needs-reply inbox endpoint, thread draft generation, and mark-resolved controllers against stable core service contracts. Remaining API RED tests are controller/DTO/error-mapping work owned by Plan 05.

---
*Phase: 05B-user-surface-ai-draft-replies*
*Completed: 2026-05-13*

## Self-Check: PASSED
