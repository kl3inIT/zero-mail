---
phase: 2C
cycle: 2
reviewers: [codex, opencode]
reviewed_at: 2026-05-07T10:01:53Z
plans_reviewed:
  - 02C-01-PLAN.md
  - 02C-02-PLAN.md
  - 02C-03-PLAN.md
  - 02C-04-PLAN.md
  - 02C-05a-PLAN.md
  - 02C-05b-PLAN.md
  - 02C-06-PLAN.md
  - 02C-07-PLAN.md
  - 02C-08-PLAN.md
models:
  codex: cx/gpt-5.5 (Codex CLI default)
  opencode: openai/gpt-5.5
replan_under_review: 67eb724 "docs(02C): replan with --reviews — close 6 HIGH + 4 MEDIUM consensus + divergent items"
prior_cycle_summary: "Cycle 1 raised 7 HIGH + 4 MEDIUM consensus concerns. Replan claimed closure of 6 HIGH + 4 MEDIUM + divergent items."
---

# Cross-AI Plan Review — Phase 2C: LLM Gateway (Cycle 2)

> **Reviewer task:** Independently verify whether the cycle-1 HIGH concerns were actually closed by commit `67eb724`, and surface any new HIGHs introduced by the replan.

## Codex Review

## Summary

Cycle 2 closes several real gaps: gateway-owned tools, core/API layering, server-side BYOK validation-on-save, and drift sanitization are now materially addressed. But it does **not** close the biggest architectural concern: Spring AI still leaks into `core.llm.service` through an explicit `LlmGatewayImpl` ArchUnit exemption and `ToolTranslator<Object>` workaround. YAML/config and LLM-09 privacy are improved but still partial. I also see new or remaining HIGH issues around `BYOKProvider` persistence/JSON mapping and OpenAI-compatible endpoint path handling.

## Closure Verdict

1. **Spring AI boundary violation: NOT CLOSED**  
   `LlmGatewayImpl` still lives in `core.llm.service`, imports Spring AI types, and is explicitly exempted from the ArchUnit rule. This is the same architectural breach with more justification text.

2. **Caller-supplied tools: CLOSED**  
   The public gateway signature is changed to `chat(CallSite, String)`, and `AllowListedTools` owns the fixed `{label, archive, save_draft}` set. There are stale frontmatter references to `List<LlmTool>`, but the implementation tasks and acceptance checks target gateway-owned tools.

3. **Core depending on API DTOs: CLOSED**  
   Plan 05b now introduces core command/result records and has `ByokController` map API DTOs to core records.

4. **BYOK save can bypass validation: CLOSED**  
   `ByokService.save()` now re-runs the upstream provider probe before encrypting/upserting, with tests for direct-save bypass and revoked-key-between-validate-and-save.

5. **YAML/config merge risk: PARTIALLY CLOSED**  
   The merge procedure is now concrete and checks duplicate top-level `spring:` / `zero-mail:` keys. However, later plans still refer to `zeromail.llm...` in places, while Plan 03 declares `zero-mail.*` canonical, and `zero-mail.llm.byok.*` lacks a clearly bound properties class.

6. **LLM-09 privacy verification incomplete: PARTIALLY CLOSED**  
   The replan adds a repository-ban ArchUnit test and scrubber tests, but span-attribute inspection is still missing, and the repo-ban implementation relies on parameter names that may not be retained unless the build uses `-parameters`.

7. **`driftCheck()` sanitization bypass: CLOSED**  
   Plan 03 explicitly routes `driftCheck()` through `sanitizationPipeline.sanitize(...)`, the same system prompt, and the same gateway-owned allow-listed tools.

## New Or Remaining Concerns

- **HIGH: Spring AI isolation remains broken.**  
  The plan did not adopt the requested pure-Java adapter seam. `LlmGatewayImpl` remains the one-class exception, which is exactly the pattern cycle 1 rejected.

- **HIGH: `BYOKProvider` DB/JSON mapping still appears broken.**  
  Plan 01 uses `@Enumerated(EnumType.STRING)` while Liquibase allows lowercase ids (`anthropic`, `openai-compatible`). JPA will persist `ANTHROPIC` / `OPENAI_COMPATIBLE`, violating the check constraint. API JSON likely has the same mismatch without `@JsonValue` / `@JsonCreator`.

- **HIGH: OpenAI-compatible endpoint path policy is still contradictory.**  
  Plan 05a says stored endpoint includes `/v1` and validation should call `${endpoint}/models`; Plan 05b still says `GET ${endpoint}/v1/models`. With the UI placeholder `https://openrouter.ai/api/v1`, this can still produce `/api/v1/v1/models`.

- **HIGH: LLM-09 is not fully verifiable yet.**  
  Observation flags are pinned, but there is no required test that inspects spans and proves prompt/completion content is absent. The static repository ban is also weaker than stated.

- **MEDIUM: Plan 06 may accidentally remove the fixed system prompt.**  
  Earlier plans add `SystemPrompts.TRIAGE_SYSTEM_PROMPT`, but the Plan 06 replacement snippet for the platform call shows `.user(...)` without `.system(...)`. That should be made impossible with an acceptance grep after Plan 06.

- **MEDIUM: Platform `anthropic` provider is still underimplemented.**  
  Config allows `provider: anthropic`, but platform wiring still appears OpenAI-compatible only. Either remove direct Anthropic platform provider from v1 config or implement it.

## Suggestions

- Move Spring AI usage behind a pure-Java client seam:
  - `LlmGatewayImpl` depends on `LlmModelClient`.
  - `SpringAiLlmModelClient` lives in `core.llm.gateway.springai`.
  - It returns a pure `RawToolCall(functionName, argsJson, usage)`.
  - Remove the ArchUnit exemption and `ToolTranslator<Object>`.

- Add `BYOKProviderAttributeConverter` and Jackson annotations:
  - `@Converter(autoApply = true)` mapping enum ↔ `id()`.
  - `@JsonValue` on `id()`.
  - `@JsonCreator` delegating to `fromId`.

- Centralize endpoint joining:
  - Store canonical base including version path.
  - Always append `models` or `messages` with a safe URI join helper.
  - Add explicit tests for `https://openrouter.ai/api/v1` → `/api/v1/models`, not `/api/v1/v1/models`.

- Strengthen LLM-09:
  - Add a test that captures Micrometer/OTel span attributes and asserts no prompt/completion/body content.
  - Ensure Java compilation retains parameter names if ArchUnit checks parameter names, or inspect method signatures/types/annotations instead.

- Add a final Plan 06 acceptance check that every `platformChatClient.prompt()` and BYOK prompt call includes `SystemPrompts.TRIAGE_SYSTEM_PROMPT`.

## Risk Assessment

**Overall risk: HIGH.** The replan closes important product-flow issues, but the remaining Spring AI boundary violation is a core architectural invariant, not a style preference. Combined with the unresolved enum persistence mismatch, contradictory endpoint path policy, and partial LLM-09 verification, execution can still fail either at runtime or against the phase’s safety/privacy guarantees. After those are corrected, the plan would likely drop to MEDIUM risk, mostly due to Spring AI M4 churn and BYOK provider complexity.
SUCCESS: The process with PID 6684 (child process of PID 13596) has been terminated.
SUCCESS: The process with PID 13596 (child process of PID 30732) has been terminated.
SUCCESS: The process with PID 30732 (child process of PID 15220) has been terminated.
SUCCESS: The process with PID 15220 (child process of PID 13896) has been terminated.
tokens used
129,440
## Summary

Cycle 2 closes several real gaps: gateway-owned tools, core/API layering, server-side BYOK validation-on-save, and drift sanitization are now materially addressed. But it does **not** close the biggest architectural concern: Spring AI still leaks into `core.llm.service` through an explicit `LlmGatewayImpl` ArchUnit exemption and `ToolTranslator<Object>` workaround. YAML/config and LLM-09 privacy are improved but still partial. I also see new or remaining HIGH issues around `BYOKProvider` persistence/JSON mapping and OpenAI-compatible endpoint path handling.

## Closure Verdict

1. **Spring AI boundary violation: NOT CLOSED**  
   `LlmGatewayImpl` still lives in `core.llm.service`, imports Spring AI types, and is explicitly exempted from the ArchUnit rule. This is the same architectural breach with more justification text.

2. **Caller-supplied tools: CLOSED**  
   The public gateway signature is changed to `chat(CallSite, String)`, and `AllowListedTools` owns the fixed `{label, archive, save_draft}` set. There are stale frontmatter references to `List<LlmTool>`, but the implementation tasks and acceptance checks target gateway-owned tools.

3. **Core depending on API DTOs: CLOSED**  
   Plan 05b now introduces core command/result records and has `ByokController` map API DTOs to core records.

4. **BYOK save can bypass validation: CLOSED**  
   `ByokService.save()` now re-runs the upstream provider probe before encrypting/upserting, with tests for direct-save bypass and revoked-key-between-validate-and-save.

5. **YAML/config merge risk: PARTIALLY CLOSED**  
   The merge procedure is now concrete and checks duplicate top-level `spring:` / `zero-mail:` keys. However, later plans still refer to `zeromail.llm...` in places, while Plan 03 declares `zero-mail.*` canonical, and `zero-mail.llm.byok.*` lacks a clearly bound properties class.

6. **LLM-09 privacy verification incomplete: PARTIALLY CLOSED**  
   The replan adds a repository-ban ArchUnit test and scrubber tests, but span-attribute inspection is still missing, and the repo-ban implementation relies on parameter names that may not be retained unless the build uses `-parameters`.

7. **`driftCheck()` sanitization bypass: CLOSED**  
   Plan 03 explicitly routes `driftCheck()` through `sanitizationPipeline.sanitize(...)`, the same system prompt, and the same gateway-owned allow-listed tools.

## New Or Remaining Concerns

- **HIGH: Spring AI isolation remains broken.**  
  The plan did not adopt the requested pure-Java adapter seam. `LlmGatewayImpl` remains the one-class exception, which is exactly the pattern cycle 1 rejected.

- **HIGH: `BYOKProvider` DB/JSON mapping still appears broken.**  
  Plan 01 uses `@Enumerated(EnumType.STRING)` while Liquibase allows lowercase ids (`anthropic`, `openai-compatible`). JPA will persist `ANTHROPIC` / `OPENAI_COMPATIBLE`, violating the check constraint. API JSON likely has the same mismatch without `@JsonValue` / `@JsonCreator`.

- **HIGH: OpenAI-compatible endpoint path policy is still contradictory.**  
  Plan 05a says stored endpoint includes `/v1` and validation should call `${endpoint}/models`; Plan 05b still says `GET ${endpoint}/v1/models`. With the UI placeholder `https://openrouter.ai/api/v1`, this can still produce `/api/v1/v1/models`.

- **HIGH: LLM-09 is not fully verifiable yet.**  
  Observation flags are pinned, but there is no required test that inspects spans and proves prompt/completion content is absent. The static repository ban is also weaker than stated.

- **MEDIUM: Plan 06 may accidentally remove the fixed system prompt.**  
  Earlier plans add `SystemPrompts.TRIAGE_SYSTEM_PROMPT`, but the Plan 06 replacement snippet for the platform call shows `.user(...)` without `.system(...)`. That should be made impossible with an acceptance grep after Plan 06.

- **MEDIUM: Platform `anthropic` provider is still underimplemented.**  
  Config allows `provider: anthropic`, but platform wiring still appears OpenAI-compatible only. Either remove direct Anthropic platform provider from v1 config or implement it.

## Suggestions

- Move Spring AI usage behind a pure-Java client seam:
  - `LlmGatewayImpl` depends on `LlmModelClient`.
  - `SpringAiLlmModelClient` lives in `core.llm.gateway.springai`.
  - It returns a pure `RawToolCall(functionName, argsJson, usage)`.
  - Remove the ArchUnit exemption and `ToolTranslator<Object>`.

- Add `BYOKProviderAttributeConverter` and Jackson annotations:
  - `@Converter(autoApply = true)` mapping enum ↔ `id()`.
  - `@JsonValue` on `id()`.
  - `@JsonCreator` delegating to `fromId`.

- Centralize endpoint joining:
  - Store canonical base including version path.
  - Always append `models` or `messages` with a safe URI join helper.
  - Add explicit tests for `https://openrouter.ai/api/v1` → `/api/v1/models`, not `/api/v1/v1/models`.

- Strengthen LLM-09:
  - Add a test that captures Micrometer/OTel span attributes and asserts no prompt/completion/body content.
  - Ensure Java compilation retains parameter names if ArchUnit checks parameter names, or inspect method signatures/types/annotations instead.

- Add a final Plan 06 acceptance check that every `platformChatClient.prompt()` and BYOK prompt call includes `SystemPrompts.TRIAGE_SYSTEM_PROMPT`.

## Risk Assessment

**Overall risk: HIGH.** The replan closes important product-flow issues, but the remaining Spring AI boundary violation is a core architectural invariant, not a style preference. Combined with the unresolved enum persistence mismatch, contradictory endpoint path policy, and partial LLM-09 verification, execution can still fail either at runtime or against the phase’s safety/privacy guarantees. After those are corrected, the plan would likely drop to MEDIUM risk, mostly due to Spring AI M4 churn and BYOK provider complexity.

---

## OpenCode Review

## Summary

Cycle 2 closes several product/security gaps on paper, especially YAML merge discipline, server-side BYOK save re-validation, core/API layering, gateway-owned tools intent, drift sanitization, and LLM-04 wording. However, it does **not** close the most important architectural blocker: Spring AI usage is still deliberately allowed in `LlmGatewayImpl` via an ArchUnit exemption, with `ToolTranslator<Object>` and casts preserving the same boundary leak. I would not execute this plan set unchanged because that remaining HIGH plus a still-unfixed `BYOKProvider` DB/JSON mapping issue and a reintroduced endpoint path ambiguity can break Phase 2C core guarantees.

## Closure Verdict

1. **Spring AI boundary violation: NOT CLOSED**  
   Plans still exempt `LlmGatewayImpl` from the ArchUnit rule and keep Spring AI types/casts in `core.llm.service`, so LLM-01 isolation is still compromised.

2. **Gateway accepts caller-supplied tools: PARTIALLY CLOSED**  
   The public interface is changed to gateway-owned tools and `AllowListedTools` is added, but plan text/frontmatter remains inconsistent and Plan 05a has a likely `List<LlmTool>` vs `List<ToolCallback>` mismatch in the BYOK branch.

3. **Core service depends on API DTOs: CLOSED**  
   Plan 05b adds core command/result records and explicitly maps API DTOs only in `ByokController`, with tests/grep gates banning `com.zeromail.api` imports in `ByokService`.

4. **BYOK save can bypass validation: CLOSED**  
   Plan 05b now requires `save()` to re-run the upstream provider probe before encrypt/upsert and adds tests proving direct save without prior validate fails.

5. **YAML duplicate top-level key/config drift: CLOSED**  
   Plan 03 now gives concrete merge instructions, canonicalizes on `zero-mail.*`, checks top-level key counts, and requires binding verification.

6. **LLM-09 privacy verification incomplete: PARTIALLY CLOSED**  
   Logback scrub extension and repository-content ArchUnit tests were added, but the repository rule depends on Java parameter names being available and there is still no concrete span-attribute inspection or `ChatResponse`/exception stringification guard.

7. **LLM-04 wording / drift/system-prompt maintenance: CLOSED**  
   Plan 03 updates `REQUIREMENTS.md`, adds `SystemPrompts.TRIAGE_SYSTEM_PROMPT`, and requires both `chat()` and `driftCheck()` to run through the sanitization pipeline.

## New Or Remaining Concerns

- **HIGH: Spring AI boundary remains intentionally broken.**  
  The core requirement says direct Spring AI usage is isolated in `core.llm.gateway.springai`, but the plans preserve an exemption for `LlmGatewayImpl` in `core.llm.service`. This is not a closure; it is a documented waiver of the invariant.

- **HIGH: `BYOKProvider` persistence/JSON mismatch is still unresolved.**  
  Plan 01 still uses `@Enumerated(EnumType.STRING)` while the DB check allows lowercase ids `'anthropic'` and `'openai-compatible'`. JPA will persist `ANTHROPIC` / `OPENAI_COMPATIBLE`, violating the check constraint. API JSON also needs explicit id mapping.

- **HIGH: OpenAI-compatible endpoint path policy is still contradictory.**  
  Plan 05a says stored endpoint includes version path and validate should call `${endpoint}/models`, but Plan 05b still says `GET ${canonicalEndpoint}/v1/models`. With `https://openrouter.ai/api/v1`, this can produce `/api/v1/v1/models`.

- **HIGH: LLM-09 verification is not fully reliable.**  
  The new repository ArchUnit rule checks parameter names, which are often unavailable as meaningful names unless compiled with `-parameters`. It also does not inspect Spring AI observation spans despite SPEC acceptance explicitly requiring no prompt/completion content in spans.

- **MEDIUM: Gateway-owned tools refactor has internal inconsistencies.**  
  The intended public signature is `chat(CallSite, String)`, but frontmatter and some plan text still mention `List<LlmTool>`, and Plan 05a’s BYOK helper appears to accept translated `ToolCallback`s while being passed project-local tools.

- **MEDIUM: Drift job test instructions conflict.**  
  Behavior says mock `LlmGateway`, but action steps still instruct creating tests with `@MockBean ChatModel`, which was a cycle-1 concern.

- **MEDIUM: Plan 06 logs `reservation.value()` despite acceptance saying reservation IDs should not be logged.**  
  The grep gate misses this because it only searches `reservation.id` / `reservation.uuid`.

- **LOW: Several grep gates are fragile.**  
  They help as smoke checks, but some can pass while the invariant is still broken, especially multi-line builder checks and log-content checks.

## Suggestions

- Replace the Spring AI exemption with a pure adapter seam:
  - `core.llm.service.LlmGatewayImpl` depends on `LlmModelClient`.
  - `core.llm.gateway.springai.SpringAiLlmModelClient` owns all `ChatClient`, `ChatResponse`, `ToolCallback`, `OpenAiChatOptions`, and vendor SDK imports.
  - Adapter returns pure Java `RawToolCall(functionName, argsJson, usageMetadata)`.
  - `ActionValidator` stays in service and validates the pure result.
  - Remove `ToolTranslator<Object>` and remove the ArchUnit exemption entirely.

- Fix `BYOKProvider` mapping:
  - Add `AttributeConverter<BYOKProvider, String>` using `id()` / `fromId()`.
  - Annotate `BYOKProvider` with `@JsonValue` and `@JsonCreator`.
  - Add persistence and MVC serialization tests proving DB stores `openai-compatible` and API accepts/returns that exact id.

- Make endpoint joining single-policy and tested:
  - Store canonical base URL including version path.
  - OpenAI-compatible validate calls `${base}/models`, not `${base}/v1/models`.
  - Anthropic validate calls `${base}/v1/messages` only if base is origin; otherwise define the same base-path rule.
  - Add exact tests for OpenRouter `https://openrouter.ai/api/v1` to ensure no double `/v1`.

- Strengthen LLM-09:
  - Add a test inspecting observation/span attributes after a gateway call with known sentinel prompt/completion strings.
  - Replace parameter-name-only repository ban with method-name/type/name checks that do not rely solely on `-parameters`.
  - Add a test that safety/sanitization/global exception handling never logs exception objects or model output.

- Align Plan 07 tests with the abstraction:
  - Worker drift tests should mock `LlmGateway`, not `ChatModel`.

## Risk Assessment

**Overall risk: HIGH.** The replan materially improves many cycle-1 gaps, but one core invariant remains explicitly waived: Spring AI isolation. That alone keeps execution risk high because Phase 2C’s central purpose is to create a single enforceable gateway boundary. The unresolved enum persistence mismatch and contradictory endpoint path policy are also likely to fail implementation or produce unsafe behavior. Once those HIGH items are fixed, residual risk should drop to MEDIUM, mostly from Spring AI 2.0.0-M4 API churn and BYOK/provider edge cases.

---

## Consensus Summary

Both reviewers independently rate Phase 2C **HIGH risk after replan**. The cycle-2 commit closes real product-flow gaps (gateway-owned tools, core/API layering, BYOK validate-on-save, drift sanitization, system prompt, REQUIREMENTS.md LLM-04, YAML merge concreteness), but **leaves the cycle-1 architectural blocker intact** and **introduces / preserves three additional HIGH issues** that can break the phase's correctness guarantees.

### Cycle-1 HIGH Closure Scoreboard (consensus)

| # | Cycle-1 HIGH                                            | Codex     | OpenCode  | Consensus |
|---|---------------------------------------------------------|-----------|-----------|-----------|
| 1 | Spring AI boundary violation (`LlmGatewayImpl` exempt)  | NOT CLOSED| NOT CLOSED| **NOT CLOSED** |
| 2 | Caller-supplied tools to gateway                         | CLOSED    | PARTIALLY | PARTIALLY (frontmatter / 05a inconsistency) |
| 3 | core ↔ api DTO layering inversion                       | CLOSED    | CLOSED    | **CLOSED** |
| 4 | BYOK save can bypass server-side validation              | CLOSED    | CLOSED    | **CLOSED** |
| 5 | YAML duplicate top-level keys / `zeromail` drift         | PARTIALLY | CLOSED    | PARTIALLY (`zero-mail.llm.byok.*` properties class still missing) |
| 6 | LLM-09 privacy verification incomplete                   | PARTIALLY | PARTIALLY | **PARTIALLY CLOSED** (no span-attribute test; ArchUnit ban depends on `-parameters`) |
| 7 | `driftCheck()` bypasses sanitization (Codex divergent HIGH) | CLOSED | CLOSED    | **CLOSED** |

### Agreed Cycle-2 HIGH Concerns (blockers — must close before execution)

1. **Spring AI boundary still violated.** `LlmGatewayImpl` in `core.llm.service` keeps an explicit ArchUnit exemption, imports Spring AI types (`ChatClient`, `ChatResponse`, `ToolCallback`, `OpenAiChatOptions`), and uses `ToolTranslator<Object>` casts. Both reviewers reject this as a documented waiver of LLM-01 rather than a fix. **Fix:** introduce `LlmModelClient` (pure Java) in `core.llm.service`; move all Spring AI calls into `core.llm.gateway.springai.SpringAiLlmModelClient`; adapter returns `RawToolCall(functionName, argsJson, usageMetadata)`; `ActionValidator` consumes pure Java result; remove `ToolTranslator<Object>`; remove the ArchUnit exemption entirely.

2. **`BYOKProvider` persistence/JSON mismatch unresolved.** Plan 01 still uses `@Enumerated(EnumType.STRING)` while the Liquibase check constraint allows lowercase ids `'anthropic'` and `'openai-compatible'`. JPA will persist `ANTHROPIC` / `OPENAI_COMPATIBLE`, violating the check constraint at first insert. Same problem on the JSON boundary without `@JsonValue` / `@JsonCreator`. **Fix:** add `BYOKProviderAttributeConverter` mapping enum ↔ `id()`; add `@JsonValue` on `id()` and `@JsonCreator` delegating to `fromId(String)`; add a persistence test inserting `openai-compatible` and a MVC test round-tripping the same id.

3. **OpenAI-compatible endpoint path policy is contradictory across plans.** Plan 05a says stored endpoint includes `/v1` and validate hits `${endpoint}/models`; Plan 05b still says `GET ${endpoint}/v1/models`. With the UI placeholder `https://openrouter.ai/api/v1`, this produces `/api/v1/v1/models`. **Fix:** pin one policy in the SPEC ("stored URL is canonical base including version path; validate appends `/models`, not `/v1/models`"); add explicit tests for OpenRouter and OpenAI base URLs proving no double-`/v1`.

4. **LLM-09 verification still not fully reliable.** No required test inspects Micrometer/OTel span attributes for absence of prompt/completion content; the new repository-content ArchUnit rule checks parameter names which are not retained without `javac -parameters`; no guard against `ChatResponse.toString()` or exception object serialization leaking content. **Fix:** add a span-attribute inspection test using a sentinel prompt; switch the ArchUnit rule to method-name + parameter-type + annotation predicates that don't depend on parameter names; add a global-exception-handler test that asserts no model output is logged.

### Agreed Cycle-2 MEDIUM Concerns (fix before execution but not blocking another cycle)

- **Gateway-owned tools refactor has stale references.** Frontmatter and parts of plan text still mention `List<LlmTool>`; Plan 05a's BYOK branch has a likely `List<LlmTool>` vs `List<ToolCallback>` mismatch. Behavior is right; surface area still inconsistent.
- **Plan 06 platform-call snippet may drop the system prompt.** Replacement code shows `.user(...)` without `.system(SystemPrompts.TRIAGE_SYSTEM_PROMPT)`. Add an acceptance grep after Plan 06 that `.system(SystemPrompts.TRIAGE_SYSTEM_PROMPT)` appears on every chat call site.
- **Drift job tests still reference `@MockBean ChatModel` in places** despite the behavior section saying mock `LlmGateway`. Action steps and behavior must agree.
- **Platform `anthropic` provider underimplemented.** Config allows `provider: anthropic` for platform routes, but platform wiring still appears OpenAI-compatible-only. Either implement it or drop it from v1 config.
- **Plan 06 logs `reservation.value()` despite acceptance saying reservation IDs should not be logged** (OpenCode); the grep gate misses it because it only searches `reservation.id` / `reservation.uuid`.

### Divergent Views (both noted, framing differs)

- **Plan 06 system-prompt risk:** Codex MEDIUM ("may accidentally remove"); OpenCode silent on this specific snippet but flagged the broader system-prompt requirement upstream. Treat as MEDIUM and add a grep gate.
- **`zero-mail.llm.byok.*` properties class:** Codex flags as part of HIGH-5 partial closure; OpenCode flags as MEDIUM ("config underspecified"). Treat as MEDIUM cleanup unless it materially blocks bind tests.
- **Anthropic platform provider:** Codex MEDIUM, OpenCode silent. Treat as MEDIUM scoping question.

### Recommended Next Step

Run `/gsd-plan-phase 2C --reviews` for **cycle 3**, focused exclusively on the **4 agreed-HIGH cycle-2 concerns** above. The fix for HIGH-1 is the largest (introducing `LlmModelClient` and refactoring Plans 03/04/05a/06 to consume it), but it eliminates HIGH-1, the residual MEDIUM around `ToolTranslator<Object>`, and clarifies the surface area for HIGH-2/3/4 verification. After cycle-3 closure, residual risk should drop to MEDIUM (Spring AI 2.0.0-M4 churn + BYOK provider asymmetry).
