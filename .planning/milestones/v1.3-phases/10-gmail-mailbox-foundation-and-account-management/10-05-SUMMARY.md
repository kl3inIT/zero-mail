---
phase: 10-gmail-mailbox-foundation-and-account-management
plan: 05
subsystem: auth
tags: [gmail, oauth2, spring-security, session, mailbox]

requires:
  - phase: 10-02
    provides: multi-mailbox schema and duplicate-active constraints
  - phase: 10-04
    provides: GmailConnectionService addConnection and reconnect helpers
provides:
  - Server-side OAuth intent snapshot contract
  - Callback-survival AuthorizationRequestRepository shim
  - Resolver intent stamping from pending session state only
  - Three-branch Google OAuth success handling for first-login, add-mailbox, and reconnect-mailbox
  - Failure-handler stale-intent cleanup
affects: [gmail, oauth, mailbox, account-management, phase-10-06, phase-11]

tech-stack:
  added: []
  patterns:
    - Spring Security OAuth2 authorization-request repository decorator
    - Server-side session snapshot as OAuth branch authority
    - Tenant-bound service call before transactional mailbox writes

key-files:
  created:
    - backend/api/src/main/java/com/zeromail/api/security/OAuthIntentSnapshot.java
    - backend/api/src/main/java/com/zeromail/api/security/IntentCarryingAuthorizationRequestRepository.java
  modified:
    - backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java
    - backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java
    - backend/api/src/main/java/com/zeromail/api/security/LoginRedirectAuthenticationFailureHandler.java
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
    - backend/api/src/test/java/com/zeromail/api/security/OAuthIntentRoutingTest.java
    - backend/api/src/test/java/com/zeromail/api/security/GoogleOAuthSuccessHandlerTest.java

key-decisions:
  - "OAuth branch authority is derived only from ZEROMAIL_OAUTH_PENDING_INTENT; URL intent/targetMailboxId/initiatingTenantId params carry no authority."
  - "The callback-survival key is ZEROMAIL_OAUTH_INTENT, written as a fresh OAuthIntentSnapshot during removeAuthorizationRequest to dirty Spring Session/Redis."
  - "Add-mailbox writes a CONNECTED row on OAuth callback via GmailConnectionService.addConnection; no PENDING row is created if consent is abandoned."
  - "First-login vs management is discriminated by server-side pending snapshot presence: absent snapshot means first_login, valid add/reconnect snapshot means management branch."
  - "Management callbacks with no refresh token fail closed with consent_denied instead of retrying without the consumed intent snapshot."

patterns-established:
  - "Resolver consumes and clears pending OAuth intent one-shot, then stamps OAuth2AuthorizationRequest attributes, not additionalParameters."
  - "Success handler consumes callback intent before scope/null-refresh checks so every early exit clears stale management intent."
  - "Management OAuth writes verify the live session tenant matches initiatingTenantId before binding TenantContext and calling core services."

requirements-completed: [GMA-01, GMA-04, GMA-07, WSP-01]

duration: 29min
completed: 2026-06-09
---

# Phase 10 Plan 05: OAuth Intent Routing Summary

**Server-side OAuth intent routing for first-login, add-mailbox, and reconnect-mailbox using session snapshots and callback cleanup**

## Performance

- **Duration:** 29 min
- **Started:** 2026-06-09T05:49:00Z
- **Completed:** 2026-06-09T06:18:00Z
- **Tasks:** 4 completed
- **Files modified:** 8

## Accomplishments

- Added `OAuthIntentSnapshot` with shared intent constants and the two session keys: `ZEROMAIL_OAUTH_PENDING_INTENT` and `ZEROMAIL_OAUTH_INTENT`.
- Added `IntentCarryingAuthorizationRequestRepository`, a Spring Security `AuthorizationRequestRepository` decorator that copies intent attributes into the HTTP session on callback removal using a fresh `OAuthIntentSnapshot`.
- Updated `GoogleAuthorizationRequestResolver` to read branch authority only from the server-side pending snapshot, validate the closed add/reconnect intent set, clear the pending key one-shot, and stamp attributes instead of URL parameters.
- Wired the custom repository into the user `SecurityConfig` OAuth2 authorization endpoint while preserving the single bundled `google` registration and framework-owned `state`.
- Updated `GoogleOAuthSuccessHandler` so absent/`first_login` intent keeps the existing provisioning path, `add_mailbox` inserts via `GmailConnectionService.addConnection`, and `reconnect_mailbox` updates the target via `GmailConnectionService.reconnect`.
- Added top-of-handler callback intent removal before scope and refresh-token checks, plus callback-time tenant mismatch fail-closed handling.
- Updated `LoginRedirectAuthenticationFailureHandler` to clear `ZEROMAIL_OAUTH_INTENT` on every OAuth failure path.

## Task Commits

Each task was committed atomically:

1. **Task 1: snapshot + repository shim** - `0abee5d5` (feat)
2. **Task 2: resolver stamping + SecurityConfig wiring** - `24b731ee` (feat)
3. **Task 3: success-handler intent branches** - `8689a08d` (feat)
4. **Task 4: failure-handler cleanup** - `c687a98e` (fix)

**Plan metadata:** committed with this summary.

## Files Created/Modified

- `backend/api/src/main/java/com/zeromail/api/security/OAuthIntentSnapshot.java` - Shared record, intent constants, request-attribute names, and session keys.
- `backend/api/src/main/java/com/zeromail/api/security/IntentCarryingAuthorizationRequestRepository.java` - Delegates to `HttpSessionOAuth2AuthorizationRequestRepository` and captures callback intent into session.
- `backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java` - Consumes pending server-side intent and stamps OAuth2 authorization-request attributes.
- `backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java` - Routes first-login/add/reconnect branches and validates live session tenant before management writes.
- `backend/api/src/main/java/com/zeromail/api/security/LoginRedirectAuthenticationFailureHandler.java` - Clears stale callback intent before all failure redirects.
- `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` - Wires the intent-carrying repository into `oauth2Login().authorizationEndpoint(...)`.
- `backend/api/src/test/java/com/zeromail/api/security/OAuthIntentRoutingTest.java` - Updated constructor wiring for the new service dependency.
- `backend/api/src/test/java/com/zeromail/api/security/GoogleOAuthSuccessHandlerTest.java` - Updated constructor wiring for the new service dependency.

## Decisions Made

- Kept `?reconnect=true` only as the `prompt=consent` trigger. It does not select add/reconnect and never carries `targetMailboxId`.
- Used the current `TenantContext` when bound, then the pre-callback session `SecurityContext`, to verify management callbacks still belong to the initiating tenant before writing.
- Management callbacks with missing refresh tokens throw `consent_denied` after removing the callback intent. Retrying without the consumed server-side snapshot would otherwise turn a management callback into a first-login-shaped flow.
- Compatibility aliases (`PENDING_SESSION_ATTRIBUTE`, `INTENT_SESSION_ATTRIBUTE`) reference the shared constants without duplicating session-key literals, preserving the existing Wave 0 tests.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Updated constructor-based tests for success handler dependency**
- **Found during:** Task 3 (success-handler branch implementation)
- **Issue:** Adding `GmailConnectionService` to the production constructor broke direct unit-test construction sites.
- **Fix:** Updated the two direct tests to pass a mocked `GmailConnectionService`.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/security/OAuthIntentRoutingTest.java`, `backend/api/src/test/java/com/zeromail/api/security/GoogleOAuthSuccessHandlerTest.java`
- **Verification:** Targeted OAuth intent tests passed.
- **Committed in:** `8689a08d`

---

**Total deviations:** 1 auto-fixed (1 missing critical).
**Impact on plan:** Test-only adaptation for the new production dependency; no behavior outside the planned OAuth routing surface.

## Issues Encountered

- PowerShell could not pipe UTF-16 here-strings into `apply_patch`; patches were applied through the Codex patch executable with UTF-8 patch arguments.
- `./gradlew :backend:api:check` currently fails on `MailboxOwnershipSeamTest` because Plan 10-06 controller endpoints are still intentionally missing. The 10-05-specific OAuth intent tests pass.

## Verification

- Context7 Spring Security 7 docs checked for `authorizationEndpoint().authorizationRequestRepository(...)` and the `removeAuthorizationRequest(HttpServletRequest, HttpServletResponse)` signature.
- JetBrains `get_file_problems(errorsOnly=true)` passed for all six edited production security files.
- `./gradlew :backend:api:test --tests "*IntentCarryingRepository*"` - passed.
- `./gradlew :backend:api:test --tests "*OAuthIntentRouting*" --tests "*IntentCarryingRepository*"` - passed.
- Source grep confirmed no resolver reads of `getParameter("intent")`, `getParameter("targetMailboxId")`, or `getParameter("initiatingTenantId")`.
- Source-order check confirmed `consumeCallbackIntentSnapshot(request)` occurs before the Gmail-scope exception and null-refresh redirect.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for Plan 10-06. The account-management controllers can write `OAuthIntentSnapshot` to `ZEROMAIL_OAUTH_PENDING_INTENT` and redirect to `/oauth2/authorization/google?reconnect=true`; the resolver and handlers now consume that contract.

---
*Phase: 10-gmail-mailbox-foundation-and-account-management*
*Completed: 2026-06-09*
