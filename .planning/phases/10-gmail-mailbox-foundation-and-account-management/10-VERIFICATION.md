---
phase: 10-gmail-mailbox-foundation-and-account-management
verified: 2026-06-09T07:12:00Z
status: passed
score: 10/10 must-haves verified
overrides_applied: 0
warnings:
  - "A broad :backend:core:check run failed after targeted phase checks passed, with many unrelated SQL grammar failures such as missing pubsub_delivery and gmail_connections.created_at in broad-suite contexts. The same phase-owned core tests pass when run directly; this is recorded as residual broad-suite test-environment risk, not a Phase 10 goal gap."
---

# Phase 10: Gmail Mailbox Foundation and Account Management Verification Report

**Phase Goal:** Convert one-Gmail-per-tenant into a workspace-owned multi-Gmail mailbox model where business configuration is shared at workspace level and mail automation is isolated per active mailbox.
**Verified:** 2026-06-09T07:12:00Z
**Status:** passed
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Existing tenants are represented as workspaces without changing login/session semantics. | VERIFIED | Phase 10 kept the bundled Google login route and first-login provisioning path; OAuth management branches are selected only when a pending intent exists. `GoogleOAuthSuccessHandler` still uses the existing provisioning path for absent/`first_login` intent. |
| 2 | The schema no longer enforces one Gmail per tenant and supports multiple active mailbox rows safely. | VERIFIED | Changeset `119-gmail-connections-multi-mailbox.yaml` drops the tenant-unique invariant, adds `is_primary`, `display_purpose`, `uq_gmail_conn_active_email`, and `uq_gmail_conn_primary`, with rollback. `Migration119Test`, `DuplicateActiveEmailTest`, and `SetPrimaryTransactionalTest` pass in the phase-owned targeted run. |
| 3 | Existing and new mailbox rows have stable mailbox identifiers for mailbox-scoped state. | VERIFIED | `GmailConnectionEntity.id` remains the mailbox id, `GmailConnectionRepository.findByIdAndTenantId(...)` exists, and `MailboxRef(UUID tenantId, UUID gmailConnectionId)` is used for mailbox-scoped Gmail operations. |
| 4 | Gmail client lookup and token caching are mailbox-aware, not tenant-wide. | VERIFIED | `GmailApiClientFactory.buildClientForMailbox(MailboxRef)` resolves by `(gmailConnectionId, tenantId)`; `accessTokenCache` get/remove/put calls are keyed by `gmailConnectionId`; legacy tenant lookup is deprecated and covered by `GmailClientLookupBoundaryTest`. |
| 5 | Mailbox-scoped operations fail closed on missing/not-owned/disconnected ids. | VERIFIED | `GmailConnectionService.resolveOwnedConnectionOrThrow(...)` and `resolveReconnectableConnectionOrThrow(...)` both use `findByIdAndTenantId`; `MailboxOwnershipSeamTest` verifies 404 for missing/not-owned, 409 for non-connected set-primary targets, and reconnect reachability for a disconnected owned mailbox. |
| 6 | Set-primary and disconnect state changes are service-owned and mailbox-scoped. | VERIFIED | `GmailConnectionService.setPrimary(...)` clears prior primary rows before setting the requested mailbox; `disconnect(MailboxRef)` uses mailbox-scoped service logic and calls `GmailApiClientFactory.buildClientForMailbox(...)`. Targeted core tests for set-primary and disconnect passed. |
| 7 | OAuth distinguishes first-login, add-mailbox, and reconnect without branch-bearing URL parameters. | VERIFIED | `OAuthIntentSnapshot` defines `add_mailbox`, `reconnect_mailbox`, `ZEROMAIL_OAUTH_PENDING_INTENT`, and `ZEROMAIL_OAUTH_INTENT`; `GoogleAuthorizationRequestResolver` reads and clears only the server-side pending snapshot; `IntentCarryingAuthorizationRequestRepository` carries callback attributes through session; grep found no `intent=`/`targetMailboxId=` URL branch params. |
| 8 | Connected mailbox REST endpoints are exposed under typed mailbox paths. | VERIFIED | `ConnectedMailboxesController` exposes `GET /api/gmail/mailboxes`, `POST /{gmailConnectionId}/primary`, `POST /{gmailConnectionId}/set-primary`, and `POST /{gmailConnectionId}/disconnect`; `ConnectMailboxController` exposes `GET /connect`, `GET /{gmailConnectionId}/reconnect`, and `POST /{gmailConnectionId}/reconnect`. Path variables are typed `UUID`. |
| 9 | Account-management list/status responses are metadata-only. | VERIFIED | `MailboxSummaryProjection` is documented metadata-only and excludes OAuth ciphertext; `MailboxSummaryResponse` maps only id, email, display purpose, status, primary flag, watch expiry, ingestion health, history id, and connected-at. Grep found no refresh-token, prompt, completion, or body fields in the DTO/projection. |
| 10 | Architectural boundaries are enforced after Phase 10 changes. | VERIFIED | `GmailClientLookupBoundaryTest` constrains legacy tenant-only Gmail lookup; `ThreadModuleScenarioTest` initially exposed the new Gmail exception dependency on `shared :: exception`, and commit `87a1a75d` added that allowed dependency. The targeted Modulith scenario passes after the fix. |

**Score:** 10/10 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/core/src/main/resources/db/changelog/changes/119-gmail-connections-multi-mailbox.yaml` | Multi-mailbox Liquibase migration | VERIFIED | Contains duplicate-active and primary partial indexes, `is_primary`, `display_purpose`, deterministic backfill, and rollback. |
| `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java` | Entity fields for primary/display purpose and audit compatibility | VERIFIED | Contains `is_primary` and `display_purpose` mappings; existing audit fields remain part of the entity. |
| `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java` | Ownership lookup | VERIFIED | Defines `findByIdAndTenantId(UUID id, UUID tenantId)`. |
| `backend/core/src/main/java/com/zeromail/core/gmail/gateway/MailboxRef.java` | Typed mailbox reference | VERIFIED | Record carries tenant id and Gmail connection id. |
| `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java` | Mailbox-aware Gmail client factory | VERIFIED | Provides `buildClientForMailbox(...)` and re-keys token cache by mailbox id. |
| `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java` | Ownership/state machine service | VERIFIED | Contains ownership resolvers, set-primary, list, disconnect, add, and reconnect helpers. |
| `backend/api/src/main/java/com/zeromail/api/security/OAuthIntentSnapshot.java` | OAuth branch snapshot contract | VERIFIED | Defines intent names, request attribute names, and session keys. |
| `backend/api/src/main/java/com/zeromail/api/security/IntentCarryingAuthorizationRequestRepository.java` | Callback-survival intent shim | VERIFIED | Implements `removeAuthorizationRequest(...)` and fresh session `setAttribute(...)` behavior. |
| `backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java` | Server-side intent stamping | VERIFIED | Reads `ZEROMAIL_OAUTH_PENDING_INTENT`, clears it, validates allowed management intents, and stamps attributes. |
| `backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java` | First-login/add/reconnect callback branching | VERIFIED | Calls `addConnection(...)` and `reconnect(...)` for management intents, with tenant validation. |
| `backend/api/src/main/java/com/zeromail/api/dto/gmail/MailboxSummaryResponse.java` | Metadata-only response DTO | VERIFIED | Record maps from `MailboxSummaryProjection` and has OpenAPI required/nullable annotations. |
| `backend/api/src/main/java/com/zeromail/api/controllers/gmail/ConnectMailboxController.java` | Add/reconnect OAuth trigger API | VERIFIED | Stores pending intent snapshot before redirecting to `/oauth2/authorization/google?reconnect=true`. |
| `backend/api/src/main/java/com/zeromail/api/controllers/gmail/ConnectedMailboxesController.java` | Mailbox list/set-primary/disconnect API | VERIFIED | Thin controller delegates to `GmailConnectionService` and uses `TenantContext.currentTenantUuid()`. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `GmailConnectionService.resolveOwnedConnectionOrThrow` | `GmailConnectionRepository.findByIdAndTenantId` | Ownership lookup | VERIFIED | Code search shows direct repository lookup at the resolver. |
| `GmailConnectionService.disconnect(MailboxRef)` | `GmailApiClientFactory.buildClientForMailbox` | Mailbox-scoped `users.stop` | VERIFIED | Code search shows disconnect calls `buildClientForMailbox(mailboxRef).users().stop("me")`. |
| `GmailApiClientFactory` | `accessTokenCache` | `gmailConnectionId` cache key | VERIFIED | Code search shows cache get/remove/put keyed by `gmailConnectionId`. |
| `ConnectMailboxController` | `OAuthIntentSnapshot.PENDING_INTENT_SESSION_ATTRIBUTE` | Fresh `setAttribute(...)` before redirect | VERIFIED | Code search shows session storage before OAuth redirect; negative grep found no branch-bearing URL params. |
| `ConnectedMailboxesController` | `GmailConnectionService` | Thin controller delegation | VERIFIED | Controller injects only `GmailConnectionService`; no repository injection. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `MailboxSummaryResponse` | mailbox summary fields | `GmailConnectionService.listMailboxes(...)` -> `MailboxSummaryProjection` | Yes | VERIFIED |
| `ConnectedMailboxesController` | list response | `connectionService.listMailboxes(tenantId).stream().map(MailboxSummaryResponse::from)` | Yes | VERIFIED |
| `ConnectMailboxController` | OAuth intent | `TenantContext.currentTenantUuid()` plus typed path `UUID gmailConnectionId` | Yes | VERIFIED |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| OAuth intent routing and mailbox ownership API seams pass together | `./gradlew :backend:api:test --tests "*OAuthIntentRouting*" --tests "*IntentCarryingRepository*" --tests "*MailboxOwnership*"` | BUILD SUCCESSFUL | PASS |
| Backend API module passes with Phase 10 endpoints and boundary tests | `./gradlew :backend:api:check` | BUILD SUCCESSFUL after `87a1a75d` | PASS |
| Phase-owned core mailbox tests pass | `./gradlew :backend:core:test --tests "*Migration119*" --tests "*DuplicateActiveEmail*" --tests "*SetPrimary*" --tests "*RefreshTokenCipherContinuity*" --tests "*GmailApiClientFactoryMailboxCache*" --tests "*GmailClientLookupBoundary*" --tests "*GmailConnectionServiceDisconnect*"` | BUILD SUCCESSFUL | PASS |
| Modulith scenario passes after Gmail shared-exception boundary fix | `./gradlew :backend:core:test --tests "*ThreadModuleScenarioTest*"` | BUILD SUCCESSFUL | PASS |
| Full core suite broad check | `./gradlew :backend:core:check` and `./gradlew :backend:core:check --max-workers=1` | Both failed with broad SQL grammar/schema-missing failures such as missing `pubsub_delivery` and `gmail_connections.created_at`; same phase-owned tests pass directly | WARNING |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| Schema drift | `gsd-tools query verify.schema-drift 10` | `drift_detected: false`, `blocking: false` | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| WSP-01 | 10-05 | Existing tenant represented as workspace without login/session change | SATISFIED | First-login branch remains default when no management intent exists. |
| WSP-02 | 10-02 | Existing one-Gmail data migrates to one primary mailbox preserving state | SATISFIED | Changeset 119 adds primary marker/backfill without token ciphertext rewrite. |
| WSP-03 | 10-03 | Stable Gmail mailbox identifier on mailbox-scoped state | SATISFIED | `gmail_connections.id` is used as `gmailConnectionId` and `MailboxRef.gmailConnectionId`. |
| WSP-04 | 10-04, 10-06 | Distinguish workspace/user/mailbox in backend APIs/logs | SATISFIED | Typed `/api/gmail/mailboxes/{gmailConnectionId}` routes and mailbox-specific service methods. |
| WSP-05 | 10-04, 10-06 | Fail closed on missing/invalid/disconnected/not-owned mailbox id | SATISFIED | `MailboxOwnershipSeamTest` passes for 404/409 matrix. |
| WSP-06 | 10-03, 10-04, 10-06 | Shared backend guard/context validates `(tenantId, gmailMailboxId)` ownership | SATISFIED | Ownership resolvers and mailbox-aware client factory use tenant+mailbox id. |
| WSP-07 | 10-02, 10-04 | Workspace-level vs mailbox-level state boundary | SATISFIED | Gmail OAuth/watch/history/status fields remain on mailbox rows; business config unchanged. |
| GMA-01 | 10-05, 10-06 | Connect additional Gmail without replacing existing mailbox | SATISFIED | Add-mailbox intent calls `GmailConnectionService.addConnection(...)`. |
| GMA-02 | 10-04, 10-06 | View connected mailboxes with metadata | SATISFIED | `GET /api/gmail/mailboxes` returns `MailboxSummaryResponse`. |
| GMA-03 | 10-04, 10-06 | Choose primary/default mailbox | SATISFIED | `POST /primary` delegates to transactional `setPrimary(...)`. |
| GMA-04 | 10-05, 10-06 | Reconnect one mailbox without touching others | SATISFIED | Reconnect intent carries `targetMailboxId` server-side and calls `reconnect(...)`. |
| GMA-05 | 10-04, 10-06 | Disconnect one mailbox only | SATISFIED | `POST /disconnect` builds `MailboxRef` and service disconnect is mailbox-scoped. |
| GMA-06 | 10-02, 10-04 | Prevent duplicate active Gmail address | SATISFIED | Partial unique index and friendly exception mapping are present; targeted test passes. |
| GMA-07 | 10-05 | Separate first-login/add/reconnect OAuth flows | SATISFIED | Resolver/repository/success-handler intent split is implemented and tested. |
| AUD-04 | 10-04, 10-06 | Metadata-only multi-mailbox health | SATISFIED | DTO/projection exclude token ciphertext, bodies, prompts, and completions. |
| VER-01 | 10-02 | Roll-forward Liquibase migration with old single-account coverage | SATISFIED | Changeset 119 has rollback and `Migration119Test` passes in targeted run. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java` | 72, 107, 112, 117, 121 | `return null` | INFO | Framework contract / invalid snapshot fallback, not a stub. |
| `backend/api/src/main/java/com/zeromail/api/security/IntentCarryingAuthorizationRequestRepository.java` | 46, 74, 82, 94, 103, 106 | `return null` | INFO | Spring Security repository contract, not an incomplete implementation. |
| `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java` | 580, 595 | `return null` | INFO | Optional constraint-name extraction helper returns null when no constraint name is available, not user-visible output. |

No blocking `TBD`, `FIXME`, or `XXX` debt markers were found in Phase 10 modified source areas.

### Human Verification Required

None. Phase 10 is backend schema/service/API foundation only; live Gmail OAuth consent can be exercised in Phase 11 UI/end-to-end verification.

### Gaps Summary

No Phase 10 goal gaps found. The only unresolved verification concern is the broad `:backend:core:check` database-schema/test-order failure described in frontmatter warnings; it did not reproduce in the phase-owned targeted core checks and is not a missing Phase 10 deliverable.

---

_Verified: 2026-06-09T07:12:00Z_
_Verifier: the agent (gsd-verifier inline)_
