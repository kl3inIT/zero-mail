---
phase: 10-gmail-mailbox-foundation-and-account-management
plan: 03
subsystem: gmail
tags: [gmail, mailbox, token-cache, archunit]

requires:
  - phase: 10-02
    provides: gmail connection id ownership lookup and multi-mailbox schema fields
provides:
  - MailboxRef value object for mailbox-scoped Gmail client lookup
  - GmailApiClientFactory buildClientForMailbox entry point
  - Gmail access-token cache keyed by gmailConnectionId
  - ArchUnit allow-list guard for legacy buildClientForTenant callers
affects: [gmail, mailbox, oauth, chat, outbound, triage, draft, api]

tech-stack:
  added: []
  patterns:
    - MailboxRef carries tenantId plus gmailConnectionId to separate cipher AAD from cache identity
    - Deprecated tenant-only Gmail lookup remains available only behind an explicit ArchUnit allow-list

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/gmail/gateway/MailboxRef.java
    - backend/core/src/test/java/com/zeromail/core/arch/GmailClientLookupBoundaryTest.java
    - backend/api/src/test/java/com/zeromail/api/arch/GmailClientLookupBoundaryTest.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java

key-decisions:
  - "GmailApiClientFactory access-token cache is now keyed by gmailConnectionId while AES-GCM decrypt AAD remains tenantId.toString()."
  - "buildClientForTenant is deprecated for removal and fails loud when a tenant has more than one CONNECTED mailbox."
  - "The live tenant-only caller allow-list contains 12 production classes; E2eStubGmailApiClientFactory was excluded because it only declares stub methods."

patterns-established:
  - "Mailbox-scoped Gmail client calls use buildClientForMailbox(MailboxRef)."
  - "API-module ArchUnit mirror asserts the allow-list equals actual core+api production callers."

requirements-completed: [WSP-06, AUD-04]

duration: 24min
completed: 2026-06-09
---

# Phase 10 Plan 03: Gmail Mailbox Client Lookup Summary

**Mailbox-scoped Gmail client factory with connection-id token caching and legacy tenant lookup guardrails**

## Performance

- **Duration:** 24 min
- **Started:** 2026-06-09T05:00:32Z
- **Completed:** 2026-06-09T05:24:22Z
- **Tasks:** 3 completed
- **Files modified:** 4

## Accomplishments

- Added `MailboxRef(UUID tenantId, UUID gmailConnectionId)` as the typed mailbox identity for Gmail client lookup.
- Added `GmailApiClientFactory.buildClientForMailbox(MailboxRef)` and request-timeout overload.
- Re-keyed `accessTokenCache` get, put, and invalid-grant remove operations from tenant id to `gmailConnection.getId()`.
- Preserved refresh-token decrypt AAD as `tenantId.toString()`.
- Marked `buildClientForTenant(...)` deprecated for removal and made it throw when more than one CONNECTED mailbox exists for a tenant.
- Added ArchUnit guardrails for tenant-only lookup, including an API-module mirror that can see both API and core production classes.

## Task Commits

Each task was committed atomically:

1. **Task 1: MailboxRef value object** - `6176a14f` (feat)
2. **Task 2: GmailApiClientFactory mailbox cache re-key** - `0862539a` (feat)
3. **Task 3: tenant-only lookup ArchUnit guard** - `0c8c0e56` (test)

**Plan metadata:** committed with this summary.

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/gmail/gateway/MailboxRef.java` - Carries tenant id and Gmail connection id together for mailbox-scoped client construction.
- `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java` - Adds mailbox lookup, connection-id cache keying, connected-grant validation, and deprecated tenant adapter guard.
- `backend/core/src/test/java/com/zeromail/core/arch/GmailClientLookupBoundaryTest.java` - Mirrors the existing core ArchUnit boundary pattern for tenant-only lookup.
- `backend/api/src/test/java/com/zeromail/api/arch/GmailClientLookupBoundaryTest.java` - Enforces the same boundary from the API module classpath and asserts no stale/missing allow-list entries.

## Final ALLOWED_TENANT_LOOKUP_CALLERS

Verified with `rg -n "\.buildClientForTenant\(" backend/core/src/main backend/api/src/main` on 2026-06-09:

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

The live grep found 13 call sites in these 12 classes. `E2eStubGmailApiClientFactory` was not included because the grep hit its method declarations, not a call to `GmailApiClientFactory.buildClientForTenant`.

## Decisions Made

- Added an API-module mirror ArchUnit test because the core module test classpath does not reliably include API module classes; without it, the intended app-wide guard would miss API-tier callers.
- Refactored the no-timeout `buildClientForTenant(UUID)` overload through a private helper so the boundary rule does not need to allow-list the factory's own overload delegation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added API-module mirror for app-wide enforcement**
- **Found during:** Task 3 (GmailClientLookupBoundaryTest ArchUnit allow-list rule)
- **Issue:** A core-module ArchUnit test can enforce core callers but cannot reliably see backend/api classes from its module classpath, despite the plan requiring app-wide coverage.
- **Fix:** Added `backend/api/src/test/java/com/zeromail/api/arch/GmailClientLookupBoundaryTest.java` to import API + core production classes and assert the live caller set equals the allow-list.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/arch/GmailClientLookupBoundaryTest.java`
- **Verification:** `./gradlew :backend:core:compileJava :backend:api:compileJava` passed; API test execution is deferred until Plan 10-05 removes the planned test-compile blocker.
- **Committed in:** `0c8c0e56`

**2. [Rule 3 - Blocking] Removed factory self-call before enforcing the boundary**
- **Found during:** Task 3 (GmailClientLookupBoundaryTest ArchUnit allow-list rule)
- **Issue:** The deprecated no-timeout overload called the deprecated timeout overload directly, which would create an internal `buildClientForTenant` method call unrelated to legacy consumer migration.
- **Fix:** Routed both deprecated overloads through a private `buildClientForSingleConnectedTenant(...)` helper.
- **Files modified:** `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java`
- **Verification:** `./gradlew :backend:core:compileJava :backend:api:compileJava` passed; live grep still reports only the 12 production caller classes above.
- **Committed in:** `0c8c0e56`

---

**Total deviations:** 2 auto-fixed (1 missing critical, 1 blocking).
**Impact on plan:** Both fixes strengthen the intended boundary without changing runtime behavior.

## Issues Encountered

- `./gradlew :backend:core:test --tests "*GmailApiClientFactoryMailboxCache*"` is still blocked at `compileTestJava` by the planned future symbol `GmailConnectionService.setPrimary(UUID, UUID)` from Plan 10-04.
- `./gradlew :backend:core:compileJava :backend:api:compileJava` passed after all changes.
- Deprecation warnings now appear at the explicit legacy `buildClientForTenant` callers; these are intentional migration pressure for Phase 11.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 10-04. The mailbox client factory now has a mailbox-typed entry point, and legacy tenant lookup is visible and bounded by architecture tests.

---
*Phase: 10-gmail-mailbox-foundation-and-account-management*
*Completed: 2026-06-09*
