---
phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification
plan: 01
subsystem: testing
tags: [gmail, mailbox-scope, postgres, archunit, spring-boot]

requires:
  - phase: 10-gmail-mailbox-foundation-and-account-management
    provides: mailbox ids, MailboxRef, buildClientForMailbox, ownership seam, connected-mailboxes API foundation
provides:
  - Phase 11 RED validation spine for mailbox-scoped ingestion, rules, outbound, audit, and API isolation
  - Two-CONNECTED-mailbox raw-JDBC fixture for core invariant tests
  - ArchUnit primary-shim boundary rule forbidding new GmailConnectionRepository.findByTenantId callers
  - API cross-account isolation harness using active-mailbox HTTP path strings
affects: [phase-11, gmail-ingestion, rules-runtime, outbound-routing, audit, api-security]

tech-stack:
  added: []
  patterns:
    - Compile-green RED contracts via reflection, information_schema, pg_indexes, and plain HTTP path strings
    - Raw-JDBC mailbox fixtures for DB round trips that bypass Hibernate first-level cache

key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/migration/OldTwoMailboxFixture.java
    - backend/core/src/test/java/com/zeromail/core/gmail/PubSubMailboxLookupTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/ObservedMailboxPkTest.java
    - backend/core/src/test/java/com/zeromail/core/inbox/ProjectionAadContinuityTest.java
    - backend/core/src/test/java/com/zeromail/core/migration/Migration12xBackfillTest.java
    - backend/core/src/test/java/com/zeromail/core/rules/MailboxOwnedRulesRuntimeTest.java
    - backend/core/src/test/java/com/zeromail/core/outbound/OutboundMailboxRoutingTest.java
    - backend/api/src/test/java/com/zeromail/api/security/CrossAccountIsolationTest.java
  modified:
    - backend/core/src/test/java/com/zeromail/core/arch/GmailClientLookupBoundaryTest.java

key-decisions:
  - "Future Phase 11 Java symbols are referenced by reflection or schema probes, never direct compile-time imports, so compileTestJava remains green."
  - "API CrossAccountIsolationTest duplicates a tiny JDBC two-mailbox fixture instead of adding Gradle test-fixtures plumbing, because backend:api does not compile backend:core test sources."
  - "Projection AAD stays tenantId:gmailMessageId:field; mailbox id enters projection identity but not encryption AAD."

patterns-established:
  - "Schema RED tests query information_schema/pg_indexes for future columns, PKs, and indexes."
  - "Runtime RED tests use plain HTTP path strings for future controller/filter surfaces."
  - "ArchUnit shim allow-lists are explicit drain targets for later mailbox-scoping plans."

requirements-completed: [VER-03, AUD-05, AUD-06, ING-01, ING-03, ING-06, AUTO-04, AUTO-06, AUD-02]

duration: multi-session
completed: 2026-06-09
---

# Phase 11 Plan 01 Summary

**Mailbox-scoped RED validation spine for Phase 11 ingestion, rules, outbound, audit, and API isolation.**

## Performance

- **Duration:** multi-session resume
- **Started:** 2026-06-09 (earlier session)
- **Completed:** 2026-06-09T20:44:18+07:00
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments

- Added a second `GmailClientLookupBoundaryTest` ArchUnit rule that forbids new `GmailConnectionRepository.findByTenantId` primary-shim callers outside a drain allow-list.
- Added six backend/core RED invariant tests plus `OldTwoMailboxFixture`, covering Pub/Sub mailbox lookup, observed-message PK collision, projection AAD continuity, migration backfill/indexes, mailbox-owned rules, and outbound/audit mailbox routing.
- Added API-side `CrossAccountIsolationTest` using a single random-port Spring context, `RestClient`, test session headers, and planned active-mailbox path strings.
- Kept Wave 0 compile-green: future symbols are reached through reflection, schema probes, or HTTP strings rather than direct Java references.

## Task Commits

1. **Task 1: Gmail primary shim boundary rule** - `d4117483` (`test`)
2. **Task 2: Core mailbox-scoped invariant scaffolds** - `88d3af53` (`test`)
3. **Task 3: Cross-account mailbox isolation harness** - `0ed578a4` (`test`)

## Files Created/Modified

- `backend/core/src/test/java/com/zeromail/core/arch/GmailClientLookupBoundaryTest.java` - added `findByTenantId_isForbiddenInMailboxScopedFlows` and `ALLOWED_PRIMARY_SHIM_CALLERS`.
- `backend/core/src/test/java/com/zeromail/core/migration/OldTwoMailboxFixture.java` - seeds one tenant with two CONNECTED mailbox rows via raw JDBC.
- `backend/core/src/test/java/com/zeromail/core/gmail/PubSubMailboxLookupTest.java` - RED for `findConnectedMailboxByEmail`, `TenantMailboxRef`, and global connected-email uniqueness.
- `backend/core/src/test/java/com/zeromail/core/gmail/ObservedMailboxPkTest.java` - RED for `mail_message_observed.gmail_connection_id` and PK `(tenant_id, gmail_connection_id, gmail_message_id)`.
- `backend/core/src/test/java/com/zeromail/core/inbox/ProjectionAadContinuityTest.java` - proves existing inbox projection cipher AAD remains tenant/message/field while projection PK waits for mailbox id.
- `backend/core/src/test/java/com/zeromail/core/migration/Migration12xBackfillTest.java` - RED for changesets 120-127, mailbox-scope backfill columns, and widened audit/rules indexes.
- `backend/core/src/test/java/com/zeromail/core/rules/MailboxOwnedRulesRuntimeTest.java` - RED for `rules.gmail_connection_id`, mailbox-scoped template uniqueness, and future mailbox-filtered rule repository load.
- `backend/core/src/test/java/com/zeromail/core/outbound/OutboundMailboxRoutingTest.java` - RED for `OutboundSendCommand.mailboxRef`, audit mailbox provenance columns, and executing-mailbox idempotency.
- `backend/api/src/test/java/com/zeromail/api/security/CrossAccountIsolationTest.java` - RED for active mailbox selection, active-only reads, and active/executing mailbox outbound confirmation behavior.

## RED Acceptance Contracts

- `PubSubMailboxLookupTest`: waits on `TenantMailboxRef`, `PubSubTenantLookupRepository.findConnectedMailboxByEmail(String)`, and `uq_gmail_conn_active_email_global`.
- `ObservedMailboxPkTest`: waits on `mail_message_observed.gmail_connection_id` and the three-column observed-message primary key.
- `ProjectionAadContinuityTest`: waits on `gmail_inbox_projection.gmail_connection_id` in the PK; cipher AAD must continue decrypting with `tenantId:gmailMessageId:field`.
- `Migration12xBackfillTest`: waits on 120-127 Liquibase changesets, mailbox columns on ingestion/projection/job/rules/audit tables, and widened `ux_triage_audit_idem` / `uq_rules_tenant_template_key_present` indexes.
- `MailboxOwnedRulesRuntimeTest`: waits on `rules.gmail_connection_id`, mailbox-scoped template uniqueness, and `RuleRepository.findEnabledByTenantIdAndGmailConnectionIdOrderByOrderIndex(UUID, UUID)`.
- `OutboundMailboxRoutingTest`: waits on `OutboundSendCommand.mailboxRef`, `triage_audit.source_mailbox_id`, `triage_audit.executing_mailbox_id`, and `ux_triage_audit_idem` including `executing_mailbox_id`.
- `CrossAccountIsolationTest`: waits on active-mailbox HTTP endpoints, mailbox binding context, active-only inbox reads, and outbound confirmation using the active/executing mailbox rather than the tenant primary shim.

## ArchUnit Allow-Lists

`ALLOWED_TENANT_LOOKUP_CALLERS` remains unchanged from Plan 10:

- `com.zeromail.api.chat.AssistantPendingActionReconciler`
- `com.zeromail.core.chat.usecases.settings.GmailSentMessagesReader`
- `com.zeromail.core.chat.usecases.tools.GetMessageToolHandler`
- `com.zeromail.core.chat.usecases.tools.GetThreadToolHandler`
- `com.zeromail.core.chat.usecases.tools.ListLabelsToolHandler`
- `com.zeromail.core.chat.usecases.tools.SearchInboxToolHandler`
- `com.zeromail.core.draft.usecases.DraftReplySourceLoader`
- `com.zeromail.core.draft.usecases.ToneContextBuilder$GmailSentMailSource`
- `com.zeromail.core.gmail.usecases.GmailPreviewReadService`
- `com.zeromail.core.outbound.usecases.ForwardMessageAssembler`
- `com.zeromail.core.outbound.usecases.GmailOutboundSendGateway`
- `com.zeromail.core.triage.usecases.TriageGmailWriter`

`ALLOWED_PRIMARY_SHIM_CALLERS` now tracks current `findByTenantId` callers:

- `com.zeromail.api.security.GmailAccessGuard`
- `com.zeromail.core.cleanup.usecases.SenderMessageReadService`
- `com.zeromail.core.gmail.usecases.GmailConnectionService`
- `com.zeromail.core.gmail.usecases.GmailDeliveryProcessingService`
- `com.zeromail.core.gmail.usecases.GmailPreviewReadService`
- `com.zeromail.core.gmail.usecases.InboxBackfillService`
- `com.zeromail.core.gmail.usecases.RecentInboxReadService`

## Verification

- `./gradlew.bat :backend:core:test --tests "*GmailClientLookupBoundary*"` - passed in Task 1.
- `./gradlew.bat :backend:core:compileTestJava` - passed in Task 2, with expected `buildClientForTenant` deprecation warnings.
- `./gradlew.bat :backend:api:compileTestJava` - passed in Task 3, with existing `buildClientForTenant` deprecation warnings.
- JetBrains file-problem check for `CrossAccountIsolationTest.java` reported no errors before timing out.

## Decisions Made

- Kept all RED contracts compile-green by using reflection, SQL metadata probes, row-count assertions, and HTTP path strings.
- Used raw JDBC in the test fixtures to avoid false positives from Hibernate first-level cache.
- Kept `CrossAccountIsolationTest` self-contained on the API test classpath rather than introducing test-fixtures source-set plumbing.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] API test fixture reuse was not available on the API test classpath**
- **Found during:** Task 3 (CrossAccountIsolationTest harness)
- **Issue:** The plan asked to reuse `OldTwoMailboxFixture`, but `backend:api` test compilation sees `backend:core` main classes, not core test classes.
- **Fix:** Duplicated a tiny API-local raw-JDBC two-mailbox helper inside `CrossAccountIsolationTest` and documented why in the class Javadoc.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/security/CrossAccountIsolationTest.java`
- **Verification:** `./gradlew.bat :backend:api:compileTestJava` passed.
- **Committed in:** `0ed578a4`

---

**Total deviations:** 1 auto-fixed (Rule 3).
**Impact on plan:** No production blast radius; the harness remains single-context, compile-green, and RED against planned HTTP behavior.

## Issues Encountered

- PowerShell could not pass the long multiline patch through the local `apply_patch.bat` wrapper, so the new API test file was created with the JetBrains MCP file tool and validated with IDE/Gradle checks.
- Pre-commit `spotlessApply` had previously left four production files with formatting-only diffs; they were not included in this plan's summary/code commits.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 11-02 can now implement the Liquibase 120-127 mailbox-scope migrations against the RED schema probes. Plans 11-03 through 11-05 have explicit runtime contracts for Pub/Sub mailbox lookup, observed/projection keys, rules/outbound mailbox routing, audit provenance, active-mailbox binding, and ArchUnit allow-list drain.

---
*Phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification*
*Completed: 2026-06-09*
