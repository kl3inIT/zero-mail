---
phase: 10-gmail-mailbox-foundation-and-account-management
plan: 02
subsystem: database
tags: [gmail, liquibase, postgresql, jpa, multi-mailbox]

requires:
  - phase: 10-01
    provides: multi-mailbox red tests and old single-account fixtures
provides:
  - Liquibase changeset 119 for multi-mailbox gmail_connections schema
  - GmailConnectionEntity mappings for is_primary and display_purpose
  - GmailConnectionRepository ownership and primary-ordered tenant queries
affects: [gmail, mailbox, oauth, account-management, phase-10]

tech-stack:
  added: []
  patterns:
    - Raw SQL Liquibase DDL with HALT preconditions for data-dependent migration guards
    - Repository ownership lookups that include both connection id and tenant id

key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/119-gmail-connections-multi-mailbox.yaml
  modified:
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java

key-decisions:
  - "Changeset 119 uses onFail: HALT for legacy duplicate CONNECTED emails so required structural DDL is never falsely marked applied."
  - "The old one-Gmail-per-tenant constraint is dropped as a table constraint, not as an index."
  - "The schema now enforces duplicate active mailbox email and at-most-one-primary with partial unique indexes."

patterns-established:
  - "Multi-mailbox uniqueness lives in Postgres partial unique indexes: uq_gmail_conn_active_email and uq_gmail_conn_primary."
  - "Mailbox ownership resolution starts from findByIdAndTenantId(UUID id, UUID tenantId)."

requirements-completed: [WSP-02, WSP-03, GMA-03, GMA-06, VER-01]

duration: 18min
completed: 2026-06-09
---

# Phase 10 Plan 02: Gmail Connection Migration Summary

**Multi-mailbox gmail_connections schema with primary mailbox flags, display labels, and tenant-owned repository lookup**

## Performance

- **Duration:** 18 min
- **Started:** 2026-06-09T04:42:00Z
- **Completed:** 2026-06-09T05:00:32Z
- **Tasks:** 3 completed
- **Files modified:** 4

## Accomplishments

- Added Liquibase changeset `119-gmail-connections-multi-mailbox` and included it from `db.changelog-master.yaml` after changeset 118.
- Replaced the old one-Gmail-per-tenant constraint with partial unique indexes `uq_gmail_conn_active_email` and `uq_gmail_conn_primary`.
- Added `is_primary` and `display_purpose` columns, with deterministic primary backfill and rollback.
- Mapped `GmailConnectionEntity.isPrimary` and `GmailConnectionEntity.displayPurpose`.
- Added repository methods `findByIdAndTenantId(UUID id, UUID tenantId)` and `findByTenantIdOrderByIsPrimaryDesc(UUID tenantId)` for later service and API plans.

## Task Commits

Each task was committed atomically:

1. **Task 1: Liquibase changeset 119** - `0c83a7c0` (feat)
2. **Task 2: GmailConnectionEntity primary fields** - `0781a305` (feat)
3. **Task 3: GmailConnectionRepository ownership queries** - `8effeea8` (feat)

**Plan metadata:** committed with this summary.

## Files Created/Modified

- `backend/core/src/main/resources/db/changelog/changes/119-gmail-connections-multi-mailbox.yaml` - Adds the raw SQL changeset, HALT precondition, deterministic primary backfill, partial unique indexes, and rollback.
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` - Includes changeset 119 after changeset 118.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java` - Maps `is_primary` and `display_purpose`.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java` - Adds tenant-owned id lookup and primary-first tenant list query.

## Decisions Made

- Used `onFail: HALT` instead of `MARK_RAN` for duplicate active legacy email rows, because changeset 119 adds required columns and indexes that must not be skipped while marked applied.
- Dropped `uq_gmail_connections_tenant_id` with `ALTER TABLE ... DROP CONSTRAINT`, matching the original Liquibase `addUniqueConstraint` shape.
- Preserved token ciphertext by keeping changeset 119 away from `refresh_token_encrypted`, `scopes_granted`, and `watch_*` columns.

## Deviations from Plan

None - plan executed exactly as written.

**Total deviations:** 0 auto-fixed.
**Impact on plan:** No scope change.

## Issues Encountered

- Plan-local acceptance checks passed: the changeset contains `DROP CONSTRAINT uq_gmail_connections_tenant_id`, both partial unique indexes, `is_primary`, `display_purpose`, `onFail: HALT`, a rollback block, and no token/scope/watch column references.
- `./gradlew :backend:core:compileJava` passed.
- Full Plan 01 red-test execution remains intentionally blocked until later plans add `MailboxRef`, `GmailConnectionService.setPrimary(...)`, and OAuth intent classes.
- Existing `GmailConnectionUniquenessTest` still encodes the old one-Gmail-per-tenant invariant and must be updated before broad core test runs can represent the new model.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 10-03. The schema and repository contract now support mailbox-scoped Gmail client lookup and token-cache isolation.

---
*Phase: 10-gmail-mailbox-foundation-and-account-management*
*Completed: 2026-06-09*
