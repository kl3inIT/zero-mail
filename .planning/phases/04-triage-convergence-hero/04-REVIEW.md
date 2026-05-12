---
phase: 04-triage-convergence-hero
reviewed: 2026-05-12T00:00:00Z
depth: standard
files_reviewed: 96
files_reviewed_list:
  - apps/web/features/triage/messages.ts
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
  - backend/api/build.gradle.kts
  - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
  - backend/api/src/main/java/com/zeromail/api/controllers/triage/SenderSafetyNetController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageAuditController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/triage/TriageTenantController.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/package-info.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/ProtectedSendersResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/SenderOptInResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/TriageShadowModeRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/TriageShadowModeResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/triage/UndoAuditResponse.java
  - backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java
  - backend/api/src/test/java/com/zeromail/api/controllers/triage/SenderSafetyNetControllerContractTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/triage/TriageTenantControllerContractTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/triage/TriageUndoControllerContractTest.java
  - backend/core/build.gradle.kts
  - backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java
  - backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java
  - backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java
  - backend/core/src/main/java/com/zeromail/core/gmail/event/package-info.java
  - backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailPreviewReadService.java
  - backend/core/src/main/java/com/zeromail/core/llm/exception/LlmEvaluationFailedException.java
  - backend/core/src/main/java/com/zeromail/core/llm/exception/TokenBudgetExceededException.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/JtokkitTruncateSanitizer.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SemanticIntentEvaluator.java
  - backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SemanticIntentResponse.java
  - backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGateway.java
  - backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java
  - backend/core/src/main/java/com/zeromail/core/llm/usecases/SemanticIntentEvaluator.java
  - backend/core/src/main/java/com/zeromail/core/llm/usecases/SemanticIntentRequest.java
  - backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java
  - backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java
  - backend/core/src/main/java/com/zeromail/core/triage/domain/TriageActionResult.java
  - backend/core/src/main/java/com/zeromail/core/triage/domain/TriageDecision.java
  - backend/core/src/main/java/com/zeromail/core/triage/domain/TriageSafetyPolicy.java
  - backend/core/src/main/java/com/zeromail/core/triage/domain/package-info.java
  - backend/core/src/main/java/com/zeromail/core/triage/exception/package-info.java
  - backend/core/src/main/java/com/zeromail/core/triage/package-info.java
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantProtectedSenderObservationEntity.java
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantProtectedSenderObservationRepository.java
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantSenderOptInEntity.java
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantSenderOptInRepository.java
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditRepository.java
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java
  - backend/core/src/main/java/com/zeromail/core/triage/persistence/package-info.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/ProtectedSenderListItem.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/SenderEmailCanonicalizer.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/SenderSafetyNetService.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageActionArgsCanonicalizer.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageActionResultJsonValidator.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageRuleEvaluationInputFactory.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageUndoService.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/UndoAuditCommand.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/UndoAuditResult.java
  - backend/core/src/main/java/com/zeromail/core/triage/usecases/package-info.java
  - backend/core/src/main/resources/db/changelog/changes/024-modulith-event-publication.yaml
  - backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml
  - backend/core/src/main/resources/db/changelog/changes/026-tenants-triage-shadow-mode.yaml
  - backend/core/src/main/resources/db/changelog/changes/027-tenant-sender-opt-in.yaml
  - backend/core/src/main/resources/db/changelog/changes/028-tenant-protected-sender-observation.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java
  - backend/core/src/test/java/com/zeromail/core/arch/TriageAuditRepositoryBoundaryArchTest.java
  - backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/event/MailMessageObservedContractTest.java
  - backend/core/src/test/java/com/zeromail/core/rules/usecases/RuleCompilerServiceTest.java
  - backend/core/src/test/java/com/zeromail/core/support/LiquibaseMigrationTest.java
  - backend/core/src/test/java/com/zeromail/core/triage/NoActiveTransactionDuringGmailWriteTest.java
  - backend/core/src/test/java/com/zeromail/core/triage/SenderSafetyNetServiceContractTest.java
  - backend/core/src/test/java/com/zeromail/core/triage/TriageActionResultJsonValidatorContractTest.java
  - backend/core/src/test/java/com/zeromail/core/triage/TriageAuditPersistenceContractTest.java
  - backend/core/src/test/java/com/zeromail/core/triage/TriageOrchestratorContractTest.java
  - backend/core/src/test/java/com/zeromail/core/triage/TriagePrivacySweepTest.java
  - backend/core/src/test/java/com/zeromail/core/triage/TriageSafetyPolicyContractTest.java
  - backend/core/src/test/java/com/zeromail/core/triage/TriageUndoServiceContractTest.java
  - backend/worker/build.gradle.kts
  - backend/worker/src/main/java/com/zeromail/worker/triage/TriageAuditPurgeBatch.java
  - backend/worker/src/main/java/com/zeromail/worker/triage/TriageAuditPurgeJob.java
  - backend/worker/src/main/java/com/zeromail/worker/triage/TriageEventCleanupJob.java
  - backend/worker/src/main/java/com/zeromail/worker/triage/TriageEventRetryJob.java
  - backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperBatch.java
  - backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java
  - backend/worker/src/main/java/com/zeromail/worker/triage/package-info.java
  - backend/worker/src/main/resources/application.yml
  - backend/worker/src/test/java/com/zeromail/worker/triage/TriageAuditPurgeJobContractTest.java
  - backend/worker/src/test/java/com/zeromail/worker/triage/TriageCreditAccountingContractTest.java
  - backend/worker/src/test/java/com/zeromail/worker/triage/TriageIdempotencyContractTest.java
  - backend/worker/src/test/java/com/zeromail/worker/triage/TriageOrchestratorIntegrationContractTest.java
  - backend/worker/src/test/java/com/zeromail/worker/triage/TriageShadowModeContractTest.java
  - gradle/libs.versions.toml
findings:
  critical: 3
  warning: 6
  info: 3
  total: 12
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-05-12
**Depth:** standard
**Files Reviewed:** 96
**Status:** issues_found

## Summary

Phase 4 wires the AI auto-triage hero flow: event-driven orchestration, sender safety net, undo/audit surface, Postgres-backed maintenance jobs, semantic-intent LLM gateway, and Liquibase changelogs 024-028. The safety architecture is mostly sound — `TriageSafetyPolicy` is a tight allow-list, the audit saga keeps Gmail writes outside the DB transaction (`Propagation.NOT_SUPPORTED`), idempotency uses `ON CONFLICT ... DO NOTHING` + `NULLS NOT DISTINCT`, and ArchUnit guards `no Gmail send` + `single Gmail writer`. Privacy logging is `event=... tenantId={}` throughout.

However, the **undo path is broken end to end** and the **label apply path is almost certainly broken**:

- The Gmail change token written by `TriageAuditSaga.gmailWritePhase` uses keys (`labelId`, `removedLabelId`) that do not match the keys `TriageUndoService` reads back (`addedLabelId`, `removedLabelIds`), so undo of any LABEL or ARCHIVE action always throws `TriageUndoUnsupportedActionException`. The plan/research docs (`04-RESEARCH.md` D-B4, `04-CONTEXT.md`) specify the `addedLabelId` / `removedLabelIds` schema — the saga is on the wrong side of the contract.
- `TriageOrchestratorService.preWriteIntent` puts the user-authored *label name* into the `labelId` slot of `TriageActionResult.Label`, and `TriageGmailWriter.applyLabel` passes that straight to `messages.modify(...).setAddLabelIds(...)`, which requires real Gmail `Label_*` ids — not names. No name→id resolution exists in the triage path.
- `TriageUndoService.undo` performs the Gmail mutation **inside an active `@Transactional` method**, violating the explicit project invariant ("Gmail writes must NOT run inside an active DB transaction") and bypassing the PENDING→APPLIED saga protection, so a crash between the Gmail call and `markReverted` leaves the audit row APPLIED while Gmail is already reverted (and a draft undo in that state becomes permanently un-undoable).

The contract tests are mostly string/reflection assertions that do not exercise these seams (e.g. `TriageAuditPersistenceContractTest` inserts a `removedLabelIds` token directly via `markApplied`, never via the saga, so the key mismatch is invisible to it).

## Critical Issues

### CR-01: Undo always fails for LABEL and ARCHIVE — Gmail change-token key mismatch between saga writer and undo reader

**File:** `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageAuditSaga.java:99-117`, `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageUndoService.java:181-205`
**Issue:** `gmailWritePhase` persists, for a label, `changeToken(Map.of("operation","applyLabel","labelId", label.labelId()))` and, for an archive, `changeToken(Map.of("operation","archiveSkipInbox","removedLabelId","INBOX"))`. `TriageUndoService.requiredAddedLabelId` reads `changeTokenNode.path("addedLabelId")` and `requireArchiveChangeToken` reads `changeTokenNode.path("removedLabelIds")` expecting a JSON array. Neither key exists in the persisted token, so `executeInverse` always throws `TriageAuditException.unsupportedActionType()` → `TriageUndoUnsupportedActionException`. Undo of label/archive is dead on arrival. `04-RESEARCH.md` D-B4 and `04-CONTEXT.md` define the intended schema as `{"addedLabelId":"Label_123"}` / `{"removedLabelIds":["INBOX"]}`, so the saga writer is the wrong side.
**Fix:** Make `gmailWritePhase` emit the documented schema:
```java
case TriageActionResult.Label label -> {
    triageGmailWriter.applyLabel(command.tenantId(), command.gmailMessageId(), label.labelId());
    yield GmailWriteResult.applied(
        command.gmailMessageId(),
        changeToken(Map.of("addedLabelId", label.labelId())));
}
case TriageActionResult.Archive ignored -> {
    triageGmailWriter.archiveSkipInbox(command.tenantId(), command.gmailMessageId());
    yield GmailWriteResult.applied(
        ARCHIVE_EXTERNAL_REF,
        changeTokenArray("removedLabelIds", List.of(INBOX_LABEL_ID)));
}
```
Add a regression test that drives `gmailWritePhase` → `markApplied` → `TriageUndoService.undo` for both action types instead of writing the token by hand.

### CR-02: `applyLabel` is called with a label *name*, not a Gmail label *id*

**File:** `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java:279-287`, `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java:40-56`
**Issue:** `preWriteIntent` maps `ActionIntent.Label(labelName)` → `new TriageActionResult.Label(label.labelName(), label.labelName())` — i.e. `labelId == labelName`. `TriageActionResult.Label.labelName()` is a user-authored label name (`ActionIntent.Label` validates only "must not be blank"; no resolution to a `Label_*` id happens anywhere in the triage path). `TriageGmailWriter.applyLabel` then does `messages.modify(...).setAddLabelIds(List.of(labelId))`. The Gmail `users.messages.modify` API requires real label IDs in `addLabelIds`; passing a display name (e.g. `"Finance"`) returns HTTP 400 "Invalid label" for every user-created label, and Gmail will not auto-create the label. The label action — one of the three v1 write actions — therefore fails for the common case.
**Fix:** Resolve label name → Gmail label id before the write. Either (a) resolve during rule compilation and store the `Label_*` id in `ActionIntent.Label` (renaming the field to `labelId`), or (b) add a label-resolution step in `TriageGmailWriter.applyLabel` that looks up / creates the label via `users.labels.list` / `users.labels.create` and uses the returned id; persist the resolved id in `TriageActionResult.Label.labelId` and in the `addedLabelId` change token. If `ActionIntent.Label.labelName()` already carries a resolved id (verify against the rules-engine compile path), rename it to make that contract explicit and close this finding.

### CR-03: `TriageUndoService.undo` runs the Gmail write inside an active DB transaction and outside the saga's crash-safety envelope

**File:** `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageUndoService.java:74-130`
**Issue:** `undo` is annotated `@Transactional` (default `REQUIRED`). Inside it, `executeInverse` calls `triageGmailWriter.removeLabel` / `restoreToInbox` / `deleteDraft`, each of which calls `gmailApiClientFactory.buildClientForTenant` (a JPA query inside the same tx) and then a Gmail HTTP mutation — all while the transaction (and its DB connection) is held open across a network round-trip. This violates the project invariant "Gmail writes must NOT run inside an active DB transaction" (the invariant the saga's `Propagation.NOT_SUPPORTED` exists to satisfy; `NoActiveTransactionDuringGmailWriteTest` covers only `TriageAuditSaga`, not undo). Beyond connection-pool pressure, there is no PENDING→APPLIED protection: if the process dies (or the tx rolls back for any reason) after `executeInverse` succeeds but before `markReverted` commits, Gmail is reverted while the audit row stays `APPLIED`; a retried undo of a `SaveDraft` then 404s on the already-deleted draft → `TriageUndoWriteFailedException` forever.
**Fix:** Restructure undo to mirror the saga: in a short tx, transition the audit row to a `REVERT_PENDING`/lease state with `WHERE decision = 'APPLIED'` guard; perform the Gmail inverse outside any transaction (`Propagation.NOT_SUPPORTED` or a non-transactional collaborator); in a second short tx, `markReverted` (idempotent on already-`REVERTED`). Make Gmail inverse ops idempotent for retry (`removeLabel`/`restoreToInbox` already are; `deleteDraft` should treat a 404 as success).

## Warnings

### WR-01: `SemanticIntentMatcher.deferred` is parsed and stored but never consulted; the DEFERRED evaluation state is unreachable from semantic matchers

**File:** `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java:412-423,589-594`
**Issue:** `parseMatcherNode` reads `booleanValue(matcherNode, "deferred")` into `SemanticIntentMatcher`, but `evaluateResolvedMatcher` for a `SemanticIntentMatcher` only does `semanticMatches.getOrDefault(nodeId, false)` → `matched` ? MATCHED : NOT_MATCHED. It never returns `MatcherEvaluationState.DEFERRED`. `failedSemanticMatches` likewise records `false` for every failed node. Since deterministic matchers can't produce DEFERRED either, the entire DEFERRED branch in `evaluateAll`/`evaluateAny`/`evaluateNot` is dead code, and a "deferred" semantic intent that the LLM could not evaluate silently becomes NOT_MATCHED (the rule does not fire) with no record that it was deferred. Either implement deferral (failed `deferred:true` nodes → DEFERRED, surfaced/retried) or drop the unused field and the unreachable DEFERRED handling.
**Fix:** Decide the semantics: if "deferred" is meant to mean "fail-open / retry-later", thread it through `resolveSemanticMatches` → `failedSemanticMatches` so failed deferred nodes yield a DEFERRED result and the orchestrator records/retries them; otherwise remove `deferred` from `SemanticIntentMatcher` and delete the DEFERRED-propagation code paths.

### WR-02: `TriageAuditNotFoundException` returns HTTP 404 with `code = error.badRequest`

**File:** `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java:365-377`, `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java`
**Issue:** `onTriageAuditNotFound` returns `HttpStatus.NOT_FOUND` but sets the wire `code` to `ErrorCodes.BAD_REQUEST` (`error.badRequest`). The frontend localizes on `code` (D-C3), so a cross-tenant / missing audit id surfaces a "bad request" message on a 404 response — and there is no `error.triage.audit.not_found` (or `error.notFound`) constant at all. `TriageUndoWriteFailedException` similarly maps a 502 to `ErrorCodes.CONFLICT`. Code/status pairs should be consistent.
**Fix:** Add `ErrorCodes.TRIAGE_AUDIT_NOT_FOUND = "error.triage.audit.not_found"` (and a matching `errors.triage.audit.not_found` feature i18n key) and use it; give `TriageUndoWriteFailedException` a dedicated code (e.g. `error.triage.undo.write_failed`) instead of `error.conflict`.

### WR-03: `TriagePendingReaperJob.reap()` and `TriageAuditPurgeJob.purge()` can terminate their batch loop early and leave work for the next run

**File:** `backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java:35-48`, `backend/worker/src/main/java/com/zeromail/worker/triage/TriageAuditPurgeBatch.java:27-56` (loop in `TriageAuditPurgeJob.purge()`)
**Issue:** `reap()` loops `while (processedCount == BATCH_LIMIT)`, but `reapStuckPendingOnce` returns the count of rows where `markFailed` returned 1 (`reapedCount`), not the number of stuck rows it *selected*. If `findStuckPendingForReaping` returns `batchLimit` rows but some are no longer PENDING (race with the saga), `reapedCount < batchLimit` and the loop exits while more stuck rows remain. The purge loop has the analogous issue: `purgeExpiredOnce` uses `FOR UPDATE SKIP LOCKED`, so contended rows are skipped and `deletedCount < batchLimit` can occur with rows still expired. Both jobs are scheduled (5 min / daily), so this only delays cleanup, but the loop condition is logically wrong.
**Fix:** Return the number of rows *selected* from the batch method (or loop on `selected == BATCH_LIMIT` separately from `reaped`/`deleted`), so the loop drains the queue rather than stopping on the first partial batch.

### WR-04: `optInSender` event is logged twice (controller + service) — and credit-ledger settle failures leak the reservation in the LLM gateway

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/triage/SenderSafetyNetController.java:46-50` + `backend/core/src/main/java/com/zeromail/core/triage/usecases/SenderSafetyNetService.java:143-147`; `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java:493-503,563-572`
**Issue:** Both `SenderSafetyNetController.optIn` and `SenderSafetyNetService.optInSender` emit `event=triage_sender_opt_in tenantId={} senderEmailHash={}`, producing duplicate audit lines for one action (the controller log is redundant since the transaction-scoped service is the authoritative point). Separately, in `LlmGatewayImpl` (`callPlatformModelClientWithCreditLedger`, `evaluateSemanticIntentsWithCreditLedger`) a `RuntimeException` from `creditLedger.settle(...)` is logged and rethrown but the reservation is never released — a settle failure leaks the held reservation. (Pre-existing pattern, but the Phase 4 semantic path inherits it.)
**Fix:** Remove the controller-side `triage_sender_opt_in` log line (keep the service one). In the gateway, on `settle` failure, attempt `creditLedger.release(reservationId)` (best-effort, swallow secondary failure) before rethrowing.

### WR-05: `SenderEmailCanonicalizer.redisCacheKeyComponent` re-canonicalizes an already-canonical address; `isProtected` re-derives it twice

**File:** `backend/core/src/main/java/com/zeromail/core/triage/usecases/SenderEmailCanonicalizer.java:29-37`, `backend/core/src/main/java/com/zeromail/core/triage/usecases/SenderSafetyNetService.java:92-122`
**Issue:** `redisCacheKeyComponent(canonicalizedEmail)` calls `canonicalize(canonicalizedEmail)` internally, and `gmailSearchToken(canonicalizedEmail)` does the same. `SenderSafetyNetService.isProtected` already canonicalizes once, then passes the canonical value into `redisCacheKeyComponent` (re-canonicalizes) and into `gmailSearchToken` (re-canonicalizes). This is functionally correct (canonicalize is idempotent) but the method contracts are confusing — a method named `redisCacheKeyComponent(String canonicalizedEmail)` should not be re-validating its input through the email regex (which would `throw` on a value the caller already vetted). It also means an opt-in stored as a non-strictly-canonical value (e.g. legacy data with mixed case) would silently fail canonical re-validation.
**Fix:** Split `canonicalize` from `hash`: `redisCacheKeyComponent`/`gmailSearchToken` should take an already-canonical address and only hash/quote it (no re-`canonicalize`), with canonicalization done exactly once at the public entry point.

### WR-06: `GmailDeliveryProcessingService.processDelivery` performs all Gmail HTTP calls inside a `@Transactional` method (Gmail-call-in-tx)

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java:28-136`
**Issue:** The class is `@Transactional` and `processDelivery` holds the transaction open across `refreshAccessToken`, `history.list` (paged), and a `messages.get` per added message — long network calls while a DB connection is checked out. This is the same anti-pattern CR-03 flags for undo. It is pre-existing (Phase ≤3 ingestion), but Phase 4's triage hero flow now depends on these observations and the load profile changes; calling it out so it is on the radar.
**Fix:** Move the Gmail fetch loop out of the transaction (collect observations into memory, then persist `insertObservedIfAbsent` + `updateLastSyncedHistoryIdMonotonic` + `updateStatus` in a short tx at the end), or process per-page with short per-page transactions.

## Info

### IN-01: `TriageActionResult.SaveDraft.threadId` is required but rule-authored `SaveDraft` intents have no thread until pre-write resolution

**File:** `backend/core/src/main/java/com/zeromail/core/triage/domain/TriageActionResult.java:26-34`, `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageOrchestratorService.java:284-285`
**Issue:** `TriageActionResult.SaveDraft` requires `threadId` to be non-blank, and `preWriteIntent` supplies `gmailThreadId` from the observed event. That works, but it couples the persisted `action_args_json` to the thread id even though the canonical hash (`TriageActionArgsCanonicalizer.canonicalJson`) deliberately includes `threadId` for SAVE_DRAFT — meaning two messages in the same thread with the same instruction collide on idempotency key, while the audit "args" surface stores a Gmail thread id. Not a bug, but worth a comment explaining why `threadId` is part of the canonical SAVE_DRAFT identity.
**Fix:** Add a short comment on `TriageActionArgsCanonicalizer.canonicalJson`'s SAVE_DRAFT branch documenting the intentional thread-scoped idempotency for drafts.

### IN-02: `ZeroMailLlmProperties.modelByCallSite()` maps `CallSite.DRAFT` to `compileModel`

**File:** `backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java:133-140`
**Issue:** `CallSite.DRAFT` (cost 2 — the draft-reply call site) is mapped to `compileModel` rather than `triageModel` or a dedicated draft model. With the current config both are `openai/gpt-4o-mini`, so it is harmless today, but the mapping is surprising and will pick the wrong model the moment `compileModel` and `triageModel` diverge. Note also that the triage write path never reserves `CallSite.TRIAGE`/`CallSite.DRAFT` credits — only `TRIAGE_PLATFORM_LLM`/`TRIAGE_DETERMINISTIC` — so those non-zero-cost call sites appear to be unused by the triage flow; if that's intended, a comment on `CallSite` would help.
**Fix:** Map `DRAFT` to `triageModel` (or add a `draftModel` property), and document which call sites the v1 triage path actually charges.

### IN-03: `GlobalExceptionHandler` has two distinct handlers (`TriageAuditException` / `TriageUndoUnsupportedActionException`) producing the identical 409 + `error.triage.undo.unsupported_action` response

**File:** `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java:335-363`, `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageUndoService.java`
**Issue:** `TriageUndoService` only ever throws via `TriageAuditException.unsupportedActionType()`; the separate `TriageUndoUnsupportedActionException` handler maps to the same status/code. Two exception types for one outcome is redundant and invites drift (a future change that updates one handler but not the other).
**Fix:** Pick one exception type for "unsupported triage action type" and delete the other handler (and class).

---

_Reviewed: 2026-05-12_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
