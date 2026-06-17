# Spring AI 2.0.0 GA — Migration Scope Brief (pre-v1.4)

**Researched:** 2026-06-17
**Author:** GSD researcher (read-only audit; no code changed)
**Sources:** `/websites/spring_io_spring-ai_reference` (Context7, HIGH reputation), local codebase grep.

---

## TL;DR

The version bump from `2.0.0-M6` → `2.0.0` is **already done in `libs.versions.toml`** (commit `eb19ecbc`) and the codebase already follows the GA tool-execution pattern: the `ToolCallingAdvisor` auto-registers, no call site sets `internalToolExecutionEnabled(false)`, no deprecated `FunctionCallback` / `defaultFunctions` / consumer-style `tools(t -> …)` patterns survive, and `ChatClient.prompt().tools(toolCallback[])` is used everywhere. **There is no breaking-change cleanup work to do.** The interesting question is the new **Tool Search Tool** feature in GA (`ToolSearchToolCallingAdvisor` + `ToolIndex`) — it directly fits v1.4's growing tool catalog (24 chat tools today, +Calendar/Drive in v1.4 → ~32-35) and the agentic meeting-brief loop. **Recommendation: skip a migration phase; ship as a quick task in v1.4 if/when token cost on the 30+ tool catalog actually bites.** Track Tool Search Tool as a deferred optimisation, not a v1.4 entry-criterion.

---

## 1. Changelog Highlights (M6 → 2.0.0 GA)

### Breaking Changes

1. **`ChatModel` no longer runs the tool-execution loop internally.** (Confirmed: introduced in 2.0.0-RC1 per the `RC1` announcement linked from `.planning/research/PITFALLS.md` Pitfall 2.) `chatModel.call(prompt)` returns the first response and stops; if `response.hasToolCalls()` is true, the caller drives the loop with `ToolCallingManager.executeToolCalls(...)`. Source: docs.spring.io/spring-ai/reference/api/chat/bedrock-converse.html, ollama-chat.html, mistralai-chat.html — all show the same `while (response.hasToolCalls()) { … }` pattern.
2. **`internalToolExecutionEnabled` flag removed from `ChatOptions`.** Source: docs.spring.io/spring-ai/reference/upgrade-notes.html. Removing it from the builder is the documented migration; if you used `false` to disable the loop, switch to `AdvisorParams.toolCallingAdvisorAutoRegister(false)` on `ChatClient` or drive the loop manually on `ChatModel`.
3. **`ChatClient.prompt().tools(t -> t.callbacks(…).context(…))` consumer-style API replaced.** New shape: `chatClient.prompt().tools(myCallback).toolContext(Map.of("tenantId", "acme"))`. Source: docs.spring.io/spring-ai/reference/upgrade-notes.html (Migrate ChatClient tools() API).
4. **`ChatClient.builder().defaultFunctions(...)` / `FunctionCallback` / `FunctionCallbackWrapper` / `MethodInvokingFunctionCallback` removed in favour of `FunctionToolCallback` and the `@Tool` / `@ToolParam` annotation model.** Already on this path since M6; GA hardens it.
5. **(Forward-looking, not GA-blocking)** — the **1.1 line** announces that `ChatClient.tools()` will be deprecated in favour of `toolSpecifications()` + `toolCallbacks()` in M8. This is **NOT in 2.0 GA** — sourced from `spring-ai/reference/1.1/upgrade-notes.html`, distinct doc tree. Mentioning for awareness only; do not pre-emptively migrate.

### New Features (notable)

1. **Tool Search Tool (`ToolSearchToolCallingAdvisor` + `ToolIndex` + `VectorToolIndex`).** Dynamic, on-demand tool discovery so 100s of tools can be registered while only the top-K semantically relevant tools are shipped to the LLM per call. Source: docs.spring.io/spring-ai/reference/guides/dynamic-tool-search.html, docs.spring.io/spring-ai/reference/api/tools.html.
2. **Stable `ToolCallingManager` / `DefaultToolCallingManager` API** for callers that disable the advisor and run the loop themselves (the "agentic" pattern).
3. **`ToolCallbacks.from(Object)`** convenience to turn any `@Tool`-annotated POJO into a `ToolCallback[]`. Source: docs.spring.io/spring-ai/reference/api/chat/bedrock-converse.html.
4. **Tool calling on `StreamingChatModel`** is supported end-to-end (we already use this via `SpringAiStreamingChatModelClient`).
5. Provider parity for **Anthropic / Google GenAI / DeepSeek / OpenAI / OpenAI-compatible (OpenRouter)** under one `ChatClient` surface — we already lean on this in `SpringAiProviderChatClientFactory`.
6. Boot 4.1-aligned auto-configuration via `spring-ai-starter-model-*` starters.

---

## 2. Tool Search Tool — What It Actually Is

**Doc quote** (docs.spring.io/spring-ai/reference/guides/dynamic-tool-search.html):

> "The Tool Search Tool pattern enables AI agents to discover tools on-demand instead of loading all tool definitions upfront. This approach significantly reduces token consumption by only sending relevant tool definitions to the model when needed. It also improves tool selection accuracy by presenting the model with a smaller, more relevant set of tools."

**Doc quote** (docs.spring.io/spring-ai/reference/api/tools.html):

> "Configure a ToolIndex (e.g., VectorToolIndex for semantic search) … Tools are registered but NOT sent to the LLM initially, being discovered on-demand."

**Mechanism (interpretation):** It is option **(b)** from the brief's question — a vector-backed tool registry where the LLM picks tools by semantic similarity. Concretely:

- You register tools normally (`ChatClient.builder().defaultTools(new MyTools())`).
- You add `ToolSearchToolCallingAdvisor.builder().toolIndex(toolIndex).maxResults(5).build()` as a default advisor.
- The advisor only exposes one synthetic `tool_search` tool to the LLM initially.
- When the LLM calls `tool_search` with a natural-language query, the advisor semantically retrieves the top-K matching tool definitions from the `ToolIndex` and feeds those back as available tools.
- Subsequent iterations then invoke the real tools.

**`ToolIndex` implementations** shown in docs: `VectorToolIndex(vectorStore)` — requires a `VectorStore` bean.

### Fit-for-purpose for Zero Mail

| Dimension | Assessment |
|-----------|------------|
| **Token cost today** | 24 tools, single-call. Each tool definition is ~100-200 tokens of JSON schema → ~3-5k tokens of tool overhead per request. Real, not crippling. |
| **Token cost in v1.4** | +Calendar (estimate +5 tools: `freebusy_check`, `propose_meeting`, `cancel_meeting`, `list_events`, `get_event`) + Drive (+3-4 tools) → ~32-35 tools → ~6-9k overhead per request. Still real, not crippling — but it's the kind of slope that justifies the optimisation before v1.5. |
| **Privacy compatibility** | **Hard blocker for `VectorToolIndex` as documented.** Spring AI's `VectorToolIndex` needs a `VectorStore`, which means embedding the tool descriptions. Tool descriptions are static, dev-authored strings — embedding them is fine and does NOT violate the v1 "no embeddings of user mail" constraint. **BUT** the project rule "**Embedding store / vector DB in v1**" hard ban in CLAUDE.md is currently scoped to user mail; it does not technically forbid an in-memory vector store of tool descriptions. Read the constraint precisely before adopting: it's a privacy line, not an infra-ban line. Still, introducing a `VectorStore` bean is a noticeable architectural addition. |
| **Alternative path** | Could implement a hand-rolled `ToolIndex` that uses keyword/intent matching (a `Map<String, ToolDescriptor>` with a small classifier) instead of a vector store. The advisor API accepts any `ToolIndex` implementation. |
| **Accuracy upside** | Doc claims "improved tool selection accuracy by presenting the model with a smaller, more relevant set of tools." Plausible — 35-tool catalogs are known to cause "tool confusion" in models below frontier tier. Material for OpenRouter cheap-tier routing. |
| **Risk** | The `tool_search` round-trip adds **at least one extra LLM call** per request (LLM → tool_search → LLM with discovered tools). Net cost may be flat or higher for short single-tool queries, only positive for long catalogs + concentrated relevance. |

**Verdict:** Tool Search Tool is a real, well-shaped capability. **It is the right answer for "we have 50+ tools and short user prompts" — not yet our shape.** Adopt when (a) catalog crosses ~40 tools, or (b) we measure tool-overhead tokens crowding out conversation history.

---

## 3. Zero Mail Codebase Audit Findings

| File | Pattern Found | M6/GA | Action Required |
|------|---------------|-------|-----------------|
| `gradle/libs.versions.toml` | `springAi = "2.0.0"` (pinned) | **GA** | None — bump already shipped |
| `SpringAiProviderChatExecutor.java` | `chatClient.prompt().system(…).user(…).tools(ToolCallback[]).options(…).call().chatResponse()` (advisor-managed loop, no `internalToolExecutionEnabled` flag) | **GA-correct** | None |
| `SpringAiLlmModelClient.java` | Same pattern; uses `.tools(Object[])`, `.advisors(…preserveRawToolCalls)`, `.options(…)`, `.call().chatResponse()` | **GA-correct** | None |
| `SpringAiLlmChatSupport.java` | `FunctionToolCallback.builder(name, lambda).description(…).inputSchema(…).inputType(Map.class).build()` | **GA-correct** | None |
| `SpringAiRawToolCallSupport.java` | Uses `ChatClient.AdvisorSpec.param(ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER, false)` — disables the auto loop on the platform call path so we can capture raw tool calls without execution | **GA-correct** (this attribute is exactly the GA-stable hook the upgrade notes point to) | None — but **double-check at runtime** that the `ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER` constant name was not renamed across RC1/RC2/GA. Cheap to verify by booting the app. |
| `SpringAiProviderChatClientFactory.java` | Per-provider builders for OpenAI / Anthropic / Google GenAI / DeepSeek with explicit `.options(…)` and `OpenAiToolChoiceOptions.required()`; tool wiring delegated to `.tools(...)` on the prompt spec | **GA-correct** | None |
| `PlatformChatClientConfig.java` | `ChatClient.create(openAiChatModel)` factory — simple and stable | **GA-correct** | None |
| `SpringAiStreamingChatModelClient.java` | `resolvedChatClient.chatClient().prompt(prompt).tools(ToolCallback[]).advisors(…preserveRawToolCalls).options(…).stream().chatResponse()` and `ToolCallingAdvisor` disabled to fan raw tool calls to the UI | **GA-correct** | None |
| `SpringAiChatModelFactory.java` | Caffeine-cached `ResolvedChatClient` per `(tenant, model, provider, secretVersion, catalogVersion)` | **GA-correct** | None |
| `ToolCallbackTranslator.java` | `FunctionToolCallback.builder(name, lambda).description(…).inputSchema(JsonSchemaGenerator.generateForType(record)).inputType(record).build()` plus the `properties: {}` workaround for no-arg object schemas — exactly the schema-compat repair allowed by CLAUDE.md | **GA-correct** | None |
| `SemanticIntentEvaluator.java` | `BeanOutputConverter<SemanticIntentResponse>` for structured output | **GA-correct** | None — already on the supported structured-output API |
| `SpringAiObservationDisabledTest.java` (`backend/api`) | Verifies prompt/completion capture is OFF | **GA-correct** | None — observation API surface stable across M6→GA |
| `OpenRouterStreamingProbeTest.java` (test only) | Uses `OpenAiChatOptions.builder().toolCallbacks(…).build()` AND `chatModel.stream(prompt)` direct on `OpenAiChatModel` | **GA-correct** | None — this is the "raw probe" test that intentionally bypasses `ChatClient` to exercise the OpenRouter HTTP shape. Does not need tool-loop semantics. The `chatModel.stream(prompt)` call here is fine in GA (no tool-execution loop required — the test asserts on the stream of raw `ChatResponse` chunks, not on tool execution). |
| `FunctionCallback` / `defaultFunctions` / `MethodInvokingFunctionCallback` / consumer-style `tools(t -> …)` | **Not found anywhere** in `backend/` | n/a | None — no M6 ghosts |
| `ChatMemory` / `MessageWindowChatMemory` / `PromptChatMemoryAdvisor` | **Not used.** Conversation history is rebuilt every call from our own `ChatMessage` aggregate (see `SpringAiStreamingChatModelClient.prompt(...)`) | by design | None for the migration. **Optional adoption** — see §4. |
| Spring AI MCP starters | **Not pulled in.** | by design | None |

**Honest summary:** The audit found **zero migration debt**. Every Spring AI call site already uses the GA-correct API shape, including the two trickier patterns (advisor auto-register opt-out, structured-output via `BeanOutputConverter`). The version bump in `libs.versions.toml` carries the whole migration.

---

## 4. New 2.0 GA Features Worth Adopting in v1.4 Context

| Feature | Fit for v1.4 | Recommendation |
|---------|--------------|----------------|
| **Tool Search Tool** (`ToolSearchToolCallingAdvisor` + `ToolIndex`) | Catalog grows from 24 → ~32-35 with Calendar/Drive. Material but not urgent. Requires either a `VectorStore` bean (re-reads the embeddings privacy line) or a hand-rolled keyword `ToolIndex`. | **Defer.** Track as a v1.5 optimisation. Re-evaluate after v1.4 measures real tool-overhead tokens in production. |
| **External tool-loop via `ToolCallingManager`** (manual `while (response.hasToolCalls())`) | v1.4 Phase 4 (AI meeting briefs) wants per-iteration budget checks (token cap, time cap, tool-call cap). The advisor-managed loop hides iteration boundaries. | **Adopt selectively in Phase 4 only.** Use `DefaultToolCallingManager` for the meeting-brief agentic path; keep `ChatClient` + advisor for everything else (chat, single-shot rules compile, semantic intent). Do **not** rewrite the existing chat-stream path. |
| **`ToolCallbacks.from(@Tool POJO)`** convenience | Could simplify `ToolCallbackTranslator` if we move from `FunctionToolCallback.builder()` to `@Tool`-annotated POJOs. But — our tool catalog is data-driven (`ChatToolCatalog.ToolDefinition` records, validated by `chatToolCatalog.validate()`), which is structurally incompatible with annotation-based discovery. | **Skip.** The current builder-based pattern is the right fit for a data-driven catalog. |
| **`BeanOutputConverter`** for meeting-brief schema | Phase 4 (AI meeting briefs) outputs a structured brief. `BeanOutputConverter<MeetingBriefSchema>` plus `.entity(MeetingBriefSchema.class)` would replace any "parse the model text as JSON manually" code. | **Adopt in Phase 4.** Aligns with how `SemanticIntentEvaluator` already works. |
| **`ChatMemory` / `MessageWindowChatMemory`** | Tempting for the chat assistant. But our conversation history is the authoritative store (`chat_message.parts` + privacy guarantees from ARCH-02 carve-out); a parallel Spring AI memory store would duplicate state and risk drift. | **Skip.** Our hand-rolled history is correct precisely because it encodes the ARCH-02 carve-out. |
| **Spring AI MCP client starters** | Could let Zero Mail consume external MCP servers (e.g., a self-hosted Google Workspace MCP). Not in v1.4 scope. | **Defer.** Out of scope. |
| **Observation enhancements** | Already verified disabled via `SpringAiObservationDisabledTest`; the disable knob remains stable. | **No change.** Cost tracking continues through our `JdbcLlmUsageRecorder`, not Spring AI observation. |

---

## 5. Recommended Implementation Scope

**Pick: quick task (under 2 hours), NOT a phase.**

Justification: codebase audit found **zero migration debt**. The version pin in `libs.versions.toml` is the migration. The only worthwhile new feature for v1.4 (`BeanOutputConverter` for meeting briefs, `ToolCallingManager` for the agentic brief loop) belongs **inside v1.4 Phase 4 (AI meeting briefs) as adoption tasks**, not as a separate pre-v1.4 migration phase. A phase with no migration delta is process tax.

### Scope items (in order)

1. **Smoke-verify the M6 → GA pin actually boots.** Run `./gradlew :backend:api:bootRun` against the dev DB, watch for any `NoSuchMethodError` / `ClassNotFoundException` on Spring AI symbols (especially `ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER`, which we depend on in `SpringAiRawToolCallSupport`). 5 minutes.
2. **Run the existing test ladder.** `./gradlew :backend:core:test :backend:api:test` — particularly `SpringAiObservationDisabledTest` and any test that exercises `SpringAiLlmModelClient` / `SpringAiStreamingChatModelClient`. 10 minutes.
3. **One-shot manual chat smoke test** through the running app to verify tool calls still stream and the `preserveRawToolCalls` advisor still suppresses the auto-loop. 10 minutes.
4. **Annotate v1.4 Phase 4 plan** (when written) with: "use `BeanOutputConverter<MeetingBriefSchema>` for structured brief output" + "use `DefaultToolCallingManager` for the agentic loop with per-iteration budget checks." This is a plan annotation, not work — done during v1.4 phase planning. 5 minutes.
5. **Open a v1.5 tracking note** in `.planning/research/` (or `ROADMAP.md` if that's the convention): "Evaluate Tool Search Tool if tool catalog crosses 40 entries OR tool-overhead tokens exceed 10% of average request." 5 minutes.

Total: ~35 minutes once you sit down. No code change. No commit unless step 1/2/3 surfaces a real failure.

### Out of scope

- Migrating the chat-stream path to `ToolCallingManager` (advisor pattern is correct for streaming).
- Adopting `@Tool` annotation discovery (our data-driven catalog is correct).
- Adopting `ChatMemory` (collides with the ARCH-02 carve-out design).
- Adding `VectorStore` / `VectorToolIndex` (premature; re-reads the v1 embeddings privacy line).
- Adding Spring AI MCP starters (v1.4 has no MCP requirement).

---

## 6. Risks + Mitigations

| Risk | Mitigation |
|------|------------|
| `ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER` constant renamed between M6 and GA (we rely on it in `SpringAiRawToolCallSupport`) | Step 1 of the quick task verifies at boot. If renamed, find the GA equivalent in the resolved JAR via JetBrains MCP `search_symbol` on `TOOL_CALLING_ADVISOR_AUTO_REGISTER` and update one line. |
| Tool-execution behaviour drifted: a GA-version `ChatClient.prompt().tools(…).call()` now auto-executes tools even when our advisor opt-out is set | Manually verify in step 3 that the chat-stream UI still receives raw tool calls (not executed) on the wire. If broken, fall back to the explicit `AdvisorParams.toolCallingAdvisorAutoRegister(false)` shape shown in the GA docs. |
| Provider-specific options API (e.g. `OpenAiChatOptions.builder().toolChoice(OpenAiToolChoiceOptions.required())`) changed signature in GA | Compile-time: any breakage shows up in `./gradlew :backend:core:compileJava`. Already on Gradle's incremental compile — fast to surface. |
| `BeanOutputConverter` semantics changed (it's stable in M6, but RC1/RC2/GA notes are sparse) | `SemanticIntentEvaluator` already uses it and is exercised by tests — green tests prove the API still works. |
| Adopting Tool Search Tool inadvertently introduces a `VectorStore` bean that conflicts with the "no embedding store in v1" CLAUDE.md rule | Don't adopt in v1.4 (see recommendation). When evaluated for v1.5, explicitly classify "embeddings of dev-authored static tool descriptions" against the privacy rule before proceeding. |

---

## 7. Open Questions for User

1. **Approve the quick-task framing?** Or do you want a small phase even though the audit found zero migration debt (for process consistency / a paper trail)?
2. **Tool Search Tool — defer to v1.5 as recommended, or evaluate inside v1.4 Phase 4** (agentic meeting briefs is the strongest concrete use-case in this milestone — calendar + drive tools land there too)?
3. **Embedding store / vector DB rule** — does the v1 ban apply to dev-authored static text (tool descriptions) too, or is it scoped strictly to user mail? Material for any future Tool Search Tool decision.
4. **`BeanOutputConverter` + `DefaultToolCallingManager` adoption** — confirmed to go into v1.4 Phase 4 plan as annotations now, or wait until phase planning?
5. **Smoke verification** — do you want me to execute steps 1-3 of the scope inline right now (boot the app, run the tests, manually exercise chat), or queue it for when you start v1.4?
