---
phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification
plan: 02
subsystem: database
tags: [gmail, mailbox-scope, postgres, liquibase, rules, audit, spring-modulith]

requires:
  - phase: 10-gmail-mailbox-foundation-and-account-management
    provides: gmail_connections multi-mailbox identity, primary mailbox ordering, per-tenant active-email uniqueness
  - phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification/11-01
    provides: RED schema and runtime probes for mailbox-scoped migrations and event/entity threading
provides:
  - Liquibase changesets 120-127 for mailbox-scoped ingestion, projection, rules, audit, jobs, and global active-email uniqueness
  - Mailbox-owned RuleEntity and RuleStatusProjection ownership surface
  - Mailbox-carrying MailMessageObserved and MailOutboundObserved records with transitional old-arity constructors
affects: [phase-11, gmail-ingestion, inbox-projection, rules-runtime, triage-audit, outbound-routing, api-security]

tech-stack:
  added: []
  patterns:
    - Append-only Liquibase nullable-to-backfill-to-NOT-NULL mailbox migrations
    - Deterministic primary mailbox backfill using DISTINCT ON ordering shared with GmailConnectionRepository
    - Transitional Java overloads for compile-green wave execution before runtime call-site migration

key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/120-pubsub-delivery-mailbox.yaml
    - backend/core/src/main/resources/db/changelog/changes/121-mail-message-observed-mailbox.yaml
    - backend/core/src/main/resources/db/changelog/changes/122-gmail-inbox-projection-mailbox.yaml
    - backend/core/src/main/resources/db/changelog/changes/123-gmail-inbox-sync-state-mailbox.yaml
    - backend/core/src/main/resources/db/changelog/changes/124-processing-job-mailbox.yaml
    - backend/core/src/main/resources/db/changelog/changes/125-triage-audit-mailbox.yaml
    - backend/core/src/main/resources/db/changelog/changes/126-rules-mailbox-ownership.yaml
    - backend/core/src/main/resources/db/changelog/changes/127-gmail-conn-global-active-email-unique.yaml
  modified:
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java
    - backend/core/src/main/java/com/zeromail/core/rules/projection/RuleStatusProjection.java
    - backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java
    - backend/core/src/main/java/com/zeromail/core/gmail/event/MailOutboundObserved.java

key-decisions:
  - "HIGH-3 locked idempotency axis: triage_audit uses executing_mailbox_id in ux_triage_audit_idem; it does not receive a gmail_connection_id column."
  - "HIGH-2 locked Pub/Sub isolation mechanism: changeset 127 adds a global partial unique index on lower(google_email) for CONNECTED rows."
  - "Inbox projection ciphertext AAD remains tenantId:gmailMessageId:field; mailbox id changes projection identity and indexes only."
  - "RuleEntity and Gmail event old-arity constructors are transitional compile-only paths and must be removed by Plans 04 and 03 respectively."

patterns-established:
  - "Every legacy mailbox backfill uses ORDER BY tenant_id, is_primary DESC, (status = 'CONNECTED') DESC, connected_at NULLS LAST, id."
  - "Hot-table PK/index swap changesets carry YAML-native DEPLOY comments with preflight, drain, apply, and resume guidance."
  - "Cross-tenant duplicate Gmail grants are rejected at schema level rather than resolved by LIMIT 1 or tenant guesswork."

requirements-completed: [WSP-03, ING-02, ING-03, ING-06, AUD-01, AUTO-01, AUTO-02, VER-03]

duration: multi-session
completed: 2026-06-09
---

# Phase 11 Plan 02 Summary

**Mailbox-scoped schema foundation for ingestion, projection, rules, audit, jobs, and in-core Gmail events.**

## Performance

- **Duration:** multi-session resume
- **Started:** 2026-06-09T13:57:50+07:00
- **Completed:** 2026-06-09T21:45:49+07:00
- **Tasks:** 4
- **Files modified:** 13

## Accomplishments

- Added append-only Liquibase changesets 120-127 and included them from `db.changelog-master.yaml` in numeric order.
- Added NOT NULL mailbox identity to Pub/Sub deliveries, observed messages, inbox projections, inbox sync cursors, processing jobs, rules, and triage audit provenance after deterministic primary-mailbox backfill.
- Rebuilt mailbox-sensitive primary keys and indexes: observed/projection/sync-state identity, rules template-key uniqueness, triage audit idempotency, and open inbox-backfill dedup.
- Added global active Gmail email uniqueness so a CONNECTED Gmail address maps to at most one tenant for Pub/Sub routing.
- Threaded mailbox id onto `RuleEntity`, `RuleStatusProjection`, `MailMessageObserved`, and `MailOutboundObserved` while keeping compile-green transitional constructors for later runtime plans.

## Task Commits

1. **Task 1: Add gmail_connection_id to ingestion tables** - `842f6e73` (`feat`)
2. **Task 2: Add triage_audit provenance + rules ownership** - `90601614` (`feat`)
3. **Task 3: Thread gmail_connection_id onto in-core domain events** - `3a8754fb` (`feat`)
4. **Task 4: Global active-email uniqueness** - `1e4b65c5` (`feat`)

## Files Created/Modified

- `backend/core/src/main/resources/db/changelog/changes/120-pubsub-delivery-mailbox.yaml` - adds `pubsub_delivery.gmail_connection_id` and widens delivery dedup.
- `backend/core/src/main/resources/db/changelog/changes/121-mail-message-observed-mailbox.yaml` - adds observed-message mailbox id and PK `(tenant_id, gmail_connection_id, gmail_message_id)`.
- `backend/core/src/main/resources/db/changelog/changes/122-gmail-inbox-projection-mailbox.yaml` - adds projection mailbox id, mailbox PK, and mailbox-aware list/thread/expiry indexes without changing cipher AAD.
- `backend/core/src/main/resources/db/changelog/changes/123-gmail-inbox-sync-state-mailbox.yaml` - makes inbox sync cursor identity `(tenant_id, gmail_connection_id)`.
- `backend/core/src/main/resources/db/changelog/changes/124-processing-job-mailbox.yaml` - adds processing-job mailbox id plus open inbox-backfill mailbox dedup index.
- `backend/core/src/main/resources/db/changelog/changes/125-triage-audit-mailbox.yaml` - adds `source_mailbox_id`, `executing_mailbox_id`, and rebuilds `ux_triage_audit_idem` on executing mailbox.
- `backend/core/src/main/resources/db/changelog/changes/126-rules-mailbox-ownership.yaml` - adds `rules.gmail_connection_id` and widens `uq_rules_tenant_template_key_present` to `(tenant_id, gmail_connection_id, template_key)`.
- `backend/core/src/main/resources/db/changelog/changes/127-gmail-conn-global-active-email-unique.yaml` - adds global CONNECTED-email uniqueness with a HALT duplicate precondition.
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` - includes changesets 120-127.
- `backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java` - adds `gmailConnectionId`, getter, mailbox constructor parameter, projection wiring, and transitional old-arity overload.
- `backend/core/src/main/java/com/zeromail/core/rules/projection/RuleStatusProjection.java` - adds `UUID gmailConnectionId` after `ruleId`.
- `backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java` - adds event mailbox id and transitional old-arity constructor.
- `backend/core/src/main/java/com/zeromail/core/gmail/event/MailOutboundObserved.java` - adds event mailbox id, privacy Javadoc, and transitional old-arity constructor.

## Verification

- `./gradlew.bat :backend:core:test --tests "*ObservedMailboxPkTest" --tests "*ProjectionAadContinuityTest"` - passed after Task 1.
- `./gradlew.bat :backend:core:compileJava` - passed after Tasks 2, 3, and 4.
- `./gradlew.bat :backend:core:compileTestJava` - passed after Tasks 2, 3, and 4.
- `./gradlew.bat :backend:core:test --tests "com.zeromail.core.migration.Migration12xBackfillTest.mailboxScopeColumnsExistAndAreNotNullableAfterBackfill" --tests "com.zeromail.core.migration.Migration12xBackfillTest.mailboxOwnedIndexesReplaceTenantOnlyUniqueness"` - passed after Task 2.
- `./gradlew.bat :backend:core:test --tests "*RuleEntity*"` - passed after Task 2.
- `./gradlew.bat :backend:core:test --tests "*Migration12*"` - passed after Task 4.
- JetBrains file-problem checks for `RuleEntity.java`, `RuleStatusProjection.java`, `MailMessageObserved.java`, `MailOutboundObserved.java`, and `127-gmail-conn-global-active-email-unique.yaml` reported no errors.
- Grep confirmed all 120-127 changesets are included from `db.changelog-master.yaml`.
- `git diff -- 119-gmail-connections-multi-mailbox.yaml InboxProjectionCipher.java` returned no diff.

## Decisions Made

- `triage_audit` intentionally has no `gmail_connection_id`; it carries `source_mailbox_id` and `executing_mailbox_id`, and idempotency is keyed on `executing_mailbox_id`.
- A CONNECTED Gmail account is globally unique across tenants through `uq_gmail_conn_active_email_global`; operators must disconnect/dedupe existing cross-tenant duplicates before migration.
- `processing_job` has no `idempotency_key` column in the current schema/code, so mailbox-scoped open backfill dedup is implemented with `uq_processing_job_open_inbox_backfill_mailbox` on `(tenant_id, gmail_connection_id)` for pending/processing inbox backfills.
- `RuleManagementService` and `RuleTemplateMaterializationService` remain on the transitional old-arity `RuleEntity` constructor for this foundation plan; Plan 04 must pass structured mailbox ids and remove that overload.
- `GmailDeliveryProcessingService`, `BackfillNeedsReplyService`, `TriageOutboundRuntimeGateTest`, `ClassifyThreadReplyStatusServiceIntegrationTest`, and `TriagePrivacySweepTest` remain on transitional event constructors; Plan 03 must migrate them and remove the overloads.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] processing_job idempotency_key does not exist**
- **Found during:** Task 1 (processing_job mailbox migration)
- **Issue:** The plan referenced suffixing `processing_job.idempotency_key`, but the current schema and enqueuer dedupe do not have that column.
- **Fix:** Added `processing_job.gmail_connection_id`, `idx_processing_job_tenant_mailbox_type`, and `uq_processing_job_open_inbox_backfill_mailbox` for one open `INBOX_PROJECTION_BACKFILL` per tenant/mailbox.
- **Files modified:** `backend/core/src/main/resources/db/changelog/changes/124-processing-job-mailbox.yaml`
- **Verification:** `*ObservedMailboxPkTest`/`*ProjectionAadContinuityTest` passed after Task 1; full `*Migration12*` passed after Task 4.
- **Committed in:** `842f6e73`

---

**Total deviations:** 1 auto-fixed (Rule 2).
**Impact on plan:** The mailbox idempotency invariant is preserved with the schema shape that actually exists; no unrelated schema surface was introduced.

## Issues Encountered

- The first Task 2 `*Migration12*` run failed only on the expected 127 range assertion because Task 4 had not yet created changeset 127. The Task 2-specific migration assertions passed, and the full `*Migration12*` suite passed after Task 4.
- Two Gradle test invocations were accidentally run in parallel and collided on Gradle binary test-result files (`NoSuchFileException` / `EOFException`). Stopping the daemon and rerunning the focused checks sequentially passed.
- PowerShell could not pass multiline patches to the local `apply_patch` helper as UTF-8, so the changelog and Java edits were applied with JetBrains MCP file tools and then verified with IDE inspections, Gradle compiles, and migration tests.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 11-03 can now populate mailbox ids in Pub/Sub delivery, observed-message inserts, projection upserts, sync-state cursor updates, and in-core events. It must migrate the exact event caller files listed above and remove the transitional event constructors.

Plan 11-04 can now make rules and outbound execution mailbox-aware against real schema columns. It must migrate `RuleManagementService` and `RuleTemplateMaterializationService` to the mailbox-carrying `RuleEntity` constructor, then remove the transitional constructor so null mailbox inserts cannot reach runtime.

---
*Phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification*
*Completed: 2026-06-09*
