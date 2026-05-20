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

## From Plan 07 (Wave 6 — Campaign orchestration services)

### Resolved by Plan 07

These Wave 0 RED stubs flipped GREEN during Plan 07 execution:

- **`CampaignUndoServiceTest.*` (5 tests now, was 3)** — `CampaignUndoService` shipped per
  UNS-07. Plan 07 also added 2 new pin tests (H-3 false-positive guard + H-2 user-deleted-label
  tolerance). Schema-column-name bugs in the seed (deferred items #1 partial) were fixed under
  Rule 1.
- **`CleanupPrivacySweepTest.future_campaign_execute_service_is_present`** — `CampaignExecuteService`
  shipped per UNS-04 / D-04. (The other privacy sweep method `campaignExecution_doesNotLeakSensitiveTokensInLogs`
  still fails due to a separate `mail_message_observed` seed schema bug, see "Still deferred" below.)
- **`TriageAuditWriterCleanupArchiveTest.recordCleanupArchive_doesNotInterfereWithSourceTriageRows`**
  (deferred item #1) — same `subject_excerpt`/`matcher_evidence` seed bug as the undo test was
  fixed under Rule 1 (auto-fix bugs) since the diagnosis was identical and the fix was a small
  schema-column rename in the test seed.

### Still deferred — out-of-scope for Plan 07

These remain RED but are not caused by Plan 07. They will be addressed when their owning wave
runs:

- **`CleanupModuleVerificationTest.cleanupModuleIsDeclaredAndVerifies`** (deferred item #3) —
  `IllegalArgumentException: No classes found in packages [com.zeromail.core.support]!`. The
  Spring Modulith verifier references a package that does not contain bootable classes. Owned
  by the wave that introduces the actual `core.support` package layout fix or moves the
  verifier reference.
- **`CleanupPrivacySweepTest.campaignExecution_doesNotLeakSensitiveTokensInLogs`** — seed insert
  into `mail_message_observed` uses unspecified column names (likely `id` PK or `sender_domain`
  schema drift) and throws `BadSqlGrammarException` before the privacy assertion runs. Same
  class of schema-drift bug as the audit-writer test that Plan 07 fixed, but in a different
  test file in a different module surface (privacy sweep, not undo/audit-write). Will be
  fixed by the wave that owns the privacy sweep test (likely Plan 08 — controller wave —
  which will also need to verify the privacy-sweep across the controller surface).

These items are tracked here so the verifier does not re-discover them as new regressions.
Plan 07 verified these failures are unchanged by its scope.
