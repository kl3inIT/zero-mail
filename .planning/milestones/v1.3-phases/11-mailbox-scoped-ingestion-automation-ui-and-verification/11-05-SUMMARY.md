---
phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification
plan: 05
subsystem: mailbox-context-and-rules-api
tags: [gmail, mailbox-scope, servlet-filter, rules, openapi, archunit]

requires:
  - phase: 11-03
    provides: mailbox-scoped ingestion, projection, sync state, and processing jobs
  - phase: 11-04
    provides: mailbox-owned automation, audit provenance, outbound MailboxRef routing, and copy-rules core service
provides:
  - MailboxContext ScopedValue and request filter binding the active Gmail mailbox
  - per-session active-mailbox resolver and GET/PUT API
  - mailbox-aware inbox and rule preview/test reads through MailboxContext
  - active mailbox execution for chat-confirmed sends
  - specific-mailbox invalid-grant disconnect handling
  - copy-rules API surface and structured rule gmailConnectionId DTO fields
  - drained Gmail client lookup ArchUnit allow-lists to documented residual callers
affects: [11-06-web-mailbox-ui, active-mailbox, rules-api, inbox-read, outbound-send, audit]

tech-stack:
  added: []
  patterns:
    - MailboxContext mirrors TenantContext with ScopedValue<UUID> and explicit MailboxRef rebind helper.
    - Active mailbox state is a tenant-namespaced Spring Session attribute, not a request body or URL-level scope.
    - Rule ownership is explicit structured API input via gmailConnectionId, never inferred from natural language.

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/mailbox/MailboxContext.java
    - backend/core/src/main/java/com/zeromail/core/mailbox/package-info.java
    - backend/api/src/main/java/com/zeromail/api/security/ActiveMailboxResolver.java
    - backend/api/src/main/java/com/zeromail/api/security/MailboxBindingFilter.java
    - backend/api/src/main/java/com/zeromail/api/controllers/gmail/ActiveMailboxController.java
    - backend/api/src/main/java/com/zeromail/api/dto/gmail/ActiveMailboxResponse.java
    - backend/api/src/main/java/com/zeromail/api/controllers/rules/CopyRulesController.java
    - backend/api/src/main/java/com/zeromail/api/dto/rules/CopyRulesRequest.java
    - backend/api/src/main/java/com/zeromail/api/dto/rules/CopyRulesResponse.java
  modified:
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
    - backend/api/src/main/java/com/zeromail/api/security/GmailAccessGuard.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/RecentInboxReadService.java
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
    - backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java
    - backend/api/src/main/java/com/zeromail/api/controllers/rules/RulesController.java
    - backend/api/src/main/java/com/zeromail/api/dto/rules/RuleCreateRequest.java
    - backend/api/src/main/java/com/zeromail/api/dto/rules/RuleUpdateRequest.java
    - backend/api/src/main/java/com/zeromail/api/dto/rules/RuleDraftPreviewRequest.java
    - backend/api/src/main/java/com/zeromail/api/dto/rules/RuleResponse.java
    - backend/core/src/test/java/com/zeromail/core/arch/GmailClientLookupBoundaryTest.java
    - apps/web/openapi/openapi.json

key-decisions:
  - PUT /api/gmail/active-mailbox/{gmailConnectionId} returns 200 with ActiveMailboxResponse, not 204, so the client can update local mailbox state from the server's validated mailbox.
  - The active mailbox session key is active_gmail_mailbox_id::{tenantId}; cross-device stickiness remains intentionally out of scope for v1.3.
  - No connected mailbox leaves MailboxContext unbound; stale/disconnected active mailbox falls back to a connected primary/candidate and updates or clears the session attribute.
  - Rule test/message and test/messages stay body-field-free; they inherit the bound active mailbox through MailboxContext-aware read services.
  - Chat-confirmed sends use the currently bound MailboxContext in AssistantSendExecutor; the API CrossAccount test does not seed a full pending-action workflow.

requirements-completed: [ING-04, ING-06, WSP-05, WSP-06, AUD-02, AUD-05, AUD-06, AUTO-01, AUTO-02, AUTO-03, UX-03]

duration: multi-session
completed: 2026-06-09
implementation_commit: 59485e1f
---

# Phase 11 Plan 05 Summary

**Active mailbox request scope and rules API surface are in place.**

## Performance

- **Duration:** multi-session resume
- **Completed:** 2026-06-09
- **Tasks:** 3
- **Implementation commit:** `59485e1f` (`feat(phase-11): bind active mailbox scope and rules API`)

## Accomplishments

- Added `MailboxContext` as `ScopedValue<UUID>` with `currentOrThrow`, `currentOptional`, and `runWith(MailboxRef, Runnable)` for worker/automation rebinding.
- Added `ActiveMailboxResolver` and `MailboxBindingFilter`, wired after `TenantBindingFilter` in `SecurityConfig`, so request reads can bind the server-side active mailbox before controller/service work.
- Added active mailbox endpoints:
  - `GET /api/gmail/active-mailbox`
  - `PUT /api/gmail/active-mailbox/{gmailConnectionId}` returning `200 OK` with `ActiveMailboxResponse`.
- Migrated inbox/preview reads to use `MailboxContext` and `buildClientForMailbox`; `CrossAccountIsolationTest` now verifies `/api/gmail/inbox` reaches Gmail through the selected active mailbox only.
- Migrated `AssistantSendExecutor` to build its executing `MailboxRef` from `MailboxContext.currentOrThrow()` and added a direct send-executor assertion for the bound mailbox.
- Updated invalid-grant refresh handling so `OAuth2TokenRefreshFailed.gmailConnectionId()` disconnects the specific failing mailbox; legacy events without a mailbox id disconnect nothing.
- Exposed copy-rules API surface:
  - `POST /api/rules/copy`
  - `CopyRulesRequest.sourceGmailConnectionId`
  - `CopyRulesRequest.targetGmailConnectionId`
  - `CopyRulesResponse.copiedCount`
  - `CopyRulesResponse.copiedRuleIds`
- Added structured rule mailbox ownership fields:
  - `RuleCreateRequest.gmailConnectionId`
  - `RuleUpdateRequest.gmailConnectionId`
  - `RuleDraftPreviewRequest.gmailConnectionId`
  - `RuleResponse.gmailConnectionId`
- Regenerated the cached OpenAPI snapshot at `apps/web/openapi/openapi.json`; generated TypeScript client regeneration remains Plan 11-06 work.

## Final ArchUnit Residuals

`ALLOWED_TENANT_LOOKUP_CALLERS` now contains only documented legacy chat/read callers:

- `com.zeromail.api.chat.AssistantPendingActionReconciler`
- `com.zeromail.core.chat.usecases.settings.GmailSentMessagesReader`
- `com.zeromail.core.chat.usecases.tools.GetMessageToolHandler`
- `com.zeromail.core.chat.usecases.tools.GetThreadToolHandler`
- `com.zeromail.core.chat.usecases.tools.ListLabelsToolHandler`
- `com.zeromail.core.chat.usecases.tools.SearchInboxToolHandler`

`ALLOWED_PRIMARY_SHIM_CALLERS` now contains:

- `com.zeromail.api.security.ActiveMailboxResolver` - the legitimate primary fallback for active mailbox resolution.
- `com.zeromail.core.cleanup.usecases.SenderMessageReadService` - legacy cleanup read path.
- `com.zeromail.core.gmail.usecases.GmailConnectionService` - mailbox management/primary helper owner.

Removed from the allow-lists as required: `GmailDeliveryProcessingService`, `TriageGmailWriter`, `GmailOutboundSendGateway`, `ForwardMessageAssembler`, `DraftReplySourceLoader`, `ToneContextBuilder$GmailSentMailSource`, `RecentInboxReadService`, `GmailPreviewReadService`, `InboxBackfillService`, and `AssistantSendExecutor`.

## Files Created/Modified

- `MailboxContext.java`, `MailboxBindingFilter.java`, `ActiveMailboxResolver.java`, `ActiveMailboxController.java`, and `ActiveMailboxResponse.java` implement server-side active mailbox binding.
- `RecentInboxReadService.java` and `GmailPreviewReadService.java` resolve active mailbox reads from `MailboxContext`.
- `AssistantSendExecutor.java` uses the bound active mailbox for confirmed sends.
- `GmailAccessGuard.java`, `DisconnectDetectingRefreshTokenClient.java`, and `OAuth2TokenRefreshFailed.java` carry and consume a specific failing mailbox id.
- `RulesController.java`, rule request/response DTOs, and new copy-rules controller/DTOs expose mailbox ownership in the backend API.
- `GmailClientLookupBoundaryTest.java` drains both allow-lists to the final documented residuals.
- Rules/controller/privacy/cross-account/invalid-grant tests were updated for explicit mailbox ownership and active-mailbox request binding.
- `DraftSafetyEvalTest.java` and `TriageAuditSafetyNetBadgeTest.java` were updated after the IDE build surfaced stale mailbox-aware constructor/signature calls outside the targeted Gradle test source sets.

## Deviations from Plan

- The RED-era `CrossAccountIsolationTest` chat-confirm probe was not kept as an HTTP confirm call because `/api/chat/{chatId}/confirm` correctly returns `404` without a seeded pending action. The executing-mailbox send invariant is now asserted directly in `AssistantSendExecutorVipIT`; the HTTP CrossAccount test focuses on active-mailbox selection, fallback, and inbox read routing.
- `:backend:api:generateOpenApiDocs` applied pending Phase 11 Liquibase migrations to the local dev database while booting the backend. This was part of normal app startup for OpenAPI generation.

## Issues Encountered

- `CrossAccountIsolationTest` initially expected `204` for `PUT /active-mailbox`; production correctly returns `200` with `ActiveMailboxResponse`.
- The initial inbox probe returned `403` because the raw fixture lacked real Gmail OAuth grant state. The test now mocks `GmailApiClientFactory` and verifies the selected `MailboxRef` at the Gmail boundary.
- JetBrains project build compiled broader source sets and found stale aiEval/worker test fixtures after mailbox-aware constructor changes. Both were updated and verified.
- Two pre-existing formatting-only dirty files were intentionally left unstaged and uncommitted: `GmailApiClientFactory.java` and `MailboxSummaryProjection.java`.

## Verification

- `./gradlew.bat :backend:api:compileTestJava :backend:core:compileTestJava` - passed.
- `./gradlew.bat :backend:api:test --tests "*RulesController*" --tests "*CopyRules*"` - passed.
- `./gradlew.bat :backend:api:test --tests "*MailboxBinding*" --tests "*CrossAccountIsolation*"` - passed.
- `./gradlew.bat :backend:core:test --tests "*GmailClientLookupBoundary*"` - passed.
- `./gradlew.bat :backend:core:test --tests "*RulePreviewService*"` - passed.
- `./gradlew.bat :backend:api:test --tests "*DisconnectOnInvalidGrant*"` - passed.
- `./gradlew.bat :backend:core:test --tests "*AssistantSendExecutorVipIT"` - passed.
- `./gradlew.bat :backend:api:generateOpenApiDocs` - passed.
- JetBrains `build_project` - passed after stale aiEval/worker fixture updates.
- `./gradlew.bat :backend:core:compileAiEvalJava :backend:worker:test --tests "*TriageAuditSafetyNetBadgeTest"` - passed.
- `rg "findByTenantId|buildClientForTenant" RecentInboxReadService.java GmailPreviewReadService.java InboxBackfillService.java` - no matches.
- `rg "GmailDeliveryProcessingService|TriageGmailWriter|GmailOutboundSendGateway|ForwardMessageAssembler|DraftReplySourceLoader|ToneContextBuilder\$GmailSentMailSource" GmailClientLookupBoundaryTest.java` - no matches.
- `git diff --cached --check` - passed before the implementation commit.
- JetBrains file problem checks - no errors on active-mailbox, rules, read-service, send-executor, invalid-grant, aiEval, and worker test files; remaining warnings are existing style/noise inspections.

## User Setup Required

None.

## Next Phase Readiness

Plan 11-06 can build the frontend mailbox switcher and generated-type integrations from real backend surfaces: active mailbox GET/PUT, copy-rules POST, rule DTO `gmailConnectionId` fields, and cached OpenAPI output.

## Self-Check

PASSED - MailboxContext binding, active mailbox API, mailbox-scoped reads, confirmed-send mailbox execution, invalid-grant specific disconnect, rules/copy-rules API surface, ArchUnit residual allow-lists, and focused verification all satisfy Plan 11-05 acceptance criteria.

---
*Phase: 11-mailbox-scoped-ingestion-automation-ui-and-verification*
*Completed: 2026-06-09*
