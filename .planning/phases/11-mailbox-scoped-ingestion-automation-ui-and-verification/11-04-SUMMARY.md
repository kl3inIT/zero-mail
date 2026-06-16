---
phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification
plan: 04
subsystem: backend-automation
tags: [gmail, mailbox-scope, rules, triage, outbound, audit]

requires:
  - phase: 11-02
    provides: gmail_connection_id columns on mailbox-scoped records, triage_audit source/executing mailbox columns, mailbox-aware idempotency index
  - phase: 11-03
    provides: mailbox-scoped ingestion events carrying source gmailConnectionId
provides:
  - mailbox-owned rule CRUD/runtime load and disabled copy-rules materialization
  - mailbox-carrying triage dispatch and mailbox-aware Gmail write adapter calls
  - executing MailboxRef outbound send command and mailbox-aware send gateway
  - source/executing mailbox audit provenance and same-mailbox undo routing
  - mailbox-scoped forward/reply/draft source reads
affects: [11-05-mailbox-context, 11-06-web-mailbox-ui, automation, audit, outbound]

tech-stack:
  added: []
  patterns:
    - MailboxRef is carried explicitly through automation writes and source reads until Plan 05 binds MailboxContext.
    - Tenant-only compatibility methods bridge only through documented primary-mailbox shims.

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/rules/usecases/CopyRulesService.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/rules/usecases/RuleManagementService.java
    - backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleRepository.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java
    - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageUndoService.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java
    - backend/core/src/main/java/com/zeromail/core/outbound/usecases/OutboundSendCommand.java
    - backend/core/src/main/java/com/zeromail/core/outbound/usecases/GmailOutboundSendGateway.java
    - backend/core/src/main/java/com/zeromail/core/outbound/usecases/ForwardMessageAssembler.java
    - backend/core/src/main/java/com/zeromail/core/draft/usecases/DraftReplySourceLoader.java
    - backend/core/src/main/java/com/zeromail/core/draft/usecases/ToneContextBuilder.java
    - backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/usecases/UnsubscribeMailtoSender.java

key-decisions:
  - Triage source and executing mailbox are the observed message mailbox in Plan 11-04; active-mailbox overrides wait for Plan 11-05.
  - Chat-confirmed sends and on-demand drafts use a documented primary-mailbox shim until Plan 11-05 provides MailboxContext.
  - GmailClientLookupBoundaryTest allow-list entries remain untouched and are intentionally drained by Plan 11-05.

patterns-established:
  - Automation runtime loads enabled rules by tenant and owning gmail_connection_id only.
  - Gmail writes/sends/read-derived source fetches accept MailboxRef at the boundary that calls GmailApiClientFactory.
  - triage_audit idempotency is keyed by executing_mailbox_id, not a tenant-global Gmail id.

requirements-completed: [AUTO-01, AUTO-02, AUTO-03, AUTO-04, AUTO-05, AUTO-06, AUD-01, AUD-02, AUD-03, AUD-07]

duration: multi-session
completed: 2026-06-09
---

# Phase 11 Plan 04 Summary

**Mailbox-owned automation routes rules, Gmail writes, outbound sends, undo, and draft/forward source reads through concrete Gmail mailboxes.**

## Performance

- **Duration:** multi-session
- **Started:** 2026-06-09 after Plan 11-03 close-out
- **Completed:** 2026-06-09T18:37:35Z
- **Tasks:** 4
- **Files modified:** 53 code/test files

## Accomplishments

- Added mailbox-owned rule runtime loading, structured rule mailbox ownership, primary-mailbox template seeding, and disabled copy-rules cloning for review before activation.
- Threaded source/executing mailbox IDs through triage dispatch, audit reservation/terminal rows, idempotency lookup, Gmail write actions, and undo routing.
- Migrated outbound sends to `OutboundSendCommand(tenantId, mailboxRef, gmailMessage)` and updated all three callers: `TriageAuditSaga`, `UnsubscribeMailtoSender`, and `AssistantSendExecutor`.
- Migrated forward/reply/draft source reads in `ForwardMessageAssembler`, `DraftReplySourceLoader`, and `ToneContextBuilder` to `buildClientForMailbox` so source reads use the same concrete mailbox as the eventual draft/send.
- Updated tests for mailbox-aware draft/audit signatures and kept privacy assertions content-free.

## Task Commits

1. **Tasks 1-4: mailbox-owned automation, audit, outbound, and source reads** - `44361cbf` (`feat(11-04): route automation through mailbox scope`)

## Files Created/Modified

- `CopyRulesService.java` - clones rules from one mailbox to another with `enabled=false` for explicit review.
- `RuleRepository.java`, `RuleManagementService.java`, `RulePreview*` - scope rule ownership/runtime/preview paths by `gmail_connection_id`.
- `TriageGmailWriter.java` - uses `buildClientForMailbox` for label/archive/draft/read/star/spam/digest actions; tenant-only methods bridge through primary mailbox.
- `GmailOutboundSendGateway.java` and `OutboundSendCommand.java` - send through the executing `MailboxRef` instead of a tenant-global Gmail client.
- `TriageAuditSaga.java`, `TriageAuditWriter.java`, `TriageAuditRepository.java`, `TriageAuditEntity.java` - persist and deduplicate by source/executing mailbox provenance.
- `TriageUndoService.java` - resolves undo writes from `triage_audit.executing_mailbox_id`.
- `ForwardMessageAssembler.java`, `DraftReplySourceLoader.java`, `ToneContextBuilder.java`, `DraftBodyGenerator.java`, `GenerateThreadDraftService.java` - pass mailbox identity through source reads and draft generation.
- `AssistantSendExecutor.java`, `UnsubscribeMailtoSender.java` - updated outbound command callers; transitional primary-mailbox shims are documented where active mailbox context does not exist yet.

## Decisions Made

- `TriageGmailWriter` and `GmailOutboundSendGateway` no longer call `buildClientForTenant`; their existing ArchUnit allow-list entries remain for Plan 11-05 to drain in the shared boundary test.
- Forward/draft source readers no longer call `buildClientForTenant`; their allow-list entries also remain for Plan 11-05.
- Transitional primary mailbox shims are intentionally limited to `AssistantSendExecutor`, `GenerateThreadDraftService`, and the tenant-only cleanup/send bridge until Plan 11-05 binds active mailbox context.
- Blocked/failed outbound behavior remains failed audit/no surprise draft; Plan 11-04 did not add draft fallback paths.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- `compileTestJava` initially failed after constructor/signature changes. Updated draft and triage tests to use `MailboxRef`, mailbox-aware `TriageAuditWriter` parameters, and typed Mockito matchers for overloaded draft methods.
- A staging pass observed a transient `.git/index.lock`; no Git process was active, the lock had already disappeared on recheck, and staging was retried without source changes.

## Verification

- `./gradlew.bat :backend:core:compileTestJava` - passed.
- `./gradlew.bat spotlessApply -q` - passed.
- `./gradlew.bat :backend:core:compileJava :backend:core:compileTestJava` - passed with existing deprecation warnings for legacy test shim calls.
- `./gradlew.bat :backend:core:test --tests "*MailboxOwnedRules*" --tests "*OutboundMailbox*"` - passed.
- `./gradlew.bat :backend:core:test --tests "*DraftPrivacyLogScrubTest" --tests "*DraftPrivacySweepTest" --tests "*GenerateThreadDraftServiceTest" --tests "*TriageAuditSagaDraftThreadingTest"` - passed.
- `rg "buildClientForTenant" TriageGmailWriter.java GmailOutboundSendGateway.java ForwardMessageAssembler.java DraftReplySourceLoader.java ToneContextBuilder.java` - no matches.
- JetBrains file problem checks - no errors for edited draft tests and mailbox writer/reader production classes.
- `git diff --name-only -- backend/core/src/test/java/com/zeromail/core/arch/GmailClientLookupBoundaryTest.java` - no output; boundary test intentionally untouched.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 11-05 can now bind `MailboxContext` / `ActiveMailboxResolver`, drain the tenant-lookup allow-list entries for migrated classes, and replace the transitional primary shims in chat/on-demand draft/default cleanup paths. Plan 11-06 can consume the copy-rules service through the API/UI surface once Plan 11-05 exposes the active-mailbox contracts.

---
*Phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification*
*Completed: 2026-06-09*
