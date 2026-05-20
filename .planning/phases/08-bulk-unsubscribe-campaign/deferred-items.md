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
