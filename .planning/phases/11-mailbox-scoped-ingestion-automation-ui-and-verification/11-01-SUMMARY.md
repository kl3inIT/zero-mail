---
phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification
plan: 01
subsystem: testing
tags: [gmail, mailbox-scope, postgres, archunit, api-security]
requires:
  - phase: 10-gmail-mailbox-foundation-and-account-management
    provides: MailboxRef, buildClientForMailbox, mailbox ownership seam, connected-mailboxes API foundation
provides:
  - Phase 11 compile-green RED validation spine
  - Two-CONNECTED-mailbox raw-JDBC core fixture
  - findByTenantId ArchUnit boundary rule and primary-shim allow-list
  - API cross-account isolation harness for planned active-mailbox binding
affects: [phase-11, gmail-ingestion, rules-runtime, outbound-routing, audit, api-security]
tech-stack:
  added: []
  patterns:
    - Future symbols reached by reflection, information_schema, pg_indexes, and HTTP path strings
    - Raw-JDBC fixtures for first-level-cache-proof mailbox isolation assertions
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

- Added `GmailClientLookupBoundaryTest.findByTenantId_isForbiddenInMailboxScopedFlows`, guarding the Phase 10 primary-mailbox compatibility shim.
- Added six backend/core RED invariant tests plus `OldTwoMailboxFixture` for Pub/Sub lookup, observed-message PK collision, projection AAD continuity, migration backfill/indexes, mailbox-owned rules, and outbound/audit routing.
- Added `CrossAccountIsolationTest` with one random-port API context, `RestClient`, test auth headers, and planned active-mailbox endpoint strings.
- Preserved the HIGH-1 compile-green rule: not-yet-built Phase 11 symbols are reached through reflection, SQL metadata probes, row-count assertions, or HTTP strings.

## Task Commits

1. **Task 1: Gmail primary shim boundary rule** - `d4117483` (`test`)
2. **Task 2: Core mailbox-scoped invariant scaffolds** - `88d3af53` (`test`)
3. **Task 3: Cross-account mailbox isolation harness** - `0ed578a4` (`test`)

## RED Acceptance Contracts

- `PubSubMailboxLookupTest` waits on `TenantMailboxRef`, `PubSubTenantLookupRepository.findConnectedMailboxByEmail(String)`, and `uq_gmail_conn_active_email_global`.
- `ObservedMailboxPkTest` waits on `mail_message_observed.gmail_connection_id` and PK `(tenant_id, gmail_connection_id, gmail_message_id)`.
- `ProjectionAadContinuityTest` waits on `gmail_inbox_projection.gmail_connection_id` in the PK while proving cipher AAD remains `tenantId:gmailMessageId:field`.
- `Migration12xBackfillTest` waits on Liquibase 120-127, mailbox columns on ingestion/projection/job/rules/audit tables, and widened audit/rules indexes.
- `MailboxOwnedRulesRuntimeTest` waits on `rules.gmail_connection_id`, mailbox-scoped template uniqueness, and `RuleRepository.findEnabledByTenantIdAndGmailConnectionIdOrderByOrderIndex(UUID, UUID)`.
- `OutboundMailboxRoutingTest` waits on `OutboundSendCommand.mailboxRef`, `triage_audit.source_mailbox_id`, `triage_audit.executing_mailbox_id`, and `ux_triage_audit_idem` including `executing_mailbox_id`.
- `CrossAccountIsolationTest` waits on active-mailbox HTTP endpoints, mailbox binding context, active-only inbox reads, and outbound confirmation using the active/executing mailbox instead of the tenant primary shim.

## ArchUnit Allow-Lists

`ALLOWED_TENANT_LOOKUP_CALLERS` stayed unchanged: `AssistantPendingActionReconciler`, `GmailSentMessagesReader`, `GetMessageToolHandler`, `GetThreadToolHandler`, `ListLabelsToolHandler`, `SearchInboxToolHandler`, `DraftReplySourceLoader`, `ToneContextBuilder$GmailSentMailSource`, `GmailPreviewReadService`, `ForwardMessageAssembler`, `GmailOutboundSendGateway`, `TriageGmailWriter`.

`ALLOWED_PRIMARY_SHIM_CALLERS` now contains: `GmailAccessGuard`, `SenderMessageReadService`, `GmailConnectionService`, `GmailDeliveryProcessingService`, `GmailPreviewReadService`, `InboxBackfillService`, `RecentInboxReadService`.

## Verification

- `./gradlew.bat :backend:core:test --tests "*GmailClientLookupBoundary*"` - passed in Task 1.
- `./gradlew.bat :backend:core:compileTestJava` - passed in Task 2, with expected `buildClientForTenant` deprecation warnings.
- `./gradlew.bat :backend:api:compileTestJava` - passed in Task 3, with existing `buildClientForTenant` deprecation warnings.
- JetBrains file-problem check for `CrossAccountIsolationTest.java` reported no errors before timing out.

## Decisions Made

- Kept every future Java production symbol out of compile-time references until later waves create it.
- Used raw JDBC in fixtures and schema tests to avoid Hibernate first-level-cache false positives.
- Duplicated the tiny two-mailbox seed helper inside `CrossAccountIsolationTest` because `backend:api` does not compile against `backend:core` test sources; adding Gradle test-fixture plumbing would have been broader than this validation harness needs.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] API test fixture reuse was unavailable on the API test classpath**
- **Found during:** Task 3 (`CrossAccountIsolationTest` harness)
- **Issue:** The plan asked to reuse `OldTwoMailboxFixture`, but `backend:api` sees `backend:core` main classes, not core test classes.
- **Fix:** Added a tiny API-local raw-JDBC mailbox seed helper and documented the deviation in class Javadoc.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/security/CrossAccountIsolationTest.java`
- **Verification:** `./gradlew.bat :backend:api:compileTestJava` passed.
- **Committed in:** `0ed578a4`

**Total deviations:** 1 auto-fixed (Rule 3).
**Impact on plan:** No production blast radius; the harness remains single-context, compile-green, and RED against planned HTTP behavior.

## Issues Encountered

- PowerShell could not pass a long multiline patch through the local `apply_patch.bat` wrapper, so the API test file was created with JetBrains MCP and verified with IDE/Gradle checks.
- Pre-commit `spotlessApply` had previously left four production files with formatting-only diffs; they were not included in Plan 11-01 commits.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 11-02 can implement Liquibase 120-127 against the RED schema probes. Plans 11-03 through 11-05 now have explicit contracts for Pub/Sub mailbox lookup, observed/projection keys, rules/outbound mailbox routing, audit provenance, active-mailbox binding, and ArchUnit allow-list drain.

---
*Phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification*
*Completed: 2026-06-09*
