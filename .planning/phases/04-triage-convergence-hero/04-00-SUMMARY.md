---
phase: 04-triage-convergence-hero
plan: 00
subsystem: testing
tags: [spring-modulith, jdbc-events, archunit, junit, wave-0, triage]

requires:
  - phase: 02A
    provides: Gmail Pub/Sub ingestion and observed-message persistence patterns
  - phase: 02B
    provides: CreditLedger and CallSite accounting contracts
  - phase: 02C
    provides: LlmGateway and privacy/sanitization boundaries
  - phase: 03
    provides: Rules domain, matcher/action contracts, and Wave 0 reflection-test pattern
provides:
  - Spring Modulith JDBC event registry dependency on core and worker classpaths
  - Compile-clean RED contract spine for Phase 4 core, worker, and API work
  - ArchUnit guards for Gmail send bans, triage Gmail write boundaries, and triage audit repository mutation names
  - CallSite membership contract expecting TRIAGE_PLATFORM_LLM and TRIAGE_DETERMINISTIC
  - Semantic intent eval harness directory marker
affects: [phase-04, triage, gmail, llm, billing, api, worker]

tech-stack:
  added: [org.springframework.modulith:spring-modulith-starter-jdbc]
  patterns:
    - Reflection/FQN-string RED scaffolds keep test source compile-clean while future classes are absent
    - Wave 0 ArchUnit guards enforce safety before production triage code lands

key-files:
  created:
    - backend/core/src/test/java/com/zeromail/core/triage/TriageOrchestratorContractTest.java
    - backend/core/src/test/java/com/zeromail/core/triage/TriageSafetyPolicyContractTest.java
    - backend/core/src/test/java/com/zeromail/core/triage/TriageActionResultJsonValidatorContractTest.java
    - backend/core/src/test/java/com/zeromail/core/triage/TriageAuditPersistenceContractTest.java
    - backend/core/src/test/java/com/zeromail/core/triage/TriageUndoServiceContractTest.java
    - backend/core/src/test/java/com/zeromail/core/triage/SenderSafetyNetServiceContractTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/event/MailMessageObservedContractTest.java
    - backend/worker/src/test/java/com/zeromail/worker/triage/TriageOrchestratorIntegrationContractTest.java
    - backend/worker/src/test/java/com/zeromail/worker/triage/TriageIdempotencyContractTest.java
    - backend/worker/src/test/java/com/zeromail/worker/triage/TriageShadowModeContractTest.java
    - backend/worker/src/test/java/com/zeromail/worker/triage/TriageCreditAccountingContractTest.java
    - backend/worker/src/test/java/com/zeromail/worker/triage/TriageAuditPurgeJobContractTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/triage/TriageUndoControllerContractTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/triage/TriageTenantControllerContractTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/triage/SenderSafetyNetControllerContractTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/TriageAuditRepositoryBoundaryArchTest.java
    - backend/core/src/test/resources/semantic-intent-eval/README.md
  modified:
    - gradle/libs.versions.toml
    - backend/core/build.gradle.kts
    - backend/worker/build.gradle.kts
    - backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java

key-decisions:
  - "Use spring-modulith-starter-jdbc without a version pin; the existing Spring Modulith BOM supplies 2.0.7-SNAPSHOT."
  - "Wave 0 triage tests reference future production types by FQN strings/reflection so compileTestJava stays green while test execution is RED."
  - "CallSiteEnumMembershipArchTest is intentionally RED until 04-02 adds TRIAGE_PLATFORM_LLM and TRIAGE_DETERMINISTIC."

patterns-established:
  - "Future-class RED contracts use enabled Class.forName presence tests plus disabled fixture-heavy method bodies."
  - "Gmail safety invariants are enforced with ArchUnit before triage write code exists."

requirements-completed: [TRG-01, TRG-02, TRG-03, TRG-04, TRG-05, TRG-06, TRG-07, TRG-08]

duration: 30min
completed: 2026-05-11
---

# Phase 04 Plan 00: Wave 0 Triage Contract Spine Summary

**Spring Modulith JDBC registry dependency plus compile-clean RED contracts for the full Phase 4 triage pipeline**

## Performance

- **Duration:** 30 min
- **Started:** 2026-05-11T10:04:24Z
- **Completed:** 2026-05-11T10:34:39Z
- **Tasks:** 3
- **Files modified:** 23

## Accomplishments

- Added `spring-modulith-starter-jdbc` to the version catalog, `backend/core`, and `backend/worker`; dependency insight confirms both `spring-modulith-starter-jdbc` and transitive `spring-modulith-events-jdbc` resolve at `2.0.7-SNAPSHOT`.
- Added 15 compile-clean RED contract tests plus the semantic-intent eval README. The tests fail at runtime on absent Phase 4 production symbols rather than compile-breaking later targeted runs.
- Added three safety/boundary guards and bumped `CallSiteEnumMembershipArchTest` from 3 to 5 members, intentionally RED until `TRIAGE_PLATFORM_LLM` and `TRIAGE_DETERMINISTIC` are introduced.

## Task Commits

1. **Task 1: Add spring-modulith-starter-jdbc dependency and verify resolution** - `3f047dd` (chore)
2. **Task 2: RED test spine - backend/core + backend/worker + backend/api scaffolds** - `410ba25` (test)
3. **Task 3: Four new ArchUnit guards + CallSite membership bump** - `760482d` (test)

## Files Created/Modified

- `gradle/libs.versions.toml` - Added the BOM-managed `spring-modulith-starter-jdbc` catalog alias.
- `backend/core/build.gradle.kts` / `backend/worker/build.gradle.kts` - Added the JDBC starter on both runtime classpaths.
- `backend/core/src/test/java/com/zeromail/core/triage/*ContractTest.java` - Core triage RED contracts for orchestrator, safety, JSON validation, audit, undo, and sender safety.
- `backend/worker/src/test/java/com/zeromail/worker/triage/*ContractTest.java` - Worker RED contracts for orchestration, idempotency, shadow mode, credit accounting, and purge jobs.
- `backend/api/src/test/java/com/zeromail/api/controllers/triage/*ContractTest.java` - API RED contracts for undo, tenant shadow mode, and sender safety endpoints.
- `backend/core/src/test/java/com/zeromail/core/arch/*Triage*.java` plus `NoGmailSendAllowedTest.java` - Safety and boundary guards.
- `backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java` - Expected member set changed from 3 to 5.
- `backend/core/src/test/resources/semantic-intent-eval/README.md` - Eval harness directory contract.

## Decisions Made

- Used Context7 Spring Modulith docs before editing; the docs show `spring-modulith-starter-jdbc`, `@ApplicationModuleListener`, `IncompleteEventPublications`, `CompletedEventPublications`, and `FailedEventPublications` in the 2.x event-publication surface. Gradle dependency insight then proved the pinned snapshot resolves the starter and JDBC events artifact.
- Kept all future Phase 4 type references as strings/reflection. This preserves compile-clean test sources and makes the RED state a runtime contract failure, matching the plan's review-finding mitigation.
- Did not add fixture directories beyond the README marker. `fixtures/*.json`, `cassettes/*.json`, and the `semanticIntentEval` Gradle task remain owned by the eval-auditor / later plans.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Cast future exception classes as Throwable subclasses in disabled reflection assertions**
- **Found during:** Task 2
- **Issue:** `compileTestJava` failed because AssertJ's `hasRootCauseInstanceOf(...)` requires `Class<? extends Throwable>`, while `Class.forName(...)` returns `Class<?>`.
- **Fix:** Added `throwableType(...).asSubclass(Throwable.class)` helpers in the two affected contract tests.
- **Files modified:** `TriageSafetyPolicyContractTest.java`, `TriageUndoServiceContractTest.java`
- **Verification:** `./gradlew.bat :backend:core:compileTestJava :backend:worker:compileTestJava :backend:api:compileTestJava --console=plain` passed.
- **Committed in:** `410ba25`

### Workflow Deviations

**1. Phase branch base**
- **Issue:** The phase branch was intentionally created from the previous planning branch because `origin/main` is 90 commits behind and lacks the Phase 04 artifacts.
- **Impact:** No code impact. Execution stayed inline on `gsd/phase-04-triage-convergence-hero` as requested.

---

**Total deviations:** 1 auto-fixed blocking issue; 1 workflow note.
**Impact on plan:** The auto-fix was necessary to satisfy the compile-clean RED-test contract. No scope creep.

## Issues Encountered

- Expected RED state: `:backend:core:test :backend:worker:test :backend:api:test --continue` fails at runtime on the new missing Phase 4 contract types and the bumped `CallSite` membership test.
- Adding the Modulith JDBC starter before the `event_publication` Liquibase changelog exists causes shutdown-time `eventPublicationRegistry` `BadSqlGrammarException` warnings in Spring context tests. The targeted unrelated tests still pass; the schema is intentionally owned by the next Phase 4 database plan.

## Verification

- `./gradlew.bat :backend:core:dependencies --configuration runtimeClasspath --console=plain` and worker equivalent resolved `spring-modulith-starter-jdbc` and `spring-modulith-events-jdbc`.
- `./gradlew.bat :backend:core:compileJava :backend:worker:compileJava --console=plain` passed.
- `./gradlew.bat :backend:core:compileTestJava :backend:worker:compileTestJava :backend:api:compileTestJava --console=plain` passed.
- `./gradlew.bat :backend:core:test --tests "*NoGmailSendAllowedTest" --console=plain` passed.
- `./gradlew.bat :backend:core:test --tests "*TriageGmailWriteBoundaryTest" --tests "*TriageAuditRepositoryBoundaryArchTest" --console=plain` passed.
- `./gradlew.bat :backend:core:test --tests "*CallSiteEnumMembershipArchTest" --console=plain` failed as expected on 3 vs 5 members.
- `./gradlew.bat :backend:core:test --tests "*RuleAstContractTest" :backend:worker:test --tests "*GmailWatchSchedulerTest" :backend:api:test --tests "*ProblemDetailContractTest" --console=plain` passed, proving unrelated targeted runs still work.
- `./gradlew.bat :backend:core:test :backend:worker:test :backend:api:test --continue --console=plain` failed as expected at runtime: core 316 tests / 10 failed / 26 skipped; worker 41 tests / 5 failed / 6 skipped; API 177 tests / 3 failed / 9 skipped.
- JetBrains file-problem checks returned no errors for the Task 3 Java files.

## Known Stubs

None - no production stubs were added. The RED tests are intentional Wave 0 contracts.

## Threat Flags

None - this plan adds a BOM-managed dependency and test-only safety contracts; it does not introduce new production endpoints, auth paths, file access patterns, or schema changes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for 04-01. The next plan can implement the Modulith event publication schema and `MailMessageObserved` event flow against the compile-clean RED contracts landed here.

## Self-Check: PASSED

- Summary file exists: `.planning/phases/04-triage-convergence-hero/04-00-SUMMARY.md`
- Task commits found: `3f047dd`, `410ba25`, `760482d`
- No accidental tracked file deletions were present in task commits.

---
*Phase: 04-triage-convergence-hero*
*Completed: 2026-05-11*
