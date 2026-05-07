---
phase: 02C-llm-gateway
plan: 04
type: execute
wave: 4
depends_on: [03]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java
  - backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java
  - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorTest.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayActionValidatorTest.java
  - backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorWave0Test.java
autonomous: true
requirements: [LLM-07]
must_haves:
  truths:
    - "Layer 1 enforcement: every LlmGatewayImpl ChatClient call sets toolChoice='required' AND internalToolExecutionEnabled(false) so the model is forced to emit a tool call and Spring AI returns it without auto-executing"
    - "Layer 2 enforcement: ActionValidator.validate(functionName) calls Action.fromFunctionName (fail-loud NoSuchElementException → SafetyViolationException) AND independently checks EnumSet.of(LABEL, ARCHIVE, SAVE_DRAFT).contains(action) — both checks must independently fail-open for 'send' to leak through"
    - "A mocked ChatModel returning {action: 'send', args: {...}} causes LlmGatewayImpl.chat to throw SafetyViolationException; the result NEVER reaches the caller"
    - "A mocked ChatModel returning {action: 'label', args: {value: 'Receipts'}} returns ToolCallResult(LABEL, {value=Receipts})"
    - "SafetyViolationException carries no rejected action name, no tool-call args, no model output content; GlobalExceptionHandler maps to HTTP 500 with code=error.llm.safety_violation"
    - "Privacy log on safety violation: event=llm_safety_violation tenantId={} callSite={} reason={exception.getClass().getSimpleName()} — never the rejected action name"
    - "Plan 01 Wave 0 ActionValidatorWave0Test @Disabled removed and now passes"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java"
      provides: "Public utility — validate(String functionName) → Action; throws SafetyViolationException on any non-allow-listed action"
      exports: ["validate"]
    - path: "backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java"
      provides: "RuntimeException with no message field (privacy invariant); subclass of LLM safety failures"
      contains: "extends RuntimeException"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java"
      to: "backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java"
      via: "constructor injection + parseToolCall calls actionValidator.validate(toolCall.name())"
      pattern: "actionValidator\\.validate"
    - from: "backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java"
      to: "Spring AI options"
      via: "OpenAiChatOptions.builder().toolChoice('required').internalToolExecutionEnabled(false)"
      pattern: "toolChoice"
---

<objective>
Wave 3a tool-call allow-list enforcement. Land the defense-in-depth guarantee that NO action outside `{LABEL, ARCHIVE, SAVE_DRAFT}` can ever leave `LlmGateway`. Two independent layers: (1) Spring AI `toolChoice="required"` + `internalToolExecutionEnabled(false)` at the wire level; (2) post-parse `ActionValidator` enum check.

Purpose: this is LLM-07 (defense-in-depth Layer 2 — pairs with Plan 02's structural sanitization wrap; together they implement the full prompt-injection hardening contract: structured tool-call schema + per-action allow-list). With Phase 4 wired to a real Gmail mailbox, a single leaked `send` action = product-killing. The validator enforces the project's most critical invariant — auto-send forbidden, full stop. Two layers because either alone is insufficient: OpenRouter sometimes ignores `toolChoice` (RESEARCH issue #1899); Spring AI M4→GA churn could silently disable Layer 1; some self-hosted vLLM endpoints accept `toolChoice` but emit free-text anyway. Both layers must independently fail open for `send` to leak.

Output: 1 production class (ActionValidator) + 1 model (SafetyViolationException) + 2 test files + Plan 01 Wave 0 scaffold turned green + LlmGatewayImpl modified at the parseToolCall + options seams.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.planning/phases/02C-llm-gateway/02C-CONTEXT.md
@.planning/phases/02C-llm-gateway/02C-PATTERNS.md
@.planning/phases/02C-llm-gateway/02C-RESEARCH.md
@.planning/phases/02C-llm-gateway/02C-03-SUMMARY.md
@backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java
@backend/core/src/main/java/com/zeromail/core/llm/model/Action.java
@backend/core/src/main/java/com/zeromail/core/llm/model/ToolCallResult.java
@backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java

<interfaces>
<!-- From Plan 03 (already on disk) -->
- `LlmGatewayImpl.parseToolCall(ChatResponse)` — the seam to inject `actionValidator.validate(...)`. Marked with `// Plan 04 modifies here` comment.
- `LlmGatewayImpl.chat()` builds `OpenAiChatOptions.builder().model(model).build()` — the seam to add `.toolChoice("required").internalToolExecutionEnabled(false)`.
- `Action.fromFunctionName(String) → Action` (throws `NoSuchElementException`).
- `Action` enum members: `{LABEL, ARCHIVE, SAVE_DRAFT}`.

<!-- Spring AI 2.0.0-M4 (verify via Context7) -->
- `OpenAiChatOptions.builder().toolChoice("required")` — D-C1 + RESEARCH issue #1899 confirms String form accepted on M4 + OpenRouter.
- `ChatClient.prompt().toolCallbacks(...).options(...)` — per-call composition.
- M4 `ChatClient.prompt()` builder method to disable internal tool execution: verify exact name. Candidates: `.internalToolExecutionEnabled(false)` (D-C1) or via `OpenAiChatOptions.builder().internalToolExecutionEnabled(false)`. Context7 query: `/spring-projects/spring-ai` "internalToolExecutionEnabled 2.0.0-M4 ChatClient".

<!-- ToolCall API (M4) -->
- `org.springframework.ai.chat.messages.AssistantMessage.ToolCall` (or similar; M4 path) — `.name()` returns function name, `.arguments()` returns JSON string.
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: SafetyViolationException + ActionValidator + unit tests</name>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java (RuntimeException analog — no content payload — PATTERNS.md "SafetyViolationException.java")
    - backend/core/src/main/java/com/zeromail/core/llm/model/Action.java (Plan 03 — fromFunctionName fail-loud)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-C2 Layer 2, D-C4 SafetyViolationException privacy invariant)
    - .planning/phases/02C-llm-gateway/02C-PATTERNS.md (sections "SafetyViolationException.java" + "LlmGatewayBoundaryTest.java" — note S-1 privacy log)
  </read_first>
  <behavior>
    - Test 1 (ActionValidatorTest#validates_label_archive_save_draft): `validator.validate("label") == Action.LABEL`; same for "archive"/ARCHIVE and "save_draft"/SAVE_DRAFT.
    - Test 2 (ActionValidatorTest#rejects_send_action): `validator.validate("send")` throws SafetyViolationException.
    - Test 3 (ActionValidatorTest#rejects_unknown_function_name): `validator.validate("forward")`, `validator.validate("delete")`, `validator.validate("mark_spam")`, `validator.validate("trash")` — all throw SafetyViolationException.
    - Test 4 (ActionValidatorTest#rejects_null_or_empty): `validator.validate(null)` throws SafetyViolationException; `validator.validate("")` throws.
    - Test 5 (ActionValidatorTest#exception_carries_no_action_name): catch SafetyViolationException, assert exception.getMessage() is null (no content payload).
    - Test 6 (SafetyViolationExceptionTest#no_message_constructor_only): `new SafetyViolationException()` constructs successfully; `.getMessage() == null`; cannot be constructed with String (no String-arg ctor).
  </behavior>
  <action>
    1. **Create `backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java`** per PATTERNS.md verbatim shape:
       ```java
       /**
        * Thrown when the LLM gateway rejects an action outside the allow-list
        * {@code {LABEL, ARCHIVE, SAVE_DRAFT}}.
        *
        * <p><b>Privacy invariant.</b> This exception carries NO rejected action name,
        * NO tool-call arguments, NO model output content. The HTTP layer maps it to
        * 500 with {@code code="error.llm.safety_violation"}; the frontend localizes
        * without ever seeing the rejected payload.
        *
        * <p><b>Defense-in-depth pairing.</b> Layer 1 enforcement (Spring AI
        * {@code toolChoice="required"} + {@code internalToolExecutionEnabled(false)})
        * is at the wire level; this exception is the Layer 2 fail-closed signal that
        * the validator caught a function name outside the allow-list. Both layers
        * must independently fail open for an unsafe action to leak.
        */
       public class SafetyViolationException extends RuntimeException {
           public SafetyViolationException() { super(); }
       }
       ```
       Critical: NO String-arg constructor, NO Throwable-arg constructor (privacy invariant — would inevitably leak content if callers pass exception messages or causes that include model output). NO action name, NO args.

    2. **Create `backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java`** — public `@Component class ActionValidator`:
       ```java
       package com.zeromail.core.llm.service;

       import java.util.EnumSet;
       import java.util.NoSuchElementException;
       import com.zeromail.core.llm.model.Action;
       import com.zeromail.core.llm.model.SafetyViolationException;
       import org.springframework.stereotype.Component;

       @Component
       public class ActionValidator {

           // D-C2: independent EnumSet check on top of Action.fromFunctionName.
           // Both checks must fail-open for an unsafe action to leak.
           private static final EnumSet<Action> ALLOW_LIST =
                   EnumSet.of(Action.LABEL, Action.ARCHIVE, Action.SAVE_DRAFT);

           public Action validate(String functionName) {
               if (functionName == null || functionName.isBlank()) {
                   throw new SafetyViolationException();
               }
               Action resolved;
               try {
                   resolved = Action.fromFunctionName(functionName);
               } catch (NoSuchElementException unknownAction) {
                   throw new SafetyViolationException();   // D-C2 Layer 2 — discard the original message
               }
               if (!ALLOW_LIST.contains(resolved)) {
                   // Defensive — should be impossible if Action enum membership is intact,
                   // but enforces D-C2 independence: even if Action gains a SEND member by mistake,
                   // ALLOW_LIST remains the single source of truth here.
                   throw new SafetyViolationException();
               }
               return resolved;
           }
       }
       ```
       Per CLAUDE.md: variable named `resolved` not `r`/`a`; exception variable `unknownAction` not `e`/`ex`.

    3. **Create `backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorTest.java`** — plain JUnit 5 unit test (no Spring), instantiate `new ActionValidator()`. Implement Tests 1–5 above plus an additional defensive test asserting that an action constructed via reflection trick (or via Action.fromFunctionName for a hypothetical future enum value not in ALLOW_LIST) is rejected — skip this if reflection is too hacky; the defensive `ALLOW_LIST.contains(resolved)` line is still valuable.

    4. Add a small inline assertion test or include in ActionValidatorTest: `assertThat(SafetyViolationException.class.getDeclaredConstructors()).hasSize(1)` — proves no String-arg constructor was added by accident.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "ActionValidatorTest"</automated>
  </verify>
  <acceptance_criteria>
    - File `backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java` exists.
    - `grep -c 'extends RuntimeException' backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java` returns `1`.
    - `grep -c 'public SafetyViolationException(' backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java` returns `1` (single no-arg ctor).
    - `grep -E 'public SafetyViolationException\(String|public SafetyViolationException\(Throwable' backend/core/src/main/java/com/zeromail/core/llm/model/SafetyViolationException.java` returns no matches (privacy invariant — no message constructors).
    - File `backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java` exists.
    - `grep -c 'EnumSet.of(Action.LABEL, Action.ARCHIVE, Action.SAVE_DRAFT)' backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java` returns `1`.
    - `grep -c 'fromFunctionName' backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java` returns `>= 1`.
    - `grep -c 'throw new SafetyViolationException' backend/core/src/main/java/com/zeromail/core/llm/service/ActionValidator.java` returns `>= 3` (null/blank, unknown, defensive non-allow-list path).
    - `./gradlew :backend:core:test --tests "ActionValidatorTest"` exits 0.
  </acceptance_criteria>
  <done>
    SafetyViolationException carries no content; ActionValidator enforces double-check (fromFunctionName + EnumSet.contains); unit tests cover label/archive/save_draft pass and send/forward/delete/mark_spam/trash/null/blank reject paths.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Wire ActionValidator + toolChoice='required' + internalToolExecutionEnabled(false) into LlmGatewayImpl + integration test + Wave 0 turned green</name>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java (Plan 03 skeleton — find the `// Plan 04 modifies here` markers)
    - backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorWave0Test.java (Plan 01 @Disabled scaffold)
    - .planning/phases/02C-llm-gateway/02C-CONTEXT.md (D-C1 Layer 1 + D-C5 test seam)
    - .planning/phases/02C-llm-gateway/02C-AI-SPEC.md (Section 3 — verify exact M4 ChatClient builder + OpenAiChatOptions API; Section 6 evaluation rubric for allow-list integrity)
    - .planning/phases/02C-llm-gateway/02C-RESEARCH.md (issue #1899 — toolChoice String form on OpenRouter; Anthropic asymmetry note)
  </read_first>
  <behavior>
    - Test 1 (LlmGatewayActionValidatorTest#rejects_send_action_at_validator): `@MockBean ChatModel` returning a ChatResponse with tool call `{name: "send", arguments: '{"to": "..."}'}` → calling `gateway.chat(CallSite.PREVIEW, "hi", List.of())` throws SafetyViolationException; ToolCallResult never returned to caller.
    - Test 2 (LlmGatewayActionValidatorTest#accepts_label_action): mock returns `{name: "label", arguments: '{"value": "Receipts"}'}` → returns `ToolCallResult(LABEL, {value=Receipts})`.
    - Test 3 (LlmGatewayActionValidatorTest#emits_safety_violation_log): on safety violation, captured Logback ListAppender contains `event=llm_safety_violation tenantId={...} callSite=PREVIEW reason=SafetyViolationException` AND does NOT contain the rejected action name `send` AND does NOT contain the args content `to`.
    - Test 4 (LlmGatewayActionValidatorTest#sets_toolChoice_required_in_options): inspect the mock's captured options (via ArgumentCaptor<OpenAiChatOptions>) — assert `capturedOptions.getToolChoice().equals("required")` AND `capturedOptions.getInternalToolExecutionEnabled().equals(Boolean.FALSE)` (H-5 lock — Spring AI 2.0.0-M4 getter shape verified via Context7).
    - Test 5 (LlmGatewayActionValidatorTest#fails_when_no_tool_call_returned): mock returns ChatResponse with empty tool calls → throws SafetyViolationException (model emitted free text instead of a tool call — fail closed).
    - Plan 01 ActionValidatorWave0Test @Disabled removed; assertion `validator.validate("send")` throws SafetyViolationException AND `validator.validate("label") == Action.LABEL` passes.
  </behavior>
  <action>
    1. **Modify `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java`** at the Plan 03 seams:
       
       (a) Add `private final ActionValidator actionValidator;` field; add to constructor; remove the `// Plan 04 will add: private final ActionValidator actionValidator;` comment.
       
       (b) In `chat()`, modify the OpenAiChatOptions construction to include `toolChoice("required")` AND `internalToolExecutionEnabled(false)`. **H-5 LOCK** (Spring AI 2.0.0-M4 docs verified via Context7 — `internalToolExecutionEnabled` lives on `OpenAiChatOptions.builder()`, NOT on `ChatClient.prompt()`):
       ```java
       import org.springframework.ai.openai.OpenAiChatOptions;

       OpenAiChatOptions perCallOptions = OpenAiChatOptions.builder()
               .model(model)
               .toolChoice("required")                       // D-C1 Layer 1
               .internalToolExecutionEnabled(false)          // H-5 + D-C1 — gateway parses, does NOT auto-execute
               .build();
       ```
       The location is locked at plan-phase, not deferred. Spring AI maintainers may also accept this property via `spring.ai.openai.chat.options.internal-tool-execution-enabled=false` in `application.yml`, but per-call-site builder pinning beats yml because (a) it keeps the safety pin co-located with the tool-call site, and (b) it survives any application.yml mutations during execution.
       
       (c) Replace `parseToolCall(chatResponse)` body with validator-backed logic:
       ```java
       private ToolCallResult parseToolCall(ChatResponse chatResponse) {
           AssistantMessage message = chatResponse.getResults().get(0).getOutput();
           if (message.getToolCalls() == null || message.getToolCalls().isEmpty()) {
               // Layer-2 fail-closed when model emits free text instead of a tool call
               throw new SafetyViolationException();
           }
           AssistantMessage.ToolCall toolCall = message.getToolCalls().get(0);
           Action action = actionValidator.validate(toolCall.name());     // D-C2 Layer 2
           Map<String, Object> args = parseJsonArgs(toolCall.arguments());
           return new ToolCallResult(action, args);
       }
       ```
       Drop the `Action.fromFunctionName(toolCall.name())` direct call — `actionValidator.validate(...)` is the single allowed path.
       
       (d) Wrap the chat() call site to convert SafetyViolationException to a privacy-safe log line and re-throw (do NOT swallow):
       ```java
       } catch (SafetyViolationException safetyViolation) {
           log.error("event=llm_safety_violation tenantId={} callSite={} reason={}",
                   tenantId, callSite, safetyViolation.getClass().getSimpleName());
           throw safetyViolation;       // Caller (Phase 4) gets the exception — no fallback, no return
       } catch (RuntimeException callFailure) {
           log.warn("event=llm_call_failed tenantId={} callSite={} reason={}",
                   tenantId, callSite, callFailure.getClass().getSimpleName());
           throw callFailure;
       }
       ```
       Critical: the `safetyViolation` catch block uses `error` log level (operator visibility); `callFailure` uses `warn`. Per S-1 + D-I1: NEVER pass the exception object to the logger — pass `getClass().getSimpleName()` only.

    2. **Update `driftCheck(prompt)` in LlmGatewayImpl** the same way — toolChoice="required", internalToolExecutionEnabled(false), parseToolCall via ActionValidator. Drift fixtures (Plan 07) all expect allow-listed actions; if a drift call returns an unknown action, that's a regression and SafetyViolationException is the right signal.

    3. **Create `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayActionValidatorTest.java`** — `@SpringBootTest`, `@MockBean ChatModel`. Tests 1–5 above. Use Mockito `ArgumentCaptor<OpenAiChatOptions>` to verify Test 4's option assertions. For the Logback test (Test 3), use a `ListAppender<ILoggingEvent>` attached to `LlmGatewayImpl`'s logger.

    4. **Modify Plan 01's `backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorWave0Test.java`** — remove `@Disabled`. Test body asserts:
       - `validator.validate("label").equals(Action.LABEL)` is true.
       - `assertThatThrownBy(() -> validator.validate("send")).isInstanceOf(SafetyViolationException.class);`

    5. **GlobalExceptionHandler mapping for SafetyViolationException is added in Plan 05** (alongside SanitizationException + InvalidByokException). Plan 04 adds a TODO comment in LlmGatewayImpl referencing Plan 05 for the controller-side mapping; the gateway itself just throws SafetyViolationException.
  </action>
  <verify>
    <automated>./gradlew :backend:core:test --tests "LlmGatewayActionValidatorTest" --tests "ActionValidatorTest" --tests "ActionValidatorWave0Test" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayMultiTenantLeakTest"</automated>
  </verify>
  <acceptance_criteria>
    - `grep -c 'private final ActionValidator actionValidator' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `1`.
    - `grep -c 'actionValidator.validate' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`.
    - `grep -c 'toolChoice("required")' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`.
    - H-5 (canonical Spring AI 2.0.0-M4 location pinned): `grep -c "internalToolExecutionEnabled(false)" backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`. Additionally, the call MUST be on `OpenAiChatOptions.builder()` (verified via Context7 — see RESEARCH Open Questions RESOLVED + this plan's research lock): `grep -E "OpenAiChatOptions\.builder\(\)[\s\S]*\.internalToolExecutionEnabled\(false\)" backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` (multiline match) returns `>= 1`.
    - `grep -c 'event=llm_safety_violation' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `>= 1`.
    - `grep -c 'Action.fromFunctionName' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java` returns `0` (validator is the only path now).
    - `grep -v '^\s*\*\|^\s*//' backend/core/src/main/java/com/zeromail/core/llm/service/LlmGatewayImpl.java | grep -E 'log\.(info|warn|error|debug).*safetyViolation\)' ` returns `0` (must NOT pass exception object — only `getClass().getSimpleName()`).
    - `grep -v '^\s*//' backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorWave0Test.java | grep -c '@Disabled'` returns `0`.
    - `./gradlew :backend:core:test --tests "LlmGatewayActionValidatorTest"` exits 0 — Tests 1–5 pass, including the captured-log assertion that the rejected action name is NOT in the log.
    - `./gradlew :backend:core:test --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayMultiTenantLeakTest"` still exits 0 (Plan 03 happy path + leak test still pass with validator wired in).
    - `./gradlew :backend:core:test --tests "LlmGatewayBoundaryTest"` exits 0 (no Spring AI imports leaked beyond the gateway.springai + service exemption).
  </acceptance_criteria>
  <done>
    Defense-in-depth tool-call enforcement is live. Layer 1 (`toolChoice="required"` + `internalToolExecutionEnabled(false)`) and Layer 2 (`ActionValidator.validate`) both wired. Mock model returning `send` is rejected with SafetyViolationException; safety violation is logged with metadata only (no rejected action name, no args). Plan 01 ActionValidatorWave0Test scaffold turned green.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Spring AI ChatModel response → LlmGatewayImpl.parseToolCall | Untrusted: model could emit free text, unknown function names, or `send`. ActionValidator + empty-tool-calls fail-closed are the boundary defenders. |
| ActionValidator.validate(...) → caller | The single value returned is ALWAYS in `{LABEL, ARCHIVE, SAVE_DRAFT}` or the call throws. No third option. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-2C-02 | Elevation of Privilege (tool-call exfiltration / unauthorized action) | LlmGatewayImpl.parseToolCall + ActionValidator | mitigate | **Two independent layers required to fail-open simultaneously for `send` to leak.** Layer 1: `OpenAiChatOptions.builder().toolChoice("required").internalToolExecutionEnabled(false)` forces the model to emit a tool call and tells Spring AI to NOT auto-execute. Layer 2: `ActionValidator` calls `Action.fromFunctionName` (fail-loud) AND `EnumSet.of(LABEL, ARCHIVE, SAVE_DRAFT).contains(...)`. Empty-tool-calls path also fails closed (LlmGatewayActionValidatorTest#fails_when_no_tool_call_returned). LlmGatewayActionValidatorTest exercises mock returning `send` → SafetyViolationException; mock returning `label` → success. |
| T-2C-05 | Information Disclosure (rejected action name / model output in logs) | SafetyViolationException + LlmGatewayImpl safety-violation log | mitigate | SafetyViolationException has NO message field, NO String constructor. Log line `event=llm_safety_violation tenantId={} callSite={} reason={exception.getClass().getSimpleName()}` — no action name, no args. LlmGatewayActionValidatorTest#emits_safety_violation_log asserts neither `send` nor `to` (args content from the mock) appears in the captured log. |
| T-2C-toolchoice-ignored | Tampering | OpenRouter / vLLM / self-hosted endpoints | mitigate | **This is the canonical D-C1+D-C2 use case.** RESEARCH issue #1899 confirms some OpenRouter routings ignore `toolChoice`; some self-hosted vLLM accepts it but emits free text. Layer 2 ActionValidator catches the slip. Both layers must independently fail-open for the leak to occur — no single point of failure. |
| T-2C-future-enum-drift | Tampering | Action enum membership | mitigate | Defensive `ALLOW_LIST.contains(resolved)` check in ActionValidator catches the case where Action enum gains a new member (e.g., `SEND` added by mistake). Without this check, fromFunctionName would pass-through. ActionValidatorTest#exception_carries_no_action_name + the EnumSet.of literal pin the allow-list at the validator. |
| T-2C-internalToolExecution-default-flips | Tampering | Spring AI M4→GA churn | accept | M4 default for `internalToolExecutionEnabled` may flip GA → forcing explicit `false` in code is the defense. If Spring AI silently auto-executes a `send` tool call, ActionValidator never gets to run. Pinning explicitly per call site is the only defense available; code review + ArchUnit check that every `OpenAiChatOptions.builder()` call in `core.llm.gateway.springai` includes the toggle (Plan 07 may extend). |
</threat_model>

<verification>
> Run all grep / shell acceptance checks via Git Bash (bash.exe), not PowerShell.

- `./gradlew :backend:core:test --tests "ActionValidatorTest" --tests "LlmGatewayActionValidatorTest" --tests "ActionValidatorWave0Test"` exits 0
- `./gradlew :backend:core:test --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayMultiTenantLeakTest"` continues to exit 0 (Plan 03 tests still green with validator wired)
- `./gradlew :backend:core:test` exits 0 (full module green)
- ArchUnit `LlmGatewayBoundaryTest` continues to pass
</verification>

<success_criteria>
- ActionValidator + SafetyViolationException land with no-content invariants enforced.
- LlmGatewayImpl wires both Layer 1 (toolChoice + internalToolExecutionEnabled) and Layer 2 (validator-backed parseToolCall).
- A mock `send` action is rejected before returning to caller; logs contain no rejected payload.
- Plan 01 Wave 0 ActionValidatorWave0Test no longer @Disabled.
- Plan 03's existing tests (PlatformPath, MultiTenantLeak) still green.
</success_criteria>

<output>
After completion, create `.planning/phases/02C-llm-gateway/02C-04-SUMMARY.md` documenting:
- Final M4 import path for `internalToolExecutionEnabled` (on ChatClient.prompt() vs OpenAiChatOptions) — verified via Context7 at execution
- The exact captured log line shape from LlmGatewayActionValidatorTest#emits_safety_violation_log (proof of metadata-only)
- Pointer for Plan 05: where in `chat(...)` to insert the BYOK branch BEFORE the platform-path call (between sanitize and the chatClient.prompt() construction)
- Note for Plan 06: `creditLedger.reserve / settle / release` wrapping is the OUTER seam — wraps both the BYOK branch (Plan 05 makes that path billing-skip) and the platform call
</output>
