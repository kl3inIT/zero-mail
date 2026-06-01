---
phase: 09-user-settings-ui-on-curated-catalog
plan: 03
subsystem: safety-net-audit
wave: 1
tags: [triage, safety-net, audit, api, settings]
requires: [09-01]
provides:
  - Safety Net DOMAIN pattern opt-in support
  - Tenant-scoped Safety Net DELETE authority rules
  - Safety Net matcher returning matched patterns for audit badges
  - Triage audit API exposure for blockedBySafetyNetPattern
affects: [sender-safety-net, triage-audit, audit-api, outbound-runtime-gates]
key-decisions:
  - "SafetyNetMatcher lives in core.triage.usecases because the active triage execution path is shared through core TriageOrchestratorService, not a worker-only TriageDecisionEngine class."
  - "Safety Net outbound blocking is tracked separately from the nullable matched pattern so malformed/missing sender guards still block sends without inventing a fake audit badge pattern."
  - "The existing ProtectedSendersResponse nested DTO was extended instead of adding parallel ProtectedSender* files that do not exist in the current API shape."
requirements-completed:
  - SET-SAFE-01
  - SET-SAFE-04
duration: 92min
completed: 2026-05-27
---

# Phase 09-03: Safety Net Pattern + Audit Badge Summary

**DOMAIN safety-net patterns, protected-sender delete authority, and audit badge API wiring**

## Performance

- **Duration:** 92 min
- **Started:** 2026-05-26T18:58:00Z
- **Completed:** 2026-05-26T20:30:00Z
- **Tasks:** 3
- **Files modified:** 23

## Accomplishments

- Extended sender Safety Net opt-in to canonicalize both EMAIL (`ceo@acme.com`) and DOMAIN (`@acme.com`) patterns, persist `pattern_kind`, and return the new response shape needed by the frontend.
- Added `DELETE /api/triage/sender-safety-net/{id}` with tenant-opaque 404 for cross-tenant rows and 403 `safety_net.observation_not_deletable` for system-observed entries.
- Added `SafetyNetMatcher` with exact EMAIL matching first and DOMAIN suffix matching anchored on `@domain`, including negative coverage for substring traps such as `ceo@notacme.evil.com` and `acme.com@evil.com`.
- Wired matched Safety Net patterns into triage audit persistence through pending and terminal insert paths, then exposed `blockedBySafetyNetPattern` through `AuditLogRow` and `AuditEntryResponse`.
- Preserved existing outbound safety behavior by keeping missing/malformed sender states as send-blocking while leaving the nullable audit badge pattern empty when there is no actual matched pattern.

## Task Commits

1. **Tasks 1-3: Safety Net DOMAIN/DELETE + audit badge API wiring** - `834a0e6e` (`feat(09-03): expose safety net pattern badges`)

## Key Files

- `SenderEmailCanonicalizer` - recognizes EMAIL vs DOMAIN patterns and rejects malformed pattern input for HTTP 400 mapping.
- `SenderSafetyNetService` - upserts user-created patterns, invalidates EMAIL cache entries, lists protected observations, and enforces delete authority.
- `SafetyNetMatcher` - returns the matched EMAIL or DOMAIN pattern for audit badge population.
- `TriageOrchestratorService` - asks the safety net for the matched pattern and passes it into outbound fallback audit commands.
- `TriageAuditWriter` / `TriageAuditRepository` / `TriageAuditSaga` - persist nullable `blocked_by_safety_net_pattern` across pending and terminal audit insert paths.
- `AuditLogReadRepository` / `AuditLogRow` / `AuditEntryResponse` - expose `blockedBySafetyNetPattern` over the triage audit API response path.
- `SenderSafetyNetDomainPatternTest`, `SenderSafetyNetDeleteAuthorityTest`, `TriageSafetyNetDomainMatchTest`, `TriageAuditSafetyNetBadgeTest`, and `AuditEntryResponseSafetyNetFieldTest` - cover the phase acceptance path.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Plan referenced worker-only classes that do not exist in the active execution path**
- **Found during:** Task 2 implementation
- **Issue:** The plan referenced `backend/worker/src/main/java/com/zeromail/worker/triage/TriageDecisionEngine.java` and a worker `SafetyNetMatcher`; the actual outbound triage logic lives in core `TriageOrchestratorService` and is reused by worker tests.
- **Fix:** Implemented `SafetyNetMatcher` in `core.triage.usecases` and wired the matched pattern through `TriageOrchestratorService` and `TriageAuditSaga`.
- **Committed in:** `834a0e6e`

**2. [Rule 3 - Blocking] Pattern-as-boolean refactor initially risked bypassing missing sender safety**
- **Found during:** Diff review before broad verification
- **Issue:** Replacing the previous `senderProtected` boolean with a nullable pattern could let a `null` sender avoid the outbound Safety Net fallback.
- **Fix:** Added a `SafetyNetEvaluation` record carrying both `blocksOutboundSend` and nullable `matchedPattern`, plus a regression test for missing sender fallback.
- **Committed in:** `834a0e6e`

**3. [Tooling] API tests initially double-encoded `@` in RestClient URI strings**
- **Found during:** Focused API verification
- **Issue:** Tests passed `%40` inside a URI string, producing HTTP 400 from canonicalization instead of exercising decoded `@` path variables.
- **Fix:** Built explicit encoded `URI` instances from raw test patterns and kept the production controller unchanged.
- **Committed in:** `834a0e6e`

### Deferred Scope

- OpenAPI TypeScript regeneration for `apps/web/lib/api/schema.d.ts` is intentionally left to 09-06 Task 1, which depends on this backend API shape.
- Bulk paste/import for Safety Net entries remains deferred to SET-SAFE-02 as planned.

**Total deviations:** 3 auto-fixed, 2 deferred scope notes.
**Impact on plan:** SET-SAFE-01 and SET-SAFE-04 are complete on the backend/API path; frontend consumption waits for 09-06 schema regeneration.

## Issues Encountered

- JetBrains MCP timed out immediately after the IntelliJ restart, so early inspection and test-report analysis fell back to Gradle/PowerShell. JetBrains MCP recovered later; file inspections and IDE build validation were run before close-out.
- Broad `*SafetyNet*/*Triage*/*AuditEntryResponse*` verification first exposed the existing source-contract expectation that opt-in email logs include `senderEmailHash`; the hashed log line was restored without logging raw domain patterns.
- No Docker/local DB files were added after the user clarified this was the wrong project for that request.

## Verification

- `./gradlew.bat :backend:api:test --tests SenderSafetyNetDeleteAuthorityTest --tests SenderSafetyNetDomainPatternTest --tests AuditEntryResponseSafetyNetFieldTest` - passed.
- `./gradlew.bat :backend:worker:test --tests TriageSafetyNetDomainMatchTest --tests TriageAuditSafetyNetBadgeTest` - passed.
- `./gradlew.bat :backend:core:test --tests TriageAuditPersistenceContractTest --tests TriageOutboundRuntimeGateTest` - passed.
- `./gradlew.bat :backend:core:test --tests SenderSafetyNetServiceContractTest --tests TriageOutboundRuntimeGateTest --tests TriageAuditPersistenceContractTest` - passed after restoring the hashed opt-in log line.
- `./gradlew.bat :backend:core:spotlessApply :backend:api:spotlessApply :backend:worker:spotlessApply` - passed.
- `./gradlew.bat :backend:core:test :backend:api:test :backend:worker:test --tests "*SafetyNet*" --tests "*Triage*" --tests "*AuditEntryResponse*"` - passed.
- JetBrains `get_file_problems` on `TriageOrchestratorService`, `SenderSafetyNetService`, `SafetyNetMatcher`, `SenderSafetyNetController`, and `AuditEntryResponse` - no errors; only pre-existing weak warnings in large core services.
- JetBrains `build_project` - passed with no problems.
- `git diff --check` - clean.

## User Setup Required

None.

## Next Phase Readiness

09-04 can proceed in Wave 1 independently. 09-06 can regenerate OpenAPI and consume `blockedBySafetyNetPattern` for the audit badge once 09-04 and 09-05 are complete.
