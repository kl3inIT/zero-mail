---
phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit
fixed_at: 2026-05-19T03:00:00Z
review_path: .planning/phases/07-chat-email-assistant-backend-frontend-send-executor-archunit/07-REVIEW.md
iteration: 1
findings_in_scope: 15
fixed: 15
skipped: 0
status: all_fixed
---

# Phase 7: Code Review Fix Report

**Fixed at:** 2026-05-19
**Source review:** `07-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope (Critical + Warning): 15
- Fixed: 15
- Skipped: 0
- Info findings (out of scope per `fix_scope=critical_warning`): 6 (IN-01 .. IN-06) — deferred to a future iteration

## Fixed Issues

### CR-01: VIP recipient hash only covers the first recipient

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java`, `backend/core/src/test/java/com/zeromail/core/chat/confirm/AssistantSendExecutorVipIT.java`
**Commit:** `c744f42a`
**Applied fix:** `recipientHash()` now sorts the canonical recipients, joins them with comma, and SHA-256s the joined string instead of `findFirst()`-ing the lexicographic minimum. Added `recipient_hash_covers_all_to_cc_and_bcc_recipients` regression test that fires a single-recipient send and a multi-recipient send through the executor and asserts the audited `recipientHash` differs between them.

### CR-02: `commitSendCompleted` runs in a separate transaction from Gmail send

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/ConfirmationStateMachine.java`, `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java`, `backend/core/src/test/java/com/zeromail/core/chat/confirm/AssistantSendExecutorVipIT.java`
**Commit:** `c744f42a`
**Applied fix:** `ConfirmationStateMachine.commitSendCompleted` is now idempotent: returns `boolean`, and if the UPDATE matches zero rows and the audit row is already in `COMMITTED` (the reconciler moved it forward), returns `false` without throwing or re-publishing. If the row is missing or in another state, fails loud as before. The `AssistantSendCompleted` event is now published from INSIDE the same `@Transactional` method so it fires only on real DB commit and is correctly visible to `@TransactionalEventListener(AFTER_COMMIT)` listeners — fixes the silent-drop bug from WR-10 as well.

### CR-03: Postgres body-ban trigger HTML regex too narrow

**Files modified:** `backend/core/src/main/resources/db/changelog/changes/047-chat-forbidden-html-expanded.yaml` (NEW), `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml`
**Commit:** `98558be1`
**Applied fix:** New Liquibase changelog 047 `CREATE OR REPLACE`s `chat_jsonb_contains_forbidden_html` with three checks: (1) dangerous tag set `<script|iframe|svg|object|embed|style|link|meta>`, (2) event-handler attributes (`\son[a-z]+\s*=`), (3) `javascript:` URIs in `href`/`src` attributes. The 042 changelog is not edited in place because it is already applied. The Privacy Draft-body carve-out is unchanged — the existing `tool-(sendEmail|replyEmail|forwardEmail|saveDraft)` allow-list in `reject_chat_message_with_body` still protects user-authored draft data.

### CR-04: Sanitizer `truncated` flag dropped in persisted tool output

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java`
**Commit:** `236296af`
**Applied fix:** Introduced a `SanitizedToolOutput(outputJson, truncated)` record returned from `sanitizeToolOutput`. Threaded `truncated` through `persistToolOutput` so the persisted `ToolOutputPart.truncated` reflects the sanitizer's decision (was always hard-coded `false`). Downstream audit can now distinguish complete vs. truncated read-tool outputs as required by the Privacy contract.

### WR-01: `reserve()` commits PROCESSING before `toCommand()` validation

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/ConfirmationStateMachine.java`, `backend/core/src/main/java/com/zeromail/core/chat/usecases/ConfirmActionService.java`
**Commit:** `fb8bcef1`
**Applied fix:** Added `ConfirmationStateMachine.revertReservation(chatId, tenantId, toolCallId)` that flips `PROCESSING` back to `PENDING` in its own `@Transactional` method. `ConfirmActionService.confirm` calls it when `confirmedSendToolHandlers.toCommand` throws, so the user does not get stuck on an infinite spinner until the 5-minute reconciliation cron expires the row.

### WR-02: Two-scheduler ownership in `ChatOrchestrator.stream`

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java`
**Commit:** `6846befd`
**Applied fix:** Documented the two-scheduler design (orchestrator-owned + Spring AI client-owned) and the idempotency assumption with a method-level comment plus an inline pointer at the task-body `finally`. Behavior unchanged — reviewer classified the issue as misleading documentation, not a leak.

### WR-03: Lease release without fencing-token equality check

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/ConfirmationLeaseService.java`, `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendCommand.java`, `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java`, `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantWriteCommand.java`, `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantWriteExecutor.java`, `backend/core/src/main/java/com/zeromail/core/chat/usecases/ConfirmActionService.java`, `backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/ConfirmedSendToolHandlers.java`, `backend/core/src/test/java/com/zeromail/core/chat/confirm/AssistantSendExecutorVipIT.java`, `backend/core/src/test/java/com/zeromail/core/chat/confirm/ConfirmationLeaseServiceIT.java`
**Commit:** `1c8bf611`
**Applied fix:** `ConfirmationLeaseService.release` now takes `processInstanceId` and executes a Lua compare-and-delete script that only DELs when the stored value matches the caller's fencing token. `ConfirmActionService` generates a `processInstanceId` once per `confirm`/`cancel`, threads it through `AssistantSendCommand` / `AssistantWriteCommand`, and the executors release with the same token they acquired. Test asserts the Lua execute path instead of the literal DEL.

### WR-04: Local `JsonMapper.builder().build()` bypasses Spring's `ObjectMapper`

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatPartsJsonConverter.java`, `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/ZeroMailChatMemory.java`, `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiStreamingChatModelClient.java`, `backend/core/src/main/java/com/zeromail/core/chat/llm/VercelProtocolEmitter.java`, `backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatController.java`, `backend/core/src/test/java/com/zeromail/core/chat/llm/VercelProtocolEmitterTest.java`
**Commit:** `730b52e6`
**Applied fix:** Removed local `JsonMapper.builder().build()` instances and pulled the Boot-managed `ObjectMapper` through DI in all four production sites. `ChatPartsJsonConverter` lost its no-arg constructor. `VercelProtocolEmitter` (per-request, not a Spring bean) now requires `ObjectMapper` explicitly; `ChatController` (the only Spring construction site) injects the mapper and passes it. `VercelProtocolEmitterTest` builds a shared `testMapper()` helper.

### WR-05: Body-ban trigger detection by message text

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatMessageJdbcRepository.java`
**Commit:** `52ce3220`
**Applied fix:** `isBodyBanViolation` walks the cause chain for `java.sql.SQLException` with SQLSTATE `23514` (the stable contract emitted by the trigger). Falls back to the legacy message-substring check only if no SQLException is in the chain so a wrapped/translated exception still classifies correctly during transitional periods.

### WR-06: Soft-delete returns 404 on re-delete attempt

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatHistoryService.java`
**Commit:** `9302bc32`
**Applied fix:** After the UPDATE returns 0 rows, run an `EXISTS` check to distinguish "chat missing" (still throws `ChatNotFoundException` → 404) from "chat exists but already soft-deleted" (returns normally → 204 No Content). Soft delete is now idempotent.

### WR-07: Magic number 4 for read-tool iteration cap with silent finish on exhaustion

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/usecases/ZeroMailChatProperties.java`, `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java`
**Commit:** `4a058f09`
**Applied fix:** Added `ZeroMailChatProperties.maxReadToolIterations` (default 4, `@Min(1)`) and used it in `executeReadToolLoop`. On cap exhaustion, log `event=chat_tool_iteration_capped` (structured, no content) and emit `streamSink.emitError("chat_too_many_tool_calls", "The assistant requested too many tool calls. Please try again.")` instead of the previous silent `emitFinish("complete")`.

### WR-08: Sanitizer body strip and length cap as disjoint passes

**Files modified:** `backend/core/src/main/java/com/zeromail/core/chat/sanitize/ToolOutputSanitizer.java`
**Commit:** `8df9482a`
**Applied fix:** Enable `capStringValues=true` in stage 1's `sanitizeJson` call so large strings in non-body fields are bounded immediately during the body-strip recursion instead of carried through to stage 2. Stage 2 remains as defense in depth (idempotent on already-capped strings). Stage 1 log entry now reflects the truncated flag correctly.

### WR-09: Frontend `useMemo` empty deps captures stale XSRF token

**Files modified:** `apps/web/features/chat/hooks/use-chat.ts`
**Commit:** `b7fc77b7`
**Applied fix:** Removed the top-level `headers: xsrfHeaderRecord()` field from the `DefaultChatTransport` constructor so it does not freeze a stale token. `prepareSendMessagesRequest` already re-reads the header per call, so per-request sends remain safe under XSRF token rotation.

### WR-10: `AssistantSendCompleted` published outside any transaction

**Files modified:** see CR-02 (same commit)
**Commit:** `c744f42a`
**Applied fix:** Fixed together with CR-02 — `AssistantSendCompleted` is now published inside `ConfirmationStateMachine.commitSendCompleted`'s `@Transactional` boundary. `@TransactionalEventListener(AFTER_COMMIT)` listeners will receive the event reliably on real commit (previously silently dropped because there was no active transaction at publish time).

### WR-11: `StaleToolCallException` returned 404 instead of 409

**Files modified:** `backend/api/src/main/java/com/zeromail/api/controllers/chat/ConfirmController.java`
**Commit:** `348260bb`
**Applied fix:** Moved `StaleToolCallException` into the `confirmationConflict()` handler so a `parts_updated_at` mismatch returns 409 Conflict — semantically distinct from a 404 Not Found and lets the frontend distinguish "stream raced ahead, refresh needed" from "chat/tool call doesn't exist."

## Skipped Issues

None — all 15 in-scope findings were applied.

## Notes for Verifier

- **Logic-bug flag:** CR-02 + WR-10 introduce idempotent semantics for `commitSendCompleted` and move event publication into the same transaction. These are correctness fixes; a Spring Boot test slice should verify (a) double-call of `commitSendCompleted` does not throw, (b) `AssistantSendCompleted` fires exactly once per send via `@RecordApplicationEvents`, (c) the event is NOT published if the row was already `COMMITTED` (reconciler-already-moved case). Existing `AuditAtomicityIT` and `AssistantSendExecutorVipIT` exercise the happy path but a dedicated reconciler-race test would lock in the idempotency guarantee.
- **CR-03 trigger expansion:** the new regex covers common XSS vectors but is signature-based. A future read-tool emitting `payload` or `content` field names is still strip-evadable from the JSON side (this is WR-08 + IN-05 territory, partially addressed by the sanitizer fusion in this iteration). The trigger expansion is a server-side belt; defense in depth still requires strict `streamdown` config on the frontend.
- **WR-07 cap on cap exhaustion:** the new error code `chat_too_many_tool_calls` is not yet i18n-keyed in `apps/web/features/chat/messages.ts`. Verifier should either (a) add the message via the per-feature i18n flow, or (b) accept the English fallback for v1.
- **JetBrains MCP problem check:** not run from the worktree because the IDE indexes the main repo. Recommend running `mcp__jetbrains__get_file_problems` on touched files (especially `ConfirmationStateMachine.java`, `AssistantSendExecutor.java`, `ChatOrchestrator.java`, `ToolOutputSanitizer.java`) after the orchestrator merges the fix branch back to the phase branch.
- **`./gradlew test` not run** during fixing — verifier phase will run the full suite. Per the verification strategy in this agent's contract, only file-level syntax checks were performed; Java compile-level verification is the verifier's responsibility.

---

_Fixed: 2026-05-19_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
