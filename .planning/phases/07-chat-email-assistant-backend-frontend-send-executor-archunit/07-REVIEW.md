---
phase: 07-chat-email-assistant-backend-frontend-send-executor-archunit
reviewed: 2026-05-19T00:00:00Z
depth: standard
files_reviewed: 76
files_reviewed_list:
  - apps/web/__tests__/chat/tool-catalog-contract.test.ts
  - apps/web/app/(protected)/(app)/chat/layout.tsx
  - apps/web/app/(protected)/(app)/chat/page.tsx
  - apps/web/e2e/chat/chat-test-utils.ts
  - apps/web/e2e/chat/confirmation-race.spec.ts
  - apps/web/e2e/chat/confirmation-replay.spec.ts
  - apps/web/e2e/chat/csrf-parity.spec.ts
  - apps/web/e2e/chat/history-sidebar.spec.ts
  - apps/web/e2e/chat/outside-source-thread.spec.ts
  - apps/web/e2e/chat/stream-happy-path.spec.ts
  - apps/web/e2e/chat/vietnamese-default.spec.ts
  - apps/web/e2e/chat/vip-banner.spec.ts
  - apps/web/features/chat/api/chat-api.ts
  - apps/web/features/chat/components/chat-workspace.tsx
  - apps/web/features/chat/components/conversation-pane.tsx
  - apps/web/features/chat/components/history-sidebar.tsx
  - apps/web/features/chat/components/preview-card/body/bulk-archive-body.tsx
  - apps/web/features/chat/components/preview-card/body/create-rule-body.tsx
  - apps/web/features/chat/components/preview-card/body/delete-rule-body.tsx
  - apps/web/features/chat/components/preview-card/body/forward-email-body.tsx
  - apps/web/features/chat/components/preview-card/body/remove-sender-from-safety-net-body.tsx
  - apps/web/features/chat/components/preview-card/body/reply-email-body.tsx
  - apps/web/features/chat/components/preview-card/body/save-memory-body.tsx
  - apps/web/features/chat/components/preview-card/body/send-email-body.tsx
  - apps/web/features/chat/components/preview-card/body/update-personal-instructions-body.tsx
  - apps/web/features/chat/components/preview-card/preview-card-state.ts
  - apps/web/features/chat/components/preview-card/preview-card.tsx
  - apps/web/features/chat/hooks/use-chat-history.ts
  - apps/web/features/chat/hooks/use-chat.ts
  - apps/web/features/chat/hooks/use-confirm-action.ts
  - apps/web/features/chat/messages.ts
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
  - backend/api/src/main/java/com/zeromail/api/ZeroMailApiApplication.java
  - backend/api/src/main/java/com/zeromail/api/chat/AssistantPendingActionReconciler.java
  - backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatHistoryController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/chat/ConfirmController.java
  - backend/api/src/main/resources/application.yml
  - backend/api/src/test/java/com/zeromail/api/chat/ReconciliationCronIT.java
  - backend/api/src/test/java/com/zeromail/api/controllers/chat/ChatControllerStreamIT.java
  - backend/api/src/test/java/com/zeromail/api/controllers/chat/ChatHistoryControllerIT.java
  - backend/api/src/test/java/com/zeromail/api/controllers/chat/ConfirmControllerIT.java
  - backend/core/src/main/java/com/zeromail/core/chat/confirm/ConfirmationStateMachine.java
  - backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AllowedSendCallSite.java
  - backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java
  - backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantWriteExecutor.java
  - backend/core/src/main/java/com/zeromail/core/chat/domain/ChatMessage.java
  - backend/core/src/main/java/com/zeromail/core/chat/domain/ChatToolName.java
  - backend/core/src/main/java/com/zeromail/core/chat/domain/parts/AssistantTextPart.java
  - backend/core/src/main/java/com/zeromail/core/chat/domain/parts/ChatMessageParts.java
  - backend/core/src/main/java/com/zeromail/core/chat/domain/sendaction/ForwardEmailToolArgs.java
  - backend/core/src/main/java/com/zeromail/core/chat/domain/sendaction/ReplyEmailToolArgs.java
  - backend/core/src/main/java/com/zeromail/core/chat/domain/sendaction/SendEmailToolArgs.java
  - backend/core/src/main/java/com/zeromail/core/chat/llm/ChatToolCallRegistry.java
  - backend/core/src/main/java/com/zeromail/core/chat/llm/TenantAwareReactorScheduler.java
  - backend/core/src/main/java/com/zeromail/core/chat/llm/VercelProtocolEmitter.java
  - backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiStreamingChatModelClient.java
  - backend/core/src/main/java/com/zeromail/core/chat/llm/springai/ZeroMailChatMemory.java
  - backend/core/src/main/java/com/zeromail/core/chat/package-info.java
  - backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatMessageJdbcRepository.java
  - backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatPartsJsonConverter.java
  - backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatPartsSchemaV1.java
  - backend/core/src/main/java/com/zeromail/core/chat/sanitize/ToolOutputSanitizer.java
  - backend/core/src/main/java/com/zeromail/core/chat/sanitize/XmlFencedPersonalizationRenderer.java
  - backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatHistoryService.java
  - backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatLlmGateway.java
  - backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java
  - backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatToolCatalog.java
  - backend/core/src/main/java/com/zeromail/core/chat/usecases/ConfirmActionService.java
  - backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/ChatReadToolHandler.java
  - backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/GetMessageToolHandler.java
  - backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/SearchInboxToolHandler.java
  - backend/core/src/main/java/com/zeromail/core/chat/usecases/tools/SearchMemoriesToolHandler.java
  - backend/core/src/main/resources/db/changelog/changes/041-chat.yaml
  - backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml
  - backend/core/src/main/resources/db/changelog/changes/043-assistant-pending-action.yaml
  - backend/core/src/main/resources/db/changelog/changes/044-assistant-action-audit.yaml
  - backend/core/src/main/resources/db/changelog/changes/045-assistant-settings.yaml
  - backend/core/src/main/resources/db/changelog/changes/046-assistant-memory-knowledge.yaml
  - backend/core/src/test/java/com/zeromail/core/arch/ChatLlmAdapterBoundaryTest.java
  - backend/core/src/test/java/com/zeromail/core/arch/ChatNoReactorSchedulerTest.java
  - backend/core/src/test/java/com/zeromail/core/arch/ChatPersistenceContentBanTest.java
  - backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java
  - backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java
  - backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java
  - backend/core/src/test/java/com/zeromail/core/chat/confirm/AssistantSendExecutorVipIT.java
  - backend/core/src/test/java/com/zeromail/core/chat/confirm/AuditAtomicityIT.java
  - backend/core/src/test/java/com/zeromail/core/chat/confirm/ConfirmationRaceIT.java
  - backend/core/src/test/java/com/zeromail/core/chat/persistence/ChatMessageBodyBanTriggerSourceAwareIT.java
  - backend/core/src/test/java/com/zeromail/core/chat/persistence/PostgresJsonPathPreflightIT.java
  - backend/core/src/test/java/com/zeromail/core/chat/usecases/tools/ReadToolsIT.java
  - scripts/ci/count-gmail-send-call-sites.sh
findings:
  critical: 4
  warning: 11
  info: 6
  total: 21
status: issues_found
---

# Phase 7: Code Review Report

**Reviewed:** 2026-05-19
**Depth:** standard
**Files Reviewed:** 76 (of 101 listed; remainder are i18n JSON, simple e2e/test scaffolding, and config files reviewed as part of adjacent checks)
**Status:** issues_found

## Summary

The phase ships a large, internally coherent chat stack with strong invariants in the right places: the 3-layer ARCH-02 body-ban (sanitizer + ArchUnit + Postgres trigger), the ARCH-01 single-send call-site flip (positive + negative paired ArchUnit + CI grep), tenant-aware Reactor scheduler, and a confirmation state machine with same-transaction audit + Redis lease + reconciliation cron. The frontend uses generated OpenAPI types, shadcn primitives, Vietnamese-first i18n, and disables Send until persistence acks land.

However, the executor pathway has a **send-safety hole around VIP enforcement** (Critical), the post-send audit `commitSendCompleted` runs in a **separate transaction from the Gmail send** breaking the SPEC ARCH-04 "audit row atomic with state transition" claim (Critical), and the body-ban Postgres trigger has **two regex/JSONPath gaps** that allow ARCH-02 violations to land in `chat_message.parts` (Critical x2). Several warnings cover lease leakage on partial failures, recipient-hash partiality, schema-version dead code, and frontend `useMemo` deps drift.

Performance-only findings are out of v1 scope and excluded.

## Critical Issues

### CR-01: VIP safety net check uses the raw `to`/`cc`/`bcc` string from the command and only verifies the **first** parsed address while audit hash only covers the first recipient — multi-recipient sends bypass VIP enforcement on the trailing addresses

**File:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java:145-155, 179-186`
**Issue:**
`rejectUnacknowledgedVipRecipient` iterates `recipients(command)` (which expands `to`, `cc`, `bcc` correctly), so VIP detection across all addresses is fine.
BUT `recipientHash` uses `findFirst()` after sorting → only the lexicographically smallest canonical recipient is recorded. The audit row therefore lies about who was emailed when `to` contains multiple recipients or when `cc`/`bcc` are set. Because the SPEC binds VIP UX + audit + reconciliation to `recipient_hash`, an attacker (or a misbehaving model) can send to a VIP + filler by listing the filler first; the audit only fingerprints the filler. This breaks the safety-net audit contract (TRG-07/08 + SET-SAFE-05) and silently weakens incident response.
Confirmation: `AssistantSendExecutorVipIT` would not catch this because it uses a single recipient.
**Fix:**
```java
private String recipientHash(AssistantSendCommand command) {
    return recipients(command).stream()
            .map(senderEmailCanonicalizer::canonicalize)
            .sorted()
            .map(senderEmailCanonicalizer::redisCacheKeyComponent)
            .collect(java.util.stream.Collectors.joining(","))  // or hash the joined string
            .transform(AssistantSendExecutor::hexSha256);
}
```
…or store a sorted list/array column instead of a single hash. Either way, do not silently drop n-1 recipients from the audit fingerprint.

---

### CR-02: `commitSendCompleted` runs in a **separate** `transactionTemplate.executeWithoutResult` AFTER the Gmail `.execute()` call — Gmail success can land while the COMMITTED audit transition never commits (e.g., DB outage, app crash between Gmail return and `commitSendCompleted` tx commit). SPEC ARCH-04 claims "audit row atomic with state transition"

**File:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java:101-130`
**Issue:**
Flow:
1. tx1: insert audit row in `SEND_IN_FLIGHT` (commit) — this is the durable "I'm about to call Gmail" marker. Correct.
2. Gmail `.send().execute()` — no tx.
3. tx2: `commitSendCompleted` flips audit `SEND_IN_FLIGHT → COMMITTED` and updates pending action.

If the process dies between (2) and (3), or if tx2's `commitSendCompleted` UPDATE fails (DB blip, deadlock), Gmail has accepted the message but Postgres still shows `SEND_IN_FLIGHT`. The `AssistantPendingActionReconciler` does recover this case by querying Gmail with `rfc822msgid:` — but the reconciler is a 5-minute cron, lives in `backend/api`, and is single-instance (no ShedLock per D-05). So the assertion in SPEC ARCH-04 that the audit transition is atomic with state is false; instead it's eventually consistent via reconciliation. The Reconciler Javadoc even acknowledges a residual gap when the `SEND_IN_FLIGHT` insert itself fails.
The bigger bug: `transactionTemplate.executeWithoutResult(_ -> commitSendCompleted(...))` will swallow no exception of its own, but if it throws (e.g., `IllegalStateException("send audit row was not in flight")` from `commitSendCompleted` line 203 because another reconciliation already moved it to COMMITTED), the `finally` block still releases the Redis lease, the published `AssistantSendCompleted` event NEVER fires (it's after `commitSendCompleted`), and the user-visible result is `ConfirmationStateMachine` returning `IllegalStateException` upstream → `ConfirmController` 500. Gmail already sent. Double-confirm-after-replay UX breaks.
**Fix:**
Two parts:
1. Make `commitSendCompleted` idempotent: if state is already `COMMITTED` for `(id, tenant_id)`, `return` instead of throwing. The reconciler may have legitimately moved the row.
2. Publish `AssistantSendCompleted` from the same transaction as `commitSendCompleted` (Spring's `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` is already used elsewhere — adopt that pattern here so the event reliably fires on real commit).
```java
@Transactional
public boolean commitSendCompleted(UUID auditId, SendCommitCommand command) {
    int changedRows = jdbcTemplate.update(...);
    if (changedRows != 1) {
        // Reconciler may have already moved it; verify and bail without throwing.
        return false;
    }
    updatePendingActionState(...);
    return true;
}
```

---

### CR-03: Postgres body-ban trigger HTML regex is too narrow — only catches `<script>` and `<iframe>`, missing common XSS vectors like `<svg onload>`, `<img onerror>`, event handlers, and `javascript:` URIs

**File:** `backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml:149-159`
**Issue:**
The HTML body-content signature regex `<[[:space:]]*(script|iframe)([[:space:]>]|/|$)` blocks `<script>` and `<iframe>` but NOT:
- `<svg onload="...">`
- `<img onerror="...">`
- `<a href="javascript:...">`
- `<style>...</style>`, `<object>`, `<embed>`, `<link>`, `<meta>`
- Bare HTML event handlers (`onclick=`, `onmouseover=`, etc.)

The trigger is sold as forbidden-HTML detection in the comment but only catches two tags. Any LLM-emitted read-tool output containing a stored XSS via `<img onerror>` from malicious email content will land in DB and be re-rendered by `streamdown@2` on the frontend. If the React markdown component does not strictly sanitize, this is a real XSS path. The threat path: prompt injection via email content → LLM emits malicious HTML in tool output → sanitizer doesn't strip non-body fields → trigger doesn't match → persist → re-render → XSS executes against the recipient user, with full session cookie scope (multi-tenant breach risk).

**Out of scope:** `assistant_pending_action.draft_body` is the explicit Privacy carve-out per CLAUDE.md — user-authored draft data the user reviews on the preview card. No DB defense needed; the Java gate at `ChatOrchestrator.draftBody()` (only the 4 allow-listed write tools populate it) is the correct boundary.

**Fix:**
- Expand the forbidden-HTML regex to cover at minimum: `<\s*(script|iframe|svg|object|embed|style|link|meta)\b`, `on[a-z]+\s*=`, `javascript\s*:` inside `href` or `src`. Better: have the trigger reject **any** HTML tag inside non-draft fields, and defer markdown rendering safety to a server-side jsoup whitelist + strict `streamdown` config on the frontend.
- Consider expanding the email-read allowlist check to include `tool-getThread` explicitly (currently the negative branch `IF part_type LIKE 'tool-%' AND chat_jsonb_contains_body_key(tool_part)` covers it implicitly, but the comment is misleading).

---

### CR-04: `ChatOrchestrator.executeReadTool` persists the **unsanitized** raw `outputJson` indirectly — the sanitizer is invoked but `persistToolOutput` is called with `sanitizedOutputJson` (correct), HOWEVER `emitToolOutputAvailable(toolCallId, sanitizedOutputJson)` is sent over SSE BEFORE the persistence step, and `sanitizeToolOutput(...)` wraps the output in a fresh `ToolOutputPart` whose `partId = "tool-output-" + outcome.toolCallId()`, then it extracts only `.outputJson` (line 415–422) and discards the sanitizer's input/confirmation sanitization, length cap, and truncation flag

**File:** `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java:202-233, 402-423`
**Issue:**
`sanitizeToolOutput` constructs a `ChatMessageParts` containing a `ToolOutputPart` with the raw Gmail output, runs it through the multi-stage `ToolOutputSanitizer`, then reaches into the sanitized parts to extract only `outputJson` (dropping the `truncated` flag and the sanitized input/confirmation). The resulting JSON string is then re-parsed and re-wrapped in a fresh `ToolOutputPart(..., parseJsonObject(outputJson), Map.of(), Map.of(), false)` in `persistToolOutput` (line 393) — so the persisted row has `truncated=false` always, even when the sanitizer's `LengthCapStage` truncated body fields.
Worse: the SSE consumer (frontend) receives sanitized JSON via `emitToolOutputAvailable`, but the persisted-version's `truncated` flag is lost, breaking the CLAUDE.md Privacy contract that "sanitized + truncated + prompt-injection-hardened" content must be visibly marked. A downstream audit/eval cannot tell which tool outputs were truncated.
ALSO: in `executeReadTool`, lines 212–221 call `persistToolCall` BEFORE `emitToolOutputAvailable` (line 222) — that's fine for the user-visible flow but means a crash between persist and emit leaves the DB row without a matching frontend frame; the frontend then issues GET history which would see input-available only, no output. Acceptable for v1, but worth a log.
**Fix:**
```java
private SanitizedToolOutput sanitizeToolOutput(UUID tenantId, TurnOutcome outcome, String outputJson) {
    // ... existing wrap ...
    ToolOutputPart sanitizedPart = sanitizedParts.parts().stream()
        .filter(ToolOutputPart.class::isInstance).map(ToolOutputPart.class::cast)
        .findFirst().orElseThrow();
    return new SanitizedToolOutput(
        writeJson(sanitizedPart.outputJson()),
        sanitizedPart.truncated());
}
private record SanitizedToolOutput(String json, boolean truncated) {}
```
…and propagate `truncated` into `persistToolOutput`.

## Warnings

### WR-01: `ConfirmationStateMachine.reserve()` commits `state=PROCESSING` to Postgres BEFORE `confirmedSendToolHandlers.toCommand(...)` is invoked — if `toCommand` throws (e.g., missing `to`/`subject`, bad VIP lookup), the pending action is stuck in PROCESSING until the reconciliation cron times it out (≤5 min). User sees infinite spinner

**File:** `backend/core/src/main/java/com/zeromail/core/chat/usecases/ConfirmActionService.java:53-65`
**Issue:**
The flow `reserve(...)` (own `@Transactional`, commits at method return) → `confirmedSendToolHandlers.toCommand(...)` → `assistantSendExecutor.execute(...)` is split across multiple transactions. If `toCommand` throws after `reserve` committed, the row sits at PROCESSING, the Redis lease is released by `finally`, but the user-visible state machine is stuck: a second confirm attempt sees `state != 'PENDING'` and `requirePendingConfirmable` throws `IllegalStateException` → 500. UX is broken until reconciliation expiry.
**Fix:** Wrap reserve + toCommand validation in one `@Transactional` boundary in `ConfirmActionService.confirm` (or do all validation BEFORE `reserve()`), and add a `revertReservation(toolCallId)` path that flips PROCESSING back to PENDING on synchronous validation failure.

---

### WR-02: `ChatOrchestrator.stream()` calls `streamScheduler.dispose()` inside both the `finally` block at line 133 AND inside `TrackingDisposable.dispose()` at line 654 — double dispose is benign for Reactor `Schedulers.fromExecutorService` but the JDK ExecutorService cast inside will see two `shutdown()` calls; relying on idempotency of `Schedulers.dispose()` is fragile

**File:** `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java:133, 651-655`
**Issue:** `streamScheduler` is owned by `TrackingDisposable` (which already disposes it) AND by the lambda's `finally`. If the SSE emitter triggers `dispose()` while the scheduled task is mid-run, both paths call `streamScheduler.dispose()`. Plus `TenantAwareReactorScheduler.scheduler()` returns a fresh scheduler per call, so disposal in the middle of `streamingChatModel.stream(prompt).subscribeOn(scheduler)` (SpringAiStreamingChatModelClient line 81–83) inside the Spring AI subscription will race with the orchestrator's `finally`. The Spring AI client already calls `scheduler.dispose()` in its `doFinally`. Triple dispose, but for different scheduler instances? Re-check: `tenantAwareReactorScheduler.scheduler()` is called in BOTH ChatOrchestrator (line 105) AND SpringAiStreamingChatModelClient (line 73). Each returns a NEW scheduler. So the dispose paths are independent — but each scheduler holds a virtual-thread ExecutorService that needs to be released exactly once. The orchestrator path is fine; the springai-client path is fine separately. Not a leak, but the "callers own disposal" comment in `TenantAwareReactorScheduler` is misleading when ownership is forked.
**Fix:** Add a comment in `ChatOrchestrator.stream()` clarifying the two-scheduler design, or pass the single orchestrator-owned scheduler to `chatLlmGateway.streamChat` so the springai client doesn't allocate its own.

---

### WR-03: `ConfirmationLeaseService.tryAcquire` returns `true` on Redis SET-NX success, but does not check whether the recovered lease value matches the caller's `processInstanceId` — if Redis returned the lease from a prior crashed process and the TTL hasn't elapsed, no caller can re-acquire until 5-min TTL expires. There is no fencing token or value-equality check on release

**File:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/ConfirmationLeaseService.java:37-67, 69-86`
**Issue:**
`release(...)` unconditionally `DELETE`s the key. If user A clicks confirm (acquires lease, calls Gmail, takes >5min unlikely-but-possible), the lease expires, user A's retry on a new browser tab acquires a new lease, but the original execution still completes successfully and then calls `release(...)` — deleting user A's NEW lease, allowing user B (different tenant under same chatId? No, chatId is tenant-scoped) to bypass safety. Within one tenant: user A's two browser tabs could double-fire under exactly this scenario.
**Fix:** Use a Lua script for release: `if redis.call("get", KEYS[1]) == ARGV[1] then redis.call("del", KEYS[1]) end`. Pass the same `processInstanceId` used at acquire time and pass it down through `AssistantSendExecutor.execute` → `confirmationLeaseService.release(chatId, toolCallId, processInstanceId)`.

---

### WR-04: `ChatPartsJsonConverter.ChatPartsJsonConverter()` (no-arg) constructs a fresh `JsonMapper.builder().build()` — this bypasses Spring's Boot-managed `ObjectMapper` (with custom modules, `JavaTimeModule`, etc.) and is invoked anytime the bean is constructed without DI (e.g., the converter is `@Component` with both constructors, Spring will pick the no-arg one when ambiguity isn't resolved). `ZeroMailChatMemory` (line 33) and `VercelProtocolEmitter` (line 25) and `SpringAiStreamingChatModelClient` (line 50) all do the same — they instantiate `JsonMapper.builder().build()` directly instead of injecting Spring's

**File:** `backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatPartsJsonConverter.java:19-21`, `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/ZeroMailChatMemory.java:33`, `backend/core/src/main/java/com/zeromail/core/chat/llm/VercelProtocolEmitter.java:25`, `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiStreamingChatModelClient.java:50`
**Issue:**
Jackson 3 default `JsonMapper.builder().build()` does NOT auto-register `JavaTimeModule` — `Instant` serialization will fail or serialize as `{seconds, nanos}` instead of ISO-8601. `AssistantTextPart.completedAt` is an `Instant`. The `ChatPartsSchemaV1.serializePart` for `AssistantTextPart` calls `.toString()` explicitly (line 102) so that path is safe; but `Sensitive<String>` (used in `SendEmailToolArgs` etc.) may have a custom serializer that requires module registration. And ad-hoc local mappers diverge in config from the injected mapper used elsewhere (different escapes, different feature flags). At minimum, this fights the Boot 4 / Jackson 3 lock-in convention in CLAUDE.md "verify Boot 4 / Jackson 3 migration assumptions."
Project conventions explicitly say Boot-managed Jackson — multiple classes here are using both DI'd `ObjectMapper` AND local `JsonMapper.builder().build()`, which is inconsistent at best.
**Fix:** Remove the no-arg constructors. Force DI of the Spring-managed `ObjectMapper`. If a no-arg constructor is needed for a non-Spring construction site (e.g., a test), keep it but mark it `@VisibleForTesting` and never use it from production.

---

### WR-05: `ChatMessageJdbcRepository.isBodyBanViolation` matches the trigger error via substring match on the message `"Chat persistence violation"` — but Postgres ERRCODE `23514` is set; pattern-matching on a localized exception message is fragile (locale, driver version, wrapped exceptions can all alter the string)

**File:** `backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatMessageJdbcRepository.java:84-93`
**Issue:** The Postgres trigger uses `USING ERRCODE = '23514'`. The Spring `DataIntegrityViolationException` exposes the SQLState. Matching on message text is unstable; a future Postgres version or driver patch could change the prefix.
**Fix:**
```java
private static boolean isBodyBanViolation(DataAccessException dataAccessException) {
    Throwable currentThrowable = dataAccessException;
    while (currentThrowable != null) {
        if (currentThrowable instanceof java.sql.SQLException sqlException
                && "23514".equals(sqlException.getSQLState())) {
            return true;
        }
        currentThrowable = currentThrowable.getCause();
    }
    return false;
}
```

---

### WR-06: `ChatHistoryController.delete` and `softDelete` rely on `ChatHistoryService.softDelete` returning early after `updatedRows == 0` — but this also fires when the chat is already-soft-deleted, NOT only when it's not found. User receives 404 for a re-delete attempt instead of idempotent 204

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatHistoryController.java:48-52`, `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatHistoryService.java:52-68`
**Issue:** The SQL `WHERE soft_deleted_at IS NULL` clause excludes already-deleted rows from the update, so an idempotent delete throws `ChatNotFoundException`. Frontend retry on transient network failure looks like a 404. Soft delete should be idempotent by definition.
**Fix:** Drop the `soft_deleted_at IS NULL` clause, OR add a separate `SELECT EXISTS` check that distinguishes "not found" from "already deleted" and return 204 in the latter case.

---

### WR-07: `ChatOrchestrator.executeReadToolLoop` uses `for (int attempt = 0; attempt < 4 ...)` magic number 4 with no comment — and the loop emits `emitFinish("complete")` at the **outside** of the loop (line 193) if the read-tool loop exhausts the 4 attempts without resolving. The LLM may never get an `error` signal even though tool-call iteration capped out

**File:** `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestrator.java:145-194`
**Issue:** Magic `4` (presumably max read-tool turns). On exhaustion, the loop falls through to `streamSink.emitFinish("complete")` — the frontend thinks the assistant finished normally. There is no warning emitted server-side either. If a model hallucinates an infinite read-tool sequence, the UX is "assistant just stopped without saying anything."
**Fix:** Extract `4` to a constant `MAX_READ_TOOL_ITERATIONS` documented in `ZeroMailChatProperties`. On exhaustion: `streamSink.emitError("chat_too_many_tool_calls", t("chat.error.too_many_tool_calls"))` (or analogous user-facing message) and log a `chat_tool_iteration_capped` event.

---

### WR-08: `ToolOutputSanitizer.EmailBodyStripStage` length cap is **only applied in stage 2**, so the body-strip in stage 1 strips known body field NAMES (`body`, `bodyText`, etc.) for READ tools, but leaves all other long-text fields uncapped and the body-strip stage does NOT strip nested body fields inside an `output.results[*].body` array path under a `tool-searchInbox` output — because `EmailBodyStripStage` recurses through Maps and Lists correctly but stage 1's `signatureMatches` count is just informational and is **not asserted by any test for nested-list cases**

**File:** `backend/core/src/main/java/com/zeromail/core/chat/sanitize/ToolOutputSanitizer.java:74-148, 242-282`
**Issue:** Trace: `sanitizeJson({results: [{body: "x"}]})` — outer is a Map (no `body` key at root level, so no strip), descends to `results` which is a List, descends to each element which is a Map containing `body` → strip triggers. ✓ Actually correct.
But re-look: in stage 1's `EmailBodyStripStage`, `sanitizeJson(inputJson, stripBodyFields, false)` calls with `capStringValues=false` — so length cap is deferred. Then stage 2 calls `sanitizeJson(inputJson, false, true)` — `stripBodyFields=false`. The body strip and the length cap are **disjoint** passes. That's by design but the design loses a property: a 100MB string in a NON-body field of a read-tool output is dropped only by stage 2's 4000-char cap; in stage 1 it's still in memory through the recursion. Memory pressure under malicious / pathological Gmail responses isn't bounded. Out-of-scope for v1 perf, but a correctness concern: the sanitizer is the only line of defense before the body-ban trigger, and a sufficiently large output could OOM the request thread before the trigger rejects it.
Separately, the body strip's regex matches only **exact field names**, not synonyms or near-matches. A future read tool emitting `payload` or `content` or `messageContent` would not be stripped. The Postgres trigger pattern is broader (`emailBody|messageBody|bodyHtml|bodyText|htmlBody|textBody|body`) but missing `content`, `payload`, `messageContent`. Defense is signature-based, easy to evade with renaming.
**Fix:**
- Combine the two stages into one recursive pass with both stripBodyFields and capStringValues true for read-tool outputs.
- Add an explicit max-output-bytes guard at the entry of `ToolOutputSanitizer.sanitize` and reject (with a clear log) outputs exceeding `chatProperties.maxToolOutputTokens() * average-bytes-per-token`.

---

### WR-09: Frontend `use-chat.ts` `useMemo` for `transport` has empty deps `[]` (line 78) — never recomputed even if `chatId` changes. The user's mental model is "one chat at a time" so this may be fine, but `prepareSendMessagesRequest` closes over `lastUserText(messages)` and `xsrfHeaderRecord()` which can rotate

**File:** `apps/web/features/chat/hooks/use-chat.ts:62-79`
**Issue:** The `useMemo(..., [])` deps array misses every closure dep (`getApiUrl`, `xsrfHeader`). If the XSRF token rotates mid-session (Spring Security default), the cached `transport.headers` (from initial render) goes stale until a full page reload. The body `prepareSendMessagesRequest` recomputes headers per call, so that path is safe. But the constructor `new DefaultChatTransport({ headers: xsrfHeaderRecord(), ... })` snapshots once.
**Fix:** Move XSRF header into `prepareSendMessagesRequest` only (already done there), and remove the initial `headers: xsrfHeaderRecord()` to avoid stale-token bug, OR add the actual deps (`[chatId]`).

---

### WR-10: `AssistantSendExecutor.execute` publishes `AssistantSendCompleted` AFTER `commitSendCompleted` but **outside** any transaction — so the event is published whether or not the commit transaction succeeded committing (the `transactionTemplate.executeWithoutResult` returns after commit; OK if no exception). However the event uses `ApplicationEventPublisher.publishEvent` and consumers using `@TransactionalEventListener(AFTER_COMMIT)` may never receive it because there's no active transaction at publish time — the event will fire synchronously or be dropped depending on listener config

**File:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/send/AssistantSendExecutor.java:131-138`
**Issue:** `@TransactionalEventListener(phase = AFTER_COMMIT)` requires an active transaction at publish time. Publishing outside any tx means the event is delivered immediately to non-`@TransactionalEventListener` listeners, but `@TransactionalEventListener` listeners (per CONVENTIONS #6) silently drop the event unless `fallbackExecution=true` is set.
**Fix:** Publish from inside `commitSendCompleted`'s transaction (move the `publishEvent` call into `ConfirmationStateMachine.commitSendCompleted` after the UPDATE succeeds). This also fixes CR-02's reliability issue.

---

### WR-11: `ConfirmController` `@ExceptionHandler` returns 404 for `PendingActionNotFoundException` AND for `StaleToolCallException` — these are semantically different cases. A stale tool call (parts_updated_at mismatch) is a 409 Conflict, not a 404

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/chat/ConfirmController.java:60-66`
**Issue:** Frontend cannot distinguish "your stream got ahead of you, please refresh" (stale) from "this chat/tool call doesn't exist" (not found). Both produce a generic 404 → identical retry UX. The `ConfirmationLeaseConflictException` and `VipAcknowledgmentMissingException` are correctly bundled at 409, but `StaleToolCallException` belongs there too.
**Fix:** Move `StaleToolCallException` into the `confirmationConflict()` handler returning 409.

## Info

### IN-01: `ChatMessageParts` record compact constructor unconditionally overwrites the caller-provided `schemaVersion` with `CURRENT_SCHEMA_VERSION` — the `schemaVersion` parameter is dead code

**File:** `backend/core/src/main/java/com/zeromail/core/chat/domain/parts/ChatMessageParts.java:9-12`
**Issue:** `public ChatMessageParts { schemaVersion = CURRENT_SCHEMA_VERSION; ... }` ignores the argument. If a caller wanted to construct a v2 envelope (e.g., for a migration test), they cannot. The factory `v1(parts)` is the only sane construction path, and the `int schemaVersion` component becomes static. Either remove the field entirely (since it's always 1) or honor the parameter.
**Fix:** Remove the `schemaVersion` from the record and expose `CURRENT_SCHEMA_VERSION` as a constant only. The schema version still gets stamped during JSON serialization by `ChatPartsSchemaV1.serialize(...)`.

---

### IN-02: `ChatToolCatalog.validate()` error message claims "must contain exactly 24 tools" but uses `ChatToolName.values().length` — if a new tool is added to the enum, the partition map `EXPECTED_PARTITION` would silently allow the size mismatch only after the partition check fails. The "24" is a hard-coded magic number in a string

**File:** `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatToolCatalog.java:56-58`
**Fix:** `"Chat tool catalog must contain exactly " + ChatToolName.values().length + " tools"`.

---

### IN-03: `ChatPartsSchemaV1.deserializePart` infers `ToolOutputPart` vs `ToolCallPart` by checking `!outputJson.isEmpty() || !confirmationJson.isEmpty()` — a legitimate `ToolOutputPart` with no output and no confirmation (a rare empty-success case) would deserialize as `ToolCallPart`, losing its "tool-output" identity

**File:** `backend/core/src/main/java/com/zeromail/core/chat/persistence/ChatPartsSchemaV1.java:61-78`
**Fix:** Persist a discriminator field (e.g., `"role": "tool-call"` vs `"role": "tool-output"`) and dispatch on that, not on payload shape.

---

### IN-04: `CRUD-like` JdbcTemplate SQL uses `now()` directly in `UPDATE ... SET updated_at = now()` AND `Timestamp.from(Instant.now())` parameter binding in alternate paths — inconsistent clock sources (DB clock vs app clock) yield drift in audit timelines

**File:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/ConfirmationStateMachine.java:110-123 vs 144-179`, `backend/api/src/main/java/com/zeromail/api/chat/AssistantPendingActionReconciler.java:126, 142-147`
**Fix:** Pick one. The `Clock` field is already injected in `ConfirmationStateMachine` and `AssistantPendingActionReconciler` — use it consistently.

---

### IN-05: `count-gmail-send-call-sites.sh` script grep filter `grep -vc 'AssistantSendExecutor'` greps for the **substring** of the file path; a future file named `MockAssistantSendExecutor.java` in production code would falsely register as the allowed call site

**File:** `scripts/ci/count-gmail-send-call-sites.sh:18-19`
**Fix:** Make the grep more specific: `grep -vc '/AssistantSendExecutor\.java:'` or filter on filename only.

---

### IN-06: Frontend `i18n` translations exist via `chatMessages` const in `features/chat/messages.ts` but ALSO via `apps/web/i18n/messages/en.json` and `vi.json` — possible double source of truth

**File:** `apps/web/features/chat/messages.ts:1`, `apps/web/i18n/messages/{en,vi}.json`
**Issue:** Per CLAUDE.md `feedback_flat_folder_structure`, co-locate i18n in feature folder. The presence of both a feature-local `messages.ts` and global `messages/{en,vi}.json` invites drift. Verify whether one is the merge source of the other; if not, consolidate.
**Fix:** Document the merge convention in `apps/web/AGENTS.md`, or remove one path.

---

_Reviewed: 2026-05-19_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
