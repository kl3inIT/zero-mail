---
phase: 10-gmail-mailbox-foundation-and-account-management
plan: 06
subsystem: api
tags: [gmail, mailbox, oauth2, spring-mvc, account-management]

requires:
  - phase: 10-02
    provides: multi-mailbox schema, typed mailbox ids, and ownership lookup persistence
  - phase: 10-04
    provides: mailbox summary projection, ownership resolvers, set-primary, disconnect, add, and reconnect service methods
  - phase: 10-05
    provides: server-side OAuthIntentSnapshot pending-intent contract consumed by the Google OAuth resolver
provides:
  - Metadata-only mailbox summary API response
  - Connected mailbox list, set-primary, and disconnect REST endpoints
  - Add-mailbox and reconnect OAuth trigger endpoints using server-side pending-intent snapshots
  - Fail-closed mailbox-scoped API seam tests aligned to current error mapping
affects: [gmail, mailbox, oauth, account-management, phase-11]

tech-stack:
  added: []
  patterns:
    - Thin Spring MVC controllers delegating to GmailConnectionService
    - Server-side HttpSession OAuth intent snapshot before redirect
    - Metadata-only DTO mapping from core projections

key-files:
  created:
    - backend/api/src/main/java/com/zeromail/api/dto/gmail/MailboxSummaryResponse.java
    - backend/api/src/main/java/com/zeromail/api/controllers/gmail/ConnectMailboxController.java
    - backend/api/src/main/java/com/zeromail/api/controllers/gmail/ConnectedMailboxesController.java
  modified:
    - backend/api/src/test/java/com/zeromail/api/controllers/gmail/MailboxOwnershipSeamTest.java

key-decisions:
  - "Mailbox account-management routes live under /api/gmail/mailboxes with typed UUID path variables; no mailbox id is accepted through a query/header seam."
  - "Add/reconnect OAuth branch authority is stored only in OAuthIntentSnapshot under ZEROMAIL_OAUTH_PENDING_INTENT before redirecting to the bundled Google OAuth route."
  - "Compatibility aliases POST /{gmailConnectionId}/set-primary and POST /{gmailConnectionId}/reconnect remain for the existing Wave 0 seam tests while the plan-preferred /primary and GET reconnect routes are also present."
  - "OpenAPI and frontend client regeneration remain deferred to Phase 11; no generated schema files were touched."

patterns-established:
  - "Controller-owned behavior is limited to TenantContext lookup, path/session binding, and service delegation; repository access stays out of API controllers."
  - "Redirect-trigger tests that assert the initial OAuth 302 use JdkClientHttpRequestFactory with HttpClient.Redirect.NEVER so the client does not follow into Spring Security."

requirements-completed: [GMA-01, GMA-02, GMA-03, GMA-04, GMA-05, WSP-04, WSP-05, WSP-06, AUD-04]

duration: 18min
completed: 2026-06-09
---

# Phase 10 Plan 06: Gmail Account-Management API Summary

**Mailbox account-management REST endpoints with metadata-only summaries and server-side OAuth intent triggers**

## Performance

- **Duration:** 18 min
- **Started:** 2026-06-09T06:19:00Z
- **Completed:** 2026-06-09T06:37:00Z
- **Tasks:** 3 completed
- **Files modified:** 4

## Accomplishments

- Added `MailboxSummaryResponse`, mapping `MailboxSummaryProjection` into metadata-only API fields with OpenAPI required/nullable annotations.
- Added add-mailbox and reconnect OAuth trigger endpoints that write a fresh `OAuthIntentSnapshot` into the HTTP session under `ZEROMAIL_OAUTH_PENDING_INTENT`, then redirect to `/oauth2/authorization/google?reconnect=true` with no branch-bearing query parameters.
- Added connected mailbox management endpoints for list, set-primary, and disconnect through thin controller delegation to `GmailConnectionService`.
- Updated `MailboxOwnershipSeamTest` to assert the current Phase 10-04 error codes and to inspect the initial reconnect 302 without following the OAuth redirect.

## Endpoint Surface

- `GET /api/gmail/mailboxes` - list connected mailbox summaries; metadata only.
- `GET /api/gmail/mailboxes/connect` - start add-mailbox OAuth by storing `add_mailbox` pending intent in the server-side session.
- `GET /api/gmail/mailboxes/{gmailConnectionId}/reconnect` - start reconnect OAuth after fail-closed reconnectable ownership pre-resolution.
- `POST /api/gmail/mailboxes/{gmailConnectionId}/reconnect` - validation compatibility alias for reconnect OAuth trigger.
- `POST /api/gmail/mailboxes/{gmailConnectionId}/primary` - set the owned connected mailbox as primary.
- `POST /api/gmail/mailboxes/{gmailConnectionId}/set-primary` - validation compatibility alias for set-primary.
- `POST /api/gmail/mailboxes/{gmailConnectionId}/disconnect` - disconnect one owned mailbox via `MailboxRef`.

## Task Commits

Each task was committed atomically:

1. **Task 1: MailboxSummaryResponse DTO** - `33f35e55` (feat)
2. **Task 2: ConnectMailboxController OAuth trigger endpoints** - `16c91dfb` (feat)
3. **Task 3: ConnectedMailboxesController and seam test closure** - `47f4121e` (feat)

**Plan metadata:** committed with this summary.

## Files Created/Modified

- `backend/api/src/main/java/com/zeromail/api/dto/gmail/MailboxSummaryResponse.java` - Metadata-only mailbox list response with `from(MailboxSummaryProjection)`.
- `backend/api/src/main/java/com/zeromail/api/controllers/gmail/ConnectMailboxController.java` - Add/reconnect OAuth trigger controller using server-side pending intent snapshots.
- `backend/api/src/main/java/com/zeromail/api/controllers/gmail/ConnectedMailboxesController.java` - List, set-primary, and disconnect mailbox management endpoints.
- `backend/api/src/test/java/com/zeromail/api/controllers/gmail/MailboxOwnershipSeamTest.java` - API seam coverage for missing/not-owned ids, non-connected status handling, and reconnect reachability.

## Decisions Made

- Kept `?reconnect=true` solely as the prompt-consent trigger; add vs reconnect is selected only by the server-side `OAuthIntentSnapshot`.
- Used `resolveReconnectableConnectionOrThrow(...)` before storing reconnect intent so not-owned ids fail closed before any OAuth redirect while DISCONNECTED owned rows remain reachable for repair.
- Returned `204 No Content` for set-primary and disconnect commands because the service owns the state change and the UI can refresh mailbox summaries.
- Added route aliases for the existing Wave 0 validation contract without removing the plan-preferred routes.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Updated stale seam-test error code expectations**
- **Found during:** Task 3 (`MailboxOwnershipSeamTest` verification)
- **Issue:** The test still expected `error.gmail.mailbox.not_owned` and `error.gmail.mailbox.disconnected`, but Plan 10-04 mapped the current service errors to `error.gmail.mailbox.not_found` and `error.gmail.disconnected`.
- **Fix:** Updated the seam test to assert the canonical shared error codes.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/controllers/gmail/MailboxOwnershipSeamTest.java`
- **Verification:** `./gradlew :backend:api:test --tests "*MailboxOwnership*"` passed.
- **Committed in:** `47f4121e`

**2. [Rule 2 - Missing Critical] Disabled redirect following for reconnect OAuth trigger assertion**
- **Found during:** Task 3 (`MailboxOwnershipSeamTest` verification)
- **Issue:** The RestClient followed `/oauth2/authorization/google?reconnect=true` and observed the downstream secured route instead of the controller's initial 302.
- **Fix:** Reused the existing no-redirect JDK HttpClient pattern from `CorsIntegrationTest` in the authenticated seam-test client.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/controllers/gmail/MailboxOwnershipSeamTest.java`
- **Verification:** `./gradlew :backend:api:test --tests "*MailboxOwnership*"` passed and the reconnect response now asserts 302 + Location.
- **Committed in:** `47f4121e`

---

**Total deviations:** 2 auto-fixed (2 missing critical).
**Impact on plan:** Test-only corrections aligned the validation seam with already-shipped service behavior and preserved the planned production contract.

## Issues Encountered

- A PowerShell acceptance-grep command failed because double-quoted regex groups were parsed as shell tokens; reran the grep with single-quoted regex and confirmed the controller does not read or emit branch-bearing OAuth URL parameters.
- No OpenAPI or frontend code generation was run. Phase 11 owns generated client/schema updates.

## Verification

- JetBrains `get_file_problems(errorsOnly=true)` passed for `MailboxSummaryResponse`, `ConnectMailboxController`, `ConnectedMailboxesController`, and `MailboxOwnershipSeamTest`.
- `./gradlew :backend:api:test --tests "*MailboxOwnership*"` - passed.
- `./gradlew :backend:api:test --tests "*OAuthIntentRouting*" --tests "*IntentCarryingRepository*" --tests "*MailboxOwnership*"` - passed.
- `./gradlew :backend:api:check` - passed.
- Grep confirmed no `request.getParameter("intent")`, `request.getParameter("targetMailboxId")`, `request.getParameter("initiatingTenantId")`, `intent=`, or `targetMailboxId=` in `ConnectMailboxController`.
- Grep confirmed `ConnectMailboxController` uses `resolveReconnectableConnectionOrThrow(...)` before reconnect redirect preparation and writes the pending intent with `setAttribute(...)`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Phase 11. Backend mailbox account-management endpoints and OAuth trigger seams are present; Phase 11 can regenerate OpenAPI/frontend clients and build the mailbox switcher UI on top of these routes.

---
*Phase: 10-gmail-mailbox-foundation-and-account-management*
*Completed: 2026-06-09*
