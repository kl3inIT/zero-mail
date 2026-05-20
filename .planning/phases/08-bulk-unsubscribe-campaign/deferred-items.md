# Phase 8 Deferred Items

Items discovered during execution that are out of scope for the current plan.

## From Plan 05 (Wave 4 — UnsubscribeHttpClient + UnsubscribeMailtoSender)

### Pre-existing Wave 0 RED stubs still failing — owned by future waves

These tests were committed in Wave 0 (commit `65bbf9d7`) as RED stubs and were already
RED before Plan 05 started. They depend on features shipped in later waves, NOT Plan 05.
Plan 05 verified these failures are unchanged by its scope (HTTP client + mailto sender).

1. **`TriageAuditWriterCleanupArchiveTest.recordCleanupArchive_doesNotInterfereWithSourceTriageRows`**
   - Failure: `BadSqlGrammarException: column "subject_excerpt" of relation "triage_audit" does not exist`.
   - Root cause: Test inserts using a hand-rolled SQL that references `subject_excerpt`, but the
     current `triage_audit` schema (Liquibase changelogs 011..035) does not include that column.
     The cleanup-archive integration that wires `subject_excerpt` (or removes the reference) is
     owned by a later wave when the cleanup writer actually persists to `triage_audit`.
   - Action: Future wave must either (a) add the column via Liquibase, (b) drop the column from
     the test SQL, or (c) refactor the test to use the existing `TriageAuditWriter` API instead
     of hand-rolled INSERT.

2. **`TriageGmailWriterLookupLabelIdTest.returnsEmptyWhenLabelMissing` + `returnsLabelIdWhenLabelExists`**
   - Failure: `NoSuchMethodException: TriageGmailWriter.lookupLabelId(UUID, String)`.
   - Root cause: Production `TriageGmailWriter` only exposes the private `resolveOrCreateLabelId`.
     The test expects a public `lookupLabelId(UUID, String) → Optional<String>` API that has not
     been introduced. This is a triage-refactor for a future wave (likely the UNS-04 cleanup
     campaign executor which needs idempotent label lookup separate from create).
   - Action: Future wave extracts `lookupLabelId` from `resolveOrCreateLabelId` as a public method,
     or rewrites the tests against `resolveOrCreateLabelId`.

Both items are tracked here so the verifier does not re-discover them as new regressions.

### Wave 0 RED stubs for classes shipped in later Phase 8 plans

These tests reference production classes that will ship in Wave 4b, Wave 5, etc. They were
written in Wave 0 to lock the contract surface (per the GSD RED-first pattern). They were
RED before Plan 05 started and Plan 05 does not alter their dependencies.

3. **`CleanupModuleVerificationTest.cleanupModuleIsDeclaredAndVerifies`**
   - Failure: `IllegalArgumentException: No classes found in packages [com.zeromail.core.support]!`
   - Root cause: Spring Modulith verifier references a package `com.zeromail.core.support` that
     does not exist yet. Owned by a later wave that introduces shared cleanup support utilities
     or that fixes the verifier reference to point at the actual package layout.

4. **`CleanupPrivacySweepTest.future_campaign_execute_service_is_present` / `campaignExecution_doesNotLeakSensitiveTokensInLogs`**
   - Failure: `ClassNotFoundException: com.zeromail.core.cleanup.usecases.CampaignExecuteService`.
   - Root cause: `CampaignExecuteService` is the Wave 4b orchestrator (campaign POST /execute).
     Will ship in Plan 06 (Wave 4b) per the phase plan layout.

5. **`CampaignUndoServiceTest.*` (3 tests)**
   - Failure: `ClassNotFoundException: com.zeromail.core.cleanup.usecases.CampaignUndoService`.
   - Root cause: `CampaignUndoService` is the 30-day undo window service. Will ship in Plan 07
     (Wave 5) per the phase plan layout.

All items above are pre-existing Wave 0 RED stubs whose dependencies live in future plans, NOT
caused by Plan 05.
