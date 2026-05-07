---
phase: 02C-llm-gateway
plan: 06
type: execute
wave: 5
depends_on: [03, 05]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayCreditLifecycleTest.java
autonomous: true
requirements: [LLM-05, LLM-06, LLM-11]
must_haves:
  truths:
    - "Platform-path call site reserves credits via Phase 2B CreditLedger.reserve(tenantId, callSite) BEFORE the ChatClient call; on success, settles; on any exception (including SafetyViolationException, SanitizationException, RuntimeException), releases — never leaks a held reservation"
    - "BYOK path bypasses the credit ledger entirely (LLM-05, LLM-11): no reserve, no settle, no release"
    - "When CreditLedger.reserve throws InsufficientCreditsException, LlmGatewayImpl re-throws it; GlobalExceptionHandler maps to HTTP 402 (existing Phase 2B mapping, preserved by Plan 05); no model call is issued"
    - "driftCheck() does NOT touch the ledger (D-E3) — it's a platform-cost operation, not user-billable"
    - "Concurrent virtual-thread calls for the same tenant never double-charge: 100 parallel chat() calls under StructuredTaskScope produce exactly 100 reserve+settle pairs (or reserve+release on failure), reconciled via Phase 2B advisory-lock-protected ledger"
    - "Privacy log on credit failure: event=llm_call_blocked_insufficient_credits tenantId={} callSite={} (mirror of D-I1; no balance amount in log)"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java"
      provides: "Final LlmGatewayImpl with sanitize → BYOK-or-platform-with-ledger → tool-call validate flow"
      contains: "creditLedger.reserve"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java"
      to: "backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java"
      via: "constructor injection + reserve/settle/release wrapping the platform call"
      pattern: "creditLedger\\.(reserve|settle|release)"
---

<objective>
Wave 5 credit cap wiring. Wrap the platform-path call site in `LlmGatewayImpl.chat()` with `CreditLedger.reserve` → try → `settle`/`release` per the cross-phase contract documented in `CreditLedger.java` Javadoc. Confirm BYOK path skips the ledger entirely (already short-circuits in Plan 05). Add a Phase 2B-style integration test proving credit lifecycle correctness under concurrent calls.

Purpose: this is LLM-06 (per-tenant daily LLM spend cap blocks billable calls when exceeded — implemented as ledger-IS-the-cap per SPEC.md Constraint "No separate `daily_spend_cap_usd` table"; Phase 2B's CreditLedger handles the per-call deduction and the cap is implicit when balance hits zero), LLM-05 (BYOK billing skip — verified by negative path test), and LLM-11 (UI surfaces credit-depleted state — backend-side error code is the contract; Plan 08 surfaces in UI).

Output: `LlmGatewayImpl` final wiring (the // Plan 06 markers from Plan 03 are now real code) + 1 integration test file covering 5 lifecycle scenarios.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/phases/02C-llm-gateway/02C-CONTEXT.md
@.planning/phases/02C-llm-gateway/02C-PATTERNS.md
@.planning/phases/02C-llm-gateway/02C-AI-SPEC.md
@.planning/phases/02C-llm-gateway/02C-03-SUMMARY.md
@.planning/phases/02C-llm-gateway/02C-04-SUMMARY.md
@.planning/phases/02C-llm-gateway/02C-05-SUMMARY.md
@backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
@backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java
@backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java
@backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java
@backend/core/src/main/java/com/zeromail/core/billing/model/ReservationId.java
@backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java
@backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java

<interfaces>
<!-- From Phase 2B (already on disk) -->
- `CreditLedger.reserve(UUID tenantId, CallSite callSite) → ReservationId` — throws `InsufficientCreditsException` when tenant balance is insufficient.
- `CreditLedger.settle(ReservationId reservationId)` — closes the reservation as a successful charge.
- `CreditLedger.release(ReservationId reservationId)` — refunds the reservation back to available balance.
- `ReservationId` — record wrapping a UUID.
- `InsufficientCreditsException` — already mapped to HTTP 402 in `GlobalExceptionHandler.java` lines 130-138 (Phase 2B; preserved by Plan 05).

<!-- Plan 03 marker comments inside LlmGatewayImpl -->
- `// Plan 06 will add: ReservationId reservation = creditLedger.reserve(tenantId, callSite);`
- `// Plan 06 will add: try { ... creditLedger.settle(reservation); ... } catch (...) { creditLedger.release(reservation); throw; }`
- These markers wrap the platform-path call site that was Plan 03's main code path.

<!-- From Plan 05 -->
- BYOK branch in chat() returns BEFORE the platform path is reached → ledger is naturally skipped on BYOK path.

<!-- Phase 2B test patterns -->
- `CreditLedgerSettleIdempotentTest` — uses `PostgresContainerTest` + `RestClient` + `LocalServerPort` + `TenantContext` ScopedValue binding. Pattern reused here.
- Pattern S-6 (PATTERNS.md): `TenantContext.currentOrThrow()` resolution at every entry point.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Wire CreditLedger reserve/settle/release into LlmGatewayImpl platform path + integration test</name>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java (Plan 03 + Plan 04 + Plan 05 — find `// Plan 06 will add:` markers; current chat() flow: sanitize → BYOK branch (returns early) → platform call → parseToolCall via validator)
    - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java (lines 25-64 — reserve/settle/release impl with @Transactional(REQUIRES_NEW) — pattern reference for PATTERNS.md "LlmGatewayImpl wiring contract")
    - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java (interface Javadoc D-D1 — verbatim the lifecycle pattern Plan 06 must replicate)
    - backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java (RestClient + LocalServerPort + TenantContext pattern)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-A1 platform path, D-E3 driftCheck-bypasses-ledger)
    - .planning/phases/02C-llm-gateway/02C-PATTERNS.md ("LlmGatewayImpl wiring contract" — full code block)
    - .planning/phases/02C-llm-gateway/02C-AI-SPEC.md (Section 1 critical failure mode #5 — credit-cap circumvention)
  </read_first>
  <behavior>
    - Test 1 (LlmGatewayCreditLifecycleTest#platform_call_reserves_then_settles_on_success): tenant with sufficient credits + no BYOK row; mock ChatModel returns label tool call; verify CreditLedger.reserve called once → settle called once → release NEVER called. Asserts via `@SpyBean CreditLedger` interaction count.
    - Test 2 (LlmGatewayCreditLifecycleTest#platform_call_releases_on_safety_violation): mock returns `send` action → SafetyViolationException → reserve called once → release called once → settle NEVER called. ToolCallResult never returned.
    - Test 3 (LlmGatewayCreditLifecycleTest#platform_call_releases_on_sanitization_exception): mock SanitizationPipeline throws SanitizationException → reserve must NOT be called (sanitization runs before reserve in chat() flow per Plan 03 ordering). Verify `verify(creditLedger, never()).reserve(...)`. Wait — re-check ordering: Plan 03 chat() does `sanitize → BYOK branch → reserve → call → settle/release`. Sanitization BEFORE reserve is intentional: a sanitization failure is a fail-fast that should NOT consume credits. Re-state: if sanitization throws, no reserve happens. **Integration test asserts this ordering.**
    - Test 4 (LlmGatewayCreditLifecycleTest#byok_path_does_not_touch_ledger): tenant with BYOK row; verify `verify(creditLedger, never()).reserve(...)`, `verify(creditLedger, never()).settle(...)`, `verify(creditLedger, never()).release(...)` for the entire BYOK call path.
    - Test 5 (LlmGatewayCreditLifecycleTest#insufficient_credits_throws_402_path): tenant with 0 credits + no BYOK; CreditLedger.reserve throws InsufficientCreditsException → LlmGatewayImpl.chat() re-throws it → through controller surface, HTTP response is 402 (existing Phase 2B mapping). Mock ChatModel verifies it was NEVER invoked (no model call when reserve fails).
    - Test 6 (LlmGatewayCreditLifecycleTest#concurrent_100_calls_balance_reconciles): 100 concurrent virtual-thread chat() calls for the SAME tenant via StructuredTaskScope; mock ChatModel returns label for half + throws RuntimeException for the other half; assert (a) reserve called 100 times, (b) settle called ~50 times, (c) release called ~50 times, (d) tenant final balance == initial - 50 (the settled half). Phase 2B's advisory-lock-protected ledger handles concurrency; this test verifies LlmGatewayImpl plays correctly with it.
    - Test 7 (LlmGatewayCreditLifecycleTest#driftCheck_does_not_touch_ledger): call `gateway.driftCheck("hello")` → assert `verify(creditLedger, never()).reserve(...)` etc. (D-E3).
  </behavior>
  <action>
    1. **Modify `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java`** at the `// Plan 06 will add:` markers:
       
       (a) Add `private final CreditLedger creditLedger;` field; add to constructor; remove the corresponding marker comment.
       
       (b) Replace the platform-path block in `chat()` with the full lifecycle wrap. After the BYOK branch (Plan 05) returns early, the platform path becomes:
       ```java
       // Sanitization already ran above; if it failed, we never reach this point — no reserve happens, no credit lost
       ReservationId reservation = creditLedger.reserve(tenantId, callSite);   // Throws InsufficientCreditsException → 402
       try {
           OpenAiChatOptions perCallOptions = OpenAiChatOptions.builder()
                   .model(model)
                   .toolChoice("required")                       // Plan 04 Layer 1
                   .internalToolExecutionEnabled(false)
                   .build();

           long startNanos = System.nanoTime();
           log.info("event=llm_call_started tenantId={} callSite={} provider={} model={}",
                   tenantId, callSite, provider, model);

           ChatResponse chatResponse = platformChatClient.prompt()
                   .user(sanitized.content())
                   .toolCallbacks(tools)
                   .options(perCallOptions)
                   .call().chatResponse();

           ToolCallResult result = parseToolCall(chatResponse);     // Plan 04 ActionValidator — throws SafetyViolationException on bad tool call

           long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
           Usage usage = chatResponse.getMetadata().getUsage();
           log.info("event=llm_call_succeeded tenantId={} callSite={} latencyMs={} promptTokens={} completionTokens={} stopReason={} truncated={}",
                   tenantId, callSite, latencyMs,
                   usage.getPromptTokens(), usage.getGenerationTokens(),
                   chatResponse.getResults().get(0).getMetadata().getFinishReason(),
                   sanitized.truncated());

           creditLedger.settle(reservation);
           return result;
       } catch (SafetyViolationException safetyViolation) {
           creditLedger.release(reservation);
           log.error("event=llm_safety_violation tenantId={} callSite={} reason={}",
                   tenantId, callSite, safetyViolation.getClass().getSimpleName());
           throw safetyViolation;
       } catch (RuntimeException callFailure) {
           creditLedger.release(reservation);
           log.warn("event=llm_call_failed tenantId={} callSite={} reason={}",
                   tenantId, callSite, callFailure.getClass().getSimpleName());
           throw callFailure;
       }
       ```
       Critical: the SafetyViolationException catch block ALSO releases — Plan 04's catch block is now wrapped inside the reserve/release lifecycle. Without this, a `send` attempt would leak the reservation forever (T-2C critical AI-SPEC failure mode #5).
       
       (c) Add a privacy log line BEFORE re-throwing InsufficientCreditsException (caught at the top of chat() since reserve is the first call):
       ```java
       try {
           reservation = creditLedger.reserve(tenantId, callSite);
       } catch (InsufficientCreditsException insufficient) {
           log.warn("event=llm_call_blocked_insufficient_credits tenantId={} callSite={}",
                   tenantId, callSite);  // No balance amount — D-I1
           throw insufficient;
       }
       ```
       Or equivalently, wrap the existing reserve call. Use enterprise variable name `insufficient` (not `e`/`ex`).
       
       (d) **DO NOT** wrap `driftCheck()` in reserve/settle/release — D-E3 explicitly says drift bypasses the ledger. Add a code comment: `// D-E3 — drift is a platform-cost operation, not user-billable; ledger NOT touched`.
       
       (e) Verify the BYOK branch (Plan 05) returns BEFORE any reserve call is reached — re-read the Plan 05 insertion point and confirm the early-return structure. If the BYOK branch is `if (byok.isPresent()) { return callViaByokFactory(...); }`, the structural guarantee holds. Add a code comment at the early return: `// LLM-05 — BYOK skips credit ledger by design`.

    2. **Create `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayCreditLifecycleTest.java`** — `@SpringBootTest` with `@MockBean ChatModel` and `@SpyBean CreditLedger` (so the real ledger runs against Testcontainers Postgres but interactions are observable). Implement Tests 1–7 above. Test 6 uses `StructuredTaskScope` and `ScopedValue.where(TenantContext.TENANT, ...)` per the Plan 03 multi-tenant pattern, but for the SAME tenant id (concurrency under one tenant). Reuse PATTERNS.md "LlmGatewayMultiTenantLeakTest.java" structural shape — adapt for single-tenant + outcome-asserting.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "LlmGatewayCreditLifecycleTest" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayActionValidatorTest" --tests "LlmGatewayByokRoutingTest" --tests "LlmGatewayMultiTenantLeakTest"</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c 'private final CreditLedger creditLedger' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `1`.
    - `grep -c 'creditLedger.reserve' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1` (in chat() platform path).
    - `grep -c 'creditLedger.settle' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`.
    - `grep -c 'creditLedger.release' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 2` (release on SafetyViolation + release on RuntimeException — separate catch blocks).
    - `grep -c 'event=llm_call_blocked_insufficient_credits' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `1`.
    - `grep -c '// LLM-05 — BYOK skips credit ledger\|// D-E3' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 2` (BYOK skip comment + drift skip comment).
    - `grep -c '// Plan 06 will add' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `0` (all markers replaced with real code).
    - `grep -E 'log\.(info|warn|error|debug).*reservation\.id|log\.(info|warn|error|debug).*reservation\.uuid' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` — reservation id NOT logged in the gateway (Phase 2B may log it internally; that's fine; the gateway only logs metadata).
    - `./gradlew :backend:core:test --tests "LlmGatewayCreditLifecycleTest"` exits 0 — all 7 tests pass.
    - `./gradlew :backend:core:test --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayActionValidatorTest" --tests "LlmGatewayByokRoutingTest" --tests "LlmGatewayMultiTenantLeakTest"` exits 0 (Plans 03/04/05 tests still pass).
    - `./gradlew :backend:core:test :backend:api:test :backend:worker:test` exits 0 (full suite green).
  </acceptance_criteria>
  <done>
    Credit ledger lifecycle wraps the platform call site exactly once. SafetyViolation, SanitizationException pre-reserve, InsufficientCredits, and arbitrary RuntimeException all reconcile correctly. BYOK path provably skips the ledger. driftCheck provably skips the ledger. 100-call concurrent test proves no double-charge under contention. LlmGatewayImpl is now feature-complete (Plans 03 → 06 fully implemented; Plan 07 adds drift; Plan 08 adds frontend).
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| LlmGatewayImpl.chat() → CreditLedger.reserve | Reserve must succeed before any model call; failure → 402 + no model invocation. |
| Try-finally lifecycle | Every successful reserve must be matched by exactly one settle OR exactly one release; never both, never neither. |
| BYOK path → ledger | No edge — provably skipped by structural early return. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2C-04 | Denial of Service / Spend abuse / Cost-DoS | LlmGatewayImpl platform path | mitigate | `creditLedger.reserve(tenantId, callSite)` is the first action on the platform path AFTER sanitization (sanitization runs first to fail-fast on hostile input without consuming credits). When tenant balance hits zero, reserve throws `InsufficientCreditsException` and no model call is issued. Phase 2B's advisory-lock-protected ledger handles concurrency — LlmGatewayCreditLifecycleTest#concurrent_100_calls_balance_reconciles asserts no double-charge under 100-virtual-thread contention. |
| T-2C-credit-leak-on-safety-violation | DoS / Cost integrity | SafetyViolationException catch block | mitigate | The catch block calls `creditLedger.release(reservation)` BEFORE re-throwing. Without this, every model rejection (potentially adversarial — attacker forces `send` attempts) would leak credits. LlmGatewayCreditLifecycleTest#platform_call_releases_on_safety_violation asserts. |
| T-2C-credit-leak-on-arbitrary-exception | DoS / Cost integrity | RuntimeException catch block | mitigate | Catch-all `catch (RuntimeException)` block also releases — covers timeouts, network errors, M4 churn surprises. LlmGatewayCreditLifecycleTest#concurrent_100_calls_balance_reconciles 50/50 success/failure split asserts release on the failed half. |
| T-2C-bill-byok-by-mistake | DoS / Cost integrity | BYOK branch | mitigate | BYOK branch is an early `return callViaByokFactory(...)` BEFORE any reserve call (Plan 05 structure). LlmGatewayCreditLifecycleTest#byok_path_does_not_touch_ledger uses `verify(creditLedger, never()).reserve(...)` to assert. |
| T-2C-drift-billed | DoS / Cost integrity | driftCheck() | mitigate | D-E3 explicit code comment + LlmGatewayCreditLifecycleTest#driftCheck_does_not_touch_ledger asserts. Drift is a platform-cost operation. |
| T-2C-balance-leak-in-log | Information Disclosure | event=llm_call_blocked_insufficient_credits | mitigate | Log line carries `tenantId={} callSite={}` only — no balance amount, no credit cost (D-I1). Test does NOT explicitly verify this; relies on grep gate in acceptance criteria + reviewer attention. |
</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "LlmGateway*"` exits 0 — all gateway-touching tests across Plans 03/04/05/06 green
- `./gradlew :backend:core:test :backend:api:test :backend:worker:test` exits 0 — full suite green
- ArchUnit `LlmGatewayBoundaryTest` + `DomainBoundaryArchTests` continue to pass
- Phase 2B `CreditLedgerSettleIdempotentTest` continues to pass (we did not touch the ledger interface; only added a new caller)
</verification>

<success_criteria>
- LlmGatewayImpl.chat() now wraps the platform call site with `creditLedger.reserve / settle / release` per the cross-phase contract Javadoc on `CreditLedger.java`.
- BYOK path + driftCheck both provably skip the ledger.
- Pre-reserve sanitization failure does not consume credits.
- 100-call concurrent integration test reconciles correctly.
- Privacy log on insufficient credits emits metadata only.
- All 7 LlmGatewayCreditLifecycleTest scenarios pass.
- LlmGatewayImpl.java is feature-complete for the platform + BYOK paths; only the drift call site (Plan 07) and frontend (Plan 08) remain.
</success_criteria>

<output>
After completion, create `.planning/phases/02C-llm-gateway/02C-06-SUMMARY.md` documenting:
- Whether the InsufficientCreditsException catch block needed any additional handling beyond rethrow (e.g., metric increment) — likely just the privacy log line per D-I1
- Final reconciliation count from LlmGatewayCreditLifecycleTest#concurrent_100_calls_balance_reconciles (proof of no double-charge / no lost credits under contention)
- Pointer for Plan 07: `gateway.driftCheck(prompt)` is the entry point for `DriftDetectionJob` — uses platform-key, no ledger, returns ToolCallResult
- Pointer for Plan 08: HTTP 402 from chat() → frontend renders `errors.llm.insufficientCredits.{title,body}` (already declared in UI-SPEC i18n keys; Plan 08 wires the localization)
</output>
