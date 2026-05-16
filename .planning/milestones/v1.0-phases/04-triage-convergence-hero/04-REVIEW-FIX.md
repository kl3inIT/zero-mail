---
phase: 04-triage-convergence-hero
fixed_at: 2026-05-12T09:39:51.5652818+07:00
review_path: .planning/phases/04-triage-convergence-hero/04-REVIEW.md
fix_scope: all
findings_in_scope: 12
fixed: 12
skipped: 0
iteration: 1
status: all_fixed
verification:
  - ./gradlew.bat spotlessApply
  - pnpm --dir apps/web lint
  - pnpm --dir apps/web typecheck
  - ./gradlew.bat :backend:core:test --tests "com.zeromail.core.triage.NoActiveTransactionDuringGmailWriteTest" --tests "com.zeromail.core.triage.TriageAuditPersistenceContractTest" --tests "com.zeromail.core.triage.TriageActionResultJsonValidatorContractTest" --tests "com.zeromail.core.triage.TriageUndoServiceContractTest" --tests "com.zeromail.core.arch.TriageAuditRepositoryBoundaryArchTest"
  - ./gradlew.bat :backend:api:test --tests "com.zeromail.api.controllers.triage.TriageUndoControllerContractTest"
  - ./gradlew.bat :backend:worker:test --tests "com.zeromail.worker.triage.TriageAuditPurgeJobContractTest"
  - ./gradlew.bat :backend:core:check :backend:api:check :backend:worker:check
---

# Phase 4 Code Review Fix Report

## Summary

All 12 findings from `04-REVIEW.md` were fixed in the `--all` scope.

## Fixed Findings

- CR-01: Aligned Gmail change-token JSON with undo's `addedLabelId` / `removedLabelIds` contract and added regression coverage.
- CR-02: Resolved or created Gmail labels before applying them, persisted the resolved label id, and stored the resolved id in the undo token.
- CR-03: Moved undo through an explicit `REVERT_PENDING` state and suspended transactions around inverse Gmail writes; draft deletion is retry-safe on 404.
- WR-01: Threaded semantic intent failures as `DEFERRED` matcher states instead of silently treating failed semantic evaluation as not matched.
- WR-02: Added dedicated triage audit-not-found and undo-write-failed error codes plus frontend messages.
- WR-03: Changed reaper and purge loop conditions to use selected row counts instead of processed/deleted counts.
- WR-04: Removed duplicate sender opt-in logging and released credit reservations on LLM settle failures.
- WR-05: Stopped re-canonicalizing already canonical sender emails in cache-key and Gmail-search helpers.
- WR-06: Suspended transactions around Gmail delivery processing network calls.
- IN-01: Documented thread-scoped SaveDraft idempotency.
- IN-02: Mapped `CallSite.DRAFT` to the triage model and documented active triage charge sites.
- IN-03: Removed the redundant `TriageUndoUnsupportedActionException` path and kept `TriageAuditException` as the unsupported-action source.

## Verification

Backend module checks passed for `backend/core`, `backend/api`, and `backend/worker`. Frontend lint and typecheck passed for the web module.
